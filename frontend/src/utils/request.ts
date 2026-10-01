import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

// 规范:统一用 @/utils/request 实例,统一 baseURL + 拦截器,禁止直接 new Axios()
const service: AxiosInstance = axios.create({
  baseURL: '/',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json;charset=utf-8'
  }
})

// 请求拦截器
service.interceptors.request.use(
  (config: AxiosRequestConfig) => {
    // 可在此注入 token 等
    return config as any
  },
  (error) => Promise.reject(error)
)

// 后端统一返回格式
export interface ResponseBase<T = unknown> {
  code: string
  message: string
  data: T
}

// 响应拦截器:统一处理 ResponseBase,code="0" 为成功
service.interceptors.response.use(
  (response) => {
    const res = response.data as ResponseBase
    if (res && typeof res.code !== 'undefined') {
      if (res.code === '0') {
        return res.data as unknown as ResponseBase
      }
      // 业务失败
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return response.data
  },
  (error) => {
    const msg = error?.response?.data?.message || error.message || '网络异常'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export interface RequestOptions extends AxiosRequestConfig {
  // 预留扩展
}

// 封装请求,泛型 T 为业务数据类型
export function request<T = unknown>(config: RequestOptions): Promise<T> {
  return service.request<unknown, T>(config)
}

export default service
