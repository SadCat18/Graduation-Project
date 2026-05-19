const TOKEN_KEY = 'skate_token'
const ROLE_KEY = 'skate_role'
const NAME_KEY = 'skate_name'
const AUTH_CHANGED_EVENT = 'auth-changed'

function emitAuthChanged() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event(AUTH_CHANGED_EVENT))
  }
}

export function setAuth(token, role, name) {
  localStorage.setItem(TOKEN_KEY, token || '')
  localStorage.setItem(ROLE_KEY, role || '')
  localStorage.setItem(NAME_KEY, name || '')
  emitAuthChanged()
}

export function clearAuth() {
  const clearByRules = (storage) => {
    if (!storage) return
    const keys = []
    for (let i = 0; i < storage.length; i += 1) {
      const key = storage.key(i)
      if (key) keys.push(key)
    }
    keys.forEach((key) => {
      const lowerKey = key.toLowerCase()
      if (
        lowerKey.startsWith('skate_') ||
        lowerKey.includes('token') ||
        lowerKey.includes('auth') ||
        lowerKey.includes('user')
      ) {
        storage.removeItem(key)
      }
    })
  }

  clearByRules(localStorage)
  clearByRules(sessionStorage)

  if (typeof document !== 'undefined' && document.cookie) {
    document.cookie.split(';').forEach((item) => {
      const eqIndex = item.indexOf('=')
      const name = (eqIndex > -1 ? item.slice(0, eqIndex) : item).trim()
      if (name) {
        document.cookie = `${name}=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/`
      }
    })
  }

  emitAuthChanged()
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function getRole() {
  return localStorage.getItem(ROLE_KEY) || ''
}

export function getName() {
  return localStorage.getItem(NAME_KEY) || ''
}

export function getAuthChangedEventName() {
  return AUTH_CHANGED_EVENT
}
