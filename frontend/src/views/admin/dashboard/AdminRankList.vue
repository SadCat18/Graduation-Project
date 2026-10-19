<script setup>
import { computed } from 'vue'
import AppIcon from '../../../components/AppIcon.vue'
import AdminEmptyState from './AdminEmptyState.vue'

const props = defineProps({
  title: { type: String, required: true },
  kicker: { type: String, default: '内容排行' },
  icon: { type: String, default: 'stats' },
  items: { type: Array, default: () => [] },
  tone: { type: String, default: 'normal' },
  emptyTitle: { type: String, default: '暂无排行数据' }
})

defineEmits(['refresh', 'select'])

const maxValue = computed(() => Math.max(1, ...props.items.map(item => Number(item.value || 0))))
</script>

<template>
  <section :class="['dashboard-card', 'rank-panel', `tone-${tone}`]">
    <header class="dashboard-section-head"><div><span class="dashboard-kicker">{{ kicker }}</span><h2>{{ title }}</h2></div><AppIcon :name="icon" :size="18" /></header>
    <AdminEmptyState v-if="!items.length" :icon="icon" :title="emptyTitle" description="当前范围内没有可排行的记录。" action-label="刷新数据" @action="$emit('refresh')" />
    <ol v-else class="rank-list">
      <li v-for="(item, index) in items.slice(0, 5)" :key="item.key || index" @click="$emit('select', item)">
        <span class="rank-index">{{ index + 1 }}</span>
        <span class="rank-main"><strong>{{ item.label }}</strong><span><i :style="{ width: `${Math.max(8, Math.round((Number(item.value || 0) / maxValue) * 100))}%` }"></i></span></span>
        <span class="rank-value"><strong>{{ item.value }}</strong><small>{{ item.metric }}</small></span>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.dashboard-card { --tone: #087a6d; height: 100%; border: 1px solid #dde3df; border-radius: 14px; padding: 14px; background: #fff; }
.tone-danger { --tone: #dc2626; }.tone-warning { --tone: #f97316; }.tone-success { --tone: #059669; }
.dashboard-section-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 10px; color: var(--tone); }
.dashboard-kicker { display: inline-flex; margin-bottom: 5px; border-radius: 999px; padding: 3px 8px; background: color-mix(in srgb, var(--tone) 9%, #fff); color: var(--tone); font-size: 12px; font-weight: 800; }
.dashboard-section-head h2 { margin: 0; color: #17211c; font-size: 17px; }
.rank-list { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; }
.rank-list li { display: grid; grid-template-columns: 26px minmax(0, 1fr) auto; align-items: center; gap: 9px; padding: 7px 4px; border-bottom: 1px solid #edf0ee; }
.rank-list li:last-child { border-bottom: 0; }.rank-list li:hover { background: #fafcfb; }
.rank-index { width: 24px; height: 24px; border-radius: 8px; display: grid; place-items: center; background: color-mix(in srgb, var(--tone) 10%, #fff); color: var(--tone); font-size: 11px; font-weight: 900; }
.rank-main { min-width: 0; display: grid; gap: 6px; }.rank-main > strong { overflow: hidden; color: #344039; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.rank-main > span { height: 5px; border-radius: 999px; background: #edf1ef; overflow: hidden; }.rank-main i { display: block; height: 100%; border-radius: inherit; background: var(--tone); }
.rank-value { display: grid; justify-items: end; }.rank-value strong { color: #26312b; font-size: 14px; }.rank-value small { color: #687067; font-size: 10px; }
</style>
