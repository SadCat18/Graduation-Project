<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import AchievementBadge from './AchievementBadge.vue'

const props = defineProps({
  badge: { type: Object, required: true },
  returnFocusTo: { type: Object, default: null },
  fallbackFocusTo: { type: Object, default: null }
})
const emit = defineEmits(['close'])
const dialog = ref(null)
const closeButton = ref(null)
let previouslyFocused = null
let previousOverflow = ''

function close() { emit('close') }
function canReceiveFocus(element) {
  return Boolean(element?.isConnected && typeof element.focus === 'function' && !element.disabled)
}
function focusableElements() {
  return [...(dialog.value?.querySelectorAll('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])') || [])]
    .filter(element => !element.disabled && element.getAttribute('aria-hidden') !== 'true')
}
function handleKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    close()
    return
  }
  if (event.key !== 'Tab') return
  const focusable = focusableElements()
  if (!focusable.length) {
    event.preventDefault()
    dialog.value?.focus()
    return
  }
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog.value)) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

onMounted(async () => {
  previouslyFocused = document.activeElement
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  window.addEventListener('keydown', handleKeydown)
  await nextTick()
  closeButton.value?.focus()
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  document.body.style.overflow = previousOverflow
  const focusTarget = [props.returnFocusTo, props.fallbackFocusTo, previouslyFocused].find(canReceiveFocus)
  focusTarget?.focus()
})
</script>

<template>
  <Teleport to="body">
    <div class="unlock-backdrop" @click.self="close">
      <section ref="dialog" class="unlock-dialog" :class="`rarity-${String(badge.rarity || 'COMMON').toLowerCase()}`" role="dialog" aria-modal="true" :aria-labelledby="`badge-unlock-title-${badge.code || 'new'}`" tabindex="-1">
        <div class="rays" aria-hidden="true"></div>
        <button ref="closeButton" class="modal-close" type="button" aria-label="关闭勋章解锁弹窗" @click="close">×</button>
        <p class="unlock-kicker">ACHIEVEMENT UNLOCKED</p>
        <AchievementBadge :badge="badge" :size="148" />
        <h2 :id="`badge-unlock-title-${badge.code || 'new'}`">{{ badge.name }}</h2>
        <p class="unlock-description">{{ badge.description }}</p>
        <button class="claim-button" type="button" @click="close">收下勋章</button>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.unlock-backdrop { --modal-gutter: clamp(10px, 3vh, 18px); position: fixed; inset: 0; z-index: 1000; display: grid; place-items: center; overflow-y: auto; padding: var(--modal-gutter); background: rgba(9, 12, 15, .76); backdrop-filter: blur(4px); }
.unlock-dialog { --modal-accent: #cbd5e1; box-sizing: border-box; position: relative; isolation: isolate; width: min(390px, 100%); max-height: calc(100dvh - var(--modal-gutter) - var(--modal-gutter)); overflow-x: hidden; overflow-y: auto; overscroll-behavior: contain; padding: 38px 28px 28px; border: 3px solid #17202a; border-radius: 12px 18px 10px 16px; color: #17202a; text-align: center; background: #f5efdf repeating-linear-gradient(112deg, transparent 0 22px, rgba(23,32,42,.035) 23px 24px); box-shadow: 8px 9px 0 var(--modal-accent), 12px 13px 0 #17202a; animation: sticker-in .34s cubic-bezier(.2,.8,.25,1.15) both; }
.rarity-common { --modal-accent: #cbd5e1; }
.rarity-rare { --modal-accent: #60a5fa; }
.rarity-epic { --modal-accent: #c084fc; }
.rarity-legendary { --modal-accent: #fbbf24; }
.rays { position: absolute; z-index: -1; width: 300px; height: 300px; top: 30px; left: 50%; translate: -50% 0; opacity: .17; background: repeating-conic-gradient(from 5deg, var(--modal-accent) 0 7deg, transparent 7deg 19deg); animation: rays-in .55s ease-out both; }
.modal-close { position: absolute; top: 10px; right: 12px; width: 34px; height: 34px; border: 2px solid #17202a; border-radius: 50%; color: #17202a; background: #fffdf7; font: 900 23px/1 sans-serif; }
.unlock-kicker { margin: 0 0 14px; font: 900 11px/1.2 Consolas, monospace; letter-spacing: .16em; }
.unlock-dialog h2 { margin: 15px 0 7px; font: 900 29px/1.1 "Microsoft YaHei", sans-serif; }
.unlock-description { min-height: 1.5em; margin: 0 0 20px; color: #555a5d; }
.claim-button { width: 100%; min-height: 46px; border: 2px solid #17202a; border-radius: 5px; color: #17202a; background: var(--modal-accent); box-shadow: 3px 3px 0 #17202a; font-weight: 900; }
button:focus-visible { outline: 3px solid var(--modal-accent); outline-offset: 3px; }
@keyframes sticker-in { from { opacity: 0; transform: translateY(18px) rotate(-2deg) scale(.92); } to { opacity: 1; transform: none; } }
@keyframes rays-in { from { opacity: 0; transform: scale(.65) rotate(-12deg); } }
@media (max-width: 440px) { .unlock-dialog { padding: 34px 20px 22px; } }
@media (prefers-reduced-motion: reduce) {
  .unlock-dialog, .rays, .claim-button, .modal-close { animation: none !important; transition: none !important; }
}
</style>
