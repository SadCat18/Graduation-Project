<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { ACTIVITY_STATUS, ACTIVITY_STATUS_LABEL, SIGN_STATUS_LABEL, normalizeActivityStatus } from '../constants/activity'
import { getRole } from '../utils/auth'
import { normalizeMediaUrl } from '../utils/url'

const profile = reactive({
  username: '',
  avatar: '',
  gender: '',
  skateStyle: '',
  phone: '',
  bio: '',
  exp: 0,
  level: 1,
  nextLevelNeedExp: 100,
  remainToNextLevel: 100
})

const passwordForm = reactive({ oldPassword: '', newPassword: '' })
const videoForm = reactive({ title: '', cover: '', url: '', intro: '' })

const dashboard = ref({})
const videos = ref([])
const signedActivities = ref([])
const role = getRole()
const router = useRouter()

const avatarFileInputRef = ref(null)
const selectedAvatarFile = ref(null)
const avatarPreviewUrl = ref('')
const savingProfile = ref(false)
const savingPassword = ref(false)

const videoFileInputRef = ref(null)
const coverFileInputRef = ref(null)
const selectedVideoFile = ref(null)
const selectedCoverFile = ref(null)
const videoUploading = ref(false)

const myContentLoading = ref(false)
const profileTab = ref('overview')
const contentTab = ref('posts')
const myPosts = ref([])
const myCollections = ref([])
const myWatchLater = ref([])
const myActivities = ref([])
const currentUserId = ref(null)
const notifications = ref([])
const notificationLoading = ref(false)
const readFilter = ref('all')
const typeFilter = ref('ALL')
const MAX_OVERVIEW_SIGNED = 3
const MAX_OVERVIEW_NOTIFICATIONS = 3
const MAX_OVERVIEW_SAVED = 3
const activityCenterTab = ref('ALL')

const overviewSignedActivities = computed(() => (signedActivities.value || []).slice(0, MAX_OVERVIEW_SIGNED))
const overviewNotifications = computed(() => (notifications.value || []).slice(0, MAX_OVERVIEW_NOTIFICATIONS))
const overviewSavedItems = computed(() => {
  const collections = (myCollections.value || []).map((item) => ({
    ...item,
    sourceType: '收藏',
    sourceTime: item.collectTime || ''
  }))
  const watchLater = (myWatchLater.value || []).map((item) => ({
    ...item,
    sourceType: '稍后再看',
    sourceTime: item.watchLaterTime || ''
  }))
  return [...collections, ...watchLater]
    .sort((a, b) => String(b.sourceTime || '').localeCompare(String(a.sourceTime || '')))
    .slice(0, MAX_OVERVIEW_SAVED)
})
const activityCenterFilters = [
  { key: 'ALL', label: '全部' },
  { key: 'PENDING', label: '待审核' },
  { key: 'APPROVED', label: '已报名' },
  { key: 'CHECKED_IN', label: '已签到' },
  { key: 'ENDED', label: '已结束' },
  { key: 'REVIEWABLE', label: '可评价场地' }
]
const filteredSignedActivities = computed(() => {
  const list = signedActivities.value || []
  if (activityCenterTab.value === 'ALL') return list
  return list.filter((item) => {
    const activityStatus = normalizeActivityStatus(item.activityStatus ?? item.status)
    const approved = item.signStatus === '1'
    const checkedIn = item.isCheckin === '1'
    const ended = activityStatus === ACTIVITY_STATUS.ENDED
    const reviewable = ended && !!item.placeId

    if (activityCenterTab.value === 'PENDING') return item.signStatus === '0'
    if (activityCenterTab.value === 'APPROVED') return approved && !checkedIn && !ended
    if (activityCenterTab.value === 'CHECKED_IN') return checkedIn
    if (activityCenterTab.value === 'ENDED') return ended
    if (activityCenterTab.value === 'REVIEWABLE') return reviewable
    return true
  })
})
const myTeachingVideos = computed(() => {
  const list = videos.value || []
  return list.filter((item) => {
    const ownerId = item?.userId ?? item?.creatorId ?? item?.authorId ?? item?.publisherId ?? item?.uploadUserId
    if (currentUserId.value != null && ownerId != null) {
      return Number(ownerId) === Number(currentUserId.value)
    }
    const ownerName = item?.username ?? item?.creatorName ?? item?.authorName ?? item?.publisherName ?? item?.nickname
    if (profile.username && ownerName) {
      return String(ownerName) === String(profile.username)
    }
    return true
  })
})

function sanitizePhone(value) {
  const phone = String(value || '').trim()
  return /^1\d{10}$/.test(phone) ? phone : ''
}

function normalizeGender(value) {
  return value === '男' || value === '女' ? value : ''
}

function handleAvatarError() {
  profile.avatar = ''
}

async function loadProfile() {
  const user = await api.profile()
  currentUserId.value = user?.userId ?? null
  profile.username = user?.username || ''
  profile.avatar = normalizeMediaUrl(user?.avatar)
  profile.gender = normalizeGender(user?.gender)
  profile.skateStyle = user?.skateStyle || ''
  profile.phone = sanitizePhone(user?.phone)
  profile.bio = user?.bio || ''
  profile.exp = Number(user?.exp || 0)
  profile.level = Number(user?.level || 1)
  profile.nextLevelNeedExp = Number(user?.nextLevelNeedExp || 100)
  profile.remainToNextLevel = Number(user?.remainToNextLevel || 100)
}

async function loadOthers() {
  const [board, videoList, signedList] = await Promise.all([
    api.dashboard(),
    api.videos(),
    api.mySignedActivities()
  ])
  dashboard.value = board || {}
  videos.value = videoList || []
  signedActivities.value = signedList || []
}

async function loadNotifications() {
  notificationLoading.value = true
  try {
    const list = await api.notifications({
      readStatus: readFilter.value,
      msgType: typeFilter.value
    })
    notifications.value = list || []
  } finally {
    notificationLoading.value = false
  }
}

async function loadMyContent() {
  myContentLoading.value = true
  try {
    const data = await api.myContent()
    myPosts.value = data?.myPosts || []
    myCollections.value = data?.myCollections || []
    myWatchLater.value = data?.myWatchLater || []
    myActivities.value = data?.myActivities || []
    try {
      myActivities.value = await api.myPublishedActivities()
    } catch (_) {}
  } finally {
    myContentLoading.value = false
  }
}

async function removeCollection(postId) {
  await api.collectPost(postId)
  await loadMyContent()
}

async function removeWatchLater(postId) {
  await api.watchLaterPost(postId)
  await loadMyContent()
}

async function removeMyPost(postId) {
  try {
    await api.deletePost(postId)
    await loadMyContent()
    await loadOthers()
    alert('帖子已删除')
  } catch (e) {
    alert(e?.message || '删除失败，请稍后重试')
  }
}

async function saveProfile() {
  const phone = String(profile.phone || '').trim()
  if (phone && !/^1\d{10}$/.test(phone)) {
    alert('手机号格式不正确，请输入 11 位手机号')
    return
  }

  savingProfile.value = true
  try {
    if (selectedAvatarFile.value) {
      const avatarUrl = await api.uploadPostImage(selectedAvatarFile.value)
      profile.avatar = normalizeMediaUrl(avatarUrl)
      clearSelectedAvatar()
    }

    await api.updateProfile({
      avatar: profile.avatar || null,
      gender: profile.gender || null,
      skateStyle: profile.skateStyle?.trim() || null,
      phone: phone || null,
      bio: profile.bio?.trim() || null
    })

    await loadProfile()
    alert('个人资料已保存')
  } catch (e) {
    alert(e?.message || '保存失败，请稍后重试')
  } finally {
    savingProfile.value = false
  }
}

async function savePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    alert('请填写旧密码和新密码')
    return
  }
  savingPassword.value = true
  try {
    await api.updatePassword(passwordForm)
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    alert('密码已更新')
  } catch (e) {
    alert(e?.message || '密码更新失败，请稍后重试')
  } finally {
    savingPassword.value = false
  }
}

async function markRead(id) {
  await api.readMessage(id)
  await Promise.all([loadOthers(), loadNotifications()])
}

async function markAllRead() {
  await api.readAllMessages()
  await Promise.all([loadOthers(), loadNotifications()])
}

function triggerAvatarSelect() {
  avatarFileInputRef.value?.click()
}

function clearSelectedAvatar() {
  if (avatarPreviewUrl.value) {
    URL.revokeObjectURL(avatarPreviewUrl.value)
  }
  avatarPreviewUrl.value = ''
  selectedAvatarFile.value = null
  if (avatarFileInputRef.value) avatarFileInputRef.value.value = ''
}

function onSelectAvatar(event) {
  const file = (event.target.files || [])[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    alert('请选择图片文件')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    alert('头像图片不能超过 10MB')
    return
  }

  clearSelectedAvatar()
  selectedAvatarFile.value = file
  avatarPreviewUrl.value = URL.createObjectURL(file)
}

function triggerVideoSelect() {
  videoFileInputRef.value?.click()
}

function triggerCoverSelect() {
  coverFileInputRef.value?.click()
}

function onSelectVideo(event) {
  const file = (event.target.files || [])[0]
  if (!file) return
  if (!file.type.startsWith('video/')) {
    alert('请选择视频文件')
    return
  }
  if (file.size > 300 * 1024 * 1024) {
    alert('视频大小不能超过 300MB')
    return
  }
  selectedVideoFile.value = file
  if (videoFileInputRef.value) videoFileInputRef.value.value = ''
}

function onSelectCover(event) {
  const file = (event.target.files || [])[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    alert('请选择图片文件')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    alert('封面图片不能超过 10MB')
    return
  }
  selectedCoverFile.value = file
  if (coverFileInputRef.value) coverFileInputRef.value.value = ''
}

function clearVideoSelection() {
  selectedVideoFile.value = null
}

function clearCoverSelection() {
  selectedCoverFile.value = null
}

async function createVideo() {
  if (!videoForm.title.trim()) {
    alert('请填写视频标题')
    return
  }
  if (!selectedVideoFile.value && !videoForm.url.trim()) {
    alert('请上传本地视频文件或填写视频地址')
    return
  }

  videoUploading.value = true
  try {
    let finalVideoUrl = videoForm.url.trim()
    let finalCoverUrl = videoForm.cover.trim()

    if (selectedVideoFile.value) {
      finalVideoUrl = await api.uploadVideoFile(selectedVideoFile.value)
    }
    if (selectedCoverFile.value) {
      finalCoverUrl = await api.uploadPostImage(selectedCoverFile.value)
    }

    await api.createVideo({
      title: videoForm.title.trim(),
      url: normalizeMediaUrl(finalVideoUrl),
      cover: normalizeMediaUrl(finalCoverUrl),
      intro: videoForm.intro.trim()
    })

    videoForm.title = ''
    videoForm.url = ''
    videoForm.cover = ''
    videoForm.intro = ''
    selectedVideoFile.value = null
    selectedCoverFile.value = null
    await loadOthers()
    alert('视频提交成功')
  } catch (e) {
    alert(e?.message || '视频提交失败，请稍后重试')
  } finally {
    videoUploading.value = false
  }
}

function formatActivityTime(value) {
  if (!value) return '未设置'
  return String(value).replace('T', ' ')
}

function signedAddress(item) {
  return [item.city, item.district, item.place, item.address].filter(Boolean).join(' · ') || '未设置地点'
}

function signStatusLabel(item) {
  return SIGN_STATUS_LABEL[item.signStatus] || '未知'
}

function activityStatusLabel(item) {
  const status = normalizeActivityStatus(item.activityStatus ?? item.status)
  return ACTIVITY_STATUS_LABEL[status] || '未知'
}

async function checkinSignedActivity(item) {
  try {
    await api.checkinActivity(item.activityId)
    await loadOthers()
    alert('签到成功')
  } catch (e) {
    alert(e?.message || '签到失败，请稍后重试')
  }
}

async function cancelSignedActivity(item) {
  try {
    await api.cancelSignActivity(item.activityId)
    await loadOthers()
    alert('已退出该活动报名')
  } catch (e) {
    alert(e?.message || '退出失败，请稍后重试')
  }
}

function canCancelSignedActivity(item) {
  if (!(item.signStatus === '0' || item.signStatus === '1')) return false
  const activityStatus = normalizeActivityStatus(item.activityStatus ?? item.status)
  return activityStatus !== ACTIVITY_STATUS.CANCELED && activityStatus !== ACTIVITY_STATUS.ENDED
}

function formatDateTime(value) {
  if (!value) return '未设置'
  return String(value).replace('T', ' ')
}

function goPostDetail(postId) {
  router.push(`/community/post/${postId}`)
}

function goActivitiesPage() {
  router.push('/activities')
}

function goPlaceReview(placeId) {
  if (!placeId) return
  router.push(`/places/${placeId}`)
}

function switchToTab(tab) {
  profileTab.value = tab
}

function switchToContentTab(tab) {
  profileTab.value = 'content'
  contentTab.value = tab
}

async function applyNotificationFilter() {
  await loadNotifications()
}

async function openNotification(item) {
  if (item.isRead === '0') {
    await markRead(item.msgId)
  }
  if (!item.jumpPath) return
  await router.push(item.jumpPath)
}

onMounted(async () => {
  if (role !== 'ADMIN') {
    await loadProfile()
    await Promise.all([loadOthers(), loadMyContent(), loadNotifications()])
  }
})

onBeforeUnmount(() => {
  clearSelectedAvatar()
})
</script>

<template>
  <div v-if="role !== 'ADMIN'" class="page-grid profile-page">
    <section class="profile-workbench">
      <div class="card identity-card">
        <div class="identity-avatar">
          <img
            v-if="avatarPreviewUrl || profile.avatar"
            :src="avatarPreviewUrl || profile.avatar"
            alt="头像预览"
            @error="handleAvatarError"
          />
          <span v-else>暂无头像</span>
        </div>
        <div class="identity-main">
          <h2>{{ profile.username || '未命名用户' }}</h2>
          <p class="muted">滑板风格：{{ profile.skateStyle || '暂未填写' }}</p>
          <div class="identity-metrics">
            <div><span>当前等级</span><strong>Lv{{ profile.level || 1 }}</strong></div>
            <div><span>当前经验</span><strong>{{ profile.exp || 0 }}</strong></div>
            <div><span>下一级经验</span><strong>{{ profile.nextLevelNeedExp || 0 }}</strong></div>
            <div><span>距离升级</span><strong>{{ profile.remainToNextLevel || 0 }}</strong></div>
          </div>
        </div>
      </div>

      <div class="main-tabs-wrap">
        <div class="main-tabs">
          <button :class="{ active: profileTab === 'overview' }" @click="profileTab = 'overview'">概览</button>
          <button :class="{ active: profileTab === 'content' }" @click="profileTab = 'content'">我的内容</button>
          <button :class="{ active: profileTab === 'activities' }" @click="profileTab = 'activities'">活动中心</button>
          <button :class="{ active: profileTab === 'notifications' }" @click="profileTab = 'notifications'">通知中心</button>
          <button :class="{ active: profileTab === 'creation' }" @click="profileTab = 'creation'">创作中心</button>
          <button :class="{ active: profileTab === 'settings' }" @click="profileTab = 'settings'">账号设置</button>
        </div>
      </div>

      <div v-if="profileTab === 'overview'" class="tab-panel">
        <div class="card">
          <div class="section-head"><h3>我的概览</h3></div>
          <div class="overview-stat-grid">
            <div class="overview-stat-card"><span>我的帖子</span><strong>{{ dashboard.postCount || 0 }}</strong></div>
            <div class="overview-stat-card"><span>我的活动</span><strong>{{ dashboard.activityCount || 0 }}</strong></div>
            <div class="overview-stat-card"><span>未读通知</span><strong>{{ dashboard.unreadMsgCount || 0 }}</strong></div>
            <div class="overview-stat-card"><span>当前等级</span><strong>Lv{{ profile.level || 1 }}</strong></div>
            <div class="overview-stat-card"><span>当前经验</span><strong>{{ profile.exp || 0 }}</strong></div>
            <div class="overview-stat-card"><span>距离下一级</span><strong>{{ profile.remainToNextLevel || 0 }}</strong></div>
          </div>
        </div>

        <div class="overview-snapshot-grid">
          <div class="card">
            <div class="section-head section-head-inline">
              <h3>最近报名活动</h3>
              <button class="btn-soft" @click="switchToTab('activities')">查看全部</button>
            </div>
            <div v-if="!overviewSignedActivities.length" class="empty-tip">你最近还没有报名活动，去活动中心看看新局吧。</div>
            <div v-for="item in overviewSignedActivities" :key="`overview-sign-${item.signId || item.activityId}`" class="list-item compact-item">
              <div class="item-title-row">
                <strong>{{ item.title }}</strong>
                <span class="tag">{{ item.isCheckin === '1' ? '已签到' : '未签到' }}</span>
              </div>
              <p class="muted">{{ formatActivityTime(item.activityTime) }}</p>
            </div>
          </div>

          <div class="card">
            <div class="section-head section-head-inline">
              <h3>最近通知</h3>
              <button class="btn-soft" @click="switchToTab('notifications')">查看全部</button>
            </div>
            <p v-if="notificationLoading" class="muted">加载中...</p>
            <div v-else-if="!overviewNotifications.length" class="empty-tip">暂无通知消息，后续动态会在这里出现。</div>
            <div v-for="m in overviewNotifications" :key="`overview-msg-${m.msgId}`" class="list-item compact-item">
              <div class="item-title-row">
                <strong>{{ m.msgTypeLabel }}</strong>
                <span class="tag">{{ m.isRead === '0' ? '未读' : '已读' }}</span>
              </div>
              <p>{{ m.content }}</p>
            </div>
          </div>

          <div class="card">
            <div class="section-head section-head-inline">
              <h3>最近收藏/稍后再看</h3>
              <button class="btn-soft" @click="switchToContentTab('collections')">查看全部</button>
            </div>
            <div v-if="!overviewSavedItems.length" class="empty-tip">还没有收藏或稍后再看内容，逛到喜欢的帖子可以先存起来。</div>
            <div v-for="item in overviewSavedItems" :key="`overview-saved-${item.postId}-${item.sourceTime}`" class="list-item compact-item">
              <div class="item-title-row">
                <strong>{{ item.title }}</strong>
                <span class="tag">{{ item.sourceType }}</span>
              </div>
              <p class="muted">{{ formatDateTime(item.sourceTime) }}</p>
              <button class="btn-soft" @click="goPostDetail(item.postId)">查看帖子</button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="profileTab === 'content'" class="tab-panel">
        <div class="card">
          <div class="section-head"><h3>我的内容</h3></div>
        <div class="tabs">
          <button :class="{ active: contentTab === 'posts' }" @click="contentTab = 'posts'">我的帖子</button>
          <button :class="{ active: contentTab === 'collections' }" @click="contentTab = 'collections'">我的收藏</button>
          <button :class="{ active: contentTab === 'watchLater' }" @click="contentTab = 'watchLater'">稍后再看</button>
          <button :class="{ active: contentTab === 'activities' }" @click="contentTab = 'activities'">我发布的活动</button>
        </div>

        <p v-if="myContentLoading" class="muted">加载中...</p>

        <template v-if="!myContentLoading && contentTab === 'posts'">
          <div v-if="!myPosts.length" class="empty-tip">你还没有发布帖子，去社区分享第一个内容吧。</div>
          <div v-for="item in myPosts" :key="item.postId" class="my-content-item">
            <div class="item-title-row">
              <strong>{{ item.title }}</strong>
              <span class="tag">{{ item.category || '未分类' }}</span>
            </div>
            <p class="muted">发布时间：{{ formatDateTime(item.createTime) }}</p>
            <p class="muted">点赞 {{ item.likeCount || 0 }} · 评论 {{ item.commentCount || 0 }} · 收藏 {{ item.collectCount || 0 }}</p>
            <div class="inline">
              <button class="btn-soft" @click="goPostDetail(item.postId)">查看帖子详情</button>
              <button class="btn-danger" @click="removeMyPost(item.postId)">删除帖子</button>
            </div>
          </div>
        </template>

        <template v-if="!myContentLoading && contentTab === 'collections'">
          <div v-if="!myCollections.length" class="empty-tip">你还没有收藏帖子，遇到喜欢的内容记得先收藏。</div>
          <div v-for="item in myCollections" :key="`${item.postId}-${item.collectTime}`" class="my-content-item">
            <div class="item-title-row">
              <strong>{{ item.title }}</strong>
              <span class="tag">{{ item.category || '未分类' }}</span>
            </div>
            <p class="muted">收藏时间：{{ formatDateTime(item.collectTime) }}</p>
            <p class="muted">点赞 {{ item.likeCount || 0 }} · 评论 {{ item.commentCount || 0 }} · 收藏 {{ item.collectCount || 0 }}</p>
            <div class="inline">
              <button class="btn-soft" @click="goPostDetail(item.postId)">查看帖子详情</button>
              <button class="btn-soft" @click="removeCollection(item.postId)">移除收藏</button>
            </div>
          </div>
        </template>

        <template v-if="!myContentLoading && contentTab === 'watchLater'">
          <div v-if="!myWatchLater.length" class="empty-tip">还没有加入稍后再看的帖子。</div>
          <div v-for="item in myWatchLater" :key="`${item.postId}-${item.watchLaterTime}`" class="my-content-item">
            <div class="item-title-row">
              <strong>{{ item.title }}</strong>
              <span class="tag">{{ item.category || '未分类' }}</span>
            </div>
            <p class="muted">加入时间：{{ formatDateTime(item.watchLaterTime) }}</p>
            <p class="muted">点赞 {{ item.likeCount || 0 }} · 评论 {{ item.commentCount || 0 }} · 收藏 {{ item.collectCount || 0 }}</p>
            <div class="inline">
              <button class="btn-soft" @click="goPostDetail(item.postId)">查看帖子详情</button>
              <button class="btn-soft" @click="removeWatchLater(item.postId)">移除</button>
            </div>
          </div>
        </template>

        <template v-if="!myContentLoading && contentTab === 'activities'">
          <div v-if="!myActivities.length" class="empty-tip">你还没有发布活动，去同城约板发起一次活动吧。</div>
          <div v-for="item in myActivities" :key="item.activityId" class="my-content-item">
            <div class="item-title-row">
              <strong>{{ item.title }}</strong>
              <span class="tag">{{ activityStatusLabel(item) }}</span>
            </div>
            <p class="muted">发布时间：{{ formatDateTime(item.createTime) }}</p>
            <p class="muted">活动时间：{{ formatDateTime(item.activityTime) }}</p>
            <p class="muted">报名人数：{{ item.signNum || 0 }} / {{ item.maxNum || '不限' }}</p>
            <p class="muted">活动地点：{{ signedAddress(item) }}</p>
            <div class="inline">
              <button class="btn-soft" @click="goActivitiesPage">查看活动详情</button>
              <button class="btn-soft" @click="goActivitiesPage">前往活动页管理</button>
            </div>
          </div>
        </template>
      </div>
      </div>

      <div v-if="profileTab === 'activities'" class="tab-panel">
        <div class="card">
          <div class="section-head section-head-inline">
            <h3>活动中心 · 我已报名活动</h3>
            <button class="btn-soft" @click="goActivitiesPage">去同城约板</button>
          </div>
          <div class="activity-center-filters">
            <button
              v-for="filter in activityCenterFilters"
              :key="filter.key"
              :class="{ active: activityCenterTab === filter.key }"
              @click="activityCenterTab = filter.key"
            >
              {{ filter.label }}
            </button>
          </div>
          <div v-if="!signedActivities.length" class="empty-tip">暂未报名活动，去同城约板挑一个感兴趣的活动吧。</div>
          <div v-else-if="!filteredSignedActivities.length" class="empty-tip">当前筛选下暂无活动，试试切换其他状态。</div>
          <div v-for="item in filteredSignedActivities" :key="item.signId || item.activityId" class="signed-item">
            <div class="signed-top">
              <strong>{{ item.title }}</strong>
              <span class="tag">{{ item.isCheckin === '1' ? '已签到' : '未签到' }}</span>
            </div>
            <p class="muted">活动时间：{{ formatActivityTime(item.activityTime) }}</p>
            <p class="muted">活动地点：{{ signedAddress(item) }}</p>
            <p class="muted">活动状态：{{ activityStatusLabel(item) }}</p>
            <p class="muted">报名状态：{{ signStatusLabel(item) }}</p>
            <p class="muted">报名时间：{{ formatActivityTime(item.signTime) }}</p>
            <div class="inline">
              <button class="btn-primary" :disabled="item.isCheckin === '1' || item.signStatus !== '1'" @click="checkinSignedActivity(item)">
                {{ item.isCheckin === '1' ? '已签到' : '去签到' }}
              </button>
              <button class="btn-soft" :disabled="!canCancelSignedActivity(item)" @click="cancelSignedActivity(item)">
                退出报名
              </button>
              <button
                v-if="normalizeActivityStatus(item.activityStatus ?? item.status) === ACTIVITY_STATUS.ENDED && item.placeId"
                class="btn-soft"
                @click="goPlaceReview(item.placeId)"
              >
                评价场地
              </button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="profileTab === 'notifications'" class="tab-panel">
        <div class="card">
          <div class="section-head section-head-inline">
            <h3>通知中心</h3>
            <span class="unread-badge">未读 {{ dashboard.unreadMsgCount || 0 }}</span>
          </div>
          <p class="muted">这里集中处理你的评论、点赞、活动和系统通知。</p>
          <div class="notify-toolbar">
            <select v-model="readFilter" @change="applyNotificationFilter">
              <option value="all">全部</option>
              <option value="0">未读</option>
              <option value="1">已读</option>
            </select>
            <select v-model="typeFilter" @change="applyNotificationFilter">
              <option value="ALL">全部类型</option>
              <option value="COMMENT">评论</option>
              <option value="LIKE">点赞</option>
              <option value="ACTIVITY">活动</option>
              <option value="SYSTEM">系统</option>
            </select>
            <button class="btn-soft" @click="markAllRead">全部已读</button>
          </div>
          <p v-if="notificationLoading" class="muted">加载中...</p>
          <div v-if="!notificationLoading && !notifications.length" class="empty-tip">暂无通知消息。</div>
          <div v-for="m in notifications" :key="m.msgId" class="list-item notification-item">
            <div class="item-title-row">
              <strong>{{ m.msgTypeLabel }}</strong>
              <span class="tag">{{ m.isRead === '0' ? '未读' : '已读' }}</span>
            </div>
            <p>{{ m.content }}</p>
            <p class="muted">{{ m.createTime?.replace('T', ' ') }}</p>
            <div class="inline">
              <button v-if="m.isRead === '0'" class="btn-soft" @click="markRead(m.msgId)">标记已读</button>
              <button v-if="m.jumpPath" class="btn-soft" @click="openNotification(m)">查看相关内容</button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="profileTab === 'creation'" class="tab-panel">
        <div class="card">
          <div class="section-head"><h3>上传教学视频</h3></div>
          <div class="form-grid">
            <input v-model="videoForm.title" placeholder="视频标题（必填）" />
            <input v-model="videoForm.url" placeholder="视频地址 URL（可选，不上传本地视频时填写）" />
          </div>

          <div class="upload-row">
            <input
              ref="videoFileInputRef"
              class="hidden-file-input"
              type="file"
              accept="video/*"
              @change="onSelectVideo"
            />
            <button type="button" class="btn-soft" @click="triggerVideoSelect">
              <AppIcon name="upload" :size="15" />
              选择本地视频
            </button>
            <span class="file-name" v-if="selectedVideoFile">{{ selectedVideoFile.name }}</span>
            <button v-if="selectedVideoFile" type="button" @click="clearVideoSelection">取消</button>
          </div>
          <p class="muted">支持本地视频上传（推荐 mp4），最大 300MB。</p>

          <div class="form-grid">
            <input v-model="videoForm.cover" placeholder="封面 URL（可选）" />
            <div class="upload-row">
              <input
                ref="coverFileInputRef"
                class="hidden-file-input"
                type="file"
                accept="image/*"
                @change="onSelectCover"
              />
              <button type="button" class="btn-soft" @click="triggerCoverSelect">
                <AppIcon name="upload" :size="15" />
                选择本地封面
              </button>
              <span class="file-name" v-if="selectedCoverFile">{{ selectedCoverFile.name }}</span>
              <button v-if="selectedCoverFile" type="button" @click="clearCoverSelection">取消</button>
            </div>
          </div>

          <textarea v-model="videoForm.intro" placeholder="视频简介" />
          <button class="btn-primary" :disabled="videoUploading" @click="createVideo">
            {{ videoUploading ? '上传并提交中...' : '提交视频' }}
          </button>
        </div>

        <div class="card">
          <div class="section-head"><h3>我的教学视频</h3></div>
          <div v-if="!myTeachingVideos.length" class="empty-tip">你还没有发布教学视频，上传第一条来沉淀你的创作内容吧。</div>
          <div v-for="v in myTeachingVideos" :key="v.videoId" class="list-item">
            <strong>{{ v.title }}</strong>
            <p class="muted">{{ v.intro }}</p>
          </div>
        </div>

        <div class="card">
          <div class="section-head"><h3>草稿箱（预留）</h3></div>
          <div class="empty-tip">后续可在这里保存未完成的视频草稿、封面和简介。</div>
        </div>
      </div>
      <div v-if="profileTab === 'settings'" class="tab-panel">
        <div class="card">
          <div class="section-head"><h3>个人资料</h3></div>

          <div class="avatar-upload-block">
            <input
              ref="avatarFileInputRef"
              class="hidden-file-input"
              type="file"
              accept="image/*"
              @change="onSelectAvatar"
            />

            <div class="inline">
              <button type="button" class="btn-soft" @click="triggerAvatarSelect">
                <AppIcon name="upload" :size="15" />
                选择本地头像
              </button>
              <button v-if="selectedAvatarFile" type="button" @click="clearSelectedAvatar">取消选择</button>
            </div>
            <p class="muted">仅支持本地图片，保存资料时自动上传。</p>
          </div>

          <div class="gender-row">
            <span class="field-title">性别</span>
            <label class="radio-option">
              <input v-model="profile.gender" type="radio" value="男" />
              <span>男</span>
            </label>
            <label class="radio-option">
              <input v-model="profile.gender" type="radio" value="女" />
              <span>女</span>
            </label>
          </div>

          <div class="form-grid">
            <input v-model="profile.skateStyle" placeholder="滑板风格（街式、碗池等）" />
            <input
              v-model="profile.phone"
              maxlength="11"
              inputmode="numeric"
              placeholder="手机号（11位）"
            />
          </div>

          <textarea v-model="profile.bio" placeholder="个人简介" />
          <button class="btn-primary" :disabled="savingProfile" @click="saveProfile">
            {{ savingProfile ? '保存中...' : '保存资料' }}
          </button>
        </div>

        <div class="card">
          <div class="section-head"><h3>修改密码</h3></div>
          <div class="form-grid">
            <input v-model="passwordForm.oldPassword" type="password" placeholder="旧密码" />
            <input v-model="passwordForm.newPassword" type="password" placeholder="新密码" />
          </div>
          <button :disabled="savingPassword" @click="savePassword">
            {{ savingPassword ? '更新中...' : '更新密码' }}
          </button>
        </div>
      </div>
    </section>
  </div>

  <div v-else class="card">
    <p>管理员请前往后台管理页维护信息。</p>
  </div>
</template>

<style scoped>
.profile-page {
  display: block;
}

.profile-workbench {
  min-width: 0;
}

.identity-card {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 16px;
  align-items: center;
  margin-bottom: 12px;
}

.identity-avatar {
  width: 92px;
  height: 92px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: var(--surface-muted);
  display: grid;
  place-items: center;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 12px;
}

.identity-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.identity-main h2 {
  margin: 0 0 6px;
}

.identity-metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 8px;
  margin-top: 10px;
}

.identity-metrics div {
  padding: 9px 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--surface-muted);
}

.identity-metrics span {
  display: block;
  color: var(--text-soft);
  font-size: 12px;
}

.identity-metrics strong {
  font-size: 18px;
}

.main-tabs-wrap {
  margin-bottom: 12px;
}

.main-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.main-tabs button {
  border: 1px solid var(--line);
  background: #fff;
}

.main-tabs button.active {
  background: var(--surface-muted);
  border-color: var(--text-soft);
}

.tab-panel {
  display: grid;
  gap: 12px;
}

.section-head-inline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.form-grid {
  display: grid;
  gap: 10px;
  margin-bottom: 10px;
}

.avatar-upload-block {
  margin-bottom: 12px;
}

.hidden-file-input {
  display: none;
}

.gender-row {
  display: flex;
  align-items: center;
  gap: 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  margin-bottom: 10px;
}

.field-title {
  color: var(--text-soft);
  font-size: 14px;
}

.radio-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text);
  cursor: pointer;
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.file-name {
  font-size: 13px;
  color: var(--text-soft);
}

.notify-toolbar {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 8px;
  margin-bottom: 10px;
}

.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.tabs button {
  border: 1px solid var(--line);
  background: #fff;
}

.tabs button.active {
  background: var(--surface-muted);
  border-color: var(--text-soft);
}

.unread-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 84px;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: var(--surface-muted);
  color: var(--text-soft);
  font-size: 13px;
}

.activity-center-filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.activity-center-filters button {
  border: 1px solid var(--line);
  background: #fff;
}

.activity-center-filters button.active {
  background: var(--surface-muted);
  border-color: var(--text-soft);
}

.my-content-item {
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  margin-bottom: 10px;
  min-width: 0;
}

.manage-box {
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 10px;
  margin-top: 10px;
  background: #f8fafc;
}

.sign-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-top: 1px solid var(--line);
}

.sign-row:first-of-type {
  border-top: 0;
}

.item-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.stats-box {
  display: grid;
  gap: 8px;
}

.stats-box div {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  border-bottom: 1px solid var(--line);
  padding-bottom: 9px;
}

.stats-box div:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.stats-box span {
  color: var(--text-soft);
}

.stats-box strong {
  font-size: 22px;
}

.overview-stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.overview-stat-card {
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 12px;
  background: #fff;
}

.overview-stat-card span {
  display: block;
  color: var(--text-soft);
  font-size: 13px;
  margin-bottom: 6px;
}

.overview-stat-card strong {
  font-size: 24px;
}

.overview-snapshot-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.compact-item {
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 10px;
  margin-top: 8px;
}

.empty-tip {
  color: var(--text-muted);
  border: 1px dashed var(--line);
  border-radius: var(--radius-sm);
  padding: 12px;
}

.signed-item {
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 12px;
  margin-top: 10px;
  background: #fff;
}

.signed-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.notification-item {
  margin-top: 8px;
}

.notification-item p,
.my-content-item p,
.signed-item p {
  word-break: break-word;
}

@media (max-width: 768px) {
  .identity-card {
    grid-template-columns: 1fr;
    justify-items: start;
  }

  .overview-stat-grid,
  .overview-snapshot-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .notify-toolbar :deep(select),
  .notify-toolbar :deep(button),
  .tabs button,
  .form-grid :deep(input),
  textarea,
  .inline :deep(button) {
    min-height: 42px;
  }

  .tabs {
    flex-wrap: nowrap;
    overflow-x: auto;
    padding-bottom: 4px;
  }

  .activity-center-filters {
    flex-wrap: nowrap;
    overflow-x: auto;
    padding-bottom: 4px;
  }

  .main-tabs {
    flex-wrap: nowrap;
    overflow-x: auto;
    padding-bottom: 4px;
  }

  .notify-toolbar {
    grid-template-columns: 1fr;
  }

  .gender-row {
    flex-wrap: wrap;
    gap: 10px;
  }

  .upload-row {
    flex-direction: column;
    align-items: stretch;
  }

  .upload-row button {
    width: 100%;
  }
}
</style>
