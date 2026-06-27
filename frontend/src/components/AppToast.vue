<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { getToastEventName } from '../utils/toast'

const toasts = ref([])
const timers = new Map()

function removeToast(id) {
  toasts.value = toasts.value.filter(item => item.id !== id)
  const timer = timers.get(id)
  if (timer) {
    window.clearTimeout(timer)
    timers.delete(id)
  }
}

function onToast(event) {
  const detail = event.detail || {}
  const toast = {
    id: detail.id || `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    message: String(detail.message || '').trim(),
    type: detail.type || 'info'
  }
  if (!toast.message) return

  toasts.value = [...toasts.value.slice(-2), toast]
  timers.set(toast.id, window.setTimeout(() => removeToast(toast.id), 2800))
}

onMounted(() => {
  window.addEventListener(getToastEventName(), onToast)
})

onBeforeUnmount(() => {
  window.removeEventListener(getToastEventName(), onToast)
  timers.forEach(timer => window.clearTimeout(timer))
  timers.clear()
})
</script>

<template>
  <Teleport to="body">
    <TransitionGroup name="toast-rise" tag="div" class="toast-stack" aria-live="polite">
      <button
        v-for="toast in toasts"
        :key="toast.id"
        type="button"
        :class="['toast-item', `toast-${toast.type}`]"
        @click="removeToast(toast.id)"
      >
        {{ toast.message }}
      </button>
    </TransitionGroup>
  </Teleport>
</template>

<style scoped>
.toast-stack {
  position: fixed;
  right: 18px;
  bottom: max(24px, env(safe-area-inset-bottom));
  z-index: 1300;
  display: grid;
  gap: 10px;
  width: min(360px, calc(100vw - 24px));
  pointer-events: none;
}

.toast-item {
  pointer-events: auto;
  width: 100%;
  min-height: 46px;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.68);
  background: rgba(21, 25, 23, 0.94);
  color: #fff;
  box-shadow: 0 18px 38px rgba(18, 21, 19, 0.18);
  backdrop-filter: blur(16px) saturate(1.15);
  text-align: left;
  line-height: 1.45;
}

.toast-success {
  background: rgba(8, 122, 109, 0.94);
}

.toast-error {
  background: rgba(185, 28, 28, 0.94);
}

.toast-rise-enter-active,
.toast-rise-leave-active {
  transition: opacity 0.24s var(--motion-ease), transform 0.24s var(--motion-ease);
}

.toast-rise-enter-from,
.toast-rise-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.98);
}

@media (max-width: 980px) {
  .toast-stack {
    left: 12px;
    right: 12px;
    bottom: calc(86px + env(safe-area-inset-bottom));
    width: auto;
  }
}

@media (prefers-reduced-motion: reduce) {
  .toast-rise-enter-active,
  .toast-rise-leave-active {
    transition-duration: 0.01ms;
  }
}
</style>
