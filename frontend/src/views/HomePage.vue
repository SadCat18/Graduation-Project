<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { ACTIVITY_STATUS_LABEL, normalizeActivityStatus } from '../constants/activity'
import { normalizeMediaUrl } from '../utils/url'
import { getToken } from '../utils/auth'

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

const fallbackBanners = [
  {
    bannerId: -1,
    title: '城市滑板社群 · 发现同好',
    imageUrl: '/brand/skate-hero-apple.svg',
    linkUrl: '/community'
  }
]

const activeBanners = computed(() => (bannerList.value.length ? bannerList.value : fallbackBanners))
const currentBanner = computed(() => activeBanners.value[currentSlide.value] || activeBanners.value[0])
const autoplayIntervalMs = computed(() => {
  const raw = Number(currentBanner.value?.intervalSeconds || activeBanners.value?.[0]?.intervalSeconds || 5)
  const seconds = Number.isFinite(raw) ? Math.min(Math.max(raw, 2), 60) : 5
  return seconds * 1000
})

function parseImages(images) {
  if (!images) return []
  return String(images)
    .split(',')
    .map(item => item.trim())
    .map(item => normalizeMediaUrl(item))
    .filter(Boolean)
}

function firstImage(images) {
  const list = parseImages(images)
  return list.length ? list[0] : ''
}

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

async function loadHomeData() {
  loading.value = true
  try {
    const [bannerData, postData, activityData, noticeData, bulletinData, newsData] = await Promise.all([
      api.publicBanners(),
      api.posts({ page: 1, size: 6 }),
      api.publicActivities({ page: 1, size: 6 }),
      api.notices(),
      api.bulletins({ limit: 5 }),
      api.news()
    ])
    bannerList.value = (bannerData || []).filter(item => item.status !== '1')
    posts.value = postData?.list || []
    activities.value = activityData?.list || []
    notices.value = noticeData || []
    bulletins.value = bulletinData || []
    newsList.value = newsData || []
    if (getToken()) {
      try {
        const rec = await api.recommendations()
        recommendPosts.value = rec?.posts || []
        recommendActivities.value = rec?.activities || []
      } catch (_) {
        recommendPosts.value = []
        recommendActivities.value = []
      }
    } else {
      recommendPosts.value = []
      recommendActivities.value = []
    }
    currentSlide.value = 0
    startAutoplay()
  } finally {
    loading.value = false
  }
}

onMounted(loadHomeData)
onBeforeUnmount(stopAutoplay)
watch(currentSlide, () => {
  startAutoplay()
})
</script>

<template>
  <div class="home-wrap">
    <section class="hero-stage" @mouseenter="stopAutoplay" @mouseleave="startAutoplay">
      <div class="carousel-main" @click="goBannerTarget">
        <transition name="carousel-fade" mode="out-in">
          <img :key="currentSlide" :src="currentBanner.imageUrl" :alt="currentBanner.title" />
        </transition>
        <div class="hero-shade"></div>
        <button class="carousel-nav nav-prev" type="button" aria-label="上一张" @click.stop="prevBanner">
          <
        </button>
        <button class="carousel-nav nav-next" type="button" aria-label="下一张" @click.stop="nextBanner">
          >
        </button>
        <div class="carousel-mask">
          <p class="hero-sub">SKATE COMMUNITY</p>
          <h1>{{ currentBanner.title }}</h1>
          <p v-if="currentBanner.linkUrl" class="muted">点击前往：{{ bannerLinkText(currentBanner.linkUrl) }}</p>
          <div class="inline hero-actions">
            <button class="btn-primary" @click.stop="$router.push('/community')">社区帖子</button>
            <button class="btn-soft" @click.stop="$router.push('/activities')">同城约板</button>
          </div>
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

    <section class="card info-stage">
      <div class="section-head info-head">
        <h3>社区帖子</h3>
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
            <img v-if="firstImage(item.images)" :src="firstImage(item.images)" alt="帖子封面" />
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
        <h3>滑板资讯</h3>
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
          <img v-if="firstImage(item.images)" :src="firstImage(item.images)" alt="推荐帖子封面" />
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
          <img v-if="firstImage(item.images)" :src="firstImage(item.images)" alt="帖子封面" />
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
  background: linear-gradient(90deg, rgba(15, 23, 42, 0.18) 0%, rgba(15, 23, 42, 0.08) 42%, rgba(15, 23, 42, 0.24) 100%);
  z-index: 1;
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

.carousel-card:hover .carousel-nav {
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

.carousel-mask {
  position: absolute;
  left: 46px;
  top: 86px;
  width: min(620px, 55%);
  z-index: 4;
  color: #ffffff;
}

.hero-sub {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.16em;
  color: rgba(255, 255, 255, 0.88);
}

.carousel-mask h1 {
  margin: 12px 0 10px;
  font-size: clamp(34px, 4.6vw, 62px);
  line-height: 1.06;
  word-break: break-word;
}

.carousel-mask p {
  margin: 4px 0 0;
  color: rgba(241, 245, 249, 0.92);
}

.hero-actions {
  margin-top: 16px;
}

.event-panel {
  position: absolute;
  right: 20px;
  top: 78px;
  width: min(360px, 27%);
  max-height: calc(100% - 96px);
  overflow: auto;
  z-index: 5;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid var(--line);
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
  font-size: 44px;
  margin: 0;
  letter-spacing: 0.02em;
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

  .carousel-mask {
    top: 42px;
    left: 18px;
    width: calc(100% - 36px);
  }

  .event-panel {
    display: none;
  }

  .info-grid,
  .grid-2 {
    grid-template-columns: 1fr;
  }

  .notice-grid {
    grid-template-columns: 1fr;
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

  .hero-actions {
    width: 100%;
  }

  .hero-actions button {
    min-height: 42px;
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

  .carousel-mask {
    top: 20px;
    left: 12px;
    width: calc(100% - 24px);
  }

  .carousel-mask h1 {
    margin: 8px 0;
    font-size: 28px;
    line-height: 1.15;
  }

  .hero-actions {
    margin-top: 10px;
    flex-wrap: wrap;
  }

  .hero-actions button {
    flex: 1 1 100%;
  }

  .info-head h3 {
    font-size: 32px;
  }

  .featured-text h4 {
    font-size: 24px;
  }
}
</style>
