<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { getToken } from '../utils/auth'
import { ACTIVITY_EXPIRY_FILTER, filterActivitiesByExpiry } from '../utils/activityExpiry'
import { normalizeMediaUrl } from '../utils/url'

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
const scoreValue = computed(() => {
  const score = Number(place.value?.score || 0)
  return Number.isFinite(score) ? score : 0
})
const scoreText = computed(() => scoreValue.value.toFixed(1))
const scorePercent = computed(() => `${Math.max(0, Math.min(scoreValue.value, 5)) * 20}%`)
const reviewCount = computed(() => Number(place.value?.reviewCount || reviews.value.length || 0))
const placeTags = computed(() => inferPlaceTags(place.value))
const introSections = computed(() => buildIntroSections(place.value))
const reviewGallery = computed(() => {
  const images = reviews.value.flatMap(item => splitImages(item.images))
  return images.slice(0, 5)
})
const placeStats = computed(() => [
  { label: '综合评分', value: scoreText.value },
  { label: '评价数量', value: reviewCount.value },
  { label: '近期活动', value: relatedActivities.value.length },
  { label: '适合强度', value: placeTags.value.includes('进阶练习') ? '进阶' : '日常' }
])

function cleanText(value) {
  return String(value || '').trim()
}

function splitImages(images) {
  return String(images || '')
    .split(',')
    .map(item => normalizeMediaUrl(item.trim()))
    .filter(Boolean)
}

function inferPlaceTags(detail) {
  const source = [detail?.name, detail?.address, detail?.intro].map(cleanText).join(' ')
  const tags = []
  if (/新手|入门|平地|空旷/.test(source)) tags.push('新手友好')
  if (/街式|台阶|栏杆|道具|ledge|rail/i.test(source)) tags.push('街式练习')
  if (/碗池|碗|pool|bowl/i.test(source)) tags.push('碗池')
  if (/夜|灯|晚上|夜滑/.test(source)) tags.push('夜滑友好')
  if (/地铁|公交|交通|商圈|公园/.test(source)) tags.push('交通便利')
  if (scoreValue.value >= 4.5) tags.push('高评分')
  if (relatedActivities.value.length) tags.push('有约板活动')
  if (!tags.length) tags.push('日常滑行')
  return [...new Set(tags)].slice(0, 6)
}

function buildIntroSections(detail) {
  const intro = cleanText(detail?.intro)
  const address = cleanText(detail?.address)
  if (!intro) {
    return [
      {
        title: '场地概况',
        body: address ? `这个场地位于 ${address}，适合先到现场观察路面、人流和灯光条件。` : '暂时还没有详细介绍，可以先通过活动和评价了解场地情况。'
      },
      {
        title: '适合练什么',
        body: '建议从基础滑行、转向、刹车和低风险动作开始，再根据现场空间决定是否练习进阶动作。'
      },
      {
        title: '注意事项',
        body: '到场前确认天气和路面状态，佩戴护具，避开人流密集区域，夜滑时优先选择照明充足的位置。'
      }
    ]
  }

  const sentences = intro
    .split(/[。！？!?；;\n]/)
    .map(item => item.trim())
    .filter(Boolean)

  return [
    { title: '场地概况', body: sentences.slice(0, 2).join('。') || intro },
    { title: '适合练什么', body: sentences.slice(2, 4).join('。') || '适合结合现场空间安排基础滑行、动作练习和轻量约板。' },
    { title: '注意事项', body: sentences.slice(4).join('。') || '练习前观察路面、行人和障碍物，控制速度，给自己留足缓冲空间。' }
  ]
}

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

function goActivities() {
  router.push('/activities')
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
  <div class="place-detail-page">
    <button class="btn-soft back-btn" @click="router.back()">
      <AppIcon name="map" :size="15" />
      返回
    </button>

    <div v-if="loading" class="card skate-loading-state">
      <p>场地加载中...</p>
    </div>
    <p v-else-if="loadError" class="card error">{{ loadError }}</p>

    <template v-else-if="place">
      <section class="place-hero">
        <div class="place-hero-main">
          <span class="eyebrow">SKATE SPOT</span>
          <h2>{{ place.name }}</h2>
          <p class="place-address">
            <AppIcon name="location" :size="16" />
            {{ place.address || '暂无地址' }}
          </p>
          <div class="place-tag-row">
            <span v-for="tag in placeTags" :key="tag">{{ tag }}</span>
          </div>
          <div class="hero-actions">
            <button class="btn-primary" @click="goActivities">查看附近活动</button>
            <a class="btn-soft" href="#review-form">发布评价</a>
          </div>
        </div>

        <div class="place-score-panel">
          <span>综合评分</span>
          <strong>{{ scoreText }}</strong>
          <div class="score-track" aria-hidden="true"><i :style="{ width: scorePercent }"></i></div>
          <p>{{ reviewCount }} 条评价 · {{ relatedActivities.length }} 个近期活动</p>
        </div>
      </section>

      <section class="place-stat-grid">
        <div v-for="item in placeStats" :key="item.label" class="place-stat-card">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </section>

      <section class="place-layout">
        <div class="place-main">
          <div class="place-section">
            <div class="section-head">
              <h3>场地介绍</h3>
              <span class="section-note">概况 · 练习 · 安全</span>
            </div>
            <div class="intro-grid">
              <article v-for="section in introSections" :key="section.title" class="intro-card">
                <span>{{ section.title }}</span>
                <p>{{ section.body }}</p>
              </article>
            </div>
          </div>

          <div class="place-section">
            <div class="section-head">
              <h3>相关约板活动</h3>
              <button class="btn-soft" @click="goActivities">去活动页</button>
            </div>
            <div v-if="!relatedActivities.length" class="empty-tip">当前没有关联的未过期活动，可以先收藏场地，等新活动上线。</div>
            <div v-else class="activity-card-grid">
              <article v-for="(item, index) in relatedActivities" :key="item.activityId" class="activity-spot-card motion-card" :style="{ '--motion-index': index }">
                <span class="activity-time">{{ formatTime(item.activityTime) }}</span>
                <strong>{{ item.title }}</strong>
                <p>{{ item.content || item.activityDesc || '暂无活动说明' }}</p>
                <div class="activity-meta">
                  <span>{{ item.signNum || 0 }}/{{ item.maxNum || '不限' }} 人</span>
                  <button class="btn-soft" @click="goActivity(item.activityId)">查看活动</button>
                </div>
              </article>
            </div>
          </div>

          <div id="review-form" class="place-section review-form-section">
            <div class="section-head">
              <h3>发布评价</h3>
              <span class="section-note">分享路面、灯光、人流和练习体验</span>
            </div>
            <div class="form-grid">
              <select v-model.number="form.score">
                <option v-for="n in [5,4,3,2,1]" :key="n" :value="n">{{ n }} 分</option>
              </select>
              <textarea v-model="form.content" placeholder="写下你的体验：路面是否平整、适合什么动作、什么时候人少..." />
              <input v-model="form.images" placeholder="图片链接，多个用英文逗号分隔，可直接上传后自动填入" />
              <div class="upload-line">
                <input type="file" accept="image/*" @change="uploadImage" />
                <p class="muted">{{ imageUploading ? '图片上传中...' : '可上传评价图片，让场地介绍更真实。' }}</p>
              </div>
              <button class="btn-primary" :disabled="submitting" @click="submitReview">
                {{ submitting ? '提交中...' : '发布评价' }}
              </button>
            </div>
          </div>
        </div>

        <aside class="place-side">
          <div class="place-gallery">
            <div class="section-head">
              <h3>场地图集</h3>
              <span class="section-note">{{ reviewGallery.length ? '来自评价图片' : '暂无图片' }}</span>
            </div>
            <div v-if="reviewGallery.length" class="gallery-grid">
              <img v-for="img in reviewGallery" :key="img" :src="img" alt="场地评价图片" loading="lazy" decoding="async" />
            </div>
            <div v-else class="gallery-placeholder">
              <AppIcon name="skateboard" :size="36" />
              <p>评价图片会自动沉淀成场地图集。</p>
            </div>
          </div>

          <div class="review-list">
            <div class="section-head">
              <h3>最新评价</h3>
              <span class="section-note">{{ reviews.length }} 条</span>
            </div>
            <div v-if="!reviews.length" class="empty-tip">还没有评价，来发布第一条吧。</div>
            <article v-for="item in reviews" :key="item.reviewId" class="review-card">
              <div class="review-top">
                <strong>{{ item.username }}</strong>
                <span>{{ Number(item.score || 0).toFixed(1) }} 分</span>
              </div>
              <p class="muted">{{ String(item.createTime || '').replace('T', ' ') }}</p>
              <p>{{ item.content || '该用户未填写文字内容。' }}</p>
              <div v-if="splitImages(item.images).length" class="review-images">
                <img v-for="img in splitImages(item.images)" :key="img" :src="img" alt="评价图片" loading="lazy" decoding="async" />
              </div>
            </article>
          </div>
        </aside>
      </section>
    </template>
  </div>
</template>

<style scoped>
.place-detail-page {
  display: grid;
  gap: 16px;
  max-width: 1160px;
  margin: 0 auto;
}

.back-btn {
  width: fit-content;
}

.place-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 18px;
  overflow: hidden;
  padding: 22px;
  border: 1px solid var(--line);
  border-radius: 22px;
  background:
    radial-gradient(circle at 10% 0%, rgba(20, 184, 166, 0.16), transparent 36%),
    radial-gradient(circle at 90% 24%, rgba(56, 189, 248, 0.14), transparent 34%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.95));
  box-shadow: 0 16px 38px rgba(15, 23, 42, 0.08);
}

.place-hero-main {
  display: grid;
  gap: 12px;
  align-content: center;
}

.eyebrow {
  width: fit-content;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.08em;
}

.place-hero h2 {
  margin: 0;
  font-size: clamp(28px, 4vw, 46px);
  line-height: 1.08;
}

.place-address {
  display: inline-flex;
  align-items: flex-start;
  gap: 6px;
  margin: 0;
  color: var(--text-soft);
  line-height: 1.55;
}

.place-tag-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.place-tag-row span {
  padding: 5px 10px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: #334155;
  font-size: 13px;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.hero-actions a {
  text-decoration: none;
}

.place-score-panel {
  display: grid;
  align-content: center;
  gap: 8px;
  padding: 18px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.78);
}

.place-score-panel span,
.section-note {
  color: var(--text-muted);
  font-size: 13px;
}

.place-score-panel strong {
  font-size: 52px;
  line-height: 1;
}

.place-score-panel p {
  margin: 0;
  color: var(--text-soft);
}

.score-track {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.22);
}

.score-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #0f766e, #38bdf8);
}

.place-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.place-stat-card,
.place-section,
.place-gallery,
.review-list {
  border: 1px solid var(--line);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.place-stat-card {
  padding: 14px;
}

.place-stat-card span {
  display: block;
  margin-bottom: 6px;
  color: var(--text-muted);
  font-size: 13px;
}

.place-stat-card strong {
  font-size: 22px;
}

.place-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(320px, 0.9fr);
  gap: 16px;
  align-items: start;
}

.place-main,
.place-side {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.place-side {
  position: sticky;
  top: 76px;
}

.place-section,
.place-gallery,
.review-list {
  padding: 18px;
}

.intro-grid {
  display: grid;
  gap: 10px;
}

.intro-card {
  padding: 14px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 14px;
  background: rgba(248, 250, 252, 0.72);
}

.intro-card span {
  display: block;
  margin-bottom: 8px;
  color: #0f766e;
  font-weight: 900;
}

.intro-card p,
.activity-spot-card p,
.review-card p {
  margin: 0;
  line-height: 1.7;
  word-break: break-word;
}

.activity-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.activity-spot-card {
  display: grid;
  gap: 8px;
  padding: 14px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.84);
}

.activity-time {
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
}

.activity-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: var(--text-muted);
  font-size: 13px;
}

.form-grid {
  display: grid;
  gap: 10px;
}

.form-grid textarea {
  min-height: 118px;
}

.upload-line {
  display: grid;
  gap: 6px;
}

.upload-line .muted {
  margin: 0;
}

.gallery-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.gallery-grid img,
.review-images img {
  width: 100%;
  border-radius: 12px;
  object-fit: cover;
  border: 1px solid var(--line);
  background: var(--surface-muted);
}

.gallery-grid img {
  height: 118px;
}

.gallery-grid img:first-child {
  grid-column: 1 / -1;
  height: 156px;
}

.gallery-placeholder {
  display: grid;
  place-items: center;
  gap: 10px;
  min-height: 190px;
  padding: 18px;
  border: 1px dashed rgba(148, 163, 184, 0.38);
  border-radius: 14px;
  color: var(--text-muted);
  text-align: center;
  background:
    linear-gradient(135deg, rgba(15, 118, 110, 0.08), transparent 42%),
    rgba(248, 250, 252, 0.7);
}

.review-card {
  display: grid;
  gap: 8px;
  padding: 12px 0;
  border-bottom: 1px solid var(--line);
}

.review-card:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.review-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.review-top span {
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
}

.review-images {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.review-images img {
  height: 72px;
}

@media (max-width: 980px) {
  .place-hero,
  .place-layout {
    grid-template-columns: 1fr;
  }

  .place-side {
    position: static;
  }

  .place-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .place-hero {
    padding: 16px;
    border-radius: 18px;
  }

  .place-stat-grid,
  .activity-card-grid {
    grid-template-columns: 1fr;
  }

  .place-section,
  .place-gallery,
  .review-list {
    padding: 14px;
  }

  .hero-actions,
  .activity-meta {
    align-items: stretch;
    flex-direction: column;
  }

  .hero-actions button,
  .hero-actions a,
  .activity-meta button {
    width: 100%;
    justify-content: center;
  }
}
</style>
