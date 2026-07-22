// 通用响应类型
export interface ApiResponse<T = any> {
  success?: boolean
  code: number
  message: string
  data: T
}

// 用户相关类型
export interface User {
  userId: string
  email: string
  secretKey: string
  createdAt: string
  lastLoginAt?: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  code: string
}

// 导出所有类型
export * from './data'
export * from './websocket'
export * from './history'
