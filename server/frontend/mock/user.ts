import type { MockMethod } from 'vite-plugin-mock'

export default [
  // 获取用户信息
  {
    url: '/api/user/profile',
    method: 'get',
    response: () => {
      return {
        code: 200,
        message: '获取成功',
        data: {
          userId: '12345',
          email: 'test@example.com',
          secretKey: 'TestKey123456789',
          createdAt: '2026-01-01T00:00:00Z',
          lastLoginAt: new Date().toISOString()
        }
      }
    }
  },
  
  // 重新生成秘钥
  {
    url: '/api/user/regenerate-key',
    method: 'post',
    response: () => {
      const newKey = generateSecretKey()
      console.log(`[Mock] 新秘钥已生成: ${newKey}`)
      return {
        success: true,
        code: 200,
        message: '秘钥已重新生成',
        data: {
          secretKey: newKey
        }
      }
    }
  },
  
  // 作废秘钥
  {
    url: '/api/user/revoke-key',
    method: 'post',
    response: () => {
      console.log('[Mock] 秘钥已作废')
      return {
        success: true,
        code: 200,
        message: '秘钥已作废'
      }
    }
  }
] as MockMethod[]

function generateSecretKey(): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789$'
  let key = ''
  for (let i = 0; i < 16; i++) {
    key += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  return key
}
