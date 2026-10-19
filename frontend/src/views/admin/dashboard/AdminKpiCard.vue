<script setup>
import { computed } from 'vue'
import AppIcon from '../../../components/AppIcon.vue'

const props = defineProps({
  item: { type: Object, required: true }
})

defineEmits(['select'])

const points = computed(() => {
  const values = props.item.series || []
  if (!values.length) return ''
  const width = 132
  const height = 34
  const min = Math.min(...values, 0)
  const max = Math.max(...values, 1)
  const range = Math.max(1, max - min)
  return values.map((value, index) => {
    const x = values.length === 1 ? width / 2 : (index * width) / (values.length - 1)
    const y = height - 2 - ((value - min) / range) * (height - 4)
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
})
</script>

<template>
  <button type="button" :class="['kpi-card', `tone-${item.tone}`]" @click="$emit('select', item.action)">
    <span class="kpi-head"><span><AppIcon :name="item.icon" :size="15" />{{ item.label }}</span><em>{{ item.deltaDisplay }}</em></span>
    <strong>{{ item.value }}</strong>
    <svg viewBox="0 0 132 34" preserveAspectRatio="none" aria-hidden="true"><polyline :points="points" /></svg>
    <small>{{ item.summary }}</small>
  </button>
</template>

<style scoped>
.kpi-card { --tone: #687067; min-width: 0; min-height: 136px; border: 1px solid #dde3df; border-top: 3px solid var(--tone); border-radius: 14px; padding: 12px; background: #fff; color: var(--tone); display: grid; gap: 6px; text-align: left; }
.tone-danger { --tone: #dc2626; background: #fffafa; }
.tone-warning { --tone: #f97316; background: #fffcf9; }
.tone-success { --tone: #059669; background: #fafffc; }
.tone-normal { --tone: #087a6d; }
.tone-neutral { --tone: #687067; }
.kpi-card:hover, .kpi-card:focus-visible { transform: translateY(-2px); box-shadow: 0 8px 20px rgba(23, 33, 28, .08); }
.kpi-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.kpi-head > span { display: inline-flex; align-items: center; gap: 6px; color: #4c5851; font-size: 12px; font-weight: 800; }
.kpi-head em { border-radius: 999px; padding: 2px 7px; background: var(--tone); color: #fff; font-size: 10px; font-style: normal; font-weight: 800; }
.kpi-card > strong { color: #16201b; font-size: 30px; line-height: 1; letter-spacing: -.03em; }
.kpi-card > svg { width: 100%; height: 34px; overflow: visible; }
.kpi-card polyline { fill: none; stroke: var(--tone); stroke-width: 2.2; stroke-linecap: round; stroke-linejoin: round; vector-effect: non-scaling-stroke; }
.kpi-card > small { color: #687067; font-size: 12px; }
</style>
