<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { ACTIVITY_STATUS_LABEL, normalizeActivityStatus } from '../constants/activity'

const route = useRoute()
const router = useRouter()

const detail = ref(null)
const loading = ref(false)
const error = ref('')

const activityId = computed(() => Number(route.params.id))
const activityIntro = computed(() => (detail.value?.activityDesc || '').trim())
const activityContent = computed(() => (detail.value?.content || '').trim())
const hasSeparateContent = computed(() => activityContent.value && activityContent.value !== activityIntro.value)
const displayIntro = computed(() => activityIntro.value || activityContent.value)

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
    detail.value = await api.activityDetail(activityId.value)
  } catch (e) {
    error.value = e?.message || '活动详情加载失败'
  } finally {
    loading.value = false
  }
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

@media (max-width: 768px) {
  .title-row {
    flex-direction: column;
  }

  .meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
