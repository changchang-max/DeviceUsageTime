import { Application, Statistics } from './data'

// WebSocket消息类型
export type WebSocketMessageType =
  | 'subscribe'
  | 'unsubscribe'
  | 'realtime_update'
  | 'ping'
  | 'pong'
  | 'error'
  | 'connected'

// WebSocket订阅消息
export interface SubscribeMessage {
  type: 'subscribe'
  userId: string
}

// WebSocket取消订阅消息
export interface UnsubscribeMessage {
  type: 'unsubscribe'
  userId: string
}

// WebSocket实时更新消息
export interface RealtimeUpdateMessage {
  type: 'realtime_update'
  userId: string
  timestamp: string
  data: {
    applications: Application[]
    statistics: Statistics
  }
}

// WebSocket心跳消息
export interface PingMessage {
  type: 'ping'
}

export interface PongMessage {
  type: 'pong'
  timestamp: string
}

// WebSocket错误消息
export interface ErrorMessage {
  type: 'error'
  code: number
  message: string
}

// WebSocket连接成功消息
export interface ConnectedMessage {
  type: 'connected'
  message: string
  userId?: string
}

// 所有WebSocket消息的联合类型
export type WebSocketMessage =
  | SubscribeMessage
  | UnsubscribeMessage
  | RealtimeUpdateMessage
  | PingMessage
  | PongMessage
  | ErrorMessage
  | ConnectedMessage
