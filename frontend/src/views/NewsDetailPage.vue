<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import { normalizeMediaUrl } from '../utils/url'

const route = useRoute()
const detail = ref(null)
const loading = ref(false)

function coverUrl() {
  const raw = detail.value?.cover
  if (!raw) return ''
  return normalizeMediaUrl(String(raw).trim())
}

async function loadData() {
  loading.value = true
  try {
    detail.value = await api.newsDetail(route.params.id)
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <div class="card">
    <p v-if="loading" class="muted">加载中...</p>
    <template v-if="detail">
      <h2>{{ detail.title }}</h2>
      <p class="muted">{{ detail.createTime?.replace('T', ' ') }} · {{ detail.category || '滑板资讯' }}</p>
      <img v-if="coverUrl()" :src="coverUrl()" alt="资讯封面" class="cover" />
      <p class="content">{{ detail.content }}</p>
    </template>
  </div>
</template>

<style scoped>
.cover {
  width: 100%;
  max-height: 360px;
  object-fit: cover;
  border-radius: 12px;
  border: 1px solid var(--line);
  margin: 12px 0;
}
.content {
  white-space: pre-wrap;
  line-height: 1.75;
}
</style>
