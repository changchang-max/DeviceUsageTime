import type { MockMethod } from 'vite-plugin-mock'

// 模拟验证码存储
const verificationCodes: Record<string, { code: string; expiredAt: number }> = {}

// 预设测试账号
const TEST_ACCOUNTS = {
  'test@example.com': {
    password: 'Test123456',
    userId: '12345',
    secretKey: 'TestKey123456789'
  }
}

export default [
  // 发送验证码
  {
    url: '/api/auth/sendcode',
    method: 'get',
    response: ({ query }: any) => {
      const { to } = query
      if (!to) {
        return {
          code: 400,
          message: '邮箱地址不能为空',
          data: null
        }
      }
      
      // 生成6位验证码
      const code = Math.floor(100000 + Math.random() * 900000).toString()
      verificationCodes[to] = {
        code,
        expiredAt: Date.now() + 5 * 60 * 1000 // 5分钟后过期
      }
      
      console.log(`[Mock] 验证码已发送到 ${to}: ${code}`)
      
      return {
        code: 200,
        message: '验证码已发送',
        data: null
      }
    }
  },
  
  // 用户注册
  {
    url: '/api/auth/register',
    method: 'post',
    response: ({ body }: any) => {
      const { email, password, code } = body
      
      // 检查是否使用测试账号邮箱
      if (TEST_ACCOUNTS[email as keyof typeof TEST_ACCOUNTS]) {
        return {
          code: 409,
          message: '该邮箱已被注册',
          data: null
        }
      }
      
      // 验证验证码
      const storedCode = verificationCodes[email]
      if (!storedCode || storedCode.code !== code) {
        return {
          code: 400,
          message: '验证码错误或已过期',
          data: null
        }
      }
      
      // 模拟注册成功
      const userId = Math.floor(10000 + Math.random() * 90000).toString()
      const secretKey = generateSecretKey()
      
      return {
        code: 201,
        message: '注册成功',
        data: {
          email,
          userId,
          secretKey,
          createdAt: new Date().toISOString()
        }
      }
    }
  },
  
  // 用户登录
  {
    url: '/api/auth/login',
    method: 'post',
    response: ({ body }: any) => {
      const { email, password } = body
      
      // 检查是否为测试账号
      const testAccount = TEST_ACCOUNTS[email as keyof typeof TEST_ACCOUNTS]
      if (testAccount) {
        // 验证测试账号密码
        if (password !== testAccount.password) {
          return {
            success: false,
            code: 401,
            message: '邮箱或密码错误',
            data: null
          }
        }
        
        // 测试账号登录成功
        console.log(`[Mock] 测试账号登录成功: ${email}`)
        return {
          success: true,
          code: 200,
          message: '登录成功',
          data: {
            email,
            token: 'mock_token_test_' + Date.now(),
            expiresIn: 604800 // 7天
          }
        }
      }
      
      // 普通账号模拟登录成功
      const token = 'mock_token_' + Date.now()
      
      return {
        success: true,
        code: 200,
        message: '登录成功',
        data: {
          email,
          token,
          expiresIn: 604800 // 7天
        }
      }
    }
  },
  
  // 退出登录
  {
    url: '/api/auth/logout',
    method: 'post',
    response: () => {
      return {
        success: true,
        code: 200,
        message: '退出成功'
      }
    }
  },
  
  // 刷新Token
  {
    url: '/api/auth/refresh',
    method: 'post',
    response: () => {
      return {
        success: true,
        code: 200,
        message: 'Token刷新成功',
        data: {
          token: 'mock_token_refreshed_' + Date.now(),
          expiresIn: 604800
        }
      }
    }
  },
  
  // 验证秘钥
  {
    url: '/api/auth/verify-key',
    method: 'get',
    response: ({ query }: any) => {
      const { key } = query
      
      if (!key || key.length < 10) {
        return {
          code: 401,
          message: '秘钥无效',
          data: null
        }
      }
      
      // 检查是否为测试秘钥
      if (key === 'TestKey123456789') {
        return {
          code: 200,
          message: '秘钥有效',
          data: {
            userId: '12345',
            userName: '测试用户'
          }
        }
      }
      
      return {
        code: 200,
        message: '秘钥有效',
        data: {
          userId: '12345',
          userName: '张三'
        }
      }
    }
  }
] as MockMethod[]

// 生成随机秘钥
function generateSecretKey(): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789$'
  let key = ''
  for (let i = 0; i < 16; i++) {
    key += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  return key
}
