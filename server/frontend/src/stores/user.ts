import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User } from '@/types'
import { loginApi, registerApi, logoutApi, getUserProfileApi } from '@/api'

export const useUserStore = defineStore('user', () => {
  // 状态
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<User | null>(null)
  
  // 计算属性
  const isLoggedIn = computed(() => !!token.value)
  
  // 登录
  const login = async (email: string, password: string) => {
    const res = await loginApi({ email, password })
    token.value = res.data.token
    localStorage.setItem('token', res.data.token)
    
    // 登录成功后获取用户信息
    await fetchUserInfo()
  }
  
  // 注册
  const register = async (email: string, password: string, code: string) => {
    const res = await registerApi({ email, password, code })
    return res.data
  }
  
  // 获取用户信息
  const fetchUserInfo = async () => {
    try {
      const res = await getUserProfileApi()
      userInfo.value = res.data
    } catch (error) {
      console.error('获取用户信息失败:', error)
    }
  }
  
  // 退出登录
  const logout = async () => {
    try {
      await logoutApi()
    } catch (error) {
      console.error('退出登录失败:', error)
    } finally {
      token.value = ''
      userInfo.value = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
  
  // 更新秘钥
  const updateSecretKey = (newKey: string) => {
    if (userInfo.value) {
      userInfo.value.secretKey = newKey
    }
  }
  
  return {
    token,
    userInfo,
    isLoggedIn,
    login,
    register,
    fetchUserInfo,
    logout,
    updateSecretKey
  }
})
