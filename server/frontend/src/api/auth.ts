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

/*
 * 说明：登录/注册/验证码/找回密码这些匿名接口统一带 skipAuth: true。
 *
 * 原因：后端 JwtAuthenticationFilter 只要发现 Authorization 头就会先解析校验，
 * Token 过期/被吊销/格式异常时直接返回 401/403，请求根本到不了 permitAll 白名单。
 * 用户在浏览器里残留了旧的 token 时，就会出现"有的人能注册、有的人提示无权限"。
 * 匿名接口不带 Token 即可彻底规避该问题。
 */

// 发送验证码(后端映射路径为 /auth/sendCode)
export const sendCodeApi = (email: string) => {
  return request.get<ApiResponse>('/auth/sendCode', {
    params: { to: email },
    skipAuth: true
  })
}

// 用户注册
export const registerApi = (data: RegisterRequest) => {
  return request.post<ApiResponse>('/auth/register', data, { skipAuth: true })
}

// 用户登录(后端 data 直接返回 token 字符串)
export const loginApi = (data: LoginRequest) => {
  return request.post<ApiResponse<string>>('/auth/login', data, { skipAuth: true })
}

// 退出登录
export const logoutApi = () => {
  return request.post<ApiResponse>('/auth/logout')
}

// 验证秘钥(后端返回 userName 与 ROLE_VISITOR 角色的 token; 秘钥可能含特殊字符,走 params 编码)
export const verifyKeyApi = (key: string) => {
  return request.get<ApiResponse<VerifyKeyData>>('/auth/verify-key', {
    params: { key },
    skipAuth: true
  })
}

// 修改密码(已登录时从Token解析用户身份; 修改成功后当前Token立即失效,需重新登录)
// 该接口同时支持未登录场景(请求体传 user_email)，Token 失效时也应放行，故不附加 Token
export const changePasswordApi = (data: ChangePasswordRequest) => {
  return request.post<ApiResponse>('/auth/change-password', data, { skipAuth: true })
}

// 注销账号(永久删除当前账号及全部关联数据,不可恢复; 需输入登录密码二次确认)
export const deregisterApi = (data: DeregisterRequest) => {
  return request.post<ApiResponse>('/auth/deregister', data)
}

// 忘记密码：获取重置验证码(发送到注册邮箱, 3分钟内有效且仅可使用一次)
export const sendResetCodeApi = (email: string) => {
  return request.get<ApiResponse<ForgotPasswordCodeData>>('/auth/forgot-password/code', {
    params: { to: email },
    skipAuth: true
  })
}

// 忘记密码：凭验证码重置密码(成功后该账号所有已登录凭证立即失效, 需重新登录)
export const resetPasswordApi = (data: ForgotPasswordRequest) => {
  return request.post<ApiResponse>('/auth/forgot-password/reset', data, { skipAuth: true })
}