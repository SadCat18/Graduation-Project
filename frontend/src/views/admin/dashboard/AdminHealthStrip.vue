<script setup>
import AppIcon from '../../../components/AppIcon.vue'

defineProps({
  health: { type: Object, required: true },
  pendingTotal: { type: Number, default: 0 },
  anomalyCount: { type: Number, default: 0 },
  updatedAt: { type: String, default: '' },
  rangeDays: { type: Number, default: 7 },
  loading: { type: Boolean, default: false }
})

defineEmits(['refresh', 'update:rangeDays'])
</script>

<template>
  <section class="health-strip" :class="`tone-${health.tone}`" aria-label="后台健康决策条">
    <div class="health-state">
      <span class="health-icon"><AppIcon :name="health.icon" :size="18" /></span>
      <span>
        <small>整体健康状态</small>
        <strong>{{ health.label }}</strong>
      </span>
    </div>
    <div class="health-fact">
      <small>待处理总数</small>
      <strong>{{ pendingTotal }}</strong>
    </div>
    <div class="health-fact">
      <small>异常趋势</small>
      <strong>{{ anomalyCount }} 项</strong>
    </div>
    <div class="health-range" role="group" aria-label="工作台时间范围">
      <small>时间范围</small>
      <span>
        <button type="button" :class="{ active: rangeDays === 7 }" :aria-pressed="rangeDays === 7" @click="$emit('update:rangeDays', 7)">近 7 天</button>
        <button type="button" :class="{ active: rangeDays === 30 }" :aria-pressed="rangeDays === 30" @click="$emit('update:rangeDays', 30)">近 30 天</button>
      </span>
    </div>
    <div class="health-update">
      <small>最近刷新</small>
      <span>{{ updatedAt || '尚未刷新' }}</span>
    </div>
    <button type="button" class="health-refresh" :disabled="loading" @click="$emit('refresh')">
      <AppIcon name="refresh" :size="15" />
      {{ loading ? '刷新中' : '刷新' }}
    </button>
  </section>
</template>

<style scoped>
.health-strip {
  --tone: #087a6d;
  border: 1px solid color-mix(in srgb, var(--tone) 28%, #dfe5e1);
  border-radius: 14px;
  padding: 12px 14px;
  background: color-mix(in srgb, var(--tone) 5%, #fff);
  display: grid;
  grid-template-columns: minmax(210px, 1.35fr) repeat(2, minmax(90px, .55fr)) minmax(144px, .9fr) minmax(130px, .8fr) auto;
  align-items: center;
  gap: 12px;
}

.tone-danger { --tone: #dc2626; }
.tone-warning { --tone: #f97316; }
.tone-success { --tone: #059669; }
.tone-neutral { --tone: #687067; }

.health-state,
.health-fact,
.health-range,
.health-update {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.health-state span:last-child,
.health-fact,
.health-range,
.health-update { display: grid; gap: 2px; }

.health-icon {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: var(--tone);
  color: #fff;
}

.health-strip small { color: #687067; font-size: 12px; }
.health-strip strong { color: #202a25; font-size: 16px; }
.health-fact strong { font-size: 22px; line-height: 1; }
.health-update span { color: #45514a; font-size: 13px; }
.health-range > span { display: inline-flex; gap: 4px; }
.health-range button { min-height: 28px; border: 1px solid #d7deda; border-radius: 8px; padding: 0 8px; background: #fff; color: #687067; font-size: 12px; font-weight: 700; }
.health-range button.active { border-color: #087a6d; background: #e8f4f1; color: #087a6d; }
.health-range button:focus-visible { outline: 3px solid rgba(8, 122, 109, .18); outline-offset: 1px; }

.health-refresh {
  min-height: 40px;
  border: 0;
  border-radius: 11px;
  padding: 0 14px;
  background: var(--tone);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  font-weight: 700;
}

@media (max-width: 760px) {
  .health-strip { grid-template-columns: 1fr 1fr; }
  .health-state, .health-range, .health-update { grid-column: 1 / -1; }
  .health-refresh { width: 100%; }
}
</style>
