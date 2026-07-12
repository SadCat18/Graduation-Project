<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { normalizeMediaUrl } from '../utils/url'

const route = useRoute()
const router = useRouter()

const video = ref(null)
const relatedVideos = ref([])
const loading = ref(false)
const error = ref('')
const playerError = ref(false)
const copyState = ref('')
const sharing = ref(false)
let copyTimer = null

const videoId = computed(() => Number(route.params.id))
const videoUrl = computed(() => normalizeMediaUrl(video.value?.url))
const coverUrl = computed(() => normalizeMediaUrl(video.value?.cover))
const currentWatchUrl = computed(() => {
  if (typeof window === 'undefined') return ''
  return window.location.href
})
const visibleRelatedVideos = computed(() =>
  (relatedVideos.value || [])
    .filter(item => Number(item.videoId) !== Number(videoId.value))
    .slice(0, 4)
)
const serverTrickTags = computed(() => String(video.value?.trickTags || '')
  .split(',')
  .map(item => item.trim())
  .filter(Boolean)
)
const displaySkillTags = computed(() => serverTrickTags.value.length ? serverTrickTags.value : skillTags(video.value))

function formatDateTime(value) {
  if (!value) return '刚刚发布'
  return String(value).replace('T', ' ').slice(0, 16)
}

function splitIntro(value) {
  return String(value || '')
    .split(/\n+/)
    .map(item => item.trim())
    .filter(Boolean)
}

function skillTags(item) {
  const text = `${item?.title || ''} ${item?.intro || ''}`
  const tags = []
  if (/Ollie|豚跳|起跳/.test(text)) tags.push('Ollie')
  if (/Manual|后轮|平衡/.test(text)) tags.push('平衡')
  if (/安全|护具|摔|热身/.test(text)) tags.push('安全')
  if (/拍|镜头|角度|视频/.test(text)) tags.push('拍摄')
  if (/新手|入门|基础/.test(text)) tags.push('入门')
  return tags.length ? tags.slice(0, 4) : ['教学视频', '动作观察']
}

async function loadVideo() {
  if (!Number.isFinite(videoId.value) || videoId.value <= 0) {
    error.value = '视频参数不正确'
    return
  }

  loading.value = true
  error.value = ''
  playerError.value = false
  try {
    const [detail, list] = await Promise.all([
      api.videoDetail(videoId.value),
      api.videos()
    ])
    video.value = detail
    relatedVideos.value = Array.isArray(list) ? list : []
  } catch (e) {
    error.value = e?.message || '视频加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/ai-coach')
}

function goCoach() {
  router.push('/ai-coach')
}

function goVideo(nextVideoId) {
  if (!nextVideoId) return
  router.push(`/videos/${nextVideoId}`)
}

async function copyText(text) {
  if (!text) return false
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
    return true
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  const ok = document.execCommand('copy')
  document.body.removeChild(textarea)
  return ok
}

async function copyVideoLink() {
  try {
    const ok = await copyText(currentWatchUrl.value || videoUrl.value)
    copyState.value = ok ? '已复制' : '复制失败'
  } catch (_) {
    copyState.value = '复制失败'
  }
  window.clearTimeout(copyTimer)
  copyTimer = window.setTimeout(() => {
    copyState.value = ''
  }, 1800)
}

async function shareVideo() {
  if (sharing.value) return
  sharing.value = true
  try {
    const payload = {
      title: video.value?.title || '滑板教学视频',
      text: video.value?.intro || '看看这条滑板教学视频',
      url: currentWatchUrl.value
    }
    if (navigator.share) {
      await navigator.share(payload)
    } else {
      await copyVideoLink()
    }
  } finally {
    sharing.value = false
  }
}

function openOriginalVideo() {
  if (!videoUrl.value) return
  window.location.href = videoUrl.value
}

watch(videoId, loadVideo)
onMounted(loadVideo)
onBeforeUnmount(() => {
  window.clearTimeout(copyTimer)
})
</script>

<template>
  <div class="video-watch-shell">
    <section class="watch-stage">
      <div class="watch-topbar">
        <button type="button" class="btn-soft icon-text-btn" @click="goBack">
          <AppIcon name="arrow-left" :size="15" />
          返回
        </button>
        <button type="button" class="btn-soft icon-text-btn" @click="goCoach">
          <AppIcon name="skateboard" :size="15" />
          问教练
        </button>
      </div>

      <p v-if="loading" class="watch-status">正在加载视频...</p>
      <p v-else-if="error" class="watch-status is-error">{{ error }}</p>

      <template v-else-if="video">
        <div class="player-frame">
          <video
            v-if="videoUrl && !playerError"
            :key="video.videoId"
            :src="videoUrl"
            :poster="coverUrl"
            controls
            playsinline
            preload="metadata"
            @error="playerError = true"
          ></video>
          <div v-else class="player-fallback">
            <img v-if="coverUrl" :src="coverUrl" alt="视频封面" />
            <div class="fallback-copy">
              <span class="fallback-badge">无法直接播放</span>
              <strong>{{ video.title || '教学视频' }}</strong>
              <p>可能是外部视频地址或格式不受当前浏览器支持，可以复制链接或打开原视频继续观看。</p>
              <div class="fallback-actions">
                <button type="button" class="btn-primary" @click="openOriginalVideo">
                  <AppIcon name="link" :size="15" />
                  打开原视频
                </button>
                <button type="button" class="btn-soft" @click="copyVideoLink">
                  <AppIcon name="copy" :size="15" />
                  复制链接
                </button>
              </div>
            </div>
          </div>
        </div>
      </template>
    </section>

    <main v-if="video && !loading" class="watch-content">
      <section class="watch-summary">
        <div class="watch-title-row">
          <div>
            <p class="watch-kicker">SKATE VIDEO</p>
            <h1>{{ video.title || '未命名教学视频' }}</h1>
            <p class="muted">发布于 {{ formatDateTime(video.createTime) }}</p>
          </div>
          <button type="button" class="btn-soft share-btn" :disabled="sharing" @click="shareVideo">
            <AppIcon name="share" :size="15" />
            分享
          </button>
        </div>

        <div class="watch-tags">
          <span v-for="tag in displaySkillTags" :key="tag">{{ tag }}</span>
        </div>

        <div class="creator-meta-panel">
          <div>
            <span>创作者系列</span>
            <strong>{{ video.creatorSeries || '独立教学视频' }}</strong>
          </div>
          <div>
            <span>内容质量分</span>
            <strong>{{ Number(video.qualityScore || 0) }}</strong>
          </div>
          <div>
            <span>动作标签</span>
            <strong>{{ displaySkillTags.join(' / ') }}</strong>
          </div>
        </div>

        <div class="intro-panel">
          <h2>视频简介</h2>
          <p v-if="!splitIntro(video.intro).length" class="muted">创作者暂未填写简介。观看时可以重点观察脚位、肩线和落地稳定性。</p>
          <p v-for="line in splitIntro(video.intro)" :key="line">{{ line }}</p>
        </div>

        <div class="training-panel">
          <h2>观看时可以留意</h2>
          <div class="training-grid">
            <div>
              <strong>脚位</strong>
              <span>起跳前后脚是否稳定，落地时脚是否回到板钉附近。</span>
            </div>
            <div>
              <strong>重心</strong>
              <span>肩膀和髋部是否跟着板走，身体有没有明显后仰。</span>
            </div>
            <div>
              <strong>节奏</strong>
              <span>先慢速看动作顺序，再回到原速模仿节拍。</span>
            </div>
          </div>
        </div>
      </section>

      <section v-if="visibleRelatedVideos.length" class="related-videos">
        <div class="section-head">
          <div>
            <h2>继续看教学视频</h2>
            <p class="muted">同站内容，适合顺着动作线索继续观察。</p>
          </div>
        </div>
        <button
          v-for="item in visibleRelatedVideos"
          :key="item.videoId"
          type="button"
          class="related-video-item"
          @click="goVideo(item.videoId)"
        >
          <span class="related-cover">
            <img v-if="normalizeMediaUrl(item.cover)" :src="normalizeMediaUrl(item.cover)" alt="视频封面" loading="lazy" decoding="async" />
            <AppIcon v-else name="play" :size="18" />
          </span>
          <span class="related-copy">
            <strong>{{ item.title || '教学视频' }}</strong>
            <small>{{ formatDateTime(item.createTime) }}</small>
            <span v-if="item.intro">{{ item.intro }}</span>
          </span>
        </button>
      </section>
    </main>

    <div v-if="video && !loading" class="watch-action-bar">
      <button type="button" @click="goCoach">
        <AppIcon name="skateboard" :size="16" />
        <span>问教练</span>
      </button>
      <button type="button" @click="copyVideoLink">
        <AppIcon name="copy" :size="16" />
        <span>{{ copyState || '复制' }}</span>
      </button>
      <button type="button" @click="shareVideo">
        <AppIcon name="share" :size="16" />
        <span>分享</span>
      </button>
      <button type="button" @click="openOriginalVideo">
        <AppIcon name="link" :size="16" />
        <span>原视频</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.video-watch-shell {
  display: grid;
  gap: 18px;
  max-width: 1040px;
  margin: 0 auto;
  padding-bottom: 78px;
}

.watch-stage {
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(18, 21, 19, 0.12);
  border-radius: 18px;
  background:
    linear-gradient(135deg, rgba(18, 21, 19, 0.94), rgba(8, 122, 109, 0.88)),
    #121513;
  box-shadow: 0 18px 42px rgba(18, 21, 19, 0.18);
}

.watch-topbar {
  position: absolute;
  z-index: 3;
  top: 12px;
  left: 12px;
  right: 12px;
  display: flex;
  justify-content: space-between;
  gap: 10px;
  pointer-events: none;
}

.watch-topbar button {
  pointer-events: auto;
}

.icon-text-btn,
.share-btn,
.fallback-actions button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  white-space: nowrap;
}

.player-frame {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  min-height: 240px;
  background: #050608;
}

.player-frame video,
.player-fallback,
.player-fallback img {
  width: 100%;
  height: 100%;
}

.player-frame video {
  display: block;
  object-fit: contain;
  background: #050608;
}

.player-fallback {
  position: relative;
  display: grid;
  place-items: center;
  min-height: inherit;
  color: #fff;
}

.player-fallback img {
  position: absolute;
  inset: 0;
  object-fit: cover;
  opacity: 0.34;
  filter: saturate(0.9);
}

.fallback-copy {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 12px;
  width: min(520px, calc(100% - 36px));
  padding: 24px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 16px;
  background: rgba(5, 6, 8, 0.62);
  backdrop-filter: blur(16px);
}

.fallback-copy strong {
  font-size: 24px;
  line-height: 1.2;
}

.fallback-copy p {
  margin: 0;
  color: rgba(255, 255, 255, 0.78);
}

.fallback-badge {
  width: fit-content;
  padding: 4px 9px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 999px;
  color: rgba(255, 255, 255, 0.82);
  font-size: 12px;
}

.fallback-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.watch-status {
  display: grid;
  place-items: center;
  min-height: 240px;
  margin: 0;
  color: #fff;
}

.watch-status.is-error {
  color: #fecaca;
}

.watch-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 0.38fr);
  gap: 18px;
  align-items: start;
}

.watch-summary,
.related-videos {
  display: grid;
  gap: 16px;
}

.watch-title-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.watch-kicker {
  margin: 0 0 6px;
  color: var(--accent);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0;
}

.watch-title-row h1 {
  margin: 0 0 6px;
  font-size: clamp(26px, 4vw, 42px);
  line-height: 1.08;
  letter-spacing: 0;
}

.watch-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.watch-tags span {
  padding: 7px 10px;
  border-radius: 999px;
  border: 1px solid rgba(8, 122, 109, 0.18);
  background: rgba(240, 253, 250, 0.86);
  color: var(--accent);
  font-size: 13px;
  font-weight: 700;
}

.creator-meta-panel {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.creator-meta-panel div {
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 12px;
  border: 1px solid rgba(8, 122, 109, 0.18);
  border-radius: 10px;
  background: rgba(8, 122, 109, 0.07);
}

.creator-meta-panel span {
  color: var(--text-soft);
  font-size: 12px;
}

.creator-meta-panel strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.intro-panel,
.training-panel,
.related-videos {
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
  box-shadow: var(--shadow-sm);
}

.intro-panel h2,
.training-panel h2,
.related-videos h2 {
  margin: 0;
  font-size: 18px;
  letter-spacing: 0;
}

.intro-panel {
  gap: 10px;
}

.intro-panel p {
  margin: 0;
  color: var(--text-soft);
}

.training-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.training-grid div {
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 12px;
  border-radius: 12px;
  background: linear-gradient(180deg, #f8fafc, #f3f4f6);
}

.training-grid span {
  color: var(--text-soft);
  font-size: 13px;
}

.related-video-item {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 12px;
  width: 100%;
  padding: 10px;
  text-align: left;
  border-color: rgba(18, 21, 19, 0.08);
}

.related-cover {
  display: grid;
  place-items: center;
  width: 76px;
  aspect-ratio: 16 / 10;
  overflow: hidden;
  border-radius: 10px;
  background: #121513;
  color: #fff;
}

.related-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.related-copy {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.related-copy strong,
.related-copy span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.related-copy small {
  color: var(--text-muted);
}

.related-copy span {
  color: var(--text-soft);
  font-size: 13px;
}

.watch-action-bar {
  position: fixed;
  z-index: 20;
  left: 50%;
  bottom: max(14px, env(safe-area-inset-bottom));
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  width: min(520px, calc(100% - 24px));
  padding: 8px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 16px 32px rgba(18, 21, 19, 0.18);
  backdrop-filter: blur(18px) saturate(1.2);
  transform: translateX(-50%);
}

.watch-action-bar button {
  display: grid;
  place-items: center;
  gap: 3px;
  min-width: 0;
  padding: 8px 4px;
  border-radius: 12px;
  font-size: 12px;
}

.watch-action-bar span {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 860px) {
  .video-watch-shell {
    gap: 14px;
    max-width: none;
    margin: -8px -8px 0;
  }

  .watch-stage {
    border-radius: 0 0 18px 18px;
  }

  .watch-content {
    grid-template-columns: 1fr;
    padding: 0 10px;
  }

  .player-frame {
    aspect-ratio: 16 / 10;
    min-height: 220px;
  }

  .watch-title-row {
    display: grid;
  }

  .share-btn {
    width: fit-content;
  }

  .training-grid {
    grid-template-columns: 1fr;
  }

  .creator-meta-panel {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 520px) {
  .watch-topbar {
    top: 10px;
    left: 10px;
    right: 10px;
  }

  .player-frame {
    aspect-ratio: 4 / 3;
    min-height: 260px;
  }

  .fallback-copy {
    width: calc(100% - 24px);
    padding: 18px;
  }

  .fallback-copy strong {
    font-size: 20px;
  }

  .intro-panel,
  .training-panel,
  .related-videos {
    padding: 15px;
    border-radius: 14px;
  }
}
</style>
