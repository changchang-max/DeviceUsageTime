import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useWebSocketStore = defineStore('websocket', () => {
  // 状态
  const connected = ref(false)
  const reconnecting = ref(false)
  const error = ref<string | null>(null)
  
  // WebSocket实例
  let ws: WebSocket | null = null
  let heartbeatTimer: number | null = null
  let reconnectTimer: number | null = null
  let reconnectAttempts = 0
  const maxReconnectAttempts = 5
  
  // 连接WebSocket
  const connect = (userId: string, token?: string, key?: string) => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    const authParam = token ? `token=${token}` : `key=${key}`
    const wsUrl = `${protocol}//${host}/ws?${authParam}`
    
    try {
      ws = new WebSocket(wsUrl)
      
      ws.onopen = () => {
        console.log('WebSocket连接成功')
        connected.value = true
        reconnecting.value = false
        reconnectAttempts = 0
        error.value = null
        
        // 订阅用户数据
        subscribe(userId)
        
        // 启动心跳
        startHeartbeat()
      }
      
      ws.onclose = () => {
        console.log('WebSocket连接关闭')
        connected.value = false
        stopHeartbeat()
        
        // 尝试重连
        if (reconnectAttempts < maxReconnectAttempts) {
          attemptReconnect(userId, token, key)
        }
      }
      
      ws.onerror = (err) => {
        console.error('WebSocket错误:', err)
        error.value = 'WebSocket连接错误'
      }
    } catch (err) {
      console.error('创建WebSocket失败:', err)
      error.value = '无法创建WebSocket连接'
    }
  }
  
  // 订阅用户数据
  const subscribe = (userId: string) => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({
        type: 'subscribe',
        userId
      }))
    }
  }
  
  // 取消订阅
  const unsubscribe = (userId: string) => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({
        type: 'unsubscribe',
        userId
      }))
    }
  }
  
  // 启动心跳
  const startHeartbeat = () => {
    heartbeatTimer = window.setInterval(() => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'ping' }))
      }
    }, 30000) // 每30秒发送一次心跳
  }
  
  // 停止心跳
  const stopHeartbeat = () => {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }
  
  // 尝试重连
  const attemptReconnect = (userId: string, token?: string, key?: string) => {
    reconnecting.value = true
    reconnectAttempts++
    
    const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000)
    
    reconnectTimer = window.setTimeout(() => {
      console.log(`尝试重连... (第${reconnectAttempts}次)`)
      connect(userId, token, key)
    }, delay)
  }
  
  // 断开连接
  const disconnect = () => {
    if (ws) {
      ws.close()
      ws = null
    }
    stopHeartbeat()
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    connected.value = false
    reconnecting.value = false
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
    getWebSocket
  }
})
