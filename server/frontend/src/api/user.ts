import request from '@/utils/request'
import type { ApiResponse, User } from '@/types'

// 获取用户信息
export const getUserProfileApi = () => {
  return request.get<ApiResponse<User>>('/user/profile')
}

// 重新生成秘钥
export const regenerateKeyApi = () => {
  return request.post<ApiResponse<{ secretKey: string }>>('/user/regenerate-key')
}

// 作废秘钥
export const revokeKeyApi = () => {
  return request.post<ApiResponse>('/user/revoke-key')
}
