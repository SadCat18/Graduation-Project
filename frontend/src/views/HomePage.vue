<script setup>
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { ACTIVITY_STATUS_LABEL, normalizeActivityStatus } from '../constants/activity'
import { normalizeMediaUrl, parseImages, firstImage } from '../utils/url'
import { getToken } from '../utils/auth'
import { loadCachedPublic } from '../utils/requestCache'

defineOptions({ name: 'HomePage' })

const router = useRouter()

const loading = ref(false)
const bannerList = ref([])
const posts = ref([])
const activities = ref([])
const notices = ref([])
const bulletins = ref([])
const newsList = ref([])
const recommendPosts = ref([])
const recommendActivities = ref([])
const currentSlide = ref(0)
let timerId = null
let deferredHomeDataId = null
let deferredHomeDataMode = ''

const fallbackBanners = [
  {
    bannerId: -1,
    title: '城市街区 · 动作瞬间',
    imageUrl: 'https://images.pexels.com/photos/1984121/pexels-photo-1984121.jpeg?auto=compress&cs=tinysrgb&w=1800',
    linkUrl: '/community'
  },
  {
    bannerId: -2,
    title: '夜间公园 · 灯下练板',
    imageUrl: 'https://images.pexels.com/photos/27733613/pexels-photo-27733613.jpeg?auto=compress&cs=tinysrgb&w=1800',
    linkUrl: '/activities'
  },
  {
    bannerId: -3,
    title: '晴天约板 · 一起开练',
    imageUrl: 'https://images.pexels.com/photos/10923771/pexels-photo-10923771.jpeg?auto=compress&cs=tinysrgb&w=1800',
    linkUrl: '/community'
  },
  {
    bannerId: -4,
    title: '碗池训练 · 控制路线',
    imageUrl: 'https://images.pexels.com/photos/10590453/pexels-photo-10590453.jpeg?auto=compress&cs=tinysrgb&w=1800',
    linkUrl: '/community'
  },
  {
    bannerId: -5,
    title: '黑白街式 · 线条感',
    imageUrl: 'https://images.pexels.com/photos/8374782/pexels-photo-8374782.jpeg?auto=compress&cs=tinysrgb&w=1800',
    linkUrl: '/community'
  },
  {
    bannerId: -6,
    title: '公园斜坡 · 速度练习',
    imageUrl: 'https://images.pexels.com/photos/10923772/pexels-photo-10923772.jpeg?auto=compress&cs=tinysrgb&w=1800',
    linkUrl: '/activities'
  }
]

const activeBanners = computed(() => (bannerList.value.length ? bannerList.value : fallbackBanners))
const currentBanner = computed(() => activeBanners.value[currentSlide.value] || activeBanners.value[0])
const entryCards = [
  { key: 'community', title: '看社区帖子', desc: '动作经验、装备讨论、路线分享都在这里', icon: 'posts', path: '/community', action: '进入社区' },
  { key: 'activities', title: '找同城约板', desc: '浏览附近活动，报名一起练板', icon: 'activities', path: '/activities', action: '查看活动' },
  { key: 'news', title: '看滑板资讯', desc: '赛事、品牌、装备和社区动态', icon: 'news', path: '/bulletins', action: '浏览快讯' },
  { key: 'coach', title: '问 AI 滑板老师', desc: '动作要点、训练计划和安全提醒', icon: 'skateboard', path: '/ai-coach', action: '开始提问' }
]
const homeStats = computed(() => [
  { label: '精选帖子', value: posts.value.length },
  { label: '同城活动', value: activities.value.length },
  { label: '资讯更新', value: newsList.value.length }
])
const autoplayIntervalMs = computed(() => {
  const raw = Number(currentBanner.value?.intervalSeconds || activeBanners.value?.[0]?.intervalSeconds || 5)
  const seconds = Number.isFinite(raw) ? Math.min(Math.max(raw, 2), 60) : 5
  return seconds * 1000
})



function activityStatusText(item) {
  const status = normalizeActivityStatus(item.activityStatus ?? item.status)
  return ACTIVITY_STATUS_LABEL[status] || '未知状态'
}

function goPostDetail(postId) {
  if (!postId) return
  router.push(`/community/post/${postId}`)
}

function goNewsDetail(newsId) {
  if (!newsId) return
  router.push(`/news/${newsId}`)
}

function goBannerTarget() {
  if (!currentBanner.value?.linkUrl) return
  const target = currentBanner.value.linkUrl.trim()
  if (target.startsWith('http://') || target.startsWith('https://')) {
    window.open(target, '_blank')
    return
  }
  router.push(target)
}

function bannerLinkText(linkUrl) {
  const target = String(linkUrl || '').trim()
  if (!target) return ''
  if (target.startsWith('/activities')) return '同城约板'
  if (target.startsWith('/community')) return '社区帖子'
  if (target.startsWith('/profile')) return '个人中心'
  if (target.startsWith('/')) return '站内页面'
  if (target.startsWith('http://') || target.startsWith('https://')) return '外部链接'
  return '页面详情'
}

function prevBanner() {
  const count = activeBanners.value.length
  if (!count) return
  currentSlide.value = (currentSlide.value - 1 + count) % count
}

function nextBanner() {
  const count = activeBanners.value.length
  if (!count) return
  currentSlide.value = (currentSlide.value + 1) % count
}

function startAutoplay() {
  stopAutoplay()
  if (activeBanners.value.length <= 1) return
  timerId = setInterval(() => {
    nextBanner()
  }, autoplayIntervalMs.value)
}

function stopAutoplay() {
  if (timerId) {
    clearInterval(timerId)
    timerId = null
  }
}


async function loadHomePrimaryData() {
  loading.value = true
  try {
    const [bannerData, postData, activityData] = await Promise.all([
      loadCachedPublic('home:banners', () => api.publicBanners()),
      loadCachedPublic('home:posts', () => api.posts({ page: 1, size: 6 })),
      loadCachedPublic('home:activities', () => api.publicActivities({ page: 1, size: 6 }))
    ])
    bannerList.value = (bannerData || []).filter(item => item.status !== '1')
    posts.value = postData?.list || []
    activities.value = activityData?.list || []
    currentSlide.value = 0
    startAutoplay()
  } finally {
    loading.value = false
  }
}

async function loadHomeSecondaryData() {
  const [noticeResult, bulletinResult, newsResult] = await Promise.allSettled([
    loadCachedPublic('public:notices', () => api.notices()),
    loadCachedPublic('public:bulletins:home', () => api.bulletins({ limit: 5 })),
    loadCachedPublic('public:news', () => api.news())
  ])

  if (noticeResult.status === 'fulfilled') {
    notices.value = noticeResult.value || []
  }
  if (bulletinResult.status === 'fulfilled') {
    bulletins.value = bulletinResult.value || []
  }
  if (newsResult.status === 'fulfilled') {
    newsList.value = newsResult.value || []
  }
}

async function loadRecommendations() {
  if (!getToken()) {
    recommendPosts.value = []
    recommendActivities.value = []
    return
  }
  try {
    const rec = await api.recommendations()
    recommendPosts.value = rec?.posts || []
    recommendActivities.value = rec?.activities || []
  } catch (_) {
    recommendPosts.value = []
    recommendActivities.value = []
  }
}

function cancelDeferredHomeData() {
  if (!deferredHomeDataId) return
  if (deferredHomeDataMode === 'idle' && 'cancelIdleCallback' in window) {
    window.cancelIdleCallback(deferredHomeDataId)
  } else {
    clearTimeout(deferredHomeDataId)
  }
  deferredHomeDataId = null
  deferredHomeDataMode = ''
}

function scheduleDeferredHomeData() {
  cancelDeferredHomeData()
  const run = () => {
    deferredHomeDataId = null
    deferredHomeDataMode = ''
    loadHomeSecondaryData()
    loadRecommendations()
  }
  if ('requestIdleCallback' in window) {
    deferredHomeDataMode = 'idle'
    deferredHomeDataId = window.requestIdleCallback(run, { timeout: 1800 })
  } else {
    deferredHomeDataMode = 'timeout'
    deferredHomeDataId = window.setTimeout(run, 700)
  }
}

onMounted(async () => {
  await loadHomePrimaryData()
  scheduleDeferredHomeData()
})
onBeforeUnmount(() => {
  cancelDeferredHomeData()
  stopAutoplay()
})
onActivated(() => {
  startAutoplay()
})
onDeactivated(() => {
  stopAutoplay()
})
</script>

<template>
  <div class="home-wrap">
    <section class="hero-stage" @mouseenter="stopAutoplay" @mouseleave="startAutoplay">
      <div class="carousel-main" @click="goBannerTarget">
        <transition name="carousel-fade" mode="out-in">
          <img :key="currentSlide" :src="currentBanner.imageUrl" :alt="currentBanner.title" fetchpriority="high" decoding="async" />
        </transition>
        <div class="hero-shade"></div>
        <button class="carousel-nav nav-prev" type="button" aria-label="上一张" @click.stop="prevBanner">
          <
        </button>
        <button class="carousel-nav nav-next" type="button" aria-label="下一张" @click.stop="nextBanner">
          >
        </button>
        <div class="hero-caption">
          <span>今日推荐</span>
          <strong>{{ currentBanner.title }}</strong>
          <small v-if="currentBanner.linkUrl">{{ bannerLinkText(currentBanner.linkUrl) }}</small>
        </div>
        <aside class="event-panel">
          <h3>滑板资讯</h3>
          <div v-for="item in newsList.slice(0, 5)" :key="item.newsId" class="event-row">
            <span class="event-badge">资讯</span>
            <div class="event-main">
              <p class="event-title link" @click.stop="goNewsDetail(item.newsId)">{{ item.title }}</p>
              <p class="event-meta">{{ item.createTime?.replace('T', ' ').slice(0, 10) }}</p>
            </div>
          </div>
          <p v-if="!newsList.length" class="event-meta">暂无滑板资讯，稍后将同步最新赛事与冠军动态。</p>
        </aside>
        <div class="dots">
          <button
            v-for="(banner, index) in activeBanners"
            :key="banner.bannerId ?? index"
            type="button"
            :class="['dot', { active: index === currentSlide }]"
            @click.stop="currentSlide = index"
          />
        </div>
      </div>
    </section>

    <section class="entry-grid" aria-label="主要入口">
      <button
        v-for="item in entryCards"
        :key="item.key"
        type="button"
        class="entry-card"
        @click="$router.push(item.path)"
      >
        <span class="entry-icon"><AppIcon :name="item.icon" :size="20" /></span>
        <span class="entry-copy">
          <strong>{{ item.title }}</strong>
          <small>{{ item.desc }}</small>
        </span>
        <em>{{ item.action }}</em>
      </button>
    </section>

    <section class="card info-stage">
      <div class="section-head info-head">
        <div>
          <p class="section-eyebrow">COMMUNITY</p>
          <h3>先看大家最近在聊什么</h3>
        </div>
        <button class="btn-soft" @click="$router.push('/community')">更多</button>
      </div>
      <div class="info-grid">
        <div class="featured-panel">
          <div
            v-for="item in posts.slice(0, 1)"
            :key="item.postId"
            class="featured-card"
            @click="goPostDetail(item.postId)"
          >
            <img v-if="firstImage(item.images)" :src="firstImage(item.images)" alt="帖子封面" loading="lazy" decoding="async" />
            <div class="featured-text">
              <h4>{{ item.title }}</h4>
              <p class="line-clamp">{{ item.content }}</p>
            </div>
          </div>
        </div>
        <div class="bulletin-panel">
          <h4 class="bulletin-title">社区快讯</h4>
          <div v-for="item in bulletins.slice(0, 6)" :key="item.bulletinId" class="bulletin-row">
            <p class="link" @click="$router.push(`/bulletins/${item.bulletinId}`)">◈ {{ item.title }}</p>
            <span>{{ item.createTime?.replace('T', ' ').slice(0, 10) }}</span>
          </div>

        </div>
      </div>
    </section>

    <section class="card news-stage">
      <div class="section-head">
        <div>
          <p class="section-eyebrow">NEWS</p>
          <h3>滑板资讯</h3>
        </div>
        <button class="btn-soft" @click="$router.push('/bulletins')">更多快讯</button>
      </div>
        <div class="news-list">
          <div v-for="item in newsList.slice(0, 6)" :key="item.newsId" class="news-row">
            <p class="link" @click="goNewsDetail(item.newsId)">• {{ item.title }}</p>
            <span>{{ item.createTime?.replace('T', ' ').slice(0, 10) }}</span>
          </div>
          <p v-if="!newsList.length" class="muted">暂无滑板资讯数据。</p>
      </div>
    </section>

    <section class="grid-2">
      <div class="card" v-if="recommendPosts.length || recommendActivities.length">
        <div class="section-head">
          <h3>为你推荐</h3>
        </div>
        <div
          v-for="item in recommendPosts.slice(0, 3)"
          :key="`rp-${item.postId}`"
          class="feed-item clickable-feed-item"
          @click="goPostDetail(item.postId)"
        >
          <img v-if="firstImage(item.images)" :src="firstImage(item.images)" alt="推荐帖子封面" loading="lazy" decoding="async" />
          <div>
            <h4>{{ item.title }}</h4>
            <p class="muted">{{ item.authorName }} · {{ item.category || '未分类' }}</p>
          </div>
        </div>
        <div v-for="item in recommendActivities.slice(0, 2)" :key="`ra-${item.activityId}`" class="activity-item">
          <h4>{{ item.title }}</h4>
          <p class="muted">{{ item.city || '未设置城市' }} {{ item.district || '' }} · {{ activityStatusText(item) }}</p>
        </div>
      </div>

      <div class="card">
        <div class="section-head">
          <h3>社区精选帖子</h3>
          <button class="btn-soft" @click="$router.push('/community')">更多</button>
        </div>
        <p v-if="loading" class="muted">加载中...</p>
        <div
          v-for="item in posts.slice(0, 4)"
          :key="item.postId"
          class="feed-item clickable-feed-item"
          @click="goPostDetail(item.postId)"
        >
          <img v-if="firstImage(item.images)" :src="firstImage(item.images)" alt="帖子封面" loading="lazy" decoding="async" />
          <div>
            <h4>{{ item.title }}</h4>
            <p class="muted">{{ item.authorName }} · {{ item.category || '未分类' }}</p>
            <p class="line-clamp">{{ item.content }}</p>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="section-head">
          <h3>同城约板活动</h3>
          <button class="btn-soft" @click="$router.push('/activities')">更多</button>
        </div>
        <p v-if="loading" class="muted">加载中...</p>
        <div v-for="item in activities.slice(0, 5)" :key="item.activityId" class="activity-item">
          <h4>{{ item.title }}</h4>
          <p class="muted">{{ item.city || '未设置城市' }} {{ item.district || '' }} · {{ item.publisherName }}</p>
          <p class="muted">状态：{{ activityStatusText(item) }}</p>
          <p class="muted">报名：{{ item.signNum || 0 }}{{ item.maxNum ? `/${item.maxNum}` : '' }}</p>
        </div>
      </div>

    </section>

    <section class="card">
      <div class="section-head">
        <h3>平台公告</h3>
        <button class="btn-soft" @click="$router.push('/community')">查看全部</button>
      </div>
      <div class="notice-grid">
        <div v-for="n in notices.slice(0, 3)" :key="n.noticeId" class="notice-item">
          <h4>{{ n.title }}</h4>
          <p class="line-clamp">{{ n.content }}</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home-wrap {
  display: grid;
  gap: 18px;
}

.hero-stage {
  position: relative;
  min-height: 520px;
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--line);
}

.carousel-main {
  position: relative;
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  min-height: 520px;
}

.carousel-main img {
  width: 100%;
  height: 520px;
  object-fit: cover;
  display: block;
  filter: saturate(1.02);
}

.hero-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0) 65%, rgba(15, 23, 42, 0.18) 100%);
  z-index: 1;
  pointer-events: none;
}

.hero-caption {
  position: absolute;
  left: 26px;
  bottom: 24px;
  z-index: 4;
  max-width: min(420px, calc(100% - 460px));
  color: #fff;
  display: grid;
  gap: 4px;
  text-shadow: 0 1px 8px rgba(15, 23, 42, 0.42);
}

.hero-caption span,
.hero-caption small {
  color: rgba(255, 255, 255, 0.82);
  font-size: 12px;
  line-height: 1.2;
}

.hero-caption strong {
  font-size: 18px;
  line-height: 1.3;
  font-weight: 700;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.carousel-fade-enter-active,
.carousel-fade-leave-active {
  transition: opacity 0.25s ease;
}

.carousel-fade-enter-from,
.carousel-fade-leave-to {
  opacity: 0;
}

.carousel-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 38px;
  height: 38px;
  border: none;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.5);
  color: #fff;
  font-size: 20px;
  line-height: 1;
  display: grid;
  place-items: center;
  box-shadow: none;
  text-shadow: none;
  opacity: 0;
  transition: opacity 0.25s ease;
  z-index: 3;
}

.carousel-main:hover .carousel-nav {
  opacity: 0.35;
}

.carousel-nav:hover {
  opacity: 0.7 !important;
}

.nav-prev {
  left: 12px;
}

.nav-next {
  right: 12px;
}

.hero-sub {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.16em;
  color: rgba(255, 255, 255, 0.88);
}

.entry-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.entry-card {
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
  padding: 14px;
  min-height: 142px;
  display: grid;
  grid-template-rows: auto 1fr auto;
  align-items: start;
  gap: 12px;
  text-align: left;
  color: var(--text);
  box-shadow: var(--shadow-sm);
}

.entry-card:hover {
  border-color: #94a3b8;
  transform: translateY(-2px);
}

.entry-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: #111827;
  color: #fff;
}

.entry-copy {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.entry-copy strong {
  font-size: 17px;
  color: #111827;
}

.entry-copy small {
  color: var(--text-muted);
  line-height: 1.45;
}

.entry-card em {
  color: #111827;
  font-style: normal;
  font-weight: 700;
  font-size: 13px;
}

.section-eyebrow {
  margin: 0 0 4px;
  color: var(--text-muted);
  font-size: 12px;
  letter-spacing: 0.08em;
}

.event-panel {
  position: absolute;
  right: 20px;
  top: 78px;
  width: min(360px, 27%);
  max-height: calc(100% - 96px);
  overflow: auto;
  z-index: 5;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(226, 232, 240, 0.85);
  padding: 18px 20px;
  color: var(--text);
  border-radius: var(--radius-md);
  backdrop-filter: blur(8px);
}

.event-panel::-webkit-scrollbar {
  width: 6px;
}

.event-panel::-webkit-scrollbar-thumb {
  background: rgba(100, 116, 139, 0.35);
  border-radius: 999px;
}

.event-panel h3 {
  margin: 0 0 12px;
  font-size: 20px;
  color: var(--text);
}

.event-row {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 10px;
  align-items: start;
  padding: 9px 0;
  border-top: 1px solid var(--line);
}

.event-badge {
  margin-top: 2px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 2px;
  background: var(--primary);
  padding: 1px 7px;
  font-size: 11px;
  white-space: nowrap;
}

.event-title {
  margin: 0;
  font-size: 16px;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.event-meta {
  margin: 4px 0 0;
  color: var(--text-soft);
  font-size: 12px;
}

.dots {
  position: absolute;
  left: 50%;
  bottom: 16px;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
  z-index: 6;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  background: rgba(255, 255, 255, 0.78);
  transition: all 0.25s ease;
}

.dot.active {
  transform: scale(1.12);
}

.info-stage {
  padding: 20px 22px;
}

.info-head h3 {
  font-size: 30px;
  margin: 0;
}

.info-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 18px;
}

.featured-card {
  cursor: pointer;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}

.featured-card img {
  width: 100%;
  height: 260px;
  object-fit: cover;
}

.featured-text {
  padding: 14px 16px;
}

.featured-text h4 {
  margin: 0 0 10px;
  font-size: 32px;
}

.bulletin-panel {
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 14px 16px;
}

.bulletin-title {
  margin: 0 0 10px;
  font-size: 26px;
}

.bulletin-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  border-top: 1px solid var(--line);
  padding: 10px 0;
}

.bulletin-row span {
  color: var(--text-soft);
  font-size: 13px;
}

.panel-actions {
  margin-top: 10px;
}

.news-stage {
  padding-top: 14px;
}

.news-list {
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}

.news-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid var(--line);
}

.news-row:first-child {
  border-top: 0;
}

.news-row p {
  margin: 0;
  word-break: break-word;
}

.news-row span {
  color: var(--text-soft);
  font-size: 13px;
  white-space: nowrap;
}

.grid-2 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.feed-item {
  display: grid;
  grid-template-columns: 110px 1fr;
  gap: 12px;
  border-bottom: 1px solid var(--line);
  padding: 12px 0;
}

.clickable-feed-item {
  cursor: pointer;
}

.feed-item:last-child,
.activity-item:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.feed-item img {
  width: 100%;
  height: 78px;
  border-radius: 10px;
  object-fit: cover;
}

.feed-item h4,
.activity-item h4,
.notice-item h4 {
  margin: 0 0 4px;
}

.feed-item p,
.activity-item p,
.notice-item p {
  margin: 0;
}

.activity-item {
  border-bottom: 1px solid var(--line);
  padding: 12px 0;
}
.link { cursor: pointer; }
.link:hover { text-decoration: underline; }

.notice-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.notice-item {
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  padding: 12px;
  background: #fcfcfc;
}

.line-clamp {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

@media (max-width: 980px) {
  .carousel-main,
  .carousel-main img,
  .hero-stage {
    min-height: 420px;
    height: 420px;
  }

  .info-grid,
  .grid-2 {
    grid-template-columns: 1fr;
  }

  .event-panel {
    display: none;
  }

  .hero-caption {
    max-width: calc(100% - 52px);
  }

  .notice-grid {
    grid-template-columns: 1fr;
  }

  .entry-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .feed-item {
    grid-template-columns: 1fr;
  }

  .carousel-main,
  .carousel-main img,
  .hero-stage {
    min-height: 340px;
    height: 340px;
  }

  .hero-caption {
    left: 18px;
    bottom: 20px;
  }

  .hero-caption strong {
    font-size: 16px;
  }

  .entry-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 480px) {
  .home-wrap {
    gap: 12px;
  }

  .carousel-main,
  .carousel-main img,
  .hero-stage {
    min-height: 290px;
    height: 290px;
  }

  .hero-caption {
    left: 14px;
    right: 14px;
    bottom: 18px;
    max-width: none;
  }

  .info-head h3 {
    font-size: 24px;
  }

  .featured-text h4 {
    font-size: 24px;
  }
}
</style>
