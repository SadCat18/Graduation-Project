<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { normalizeMediaUrl } from '../utils/url'
import { getToken } from '../utils/auth'

const route = useRoute()
const router = useRouter()
const detail = ref(null)
const loading = ref(false)
const REPORT_REASONS = ['广告', '辱骂', '人身攻击', '虚假信息', '违法违规', '其他']

const imageList = computed(() => {
  const raw = detail.value?.imageUrls
  if (!raw) return []
  return String(raw).split(',').map(item => normalizeMediaUrl(item.trim())).filter(Boolean)
})

async function loadData() {
  loading.value = true
  try {
    detail.value = await api.bulletinDetail(route.params.id)
  } finally {
    loading.value = false
  }
}

onMounted(loadData)

function chooseReportReason() {
  const tip = REPORT_REASONS.map((item, idx) => `${idx + 1}. ${item}`).join('\n')
  const picked = window.prompt(`请选择举报原因，输入序号：\n${tip}`, '1')
  const idx = Number(picked) - 1
  if (!Number.isInteger(idx) || idx < 0 || idx >= REPORT_REASONS.length) return ''
  return REPORT_REASONS[idx]
}

async function reportBulletin() {
  if (!getToken()) {
    router.push('/login')
    return
  }
  if (!detail.value?.bulletinId) return
  const reason = chooseReportReason()
  if (!reason) return
  const detailText = window.prompt('可补充举报说明（可留空）', '') || ''
  await api.createReport({ targetType: 'BULLETIN', targetId: detail.value.bulletinId, reason, detail: detailText })
  alert('举报已提交，感谢你的反馈')
}
</script>

<template>
  <div class="card detail-wrap">
    <div class="inline top-nav">
      <button class="btn-soft" @click="router.push('/bulletins')">返回快讯列表</button>
      <span class="muted">社区快讯 / 详情</span>
    </div>

    <p v-if="loading" class="muted">加载中...</p>
    <template v-else-if="detail">
      <div class="inline top-actions">
        <button class="btn-soft" @click="reportBulletin">举报快讯</button>
      </div>
      <h2>{{ detail.title }}</h2>
      <p class="muted meta">{{ detail.createTime?.replace('T', ' ') }} · {{ detail.publisherName || '匿名发布' }} · {{ detail.bulletinType || '社区快讯' }}</p>
      <div v-if="imageList.length" class="image-grid">
        <img v-for="img in imageList" :key="img" :src="img" alt="快讯图片" />
      </div>
      <p class="content">{{ detail.content }}</p>
    </template>
    <p v-else class="muted">该快讯不存在或暂不可见。</p>
  </div>
</template>

<style scoped>
.detail-wrap {
  padding: 20px;
}

.top-nav {
  margin-bottom: 12px;
}

.top-actions {
  margin-bottom: 10px;
}

h2 {
  margin: 0 0 8px;
  font-size: 30px;
  line-height: 1.3;
}

.meta {
  margin: 0 0 12px;
}

.content {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #0f172a;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
  margin: 10px 0 14px;
}

.image-grid img {
  width: 100%;
  height: 150px;
  object-fit: cover;
  border-radius: 10px;
  border: 1px solid var(--line);
}
</style>
