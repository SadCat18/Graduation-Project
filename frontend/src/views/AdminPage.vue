<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AppIcon from '../components/AppIcon.vue'
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
const bulletinTypeStats = ref([])
const newsList = ref([])
const places = ref([])
const placeReviews = ref([])
const banners = ref([])
const videos = ref([])
const reports = ref([])
const reviewActivities = ref([])
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

const noticeForm = reactive({ title: '', content: '', status: '0' })
const newsForm = reactive({ title: '', content: '', category: '', cover: '' })
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
const newsTotalPages = computed(() => Math.max(1, Math.ceil((newsList.value.length || 0) / ADMIN_PAGE_SIZE)))
const placesTotalPages = computed(() => Math.max(1, Math.ceil((places.value.length || 0) / ADMIN_PAGE_SIZE)))
const bannersTotalPages = computed(() => Math.max(1, Math.ceil((banners.value.length || 0) / ADMIN_PAGE_SIZE)))
const videosTotalPages = computed(() => Math.max(1, Math.ceil((videos.value.length || 0) / ADMIN_PAGE_SIZE)))
const pagedNotices = computed(() => sliceByPage(notices.value, contentPageState.notices))
const pagedNews = computed(() => sliceByPage(newsList.value, contentPageState.news))
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
const reviewCenterTabs = [
  { key: 'all', label: '全部待办' },
  { key: 'activity', label: '活动' },
  { key: 'bulletin', label: '快讯' },
  { key: 'report', label: '举报' },
  { key: 'placeReview', label: '场地评价' },
  { key: 'done', label: '已处理' }
]

function fmtTime(value) {
  return String(value || '').replace('T', ' ') || '-'
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
    ]
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
    ]
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
  reviewDrawerVisible.value = true
}

function closeReviewDrawer() {
  reviewDrawerVisible.value = false
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
  newsForm.title = ''
  newsForm.content = ''
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
}

onMounted(refreshAll)
</script>

<template>
  <div class="admin-layout">
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
        <button class="btn-primary" @click="refreshAll">
          <AppIcon name="refresh" :size="15" />
          刷新
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
                <button v-if="item.status === '0'" class="btn-soft" @click="handleReviewCardAction(item, 'reject')">驳回</button>
              </template>
              <template v-else-if="item.type === 'bulletin'">
                <button v-if="item.status === '0'" class="btn-primary" @click="handleReviewCardAction(item, 'approve')">通过</button>
                <button class="btn-soft" @click="handleReviewCardAction(item, 'view')">查看</button>
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
            </div>
            <div class="review-drawer-actions">
              <template v-if="reviewDrawerItem.type === 'activity'">
                <button v-if="reviewDrawerItem.status === '0'" class="btn-primary" @click="handleReviewCardAction(reviewDrawerItem, 'approve')">通过</button>
                <button v-if="reviewDrawerItem.status === '0'" class="btn-soft" @click="handleReviewCardAction(reviewDrawerItem, 'reject')">驳回</button>
              </template>
              <template v-else-if="reviewDrawerItem.type === 'bulletin'">
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
        <div v-for="p in posts" :key="p.postId" class="data-row">
          <div>
            <strong>{{ p.title }}</strong>
            <p>置顶：{{ p.isTop === '1' ? '是' : '否' }}</p>
          </div>
          <div class="row-actions">
            <button @click="topPost(p)">{{ p.isTop === '1' ? '取消置顶' : '置顶' }}</button>
            <button class="btn-danger" @click="deletePost(p.postId)">删除</button>
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
          <p class="muted">快讯审核已迁移至“审核中心”。</p>
          <label class="field-caption">资讯标题 <span class="required">*</span></label>
          <input
            v-model="newsForm.title"
            :class="inputClass('news', 'title')"
            placeholder="例如：2026 年滑板入门训练路线"
            @input="clearError('news', 'title')"
          />
          <p v-if="formErrors.news.title" class="field-error">{{ formErrors.news.title }}</p>
          <label class="field-caption">资讯分类（示例：技巧 / 装备 / 赛事）</label>
          <input v-model="newsForm.category" placeholder="请输入资讯分类（可选）" />
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
            <strong>{{ n.title }}</strong>
            <button class="btn-danger" @click="deleteNews(n.newsId)">删除</button>
          </div>
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

  .pagination-bar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
