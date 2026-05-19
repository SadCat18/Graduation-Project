<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { normalizeMediaUrl } from '../utils/url'

const router = useRouter()
const list = ref([])
const loading = ref(false)

function firstImage(raw) {
  if (!raw) return ''
  return String(raw).split(',').map(item => normalizeMediaUrl(item.trim())).find(Boolean) || ''
}

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

    <div
      v-for="item in list"
      :key="item.bulletinId"
      class="bulletin-card"
      @click="goDetail(item.bulletinId)"
    >
      <img v-if="firstImage(item.imageUrls)" :src="firstImage(item.imageUrls)" alt="快讯封面" class="hero-image" />
      <div class="body">
        <h4>{{ item.title }}</h4>
        <div class="meta-row">
          <span class="meta-time">{{ item.createTime?.replace('T', ' ') }}</span>
          <span class="meta-author">{{ item.publisherName }}</span>
          <span class="meta-tag">{{ item.bulletinType || '社区快讯' }}</span>
        </div>
        <button class="btn-soft detail-btn" @click.stop="goDetail(item.bulletinId)">查看详情</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bulletin-wrap {
  padding: 22px 22px 16px;
}

.bulletin-head {
  margin-bottom: 10px;
}

.bulletin-head h3 {
  margin: 0;
  font-size: 32px;
  line-height: 1.2;
  letter-spacing: 0.01em;
}

.publish-btn {
  min-height: 38px;
  padding-inline: 14px;
}

.bulletin-card {
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  overflow: hidden;
  margin-top: 14px;
  cursor: pointer;
  background: var(--bg-1);
  box-shadow: var(--shadow-1);
  transition: transform var(--motion-base) var(--ease-ui), border-color var(--motion-base) var(--ease-ui);
}

.bulletin-card:hover {
  transform: translateY(-2px);
  border-color: var(--line-strong);
}

.hero-image {
  width: 100%;
  height: 180px;
  object-fit: cover;
  display: block;
}

.body {
  padding: 14px 16px 16px;
}

.body h4 {
  margin: 0 0 10px;
  font-size: 24px;
  line-height: 1.25;
  color: var(--text-1);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  color: var(--text-3);
  font-size: 13px;
}

.meta-time {
  color: #6b7280;
}

.meta-author {
  color: var(--text-2);
}

.meta-tag {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 10px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--line-strong);
  background: var(--bg-2);
  color: var(--text-2);
  font-size: 12px;
  font-weight: 600;
}

.detail-btn {
  margin-top: 12px;
  min-height: 34px;
  padding-inline: 12px;
}

@media (max-width: 900px) {
  .bulletin-wrap {
    padding: 16px 14px 12px;
  }

  .bulletin-head h3 {
    font-size: 28px;
  }

  .hero-image {
    height: 180px;
  }

  .body h4 {
    font-size: 24px;
  }
}
</style>
