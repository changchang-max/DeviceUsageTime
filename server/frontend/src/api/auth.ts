import request from '@/utils/request'
import type {
  ApiResponse,
  ChangePasswordRequest,
  DeregisterRequest,
  LoginRequest,
  RegisterRequest,
  VerifyKeyData
} from '@/types'

// 发送验证码(后端映射路径为 /auth/sendCode)
export const sendCodeApi = (email: string) => {
  return request.get<ApiResponse>('/auth/sendCode', {
    params: { to: email }
  })
}

// 用户注册
export const registerApi = (data: RegisterRequest) => {
  return request.post<ApiResponse>('/auth/register', data)
}

// 用户登录(后端 data 直接返回 token 字符串)
export const loginApi = (data: LoginRequest) => {
  return request.post<ApiResponse<string>>('/auth/login', data)
}

// 退出登录
export const logoutApi = () => {
  return request.post<ApiResponse>('/auth/logout')
}

// 验证秘钥(后端返回 userName 与 ROLE_VISITOR 角色的 token; 秘钥可能含特殊字符,走 params 编码)
export const verifyKeyApi = (key: string) => {
  return request.get<ApiResponse<VerifyKeyData>>('/auth/verify-key', {
    params: { key }
  })
}

// 修改密码(已登录时从Token解析用户身份; 修改成功后当前Token立即失效,需重新登录)
export const changePasswordApi = (data: ChangePasswordRequest) => {
  return request.post<ApiResponse>('/auth/change-password', data)
}

// 注销账号(永久删除当前账号及全部关联数据,不可恢复; 需输入登录密码二次确认)
export const deregisterApi = (data: DeregisterRequest) => {
  return request.post<ApiResponse>('/auth/deregister', data)
}
