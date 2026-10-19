<script setup>
import AppIcon from '../../../components/AppIcon.vue'
import AdminEmptyState from './AdminEmptyState.vue'

defineProps({
  items: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

defineEmits(['select', 'refresh'])
</script>

<template>
  <section class="dashboard-card priority-panel" aria-labelledby="priority-title">
    <header class="dashboard-section-head">
      <div>
        <span class="dashboard-kicker is-risk">风险队列</span>
        <h2 id="priority-title">先处理最急的事项</h2>
      </div>
      <span class="section-hint">按风险与等待时间排序</span>
    </header>

    <div v-if="loading" class="priority-skeleton" aria-label="待办加载中">
      <span v-for="index in 4" :key="index"></span>
    </div>
    <AdminEmptyState
      v-else-if="!items.length"
      icon="notice"
      title="当前没有待办"
      description="审核、举报、场地和评价队列均已清理。"
      action-label="重新检查"
      @action="$emit('refresh')"
    />
    <div v-else class="priority-list">
      <button
        v-for="item in items"
        :key="item.key"
        type="button"
        :class="['priority-item', `tone-${item.tone}`]"
        @click="$emit('select', item.action)"
      >
        <span class="priority-icon"><AppIcon :name="item.icon" :size="17" /></span>
        <span class="priority-main">
          <span class="priority-title-row">
            <strong>{{ item.label }}</strong>
            <em>{{ item.riskLabel }}</em>
          </span>
          <small>{{ item.waitLabel }} · {{ item.detail }}</small>
        </span>
        <strong class="priority-count">{{ item.count }}</strong>
        <span class="priority-go">查看并处理 <span aria-hidden="true">→</span></span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.dashboard-card {
  border: 1px solid #dde3df;
  border-radius: 14px;
  padding: 14px;
  background: #fff;
}

.dashboard-section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.dashboard-kicker {
  display: inline-flex;
  margin-bottom: 5px;
  border-radius: 999px;
  padding: 3px 8px;
  background: #eef3f0;
  color: #526159;
  font-size: 12px;
  font-weight: 800;
}

.dashboard-kicker.is-risk { background: #fff0e7; color: #bb4e0b; }
.dashboard-section-head h2 { margin: 0; color: #17211c; font-size: 17px; }
.section-hint { color: #687067; font-size: 12px; }

.priority-list { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }

.priority-item {
  --tone: #687067;
  min-width: 0;
  min-height: 82px;
  border: 1px solid color-mix(in srgb, var(--tone) 24%, #dfe4e1);
  border-radius: 14px;
  padding: 11px;
  background: color-mix(in srgb, var(--tone) 4%, #fff);
  color: #28332d;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  text-align: left;
}

.priority-item.tone-danger { --tone: #dc2626; }
.priority-item.tone-warning { --tone: #f97316; }
.priority-item.tone-success { --tone: #059669; }
.priority-item.tone-neutral { --tone: #687067; }
.priority-item:hover, .priority-item:focus-visible { transform: translateY(-1px); box-shadow: 0 8px 20px rgba(23, 33, 28, .08); }

.priority-icon {
  width: 34px;
  height: 34px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  background: var(--tone);
  color: #fff;
}

.priority-main { min-width: 0; display: grid; gap: 5px; }
.priority-title-row { display: flex; align-items: center; gap: 7px; }
.priority-title-row strong { font-size: 14px; }
.priority-title-row em { border-radius: 999px; padding: 2px 7px; background: var(--tone); color: #fff; font-size: 10px; font-style: normal; }
.priority-main small { overflow: hidden; color: #687067; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.priority-count { color: var(--tone); font-size: 28px; line-height: 1; }
.priority-go { grid-column: 2 / -1; margin-top: -5px; color: var(--tone); font-size: 11px; font-weight: 700; }

.priority-skeleton { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.priority-skeleton span { height: 82px; border-radius: 14px; background: linear-gradient(90deg, #eef1ef, #f8faf9, #eef1ef); background-size: 200% 100%; animation: pulse 1.4s infinite; }
@keyframes pulse { to { background-position: -200% 0; } }

@media (max-width: 760px) {
  .priority-list, .priority-skeleton { grid-template-columns: 1fr; }
  .priority-item { min-height: 88px; }
}
</style>
