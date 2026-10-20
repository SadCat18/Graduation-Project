<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import AdminEmptyState from './AdminEmptyState.vue'
import echarts from './echarts'

const props = defineProps({
  items: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

defineEmits(['refresh'])

const chartEl = ref(null)
let chart = null
let chartElement = null
let resizeObserver = null
const rows = computed(() => props.items.map(item => ({ name: item.type || item.name || '未分类', value: Number(item.count ?? item.value ?? 0) })).filter(item => item.value > 0))
const total = computed(() => rows.value.reduce((sum, item) => sum + item.value, 0))

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
  if (!chartEl.value || !rows.value.length) return
  chart.setOption({
    animationDuration: 360,
    color: ['#087A6D', '#2563EB', '#F97316', '#7C3AED', '#059669', '#687067'],
    tooltip: { renderMode: 'richText', trigger: 'item', formatter: '{b}\n{c} 条 · {d}%', backgroundColor: '#17211c', borderWidth: 0, textStyle: { color: '#fff' } },
    legend: { type: 'scroll', orient: 'vertical', right: 4, top: 'middle', itemWidth: 10, itemHeight: 10, textStyle: { color: '#56615b', fontSize: 11 } },
    series: [{
      type: 'pie', radius: ['52%', '76%'], center: ['34%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: '#fff', borderWidth: 3, borderRadius: 5 },
      label: { show: false }, emphasis: { label: { show: true, formatter: '{d}%', fontSize: 16, fontWeight: 800 } },
      data: rows.value
    }],
    graphic: [{ type: 'text', left: '27%', top: '45%', style: { text: `${total.value}\n总计`, textAlign: 'center', fill: '#26312b', fontSize: 13, fontWeight: 700, lineHeight: 18 } }]
  }, true)
}

watch([chartEl, rows, () => props.loading, () => props.error], renderChart, { deep: true, flush: 'post' })
onBeforeUnmount(disposeChart)
</script>

<template>
  <section class="dashboard-card donut-panel" aria-labelledby="donut-title">
    <header><span>分类占比</span><h2 id="donut-title">快讯内容结构</h2></header>
    <div v-if="loading" class="donut-loading" role="status">正在加载分类数据</div>
    <AdminEmptyState v-else-if="error" icon="x" title="分类加载失败" :description="error" action-label="重新加载" @action="$emit('refresh')" />
    <AdminEmptyState v-else-if="!rows.length" icon="stats" title="暂无分类数据" description="当前没有可统计的快讯分类。" action-label="刷新数据" @action="$emit('refresh')" />
    <div v-else ref="chartEl" class="donut-chart" role="img" tabindex="0" :aria-label="`快讯分类共 ${total} 条，${rows.map(item => `${item.name} ${item.value} 条`).join('，')}`"></div>
  </section>
</template>

<style scoped>
.dashboard-card { border: 1px solid #dde3df; border-radius: 14px; padding: 14px; background: #fff; }
header span { display: inline-flex; margin-bottom: 5px; border-radius: 999px; padding: 3px 8px; background: #eef3f0; color: #687067; font-size: 12px; font-weight: 800; }
header h2 { margin: 0; color: #17211c; font-size: 17px; }
.donut-chart { width: 100%; height: 260px; outline: none; }.donut-chart:focus-visible { border-radius: 10px; box-shadow: 0 0 0 3px rgba(8, 122, 109, .16); }
.donut-loading { min-height: 220px; display: grid; place-content: center; color: #687067; font-size: 12px; }
@media (max-width: 760px) { .donut-chart { min-height: 220px; } }
</style>
