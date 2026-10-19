<script setup>
import { computed, ref } from 'vue'
import AdminFunnelChart from './AdminFunnelChart.vue'
import AdminHealthStrip from './AdminHealthStrip.vue'
import AdminKpiCard from './AdminKpiCard.vue'
import AdminPriorityQueue from './AdminPriorityQueue.vue'
import AdminQuickActions from './AdminQuickActions.vue'
import AdminRankList from './AdminRankList.vue'
import AdminTrendChart from './AdminTrendChart.vue'

const props = defineProps({
  stats: { type: Object, default: () => ({}) },
  analytics: { type: Object, default: () => ({}) },
  reviewActivities: { type: Array, default: () => [] },
  reviewBulletins: { type: Array, default: () => [] },
  reports: { type: Array, default: () => [] },
  pendingPlaces: { type: Array, default: () => [] },
  placeReviews: { type: Array, default: () => [] },
  quickActions: { type: Array, default: () => [] },
  newsPendingCount: { type: Number, default: 0 },
  updatedAt: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

const emit = defineEmits(['navigate', 'refresh'])

const rangeDays = ref(7)
const pendingActivities = computed(() => props.reviewActivities.filter(item => String(item?.reviewStatus || '0') === '0'))
const pendingBulletins = computed(() => props.reviewBulletins.filter(item => String(item?.status || '0') === '0'))
const currentTrend = computed(() => props.analytics?.[`trend${rangeDays.value}d`] || [])
const previousTrend = computed(() => props.analytics?.[`previousTrend${rangeDays.value}d`] || [])
const currentConversion = computed(() => rangeDays.value === 30
  ? (props.analytics?.activityConversion30d || {})
  : (props.analytics?.activityConversion || {}))
const currentSummary = computed(() => summarizeTrend(currentTrend.value))
const previousSummary = computed(() => summarizeTrend(previousTrend.value))
const reportHotspots = computed(() => aggregateReports(props.reports))

const priorityItems = computed(() => {
  const items = [
    queueItem({
      key: 'review', label: '审核待办', icon: 'notice',
      count: pendingActivities.value.length + pendingBulletins.value.length,
      source: [...pendingActivities.value, ...pendingBulletins.value],
      detail: `活动 ${pendingActivities.value.length} · 快讯 ${pendingBulletins.value.length}`,
      action: { tab: 'reviewCenter', reviewTab: 'all', statusFilter: 'pending' }
    }),
    queueItem({
      key: 'report', label: '举报处置', icon: 'admin', count: props.reports.length, source: props.reports,
      detail: reportHotspots.value.some(item => item.value > 1) ? '存在重复举报对象' : '核对对象与举报原因',
      forceDanger: reportHotspots.value.some(item => item.highRisk),
      action: { tab: 'reports', reportStatus: 'pending' }
    }),
    queueItem({
      key: 'place', label: '场地核实', icon: 'location', count: props.pendingPlaces.length, source: props.pendingPlaces,
      detail: '核实地址、简介与安全信息',
      action: { tab: 'reviewCenter', reviewTab: 'placeContribution', statusFilter: 'pending' }
    }),
    queueItem({
      key: 'review-place', label: '评价核查', icon: 'comments', count: props.placeReviews.length, source: props.placeReviews,
      detail: '结合场地上下文核查评价',
      action: { tab: 'reviewCenter', reviewTab: 'placeReview', statusFilter: 'pending' }
    })
  ]
  const weight = { danger: 0, warning: 1, success: 2, neutral: 3 }
  return items.sort((a, b) => weight[a.tone] - weight[b.tone] || b.waitMinutes - a.waitMinutes)
})

const pendingTotal = computed(() => priorityItems.value.reduce((sum, item) => sum + item.count, 0))
const trendComparisons = computed(() => ['newUsers', 'newPosts', 'newActivities', 'newSigns'].map(key => compareValue(currentSummary.value[key], previousSummary.value[key], rangeDays.value)))
const anomalyCount = computed(() => trendComparisons.value.filter(item => item.rate <= -20).length)
const health = computed(() => {
  if (priorityItems.value.some(item => item.tone === 'danger')) return { label: '需要立即处置', tone: 'danger', icon: 'notice' }
  if (pendingTotal.value > 0 || anomalyCount.value > 0) return { label: '存在待处理事项', tone: 'warning', icon: 'admin' }
  return { label: '运营正常', tone: 'success', icon: 'admin' }
})

const kpiItems = computed(() => [
  buildKpi('users', '用户总量', Number(props.stats?.userTotal || 0), 'users', 'newUsers', { tab: 'stats' }),
  buildKpi('content', '内容总量', Number(props.stats?.postTotal || 0) + Number(props.stats?.bulletinTotal || 0) + Number(props.stats?.newsTotal || 0), 'posts', 'newPosts', { tab: 'stats' }),
  buildKpi('activities', '活动总量', Number(props.stats?.activityTotal || 0), 'activities', 'newActivities', { tab: 'analytics' }),
  buildKpi('signs', '报名总量', Number(props.stats?.signTotal || 0), 'notice', 'newSigns', { tab: 'analytics' })
])

const quickItems = computed(() => props.quickActions.map(item => ({
  ...item,
  shortDesc: {
    review: '集中处理风险队列', posts: '筛选、AI 建议与批量操作', comments: '结合帖子上下文处理', news: '核对 AI 结果后发布'
  }[item.key],
  badge: {
    review: pendingTotal.value,
    news: props.newsPendingCount
  }[item.key] || ''
})))

const hotContentItems = computed(() => {
  const posts = (props.analytics?.hotPosts || []).map(item => ({
    key: `post-${item.postId}`, label: item.title || `帖子 #${item.postId}`,
    value: Number(item.hotScore ?? item.likeCount ?? 0), metric: '热度', action: { tab: 'posts' }
  }))
  const activities = (props.analytics?.hotActivities || []).map(item => ({
    key: `activity-${item.activityId}`, label: item.title || `活动 #${item.activityId}`,
    value: Number(item.signNum ?? item.signCount ?? 0), metric: '报名', action: { tab: 'activities' }
  }))
  return [...posts, ...activities].sort((a, b) => b.value - a.value).slice(0, 5)
})

const riskItems = computed(() => reportHotspots.value.slice(0, 5))
const updatedAtLabel = computed(() => formatTime(props.updatedAt))

function buildKpi(key, label, value, icon, trendKey, action) {
  const comparison = compareValue(currentSummary.value[trendKey], previousSummary.value[trendKey], rangeDays.value)
  return {
    key, label, value, icon, action,
    deltaDisplay: comparison.display,
    tone: comparison.tone,
    series: currentTrend.value.map(item => Number(item?.[trendKey] || 0)),
    summary: comparison.summary
  }
}

function navigate(action) {
  if (action?.action) emit('navigate', action.action)
  else emit('navigate', action)
}

function queueItem({ key, label, icon, count, source, detail, action, forceDanger = false }) {
  const waitMinutes = oldestWaitMinutes(source)
  const overdue = waitMinutes >= 24 * 60
  const tone = forceDanger || overdue ? 'danger' : count > 0 ? 'warning' : 'success'
  return {
    key, label, icon, count, detail, action, waitMinutes, tone,
    riskLabel: tone === 'danger' ? '高风险' : tone === 'warning' ? '待处理' : '已清理',
    waitLabel: count ? `最久等待 ${formatWait(waitMinutes)}` : '当前无待办'
  }
}

function oldestWaitMinutes(items) {
  const now = Date.now()
  return items.reduce((max, item) => {
    const timestamp = new Date(item?.createTime || item?.submitTime || '').getTime()
    if (!Number.isFinite(timestamp)) return max
    return Math.max(max, Math.max(0, Math.round((now - timestamp) / 60000)))
  }, 0)
}

function formatWait(minutes) {
  if (minutes >= 1440) return `${Math.floor(minutes / 1440)} 天`
  if (minutes >= 60) return `${Math.floor(minutes / 60)} 小时`
  return `${Math.max(1, minutes)} 分钟`
}

function summarizeTrend(rows) {
  const result = { newUsers: 0, newPosts: 0, newActivities: 0, newSigns: 0 }
  rows.forEach(row => Object.keys(result).forEach(key => { result[key] += Number(row?.[key] || 0) }))
  return result
}

function compareValue(current, previous, days = 7) {
  const now = Number(current || 0)
  const before = Number(previous || 0)
  if (before <= 0) {
    if (now <= 0) return { rate: 0, display: '—', tone: 'neutral', summary: '当前与上期均无新增' }
    return { rate: 100, display: '新增', tone: 'normal', summary: `近 ${days} 天新增 ${now}` }
  }
  const rate = Math.round(((now - before) / before) * 100)
  const display = `${rate > 0 ? '+' : ''}${rate}%`
  if (rate <= -20) return { rate, display, tone: 'danger', summary: `较上期明显下降，近 ${days} 天新增 ${now}` }
  if (rate < 0) return { rate, display, tone: 'warning', summary: `较上期小幅下降，近 ${days} 天新增 ${now}` }
  if (rate > 0) return { rate, display, tone: 'success', summary: `较上期改善，近 ${days} 天新增 ${now}` }
  return { rate, display, tone: 'neutral', summary: `与上期持平，近 ${days} 天新增 ${now}` }
}

function aggregateReports(reports) {
  const groups = new Map()
  reports.forEach(item => {
    const key = `${item?.targetType || 'UNKNOWN'}-${item?.targetId || item?.reportId}`
    const existing = groups.get(key) || {
      key, label: item?.targetTitle || `${item?.targetType || '内容'} #${item?.targetId || '-'}`,
      value: 0, metric: '举报', latest: '', highRisk: false, action: { tab: 'reports', reportStatus: 'pending' }
    }
    existing.value += 1
    existing.latest = [existing.latest, item?.createTime || ''].sort().reverse()[0]
    const text = `${item?.reason || ''} ${item?.detail || ''}`.toLowerCase()
    existing.highRisk = existing.highRisk || ['违法违规', '人身攻击', '安全', '暴力', 'harassment', 'unsafe'].some(word => text.includes(word))
    groups.set(key, existing)
  })
  return [...groups.values()]
    .map(item => ({ ...item, highRisk: item.highRisk || item.value > 1 }))
    .sort((a, b) => Number(b.highRisk) - Number(a.highRisk) || b.value - a.value || String(b.latest).localeCompare(String(a.latest)))
}

function formatTime(value) {
  const date = new Date(value || '')
  if (!Number.isFinite(date.getTime())) return '尚未刷新'
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(date)
}
</script>

<template>
  <div class="admin-dashboard">
    <AdminHealthStrip class="dashboard-health" :health="health" :pending-total="pendingTotal" :anomaly-count="anomalyCount" :updated-at="updatedAtLabel" :range-days="rangeDays" :loading="loading" @update:range-days="rangeDays = $event" @refresh="$emit('refresh')" />
    <AdminPriorityQueue class="dashboard-priority" :items="priorityItems" :loading="loading" @select="navigate" @refresh="$emit('refresh')" />
    <AdminQuickActions class="dashboard-quick" :items="quickItems" @select="navigate" />
    <section class="dashboard-kpis" aria-label="核心运营指标">
      <AdminKpiCard v-for="item in kpiItems" :key="item.key" :item="item" @select="navigate" />
    </section>
    <AdminTrendChart class="dashboard-trend" :rows="currentTrend" :range-days="rangeDays" :loading="loading" :error="error" @refresh="$emit('refresh')" />
    <AdminFunnelChart class="dashboard-funnel" :conversion="currentConversion" :loading="loading" :error="error" @refresh="$emit('refresh')" />
    <AdminRankList class="dashboard-hot" title="热门内容排行" kicker="内容排行" icon="posts" :items="hotContentItems" empty-title="暂无热门内容" @refresh="$emit('refresh')" @select="navigate" />
    <AdminRankList class="dashboard-risk" title="举报热点排行" kicker="风险对象" icon="notice" tone="danger" :items="riskItems" empty-title="暂无举报热点" @refresh="$emit('refresh')" @select="navigate" />
  </div>
</template>

<style scoped>
.admin-dashboard { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); gap: 14px; }
.dashboard-health, .dashboard-kpis { grid-column: 1 / -1; }
.dashboard-priority, .dashboard-trend { grid-column: span 8; }
.dashboard-quick, .dashboard-funnel { grid-column: span 4; }
.dashboard-hot, .dashboard-risk { grid-column: span 6; }
.dashboard-kpis { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }

@media (max-width: 980px) {
  .admin-dashboard > * { grid-column: 1 / -1; }
  .dashboard-health { order: 0; }
  .dashboard-priority { order: 1; }
  .dashboard-kpis { order: 2; }
  .dashboard-trend { order: 3; }
  .dashboard-funnel { order: 4; }
  .dashboard-quick { order: 5; }
  .dashboard-hot { order: 6; }
  .dashboard-risk { order: 7; }
}

@media (max-width: 760px) {
  .admin-dashboard { grid-template-columns: 1fr; gap: 12px; }
  .dashboard-kpis { grid-template-columns: 1fr; }
}
</style>
