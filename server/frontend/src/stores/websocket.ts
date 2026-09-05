import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { WebSocketMessage } from '@/types/websocket'

/**
 * WebSocket状态管理
 *
 * 连接地址: {protocol}//{host}/ws/device?token=xxx 或 ?key=xxx
 * 协议定义见 docs/前后端API文档.md 第7章
 * - 连接成功(服务端connected消息)后即可订阅用户实时数据
 * - 每30秒发送一次ping，服务端返回pong；超过60秒未收到pong则断开并自动重连
 * - 断线自动重连，指数退避(1s/2s/4s/...，最长30s)，最多尝试5次
 */
export const useWebSocketStore = defineStore('websocket', () => {
  // 状态
  const connected = ref(false)
  const reconnecting = ref(false)
  const error = ref<string | null>(null)

  // WebSocket实例与配置
  let ws: WebSocket | null = null
  let heartbeatTimer: number | null = null
  let pongTimeout: number | null = null
  let reconnectTimer: number | null = null
  let reconnectAttempts = 0
  let generation = 0
  let manuallyClosed = false
  const maxReconnectAttempts = 5
  const heartbeatInterval = 30000 // 每30秒发送一次心跳
  const pongTimeoutMs = 60000 // 超过60秒未收到pong视为心跳超时

  // 消息处理器(页面注册，用于处理realtime_update等业务消息)
  let messageHandler: ((message: WebSocketMessage) => void) | null = null

  /**
   * 解析服务端消息
   */
  const parseMessage = (data: string): WebSocketMessage | null => {
    try {
      return JSON.parse(data) as WebSocketMessage
    } catch {
      return null
    }
  }

  /**
   * 清除pong等待定时器
   */
  const clearPongTimeout = () => {
    if (pongTimeout) {
      window.clearTimeout(pongTimeout)
      pongTimeout = null
    }
  }

  /**
   * 发送一次心跳并启动60秒超时检测
   */
  const sendPing = () => {
    if (!ws || ws.readyState !== WebSocket.OPEN) return

    ws.send(JSON.stringify({ type: 'ping' }))
    clearPongTimeout()

    const currentGeneration = generation
    pongTimeout = window.setTimeout(() => {
      // 仅当仍是当前连接且确实未收到pong时才断开
      if (generation === currentGeneration && ws && ws.readyState === WebSocket.OPEN) {
        console.warn('WebSocket心跳超时，断开连接以触发重连')
        ws.close()
      }
    }, pongTimeoutMs)
  }

  /**
   * 启动心跳
   */
  const startHeartbeat = () => {
    stopHeartbeat()
    sendPing()
    heartbeatTimer = window.setInterval(sendPing, heartbeatInterval)
  }

  /**
   * 停止心跳
   */
  const stopHeartbeat = () => {
    if (heartbeatTimer) {
      window.clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
    clearPongTimeout()
  }

  /**
   * 连接WebSocket
   * @param userId 要订阅的用户标识(本系统中为用户邮箱，秘钥查看者可不传)
   * @param token 登录Token(可选)
   * @param key 用户秘钥(可选)
   */
  const connect = (userId: string, token?: string, key?: string) => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    // 秘钥认证优先于Token认证(与REST请求层保持一致):
    // 携带key打开监控页(查看他人分享)时,即使浏览器残留登录Token,也以秘钥身份连接
    const authParam = key
      ? `key=${encodeURIComponent(key)}`
      : token
        ? `token=${encodeURIComponent(token)}`
        : ''
    const wsUrl = `${protocol}//${host}/ws/device?${authParam}`

    const currentGeneration = ++generation
    manuallyClosed = false

    try {
      ws = new WebSocket(wsUrl)

      ws.onopen = () => {
        if (generation !== currentGeneration) return
        console.log('WebSocket连接成功:', wsUrl)
        connected.value = true
        reconnecting.value = false
        reconnectAttempts = 0
        error.value = null

        // 订阅用户数据(仅当明确知道要订阅的用户标识时)
        if (userId) {
          subscribe(userId)
        }

        // 启动心跳
        startHeartbeat()
      }

      ws.onmessage = (event) => {
        const message = parseMessage(String(event.data))
        if (!message) return

        if (message.type === 'pong') {
          // 收到心跳响应
          clearPongTimeout()
        } else if (message.type === 'error') {
          // 服务端错误消息(401/403/404等)
          error.value = message.message
        }

        // 交给页面注册的业务处理器
        messageHandler?.(message)
      }

      ws.onclose = () => {
        if (generation !== currentGeneration) return
        console.log('WebSocket连接关闭')
        connected.value = false
        stopHeartbeat()

        // 非手动断开时尝试重连
        if (!manuallyClosed && reconnectAttempts < maxReconnectAttempts) {
          attemptReconnect(userId, token, key)
        }
      }

      ws.onerror = () => {
        if (generation !== currentGeneration) return
        console.error('WebSocket连接错误')
        error.value = 'WebSocket连接错误'
      }
    } catch (err) {
      console.error('创建WebSocket失败:', err)
      error.value = '无法创建WebSocket连接'
    }
  }

  /**
   * 订阅用户数据(协议7.2)
   */
  const subscribe = (userId: string) => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({
        type: 'subscribe',
        userId
      }))
    }
  }

  /**
   * 取消订阅(协议7.3)
   */
  const unsubscribe = (userId: string) => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({
        type: 'unsubscribe',
        userId
      }))
    }
  }

  /**
   * 尝试重连(指数退避: 1s/2s/4s...最长30s)
   */
  const attemptReconnect = (userId: string, token?: string, key?: string) => {
    reconnecting.value = true
    reconnectAttempts++

    const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000)

    reconnectTimer = window.setTimeout(() => {
      console.log(`尝试重连... (第${reconnectAttempts}次)`)
      connect(userId, token, key)
    }, delay)
  }

  /**
   * 注册消息处理器(页面在连接成功后调用，重连后依然生效)
   */
  const setMessageHandler = (handler: ((message: WebSocketMessage) => void) | null) => {
    messageHandler = handler
  }

  /**
   * 断开连接
   */
  const disconnect = () => {
    manuallyClosed = true
    if (ws) {
      ws.close()
      ws = null
    }
    stopHeartbeat()
    if (reconnectTimer) {
      window.clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    connected.value = false
    reconnecting.value = false
    reconnectAttempts = 0
  }

  // 获取WebSocket实例
  const getWebSocket = () => ws

  return {
    connected,
    reconnecting,
    error,
    connect,
    subscribe,
    unsubscribe,
    disconnect,
    setMessageHandler,
    getWebSocket
  }
})
