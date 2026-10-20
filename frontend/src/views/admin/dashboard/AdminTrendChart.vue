<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import AppIcon from '../../../components/AppIcon.vue'
import AdminEmptyState from './AdminEmptyState.vue'
import echarts from './echarts'

const props = defineProps({
  rows: { type: Array, default: () => [] },
  rangeDays: { type: Number, default: 7 },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

defineEmits(['refresh'])

const chartEl = ref(null)
let chart = null
let chartElement = null
let resizeObserver = null
const hasData = computed(() => props.rows.length > 0)
const summary = computed(() => {
  if (!props.rows.length) return `近 ${props.rangeDays} 天暂无运营趋势数据。`
  const latest = props.rows[props.rows.length - 1]
  return `最近一天新增用户 ${Number(latest.newUsers || 0)}，帖子 ${Number(latest.newPosts || 0)}，活动 ${Number(latest.newActivities || 0)}，报名 ${Number(latest.newSigns || 0)}。`
})

function disposeChart() {
  resizeObserver?.disconnect()
  resizeObserver = null
  chart?.dispose()
  chart = null
  chartElement = null
}

function renderChart() {
  if (chartEl.value !== chartElement) {
    disposeChart()
    chartElement = chartEl.value
    if (chartElement) {
      chart = echarts.init(chartElement)
      if (typeof ResizeObserver !== 'undefined') {
        const instance = chart
        resizeObserver = new ResizeObserver(() => { if (chart === instance) instance.resize() })
        resizeObserver.observe(chartElement)
      }
    }
  }
  if (!chartEl.value || !hasData.value) return
  chart.setOption({
    animationDuration: 360,
    color: ['#087A6D', '#2563EB', '#F97316', '#7C3AED'],
    tooltip: { renderMode: 'richText', trigger: 'axis', backgroundColor: '#17211c', borderWidth: 0, textStyle: { color: '#fff' } },
    legend: { top: 0, right: 0, itemWidth: 16, itemHeight: 4, textStyle: { color: '#5e6963', fontSize: 11 } },
    grid: { left: 8, right: 8, top: 38, bottom: 4, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: props.rows.map(item => String(item.date || '').slice(5)),
      axisLine: { lineStyle: { color: '#d9dfdb' } },
      axisTick: { show: false },
      axisLabel: { color: '#687067', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#edf0ee' } },
      axisLabel: { color: '#879089', fontSize: 10 }
    },
    series: [
      ['新增用户', 'newUsers'], ['新增帖子', 'newPosts'], ['发布活动', 'newActivities'], ['活动报名', 'newSigns']
    ].map(([name, key]) => ({
      name,
      type: 'line',
      smooth: 0.32,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { width: 2.5 },
      emphasis: { focus: 'series' },
      data: props.rows.map(item => Number(item[key] || 0))
    }))
  }, true)
}

watch([chartEl, () => props.rows, () => props.loading, () => props.error], renderChart, { deep: true, flush: 'post' })
onBeforeUnmount(disposeChart)
</script>

<template>
  <section class="dashboard-card trend-panel" aria-labelledby="trend-chart-title">
    <header class="dashboard-section-head">
      <div><span class="dashboard-kicker">趋势与异常</span><h2 id="trend-chart-title">近 {{ rangeDays }} 天运营趋势</h2></div>
      <span class="chart-legend-hint"><AppIcon name="stats" :size="14" /> 悬浮或点击查看日期明细</span>
    </header>
    <div v-if="loading" class="chart-loading" role="status"><span></span><p>正在加载趋势数据</p></div>
    <AdminEmptyState v-else-if="error" icon="x" title="趋势加载失败" :description="error" action-label="重新加载" @action="$emit('refresh')" />
    <AdminEmptyState v-else-if="!hasData" icon="stats" title="暂无趋势数据" description="当前时间范围内没有运营新增记录。" action-label="刷新数据" @action="$emit('refresh')" />
    <div v-else ref="chartEl" class="trend-chart" role="img" tabindex="0" :aria-label="summary"></div>
    <p class="chart-text-summary">{{ summary }}</p>
  </section>
</template>

<style scoped>
.dashboard-card { border: 1px solid #dde3df; border-radius: 14px; padding: 14px; background: #fff; }
.dashboard-section-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 6px; }
.dashboard-kicker { display: inline-flex; margin-bottom: 5px; border-radius: 999px; padding: 3px 8px; background: #e8f4f1; color: #087a6d; font-size: 12px; font-weight: 800; }
.dashboard-section-head h2 { margin: 0; color: #17211c; font-size: 17px; }
.chart-legend-hint { display: inline-flex; align-items: center; gap: 5px; color: #687067; font-size: 11px; }
.trend-chart { width: 100%; height: 260px; outline: none; }
.trend-chart:focus-visible { border-radius: 10px; box-shadow: 0 0 0 3px rgba(8, 122, 109, .16); }
.chart-text-summary { margin: 4px 0 0; color: #687067; font-size: 12px; }
.chart-loading { min-height: 220px; display: grid; place-content: center; justify-items: center; gap: 9px; color: #687067; }
.chart-loading span { width: 28px; height: 28px; border: 3px solid #d8e3df; border-top-color: #087a6d; border-radius: 50%; animation: spin .8s linear infinite; }
.chart-loading p { margin: 0; font-size: 12px; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 760px) { .trend-chart { min-height: 220px; } .chart-legend-hint { display: none; } }
</style>
