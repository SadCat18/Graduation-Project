<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { normalizeMediaUrl } from '../utils/url'
import { BULLETIN_TYPES } from '../constants/bulletin'

const router = useRouter()
const form = reactive({ title: '', bulletinType: BULLETIN_TYPES[0], content: '', imageUrls: '' })
const fileInputRef = ref(null)
const uploading = ref(false)
const error = ref('')

function parseImages(raw) {
  if (!raw) return []
  return String(raw).split(',').map(item => normalizeMediaUrl(item.trim())).filter(Boolean)
}

const imagePreviewList = computed(() => parseImages(form.imageUrls))

function removeImage(index) {
  const arr = parseImages(form.imageUrls)
  arr.splice(index, 1)
  form.imageUrls = arr.join(',')
}

function triggerSelect() {
  fileInputRef.value?.click()
}

async function uploadImage(event) {
  const file = (event.target.files || [])[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    error.value = '请选择图片文件。'
    return
  }
  uploading.value = true
  error.value = ''
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
  if (!form.title.trim()) {
    error.value = '请填写标题。'
    return
  }
  if (!form.bulletinType.trim()) {
    error.value = '请选择类型。'
    return
  }
  if (!form.content.trim()) {
    error.value = '请填写内容。'
    return
  }
  error.value = ''
  await api.createBulletin(form)
  alert('提交审核成功，待管理员审核通过后展示。')
  router.push('/bulletins')
}
</script>

<template>
  <div class="card publish-wrap">
    <h3>发布社区快讯</h3>
    <p class="muted">支持本地上传图片或填写图片 URL（多个地址用英文逗号分隔）。</p>

    <label>标题</label>
    <input v-model="form.title" placeholder="例如：周末街式赛报名开启" />

    <label>类型</label>
    <select v-model="form.bulletinType">
      <option v-for="item in BULLETIN_TYPES" :key="item" :value="item">{{ item }}</option>
    </select>

    <label>内容</label>
    <textarea v-model="form.content" rows="8" placeholder="请输入完整快讯内容" />

    <label>图片 URL</label>
    <input v-model="form.imageUrls" placeholder="https://a.jpg,https://b.jpg" />

    <input ref="fileInputRef" type="file" class="hidden" accept="image/*" @change="uploadImage" />
    <div class="inline action-row">
      <button class="btn-soft" type="button" :disabled="uploading" @click="triggerSelect">{{ uploading ? '上传中...' : '本地上传图片' }}</button>
      <span v-if="error" class="error">{{ error }}</span>
    </div>

    <div v-if="imagePreviewList.length" class="preview-grid">
      <div v-for="(img, index) in imagePreviewList" :key="img" class="preview-item">
        <img :src="img" alt="预览图" />
        <button class="btn-danger mini-remove" type="button" @click="removeImage(index)">移除</button>
      </div>
    </div>

    <div class="inline action-row">
      <button class="btn-primary" @click="submit">提交审核</button>
      <button class="btn-soft" @click="$router.push('/bulletins')">返回列表</button>
    </div>
  </div>
</template>

<style scoped>
.publish-wrap {
  padding: 20px;
}

.hidden {
  display: none;
}

.action-row {
  margin-top: 8px;
}

.preview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
  margin-top: 10px;
}

.preview-item {
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 6px;
  background: #fff;
}

.preview-item img {
  width: 100%;
  height: 100px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid var(--line);
  display: block;
}

.mini-remove {
  margin-top: 6px;
  width: 100%;
  min-height: 30px;
}
</style>
