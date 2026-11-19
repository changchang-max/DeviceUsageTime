import request from '@/utils/request'
import type {
  ApiResponse,
  ChangePasswordRequest,
  DeregisterRequest,
  ForgotPasswordCodeData,
  ForgotPasswordRequest,
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

// 忘记密码：获取重置验证码(发送到注册邮箱, 3分钟内有效且仅可使用一次)
export const sendResetCodeApi = (email: string) => {
  return request.get<ApiResponse<ForgotPasswordCodeData>>('/auth/forgot-password/code', {
    params: { to: email }
  })
}

// 忘记密码：凭验证码重置密码(成功后该账号所有已登录凭证立即失效, 需重新登录)
export const resetPasswordApi = (data: ForgotPasswordRequest) => {
  return request.post<ApiResponse>('/auth/forgot-password/reset', data)
}
