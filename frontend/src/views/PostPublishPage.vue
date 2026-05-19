<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { getToken } from '../utils/auth'

const router = useRouter()

const uploading = ref(false)
const fileInputRef = ref(null)
const selectedImages = ref([])
const previewUrls = ref([])
const maxImageCount = 9

const postForm = reactive({
  title: '',
  category: '技巧',
  content: ''
})

function clearPreviewUrls() {
  previewUrls.value.forEach(url => URL.revokeObjectURL(url))
  previewUrls.value = []
}

function clearImageSelection() {
  selectedImages.value = []
  clearPreviewUrls()
  if (fileInputRef.value) fileInputRef.value.value = ''
}

function triggerSelectImages() {
  fileInputRef.value?.click()
}

function onSelectImages(event) {
  const pickedFiles = Array.from(event.target.files || [])
    .filter(file => file.type.startsWith('image/'))
  if (!pickedFiles.length) return

  const mergedFiles = [...selectedImages.value]
  for (const file of pickedFiles) {
    if (mergedFiles.length >= maxImageCount) break
    const duplicate = mergedFiles.some(
      item => item.name === file.name && item.size === file.size && item.lastModified === file.lastModified
    )
    if (!duplicate) mergedFiles.push(file)
  }

  selectedImages.value = mergedFiles
  clearPreviewUrls()
  previewUrls.value = selectedImages.value.map(file => URL.createObjectURL(file))
  if (fileInputRef.value) fileInputRef.value.value = ''
}

function removeSelectedImage(index) {
  const removedUrl = previewUrls.value[index]
  if (removedUrl) URL.revokeObjectURL(removedUrl)
  selectedImages.value.splice(index, 1)
  previewUrls.value.splice(index, 1)
}

async function uploadSelectedImages() {
  if (!selectedImages.value.length) return []
  const uploadResults = await Promise.all(
    selectedImages.value.map(file => api.uploadPostImage(file))
  )
  return uploadResults.filter(Boolean)
}

async function publishPost() {
  if (!getToken()) {
    router.push('/login')
    return
  }
  if (!postForm.title.trim()) {
    alert('请填写帖子标题')
    return
  }

  uploading.value = true
  try {
    const imageUrls = await uploadSelectedImages()
    await api.createPost({
      title: postForm.title.trim(),
      category: postForm.category.trim(),
      content: postForm.content.trim(),
      images: imageUrls.join(',')
    })
    alert('发布成功')
    router.push('/community')
  } catch (e) {
    alert(e?.message || '发布失败，请稍后重试')
  } finally {
    uploading.value = false
  }
}

onBeforeUnmount(() => {
  clearPreviewUrls()
})
</script>

<template>
  <div class="publish-shell">
    <div class="card publish-card">
      <div class="section-head">
        <div>
          <h2>发布社区帖子</h2>
          <p class="muted">上传本地图片，分享动作、装备与路线。</p>
        </div>
        <button class="btn-soft" @click="$router.push('/community')">返回社区</button>
      </div>

      <div class="form-grid">
        <input v-model="postForm.title" placeholder="帖子标题（如：新手 Ollie 训练心得）" />
        <input v-model="postForm.category" placeholder="分类（技巧/赛事/装备/刷街）" />
      </div>

      <textarea v-model="postForm.content" placeholder="写下你的滑板内容..." />

      <div class="upload-block">
        <div class="upload-header">
          <span class="upload-label">上传图片</span>
          <span class="muted">已选 {{ selectedImages.length }}/{{ maxImageCount }}</span>
        </div>
        <input
          ref="fileInputRef"
          class="file-input"
          type="file"
          accept="image/*"
          multiple
          @change="onSelectImages"
        />
        <div class="inline">
          <button type="button" class="btn-soft" @click="triggerSelectImages">
            <AppIcon name="upload" :size="15" />
            选择本地图片
          </button>
          <button v-if="selectedImages.length" type="button" @click="clearImageSelection">清空图片</button>
        </div>
        <p class="muted">仅支持本地上传，最多 9 张，单张不超过 10MB。</p>
      </div>

      <div v-if="previewUrls.length" class="preview-grid">
        <div v-for="(url, index) in previewUrls" :key="url" class="preview-item">
          <img :src="url" alt="预览图" />
          <button class="remove-btn" @click="removeSelectedImage(index)">移除</button>
        </div>
      </div>

      <button class="btn-primary publish-btn" :disabled="uploading" @click="publishPost">
        {{ uploading ? '上传并发布中...' : '确认发布帖子' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.publish-shell {
  max-width: 980px;
  margin: 0 auto;
}

.publish-card {
  display: grid;
  gap: 14px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.upload-block {
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  padding: 12px;
  background: var(--surface-muted);
}

.upload-label {
  font-weight: 600;
  color: var(--text-soft);
}

.upload-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.file-input {
  display: none;
}

.preview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
  gap: 10px;
}

.preview-item {
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 6px;
  background: #fff;
}

.preview-item img {
  width: 100%;
  height: 92px;
  object-fit: cover;
  border-radius: 7px;
  display: block;
}

.remove-btn {
  margin-top: 6px;
  width: 100%;
}

.publish-btn {
  justify-self: flex-start;
}

@media (max-width: 768px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .publish-btn {
    width: 100%;
  }
}
</style>
