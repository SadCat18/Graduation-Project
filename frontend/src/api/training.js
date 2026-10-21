import axios from 'axios'
import { getToken, clearAuth } from '../utils/auth'
const expireOwnRequest = config => { if (config?.headers?.Authorization === `Bearer ${getToken()}`) clearAuth() }
export const trainingHttp = axios.create({ baseURL: '/', timeout: 15000 })
export class TrainingError extends Error {
  constructor(message, status, data, transportCode) { super(message); this.status = status; this.data = data; this.errorCode = data?.errorCode; this.transportCode = transportCode }
}
trainingHttp.interceptors.request.use(config => { const token = getToken(); if (token) config.headers.Authorization = `Bearer ${token}`; return config }, undefined, { synchronous: true })
trainingHttp.interceptors.response.use(response => {
  const body = response.data
  if (body?.code === 401) expireOwnRequest(response.config)
  if (body?.code !== 200) throw new TrainingError(body?.message || '训练请求未完成', body?.code, body?.data)
  return body.data
}, error => {
  const response = error.response
  if (response?.status === 401) expireOwnRequest(error.config || response.config)
  throw new TrainingError(response?.data?.message || '连接未完成，操作结果待核对', response?.status, response?.data?.data, error.code)
})
export const USER_TRAINING = '/api/user/training-guidance'
export const ADMIN_TRAINING = '/api/admin/training-guidance'
export const trainingApi = {
  get: (path, params) => trainingHttp.get(path, { params }),
  preview: (path, data) => trainingHttp.post(path, data),
  explain: (path, data) => trainingHttp.post(path, data, { timeout: 60000 }),
  send: intent => trainingHttp.request({ method: intent.method, url: intent.path, data: intent.payload })
}
export const pathKey = value => encodeURIComponent(String(value))
export const resultUnknown = error => error?.status == null || error.status >= 500
export function trainingErrorText(error) {
  const messages = { PLAN_VERSION_CONFLICT: '计划已变化，请刷新后检查最新安排。', IDEMPOTENCY_CONFLICT: '这次操作与已提交内容不一致，请先核对记录并刷新。', SUGGESTION_EXPIRED: '建议已过期，请刷新，不能强行接受。', CONTENT_UNAVAILABLE: '教学内容暂不可用，请查看提醒或选择其他可用模板。', PLAN_CONFLICT: '已有未终结计划，请先核对当前或旧训练计划。', AI_UNAVAILABLE: 'AI解释暂不可用，已保存记录和固定指导不受影响。' }
  return messages[error?.errorCode] || (error?.status === 401 ? '登录已失效，请重新登录。' : error?.status === 403 ? '当前身份没有此操作权限。' : error?.status === 409 ? '内容或版本已变化，请刷新后核对。' : error?.status === 429 ? '请求过于频繁，请稍后手动重试。' : error?.status >= 500 ? '服务暂不可用，请手动核对或重试。' : error?.message || '操作未完成，请重试。')
}
