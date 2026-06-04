<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import AiLoadingOverlay from '../components/AiLoadingOverlay.vue'
import { api } from '../api'
import { BULLETIN_TYPES } from '../constants/bulletin'

const tab = ref('stats')
const reviewCenterTab = ref('all')
const contentTab = ref('notice')
const reviewStatusFilter = ref('all')
const reviewTypeFilter = ref('all')
const reviewTimeFilter = ref('all')
const reviewKeyword = ref('')
const selectedReviewIds = ref([])
const reviewDrawerVisible = ref(false)
const reviewDrawerId = ref('')
const moderationLoadingKey = ref('')
const moderationResultKey = ref('')
const moderationResult = ref(null)
const postModerationLoadingId = ref(null)
const postModerationResultId = ref(null)
const postModerationResult = ref(null)
const ADMIN_PAGE_SIZE = 10
const stats = ref({})
const analytics = ref({
  trend7d: [],
  hotPosts: [],
  hotActivities: [],
  bulletinTypeRatio: []
})
const users = ref([])
const posts = ref([])
const comments = ref([])
const activities = ref([])
const activityReviewFilter = ref('0')
const notices = ref([])
const bulletins = ref([])
const reviewBulletins = ref([])
const bulletinTypeFilter = ref('全部')
const bulletinStatusFilter = ref('全部')
const newsStatusFilter = ref('全部')
const bulletinTypeStats = ref([])
const newsList = ref([])
const newsDetailVisible = ref(false)
const newsDetailLoading = ref(false)
const newsReprocessLoading = ref(false)
const newsSyncLoading = ref(false)
const newsSyncMode = ref('')
const newsReviewLoading = ref(false)
const newsSyncSummary = ref(null)
const activeNewsDetail = ref(null)
const places = ref([])
const placeReviews = ref([])
const banners = ref([])
const videos = ref([])
const reports = ref([])
const reviewActivities = ref([])
const loadedTabs = reactive({
  stats: false,
  analytics: false,
  reviewCenter: false,
  users: false,
  posts: false,
  comments: false,
  activities: false,
  reports: false,
  content: false
})
const pageState = reactive({
  users: { page: 1, total: 0 },
  posts: { page: 1, total: 0 },
  comments: { page: 1, total: 0 },
  activities: { page: 1, total: 0 }
})
const contentPageState = reactive({
  notices: 1,
  news: 1,
  places: 1,
  banners: 1,
  videos: 1
})
const contentTabs = [
  { key: 'notice', label: '公告' },
  { key: 'news', label: '资讯' },
  { key: 'place', label: '场地' },
  { key: 'banner', label: '轮播图' },
  { key: 'video', label: '视频' }
]
const newsCategoryOptions = ['赛事资讯', '装备动态', '技巧教学', '社区动态', '官方公告', '品牌资讯', '未分类']

const noticeForm = reactive({ title: '', content: '', status: '0' })
const newsForm = reactive({
  title: '',
  content: '',
  summary: '',
  category: '',
  cover: '',
  sourceName: '',
  sourceUrl: ''
})
const newsEditForm = reactive({
  newsId: null,
  title: '',
  content: '',
  summary: '',
  category: '',
  cover: '',
  sourceName: '',
  sourceUrl: '',
  originTitle: '',
  originContent: '',
  originSummary: '',
  aiTitle: '',
  aiSummary: '',
  aiCategory: '',
  aiTranslatedContent: '',
  status: '0',
  aiStatus: '',
  aiErrorMessage: '',
  syncTime: ''
})
const placeForm = reactive({ name: '', address: '', intro: '', score: 4.5 })
const bannerForm = reactive({ title: '', imageUrl: '', linkUrl: '/community', sortNum: 0, intervalSeconds: 5, status: '0' })
const bannerFileInputRef = ref(null)
const bannerUploading = ref(false)
const formErrors = reactive({
  notice: { title: '', content: '' },
  news: { title: '', content: '' },
  place: { name: '', address: '', score: '' },
  banner: { title: '', linkUrl: '', sortNum: '', intervalSeconds: '', imageUrl: '' }
})

const navItems = [
  { key: 'stats', label: '数据统计', icon: 'stats' },
  { key: 'analytics', label: '运营分析', icon: 'stats' },
  { key: 'reviewCenter', label: '审核中心', icon: 'notice' },
  { key: 'users', label: '用户管理', icon: 'users' },
  { key: 'posts', label: '帖子管理', icon: 'posts' },
  { key: 'comments', label: '评论管理', icon: 'comments' },
  { key: 'activities', label: '活动管理', icon: 'activities' },
  { key: 'reports', label: '举报管理', icon: 'notice' },
  { key: 'content', label: '内容管理', icon: 'content' }
]

const statCards = computed(() => [
  { key: 'userTotal', label: '用户总数', icon: 'users', value: Number(stats.value.userTotal || 0) },
  { key: 'postTotal', label: '帖子总数', icon: 'posts', value: Number(stats.value.postTotal || 0) },
  { key: 'commentTotal', label: '评论总数', icon: 'comments', value: Number(stats.value.commentTotal || 0) },
  { key: 'activityTotal', label: '活动总数', icon: 'activities', value: Number(stats.value.activityTotal || 0) },
  { key: 'noticeTotal', label: '公告总数', icon: 'notice', value: Number(stats.value.noticeTotal || 0) },
  { key: 'bulletinTotal', label: '快讯总数', icon: 'news', value: Number(stats.value.bulletinTotal || 0) },
  { key: 'newsTotal', label: '资讯总数', icon: 'news', value: Number(stats.value.newsTotal || 0) },
  { key: 'bannerTotal', label: '轮播总数', icon: 'banner', value: Number(stats.value.bannerTotal || 0) }
])

const maxStatValue = computed(() => Math.max(...statCards.value.map(item => item.value), 1))
const statBarData = computed(() => statCards.value.map(item => ({
  ...item,
  width: `${Math.round((item.value / maxStatValue.value) * 100)}%`
})))
const trendBarMax = computed(() => {
  const rows = analytics.value?.trend7d || []
  const maxValue = rows.reduce((acc, item) => {
    const values = [
      Number(item.newUsers || 0),
      Number(item.newPosts || 0),
      Number(item.newActivities || 0),
      Number(item.newSigns || 0)
    ]
    return Math.max(acc, ...values)
  }, 1)
  return Math.max(1, maxValue)
})
const bulletinRatioMax = computed(() => {
  const rows = analytics.value?.bulletinTypeRatio || []
  return Math.max(1, ...rows.map(item => Number(item.count || 0)))
})
const usersTotalPages = computed(() => Math.max(1, Math.ceil((pageState.users.total || 0) / ADMIN_PAGE_SIZE)))
const postsTotalPages = computed(() => Math.max(1, Math.ceil((pageState.posts.total || 0) / ADMIN_PAGE_SIZE)))
const commentsTotalPages = computed(() => Math.max(1, Math.ceil((pageState.comments.total || 0) / ADMIN_PAGE_SIZE)))
const activitiesTotalPages = computed(() => Math.max(1, Math.ceil((pageState.activities.total || 0) / ADMIN_PAGE_SIZE)))
const noticesTotalPages = computed(() => Math.max(1, Math.ceil((notices.value.length || 0) / ADMIN_PAGE_SIZE)))
const filteredNewsList = computed(() => {
  if (newsStatusFilter.value === '全部') return newsList.value
  return newsList.value.filter(item => String(item?.status || '0') === newsStatusFilter.value)
})
const newsAuditStats = computed(() => {
  const stats = { pending: 0, approved: 0, rejected: 0 }
  newsList.value.forEach((item) => {
    const status = String(item?.status || '0')
    if (status === '1') stats.approved += 1
    else if (status === '2') stats.rejected += 1
    else stats.pending += 1
  })
  return stats
})
const newsTotalPages = computed(() => Math.max(1, Math.ceil((filteredNewsList.value.length || 0) / ADMIN_PAGE_SIZE)))
const placesTotalPages = computed(() => Math.max(1, Math.ceil((places.value.length || 0) / ADMIN_PAGE_SIZE)))
const bannersTotalPages = computed(() => Math.max(1, Math.ceil((banners.value.length || 0) / ADMIN_PAGE_SIZE)))
const videosTotalPages = computed(() => Math.max(1, Math.ceil((videos.value.length || 0) / ADMIN_PAGE_SIZE)))
const pagedNotices = computed(() => sliceByPage(notices.value, contentPageState.notices))
const pagedNews = computed(() => sliceByPage(filteredNewsList.value, contentPageState.news))
const pagedPlaces = computed(() => sliceByPage(places.value, contentPageState.places))
const pagedBanners = computed(() => sliceByPage(banners.value, contentPageState.banners))
const pagedVideos = computed(() => sliceByPage(videos.value, contentPageState.videos))
const bulletinFilterOptions = computed(() => ['全部', ...BULLETIN_TYPES])
const bulletinStatusOptions = [
  { label: '全部', value: '全部' },
  { label: '待审核', value: '0' },
  { label: '已通过', value: '1' },
  { label: '已驳回', value: '2' }
]
const newsStatusOptions = [
  { label: '全部', value: '全部' },
  { label: '待审核', value: '0' },
  { label: '已通过', value: '1' },
  { label: '已驳回', value: '2' }
]
const reviewCenterTabs = [
  { key: 'all', label: '全部待办' },
  { key: 'activity', label: '活动' },
  { key: 'bulletin', label: '快讯' },
  { key: 'report', label: '举报' },
  { key: 'placeReview', label: '场地评价' },
  { key: 'done', label: '已处理' }
]
const moderationSteps = ['抽取正文', '分析元信息', '识别风险点', '判断风险等级', '整理审核建议']
let newsSyncPollTimer = null

function fmtTime(value) {
  return String(value || '').replace('T', ' ') || '-'
}

function cleanNewsText(value) {
  const raw = String(value || '').trim()
  if (!raw) return ''
  const lower = raw.toLowerCase()
  const isInstagramEmbed = lower.includes('instagram-media') || lower.includes('instagram.com/embed.js')
  const decoded = decodeHtmlEntities(raw)
  const withoutScript = decoded
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/p>/gi, '\n')
    .replace(/<[^>]+>/g, ' ')
  const cleaned = decodeHtmlEntities(withoutScript)
    .replace(/\u00a0/g, ' ')
    .replace(/[ \t\r\f\v]+/g, ' ')
    .replace(/ *\n */g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
  if (isInstagramEmbed && (!cleaned || cleaned.length > 180)) {
    return '这条资讯来自 Instagram 嵌入内容，请点击原文链接查看完整内容。'
  }
  return cleaned
}

function cleanInlineNewsText(value) {
  return cleanNewsText(value).replace(/\s+/g, ' ').trim()
}

function decodeHtmlEntities(value) {
  const textarea = document.createElement('textarea')
  textarea.innerHTML = String(value || '')
  return textarea.value
}

function newsStatusLabel(status) {
  if (status === '1') return '已通过'
  if (status === '2') return '已驳回'
  return '待审核'
}

function newsStatusClass(status) {
  if (status === '1') return 'is-approved'
  if (status === '2') return 'is-rejected'
  return 'is-pending'
}

function newsAiStatusLabel(status) {
  if (status === 'SUCCESS') return 'AI 成功'
  if (status === 'PARTIAL') return 'AI 部分失败'
  if (status === 'FAILED') return 'AI 失败'
  if (status === 'SKIPPED') return 'AI 跳过'
  return 'AI 状态未知'
}

function newsAuditHint(status) {
  if (status === '1') return '该资讯已审核通过，前台可见'
  if (status === '2') return '该资讯已驳回，前台不会展示'
  return '该资讯为待审核状态，前台不会展示'
}

function isApprovedStatus(item) {
  if (item.type === 'placeReview') return false
  return item.status === '1'
}

function isRejectedStatus(item) {
  if (item.type === 'placeReview') return false
  return item.status === '2'
}

function toTimestamp(value) {
  const t = new Date(value || '').getTime()
  return Number.isFinite(t) ? t : 0
}

function keywordMatch(item, keyword) {
  const k = String(keyword || '').trim().toLowerCase()
  if (!k) return true
  const text = [
    item.title,
    item.actor,
    item.summary,
    item.searchText
  ].join(' ').toLowerCase()
  return text.includes(k)
}

const pendingCounts = computed(() => {
  const activity = filteredReviewCards.value.filter(item => item.type === 'activity').length
  const bulletin = filteredReviewCards.value.filter(item => item.type === 'bulletin').length
  const report = filteredReviewCards.value.filter(item => item.type === 'report').length
  const placeReview = filteredReviewCards.value.filter(item => item.type === 'placeReview').length
  return { activity, bulletin, report, placeReview }
})

const reviewCards = computed(() => {
  const activityCards = reviewActivities.value.map(item => ({
    id: `activity-${item.activityId}`,
    type: 'activity',
    typeLabel: '活动',
    status: item.reviewStatus,
    statusLabel: activityReviewLabel(item),
    title: item.title || `活动 #${item.activityId}`,
    actor: item.publisherName || item.username || '未知发布人',
    time: fmtTime(item.createTime),
    summary: item.content || item.description || '暂无活动简介',
    fullContent: item.content || item.description || '暂无正文',
    imageUrl: item.cover || item.imageUrl || '',
    historyNote: item.rejectReason || '',
    searchText: `${item.reason || ''} ${item.publisherName || ''} ${item.username || ''}`,
    facts: [
      { label: '活动时间', value: `${fmtTime(item.startTime)} ~ ${fmtTime(item.endTime)}` },
      { label: '地点', value: item.address || '未填写' },
      { label: '报名人数', value: `${Number(item.signNum || 0)} / ${item.maxNum || '不限'}` },
      { label: '审核状态', value: activityReviewLabel(item) },
      { label: '活动状态', value: activityStatusLabel(item) }
    ],
    detailFacts: [
      { label: '活动ID', value: String(item.activityId || '-') },
      { label: '活动时间', value: `${fmtTime(item.startTime)} ~ ${fmtTime(item.endTime)}` },
      { label: '地点', value: item.address || '未填写' },
      { label: '报名人数', value: `${Number(item.signNum || 0)} / ${item.maxNum || '不限'}` },
      { label: '审核状态', value: activityReviewLabel(item) },
      { label: '活动状态', value: activityStatusLabel(item) }
    ],
    moderationPayload: {
      contentType: 'ACTIVITY',
      title: item.title || `活动 #${item.activityId}`,
      content: item.content || item.description || '暂无正文',
      extraInfo: [
        item.address ? `地点：${item.address}` : '',
        item.activityType ? `活动类型：${item.activityType}` : '',
        item.publisherName || item.username ? `发布人：${item.publisherName || item.username}` : '',
        item.createTime ? `发布时间：${fmtTime(item.createTime)}` : ''
      ].filter(Boolean).join('；')
    }
  }))
  const bulletinCards = reviewBulletins.value.map(item => ({
    id: `bulletin-${item.bulletinId}`,
    type: 'bulletin',
    typeLabel: '快讯',
    status: item.status,
    statusLabel: item.status === '0' ? '待审核' : (item.status === '1' ? '已通过' : '已驳回'),
    title: item.title || `快讯 #${item.bulletinId}`,
    actor: item.publisherName || '未知发布人',
    time: fmtTime(item.createTime),
    summary: item.content || '暂无摘要',
    fullContent: item.content || '暂无正文',
    imageUrl: item.cover || item.imageUrl || '',
    historyNote: item.rejectReason || '',
    searchText: `${item.bulletinType || ''} ${item.publisherName || ''}`,
    facts: [
      { label: '快讯分类', value: item.bulletinType || '未分类' },
      { label: '发布人', value: item.publisherName || '未知发布人' },
      { label: '发布时间', value: fmtTime(item.createTime) }
    ],
    detailFacts: [
      { label: '快讯ID', value: String(item.bulletinId || '-') },
      { label: '快讯分类', value: item.bulletinType || '未分类' },
      { label: '发布人', value: item.publisherName || '未知发布人' },
      { label: '发布时间', value: fmtTime(item.createTime) }
    ],
    moderationPayload: {
      contentType: 'BULLETIN',
      title: item.title || `快讯 #${item.bulletinId}`,
      content: item.content || '暂无正文',
      extraInfo: [
        item.bulletinType ? `快讯分类：${item.bulletinType}` : '',
        item.publisherName ? `发布人：${item.publisherName}` : '',
        item.createTime ? `发布时间：${fmtTime(item.createTime)}` : ''
      ].filter(Boolean).join('；')
    }
  }))
  const reportCards = reports.value.map(item => ({
    id: `report-${item.reportId}`,
    type: 'report',
    typeLabel: '举报',
    status: item.status,
    statusLabel: item.statusLabel || (item.status === '0' ? '待处理' : '已处理'),
    title: item.targetTitle || `举报 #${item.reportId}`,
    actor: item.reporterName || '未知举报人',
    time: fmtTime(item.createTime),
    summary: item.detail || item.handleNote || '无补充说明',
    fullContent: item.detail || item.targetContent || item.handleNote || '暂无正文',
    imageUrl: item.targetCover || '',
    historyNote: item.handleNote || '',
    searchText: `${item.reason || ''} ${item.detail || ''} ${item.reporterName || ''} ${item.targetTitle || ''}`,
    facts: [
      { label: '举报对象', value: `${item.targetType || '未知'} / ${item.targetTitle || '内容已删除'}` },
      { label: '举报原因', value: item.reason || '未填写' },
      { label: '举报人', value: item.reporterName || '未知举报人' },
      { label: '补充说明', value: item.detail || '无' },
      { label: '处理状态', value: item.statusLabel || (item.status === '0' ? '待处理' : '已处理') }
    ],
    detailFacts: [
      { label: '举报ID', value: String(item.reportId || '-') },
      { label: '举报对象', value: `${item.targetType || '未知'} / ${item.targetTitle || '内容已删除'}` },
      { label: '举报原因', value: item.reason || '未填写' },
      { label: '举报人', value: item.reporterName || '未知举报人' },
      { label: '补充说明', value: item.detail || '无' },
      { label: '处理状态', value: item.statusLabel || (item.status === '0' ? '待处理' : '已处理') }
    ]
  }))
  const placeReviewCards = placeReviews.value.map(item => ({
    id: `place-review-${item.reviewId}`,
    type: 'placeReview',
    typeLabel: '场地评价',
    status: '0',
    statusLabel: '待处理',
    title: `${item.placeName || '未知场地'} · ${item.score || '-'}分`,
    actor: item.username || '匿名用户',
    time: fmtTime(item.createTime),
    summary: item.content || '无文字评价',
    fullContent: item.content || '无文字评价',
    imageUrl: item.imageUrl || '',
    historyNote: '',
    searchText: `${item.placeName || ''} ${item.username || ''}`,
    facts: [
      { label: '场地名称', value: item.placeName || '未知' },
      { label: '评分', value: `${item.score || '-'} 分` },
      { label: '评价人', value: item.username || '匿名用户' }
    ],
    detailFacts: [
      { label: '评价ID', value: String(item.reviewId || '-') },
      { label: '场地名称', value: item.placeName || '未知' },
      { label: '评分', value: `${item.score || '-'} 分` },
      { label: '评价人', value: item.username || '匿名用户' }
    ]
  }))
  return [...activityCards, ...bulletinCards, ...reportCards, ...placeReviewCards]
})

const filteredReviewCards = computed(() => {
  let list = reviewCards.value

  if (reviewCenterTab.value === 'all') {
    list = list.filter(item =>
      (item.type === 'activity' && item.status === '0') ||
      (item.type === 'bulletin' && item.status === '0') ||
      (item.type === 'report' && item.status === '0') ||
      item.type === 'placeReview'
    )
  } else if (reviewCenterTab.value === 'done') {
    list = list.filter(item =>
      (item.type === 'activity' && item.status !== '0') ||
      (item.type === 'bulletin' && item.status !== '0') ||
      (item.type === 'report' && item.status !== '0')
    )
  } else if (reviewCenterTab.value === 'activity') {
    list = list.filter(item => item.type === 'activity' && item.status === '0')
  } else if (reviewCenterTab.value === 'bulletin') {
    list = list.filter(item => item.type === 'bulletin' && item.status === '0')
  } else if (reviewCenterTab.value === 'report') {
    list = list.filter(item => item.type === 'report' && item.status === '0')
  } else if (reviewCenterTab.value === 'placeReview') {
    list = list.filter(item => item.type === 'placeReview')
  }

  if (reviewTypeFilter.value !== 'all') {
    list = list.filter(item => item.type === reviewTypeFilter.value)
  }

  if (reviewStatusFilter.value === 'pending') {
    list = list.filter(item => item.type === 'placeReview' || item.status === '0')
  } else if (reviewStatusFilter.value === 'approved') {
    list = list.filter(item => isApprovedStatus(item) || (item.type === 'report' && item.status === '1'))
  } else if (reviewStatusFilter.value === 'rejected') {
    list = list.filter(item => isRejectedStatus(item))
  }

  if (reviewTimeFilter.value !== 'all') {
    const now = new Date()
    const nowTs = now.getTime()
    let startTs = 0
    if (reviewTimeFilter.value === 'today') {
      const start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
      startTs = start.getTime()
    } else if (reviewTimeFilter.value === '7d') {
      startTs = nowTs - 7 * 24 * 60 * 60 * 1000
    } else if (reviewTimeFilter.value === '30d') {
      startTs = nowTs - 30 * 24 * 60 * 60 * 1000
    }
    list = list.filter(item => toTimestamp(item.time) >= startTs)
  }

  list = list.filter(item => keywordMatch(item, reviewKeyword.value))
  if (selectedReviewIds.value.length) {
    const ids = new Set(list.map(item => item.id))
    selectedReviewIds.value = selectedReviewIds.value.filter(id => ids.has(id))
  }
  return list
})

const selectedReviewCards = computed(() => {
  const ids = new Set(selectedReviewIds.value)
  return filteredReviewCards.value.filter(item => ids.has(item.id))
})

const batchTypeLabel = computed(() => {
  const types = [...new Set(selectedReviewCards.value.map(item => item.type))]
  if (types.length !== 1) return ''
  return types[0] === 'activity' ? '活动' : (types[0] === 'bulletin' ? '快讯' : '')
})

const canBatchApproveOrReject = computed(() => {
  if (!selectedReviewCards.value.length) return false
  const types = [...new Set(selectedReviewCards.value.map(item => item.type))]
  if (types.length !== 1) return false
  if (!['activity', 'bulletin'].includes(types[0])) return false
  return selectedReviewCards.value.every(item => item.status === '0')
})

const allFilteredSelected = computed(() => {
  if (!filteredReviewCards.value.length) return false
  return filteredReviewCards.value.every(item => selectedReviewIds.value.includes(item.id))
})

const reviewDrawerItem = computed(() => reviewCards.value.find(item => item.id === reviewDrawerId.value) || null)
const moderationOverlayVisible = computed(() => Boolean(moderationLoadingKey.value || postModerationLoadingId.value))
const moderationOverlayTitle = computed(() => {
  if (moderationLoadingKey.value) {
    return 'AI 正在生成审核建议'
  }
  if (postModerationLoadingId.value) {
    return 'AI 正在分析帖子风险'
  }
  return 'AI 正在处理中'
})
const moderationOverlaySubtitle = computed(() => {
  if (moderationLoadingKey.value) {
    return '正在识别广告导流、违规内容和展示风险，请稍候。'
  }
  if (postModerationLoadingId.value) {
    return '正在提取帖子重点，整理风险等级、风险点和处理建议。'
  }
  return '请稍候，系统正在整理结果。'
})

function resetReviewFilters() {
  reviewStatusFilter.value = 'all'
  reviewTypeFilter.value = 'all'
  reviewTimeFilter.value = 'all'
  reviewKeyword.value = ''
  reviewCenterTab.value = 'all'
}

function toggleSelectAllFiltered(checked) {
  if (checked) {
    selectedReviewIds.value = filteredReviewCards.value.map(item => item.id)
  } else {
    selectedReviewIds.value = []
  }
}

function toggleSelectReview(id, checked) {
  if (checked) {
    if (!selectedReviewIds.value.includes(id)) selectedReviewIds.value.push(id)
  } else {
    selectedReviewIds.value = selectedReviewIds.value.filter(i => i !== id)
  }
}

function openReviewDrawer(item) {
  reviewDrawerId.value = item.id
  moderationLoadingKey.value = ''
  moderationResultKey.value = ''
  moderationResult.value = null
  reviewDrawerVisible.value = true
}

function closeReviewDrawer() {
  reviewDrawerVisible.value = false
}

async function fetchModerationSuggestForReviewItem(item) {
  if (!item?.moderationPayload) {
    alert('当前内容暂不支持 AI 审核建议')
    return
  }
  moderationLoadingKey.value = item.id
  try {
    const result = await api.moderationSuggest(item.moderationPayload)
    moderationResultKey.value = item.id
    moderationResult.value = {
      riskLevel: result?.riskLevel || 'LOW',
      riskPoints: Array.isArray(result?.riskPoints) ? result.riskPoints : [],
      suggestion: result?.suggestion || '',
      normalizedSummary: result?.normalizedSummary || ''
    }
  } catch (e) {
    alert(e?.message || 'AI 审核建议获取失败，请稍后重试')
  } finally {
    moderationLoadingKey.value = ''
  }
}

function buildPostModerationPayload(post) {
  return {
    contentType: 'POST',
    title: post?.title || `帖子 #${post?.postId || ''}`,
    content: post?.content || '暂无正文',
    extraInfo: [
      post?.category ? `分类：${post.category}` : '',
      post?.username || post?.publisherName ? `发布人：${post.username || post.publisherName}` : '',
      post?.createTime ? `发布时间：${fmtTime(post.createTime)}` : '',
      post?.isTop ? `是否置顶：${post.isTop === '1' ? '是' : '否'}` : ''
    ].filter(Boolean).join('；')
  }
}

async function fetchModerationSuggestForPost(post) {
  if (!post) return
  postModerationLoadingId.value = post.postId
  try {
    const result = await api.moderationSuggest(buildPostModerationPayload(post))
    postModerationResultId.value = post.postId
    postModerationResult.value = {
      riskLevel: result?.riskLevel || 'LOW',
      riskPoints: Array.isArray(result?.riskPoints) ? result.riskPoints : [],
      suggestion: result?.suggestion || '',
      normalizedSummary: result?.normalizedSummary || ''
    }
  } catch (e) {
    alert(e?.message || 'AI 审核建议获取失败，请稍后重试')
  } finally {
    postModerationLoadingId.value = null
  }
}

async function batchReview(action) {
  if (!selectedReviewCards.value.length) {
    alert('请先勾选要批量处理的内容')
    return
  }
  if (!canBatchApproveOrReject.value) {
    alert('仅支持同类型（活动或快讯）且待审核内容的批量处理，请重新选择')
    return
  }
  const isApprove = action === 'approve'
  const actionLabel = isApprove ? '通过' : '驳回'
  const typeLabel = batchTypeLabel.value || '内容'
  if (!confirm(`确认批量${actionLabel}${selectedReviewCards.value.length}条${typeLabel}吗？`)) return

  const targets = [...selectedReviewCards.value]
  for (const item of targets) {
    if (item.type === 'activity') {
      const source = reviewActivities.value.find(i => `activity-${i.activityId}` === item.id)
      if (source) await api.adminReviewActivity(source.activityId, { status: isApprove ? '1' : '2' })
    } else if (item.type === 'bulletin') {
      const source = reviewBulletins.value.find(i => `bulletin-${i.bulletinId}` === item.id)
      if (source) await api.adminReviewBulletin(source.bulletinId, { status: isApprove ? '1' : '2', rejectReason: '' })
    }
  }
  selectedReviewIds.value = []
  await Promise.all([loadActivities(), loadContentData(), loadReviewCenterData()])
}

function sliceByPage(list, page) {
  const start = (page - 1) * ADMIN_PAGE_SIZE
  return list.slice(start, start + ADMIN_PAGE_SIZE)
}

function normalizeServerPage(key) {
  const totalMap = {
    users: usersTotalPages.value,
    posts: postsTotalPages.value,
    comments: commentsTotalPages.value,
    activities: activitiesTotalPages.value
  }
  const totalPages = totalMap[key] || 1
  if (pageState[key].page > totalPages) {
    pageState[key].page = totalPages
  }
}

function normalizeContentPages() {
  if (contentPageState.notices > noticesTotalPages.value) contentPageState.notices = noticesTotalPages.value
  if (contentPageState.news > newsTotalPages.value) contentPageState.news = newsTotalPages.value
  if (contentPageState.places > placesTotalPages.value) contentPageState.places = placesTotalPages.value
  if (contentPageState.banners > bannersTotalPages.value) contentPageState.banners = bannersTotalPages.value
  if (contentPageState.videos > videosTotalPages.value) contentPageState.videos = videosTotalPages.value
}

function clearError(group, field) {
  if (!formErrors[group]) return
  formErrors[group][field] = ''
}

function setError(group, field, message) {
  if (!formErrors[group]) return
  formErrors[group][field] = message
}

function clearGroupErrors(group) {
  if (!formErrors[group]) return
  Object.keys(formErrors[group]).forEach((field) => {
    formErrors[group][field] = ''
  })
}

function hasGroupErrors(group) {
  if (!formErrors[group]) return false
  return Object.values(formErrors[group]).some(Boolean)
}

function inputClass(group, field) {
  return formErrors[group]?.[field] ? 'input-error' : ''
}

async function loadStats() {
  stats.value = await api.adminStats()
}

async function loadAnalytics() {
  analytics.value = await api.adminAnalytics()
}

async function loadUsers() {
  const res = await api.adminUsers({ page: pageState.users.page, size: ADMIN_PAGE_SIZE })
  users.value = res.list || []
  pageState.users.total = Number(res.total || 0)
  normalizeServerPage('users')
  if (!users.value.length && pageState.users.page > 1) {
    pageState.users.page -= 1
    await loadUsers()
  }
}

async function toggleUserStatus(item) {
  const target = item.status === '0' ? '1' : '0'
  await api.adminUserStatus(item.userId, target)
  await loadUsers()
}

async function toggleUserBulletinPermission(item) {
  const target = item.bulletinPermission === '1' ? '0' : '1'
  await api.adminUserBulletinPermission(item.userId, target)
  await loadUsers()
}

async function loadPosts() {
  const res = await api.adminPosts({ page: pageState.posts.page, size: ADMIN_PAGE_SIZE })
  posts.value = res.list || []
  pageState.posts.total = Number(res.total || 0)
  normalizeServerPage('posts')
  if (!posts.value.length && pageState.posts.page > 1) {
    pageState.posts.page -= 1
    await loadPosts()
  }
}

async function deletePost(id) {
  await api.adminDeletePost(id)
  await loadPosts()
}

async function topPost(item) {
  await api.adminTopPost(item.postId, item.isTop === '1' ? '0' : '1')
  await loadPosts()
}

async function loadComments() {
  const res = await api.adminComments({ page: pageState.comments.page, size: ADMIN_PAGE_SIZE })
  comments.value = res.list || []
  pageState.comments.total = Number(res.total || 0)
  normalizeServerPage('comments')
  if (!comments.value.length && pageState.comments.page > 1) {
    pageState.comments.page -= 1
    await loadComments()
  }
}

async function deleteComment(id) {
  await api.adminDeleteComment(id)
  await loadComments()
}

async function loadActivities() {
  const res = await api.adminActivities({
    page: pageState.activities.page,
    size: ADMIN_PAGE_SIZE,
    reviewStatus: activityReviewFilter.value
  })
  activities.value = res.list || []
  pageState.activities.total = Number(res.total || 0)
  normalizeServerPage('activities')
  if (!activities.value.length && pageState.activities.page > 1) {
    pageState.activities.page -= 1
    await loadActivities()
  }
}

function activityReviewLabel(item) {
  if (item.reviewStatus === '0') return '待审核'
  if (item.reviewStatus === '1') return '已通过'
  if (item.reviewStatus === '2') return '已驳回'
  return '未知'
}

function activityStatusLabel(item) {
  if (item.activityStatus === '2') return '报名中'
  if (item.activityStatus === '3') return '已满员'
  if (item.activityStatus === '4') return '已取消'
  if (item.activityStatus === '5') return '已结束'
  if (item.activityStatus === '1') return '审核驳回'
  return '待审核'
}

async function reviewActivity(item, status) {
  await api.adminReviewActivity(item.activityId, { status })
  await Promise.all([loadActivities(), loadReviewCenterData()])
}

async function forceActivityStatus(item, status) {
  await api.adminActivityStatus(item.activityId, status)
  await Promise.all([loadActivities(), loadReviewCenterData()])
}

async function deleteActivity(id) {
  await api.adminDeleteActivity(id)
  await Promise.all([loadActivities(), loadReviewCenterData()])
}

async function loadContentData() {
  const [noticeData, newsData, placeData, bannerData, placeReviewData, videoData] = await Promise.all([
    api.adminNotices(),
    api.adminNews(),
    api.adminPlaces(),
    api.adminBanners(),
    api.adminPlaceReviews(),
    api.adminVideos()
  ])
  notices.value = noticeData
  newsList.value = newsData
  places.value = placeData
  banners.value = bannerData
  placeReviews.value = placeReviewData || []
  videos.value = videoData || []
  normalizeContentPages()
}

async function loadReports() {
  reports.value = await api.adminReports()
}

async function loadReviewCenterData() {
  const [activityData, reportData, bulletinData, placeReviewData] = await Promise.all([
    api.adminActivities({ page: 1, size: 200 }),
    api.adminReports(),
    api.adminBulletins({ type: null, status: null }),
    api.adminPlaceReviews()
  ])
  reviewActivities.value = activityData.list || []
  reports.value = reportData || []
  reviewBulletins.value = bulletinData || []
  placeReviews.value = placeReviewData || []
}

async function handleReport(item, status) {
  const handleNote = status === '2' ? (prompt('请输入驳回说明（可选）') || '') : (prompt('请输入处理说明（可选）') || '')
  await api.adminHandleReport(item.reportId, { status, handleNote })
  await Promise.all([loadReports(), loadReviewCenterData()])
}

async function deleteReportedTarget(item) {
  await api.adminDeleteReportedTarget(item.reportId)
  await Promise.all([loadReports(), loadPosts(), loadComments(), loadContentData(), loadReviewCenterData()])
}

async function applyBulletinTypeFilter() {
  contentPageState.bulletins = 1
  await loadContentData()
}

async function applyBulletinStatusFilter() {
  contentPageState.bulletins = 1
  await loadContentData()
}

async function changeUsersPage(next) {
  if (next < 1 || next > usersTotalPages.value || next === pageState.users.page) return
  pageState.users.page = next
  await loadUsers()
}

async function changePostsPage(next) {
  if (next < 1 || next > postsTotalPages.value || next === pageState.posts.page) return
  pageState.posts.page = next
  await loadPosts()
}

async function changeCommentsPage(next) {
  if (next < 1 || next > commentsTotalPages.value || next === pageState.comments.page) return
  pageState.comments.page = next
  await loadComments()
}

async function changeActivitiesPage(next) {
  if (next < 1 || next > activitiesTotalPages.value || next === pageState.activities.page) return
  pageState.activities.page = next
  await loadActivities()
}

async function changeActivityReviewFilter(next) {
  if (activityReviewFilter.value === next) return
  activityReviewFilter.value = next
  pageState.activities.page = 1
  await loadActivities()
}

function changeContentPage(key, next, totalPages) {
  if (next < 1 || next > totalPages || next === contentPageState[key]) return
  contentPageState[key] = next
}

function resetNewsForm() {
  newsForm.title = ''
  newsForm.content = ''
  newsForm.summary = ''
  newsForm.category = ''
  newsForm.cover = ''
  newsForm.sourceName = ''
  newsForm.sourceUrl = ''
}

function fillNewsEditForm(detail = {}) {
  newsEditForm.newsId = detail.newsId ?? null
  newsEditForm.title = detail.title || ''
  newsEditForm.content = detail.content || ''
  newsEditForm.summary = detail.summary || ''
  newsEditForm.category = detail.category || ''
  newsEditForm.cover = detail.cover || ''
  newsEditForm.sourceName = detail.sourceName || ''
  newsEditForm.sourceUrl = detail.sourceUrl || ''
  newsEditForm.originTitle = detail.originTitle || ''
  newsEditForm.originContent = detail.originContent || ''
  newsEditForm.originSummary = detail.originSummary || ''
  newsEditForm.aiTitle = detail.aiTitle || ''
  newsEditForm.aiSummary = detail.aiSummary || ''
  newsEditForm.aiCategory = detail.aiCategory || ''
  newsEditForm.aiTranslatedContent = detail.aiTranslatedContent || ''
  newsEditForm.status = detail.status || '0'
  newsEditForm.aiStatus = detail.aiStatus || ''
  newsEditForm.aiErrorMessage = detail.aiErrorMessage || ''
  newsEditForm.syncTime = detail.syncTime || ''
}

function closeNewsDetail() {
  newsDetailVisible.value = false
  newsDetailLoading.value = false
  activeNewsDetail.value = null
}

async function openNewsDetail(item) {
  if (!item?.newsId) return
  newsDetailVisible.value = true
  newsDetailLoading.value = true
  try {
    const detail = await api.adminNewsDetail(item.newsId)
    activeNewsDetail.value = detail
    fillNewsEditForm(detail)
  } catch (error) {
    alert(error?.message || '资讯详情加载失败')
    closeNewsDetail()
  } finally {
    newsDetailLoading.value = false
  }
}

async function reprocessNewsAi() {
  if (!newsEditForm.newsId || newsReprocessLoading.value) {
    return
  }
  newsReprocessLoading.value = true
  try {
    const detail = await api.adminReprocessNewsAi(newsEditForm.newsId)
    activeNewsDetail.value = detail
    fillNewsEditForm(detail)
    await loadContentData()
    alert('资讯 AI 重跑完成，已刷新最新结果')
  } catch (error) {
    alert(error?.message || '资讯 AI 重跑失败')
  } finally {
    newsReprocessLoading.value = false
  }
}

async function syncNewsOnce() {
  if (newsSyncLoading.value) return
  newsSyncLoading.value = true
  newsSyncMode.value = 'normal'
  newsSyncSummary.value = null
  try {
    const result = await api.adminSyncNews()
    newsSyncSummary.value = result || null
    if (result?.running) {
      startNewsSyncPolling()
    } else {
      newsSyncLoading.value = false
      newsSyncMode.value = ''
      await loadContentData()
      alert(result?.message || '资讯拉取完成')
    }
  } catch (error) {
    alert(error?.message || '资讯拉取失败')
    newsSyncLoading.value = false
    newsSyncMode.value = ''
  }
}

async function syncNewsAiOnly() {
  if (newsSyncLoading.value) return
  newsSyncLoading.value = true
  newsSyncMode.value = 'ai'
  newsSyncSummary.value = null
  try {
    const result = await api.adminSyncNewsAi()
    newsSyncSummary.value = result || null
    if (result?.running) {
      startNewsSyncPolling()
    } else {
      newsSyncLoading.value = false
      newsSyncMode.value = ''
      await loadContentData()
      alert(result?.message || 'AI 全网抓取完成')
    }
  } catch (error) {
    alert(error?.message || 'AI 全网抓取失败')
    newsSyncLoading.value = false
    newsSyncMode.value = ''
  }
}

function clearNewsSyncPolling() {
  if (newsSyncPollTimer) {
    clearTimeout(newsSyncPollTimer)
    newsSyncPollTimer = null
  }
}

function startNewsSyncPolling() {
  clearNewsSyncPolling()
  newsSyncPollTimer = window.setTimeout(async () => {
    try {
      const status = await api.adminSyncNewsStatus()
      newsSyncSummary.value = status || null
      if (status?.running) {
        startNewsSyncPolling()
        return
      }
      newsSyncLoading.value = false
      newsSyncMode.value = ''
      await loadContentData()
    } catch (_) {
      newsSyncLoading.value = false
      newsSyncMode.value = ''
    } finally {
      if (!newsSyncSummary.value?.running) {
        clearNewsSyncPolling()
      }
    }
  }, 3000)
}

async function saveNewsDetail() {
  clearGroupErrors('news')
  if (!newsEditForm.newsId) {
    alert('当前资讯缺少有效 ID，暂时无法保存')
    return
  }
  if (!String(newsEditForm.title || '').trim()) setError('news', 'title', '请填写展示标题')
  if (!String(newsEditForm.content || '').trim()) setError('news', 'content', '请填写展示正文')
  if (hasGroupErrors('news')) return

  await api.adminUpdateNews(newsEditForm.newsId, {
    title: newsEditForm.title,
    content: newsEditForm.content,
    summary: newsEditForm.summary,
    category: newsEditForm.category,
    cover: newsEditForm.cover,
    sourceName: newsEditForm.sourceName,
    sourceUrl: newsEditForm.sourceUrl,
    originTitle: newsEditForm.originTitle,
    originContent: newsEditForm.originContent,
    originSummary: newsEditForm.originSummary,
    aiTitle: newsEditForm.aiTitle,
    aiSummary: newsEditForm.aiSummary,
    aiCategory: newsEditForm.aiCategory,
    aiTranslatedContent: newsEditForm.aiTranslatedContent,
    status: newsEditForm.status
  })
  await loadContentData()
  if (newsEditForm.newsId) {
    const detail = await api.adminNewsDetail(newsEditForm.newsId)
    activeNewsDetail.value = detail
    fillNewsEditForm(detail)
  }
}

async function reviewNews(status) {
  if (!newsEditForm.newsId || newsReviewLoading.value) return
  newsReviewLoading.value = true
  try {
    await api.adminReviewNews(newsEditForm.newsId, { status })
    newsEditForm.status = status
    await loadContentData()
    const detail = await api.adminNewsDetail(newsEditForm.newsId)
    activeNewsDetail.value = detail
    fillNewsEditForm(detail)
    alert(status === '1' ? '资讯已审核通过' : '资讯已驳回')
  } catch (error) {
    alert(error?.message || '资讯审核失败')
  } finally {
    newsReviewLoading.value = false
  }
}

async function createNotice() {
  clearGroupErrors('notice')
  if (!String(noticeForm.title || '').trim()) setError('notice', 'title', '请填写公告标题')
  if (!String(noticeForm.content || '').trim()) setError('notice', 'content', '请填写公告内容')
  if (hasGroupErrors('notice')) return

  await api.adminCreateNotice(noticeForm)
  noticeForm.title = ''
  noticeForm.content = ''
  await loadContentData()
}

async function deleteNotice(id) {
  await api.adminDeleteNotice(id)
  await loadContentData()
}

async function reviewBulletin(item, status) {
  const rejectReason = status === '2' ? prompt('请输入驳回原因（可选）') || '' : ''
  await api.adminReviewBulletin(item.bulletinId, { status, rejectReason })
  await Promise.all([loadContentData(), loadReviewCenterData()])
}

async function deleteBulletin(id) {
  await api.adminDeleteBulletin(id)
  await loadContentData()
}

async function createNews() {
  clearGroupErrors('news')
  if (!String(newsForm.title || '').trim()) setError('news', 'title', '请填写资讯标题')
  if (!String(newsForm.content || '').trim()) setError('news', 'content', '请填写资讯内容')
  if (hasGroupErrors('news')) return

  await api.adminCreateNews(newsForm)
  resetNewsForm()
  await loadContentData()
}

async function deleteNews(id) {
  await api.adminDeleteNews(id)
  await loadContentData()
}

async function createPlace() {
  clearGroupErrors('place')
  if (!String(placeForm.name || '').trim()) setError('place', 'name', '请填写场地名称')
  if (!String(placeForm.address || '').trim()) setError('place', 'address', '请填写场地地址')
  if (!Number.isFinite(Number(placeForm.score)) || Number(placeForm.score) < 0 || Number(placeForm.score) > 5) {
    setError('place', 'score', '场地评分必须在 0 到 5 之间')
  }
  if (hasGroupErrors('place')) return

  await api.adminCreatePlace(placeForm)
  placeForm.name = ''
  placeForm.address = ''
  placeForm.intro = ''
  placeForm.score = 4.5
  await loadContentData()
}

async function deletePlace(id) {
  await api.adminDeletePlace(id)
  await loadContentData()
}

async function deletePlaceReview(id) {
  await api.adminDeletePlaceReview(id)
  await Promise.all([loadContentData(), loadReviewCenterData()])
}

async function deleteVideo(id) {
  await api.adminDeleteVideo(id)
  await loadContentData()
}

function viewReviewItem(item) {
  openReviewDrawer(item)
}

async function handleReviewCardAction(item, action) {
  if (item.type === 'activity') {
    const source = reviewActivities.value.find(i => `activity-${i.activityId}` === item.id)
    if (!source) return
    if (action === 'approve') return reviewActivity(source, '1')
    if (action === 'reject') return reviewActivity(source, '2')
    if (action === 'view') return viewReviewItem(item)
  }
  if (item.type === 'bulletin') {
    const source = reviewBulletins.value.find(i => `bulletin-${i.bulletinId}` === item.id)
    if (!source) return
    if (action === 'approve') return reviewBulletin(source, '1')
    if (action === 'reject') return reviewBulletin(source, '2')
    if (action === 'view') return viewReviewItem(item)
  }
  if (item.type === 'report') {
    const source = reports.value.find(i => `report-${i.reportId}` === item.id)
    if (!source) return
    if (action === 'done') return handleReport(source, '1')
    if (action === 'reject') return handleReport(source, '2')
    if (action === 'delete') return deleteReportedTarget(source)
    if (action === 'view') return viewReviewItem(item)
  }
  if (item.type === 'placeReview') {
    const source = placeReviews.value.find(i => `place-review-${i.reviewId}` === item.id)
    if (!source) return
    if (action === 'delete') return deletePlaceReview(source.reviewId)
    if (action === 'view') return viewReviewItem(item)
  }
}

function triggerBannerImageSelect() {
  bannerFileInputRef.value?.click()
}

async function uploadBannerImage(event) {
  const file = (event.target.files || [])[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    alert('请选择图片文件')
    return
  }
  bannerUploading.value = true
  try {
    const imageUrl = await api.uploadPostImage(file)
    bannerForm.imageUrl = imageUrl
    clearError('banner', 'imageUrl')
  } catch (e) {
    alert(e?.message || '上传失败')
  } finally {
    bannerUploading.value = false
    if (bannerFileInputRef.value) {
      bannerFileInputRef.value.value = ''
    }
  }
}

async function createBanner() {
  clearGroupErrors('banner')
  const title = String(bannerForm.title || '').trim()
  const linkUrl = String(bannerForm.linkUrl || '').trim()
  const imageUrl = String(bannerForm.imageUrl || '').trim()
  const sortNum = Number(bannerForm.sortNum || 0)
  const intervalSeconds = Number(bannerForm.intervalSeconds || 5)

  if (!title) setError('banner', 'title', '请填写轮播标题')
  if (linkUrl && !linkUrl.startsWith('/') && !linkUrl.startsWith('http://') && !linkUrl.startsWith('https://')) {
    setError('banner', 'linkUrl', '跳转链接需为 /路径 或 http(s) 链接')
  }
  if (!Number.isFinite(sortNum) || sortNum < 0) setError('banner', 'sortNum', '排序值必须为大于等于 0 的数字')
  if (!Number.isFinite(intervalSeconds) || intervalSeconds < 2 || intervalSeconds > 60) {
    setError('banner', 'intervalSeconds', '轮播间隔需为 2~60 秒')
  }
  if (!imageUrl) setError('banner', 'imageUrl', '请填写图片地址或上传图片')
  if (hasGroupErrors('banner')) return

  await api.adminCreateBanner({
    title,
    imageUrl,
    linkUrl,
    sortNum,
    intervalSeconds,
    status: bannerForm.status
  })

  bannerForm.title = ''
  bannerForm.imageUrl = ''
  bannerForm.linkUrl = '/community'
  bannerForm.sortNum = 0
  bannerForm.intervalSeconds = 5
  bannerForm.status = '0'
  await loadContentData()
}

async function toggleBannerStatus(item) {
  const target = item.status === '0' ? '1' : '0'
  await api.adminUpdateBanner(item.bannerId, {
    title: item.title,
    imageUrl: item.imageUrl,
    linkUrl: item.linkUrl || '',
    sortNum: item.sortNum || 0,
    intervalSeconds: item.intervalSeconds || 5,
    status: target
  })
  await loadContentData()
}

async function deleteBanner(id) {
  await api.adminDeleteBanner(id)
  await loadContentData()
}

async function refreshAll() {
  await Promise.all([
    loadStats(),
    loadAnalytics(),
    loadUsers(),
    loadPosts(),
    loadComments(),
    loadActivities(),
    loadReports(),
    loadContentData(),
    loadReviewCenterData()
  ])
  Object.keys(loadedTabs).forEach((key) => {
    loadedTabs[key] = true
  })
}

async function loadTabData(tabKey, force = false) {
  if (!force && loadedTabs[tabKey]) return

  if (tabKey === 'stats') {
    await loadStats()
  } else if (tabKey === 'analytics') {
    await loadAnalytics()
  } else if (tabKey === 'reviewCenter') {
    await loadReviewCenterData()
  } else if (tabKey === 'users') {
    await loadUsers()
  } else if (tabKey === 'posts') {
    await loadPosts()
  } else if (tabKey === 'comments') {
    await loadComments()
  } else if (tabKey === 'activities') {
    await loadActivities()
  } else if (tabKey === 'reports') {
    await loadReports()
  } else if (tabKey === 'content') {
    await loadContentData()
  }

  loadedTabs[tabKey] = true
}

async function refreshCurrentTab() {
  await loadTabData(tab.value, true)
}

onMounted(async () => {
  await loadTabData(tab.value, true)
})

watch(tab, async (nextTab) => {
  await loadTabData(nextTab)
})

watch(newsStatusFilter, () => {
  contentPageState.news = 1
})

onBeforeUnmount(() => {
  clearNewsSyncPolling()
})
</script>

<template>
  <div class="admin-layout">
    <AiLoadingOverlay
      :visible="moderationOverlayVisible"
      :title="moderationOverlayTitle"
      :subtitle="moderationOverlaySubtitle"
      :steps="moderationSteps"
    />
    <transition name="news-sync-float">
      <div v-if="newsSyncLoading" class="news-sync-floating" role="status" aria-live="polite" aria-busy="true">
        <div class="news-sync-floating-head">
          <strong>正在拉取滑板资讯</strong>
          <span>后台处理中</span>
        </div>
        <p>系统正在执行来源抓取、去重、AI 处理与待审核入库，你可以继续查看后台其他内容。</p>
        <div class="news-sync-floating-steps">
          <span>抓取来源</span>
          <span>去重</span>
          <span>AI 处理</span>
          <span>待审核入库</span>
        </div>
      </div>
    </transition>

    <aside class="left-nav">
      <div class="brand-block">
        <h2>后台管理中心</h2>
        <p>君临天下</p>
      </div>
      <button
        v-for="item in navItems"
        :key="item.key"
        :class="['nav-btn', { active: tab === item.key }]"
        @click="tab = item.key"
      >
        <AppIcon :name="item.icon" :size="16" />
        <span>{{ item.label }}</span>
      </button>
    </aside>

    <section class="main-panel">
      <div class="panel-head card">
        <div>
          <h1>{{ navItems.find(item => item.key === tab)?.label || '后台管理中心' }}</h1>
          <p>统一管理用户、帖子、评论、活动、公告和轮播图。</p>
        </div>
        <button class="btn-primary" @click="refreshCurrentTab">
          <AppIcon name="refresh" :size="15" />
          刷新当前
        </button>
      </div>

      <div v-if="tab === 'stats'" class="card">
        <div class="stat-grid">
          <div v-for="item in statCards" :key="item.key" class="stat-card">
            <div class="stat-top">
              <span class="icon-chip"><AppIcon :name="item.icon" :size="14" /></span>
              <strong>{{ item.label }}</strong>
            </div>
            <div class="stat-value">{{ item.value }}</div>
          </div>
        </div>

        <div class="viz-grid">
          <div class="viz-card">
            <h3>模块统计图</h3>
            <div v-for="item in statBarData" :key="item.key" class="bar-row">
              <span class="bar-label">{{ item.label }}</span>
              <div class="bar-track">
                <div class="bar-fill" :style="{ width: item.width }" />
              </div>
              <strong>{{ item.value }}</strong>
            </div>
          </div>

          <div class="viz-card">
            <h3>核心概览</h3>
            <div class="overview-list">
              <p><span>用户</span><strong>{{ stats.userTotal || 0 }}</strong></p>
              <p><span>帖子</span><strong>{{ stats.postTotal || 0 }}</strong></p>
              <p><span>评论</span><strong>{{ stats.commentTotal || 0 }}</strong></p>
              <p><span>活动</span><strong>{{ stats.activityTotal || 0 }}</strong></p>
            </div>
          </div>
        </div>
      </div>

      <div v-if="tab === 'analytics'" class="card analytics-panel">
        <div class="viz-card">
          <h3>近 7 天运营趋势</h3>
          <div v-if="!(analytics.trend7d || []).length" class="muted">暂无趋势数据</div>
          <div v-else class="trend-table-wrap">
            <table class="simple-table">
              <thead>
                <tr>
                  <th>日期</th>
                  <th>新增用户</th>
                  <th>新增帖子</th>
                  <th>发布活动</th>
                  <th>活动报名</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in analytics.trend7d" :key="item.date">
                  <td>{{ item.date }}</td>
                  <td>{{ item.newUsers || 0 }}</td>
                  <td>{{ item.newPosts || 0 }}</td>
                  <td>{{ item.newActivities || 0 }}</td>
                  <td>{{ item.newSigns || 0 }}</td>
                </tr>
              </tbody>
            </table>
            <div v-for="item in analytics.trend7d" :key="`bar-${item.date}`" class="bar-row multi">
              <span class="bar-label">{{ item.date }}</span>
              <div class="multi-track">
                <div class="multi-fill users" :style="{ width: `${Math.round((Number(item.newUsers || 0) / trendBarMax) * 100)}%` }"></div>
                <div class="multi-fill posts" :style="{ width: `${Math.round((Number(item.newPosts || 0) / trendBarMax) * 100)}%` }"></div>
                <div class="multi-fill acts" :style="{ width: `${Math.round((Number(item.newActivities || 0) / trendBarMax) * 100)}%` }"></div>
                <div class="multi-fill signs" :style="{ width: `${Math.round((Number(item.newSigns || 0) / trendBarMax) * 100)}%` }"></div>
              </div>
            </div>
            <p class="muted">条形颜色：用户 / 帖子 / 活动 / 报名</p>
          </div>
        </div>

        <div class="analytics-grid">
          <div class="viz-card">
            <h3>热门帖子排行</h3>
            <div v-if="!(analytics.hotPosts || []).length" class="muted">暂无帖子数据</div>
            <div v-for="(item, idx) in analytics.hotPosts" :key="`post-${item.postId}`" class="mini-row">
              <div>
                <strong>#{{ idx + 1 }} {{ item.title }}</strong>
                <p>热度 {{ item.hotScore || 0 }} · 赞 {{ item.likeCount || 0 }} · 评 {{ item.commentCount || 0 }}</p>
              </div>
            </div>
          </div>

          <div class="viz-card">
            <h3>热门活动排行</h3>
            <div v-if="!(analytics.hotActivities || []).length" class="muted">暂无活动数据</div>
            <div v-for="(item, idx) in analytics.hotActivities" :key="`act-${item.activityId}`" class="mini-row">
              <div>
                <strong>#{{ idx + 1 }} {{ item.title }}</strong>
                <p>报名 {{ item.signNum || 0 }} / {{ item.maxNum || '不限' }}</p>
              </div>
            </div>
          </div>
        </div>

        <div class="viz-card">
          <h3>快讯分类占比</h3>
          <div v-if="!(analytics.bulletinTypeRatio || []).length" class="muted">暂无分类数据</div>
          <div v-for="item in analytics.bulletinTypeRatio" :key="`ratio-${item.type}`" class="bar-row">
            <span class="bar-label">{{ item.type }}</span>
            <div class="bar-track">
              <div class="bar-fill" :style="{ width: `${Math.round((Number(item.count || 0) / bulletinRatioMax) * 100)}%` }" />
            </div>
            <strong>{{ item.count || 0 }}</strong>
          </div>
        </div>
      </div>

      <div v-if="tab === 'reviewCenter'" class="review-center">
        <div class="card review-filter-bar">
          <select v-model="reviewStatusFilter">
            <option value="all">状态：全部</option>
            <option value="pending">状态：待处理</option>
            <option value="approved">状态：已通过/已处理</option>
            <option value="rejected">状态：已驳回</option>
          </select>
          <select v-model="reviewTypeFilter">
            <option value="all">类型：全部</option>
            <option value="activity">活动</option>
            <option value="bulletin">快讯</option>
            <option value="report">举报</option>
            <option value="placeReview">场地评价</option>
          </select>
          <select v-model="reviewTimeFilter">
            <option value="all">时间：全部</option>
            <option value="today">今天</option>
            <option value="7d">近 7 天</option>
            <option value="30d">近 30 天</option>
          </select>
          <input
            v-model="reviewKeyword"
            type="text"
            placeholder="关键词：标题/发布人/举报原因"
          />
          <button class="btn-soft" @click="resetReviewFilters">重置筛选</button>
        </div>

        <div class="card review-kpi-grid">
          <div class="review-kpi-card">
            <p>活动（当前结果）</p>
            <strong>{{ pendingCounts.activity }}</strong>
          </div>
          <div class="review-kpi-card">
            <p>快讯（当前结果）</p>
            <strong>{{ pendingCounts.bulletin }}</strong>
          </div>
          <div class="review-kpi-card">
            <p>举报（当前结果）</p>
            <strong>{{ pendingCounts.report }}</strong>
          </div>
          <div class="review-kpi-card">
            <p>场地评价（当前结果）</p>
            <strong>{{ pendingCounts.placeReview }}</strong>
          </div>
        </div>

        <div class="card review-tabs">
          <button
            v-for="item in reviewCenterTabs"
            :key="item.key"
            class="btn-soft"
            :class="{ active: reviewCenterTab === item.key }"
            @click="reviewCenterTab = item.key"
          >
            {{ item.label }}
          </button>
        </div>

        <div class="card content-list">
          <div class="review-batch-bar">
            <label class="review-check-all">
              <input
                type="checkbox"
                :checked="allFilteredSelected"
                @change="toggleSelectAllFiltered($event.target.checked)"
              />
              <span>全选当前列表</span>
            </label>
            <span class="muted">已选 {{ selectedReviewCards.length }} 条</span>
            <button class="btn-primary" :disabled="!canBatchApproveOrReject" @click="batchReview('approve')">批量通过</button>
            <button class="btn-soft" :disabled="!canBatchApproveOrReject" @click="batchReview('reject')">批量驳回</button>
          </div>
          <div v-if="!filteredReviewCards.length" class="muted">未找到匹配项，请调整筛选条件或关键词。</div>
          <div v-for="item in filteredReviewCards" :key="item.id" class="review-card">
            <div class="review-card-main">
              <div class="review-card-head">
                <label class="review-select">
                  <input
                    type="checkbox"
                    :checked="selectedReviewIds.includes(item.id)"
                    @change="toggleSelectReview(item.id, $event.target.checked)"
                  />
                </label>
                <span class="status-chip">{{ item.typeLabel }}</span>
                <span class="status-chip">{{ item.statusLabel }}</span>
              </div>
              <h3 class="review-title clamp-2">{{ item.title }}</h3>
              <p class="review-meta">发布人：{{ item.actor }}</p>
            </div>

            <div class="review-card-judge">
              <p class="review-meta">发布时间：{{ item.time }}</p>
              <p class="review-summary clamp-3">{{ item.summary }}</p>
              <div class="review-facts">
                <p v-for="fact in item.facts" :key="`${item.id}-${fact.label}`">
                  <span>{{ fact.label }}：</span>{{ fact.value }}
                </p>
              </div>
            </div>

            <div class="review-card-actions">
              <template v-if="item.type === 'activity'">
                <button v-if="item.status === '0'" class="btn-primary" @click="handleReviewCardAction(item, 'approve')">通过</button>
                <button class="btn-soft" @click="handleReviewCardAction(item, 'view')">查看</button>
                <button class="btn-soft" :disabled="moderationLoadingKey === item.id" @click="fetchModerationSuggestForReviewItem(item)">
                  {{ moderationLoadingKey === item.id ? '分析中...' : 'AI 审核建议' }}
                </button>
                <button v-if="item.status === '0'" class="btn-soft" @click="handleReviewCardAction(item, 'reject')">驳回</button>
              </template>
              <template v-else-if="item.type === 'bulletin'">
                <button v-if="item.status === '0'" class="btn-primary" @click="handleReviewCardAction(item, 'approve')">通过</button>
                <button class="btn-soft" @click="handleReviewCardAction(item, 'view')">查看</button>
                <button class="btn-soft" :disabled="moderationLoadingKey === item.id" @click="fetchModerationSuggestForReviewItem(item)">
                  {{ moderationLoadingKey === item.id ? '分析中...' : 'AI 审核建议' }}
                </button>
                <button v-if="item.status === '0'" class="btn-soft" @click="handleReviewCardAction(item, 'reject')">驳回</button>
              </template>
              <template v-else-if="item.type === 'report'">
                <button v-if="item.status === '0'" class="btn-primary" @click="handleReviewCardAction(item, 'done')">标记已处理</button>
                <button class="btn-soft" @click="handleReviewCardAction(item, 'view')">查看</button>
                <button v-if="item.status === '0'" class="btn-soft" @click="handleReviewCardAction(item, 'reject')">驳回举报</button>
                <button class="btn-danger" @click="handleReviewCardAction(item, 'delete')">删除违规内容</button>
              </template>
              <template v-else-if="item.type === 'placeReview'">
                <button class="btn-soft" @click="handleReviewCardAction(item, 'view')">查看</button>
                <button class="btn-danger" @click="handleReviewCardAction(item, 'delete')">删除</button>
              </template>
            </div>
          </div>
        </div>

        <div v-if="reviewDrawerVisible && reviewDrawerItem" class="review-drawer-mask" @click.self="closeReviewDrawer">
          <aside class="review-drawer">
            <div class="review-drawer-head">
              <h3>审核详情</h3>
              <button class="btn-soft" @click="closeReviewDrawer">关闭</button>
            </div>
            <div class="review-drawer-body">
              <h4 class="review-drawer-title">{{ reviewDrawerItem.title }}</h4>
              <p>类型：{{ reviewDrawerItem.typeLabel }} · 状态：{{ reviewDrawerItem.statusLabel }}</p>
              <p>发布人/举报人：{{ reviewDrawerItem.actor }}</p>
              <p>发布时间：{{ reviewDrawerItem.time }}</p>
              <img v-if="reviewDrawerItem.imageUrl" :src="reviewDrawerItem.imageUrl" alt="详情图片" class="review-drawer-image" />
              <div class="review-drawer-block">
                <strong>完整正文</strong>
                <p>{{ reviewDrawerItem.fullContent }}</p>
              </div>
              <div class="review-drawer-block">
                <strong>业务信息</strong>
                <p v-for="fact in reviewDrawerItem.detailFacts" :key="`drawer-${reviewDrawerItem.id}-${fact.label}`">
                  <span>{{ fact.label }}：</span>{{ fact.value }}
                </p>
              </div>
              <div v-if="reviewDrawerItem.historyNote" class="review-drawer-block">
                <strong>历史处理备注</strong>
                <p>{{ reviewDrawerItem.historyNote }}</p>
              </div>
              <div v-if="moderationResultKey === reviewDrawerItem.id && moderationResult" class="review-drawer-block moderation-block">
                <strong>AI 审核建议</strong>
                <p><span>风险等级：</span>{{ moderationResult.riskLevel }}</p>
                <p><span>处理建议：</span>{{ moderationResult.suggestion || '暂无建议' }}</p>
                <p><span>规范摘要：</span>{{ moderationResult.normalizedSummary || '暂无摘要' }}</p>
                <div v-if="moderationResult.riskPoints.length" class="moderation-points">
                  <span>风险点：</span>
                  <ul>
                    <li v-for="point in moderationResult.riskPoints" :key="point">{{ point }}</li>
                  </ul>
                </div>
                <p v-else><span>风险点：</span>未发现明显风险点</p>
              </div>
            </div>
            <div class="review-drawer-actions">
              <template v-if="reviewDrawerItem.type === 'activity'">
                <button class="btn-soft" :disabled="moderationLoadingKey === reviewDrawerItem.id" @click="fetchModerationSuggestForReviewItem(reviewDrawerItem)">
                  {{ moderationLoadingKey === reviewDrawerItem.id ? '分析中...' : 'AI 审核建议' }}
                </button>
                <button v-if="reviewDrawerItem.status === '0'" class="btn-primary" @click="handleReviewCardAction(reviewDrawerItem, 'approve')">通过</button>
                <button v-if="reviewDrawerItem.status === '0'" class="btn-soft" @click="handleReviewCardAction(reviewDrawerItem, 'reject')">驳回</button>
              </template>
              <template v-else-if="reviewDrawerItem.type === 'bulletin'">
                <button class="btn-soft" :disabled="moderationLoadingKey === reviewDrawerItem.id" @click="fetchModerationSuggestForReviewItem(reviewDrawerItem)">
                  {{ moderationLoadingKey === reviewDrawerItem.id ? '分析中...' : 'AI 审核建议' }}
                </button>
                <button v-if="reviewDrawerItem.status === '0'" class="btn-primary" @click="handleReviewCardAction(reviewDrawerItem, 'approve')">通过</button>
                <button v-if="reviewDrawerItem.status === '0'" class="btn-soft" @click="handleReviewCardAction(reviewDrawerItem, 'reject')">驳回</button>
              </template>
              <template v-else-if="reviewDrawerItem.type === 'report'">
                <button v-if="reviewDrawerItem.status === '0'" class="btn-primary" @click="handleReviewCardAction(reviewDrawerItem, 'done')">标记已处理</button>
                <button v-if="reviewDrawerItem.status === '0'" class="btn-soft" @click="handleReviewCardAction(reviewDrawerItem, 'reject')">驳回举报</button>
                <button class="btn-danger" @click="handleReviewCardAction(reviewDrawerItem, 'delete')">删除违规内容</button>
              </template>
              <template v-else-if="reviewDrawerItem.type === 'placeReview'">
                <button class="btn-danger" @click="handleReviewCardAction(reviewDrawerItem, 'delete')">删除</button>
              </template>
            </div>
          </aside>
        </div>
      </div>

      <div v-if="tab === 'users'" class="card content-list">
        <div v-for="u in users" :key="u.userId" class="data-row">
          <div>
            <strong>{{ u.username }}</strong>
            <p>{{ u.phone || '未填写手机号' }}</p>
          </div>
          <div class="status-chip">{{ u.status === '0' ? '正常' : '禁用' }} · 快讯权限：{{ u.bulletinPermission === '1' ? '已开通' : '未开通' }}</div>
          <div class="row-actions">
            <button @click="toggleUserStatus(u)">{{ u.status === '0' ? '禁用' : '恢复' }}</button>
            <button @click="toggleUserBulletinPermission(u)">{{ u.bulletinPermission === '1' ? '关闭快讯权限' : '开通快讯权限' }}</button>
          </div>
        </div>
        <div class="pagination-bar">
          <button class="btn-soft" :disabled="pageState.users.page <= 1" @click="changeUsersPage(pageState.users.page - 1)">上一页</button>
          <span class="muted">第 {{ pageState.users.page }} / {{ usersTotalPages }} 页（共 {{ pageState.users.total }} 条）</span>
          <button class="btn-soft" :disabled="pageState.users.page >= usersTotalPages" @click="changeUsersPage(pageState.users.page + 1)">下一页</button>
        </div>
      </div>

      <div v-if="tab === 'posts'" class="card content-list">
        <div v-for="p in posts" :key="p.postId" class="post-admin-card">
          <div class="data-row">
            <div>
              <strong>{{ p.title }}</strong>
              <p>置顶：{{ p.isTop === '1' ? '是' : '否' }}</p>
            </div>
            <div class="row-actions">
              <button class="btn-soft" :disabled="postModerationLoadingId === p.postId" @click="fetchModerationSuggestForPost(p)">
                {{ postModerationLoadingId === p.postId ? '分析中...' : 'AI 审核建议' }}
              </button>
              <button @click="topPost(p)">{{ p.isTop === '1' ? '取消置顶' : '置顶' }}</button>
              <button class="btn-danger" @click="deletePost(p.postId)">删除</button>
            </div>
          </div>
          <div v-if="postModerationResultId === p.postId && postModerationResult" class="moderation-inline-panel">
            <p><strong>风险等级：</strong>{{ postModerationResult.riskLevel }}</p>
            <p><strong>处理建议：</strong>{{ postModerationResult.suggestion || '暂无建议' }}</p>
            <p><strong>规范摘要：</strong>{{ postModerationResult.normalizedSummary || '暂无摘要' }}</p>
            <div v-if="postModerationResult.riskPoints.length" class="moderation-points">
              <strong>风险点：</strong>
              <ul>
                <li v-for="point in postModerationResult.riskPoints" :key="point">{{ point }}</li>
              </ul>
            </div>
            <p v-else><strong>风险点：</strong>未发现明显风险点</p>
          </div>
        </div>
        <div class="pagination-bar">
          <button class="btn-soft" :disabled="pageState.posts.page <= 1" @click="changePostsPage(pageState.posts.page - 1)">上一页</button>
          <span class="muted">第 {{ pageState.posts.page }} / {{ postsTotalPages }} 页（共 {{ pageState.posts.total }} 条）</span>
          <button class="btn-soft" :disabled="pageState.posts.page >= postsTotalPages" @click="changePostsPage(pageState.posts.page + 1)">下一页</button>
        </div>
      </div>

      <div v-if="tab === 'comments'" class="card content-list">
        <div v-for="c in comments" :key="c.commentId" class="data-row">
          <div>
            <strong>帖子 {{ c.postId }}</strong>
            <p>{{ c.content }}</p>
          </div>
          <button class="btn-danger" @click="deleteComment(c.commentId)">删除</button>
        </div>
        <div class="pagination-bar">
          <button class="btn-soft" :disabled="pageState.comments.page <= 1" @click="changeCommentsPage(pageState.comments.page - 1)">上一页</button>
          <span class="muted">第 {{ pageState.comments.page }} / {{ commentsTotalPages }} 页（共 {{ pageState.comments.total }} 条）</span>
          <button class="btn-soft" :disabled="pageState.comments.page >= commentsTotalPages" @click="changeCommentsPage(pageState.comments.page + 1)">下一页</button>
        </div>
      </div>

      <div v-if="tab === 'activities'" class="card content-list">
        <div class="inline">
          <button class="btn-soft" :class="{ active: activityReviewFilter === '0' }" @click="changeActivityReviewFilter('0')">待审核</button>
          <button class="btn-soft" :class="{ active: activityReviewFilter === '1' }" @click="changeActivityReviewFilter('1')">已通过</button>
          <button class="btn-soft" :class="{ active: activityReviewFilter === '2' }" @click="changeActivityReviewFilter('2')">已驳回</button>
        </div>
        <div v-for="a in activities" :key="a.activityId" class="data-row">
          <div>
            <strong>{{ a.title }}</strong>
            <p>审核：{{ activityReviewLabel(a) }} · 活动：{{ activityStatusLabel(a) }}</p>
          </div>
          <div class="row-actions">
            <button v-if="a.reviewStatus === '0'" @click="reviewActivity(a, '1')">审核通过</button>
            <button v-if="a.reviewStatus === '0'" class="btn-soft" @click="reviewActivity(a, '2')">审核驳回</button>
            <button class="btn-soft" @click="forceActivityStatus(a, '5')">强制下架</button>
            <button class="btn-soft" @click="forceActivityStatus(a, '4')">强制取消</button>
            <button class="btn-danger" @click="deleteActivity(a.activityId)">删除</button>
          </div>
        </div>
        <div class="pagination-bar">
          <button class="btn-soft" :disabled="pageState.activities.page <= 1" @click="changeActivitiesPage(pageState.activities.page - 1)">上一页</button>
          <span class="muted">第 {{ pageState.activities.page }} / {{ activitiesTotalPages }} 页（共 {{ pageState.activities.total }} 条）</span>
          <button class="btn-soft" :disabled="pageState.activities.page >= activitiesTotalPages" @click="changeActivitiesPage(pageState.activities.page + 1)">下一页</button>
        </div>
      </div>

      <div v-if="tab === 'reports'" class="card content-list">
        <div v-if="!reports.length" class="muted">暂无举报记录</div>
        <div v-for="item in reports" :key="item.reportId" class="data-row">
          <div>
            <strong>#{{ item.reportId }} {{ item.targetTitle || '内容已删除' }}</strong>
            <p>
              类型：{{ item.targetType }} · 原因：{{ item.reason }} · 举报人：{{ item.reporterName || '未知' }}
            </p>
            <p>状态：{{ item.statusLabel }} · 时间：{{ item.createTime?.replace('T', ' ') }}</p>
            <p v-if="item.detail">补充说明：{{ item.detail }}</p>
            <p v-if="item.handleNote">处理备注：{{ item.handleNote }}</p>
          </div>
          <div class="row-actions">
            <button v-if="item.status === '0'" class="btn-soft" @click="handleReport(item, '1')">标记已处理</button>
            <button v-if="item.status === '0'" class="btn-soft" @click="handleReport(item, '2')">驳回举报</button>
            <button class="btn-danger" @click="deleteReportedTarget(item)">删除违规内容</button>
          </div>
        </div>
      </div>

      <div v-if="tab === 'content'" class="content-tab-panel">
        <div class="card review-tabs">
          <button
            v-for="item in contentTabs"
            :key="item.key"
            class="btn-soft"
            :class="{ active: contentTab === item.key }"
            @click="contentTab = item.key"
          >
            {{ item.label }}
          </button>
        </div>

        <div v-if="contentTab === 'notice'" class="card form-card">
          <h3>公告管理</h3>
          <label class="field-caption">公告标题 <span class="required">*</span></label>
          <input
            v-model="noticeForm.title"
            :class="inputClass('notice', 'title')"
            placeholder="例如：五一放假活动安排"
            @input="clearError('notice', 'title')"
          />
          <p v-if="formErrors.notice.title" class="field-error">{{ formErrors.notice.title }}</p>
          <label class="field-caption">公告内容 <span class="required">*</span></label>
          <textarea
            v-model="noticeForm.content"
            :class="inputClass('notice', 'content')"
            placeholder="请输入公告详细内容"
            @input="clearError('notice', 'content')"
          />
          <p v-if="formErrors.notice.content" class="field-error">{{ formErrors.notice.content }}</p>
          <button class="btn-primary" @click="createNotice">新增公告</button>
          <div v-for="n in pagedNotices" :key="n.noticeId" class="mini-row">
            <strong>{{ n.title }}</strong>
            <button class="btn-danger" @click="deleteNotice(n.noticeId)">删除</button>
          </div>
          <div class="pagination-bar compact">
            <button class="btn-soft" :disabled="contentPageState.notices <= 1" @click="changeContentPage('notices', contentPageState.notices - 1, noticesTotalPages)">上一页</button>
            <span class="muted">第 {{ contentPageState.notices }} / {{ noticesTotalPages }} 页</span>
            <button class="btn-soft" :disabled="contentPageState.notices >= noticesTotalPages" @click="changeContentPage('notices', contentPageState.notices + 1, noticesTotalPages)">下一页</button>
          </div>
        </div>

        <div v-if="contentTab === 'news'" class="card form-card">
          <h3>资讯管理</h3>
          <p class="muted">支持查看原始资讯与 AI 处理结果对比，人工可修正最终展示内容，并控制审核状态。</p>
          <div class="news-audit-overview">
            <button class="news-audit-chip" :class="{ active: newsStatusFilter === '全部' }" @click="newsStatusFilter = '全部'">
              <strong>{{ newsList.length }}</strong>
              <span>全部资讯</span>
            </button>
            <button class="news-audit-chip pending" :class="{ active: newsStatusFilter === '0' }" @click="newsStatusFilter = '0'">
              <strong>{{ newsAuditStats.pending }}</strong>
              <span>待审核</span>
            </button>
            <button class="news-audit-chip approved" :class="{ active: newsStatusFilter === '1' }" @click="newsStatusFilter = '1'">
              <strong>{{ newsAuditStats.approved }}</strong>
              <span>已通过</span>
            </button>
            <button class="news-audit-chip rejected" :class="{ active: newsStatusFilter === '2' }" @click="newsStatusFilter = '2'">
              <strong>{{ newsAuditStats.rejected }}</strong>
              <span>已驳回</span>
            </button>
          </div>
          <div class="news-audit-filter-row">
            <span class="field-caption">审核状态筛选</span>
            <select v-model="newsStatusFilter" class="news-status-select">
              <option v-for="option in newsStatusOptions" :key="`news-filter-${option.value}`" :value="option.value">
                {{ option.label }}
              </option>
            </select>
            <span class="muted">拉取入库的资讯默认进入待审核，只有“已通过”才会在前台展示。</span>
          </div>
          <div class="row-actions">
            <button class="btn-soft news-sync-btn" :disabled="newsSyncLoading" @click="syncNewsOnce">
              {{ newsSyncLoading && newsSyncMode === 'normal' ? '拉取中...' : '一键拉取滑板资讯' }}
            </button>
            <button class="btn-soft news-sync-btn" :disabled="newsSyncLoading" @click="syncNewsAiOnly">
              {{ newsSyncLoading && newsSyncMode === 'ai' ? 'AI 抓取中...' : 'AI 全网抓取' }}
            </button>
          </div>
          <div v-if="newsSyncSummary" class="news-sync-summary">
            <strong>{{ newsSyncSummary.message || '本次同步已完成' }}</strong>
            <p>
              抓取 {{ newsSyncSummary.fetchedCount || 0 }} 条，
              新增 {{ newsSyncSummary.newCount || 0 }} 条，
              重复 {{ newsSyncSummary.duplicateCount || 0 }} 条，
              失败 {{ newsSyncSummary.failedCount || 0 }} 条。
            </p>
            <div v-if="newsSyncSummary.sourceResults?.length" class="news-sync-source-list">
              <p
                v-for="source in newsSyncSummary.sourceResults"
                :key="`${source.sourceId || source.sourceName}-${source.sourceUrl}`"
                class="news-sync-source-item"
              >
                {{ source.sourceName }}：抓取 {{ source.fetchedCount || 0 }} 条，新增 {{ source.newCount || 0 }} 条，重复 {{ source.duplicateCount || 0 }} 条，失败 {{ source.failedCount || 0 }} 条
                <span v-if="source.message"> · {{ source.message }}</span>
              </p>
            </div>
          </div>
          <label class="field-caption">资讯标题 <span class="required">*</span></label>
          <input
            v-model="newsForm.title"
            :class="inputClass('news', 'title')"
            placeholder="例如：2026 年滑板入门训练路线"
            @input="clearError('news', 'title')"
          />
          <p v-if="formErrors.news.title" class="field-error">{{ formErrors.news.title }}</p>
          <label class="field-caption">资讯摘要（可选）</label>
          <textarea v-model="newsForm.summary" placeholder="可填写人工摘要，后续也可在详情中调整" />
          <label class="field-caption">资讯分类</label>
          <select v-model="newsForm.category">
            <option value="">请选择或留空</option>
            <option v-for="option in newsCategoryOptions" :key="option" :value="option">{{ option }}</option>
          </select>
          <label class="field-caption">来源名称（可选）</label>
          <input v-model="newsForm.sourceName" placeholder="例如：World Skate / Thrasher" />
          <label class="field-caption">原文链接（可选）</label>
          <input v-model="newsForm.sourceUrl" placeholder="请输入原文链接 https://..." />
          <label class="field-caption">资讯内容 <span class="required">*</span></label>
          <textarea
            v-model="newsForm.content"
            :class="inputClass('news', 'content')"
            placeholder="请输入资讯正文内容"
            @input="clearError('news', 'content')"
          />
          <p v-if="formErrors.news.content" class="field-error">{{ formErrors.news.content }}</p>
          <button class="btn-primary" @click="createNews">新增资讯</button>
          <div v-for="n in pagedNews" :key="n.newsId" class="mini-row">
            <div class="news-row-main">
              <div class="news-row-head">
                <strong>{{ n.title }}</strong>
                <span class="news-status-badge" :class="newsStatusClass(n.status)">{{ newsStatusLabel(n.status) }}</span>
              </div>
              <p>{{ n.category || '未分类' }} · {{ n.sourceName || '来源待补充' }}</p>
              <p>{{ newsAuditHint(n.status) }}</p>
              <p>{{ newsAiStatusLabel(n.aiStatus) }} · {{ fmtTime(n.syncTime || n.createTime) }}</p>
              <p class="clamp-2">{{ n.summary || n.aiSummary || '暂无摘要' }}</p>
            </div>
            <div class="row-actions">
              <button class="btn-soft" @click="openNewsDetail(n)">查看详情</button>
              <button class="btn-danger" @click="deleteNews(n.newsId)">删除</button>
            </div>
          </div>
          <p v-if="!pagedNews.length" class="muted">当前筛选下暂无资讯。</p>
          <div class="pagination-bar compact">
            <button class="btn-soft" :disabled="contentPageState.news <= 1" @click="changeContentPage('news', contentPageState.news - 1, newsTotalPages)">上一页</button>
            <span class="muted">第 {{ contentPageState.news }} / {{ newsTotalPages }} 页</span>
            <button class="btn-soft" :disabled="contentPageState.news >= newsTotalPages" @click="changeContentPage('news', contentPageState.news + 1, newsTotalPages)">下一页</button>
          </div>
        </div>

        <div v-if="contentTab === 'place'" class="card form-card">
          <h3>场地管理</h3>
          <label class="field-caption">场地名称 <span class="required">*</span></label>
          <input
            v-model="placeForm.name"
            :class="inputClass('place', 'name')"
            placeholder="例如：世纪公园滑板场"
            @input="clearError('place', 'name')"
          />
          <p v-if="formErrors.place.name" class="field-error">{{ formErrors.place.name }}</p>
          <label class="field-caption">场地地址 <span class="required">*</span></label>
          <input
            v-model="placeForm.address"
            :class="inputClass('place', 'address')"
            placeholder="例如：北京市朝阳区 XX 路 88 号"
            @input="clearError('place', 'address')"
          />
          <p v-if="formErrors.place.address" class="field-error">{{ formErrors.place.address }}</p>
          <label class="field-caption">场地评分（0~5） <span class="required">*</span></label>
          <input
            v-model.number="placeForm.score"
            :class="inputClass('place', 'score')"
            type="number"
            min="0"
            max="5"
            step="0.1"
            placeholder="例如：4.5"
            @input="clearError('place', 'score')"
          />
          <p v-if="formErrors.place.score" class="field-error">{{ formErrors.place.score }}</p>
          <p class="field-tip">评分越高会更靠前展示，建议保留 1 位小数。</p>
          <label class="field-caption">场地简介（可选）</label>
          <textarea v-model="placeForm.intro" placeholder="请输入场地简介（如开放时间、地形特点）" />
          <button class="btn-primary" @click="createPlace">新增场地</button>
          <div v-for="p in pagedPlaces" :key="p.placeId" class="mini-row">
            <strong>{{ p.name }}（{{ p.score }}）</strong>
            <button class="btn-danger" @click="deletePlace(p.placeId)">删除</button>
          </div>
          <div class="pagination-bar compact">
            <button class="btn-soft" :disabled="contentPageState.places <= 1" @click="changeContentPage('places', contentPageState.places - 1, placesTotalPages)">上一页</button>
            <span class="muted">第 {{ contentPageState.places }} / {{ placesTotalPages }} 页</span>
            <button class="btn-soft" :disabled="contentPageState.places >= placesTotalPages" @click="changeContentPage('places', contentPageState.places + 1, placesTotalPages)">下一页</button>
          </div>
        </div>

        <div v-if="contentTab === 'banner'" class="card form-card">
          <h3>轮播图管理</h3>
          <label class="field-caption">轮播标题 <span class="required">*</span></label>
          <input
            v-model="bannerForm.title"
            :class="inputClass('banner', 'title')"
            placeholder="例如：城市滑板社群 · 发现同好"
            @input="clearError('banner', 'title')"
          />
          <p v-if="formErrors.banner.title" class="field-error">{{ formErrors.banner.title }}</p>
          <label class="field-caption">跳转链接（站内路径或 https 外链）</label>
          <input
            v-model="bannerForm.linkUrl"
            :class="inputClass('banner', 'linkUrl')"
            placeholder="例如：/community 或 https://example.com"
            @input="clearError('banner', 'linkUrl')"
          />
          <p v-if="formErrors.banner.linkUrl" class="field-error">{{ formErrors.banner.linkUrl }}</p>
          <p class="field-tip">示例：/community、/activities、https://your-site.com/page</p>
          <label class="field-caption">排序值（数字越小越靠前）</label>
          <input
            v-model.number="bannerForm.sortNum"
            :class="inputClass('banner', 'sortNum')"
            type="number"
            min="0"
            placeholder="例如：0 / 1 / 2"
            @input="clearError('banner', 'sortNum')"
          />
          <p v-if="formErrors.banner.sortNum" class="field-error">{{ formErrors.banner.sortNum }}</p>
          <p class="field-tip">建议从 0 开始递增，避免重复排序。</p>
          <label class="field-caption">自动轮播间隔（秒） <span class="required">*</span></label>
          <input
            v-model.number="bannerForm.intervalSeconds"
            :class="inputClass('banner', 'intervalSeconds')"
            type="number"
            min="2"
            max="60"
            step="1"
            placeholder="例如：5"
            @input="clearError('banner', 'intervalSeconds')"
          />
          <p v-if="formErrors.banner.intervalSeconds" class="field-error">{{ formErrors.banner.intervalSeconds }}</p>
          <p class="field-tip">建议 3~8 秒。当前值会用于首页自动轮播。</p>
          <label class="field-caption">图片地址（可手填 URL 或点击下方上传） <span class="required">*</span></label>
          <input
            v-model="bannerForm.imageUrl"
            :class="inputClass('banner', 'imageUrl')"
            placeholder="例如：https://xx.com/banner.jpg"
            @input="clearError('banner', 'imageUrl')"
          />
          <p v-if="formErrors.banner.imageUrl" class="field-error">{{ formErrors.banner.imageUrl }}</p>
          <input
            ref="bannerFileInputRef"
            class="banner-file-input"
            type="file"
            accept="image/*"
            @change="uploadBannerImage"
          />
          <div class="row-actions">
            <button type="button" :disabled="bannerUploading" @click="triggerBannerImageSelect">
              {{ bannerUploading ? '上传中...' : '上传图片' }}
            </button>
            <button class="btn-primary" @click="createBanner">新增轮播图</button>
          </div>
          <div v-for="b in pagedBanners" :key="b.bannerId" class="mini-row">
            <img v-if="b.imageUrl" :src="b.imageUrl" alt="轮播图预览" class="banner-thumb" />
            <div class="banner-meta">
              <strong>{{ b.title }}</strong>
              <p>排序 {{ b.sortNum || 0 }} · 间隔 {{ b.intervalSeconds || 5 }} 秒 · {{ b.status === '0' ? '上架' : '下架' }}</p>
            </div>
            <div class="row-actions">
              <button @click="toggleBannerStatus(b)">{{ b.status === '0' ? '下架' : '上架' }}</button>
              <button class="btn-danger" @click="deleteBanner(b.bannerId)">删除</button>
            </div>
          </div>
          <div class="pagination-bar compact">
            <button class="btn-soft" :disabled="contentPageState.banners <= 1" @click="changeContentPage('banners', contentPageState.banners - 1, bannersTotalPages)">上一页</button>
            <span class="muted">第 {{ contentPageState.banners }} / {{ bannersTotalPages }} 页</span>
            <button class="btn-soft" :disabled="contentPageState.banners >= bannersTotalPages" @click="changeContentPage('banners', contentPageState.banners + 1, bannersTotalPages)">下一页</button>
          </div>
        </div>

        <div v-if="contentTab === 'video'" class="card form-card">
          <h3>视频管理</h3>
          <div v-if="!pagedVideos.length" class="muted">暂无视频数据</div>
          <div v-for="v in pagedVideos" :key="v.videoId" class="mini-row">
            <div>
              <strong>{{ v.title || `视频 #${v.videoId}` }}</strong>
              <p>{{ (v.username || v.publisherName || '未知发布人') }} · {{ String(v.createTime || '').replace('T', ' ') }}</p>
            </div>
            <button class="btn-danger" @click="deleteVideo(v.videoId)">删除</button>
          </div>
          <div class="pagination-bar compact">
            <button class="btn-soft" :disabled="contentPageState.videos <= 1" @click="changeContentPage('videos', contentPageState.videos - 1, videosTotalPages)">上一页</button>
            <span class="muted">第 {{ contentPageState.videos }} / {{ videosTotalPages }} 页</span>
            <button class="btn-soft" :disabled="contentPageState.videos >= videosTotalPages" @click="changeContentPage('videos', contentPageState.videos + 1, videosTotalPages)">下一页</button>
          </div>
        </div>
      </div>
    </section>

    <div v-if="newsDetailVisible" class="news-detail-mask" @click.self="closeNewsDetail">
      <div class="news-detail-modal">
        <div class="news-detail-head">
          <div>
            <h3>资讯审核详情</h3>
            <p>对比原始内容与 AI 结果，人工确认最终展示版本。</p>
          </div>
          <div class="news-detail-head-actions">
            <button
              class="btn-soft news-ai-refresh-btn"
              :disabled="newsReprocessLoading || !newsEditForm.newsId"
              @click="reprocessNewsAi"
            >
              {{ newsReprocessLoading ? 'AI 重跑中...' : '重跑 AI 结果' }}
            </button>
            <button class="btn-soft" @click="closeNewsDetail">关闭</button>
          </div>
        </div>
        <div class="news-detail-body">
          <div v-if="newsDetailLoading" class="muted">正在加载资讯详情...</div>
          <template v-else-if="activeNewsDetail">
            <div class="news-compare-grid">
              <div class="news-compare-card">
                <div class="news-card-label">原始资讯</div>
                <p>
                  <span>审核状态</span>
                  <strong class="news-status-inline" :class="newsStatusClass(newsEditForm.status)">{{ newsStatusLabel(newsEditForm.status) }}</strong>
                </p>
                <p><span>同步时间</span>{{ fmtTime(newsEditForm.syncTime) }}</p>
                <p><span>原始标题</span>{{ cleanInlineNewsText(newsEditForm.originTitle) || '未保留' }}</p>
                <p><span>原始摘要</span>{{ cleanInlineNewsText(newsEditForm.originSummary) || '未提供' }}</p>
                <div class="news-block">
                  <span>原始正文</span>
                  <pre>{{ cleanNewsText(newsEditForm.originContent) || '未保留原文内容' }}</pre>
                </div>
                <p><span>原文链接</span>
                  <a v-if="newsEditForm.sourceUrl" :href="newsEditForm.sourceUrl" target="_blank" rel="noreferrer">{{ newsEditForm.sourceUrl }}</a>
                  <template v-else>未提供</template>
                </p>
                <p><span>来源名称</span>{{ newsEditForm.sourceName || '未提供' }}</p>
              </div>
              <div class="news-compare-card ai-card">
                <div class="news-card-label">AI 处理结果</div>
                <p class="news-inline-tip">切换资讯模型后，可点击右上角“重跑 AI 结果”重新生成摘要、分类、翻译与标题优化。</p>
                <p><span>AI 处理状态</span>{{ newsAiStatusLabel(newsEditForm.aiStatus) }}</p>
                <p><span>AI 优化标题</span>{{ cleanInlineNewsText(newsEditForm.aiTitle) || '未生成' }}</p>
                <p><span>AI 中文摘要</span>{{ cleanInlineNewsText(newsEditForm.aiSummary) || '未生成' }}</p>
                <p><span>AI 分类</span>{{ cleanInlineNewsText(newsEditForm.aiCategory) || '未分类' }}</p>
                <p v-if="newsEditForm.aiErrorMessage"><span>失败原因</span>{{ newsEditForm.aiErrorMessage }}</p>
                <div class="news-block">
                  <span>AI 翻译正文</span>
                  <pre>{{ cleanNewsText(newsEditForm.aiTranslatedContent) || '未生成翻译正文' }}</pre>
                </div>
              </div>
            </div>

            <div class="card form-card news-edit-card">
              <h4>人工最终展示内容</h4>
              <p class="muted">这里保存的是站内最终展示版本，不会覆盖上方 AI 原始结果留档。待审核和已驳回资讯均不会在前台展示。</p>
              <label class="field-caption">展示标题 <span class="required">*</span></label>
              <input v-model="newsEditForm.title" :class="inputClass('news', 'title')" placeholder="请输入最终展示标题" />
              <label class="field-caption">展示分类</label>
              <select v-model="newsEditForm.category">
                <option value="">请选择或留空</option>
                <option v-for="option in newsCategoryOptions" :key="`edit-${option}`" :value="option">{{ option }}</option>
              </select>
              <label class="field-caption">审核状态</label>
              <select v-model="newsEditForm.status">
                <option value="0">待审核</option>
                <option value="1">已通过</option>
                <option value="2">已驳回</option>
              </select>
              <label class="field-caption">展示摘要</label>
              <textarea v-model="newsEditForm.summary" placeholder="请输入最终展示摘要" />
              <label class="field-caption">展示正文 <span class="required">*</span></label>
              <textarea v-model="newsEditForm.content" :class="inputClass('news', 'content')" placeholder="请输入最终展示正文" />
              <label class="field-caption">来源名称</label>
              <input v-model="newsEditForm.sourceName" placeholder="请输入来源名称" />
              <label class="field-caption">原文链接</label>
              <input v-model="newsEditForm.sourceUrl" placeholder="请输入原文链接" />
              <div class="news-detail-actions">
                <button class="btn-primary" @click="saveNewsDetail">保存人工修正</button>
                <button class="btn-soft" :disabled="newsReviewLoading" @click="reviewNews('1')">审核通过</button>
                <button class="btn-soft" :disabled="newsReviewLoading" @click="reviewNews('2')">驳回</button>
                <button class="btn-soft" @click="closeNewsDetail">取消</button>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: 16px;
}

.left-nav {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  padding: 14px;
  min-height: 78vh;
  position: sticky;
  top: 74px;
  box-shadow: var(--shadow-sm);
}

.brand-block {
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}

.brand-block h2 {
  margin: 0;
}

.brand-block p {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-size: 13px;
}

.nav-btn {
  width: 100%;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-soft);
  border-radius: 10px;
  padding: 10px 11px;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.nav-btn:hover,
.nav-btn.active {
  background: var(--surface-muted);
  border-color: var(--line);
  color: var(--text);
}

.main-panel {
  display: grid;
  gap: 14px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.panel-head h1 {
  margin: 0;
  font-size: 24px;
}

.panel-head p {
  margin: 6px 0 0;
  color: var(--text-muted);
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 12px;
}

.stat-card {
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  padding: 12px;
  background: var(--surface-muted);
}

.stat-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-chip {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
}

.stat-value {
  margin-top: 8px;
  font-size: 30px;
  font-weight: 700;
}

.viz-grid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 12px;
}

.viz-card {
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  padding: 12px;
  background: #fff;
}

.viz-card h3 {
  margin: 0 0 10px;
}

.bar-row {
  display: grid;
  grid-template-columns: 88px 1fr 44px;
  gap: 8px;
  align-items: center;
  margin-bottom: 9px;
}

.bar-row.multi {
  grid-template-columns: 88px 1fr;
}

.bar-label {
  color: var(--text-soft);
  font-size: 13px;
}

.bar-track {
  height: 8px;
  border-radius: 999px;
  background: #e5e7eb;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 999px;
  background: #111827;
}

.multi-track {
  display: grid;
  gap: 5px;
}

.multi-fill {
  height: 7px;
  border-radius: 999px;
}

.multi-fill.users { background: #2563eb; }
.multi-fill.posts { background: #16a34a; }
.multi-fill.acts { background: #d97706; }
.multi-fill.signs { background: #7c3aed; }

.analytics-panel {
  display: grid;
  gap: 12px;
}

.analytics-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.review-center {
  display: grid;
  gap: 12px;
}

.review-filter-bar {
  display: grid;
  grid-template-columns: repeat(4, minmax(160px, 1fr)) 220px auto;
  gap: 8px;
  align-items: center;
}

.review-filter-bar input,
.review-filter-bar select {
  width: 100%;
}

.review-kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 10px;
}

.review-kpi-card {
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: #fff;
  padding: 12px;
}

.review-kpi-card p {
  margin: 0;
  color: var(--text-soft);
  font-size: 13px;
}

.review-kpi-card strong {
  display: block;
  margin-top: 6px;
  font-size: 26px;
}

.review-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.review-batch-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: #f8fafc;
}

.review-check-all,
.review-select {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.review-card {
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  padding: 12px;
  background: #fff;
  display: grid;
  gap: 10px;
}

.review-title {
  margin: 6px 0 0;
  font-size: 16px;
  line-height: 1.4;
}

.review-card-head {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.review-card-main {
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--line);
}

.review-card-judge {
  display: grid;
  gap: 6px;
}

.review-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(220px, 1fr));
  gap: 6px 12px;
}

.review-facts p {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}

.review-facts span {
  color: var(--text-soft);
}

.review-card-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.review-card-actions .btn-primary {
  font-weight: 700;
}

.review-card-actions .btn-danger {
  border-width: 1px;
}

.clamp-2,
.clamp-3 {
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
}

.clamp-2 {
  -webkit-line-clamp: 2;
}

.clamp-3 {
  -webkit-line-clamp: 3;
  word-break: break-word;
}

.review-meta,
.review-summary,
.review-biz {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 13px;
}

.review-drawer-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  z-index: 40;
  display: flex;
  justify-content: flex-end;
}

.review-drawer {
  width: min(640px, 92vw);
  height: 100vh;
  background: #fff;
  border-left: 1px solid var(--line);
  display: grid;
  grid-template-rows: auto 1fr auto;
}

.review-drawer-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid var(--line);
}

.review-drawer-head h3 {
  margin: 0;
}

.review-drawer-body {
  padding: 14px 16px;
  overflow: auto;
  display: grid;
  gap: 10px;
}

.review-drawer-title {
  margin: 0;
  line-height: 1.4;
}

.review-drawer-image {
  width: 100%;
  max-height: 220px;
  object-fit: cover;
  border: 1px solid var(--line);
  border-radius: 10px;
}

.review-drawer-block {
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 10px;
  background: #f8fafc;
}

.review-drawer-block strong {
  display: block;
  margin-bottom: 6px;
}

.review-drawer-block p {
  margin: 0 0 6px;
  color: var(--text-muted);
  word-break: break-word;
}

.review-drawer-block p:last-child {
  margin-bottom: 0;
}

.review-drawer-actions {
  border-top: 1px solid var(--line);
  padding: 12px 16px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.moderation-block,
.moderation-inline-panel {
  background: #fffdf7;
  border-color: #eadfb7;
}

.moderation-points {
  display: grid;
  gap: 6px;
}

.moderation-points ul {
  margin: 0;
  padding-left: 18px;
}

.post-admin-card {
  display: grid;
  gap: 8px;
}

.moderation-inline-panel {
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  padding: 12px;
}

.moderation-inline-panel p {
  margin: 0 0 6px;
  color: var(--text-muted);
}

.moderation-inline-panel p:last-child {
  margin-bottom: 0;
}

.trend-table-wrap {
  display: grid;
  gap: 8px;
}

.simple-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.simple-table th,
.simple-table td {
  border: 1px solid var(--line);
  padding: 6px 8px;
  text-align: left;
}

.simple-table thead th {
  background: #f8fafc;
}

.overview-list {
  display: grid;
  gap: 8px;
}

.overview-list p {
  margin: 0;
  display: flex;
  justify-content: space-between;
  border-bottom: 1px solid var(--line);
  padding-bottom: 8px;
}

.overview-list p:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.overview-list span {
  color: var(--text-soft);
}

.content-list {
  display: grid;
  gap: 10px;
}

.data-row {
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  padding: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  background: #fff;
}

.data-row p {
  margin: 5px 0 0;
  color: var(--text-muted);
  font-size: 13px;
}

.status-chip {
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: var(--surface-muted);
  color: var(--text-soft);
  font-size: 12px;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.content-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(280px, 1fr));
  gap: 12px;
}

.content-tab-panel {
  display: grid;
  gap: 12px;
}

.form-card {
  display: grid;
  gap: 8px;
}

.form-card h3 {
  margin: 0;
}

.field-caption {
  font-size: 13px;
  color: var(--text-soft);
  margin-top: 2px;
}

.required {
  color: #dc2626;
  margin-left: 2px;
}

.field-tip {
  margin: -2px 0 2px;
  font-size: 12px;
  color: var(--text-muted);
}

.field-error {
  margin: -2px 0 2px;
  font-size: 12px;
  color: #dc2626;
}

.input-error {
  border-color: #ef4444;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.12);
}

.mini-row {
  border-top: 1px solid var(--line);
  padding-top: 8px;
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.mini-row p {
  margin: 5px 0 0;
  color: var(--text-muted);
  font-size: 13px;
}

.news-row-main {
  flex: 1;
  min-width: 0;
}

.news-sync-floating {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 80;
  width: min(360px, calc(100vw - 32px));
  border: 1px solid #bfd9c8;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.16);
  padding: 16px 16px 14px;
  display: grid;
  gap: 10px;
}

.news-sync-floating-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.news-sync-floating-head strong {
  color: #0f172a;
  font-size: 15px;
}

.news-sync-floating-head span {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: #eefaf2;
  border: 1px solid #bfd9c8;
  color: #18603b;
  font-size: 12px;
}

.news-sync-floating p {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.7;
}

.news-sync-floating-steps {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.news-sync-floating-steps span {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: #f8fafc;
  border: 1px solid var(--line);
  color: #475569;
  font-size: 12px;
}

.news-sync-float-enter-active,
.news-sync-float-leave-active {
  transition: all 0.22s ease;
}

.news-sync-float-enter-from,
.news-sync-float-leave-to {
  opacity: 0;
  transform: translateY(12px);
}

.news-row-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.news-audit-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.news-audit-chip {
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
  padding: 12px 14px;
  display: grid;
  gap: 4px;
  text-align: left;
  cursor: pointer;
}

.news-audit-chip strong {
  font-size: 22px;
  line-height: 1;
  color: #0f172a;
}

.news-audit-chip span {
  font-size: 13px;
  color: var(--text-muted);
}

.news-audit-chip.active {
  border-color: #111827;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
}

.news-audit-chip.pending {
  background: #fff8e8;
  border-color: #f1d9a6;
}

.news-audit-chip.approved {
  background: #eefaf2;
  border-color: #bfd9c8;
}

.news-audit-chip.rejected {
  background: #fff1f2;
  border-color: #fecdd3;
}

.news-audit-filter-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 12px;
  margin-bottom: 12px;
}

.news-status-select {
  min-width: 140px;
}

.news-status-badge,
.news-status-inline {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid var(--line);
  font-size: 12px;
  line-height: 1.2;
}

.news-status-badge.is-pending,
.news-status-inline.is-pending {
  background: #fff8e8;
  border-color: #f1d9a6;
  color: #8a5a00;
}

.news-status-badge.is-approved,
.news-status-inline.is-approved {
  background: #eefaf2;
  border-color: #bfd9c8;
  color: #18603b;
}

.news-status-badge.is-rejected,
.news-status-inline.is-rejected {
  background: #fff1f2;
  border-color: #fecdd3;
  color: #b42318;
}

.news-detail-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.42);
  z-index: 50;
  display: grid;
  place-items: center;
  padding: 20px;
}

.news-detail-modal {
  width: min(1080px, 100%);
  max-height: 88vh;
  overflow: auto;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 18px;
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.18);
  display: grid;
  gap: 0;
}

.news-detail-head {
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--line);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.news-detail-head-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.news-detail-head h3,
.news-edit-card h4 {
  margin: 0;
}

.news-detail-head p {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-size: 13px;
}

.news-detail-body {
  padding: 18px 20px 20px;
  display: grid;
  gap: 14px;
}

.news-compare-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.news-compare-card {
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: #f8fafc;
  padding: 14px;
  display: grid;
  gap: 10px;
}

.news-compare-card.ai-card {
  background: #fcfcf7;
  border-color: #e7dfbf;
}

.news-ai-refresh-btn {
  border-color: #d9c88f;
  background: #fff8df;
  color: #7a5a00;
}

.news-sync-btn {
  border-color: #bfd9c8;
  background: #eefaf2;
  color: #18603b;
}

.news-sync-summary {
  border: 1px solid #cfe6d6;
  background: #f3fbf5;
  border-radius: 12px;
  padding: 10px 12px;
  color: #1f5131;
}

.news-sync-summary p {
  margin: 6px 0 0;
  color: #42634d;
  font-size: 13px;
}

.news-sync-source-list {
  margin-top: 10px;
  display: grid;
  gap: 6px;
}

.news-sync-source-item {
  margin: 0;
  color: #335942;
  line-height: 1.6;
}

.news-ai-refresh-btn:disabled {
  opacity: 0.68;
}

.news-card-label {
  display: inline-flex;
  width: fit-content;
  padding: 4px 10px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--line);
  font-size: 12px;
  color: var(--text-soft);
}

.news-compare-card p,
.news-block {
  margin: 0;
  display: grid;
  gap: 6px;
}

.news-inline-tip {
  padding: 8px 10px;
  border: 1px dashed #decf9b;
  border-radius: 10px;
  background: rgba(255, 248, 223, 0.72);
  color: #7a5a00;
  font-size: 12px;
  line-height: 1.6;
}

.news-compare-card span,
.news-block span {
  font-size: 12px;
  color: var(--text-soft);
}

.news-block pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-muted);
  max-height: 260px;
  overflow: auto;
}

.news-edit-card {
  background: #fff;
}

.news-detail-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.banner-file-input {
  display: none;
}

.banner-thumb {
  width: 84px;
  height: 54px;
  border-radius: 8px;
  border: 1px solid var(--line);
  object-fit: cover;
}

.banner-meta {
  flex: 1;
}

.pagination-bar {
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.pagination-bar.compact {
  margin-top: 10px;
}

.bulletin-tools {
  margin-bottom: 8px;
}

.bulletin-tools select {
  width: 200px;
}

.bulletin-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.stat-chip {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 12px;
  color: #334155;
  background: #f8fafc;
}

@media (max-width: 1120px) {
  .admin-layout {
    grid-template-columns: 1fr;
  }

  .left-nav {
    position: static;
    min-height: auto;
  }

  .stat-grid {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }

  .viz-grid {
    grid-template-columns: 1fr;
  }

  .analytics-grid {
    grid-template-columns: 1fr;
  }

  .review-kpi-grid {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }

  .review-filter-bar {
    grid-template-columns: 1fr 1fr;
  }

  .review-batch-bar {
    align-items: flex-start;
  }

  .review-facts {
    grid-template-columns: 1fr;
  }

  .review-drawer-mask {
    align-items: flex-end;
  }

  .review-drawer {
    width: 100vw;
    height: min(88vh, 100vh);
    border-left: 0;
    border-top: 1px solid var(--line);
    border-top-left-radius: 12px;
    border-top-right-radius: 12px;
  }

  .content-grid {
    grid-template-columns: 1fr;
  }

  .news-compare-grid {
    grid-template-columns: 1fr;
  }

  .news-sync-floating {
    right: 12px;
    left: 12px;
    bottom: 12px;
    width: auto;
  }

  .pagination-bar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
