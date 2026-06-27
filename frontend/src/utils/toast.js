const TOAST_EVENT = 'skate-toast'
let originalAlert = null
let alertBridgeInstalled = false

export function getToastEventName() {
  return TOAST_EVENT
}

export function notify(message, type = 'info') {
  const text = String(message || '').trim()
  if (!text || typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent(TOAST_EVENT, {
    detail: {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      message: text,
      type
    }
  }))
}

export function installToastAlertBridge() {
  if (typeof window === 'undefined' || alertBridgeInstalled) return
  originalAlert = window.alert
  window.alert = (message) => notify(message)
  alertBridgeInstalled = true
}

export function restoreToastAlertBridge() {
  if (typeof window === 'undefined' || !alertBridgeInstalled) return
  window.alert = originalAlert
  originalAlert = null
  alertBridgeInstalled = false
}
