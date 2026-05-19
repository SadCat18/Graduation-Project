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
  font-size: 34px;
  line-height: 1.2;
  letter-spacing: 0.01em;
}

.publish-btn {
  min-height: 38px;
  padding-inline: 14px;
}

.bulletin-card {
  border: 1px solid var(--line);
  border-radius: 16px;
  overflow: hidden;
  margin-top: 14px;
  cursor: pointer;
  background: #fff;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.06);
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
}

.bulletin-card:hover {
  transform: translateY(-3px);
  border-color: rgba(148, 163, 184, 0.45);
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.1);
}

.hero-image {
  width: 100%;
  height: 230px;
  object-fit: cover;
  display: block;
}

.body {
  padding: 14px 16px 16px;
}

.body h4 {
  margin: 0 0 10px;
  font-size: 32px;
  line-height: 1.25;
  color: #0f172a;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  color: #64748b;
  font-size: 13px;
}

.meta-time {
  color: #6b7280;
}

.meta-author {
  color: #475569;
}

.meta-tag {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: rgba(241, 245, 249, 0.88);
  color: #334155;
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
