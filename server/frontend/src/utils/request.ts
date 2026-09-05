import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types'

// 创建axios实例
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 秘钥认证请求(URL查询参数携带key)不附加Bearer Token，
    // 避免后端优先按Token解析而忽略秘钥(秘钥可能含特殊字符，统一走axios params编码)
    const useKeyAuth = Boolean(
      (config.params as Record<string, unknown> | undefined)?.key ||
        new URLSearchParams(config.url?.split('?')[1] || '').has('key')
    )

    // 从localStorage获取token
    const token = localStorage.getItem('token')
    if (token && !useKeyAuth) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器: 直接返回ApiResponse对象(而不是AxiosResponse)
service.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResponse
    const config = response.config as InternalAxiosRequestConfig & { ignore404?: boolean }

    // 如果返回的状态码为200/201,直接返回数据
    if (res.code === 200 || res.code === 201) {
      return res as unknown as AxiosResponse
    }

    // 历史数据查询等场景: 404(该日期无数据)属正常空状态,不弹错误提示,原样返回让调用方清空展示
    if (res.code === 404 && config.ignore404) {
      return res as unknown as AxiosResponse
    }

    // 其他状态码显示错误信息
    ElMessage.error(res.message || '请求失败')
    const err = new Error(res.message || '请求失败') as Error & { code?: number }
    err.code = res.code
    return Promise.reject(err)
  },
  (error) => {
    // 处理HTTP错误
    if (error.response) {
      const status = error.response.status
      switch (status) {
        case 401:
          ElMessage.error('未授权,请重新登录')
          localStorage.removeItem('token')
          localStorage.removeItem('user')
          window.location.href = '/login'
          break
        case 403:
          ElMessage.error('无权限访问')
          break
        case 404:
          ElMessage.error('请求的资源不存在')
          break
        case 500:
          ElMessage.error('服务器错误')
          break
        default:
          ElMessage.error(error.response.data?.message || '请求失败')
      }
    } else {
      ElMessage.error('网络错误,请检查网络连接')
    }
    return Promise.reject(error)
  }
)

/**
 * 扩展请求配置: ignore404 表示 404 业务码(如"该日期无数据")不作为错误提示
 */
export interface RequestConfig extends AxiosRequestConfig {
  ignore404?: boolean
}

/**
 * 类型化请求客户端
 *
 * 由于响应拦截器直接返回了 ApiResponse(而非 AxiosResponse)，
 * 因此这里将泛型定义为 Promise<T>，使调用方的返回值 res 即为 ApiResponse<T>，
 * 从而 res.data 直接是业务数据类型。
 */
const request = {
  get<T = unknown>(url: string, config?: RequestConfig): Promise<T> {
    return service.get(url, config as AxiosRequestConfig) as unknown as Promise<T>
  },
  post<T = unknown>(url: string, data?: unknown, config?: RequestConfig): Promise<T> {
    return service.post(url, data, config as AxiosRequestConfig) as unknown as Promise<T>
  }
}

export default request
