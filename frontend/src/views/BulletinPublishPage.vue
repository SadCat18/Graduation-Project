<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { normalizeMediaUrl } from '../utils/url'

const router = useRouter()
const form = reactive({ title: '', bulletinType: '', content: '', imageUrls: '' })
const fileInputRef = ref(null)
const uploading = ref(false)

function parseImages(raw) {
  if (!raw) return []
  return String(raw).split(',').map(item => normalizeMediaUrl(item.trim())).filter(Boolean)
}

function triggerSelect() {
  fileInputRef.value?.click()
}

async function uploadImage(event) {
  const file = (event.target.files || [])[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    alert('请选择图片文件')
    return
  }
  uploading.value = true
  try {
    const imageUrl = await api.uploadPostImage(file)
    const current = parseImages(form.imageUrls)
    current.push(imageUrl)
    form.imageUrls = current.join(',')
  } finally {
    uploading.value = false
    if (fileInputRef.value) fileInputRef.value.value = ''
  }
}

async function submit() {
  if (!form.title.trim() || !form.bulletinType.trim() || !form.content.trim()) {
    alert('请完整填写标题、类型和内容')
    return
  }
  await api.createBulletin(form)
  alert('提交成功，等待管理员审核后展示')
  router.push('/bulletins')
}
</script>

<template>
  <div class="card">
    <h3>发布社区快讯</h3>
    <p class="muted">支持本地上传图片，或填写图片 URL（多个用英文逗号分隔）。</p>

    <label>标题</label>
    <input v-model="form.title" placeholder="例如：周末街式赛报名开启" />

    <label>类型</label>
    <input v-model="form.bulletinType" placeholder="例如：活动预告 / 比赛信息 / 同城动态 / 店铺活动" />

    <label>内容</label>
    <textarea v-model="form.content" rows="8" placeholder="请输入完整快讯内容" />

    <label>图片 URL</label>
    <input v-model="form.imageUrls" placeholder="https://a.jpg,https://b.jpg" />

    <input ref="fileInputRef" type="file" class="hidden" accept="image/*" @change="uploadImage" />
    <div class="inline">
      <button class="btn-soft" type="button" :disabled="uploading" @click="triggerSelect">
        {{ uploading ? '上传中...' : '本地上传图片' }}
      </button>
    </div>

    <div v-if="parseImages(form.imageUrls).length" class="preview-grid">
      <img v-for="img in parseImages(form.imageUrls)" :key="img" :src="img" alt="预览图" />
    </div>

    <div class="inline">
      <button class="btn-primary" @click="submit">提交审核</button>
      <button class="btn-soft" @click="$router.push('/bulletins')">返回列表</button>
    </div>
  </div>
</template>

<style scoped>
.hidden { display: none; }
.preview-grid { display: grid; grid-template-columns: repeat(auto-fill,minmax(120px,1fr)); gap: 8px; margin-top: 8px; }
.preview-grid img { width: 100%; height: 90px; object-fit: cover; border-radius: var(--radius-md); border: 1px solid var(--line); }
</style>
