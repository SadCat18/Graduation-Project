import axios from 'axios'
import { clearAuth, getToken } from '../utils/auth'

const http = axios.create({
  baseURL: '/',
  timeout: 10000
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body.code !== 200) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body.data
  },
  (error) => {
    if (error?.code === 'ECONNABORTED' || String(error?.message || '').includes('timeout')) {
      return Promise.reject(new Error('请求超时，请稍后重试'))
    }
    const status = error?.response?.status
    if ((status === 401 || status === 403) && getToken()) {
      clearAuth()
      if (!['/login', '/register'].includes(window.location.pathname)) {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export default http
