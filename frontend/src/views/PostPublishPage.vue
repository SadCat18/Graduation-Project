<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import AiLoadingOverlay from '../components/AiLoadingOverlay.vue'
import { api } from '../api'
import { getToken } from '../utils/auth'

const router = useRouter()

const uploading = ref(false)
const polishing = ref(false)
const fileInputRef = ref(null)
const selectedImages = ref([])
const previewUrls = ref([])
const aiResult = ref(null)
const maxImageCount = 9
const categoryOptions = ['技巧交流', '装备讨论', '路线分享', '活动讨论', '经验分享']
const aiLoadingSteps = ['整理标题', '分析正文语气', '优化表达结构', '生成分类建议', '补全摘要与风险提示']

const postForm = reactive({
  title: '',
  category: '技巧交流',
  content: ''
})

const canRunAiPolish = computed(() => postForm.title.trim() || postForm.content.trim())

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

async function runAiPolish() {
  if (!getToken()) {
    router.push('/login')
    return
  }
  if (!canRunAiPolish.value) {
    alert('请先输入标题或正文，再使用 AI 润色')
    return
  }

  polishing.value = true
  try {
    const result = await api.postPolish({
      title: postForm.title.trim() || '未命名滑板帖子',
      content: postForm.content.trim() || '请根据标题补全并优化正文表达'
    })
    aiResult.value = {
      title: result?.title || '',
      content: result?.content || '',
      category: result?.category || '',
      summary: result?.summary || '',
      riskTips: Array.isArray(result?.riskTips) ? result.riskTips : []
    }
  } catch (e) {
    alert(e?.message || 'AI 润色失败，请稍后重试')
  } finally {
    polishing.value = false
  }
}

function applyAiTitle() {
  if (aiResult.value?.title) {
    postForm.title = aiResult.value.title
  }
}

function applyAiContent() {
  if (aiResult.value?.content) {
    postForm.content = aiResult.value.content
  }
}

function applyAiCategory() {
  if (aiResult.value?.category) {
    postForm.category = aiResult.value.category
  }
}

function applyAiAll() {
  applyAiTitle()
  applyAiContent()
  applyAiCategory()
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
    <AiLoadingOverlay
      :visible="polishing"
      title="AI 正在润色帖子"
      subtitle="正在结合滑板社区语境优化标题、正文和分类建议。"
      :steps="aiLoadingSteps"
    />

    <div class="card publish-card">
      <div class="section-head">
        <div>
          <h2>发布社区帖子</h2>
          <p class="muted">上传本地图片，分享动作、装备与路线。</p>
        </div>
        <div class="head-actions">
          <button
            type="button"
            class="btn-soft"
            :disabled="polishing"
            @click="runAiPolish"
          >
            <AppIcon name="refresh" :size="15" />
            {{ polishing ? 'AI 润色中...' : 'AI 润色' }}
          </button>
          <button class="btn-soft" @click="$router.push('/community')">返回社区</button>
        </div>
      </div>

      <div class="form-grid">
        <input v-model="postForm.title" placeholder="帖子标题（如：新手 Ollie 训练心得）" />
        <input v-model="postForm.category" placeholder="分类（技巧交流/装备讨论/路线分享）" />
      </div>

      <textarea v-model="postForm.content" placeholder="写下你的滑板内容..." />

      <div v-if="aiResult" class="ai-panel">
        <div class="ai-panel-head">
          <div>
            <span class="ai-badge">AI 润色结果</span>
            <p class="muted">先预览，再按需应用到当前帖子内容。</p>
          </div>
          <button type="button" class="btn-primary ai-apply-all" @click="applyAiAll">应用全部结果</button>
        </div>

        <div class="ai-result-grid">
          <div class="ai-result-block">
            <div class="ai-result-title-row">
              <h3>优化标题</h3>
              <button type="button" class="btn-soft" @click="applyAiTitle">应用 AI 标题</button>
            </div>
            <p class="ai-result-text">{{ aiResult.title || '暂无结果' }}</p>
          </div>

          <div class="ai-result-block">
            <div class="ai-result-title-row">
              <h3>推荐分类</h3>
              <button
                type="button"
                class="btn-soft"
                :disabled="!aiResult.category"
                @click="applyAiCategory"
              >
                采用推荐分类
              </button>
            </div>
            <div class="ai-category-row">
              <span class="ai-category-chip">{{ aiResult.category || '暂无分类建议' }}</span>
              <span
                v-if="aiResult.category && categoryOptions.includes(aiResult.category)"
                class="muted"
              >
                可直接应用到当前分类
              </span>
            </div>
          </div>

          <div class="ai-result-block ai-result-block-wide">
            <div class="ai-result-title-row">
              <h3>优化正文</h3>
              <button type="button" class="btn-soft" @click="applyAiContent">应用 AI 正文</button>
            </div>
            <p class="ai-result-text ai-result-content">{{ aiResult.content || '暂无结果' }}</p>
          </div>

          <div class="ai-result-block">
            <h3>一句话摘要</h3>
            <p class="ai-result-text">{{ aiResult.summary || '暂无摘要' }}</p>
          </div>

          <div class="ai-result-block">
            <h3>风险提示</h3>
            <ul v-if="aiResult.riskTips.length" class="risk-list">
              <li v-for="tip in aiResult.riskTips" :key="tip">{{ tip }}</li>
            </ul>
            <p v-else class="ai-result-text">未发现明显风险提示</p>
          </div>
        </div>
      </div>

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

.head-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.ai-panel {
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  padding: 14px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(247, 249, 255, 0.98));
  display: grid;
  gap: 14px;
}

.ai-panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.ai-badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.06);
  color: var(--text-soft);
  font-size: 13px;
  font-weight: 700;
}

.ai-apply-all {
  white-space: nowrap;
}

.ai-result-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.ai-result-block {
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  padding: 12px;
  display: grid;
  gap: 10px;
}

.ai-result-block-wide {
  grid-column: 1 / -1;
}

.ai-result-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}

.ai-result-title-row h3,
.ai-result-block h3 {
  margin: 0;
  font-size: 15px;
}

.ai-result-text {
  margin: 0;
  color: var(--text);
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-result-content {
  min-height: 108px;
}

.ai-category-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.ai-category-chip {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.08);
  color: var(--text);
  font-weight: 600;
}

.risk-list {
  margin: 0;
  padding-left: 18px;
  color: var(--text);
  display: grid;
  gap: 6px;
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
  .form-grid input,
  textarea,
  .upload-block button,
  .publish-btn {
    min-height: 42px;
  }

  textarea {
    min-height: 120px;
  }

  .form-grid,
  .ai-result-grid {
    grid-template-columns: 1fr;
  }

  .ai-panel-head,
  .ai-result-title-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .publish-btn {
    width: 100%;
  }
}

@media (max-width: 480px) {
  .section-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .head-actions {
    width: 100%;
  }

  .head-actions button,
  .ai-apply-all {
    width: 100%;
  }

  .preview-grid {
    grid-template-columns: 1fr 1fr;
  }

  .upload-block .inline {
    flex-wrap: wrap;
  }

  .upload-block .inline button {
    flex: 1 1 100%;
  }
}
</style>
