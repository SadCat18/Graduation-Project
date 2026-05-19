<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import { normalizeMediaUrl } from '../utils/url'

const route = useRoute()
const detail = ref(null)
const loading = ref(false)

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
</script>

<template>
  <div class="card">
    <p v-if="loading" class="muted">加载中...</p>
    <template v-if="detail">
      <h2>{{ detail.title }}</h2>
      <p class="muted">{{ detail.createTime?.replace('T', ' ') }} · {{ detail.publisherName }} · {{ detail.bulletinType }}</p>
      <div v-if="imageList.length" class="image-grid">
        <img v-for="img in imageList" :key="img" :src="img" alt="快讯图片" />
      </div>
      <p class="content">{{ detail.content }}</p>
    </template>
  </div>
</template>

<style scoped>
.content { white-space: pre-wrap; line-height: 1.7; }
.image-grid { display: grid; grid-template-columns: repeat(auto-fill,minmax(180px,1fr)); gap: 10px; margin: 10px 0; }
.image-grid img { width: 100%; height: 130px; object-fit: cover; border-radius: 10px; border: 1px solid var(--line); }
</style>
