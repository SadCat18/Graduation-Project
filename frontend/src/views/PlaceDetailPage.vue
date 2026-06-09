<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { getToken } from '../utils/auth'
import { ACTIVITY_EXPIRY_FILTER, filterActivitiesByExpiry } from '../utils/activityExpiry'

const route = useRoute()
const router = useRouter()
const placeId = computed(() => Number(route.params.id))
const place = ref(null)
const reviews = ref([])
const relatedActivities = ref([])
const loading = ref(false)
const loadError = ref('')
const submitting = ref(false)
const form = reactive({ score: 5, content: '', images: '' })
const imageUploading = ref(false)

const isLoggedIn = computed(() => !!getToken())

async function loadData() {
  loading.value = true
  loadError.value = ''
  place.value = null
  reviews.value = []
  relatedActivities.value = []
  try {
    const [detail, list] = await Promise.all([api.placeDetail(placeId.value), api.placeReviews(placeId.value)])
    place.value = detail
    reviews.value = list || []
    await loadRelatedActivities(detail)
  } catch (e) {
    loadError.value = e?.message || '场地加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function loadRelatedActivities(detail) {
  if (!detail?.name) return
  const data = await api.publicActivities({
    page: 1,
    size: 20,
    keyword: detail.name,
    expired: false
  })
  relatedActivities.value = filterActivitiesByExpiry(data?.list || [], ACTIVITY_EXPIRY_FILTER.ACTIVE)
    .filter((item) => {
      if (item.placeId && Number(item.placeId) === placeId.value) return true
      return String(item.place || '').includes(detail.name) || String(item.address || '').includes(detail.name)
    })
    .slice(0, 4)
}

function goActivity(activityId) {
  if (!activityId) return
  router.push(`/activities/${activityId}`)
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ') : '未设置'
}

async function submitReview() {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  submitting.value = true
  try {
    await api.createPlaceReview({
      placeId: placeId.value,
      score: Number(form.score),
      content: form.content?.trim() || null,
      images: form.images?.trim() || null
    })
    form.score = 5
    form.content = ''
    form.images = ''
    await loadData()
    alert('评价发布成功')
  } finally {
    submitting.value = false
  }
}

async function uploadImage(event) {
  const file = (event.target.files || [])[0]
  if (!file) return
  imageUploading.value = true
  try {
    const url = await api.uploadPostImage(file)
    form.images = form.images ? `${form.images},${url}` : url
  } finally {
    imageUploading.value = false
    event.target.value = ''
  }
}

onMounted(loadData)

watch(placeId, () => {
  loadData()
})
</script>

<template>
  <div class="card place-detail-page">
    <button class="btn-soft" @click="router.back()">返回</button>
    <p v-if="loading" class="muted">加载中...</p>
    <p v-else-if="loadError" class="error">{{ loadError }}</p>
    <template v-else-if="place">
      <h2>{{ place.name }}</h2>
      <p class="muted">{{ place.address }}</p>
      <p class="muted">综合评分：{{ place.score || 0 }}（{{ place.reviewCount || 0 }} 条评价）</p>
      <p>{{ place.intro || '暂无场地介绍' }}</p>

      <div class="card">
        <h3>相关约板活动</h3>
        <div v-if="!relatedActivities.length" class="empty-tip">当前没有关联的未过期活动。</div>
        <div v-for="item in relatedActivities" :key="item.activityId" class="list-item">
          <strong>{{ item.title }}</strong>
          <p class="muted">{{ formatTime(item.activityTime) }} · {{ item.signNum || 0 }}/{{ item.maxNum || '不限' }} 人</p>
          <p>{{ item.content || item.activityDesc || '暂无活动说明' }}</p>
          <button class="btn-soft" @click="goActivity(item.activityId)">查看活动</button>
        </div>
      </div>

      <div class="card">
        <h3>发布评价</h3>
        <div class="form-grid">
          <select v-model.number="form.score">
            <option v-for="n in [5,4,3,2,1]" :key="n" :value="n">{{ n }} 分</option>
          </select>
          <textarea v-model="form.content" placeholder="写下你的体验（可选）" />
          <input v-model="form.images" placeholder="图片链接，多个用英文逗号分隔（可选）" />
          <input type="file" accept="image/*" @change="uploadImage" />
          <p class="muted">{{ imageUploading ? '图片上传中...' : '可直接上传评价图片' }}</p>
          <button class="btn-primary" :disabled="submitting" @click="submitReview">{{ submitting ? '提交中...' : '发布评价' }}</button>
        </div>
      </div>

      <div class="card">
        <h3>最新评价</h3>
        <div v-if="!reviews.length" class="empty-tip">还没有评价，来发布第一条吧。</div>
        <div v-for="item in reviews" :key="item.reviewId" class="list-item">
          <strong>{{ item.username }}</strong>
          <p class="muted">{{ item.score }} 分 · {{ String(item.createTime || '').replace('T', ' ') }}</p>
          <p>{{ item.content || '该用户未填写文字内容。' }}</p>
          <div v-if="item.images">
            <img v-for="img in String(item.images).split(',').filter(Boolean)" :key="img" :src="img.trim()" class="review-img" loading="lazy" decoding="async" />
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.place-detail-page { display: grid; gap: 12px; }
.form-grid { display: grid; gap: 8px; }
.review-img { width: 120px; height: 90px; object-fit: cover; border-radius: 8px; margin-right: 8px; }
</style>
