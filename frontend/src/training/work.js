import { ref, reactive, onBeforeUnmount } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { getToken, getRole, getAuthChangedEventName } from '../utils/auth'
import { trainingApi, resultUnknown, trainingErrorText } from '../api/training'
export const clone = value => JSON.parse(JSON.stringify(value))
export const requestKey = () => crypto.randomUUID()
const auth = () => `${getRole()}:${getToken()}`
// Only a hash identifies the signed-in session; never persist its token.
export async function ownerTag(value) {
  const bytes = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value))
  return Array.from(new Uint8Array(bytes), byte => byte.toString(16).padStart(2, '0')).join('')
}
export function useTrainingWork(prefix) {
  const ready = ref(false), pending = ref(null), writing = ref(false), notice = ref(''), busy = reactive({}), errors = reactive({}), failure = ref(null)
  let epoch = 0, disposed = false, owner = '', storageKey = '', generations = {}
  const current = (generation, identity) => !disposed && epoch === generation && auth() === identity
  const persist = () => { if (!storageKey) throw new Error('训练会话尚未准备好'); if (pending.value) sessionStorage.setItem(storageKey, JSON.stringify(pending.value)); else sessionStorage.removeItem(storageKey) }
  function invalidate() { epoch++; generations = {}; for (const key of Object.keys(busy)) busy[key] = false; for (const key of Object.keys(errors)) if (!['write','session'].includes(key)) errors[key] = ''; if (!pending.value) { errors.write = ''; failure.value = null } }
  async function init() {
    invalidate(); const generation = epoch, identity = auth(); ready.value = false; pending.value = null
    if (getRole() !== (prefix.includes('/admin/') ? 'ADMIN' : 'USER')) { errors.session = '请使用对应的已登录身份进入此训练区域。'; return }
    try {
      const tag = await ownerTag(identity)
      if (!current(generation, identity)) return
      owner = identity; storageKey = `training-intent-v1:${prefix.includes('/admin/') ? 'admin' : 'user'}:${tag}`
      const saved = JSON.parse(sessionStorage.getItem(storageKey) || 'null')
      if (saved && ['POST', 'PUT'].includes(saved.method) && saved.path?.startsWith(prefix + '/') && /^[a-zA-Z0-9_-]{8,100}$/.test(saved.payload?.requestKey)) pending.value = { ...saved, status: 'unknown' }
      errors.session = ''; ready.value = true
    } catch { if (current(generation, identity)) errors.session = '无法保存操作恢复信息，请允许本页会话存储后重新进入。' }
  }
  function changed() { invalidate(); ready.value = false; pending.value = null; writing.value = false; notice.value = ''; failure.value = null; for (const key of Object.keys(errors)) delete errors[key] }
  window.addEventListener(getAuthChangedEventName(), changed)
  async function read(slot, run, commit, onError) {
    if (!ready.value || disposed) return
    const generation = epoch, identity = auth(), sequence = (generations[slot] || 0) + 1; generations[slot] = sequence
    const valid = () => current(generation, identity) && generations[slot] === sequence
    busy[slot] = true; errors[slot] = ''
    try { const data = await run(); if (valid()) { commit(data); return data } }
    catch (error) { if (valid()) { errors[slot] = trainingErrorText(error); failure.value = error; onError?.(error) } }
    finally { if (valid()) busy[slot] = false }
  }
  async function execute(commit) {
    if (!ready.value || writing.value || !pending.value || owner !== auth()) return
    const intent = clone(pending.value), generation = epoch, identity = auth()
    const valid = () => current(generation, identity)
    writing.value = true; errors.write = ''; failure.value = null; notice.value = ''
    try { pending.value.status = 'unknown'; persist() }
    catch { pending.value = null; writing.value = false; errors.write = '无法保留原操作，尚未发送，请允许会话存储后重试。'; return }
    try {
      const data = await trainingApi.send(intent)
      if (!valid()) return
      pending.value = null; try { persist() } catch { notice.value = '服务端已确认，但恢复信息未能清理，再次核对仍使用原操作。' }; writing.value = false; notice.value = '操作已由服务端确认。'; commit?.(data, intent); return data
    } catch (error) {
      if (!valid()) return
      failure.value = error; errors.write = trainingErrorText(error)
      if (!resultUnknown(error)) { pending.value = null; try { persist() } catch { /* A known rejected write has no committed result. */ } }
      else { pending.value = { ...intent, status: 'unknown' }; try { persist() } catch { errors.session = '原操作暂保留在本页，请勿关闭页面，先核对或重试。' }; notice.value = '本次结果未知，请核对并重试同一次操作；原内容已保留，勿另建重复记录。' }
    } finally { if (valid()) writing.value = false }
  }
  async function write(label, path, payload, commit, method = 'POST') {
    if (!ready.value || pending.value || writing.value) return
    pending.value = { label, path, method, payload: clone({ ...payload, requestKey: requestKey() }), status: 'unknown' }
    return execute(commit)
  }
  function unload(event) { if (writing.value || pending.value) { event.preventDefault(); event.returnValue = '' } }
  window.addEventListener('beforeunload', unload)
  onBeforeRouteLeave(() => { if (writing.value) { notice.value = '操作正在处理中，请等待结果后离开。'; return false } return true })
  onBeforeUnmount(() => { disposed = true; invalidate(); window.removeEventListener(getAuthChangedEventName(), changed); window.removeEventListener('beforeunload', unload) })
  return { ready, pending, writing, notice, busy, errors, failure, init, invalidate, cancel: slot => { generations[slot] = (generations[slot] || 0) + 1; busy[slot] = false; errors[slot] = '' }, read, write, retry: execute }
}
