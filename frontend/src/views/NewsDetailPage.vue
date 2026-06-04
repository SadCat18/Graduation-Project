<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { normalizeMediaUrl } from '../utils/url'

const route = useRoute()
const router = useRouter()
const detail = ref(null)
const loading = ref(false)

const displayTitle = computed(() => detail.value?.title || detail.value?.originTitle || '滑板资讯详情')
const displaySummary = computed(() => detail.value?.summary || detail.value?.aiSummary || detail.value?.originSummary || '')
const displayCategory = computed(() => detail.value?.category || detail.value?.aiCategory || '滑板资讯')
const displaySourceName = computed(() => detail.value?.sourceName || '滑板资讯来源')
const displayContent = computed(() => cleanNewsText(
  detail.value?.content || detail.value?.aiTranslatedContent || detail.value?.originContent || ''
))
const displayTime = computed(() => String(detail.value?.createTime || '').replace('T', ' '))

const coverUrl = computed(() => {
  const raw = detail.value?.cover
  if (!raw) return ''
  return normalizeMediaUrl(String(raw).trim())
})

async function loadData() {
  loading.value = true
  try {
    detail.value = await api.newsDetail(route.params.id)
  } finally {
    loading.value = false
  }
}

function openSourceUrl() {
  const raw = String(detail.value?.sourceUrl || '').trim()
  if (!raw) return
  window.open(raw, '_blank', 'noopener,noreferrer')
}

function cleanNewsText(value) {
  const raw = String(value || '').trim()
  if (!raw) return ''
  const lower = raw.toLowerCase()
  const isInstagramEmbed = lower.includes('instagram-media') || lower.includes('instagram.com/embed.js')
  const decoded = decodeHtmlEntities(raw)
  const withoutScript = decoded
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/p>/gi, '\n')
    .replace(/<[^>]+>/g, ' ')
  const cleaned = decodeHtmlEntities(withoutScript)
    .replace(/\u00a0/g, ' ')
    .replace(/[ \t\r\f\v]+/g, ' ')
    .replace(/ *\n */g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
  if (isInstagramEmbed && (!cleaned || cleaned.length > 180)) {
    return '这条资讯来自 Instagram 嵌入内容，请点击右侧原文链接查看完整内容。'
  }
  return cleaned
}

function cleanInlineText(value) {
  return cleanNewsText(value).replace(/\s+/g, ' ').trim()
}

function decodeHtmlEntities(value) {
  const textarea = document.createElement('textarea')
  textarea.innerHTML = String(value || '')
  return textarea.value
}

onMounted(loadData)
</script>

<template>
  <div class="news-detail-page">
    <div class="news-shell">
      <div class="inline top-nav">
        <button class="btn-soft" @click="router.back()">返回</button>
        <span class="muted">滑板资讯 / 详情</span>
      </div>

      <div class="news-layout card">
        <p v-if="loading" class="muted">资讯加载中...</p>
        <template v-else-if="detail">
          <div class="news-hero">
            <div class="news-hero-copy">
              <p class="eyebrow">{{ cleanInlineText(displayCategory) }}</p>
              <h1>{{ cleanInlineText(displayTitle) }}</h1>
              <p v-if="displaySummary" class="summary">{{ cleanInlineText(displaySummary) }}</p>
              <div class="meta-row">
                <span>{{ displayTime || '时间待补充' }}</span>
                <span>{{ cleanInlineText(displaySourceName) }}</span>
                <button
                  v-if="detail.sourceUrl"
                  type="button"
                  class="source-link"
                  @click="openSourceUrl"
                >
                  查看原文
                </button>
              </div>
            </div>
            <div class="news-cover-wrap" :class="{ empty: !coverUrl }">
              <img v-if="coverUrl" :src="coverUrl" alt="资讯封面" class="cover" />
              <div v-else class="cover-fallback">
                <strong>{{ cleanInlineText(displayCategory) }}</strong>
                <span>{{ cleanInlineText(displaySourceName) }}</span>
              </div>
            </div>
          </div>

          <div class="news-body">
            <article class="content-panel">
              <h3>正文内容</h3>
              <p class="content">{{ displayContent || '该资讯暂无可展示正文。' }}</p>
            </article>

            <aside class="info-panel">
              <div class="info-card">
                <h4>资讯信息</h4>
                <p><span>展示分类</span>{{ cleanInlineText(displayCategory) }}</p>
                <p><span>来源名称</span>{{ cleanInlineText(displaySourceName) }}</p>
                <p><span>发布时间</span>{{ displayTime || '待补充' }}</p>
              </div>

              <div v-if="detail.sourceUrl" class="info-card">
                <h4>原文入口</h4>
                <p class="link-block">{{ detail.sourceUrl }}</p>
                <button type="button" class="btn-primary source-btn" @click="openSourceUrl">打开原文链接</button>
              </div>
            </aside>
          </div>
        </template>
        <p v-else class="muted">该资讯不存在或暂不可见。</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.news-detail-page {
  padding: 8px 0 20px;
}

.news-shell {
  display: grid;
  gap: 12px;
}

.top-nav {
  margin-bottom: 2px;
}

.news-layout {
  padding: 18px;
  display: grid;
  gap: 18px;
}

.news-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(260px, 0.8fr);
  gap: 18px;
  align-items: stretch;
}

.news-hero-copy {
  display: grid;
  gap: 12px;
}

.eyebrow {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #7c3aed;
  font-weight: 700;
}

.news-hero-copy h1 {
  margin: 0;
  font-size: 34px;
  line-height: 1.25;
}

.summary {
  margin: 0;
  padding: 12px 14px;
  border-radius: 14px;
  background: linear-gradient(135deg, #f8fafc, #eef2ff);
  border: 1px solid var(--line);
  color: #334155;
  line-height: 1.7;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  align-items: center;
  color: var(--text-muted);
  font-size: 13px;
}

.source-link {
  border: 0;
  background: transparent;
  color: #2563eb;
  padding: 0;
  cursor: pointer;
}

.source-link:hover {
  text-decoration: underline;
}

.news-cover-wrap {
  min-height: 240px;
}

.news-cover-wrap.empty {
  border-radius: 18px;
  border: 1px solid var(--line);
  background: linear-gradient(135deg, #f8fafc, #e2e8f0);
  overflow: hidden;
}

.cover {
  width: 100%;
  height: 100%;
  min-height: 240px;
  max-height: 360px;
  object-fit: cover;
  border-radius: 18px;
  border: 1px solid var(--line);
  box-shadow: 0 14px 40px rgba(15, 23, 42, 0.08);
}

.cover-fallback {
  min-height: 240px;
  height: 100%;
  display: grid;
  place-items: center;
  text-align: center;
  gap: 8px;
  color: #334155;
  padding: 24px;
}

.cover-fallback strong {
  font-size: 22px;
}

.cover-fallback span {
  color: var(--text-muted);
}

.news-body {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) 320px;
  gap: 18px;
}

.content-panel,
.info-card {
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
}

.content-panel {
  padding: 18px;
}

.content-panel h3,
.info-card h4 {
  margin: 0 0 12px;
}

.content {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.9;
  color: #0f172a;
}

.info-panel {
  display: grid;
  gap: 12px;
  align-content: start;
}

.info-card {
  padding: 16px;
  display: grid;
  gap: 10px;
}

.info-card p {
  margin: 0;
  display: grid;
  gap: 4px;
  color: #334155;
}

.info-card span {
  font-size: 12px;
  color: var(--text-muted);
}

.link-block {
  word-break: break-all;
}

.source-btn {
  width: 100%;
}

@media (max-width: 920px) {
  .news-hero,
  .news-body {
    grid-template-columns: 1fr;
  }

  .news-hero-copy h1 {
    font-size: 28px;
  }
}
</style>
