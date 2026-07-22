import request from '@/utils/request'
import type { ApiResponse, LoginRequest, RegisterRequest, User } from '@/types'

// 发送验证码
export const sendCodeApi = (email: string) => {
  return request.get<ApiResponse>(`/auth/sendcode?to=${email}`)
}

// 用户注册
export const registerApi = (data: RegisterRequest) => {
  return request.post<ApiResponse<User>>('/auth/register', data)
}

// 用户登录
export const loginApi = (data: LoginRequest) => {
  return request.post<ApiResponse<{ email: string; token: string; expiresIn: number }>>('/auth/login', data)
}

// 退出登录
export const logoutApi = () => {
  return request.post<ApiResponse>('/auth/logout')
}

// 刷新Token
export const refreshTokenApi = () => {
  return request.post<ApiResponse<{ token: string; expiresIn: number }>>('/auth/refresh')
}

// 验证秘钥
export const verifyKeyApi = (key: string) => {
  return request.get<ApiResponse<{ userId: string; userName?: string }>>(`/auth/verify-key?key=${key}`)
}
