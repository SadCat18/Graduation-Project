import { nextTick } from 'vue'

export function focusContent(element, scroll = true) {
  if (!element?.isConnected) return
  element.setAttribute('tabindex', '-1')
  element.style.scrollMarginTop = `${(document.querySelector('.topbar')?.getBoundingClientRect().height || 0) + 20}px`
  element.focus({ preventScroll: true })
  if (scroll) element.scrollIntoView({ block: 'start', behavior: 'instant' })
}

// RouterView transitions and deferred homepage content can mount after navigation.
// Resolve one navigation intent once, cancel it on the next navigation.
let pending = null
export function cancelPageFocus() { pending?.disconnect(); pending = null }
export async function focusRoute(to, from, savedPosition) {
  cancelPageFocus()
  if (savedPosition) return savedPosition
  if (to.path === from.path && !to.hash && to.path !== '/skate') return false
  await nextTick()
  const target = () => {
    if (to.hash) { try { return document.getElementById(decodeURIComponent(to.hash.slice(1))) } catch { return null } }
    const page = [...document.querySelectorAll('[data-route-path]')].find(el => el.getAttribute('data-route-path') === to.path)
    return page?.querySelector('h1, h2, h3')
  }
  const apply = () => { const el = target(); if (!el) return false; cancelPageFocus(); focusContent(el); return true }
  if (!apply()) { pending = new MutationObserver(apply); pending.observe(document.querySelector('.main-container') || document.body, { childList: true, subtree: true }) }
  return to.hash ? false : { left: 0, top: 0 }
}
