<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AdminEmptyState from './AdminEmptyState.vue'
import echarts from './echarts'

const props = defineProps({
  conversion: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

defineEmits(['refresh'])

const chartEl = ref(null)
let chart = null
let resizeObserver = null
const views = computed(() => Number(props.conversion?.views || 0))
const signs = computed(() => Number(props.conversion?.signs || 0))
const rate = computed(() => Number(props.conversion?.rate || 0))
const hasData = computed(() => views.value > 0 || signs.value > 0)

function renderChart() {
  if (!chartEl.value || !hasData.value) return
  if (!chart) chart = echarts.init(chartEl.value)
  chart.setOption({
    animationDuration: 360,
    color: ['#087A6D', '#F97316'],
    tooltip: { trigger: 'item', formatter: '{b}<br/>{c}', backgroundColor: '#17211c', borderWidth: 0, textStyle: { color: '#fff' } },
    series: [{
      type: 'funnel',
      left: '8%', top: 8, bottom: 6, width: '84%', minSize: '44%', maxSize: '100%',
      sort: 'descending', gap: 5,
      label: { show: true, position: 'inside', color: '#fff', fontWeight: 700, formatter: '{b}  {c}' },
      labelLine: { show: false },
      itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 5 },
      data: [{ name: '活动浏览', value: views.value }, { name: '完成报名', value: signs.value }]
    }]
  }, true)
}

watch(() => props.conversion, async () => { await nextTick(); renderChart() }, { deep: true })
onMounted(async () => {
  await nextTick(); renderChart()
  if (typeof ResizeObserver !== 'undefined' && chartEl.value) {
    resizeObserver = new ResizeObserver(() => chart?.resize())
    resizeObserver.observe(chartEl.value)
  }
})
onBeforeUnmount(() => { resizeObserver?.disconnect(); chart?.dispose() })
</script>

<template>
  <section class="dashboard-card funnel-panel" aria-labelledby="funnel-title">
    <header class="dashboard-section-head"><div><span class="dashboard-kicker">活动转化</span><h2 id="funnel-title">浏览到报名</h2></div><strong>{{ rate }}%</strong></header>
    <div v-if="loading" class="funnel-loading" role="status">正在加载转化数据</div>
    <AdminEmptyState v-else-if="error" icon="x" title="转化加载失败" :description="error" action-label="重新加载" @action="$emit('refresh')" />
    <AdminEmptyState v-else-if="!hasData" icon="activities" title="暂无转化数据" description="当前尚未记录活动浏览或报名。" action-label="刷新数据" @action="$emit('refresh')" />
    <div v-else ref="chartEl" class="funnel-chart" role="img" tabindex="0" :aria-label="`活动浏览 ${views}，报名 ${signs}，转化率 ${rate}%`"></div>
    <div class="funnel-summary"><span><i class="is-view"></i>浏览 <strong>{{ views }}</strong></span><span><i class="is-sign"></i>报名 <strong>{{ signs }}</strong></span></div>
  </section>
</template>

<style scoped>
.dashboard-card { height: 100%; border: 1px solid #dde3df; border-radius: 14px; padding: 14px; background: #fff; }
.dashboard-section-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 6px; }
.dashboard-kicker { display: inline-flex; margin-bottom: 5px; border-radius: 999px; padding: 3px 8px; background: #fff0e7; color: #c5530d; font-size: 12px; font-weight: 800; }
.dashboard-section-head h2 { margin: 0; color: #17211c; font-size: 17px; }
.dashboard-section-head > strong { color: #087a6d; font-size: 28px; line-height: 1; }
.funnel-chart { width: 100%; height: 230px; outline: none; }
.funnel-chart:focus-visible { border-radius: 10px; box-shadow: 0 0 0 3px rgba(8, 122, 109, .16); }
.funnel-summary { display: flex; justify-content: center; gap: 16px; color: #687067; font-size: 12px; }
.funnel-summary span { display: inline-flex; align-items: center; gap: 5px; }
.funnel-summary i { width: 8px; height: 8px; border-radius: 50%; }.is-view { background: #087a6d; }.is-sign { background: #f97316; }
.funnel-summary strong { color: #27322c; }
.funnel-loading { min-height: 220px; display: grid; place-content: center; color: #687067; font-size: 12px; }
@media (max-width: 760px) { .funnel-chart { min-height: 220px; } }
</style>
