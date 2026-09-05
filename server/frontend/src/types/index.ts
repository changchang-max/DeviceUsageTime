// 通用响应类型
export interface ApiResponse<T = any> {
  success?: boolean
  code: number
  message: string
  data: T
}

// 用户相关类型(后端 UserProfileVO: 邮箱即全局唯一标识,无userId字段)
export interface User {
  userId?: string
  email: string
  secretKey: string
  createdAt: string
  lastLoginAt?: string
}

// 登录请求体(字段名与后端 LoginDTO / 数据库实体 user_email 保持一致)
export interface LoginRequest {
  user_email: string
  user_password: string
}

// 注册请求体(字段名与后端 register 接口保持一致)
export interface RegisterRequest {
  user_email: string
  user_password: string
  code: string
}

// 秘钥验证返回数据(后端 VerifyKeyVO)
export interface VerifyKeyData {
  userName: string
  token: string
}

// 导出所有类型
export * from './data'
export * from './websocket'
export * from './history'
