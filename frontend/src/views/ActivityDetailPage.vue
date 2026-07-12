<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { ACTIVITY_STATUS_LABEL, SIGN_STATUS_LABEL, normalizeActivityStatus } from '../constants/activity'
import { getToken } from '../utils/auth'

const route = useRoute()
const router = useRouter()

const detail = ref(null)
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionMessage = ref('')
const actionError = ref('')

const activityId = computed(() => Number(route.params.id))
const activityIntro = computed(() => (detail.value?.activityDesc || '').trim())
const activityContent = computed(() => (detail.value?.content || '').trim())
const hasSeparateContent = computed(() => activityContent.value && activityContent.value !== activityIntro.value)
const displayIntro = computed(() => activityIntro.value || activityContent.value)
const isLoggedIn = computed(() => !!getToken())
const signStatusText = computed(() => {
  if (!isLoggedIn.value) return '登录后可报名、签到和参与复盘'
  if (!detail.value?.signStatus) return '尚未报名'
  if (detail.value?.isCheckin === '1') return '已签到'
  return SIGN_STATUS_LABEL[detail.value.signStatus] || '状态未知'
})
const primaryActionText = computed(() => {
  if (!isLoggedIn.value) return '登录后报名'
  if (detail.value?.canJoin) return '报名参加'
  if (detail.value?.canCheckin) return '现场签到'
  if (detail.value?.canCancelSign) return '退出报名'
  return ''
})

function displayAddress(item) {
  const values = [item?.city, item?.district, item?.place, item?.address].filter(Boolean)
  return values.join(' · ') || '未设置地点'
}

function displayStatus(item) {
  const status = normalizeActivityStatus(item?.activityStatus ?? item?.status)
  return ACTIVITY_STATUS_LABEL[status] || '未知状态'
}

async function loadData() {
  if (!Number.isFinite(activityId.value)) {
    error.value = '活动参数错误'
    return
  }
  loading.value = true
  error.value = ''
  try {
    if (isLoggedIn.value) {
      try {
        detail.value = await api.myActivityDetail(activityId.value)
      } catch (e) {
        if (e?.response?.status === 401 || e?.response?.status === 403) {
          detail.value = await api.activityDetail(activityId.value)
        } else {
          throw e
        }
      }
    } else {
      detail.value = await api.activityDetail(activityId.value)
    }
    trackProductEvent('VIEW_ACTIVITY')
  } catch (e) {
    error.value = e?.message || '活动详情加载失败'
  } finally {
    loading.value = false
  }
}

function trackProductEvent(eventType, targetType = 'ACTIVITY', targetId = activityId.value) {
  if (!isLoggedIn.value) return
  api.trackProductEvent({ eventType, targetType, targetId, sourcePage: 'activity-detail' }).catch(() => {})
}

async function runAction(action) {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  actionLoading.value = true
  actionError.value = ''
  actionMessage.value = ''
  try {
    await action()
    await loadData()
  } catch (e) {
    actionError.value = e?.message || '操作失败，请稍后重试'
  } finally {
    actionLoading.value = false
  }
}

async function handlePrimaryAction() {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  if (detail.value?.canJoin) {
    await runAction(async () => {
      await api.signActivity(activityId.value)
      trackProductEvent('SIGN_ACTIVITY')
      actionMessage.value = '报名已提交，等待发起人确认。'
    })
    return
  }
  if (detail.value?.canCheckin) {
    await runAction(async () => {
      await api.checkinActivity(activityId.value)
      trackProductEvent('CHECKIN_ACTIVITY')
      actionMessage.value = '签到成功，活动结束后可以写复盘或评价场地。'
    })
    return
  }
  if (detail.value?.canCancelSign) {
    await runAction(async () => {
      await api.cancelSignActivity(activityId.value)
      actionMessage.value = '已退出报名。'
    })
  }
}

async function confirmSafety(type) {
  await runAction(async () => {
    if (type === 'arrival') {
      await api.confirmActivityArrival(activityId.value)
      trackProductEvent('CONFIRM_ARRIVAL')
      actionMessage.value = 'Arrival confirmed.'
      return
    }
    await api.confirmActivityReturn(activityId.value)
    trackProductEvent('CONFIRM_RETURN')
    actionMessage.value = 'Safe return confirmed.'
  })
}

function goPostEventAction(action) {
  if (!action) return
  if (action.key === 'REVIEW_PLACE' && action.targetId) {
    router.push(`/places/${action.targetId}`)
    return
  }
  if (action.key === 'WRITE_RECAP_POST') {
    router.push('/community/publish')
    return
  }
  router.push('/community')
}

onMounted(loadData)
</script>

<template>
  <div class="detail-wrap">
    <div class="card">
      <div class="section-head">
        <button class="btn-soft" @click="router.push('/activities')">
          <AppIcon name="activity" :size="15" />
          返回活动列表
        </button>
      </div>

      <p v-if="loading" class="muted">加载中...</p>
      <p v-if="error" class="error">{{ error }}</p>

      <template v-if="detail && !loading">
        <div class="title-row">
          <h2>{{ detail.title }}</h2>
          <span class="tag">{{ displayStatus(detail) }}</span>
        </div>
        <div class="action-panel">
          <div>
            <span class="action-label">参与状态</span>
            <strong>{{ signStatusText }}</strong>
            <p v-if="detail.full" class="muted">活动名额已满</p>
            <p v-else-if="detail.spotsLeft !== null && detail.spotsLeft !== undefined" class="muted">剩余 {{ detail.spotsLeft }} 个名额</p>
          </div>
          <button
            v-if="primaryActionText"
            class="btn-primary"
            :disabled="actionLoading"
            @click="handlePrimaryAction"
          >
            <AppIcon name="activity" :size="15" />
            {{ actionLoading ? '处理中...' : primaryActionText }}
          </button>
        </div>
        <p v-if="actionMessage" class="success">{{ actionMessage }}</p>
        <p v-if="actionError" class="error">{{ actionError }}</p>
        <div class="meta-grid">
          <p><strong>时间</strong>{{ detail.activityTime ? String(detail.activityTime).replace('T', ' ') : '未设置' }}</p>
          <p><strong>地点</strong>{{ displayAddress(detail) }}</p>
          <p><strong>人数</strong>{{ detail.signNum || 0 }} / {{ detail.maxNum || '不限' }}</p>
          <p><strong>发布者</strong>{{ detail.publisherName || '未知用户' }}</p>
          <p><strong>类型</strong>{{ detail.activityType || '未设置' }}</p>
        </div>
        <div class="content-block">
          <h3>活动介绍</h3>
          <p class="content">{{ displayIntro || '暂无活动介绍' }}</p>
        </div>
        <div class="content-block">
          <h3>活动说明</h3>
          <p v-if="hasSeparateContent" class="content">{{ activityContent }}</p>
          <p v-else class="muted">该活动未保存单独的完整说明。</p>
        </div>
        <div v-if="detail.safetyCompanion" class="content-block safety-companion">
          <div v-if="isLoggedIn && (detail.canConfirmArrival || detail.canConfirmReturn || detail.arrivalConfirmed === '1' || detail.returnConfirmed === '1')" class="safety-confirm-row">
            <button v-if="detail.canConfirmArrival" class="btn-soft small" :disabled="actionLoading" @click="confirmSafety('arrival')">我已到达</button>
            <span v-else-if="detail.arrivalConfirmed === '1'" class="confirm-pill">已确认到达</span>
            <button v-if="detail.canConfirmReturn" class="btn-soft small" :disabled="actionLoading" @click="confirmSafety('return')">我已安全离开</button>
            <span v-else-if="detail.returnConfirmed === '1'" class="confirm-pill">已安全离开</span>
          </div>
          <h3>安全陪伴</h3>
          <ul>
            <li>{{ detail.safetyCompanion.shareLocationHint }}</li>
            <li>{{ detail.safetyCompanion.arrivalConfirmationHint }}</li>
            <li>{{ detail.safetyCompanion.weatherReminder }}</li>
            <li>{{ detail.safetyCompanion.protectiveGearReminder }}</li>
          </ul>
          <p v-if="detail.safetyCompanion.nightSession" class="muted">夜间活动请确认灯光、人流和返程路线。</p>
        </div>
        <div v-if="detail.canReviewAfterEvent || detail.postEventActions?.length" class="content-block post-event">
          <h3>活动后续</h3>
          <p class="muted">把签到后的体验沉淀成评价、复盘或社区素材。</p>
          <div class="post-event-actions">
            <button
              v-for="action in detail.postEventActions || []"
              :key="action.key"
              class="btn-soft"
              @click="goPostEventAction(action)"
            >
              {{ action.label }}
            </button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.detail-wrap {
  max-width: 980px;
  margin: 0 auto;
}

.title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.title-row h2 {
  margin: 0;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 18px;
  margin-top: 14px;
}

.meta-grid p {
  margin: 0;
  color: var(--text-soft);
  line-height: 1.6;
}

.meta-grid strong {
  display: block;
  color: var(--text);
  margin-bottom: 2px;
}

.action-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 16px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #f8fafc;
}

.action-panel strong {
  display: block;
  margin-top: 4px;
  color: var(--text);
  font-size: 18px;
}

.action-label {
  color: var(--text-soft);
  font-size: 13px;
}

.content-block {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid var(--line);
}

.content-block h3 {
  margin: 0 0 10px;
}

.content {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.8;
  color: var(--text);
}

.safety-companion ul {
  display: grid;
  gap: 8px;
  margin: 0;
  padding-left: 18px;
}

.safety-companion li {
  line-height: 1.6;
  color: var(--text);
}

.safety-confirm-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.btn-soft.small {
  min-height: 34px;
  padding: 7px 10px;
  font-size: 13px;
}

.confirm-pill {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 7px 10px;
  border-radius: 8px;
  background: rgba(46, 125, 50, 0.1);
  color: #2e7d32;
  font-size: 13px;
  font-weight: 700;
}

.post-event-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

@media (max-width: 768px) {
  .title-row {
    flex-direction: column;
  }

  .action-panel {
    align-items: stretch;
    flex-direction: column;
  }

  .meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
