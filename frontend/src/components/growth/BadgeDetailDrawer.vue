<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import AchievementBadge from './AchievementBadge.vue'

const props = defineProps({
  badge: { type: Object, required: true },
  returnFocusTo: { type: Object, default: null }
})
const emit = defineEmits(['close'])
const dialog = ref(null)
const closeButton = ref(null)
let previouslyFocused = null
let previousOverflow = ''

const rarityLabel = computed(() => ({
  COMMON: '普通', RARE: '稀有', EPIC: '史诗', LEGENDARY: '传说'
})[props.badge?.rarity] || '普通')
const progress = computed(() => Math.min(100, Math.round(
  Number(props.badge?.current || 0) / Math.max(1, Number(props.badge?.target || 1)) * 100
)))
const formattedUnlockTime = computed(() => {
  const value = String(props.badge?.unlockTime || '').replace('T', ' ')
  return value || '时间未记录'
})

function close() {
  emit('close')
}

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
  const focusTarget = [props.returnFocusTo, previouslyFocused].find(canReceiveFocus)
  focusTarget?.focus()
})
</script>

<template>
  <Teleport to="body">
    <div class="badge-drawer-backdrop" @mousedown.self="close">
      <aside
        ref="dialog"
        class="badge-drawer"
        :class="`rarity-${String(badge.rarity || 'COMMON').toLowerCase()}`"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="`badge-detail-${badge.code || 'unknown'}`"
        tabindex="-1"
      >
        <button ref="closeButton" class="drawer-close" type="button" aria-label="关闭徽章详情" @click="close">×</button>
        <p class="drawer-kicker">BADGE ARCHIVE / 徽章档案</p>
        <AchievementBadge :badge="badge" :size="128" />
        <span class="rarity-label">{{ rarityLabel }}</span>
        <h2 :id="`badge-detail-${badge.code || 'unknown'}`">{{ badge.name }}</h2>
        <p class="drawer-description">{{ badge.description }}</p>

        <div v-if="badge.unlocked" class="archive-stamp">
          <strong>已收入图鉴</strong>
          <span>获得时间：{{ formattedUnlockTime }}</span>
        </div>
        <div v-else class="locked-progress">
          <div class="progress-copy"><span>当前进度</span><strong>{{ badge.current || 0 }} / {{ badge.target || 0 }}</strong></div>
          <div class="drawer-progress" role="progressbar" :aria-valuenow="progress" aria-valuemin="0" aria-valuemax="100">
            <span :style="{ width: `${progress}%` }"></span>
          </div>
          <p>完成条件后，这枚徽章会自动收入你的图鉴。</p>
        </div>
      </aside>
    </div>
  </Teleport>
</template>

<style scoped>
.badge-drawer-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
  overflow: hidden;
  background: rgba(9, 12, 15, 0.72);
  backdrop-filter: blur(4px);
}

.badge-drawer {
  --drawer-accent: #cbd5e1;
  position: relative;
  box-sizing: border-box;
  width: min(440px, 100%);
  max-height: 100dvh;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 54px 34px 34px;
  border-left: 3px solid #17202a;
  color: #17202a;
  background: #f5efdf repeating-linear-gradient(112deg, transparent 0 22px, rgba(23, 32, 42, 0.035) 23px 24px);
  box-shadow: -10px 0 0 var(--drawer-accent), -14px 0 0 #17202a;
  animation: drawer-in 0.28s cubic-bezier(0.22, 1, 0.36, 1) both;
}

.rarity-common { --drawer-accent: #cbd5e1; }
.rarity-rare { --drawer-accent: #60a5fa; }
.rarity-epic { --drawer-accent: #c084fc; }
.rarity-legendary { --drawer-accent: #fbbf24; }

.drawer-close {
  position: absolute;
  top: 16px;
  right: 18px;
  width: 38px;
  height: 38px;
  padding: 0;
  border: 2px solid #17202a;
  border-radius: 50%;
  color: #17202a;
  background: #fffdf7;
  font: 900 24px/1 sans-serif;
}

.drawer-kicker {
  margin: 0 0 24px;
  color: #68665f;
  font: 800 11px/1.2 Consolas, monospace;
  letter-spacing: 0.16em;
}

.rarity-label {
  display: inline-flex;
  margin-top: 18px;
  padding: 4px 10px;
  border: 2px solid #17202a;
  background: var(--drawer-accent);
  font-size: 12px;
  font-weight: 900;
}

.badge-drawer h2 {
  margin: 12px 0 8px;
  font: 900 32px/1.1 "Microsoft YaHei", sans-serif;
}

.drawer-description {
  margin: 0 0 24px;
  color: #555a5d;
}

.archive-stamp,
.locked-progress {
  padding: 18px;
  border: 2px solid #17202a;
  background: rgba(255, 255, 255, 0.58);
}

.archive-stamp {
  display: grid;
  gap: 7px;
  color: #176c4d;
  transform: rotate(-1deg);
}

.archive-stamp strong { font-size: 20px; }
.archive-stamp span,
.locked-progress p { font-size: 13px; }

.progress-copy {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.drawer-progress {
  height: 14px;
  padding: 2px;
  border: 2px solid #17202a;
  background: #fffdf7;
}

.drawer-progress span {
  display: block;
  height: 100%;
  background: var(--drawer-accent);
}

.locked-progress p { margin: 12px 0 0; color: #68665f; }
button:focus-visible { outline: 3px solid var(--drawer-accent); outline-offset: 3px; }

@keyframes drawer-in {
  from { opacity: 0; transform: translateX(28px); }
  to { opacity: 1; transform: translateX(0); }
}

@media (max-width: 640px) {
  .badge-drawer-backdrop { align-items: flex-end; }
  .badge-drawer {
    width: 100%;
    max-height: min(82dvh, 720px);
    padding: 48px 22px 28px;
    border: 3px solid #17202a;
    border-bottom: 0;
    border-radius: 22px 22px 0 0;
    box-shadow: 0 -8px 0 var(--drawer-accent), 0 -12px 0 #17202a;
  }
}

@media (prefers-reduced-motion: reduce) {
  .badge-drawer,
  .drawer-close { animation: none !important; transition: none !important; }
}
</style>
