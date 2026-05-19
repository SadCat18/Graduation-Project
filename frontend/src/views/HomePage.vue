<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { ACTIVITY_STATUS_LABEL, normalizeActivityStatus } from '../constants/activity'
import { normalizeMediaUrl } from '../utils/url'

const router = useRouter()

const loading = ref(false)
const bannerList = ref([])
const posts = ref([])
const activities = ref([])
const notices = ref([])
const bulletins = ref([])
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
    const [bannerData, postData, activityData, noticeData, bulletinData] = await Promise.all([
      api.publicBanners(),
      api.posts({ page: 1, size: 6 }),
      api.publicActivities({ page: 1, size: 6 }),
      api.notices(),
      api.bulletins({ limit: 5 })
    ])
    bannerList.value = (bannerData || []).filter(item => item.status !== '1')
    posts.value = postData?.list || []
    activities.value = activityData?.list || []
    notices.value = noticeData || []
    bulletins.value = bulletinData || []
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
    <section class="card hero-card">
      <div>
        <p class="hero-sub">城市滑板社区</p>
        <h1>城市滑板交流网站</h1>
        <p class="hero-desc">发现本地板点、约滑同好和最新快讯。新手找组织，老手分享技巧，都能在这里快速连接。</p>
        <div class="inline">
          <button class="btn-primary" @click="$router.push('/community')">
            <AppIcon name="community" :size="16" />
            进入社区帖子
          </button>
          <button @click="$router.push('/activities')">
            <AppIcon name="activity" :size="16" />
            查看同城约板
          </button>
        </div>
      </div>
      <div class="hero-stats">
        <div class="metric">
          <span>精选帖子</span>
          <strong>{{ posts.length }}</strong>
        </div>
        <div class="metric">
          <span>推荐活动</span>
          <strong>{{ activities.length }}</strong>
        </div>
        <div class="metric">
          <span>平台公告</span>
          <strong>{{ notices.length }}</strong>
        </div>
        <div class="metric">
          <span>社区快讯</span>
          <strong>{{ bulletins.length }}</strong>
        </div>
      </div>
    </section>

    <section class="card carousel-card" @mouseenter="stopAutoplay" @mouseleave="startAutoplay">
      <div class="carousel-main" @click="goBannerTarget">
        <transition name="carousel-fade" mode="out-in">
          <img :key="currentSlide" :src="currentBanner.imageUrl" :alt="currentBanner.title" />
        </transition>
        <button class="carousel-nav nav-prev" type="button" aria-label="上一张" @click.stop="prevBanner">
          <
        </button>
        <button class="carousel-nav nav-next" type="button" aria-label="下一张" @click.stop="nextBanner">
          >
        </button>
        <div class="carousel-mask">
          <h3>{{ currentBanner.title }}</h3>
          <p v-if="currentBanner.linkUrl" class="muted">点击前往：{{ bannerLinkText(currentBanner.linkUrl) }}</p>
        </div>
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

    <section class="grid-3">
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

      <div class="card">
        <div class="section-head">
          <h3>社区快讯</h3>
          <button class="btn-soft" @click="$router.push('/bulletins')">更多</button>
        </div>
        <p v-if="loading" class="muted">加载中...</p>
        <div v-for="item in bulletins.slice(0, 5)" :key="item.bulletinId" class="activity-item">
          <h4 @click="$router.push(`/bulletins/${item.bulletinId}`)" class="link">{{ item.title }}</h4>
          <p class="muted">{{ item.createTime?.replace('T', ' ') }}</p>
          <p class="muted">{{ item.publisherName }} · {{ item.bulletinType }}</p>
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
  gap: 16px;
}
.grid-3 {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.hero-card {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  align-items: center;
  gap: 18px;
  border-color: var(--line-strong);
}

.hero-sub {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.hero-card h1 {
  margin: 0;
  font-size: 34px;
  line-height: 1.2;
}

.hero-desc {
  margin: 12px 0 16px;
  color: var(--text-soft);
}

.hero-stats {
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-md);
  background: linear-gradient(180deg, #ffffff 0%, #edf4ff 100%);
  padding: 16px;
  display: grid;
  gap: 10px;
  box-shadow: var(--shadow-1);
}

.metric {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  border-bottom: 1px dashed rgba(166, 255, 60, 0.55);
  padding-bottom: 10px;
}

.metric:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.metric span {
  color: var(--text-soft);
  font-size: 13px;
}

.metric strong {
  font-size: 22px;
}

.carousel-card {
  padding: 14px;
}

.carousel-main {
  position: relative;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-md);
  overflow: hidden;
  cursor: pointer;
  box-shadow: var(--shadow-2);
}

.carousel-main img {
  width: 100%;
  height: 320px;
  object-fit: cover;
  display: block;
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
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid var(--line-strong);
  color: var(--text-1);
  font-size: 20px;
  line-height: 1;
  display: grid;
  place-items: center;
  box-shadow: none;
  text-shadow: none;
  opacity: 0;
  transition: opacity var(--motion-base) var(--ease-ui);
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
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16px;
  color: var(--text-1);
  background: linear-gradient(to top, rgba(242, 248, 255, 0.92), rgba(242, 248, 255, 0.55));
}

.carousel-mask h3 {
  margin: 0;
}

.carousel-mask p {
  margin: 4px 0 0;
  color: var(--text-2);
}

.dots {
  position: absolute;
  left: 50%;
  bottom: 12px;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
  z-index: 4;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: var(--radius-md);
  border: 1px solid var(--line-strong);
  background: rgba(227, 238, 255, 0.84);
  transition: all 0.25s ease;
}

.dot.active {
  transform: scale(1.12);
  background: linear-gradient(90deg, #22c9ff 0%, #a6ff3c 100%);
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
  border-radius: var(--radius-md);
  border: 1px solid var(--line-strong);
  object-fit: cover;
}

.feed-item h4,
.activity-item h4,
.notice-item h4 {
  margin: 0 0 4px;
  color: var(--text-1);
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
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-md);
  padding: 12px;
  background: linear-gradient(180deg, #ffffff 0%, #edf4ff 100%);
}

.line-clamp {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

@media (max-width: 980px) {
  .hero-card {
    grid-template-columns: 1fr;
  }

  .grid-3 {
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

  .carousel-main img {
    height: 220px;
  }

  .hero-card h1 {
    font-size: 27px;
  }
}
</style>


