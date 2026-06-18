<script setup>
import { computed } from 'vue'
import { badgeIconPaths, fallbackBadgeIconPaths } from '../../constants/badges'

const props = defineProps({
  badge: { type: Object, required: true },
  size: { type: [Number, String], default: 76 },
  compact: { type: Boolean, default: false }
})

const rarityMeta = {
  COMMON: { label: '普通', color: '#cbd5e1' },
  RARE: { label: '稀有', color: '#60a5fa' },
  EPIC: { label: '史诗', color: '#c084fc' },
  LEGENDARY: { label: '传说', color: '#fbbf24' }
}
const rarity = computed(() => rarityMeta[props.badge?.rarity] || rarityMeta.COMMON)
const paths = computed(() => badgeIconPaths[props.badge?.iconCode] || fallbackBadgeIconPaths)
const dimension = computed(() => typeof props.size === 'number' ? `${props.size}px` : props.size)
const title = computed(() => `${props.badge?.name || '未知勋章'}，${rarity.value.label}${props.badge?.unlocked ? '，已解锁' : '，未解锁'}`)
</script>

<template>
  <figure class="achievement-badge" :class="{ locked: !badge.unlocked, compact }" :style="{ '--badge-color': rarity.color, '--badge-size': dimension }" :aria-label="title">
    <span class="badge-shadow" aria-hidden="true"></span>
    <span class="badge-sticker"><span class="badge-face">
      <svg viewBox="0 0 64 64" role="img" focusable="false">
        <title>{{ title }}</title>
        <path v-for="(path, index) in paths" :key="index" :d="path" fill="none" stroke="currentColor" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </span></span>
    <figcaption>{{ rarity.label }}</figcaption>
  </figure>
</template>

<style scoped>
.achievement-badge { position: relative; display: inline-grid; width: var(--badge-size); flex: 0 0 var(--badge-size); place-items: center; gap: 4px; margin: 0; color: #17202a; isolation: isolate; }
.badge-shadow, .badge-sticker, .badge-face { box-sizing: border-box; width: var(--badge-size); aspect-ratio: 1; clip-path: polygon(50% 0, 94% 18%, 86% 72%, 50% 100%, 14% 72%, 6% 18%); }
.badge-shadow { position: absolute; top: 5px; left: 5px; z-index: -1; background: var(--badge-color); opacity: .72; filter: drop-shadow(0 0 9px color-mix(in srgb, var(--badge-color) 48%, transparent)); }
.badge-sticker { display: grid; place-items: center; padding: 5px; background: #fffdf7; }
.badge-face { display: grid; place-items: center; width: 100%; border: 2px solid #17202a; background: var(--badge-color); }
.badge-face svg { width: 68%; height: 68%; transform: rotate(-2deg); }
figcaption { padding: 2px 7px; border: 1px solid #17202a; background: #fffdf7; font-size: 10px; font-weight: 900; line-height: 1.2; letter-spacing: .12em; transform: rotate(-2deg); }
.compact figcaption { display: none; }
.achievement-badge.locked { opacity: .48; filter: grayscale(1); }
.locked .badge-shadow { opacity: 0; filter: none; }
</style>
