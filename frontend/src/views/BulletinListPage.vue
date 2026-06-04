<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { firstImage } from '../utils/url'
import { BULLETIN_SORT_OPTIONS, BULLETIN_TYPES } from '../constants/bulletin'

defineOptions({ name: 'BulletinListPage' })

const router = useRouter()
const list = ref([])
const loading = ref(false)
const activeType = ref('全部')
const activeSort = ref('latest')

const typeOptions = computed(() => ['全部', ...BULLETIN_TYPES])
const sortHint = computed(() => {
  if (activeSort.value !== 'city') return ''
  return '当前快讯暂无城市字段，已按最新发布展示。'
})


function shortText(text, max = 90) {
  const value = String(text || '').trim().replace(/\s+/g, ' ')
  if (!value) return '点击查看本条快讯详情。'
  return value.length > max ? `${value.slice(0, max)}...` : value
}

function relativeTime(value) {
  if (!value) return '未知时间'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value).replace('T', ' ')
  const diff = Date.now() - d.getTime()
  const hour = 1000 * 60 * 60
  const day = hour * 24
  if (diff < hour) {
    const m = Math.max(1, Math.floor(diff / (1000 * 60)))
    return `${m} 分钟前`
  }
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < day * 2) return '昨天'
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function tagClass(type) {
  const key = String(type || '').trim()
  if (key.includes('活动')) return 'tag-activity'
  if (key.includes('比赛') || key.includes('赛事')) return 'tag-match'
  if (key.includes('同城')) return 'tag-city'
  if (key.includes('店铺')) return 'tag-shop'
  return 'tag-default'
}

function hotScore(item) {
  const now = Date.now()
  const createTime = new Date(item.createTime || 0).getTime()
  const ageHours = Number.isFinite(createTime) ? Math.max(1, (now - createTime) / (1000 * 60 * 60)) : 9999
  const freshness = 100 / ageHours
  const imageBonus = firstImage(item.imageUrls) ? 12 : 0
  const titleScore = Math.min(20, String(item.title || '').trim().length * 0.45)
  const contentScore = Math.min(35, String(item.content || '').trim().length * 0.12)
  const cityBoost = String(item.bulletinType || '').includes('同城') ? 8 : 0
  return freshness + imageBonus + titleScore + contentScore + cityBoost
}

const visibleList = computed(() => {
  let data = [...list.value]
  if (activeType.value !== '全部') data = data.filter(item => (item.bulletinType || '').trim() === activeType.value)
  if (activeSort.value === 'hot') {
    data.sort((a, b) => hotScore(b) - hotScore(a))
  } else {
    data.sort((a, b) => new Date(b.createTime || 0).getTime() - new Date(a.createTime || 0).getTime())
  }
  return data
})

const topItem = computed(() => visibleList.value[0] || null)
const gridItems = computed(() => visibleList.value.slice(1))
const latestItems = computed(() => [...list.value]
  .sort((a, b) => new Date(b.createTime || 0).getTime() - new Date(a.createTime || 0).getTime())
  .slice(0, 5))

async function loadData() {
  loading.value = true
  try {
    list.value = await api.bulletinsAll()
  } finally {
    loading.value = false
  }
}

function goDetail(id) {
  router.push(`/bulletins/${id}`)
}

onMounted(loadData)
</script>

<template>
  <div class="card bulletin-wrap">
    <div class="section-head bulletin-head">
      <h3>社区快讯</h3>
      <button class="btn-primary publish-btn" @click="$router.push('/bulletins/publish')">发布快讯</button>
    </div>

    <p v-if="loading" class="muted">加载中...</p>
    <template v-else>
      <div class="toolbar">
        <div class="inline filter-row">
          <button
            v-for="type in typeOptions"
            :key="type"
            class="chip-btn"
            :class="{ active: activeType === type }"
            @click="activeType = type"
          >
            {{ type }}
          </button>
        </div>
        <select v-model="activeSort" class="sort-select">
          <option v-for="item in BULLETIN_SORT_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
      </div>
      <p v-if="sortHint" class="muted sort-hint">{{ sortHint }}</p>

      <div v-if="topItem" class="top-card" @click="goDetail(topItem.bulletinId)">
        <img v-if="firstImage(topItem.imageUrls)" :src="firstImage(topItem.imageUrls)" alt="头条封面" class="top-image" />
        <div v-else class="top-image placeholder">社区快讯</div>
        <div class="top-body">
          <p class="top-label">头条快讯</p>
          <h4>{{ topItem.title }}</h4>
          <p class="top-summary">{{ shortText(topItem.content, 120) }}</p>
          <div class="meta-row">
            <span class="meta-time">{{ relativeTime(topItem.createTime) }}</span>
            <span class="meta-author">{{ topItem.publisherName || '匿名发布' }}</span>
            <span class="meta-tag" :class="tagClass(topItem.bulletinType)">{{ topItem.bulletinType || '社区快讯' }}</span>
          </div>
        </div>
      </div>

      <div class="content-layout">
        <div class="news-grid">
          <div
            v-for="item in gridItems"
            :key="item.bulletinId"
            class="bulletin-card"
            @click="goDetail(item.bulletinId)"
          >
            <img v-if="firstImage(item.imageUrls)" :src="firstImage(item.imageUrls)" alt="快讯封面" class="hero-image" />
            <div v-else class="hero-image placeholder">无图快讯</div>
            <div class="body">
              <h4>{{ item.title }}</h4>
              <p class="summary">{{ shortText(item.content) }}</p>
              <div class="meta-row">
                <span class="meta-time">{{ relativeTime(item.createTime) }}</span>
                <span class="meta-author">{{ item.publisherName || '匿名发布' }}</span>
                <span class="meta-tag" :class="tagClass(item.bulletinType)">{{ item.bulletinType || '社区快讯' }}</span>
              </div>
              <button class="btn-soft detail-btn" @click.stop="goDetail(item.bulletinId)">查看详情</button>
            </div>
          </div>
        </div>

        <aside class="side-card">
          <h4>最新发布</h4>
          <div v-for="item in latestItems" :key="`latest-${item.bulletinId}`" class="latest-row" @click="goDetail(item.bulletinId)">
            <p>{{ item.title }}</p>
            <span>{{ relativeTime(item.createTime) }}</span>
          </div>
          <p v-if="!latestItems.length" class="muted">暂无数据</p>
        </aside>
      </div>

      <div v-if="!visibleList.length" class="empty">
        <p class="muted">当前分类下还没有快讯，试试切换分类。</p>
      </div>
    </template>
  </div>
</template>

<style scoped>
.bulletin-wrap {
  padding: 22px 22px 16px;
}

.bulletin-head {
  margin-bottom: 12px;
}

.bulletin-head h3 {
  margin: 0;
  font-size: 34px;
  line-height: 1.2;
  letter-spacing: 0.01em;
}

.publish-btn {
  min-height: 38px;
  padding-inline: 14px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 14px;
}

.sort-hint {
  margin: -6px 0 10px;
}

.filter-row {
  gap: 6px;
}

.chip-btn {
  min-height: 32px;
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: #fff;
  box-shadow: none;
}

.chip-btn.active {
  border-color: #334155;
  background: #eef2ff;
  color: #1e293b;
}

.sort-select {
  max-width: 170px;
  min-width: 160px;
}

.top-card {
  border: 1px solid var(--line);
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  background: #fff;
  box-shadow: 0 12px 26px rgba(15, 23, 42, 0.08);
  margin-bottom: 14px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: stretch;
}

.top-image {
  width: 100%;
  height: 360px;
  object-fit: cover;
  display: block;
}

.placeholder {
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #e2e8f0, #f8fafc);
  color: #64748b;
  font-weight: 600;
}

.top-body {
  padding: 10px 12px;
}

.top-label {
  margin: 0 0 8px;
  font-size: 11px;
  color: #475569;
}

.top-body h4 {
  margin: 0 0 8px;
  font-size: 20px;
  line-height: 1.25;
}

.top-summary {
  margin: 0 0 8px;
  color: #334155;
  font-size: 13px;
}

.content-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 14px;
}

.news-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.bulletin-card {
  border: 1px solid var(--line);
  border-radius: 14px;
  overflow: hidden;
  cursor: pointer;
  background: #fff;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.06);
  transition: transform 0.24s ease, box-shadow 0.24s ease, border-color 0.24s ease;
}

.bulletin-card:hover {
  transform: translateY(-2px);
  border-color: rgba(100, 116, 139, 0.5);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.1);
}

.hero-image {
  width: 100%;
  height: 170px;
  object-fit: cover;
  display: block;
}

.body {
  padding: 12px 14px 14px;
}

.body h4 {
  margin: 0 0 8px;
  font-size: 22px;
  line-height: 1.3;
  color: #0f172a;
}

.summary {
  margin: 0 0 10px;
  color: #334155;
  font-size: 14px;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  color: #64748b;
  font-size: 12px;
}

.meta-tag {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  font-size: 12px;
  font-weight: 600;
}

.tag-default { background: #f1f5f9; color: #334155; }
.tag-activity { background: #ecfeff; color: #0f766e; border-color: rgba(15, 118, 110, 0.32); }
.tag-match { background: #fff7ed; color: #c2410c; border-color: rgba(194, 65, 12, 0.28); }
.tag-city { background: #eff6ff; color: #1d4ed8; border-color: rgba(29, 78, 216, 0.28); }
.tag-shop { background: #faf5ff; color: #7e22ce; border-color: rgba(126, 34, 206, 0.28); }

.detail-btn {
  margin-top: 10px;
  min-height: 32px;
  padding-inline: 12px;
}

.side-card {
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 12px;
  background: #f8fafc;
}

.side-card h4 {
  margin: 0 0 8px;
}

.latest-row {
  padding: 8px 0;
  border-bottom: 1px dashed #cbd5e1;
  cursor: pointer;
}

.latest-row:last-child {
  border-bottom: none;
}

.latest-row p {
  margin: 0 0 3px;
  font-size: 14px;
  color: #0f172a;
}

.latest-row span {
  font-size: 12px;
  color: #64748b;
}

.empty {
  margin-top: 14px;
  text-align: center;
}

@media (max-width: 1024px) {
  .content-layout {
    grid-template-columns: 1fr;
  }

  .side-card {
    order: 2;
  }
}

@media (max-width: 900px) {
  .bulletin-wrap {
    padding: 16px 14px 12px;
  }

  .bulletin-head h3 {
    font-size: 28px;
  }

  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .sort-select {
    max-width: none;
  }

  .top-card {
    grid-template-columns: 1fr;
  }

  .top-image {
    height: 150px;
  }

  .news-grid {
    grid-template-columns: 1fr;
  }
}
</style>
