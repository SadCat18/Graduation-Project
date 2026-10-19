<script setup>
import AppIcon from '../../../components/AppIcon.vue'

defineProps({
  items: { type: Array, default: () => [] }
})

defineEmits(['select'])
</script>

<template>
  <section class="dashboard-card quick-panel" aria-labelledby="quick-title">
    <header class="dashboard-section-head">
      <div>
        <span class="dashboard-kicker">快捷动作</span>
        <h2 id="quick-title">下一步去哪里</h2>
      </div>
    </header>
    <div class="quick-list">
      <button v-for="item in items" :key="item.key" type="button" @click="$emit('select', item)">
        <span class="quick-icon"><AppIcon :name="item.icon" :size="16" /></span>
        <span><strong>{{ item.label }}</strong><small>{{ item.shortDesc || item.desc }}</small></span>
        <em v-if="item.badge">{{ item.badge }}</em>
        <AppIcon name="arrow-right" :size="14" />
      </button>
    </div>
  </section>
</template>

<style scoped>
.dashboard-card { height: 100%; border: 1px solid #dde3df; border-radius: 14px; padding: 14px; background: #fff; }
.dashboard-section-head { margin-bottom: 12px; }
.dashboard-kicker { display: inline-flex; margin-bottom: 5px; border-radius: 999px; padding: 3px 8px; background: #e8f4f1; color: #087a6d; font-size: 12px; font-weight: 800; }
.dashboard-section-head h2 { margin: 0; color: #17211c; font-size: 17px; }
.quick-list { display: grid; gap: 8px; }
.quick-list button { min-height: 51px; border: 1px solid #e1e6e3; border-radius: 12px; padding: 8px 9px; background: #fbfcfb; color: #28332d; display: grid; grid-template-columns: auto minmax(0, 1fr) auto auto; align-items: center; gap: 8px; text-align: left; }
.quick-list button:hover, .quick-list button:focus-visible { border-color: #8eb9ae; background: #f2f8f6; }
.quick-icon { width: 31px; height: 31px; border-radius: 10px; display: grid; place-items: center; background: #e8f4f1; color: #087a6d; }
.quick-list button > span:nth-child(2) { min-width: 0; display: grid; gap: 1px; }
.quick-list strong { font-size: 13px; }
.quick-list small { overflow: hidden; color: #687067; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.quick-list em { min-width: 23px; border-radius: 999px; padding: 3px 7px; background: #f97316; color: #fff; font-size: 11px; font-style: normal; text-align: center; }
</style>
