// 通用响应类型
// data 允许为 null：后端在"该日期无数据"(404)等空状态下会返回 null
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

// 修改密码请求体(字段名与后端 ChangePasswordDTO 保持一致; 已登录时 user_email 可省略)
export interface ChangePasswordRequest {
  old_password: string
  new_password: string
}

// 注销账号请求体(字段名与后端 DeregisterDTO 保持一致)
export interface DeregisterRequest {
  user_password: string
}

// 忘记密码：发送验证码返回数据(后端 ForgotPasswordCodeVO)
export interface ForgotPasswordCodeData {
  user_email: string
  /** 验证码有效期(秒) */
  expires_in: number
}

// 忘记密码：重置密码请求体(字段名与后端 ForgotPasswordDTO 保持一致)
export interface ForgotPasswordRequest {
  user_email: string
  code: string
  new_password: string
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
