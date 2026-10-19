import axios from 'axios'
import { clearAuth, getToken } from '../utils/auth'

// Separate adapter preserves structured conflicts and never redirects on 403.
export const boardHttp = axios.create({ baseURL: '/', timeout: 15000 })
export class BoardError extends Error {
  constructor(message, status, data) { super(message); this.status = status; this.data = data; this.errorCode = data?.errorCode }
}
boardHttp.interceptors.request.use(config => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
boardHttp.interceptors.response.use(response => {
  const body = response.data
  if (body?.code !== 200) throw new BoardError(body?.message || '请求失败', body?.code, body?.data)
  if (body.data?.errorCode) throw new BoardError(body.data.errorCode, 200, body.data)
  return body.data
}, error => {
  const status = error.response?.status
  if (status === 401) clearAuth()
  throw new BoardError(status === 401 ? '登录已失效，请重新登录' : status === 403 ? '当前身份没有此操作权限' : error.response?.data?.message || '请求未完成，请重试', status, error.response?.data?.data)
})
const publicRoot = '/api/skate/board-assistant'
const userRoot = '/api/user/board-plans'
const adminRoot = '/api/admin/board-assistant'
const keyPath = key => encodeURIComponent(key)
export const boardApi = {
  catalog: params => boardHttp.get(`${publicRoot}/catalog`, { params }),
  variant: key => boardHttp.get(`${publicRoot}/variants/${keyPath(key)}`),
  recommend: demand => boardHttp.post(`${publicRoot}/recommend`, demand),
  evaluate: payload => boardHttp.post(`${publicRoot}/evaluate`, payload),
  replacements: payload => boardHttp.post(`${publicRoot}/replacements`, payload),
  compare: plans => boardHttp.post(`${publicRoot}/compare`, { plans }),
  plans: params => boardHttp.get(userRoot, { params }),
  plan: key => boardHttp.get(`${userRoot}/${keyPath(key)}`),
  save: payload => boardHttp.post(userRoot, payload),
  rename: (key, payload) => boardHttp.patch(`${userRoot}/${keyPath(key)}`, payload),
  copy: (key, payload = {}) => boardHttp.post(`${userRoot}/${keyPath(key)}/copy`, payload),
  remove: key => boardHttp.delete(`${userRoot}/${keyPath(key)}`),
  records: (resource, params) => boardHttp.get(`${adminRoot}/${resource}`, { params }),
  createRecord: (resource, payload) => boardHttp.post(`${adminRoot}/${resource}`, payload),
  updateRecord: (resource, key, payload) => boardHttp.patch(`${adminRoot}/${resource}/${keyPath(key)}`, payload),
  activate: (resource, key, payload) => boardHttp.post(`${adminRoot}/${resource}/${keyPath(key)}/activation`, payload),
  trial: demand => boardHttp.post(`${adminRoot}/trial`, demand)
}
