<template>
  <section class="user-profile-page">
    <div v-if="loading" class="profile-state card">正在加载用户主页…</div>

    <div v-else-if="error" class="profile-state card">
      <p class="eyebrow">USER SPACE</p>
      <h1>用户不存在</h1>
      <p>{{ error }}</p>
      <button class="btn-soft" type="button" @click="router.push('/community')">返回社区</button>
    </div>

    <template v-else>
      <section ref="heroEl" class="profile-identity" aria-labelledby="profile-name">
        <div class="identity-avatar-wrap">
          <img class="profile-avatar" :src="normalizeMediaUrl(profile.avatar) || defaultAvatar" alt="" @error="localImageFallback($event, defaultAvatar)" />
          <span class="online-mark" aria-label="在线"></span>
        </div>

        <div class="identity-copy">
          <div class="identity-name-row">
            <p class="eyebrow">USER SPACE</p>
            <span class="identity-id">ID {{ targetUserId }}</span>
          </div>
          <h1 id="profile-name">{{ profile.username || '匿名滑手' }}</h1>
          <p class="bio">{{ profile.bio || '这个滑手还没有填写简介。' }}</p>
          <div class="profile-meta" aria-label="用户标签">
            <span class="meta-chip level-chip">Lv{{ profile.level || 1 }}</span>
            <span class="meta-chip">{{ profile.skateStyle || '未设置风格' }}</span>
            <span class="meta-chip">{{ profile.gender || '未设置性别' }}</span>
          </div>
        </div>

        <div class="profile-actions" aria-label="用户操作">
          <template v-if="isSelf">
            <button class="message-button" type="button" @click="router.push('/profile')">
              进入个人中心
            </button>
            <button class="follow-button" type="button" @click="copyProfileLink">复制主页链接</button>
          </template>
          <template v-else>
            <button class="message-button" type="button" :disabled="actionLoading" @click="startConversation">私信</button>
            <button class="follow-button" type="button" :disabled="actionLoading" @click="toggleFollow">
              {{ followStatus?.following ? (followStatus?.mutual ? '互相关注' : '已关注') : '关注' }}
            </button>
          </template>
          <div class="more-actions">
            <button
              class="more-button"
              type="button"
              aria-label="更多操作"
              :aria-expanded="isMoreMenuOpen"
              @click="isMoreMenuOpen = !isMoreMenuOpen"
            >
              <AppIcon name="more" :size="20" />
            </button>
            <div v-if="isMoreMenuOpen" class="more-menu" role="menu">
              <button type="button" role="menuitem" @click="copyProfileLink">
                <AppIcon name="link" :size="17" />
                复制主页链接
              </button>
              <button type="button" role="menuitem" @click="scrollToPosts">
                <AppIcon name="posts" :size="17" />
                查看 TA 的帖子
              </button>
              <button v-if="isSelf" type="button" role="menuitem" @click="router.push('/profile')">
                <AppIcon name="user" :size="17" />
                编辑个人资料
              </button>
            </div>
          </div>
        </div>
      </section>

      <section class="stats-row" aria-label="用户数据">
        <button class="stat-card" type="button" @click="scrollToPosts">
          <AppIcon name="posts" :size="22" />
          <span>帖子</span>
          <strong>{{ profile.postCount || 0 }}</strong>
        </button>
        <div class="stat-card">
          <AppIcon name="users" :size="22" />
          <span>粉丝</span>
          <strong>{{ formatCount(profile.followerCount) }}</strong>
        </div>
        <div class="stat-card">
          <AppIcon name="user" :size="22" />
          <span>关注</span>
          <strong>{{ formatCount(profile.followingCount) }}</strong>
        </div>
      </section>

      <div class="profile-content-grid">
        <main ref="postsEl" class="feed-panel card">
          <div class="feed-heading">
            <div>
              <p class="eyebrow">RECENT POSTS</p>
              <h2>TA 的动态</h2>
            </div>
            <div class="feed-tabs" aria-label="帖子排序">
              <button :class="{ active: postOrder === 'latest' }" type="button" @click="postOrder = 'latest'">最新</button>
              <button :class="{ active: postOrder === 'popular' }" type="button" @click="postOrder = 'popular'">热门</button>
            </div>
          </div>

          <div v-if="postsLoading" class="profile-state">正在加载帖子…</div>
          <div v-else-if="postError" class="profile-state">{{ postError }}</div>
          <div v-else-if="!posts.length" class="profile-state">TA 还没有发布帖子。</div>

          <article
            v-for="post in sortedPosts"
            :key="post.postId"
            class="post-item"
            @click="router.push(`/community/post/${post.postId}`)"
          >
            <div class="post-topline">
              <span class="post-category">{{ post.category || '社区分享' }}</span>
              <time>{{ formatTime(post.createTime) }}</time>
            </div>
            <h3>{{ post.title }}</h3>
            <div class="post-metrics" aria-label="帖子互动数据">
              <span><AppIcon name="like" :size="16" />{{ post.likeCount || 0 }}</span>
              <span><AppIcon name="comment" :size="16" />{{ post.commentCount || 0 }}</span>
              <span><AppIcon name="collect" :size="16" />{{ post.collectCount || 0 }}</span>
              <AppIcon class="post-arrow" name="arrow-right" :size="18" />
            </div>
          </article>
        </main>

        <aside class="profile-rail">
          <section class="rail-card card">
            <div class="rail-heading">
              <h2>关系速览</h2>
              <span class="rail-dot">{{ profile.followerCount || 0 }}</span>
            </div>
            <p v-if="Number(profile.followerCount || 0) === 0">暂无粉丝</p>
            <p v-else>已有 <strong>{{ formatCount(profile.followerCount) }}</strong> 位滑手关注 TA</p>
          </section>

          <section class="rail-card card">
            <div class="rail-heading">
              <h2>兴趣标签</h2>
              <AppIcon name="target" :size="19" />
            </div>
            <div class="interest-tags">
              <span>{{ profile.skateStyle || '街式滑板' }}</span>
              <span>Lv{{ profile.level || 1 }} 成长中</span>
              <span v-if="profile.gender">{{ profile.gender }}</span>
              <span>社区创作</span>
            </div>
          </section>

          <section v-if="profile.creatorSummary?.hasCreatorContent" class="rail-card creator-card card">
            <div class="rail-heading">
              <h2>创作档案</h2>
              <span class="creator-score">{{ profile.creatorSummary.qualityScore || 0 }}</span>
            </div>
            <div class="creator-stats">
              <div><strong>{{ profile.creatorSummary.videoCount || 0 }}</strong><span>视频</span></div>
              <div><strong>{{ profile.creatorSummary.tutorialPostCount || 0 }}</strong><span>教程</span></div>
              <div><strong>{{ profile.creatorSummary.totalEngagement || 0 }}</strong><span>互动</span></div>
            </div>
            <button class="rail-link" type="button" @click="scrollToPosts">查看作品 <AppIcon name="arrow-right" :size="16" /></button>
          </section>
        </aside>
      </div>

      <Transition name="mobile-actions">
        <div v-if="showMobileActions && !isSelf" class="mobile-profile-actions" aria-label="用户快捷操作">
          <button class="message-button" type="button" :disabled="actionLoading" @click="startConversation">私信</button>
          <button class="follow-button" type="button" :disabled="actionLoading" @click="toggleFollow">
            {{ followStatus?.following ? '已关注' : '关注' }}
          </button>
          <button class="more-button" type="button" aria-label="更多操作" @click="isMoreMenuOpen = !isMoreMenuOpen">
            <AppIcon name="more" :size="20" />
          </button>
        </div>
      </Transition>
    </template>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { getToken } from '../utils/auth'
import { localImageFallback } from '../utils/imageFallback'
import { normalizeMediaUrl } from '../utils/url'

const route = useRoute()
const router = useRouter()
const defaultAvatar = '/fallback/avatar.svg'

const profile = ref({})
const currentUser = ref(null)
const posts = ref([])
const total = ref(0)
const followStatus = ref(null)
const loading = ref(true)
const postsLoading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const postError = ref('')
const postOrder = ref('latest')
const isMoreMenuOpen = ref(false)
const showMobileActions = ref(false)
const heroEl = ref(null)
const postsEl = ref(null)
let heroObserver = null

const targetUserId = computed(() => Number(route.params.id))
const isLoggedIn = computed(() => Boolean(getToken()))
const isSelf = computed(() => Number(currentUser.value?.userId || 0) === targetUserId.value && targetUserId.value > 0)
const sortedPosts = computed(() => {
  const list = [...posts.value]
  if (postOrder.value === 'popular') {
    return list.sort((a, b) => Number(b.likeCount || 0) + Number(b.commentCount || 0) - Number(a.likeCount || 0) - Number(a.commentCount || 0))
  }
  return list
})

function requireLogin() {
  if (isLoggedIn.value) return true
  router.push('/login')
  return false
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

function formatCount(value) {
  const count = Number(value || 0)
  return count >= 10000 ? `${(count / 10000).toFixed(count >= 100000 ? 0 : 1)}万` : count
}

function scrollToPosts() {
  isMoreMenuOpen.value = false
  postsEl.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function copyProfileLink() {
  isMoreMenuOpen.value = false
  const link = window.location.href
  try {
    await navigator.clipboard.writeText(link)
    alert('主页链接已复制')
  } catch (_) {
    window.prompt('复制主页链接', link)
  }
}

async function loadCurrentUser() {
  currentUser.value = null
  if (!isLoggedIn.value) return
  try {
    currentUser.value = await api.profile()
  } catch (_) {
    currentUser.value = null
  }
}

async function loadProfile() {
  loading.value = true
  error.value = ''
  try {
    await loadCurrentUser()
    profile.value = await api.publicUserProfile(targetUserId.value)
    followStatus.value = isLoggedIn.value && !isSelf.value ? await api.followStatus(targetUserId.value) : null
  } catch (e) {
    error.value = e?.message || '用户加载失败'
  } finally {
    loading.value = false
  }
}

async function loadPosts() {
  postsLoading.value = true
  postError.value = ''
  try {
    const result = await api.publicUserPosts(targetUserId.value, { page: 1, size: 20 })
    posts.value = result?.list || []
    total.value = result?.total || posts.value.length
  } catch (e) {
    posts.value = []
    total.value = 0
    postError.value = e?.message || '帖子加载失败'
  } finally {
    postsLoading.value = false
  }
}

async function toggleFollow() {
  if (!requireLogin() || isSelf.value) return
  actionLoading.value = true
  try {
    followStatus.value = followStatus.value?.following
      ? await api.unfollowUser(targetUserId.value)
      : await api.followUser(targetUserId.value)
    await loadProfile()
  } catch (e) {
    alert(e?.message || '关注操作失败')
  } finally {
    actionLoading.value = false
  }
}

async function startConversation() {
  if (!requireLogin() || isSelf.value) return
  actionLoading.value = true
  try {
    const conversation = await api.createConversation({ targetUserId: targetUserId.value })
    const conversationId = conversation?.conversationId
    router.push(conversationId ? `/messages/${conversationId}` : '/messages')
  } catch (e) {
    alert(e?.message || '私信会话创建失败')
  } finally {
    actionLoading.value = false
  }
}

function observeHero() {
  heroObserver?.disconnect()
  if (!window.IntersectionObserver || !heroEl.value) return
  heroObserver = new IntersectionObserver(([entry]) => {
    showMobileActions.value = !entry.isIntersecting
  }, { threshold: 0.22 })
  heroObserver.observe(heroEl.value)
}

async function loadAll() {
  isMoreMenuOpen.value = false
  showMobileActions.value = false
  await loadProfile()
  if (!error.value) await loadPosts()
  requestAnimationFrame(observeHero)
}

onMounted(loadAll)
onBeforeUnmount(() => heroObserver?.disconnect())
watch(() => route.params.id, loadAll)
</script>

<style scoped>
.user-profile-page { max-width: 1060px; margin: 0 auto; padding: 38px 18px 74px; }
.profile-state { padding: 32px; color: var(--text-muted); text-align: center; }
.profile-state h1 { margin: 0; color: var(--text); }
.profile-identity { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 26px; align-items: center; padding: 10px 0 28px; border-bottom: 1px solid var(--line); }
.identity-avatar-wrap { position: relative; width: 116px; height: 116px; }
.profile-avatar { width: 100%; height: 100%; border: 4px solid #fff; border-radius: 50%; object-fit: cover; background: var(--surface); box-shadow: 0 10px 28px rgba(18, 21, 19, .13); }
.online-mark { position: absolute; right: 5px; bottom: 8px; width: 17px; height: 17px; border: 3px solid var(--bg-soft); border-radius: 50%; background: var(--accent-warm); }
.identity-name-row { display: flex; align-items: center; gap: 10px; }
.eyebrow { margin: 0; color: var(--text-muted); font-size: 11px; font-weight: 800; letter-spacing: .12em; }
.identity-id { color: var(--text-muted); font-size: 12px; }
.identity-copy h1, .feed-heading h2, .rail-heading h2 { margin: 0; color: var(--text); }
.identity-copy h1 { font-size: clamp(30px, 4vw, 40px); letter-spacing: -.045em; }
.bio { max-width: 590px; margin: 7px 0 12px; color: var(--text-soft); line-height: 1.65; }
.profile-meta { display: flex; flex-wrap: wrap; gap: 7px; }
.meta-chip { padding: 4px 10px; border: 1px solid var(--line); border-radius: 999px; background: rgba(255,255,255,.6); color: var(--text-soft); font-size: 12px; font-weight: 700; }
.level-chip { border-color: rgba(249,115,22,.25); color: #bc5b13; background: rgba(249,115,22,.08); }
.profile-actions { display: flex; align-items: center; gap: 9px; }
.message-button, .follow-button { min-width: 92px; min-height: 44px; padding: 10px 16px; border-radius: 999px; font-weight: 800; }
.message-button { border-color: #087a6d; background: #087a6d; color: #fff; box-shadow: 0 8px 18px rgba(8,122,109,.22); }
.message-button:hover:not(:disabled) { background: #066b60; border-color: #066b60; }
.follow-button { border-color: rgba(8,122,109,.76); background: rgba(255,255,255,.66); color: #087a6d; }
.more-actions { position: relative; }
.more-button { display: inline-grid; place-items: center; width: 44px; height: 44px; padding: 0; border-color: rgba(8,122,109,.64); border-radius: 50%; background: rgba(255,255,255,.7); color: #087a6d; }
.more-menu { position: absolute; z-index: 8; top: calc(100% + 10px); right: 0; display: grid; min-width: 184px; padding: 6px; border: 1px solid var(--line); border-radius: 14px; background: rgba(255,255,255,.98); box-shadow: var(--shadow-md); }
.more-menu button { display: flex; align-items: center; gap: 9px; padding: 9px 10px; border: 0; border-radius: 9px; background: transparent; box-shadow: none; color: var(--text-soft); text-align: left; font-size: 13px; }
.more-menu button:hover { background: var(--surface-muted); color: var(--text); transform: none; }
.stats-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 20px 0; }
.stat-card { display: grid; grid-template-columns: auto 1fr; column-gap: 10px; align-items: center; min-height: 84px; padding: 14px 16px; border: 1px solid var(--line); border-radius: 14px; background: rgba(255,255,255,.74); color: var(--text-muted); box-shadow: var(--shadow-sm); text-align: left; }
.stat-card > svg { grid-row: span 2; color: var(--accent); }
.stat-card strong { color: var(--text); font-size: 22px; line-height: 1; }
.profile-content-grid { display: grid; grid-template-columns: minmax(0, 1fr) 278px; gap: 16px; align-items: start; }
.feed-panel { padding: 0; overflow: hidden; }
.feed-heading { display: flex; align-items: center; justify-content: space-between; gap: 15px; padding: 20px 22px 14px; border-bottom: 1px solid var(--line); }
.feed-heading h2 { font-size: 21px; }
.feed-tabs { display: flex; gap: 4px; padding: 3px; border-radius: 999px; background: var(--surface-muted); }
.feed-tabs button { min-width: 48px; padding: 5px 9px; border: 0; border-radius: 999px; background: transparent; box-shadow: none; color: var(--text-muted); font-size: 12px; font-weight: 800; }
.feed-tabs button.active { background: #fff; color: var(--accent); box-shadow: 0 2px 7px rgba(18,21,19,.08); }
.post-item { padding: 19px 22px; border-bottom: 1px solid var(--line); cursor: pointer; transition: background .2s ease; }
.post-item:last-child { border-bottom: 0; }
.post-item:hover { background: rgba(246,247,244,.72); }
.post-topline { display: flex; justify-content: space-between; gap: 12px; color: var(--text-muted); font-size: 12px; }
.post-category { color: var(--accent); font-weight: 800; }
.post-item h3 { margin: 8px 0 13px; color: var(--text); font-size: 17px; }
.post-metrics { display: flex; align-items: center; gap: 17px; color: var(--text-muted); font-size: 13px; }
.post-metrics span { display: inline-flex; align-items: center; gap: 5px; }
.post-arrow { margin-left: auto; color: var(--accent); }
.profile-rail { display: grid; gap: 14px; }
.rail-card { padding: 18px; }
.rail-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.rail-heading h2 { font-size: 16px; }
.rail-dot, .creator-score { min-width: 25px; padding: 2px 7px; border-radius: 999px; background: rgba(8,122,109,.1); color: var(--accent); font-size: 12px; font-weight: 800; text-align: center; }
.rail-card p { margin: 10px 0 13px; color: var(--text-muted); font-size: 13px; }
.rail-card p strong { color: var(--text); }
.avatar-stack { display: flex; align-items: center; }
.avatar-stack img, .stack-count { width: 34px; height: 34px; border: 2px solid #fff; border-radius: 50%; }
.avatar-stack img { object-fit: cover; }
.stack-count { display: grid; place-items: center; margin-left: -8px; background: var(--primary); color: #fff; font-size: 10px; font-weight: 800; }
.interest-tags { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 14px; }
.interest-tags span { padding: 5px 8px; border: 1px solid var(--line); border-radius: 999px; color: var(--text-soft); font-size: 12px; }
.creator-card { border-color: rgba(249,115,22,.22); }
.creator-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin: 15px 0; }
.creator-stats div { display: grid; gap: 1px; }
.creator-stats strong { color: var(--text); font-size: 17px; }
.creator-stats span { color: var(--text-muted); font-size: 11px; }
.rail-link { display: inline-flex; align-items: center; gap: 5px; padding: 0; border: 0; background: transparent; box-shadow: none; color: var(--accent); font-size: 13px; font-weight: 800; }
.rail-link:hover { transform: none; box-shadow: none; }
.mobile-profile-actions { display: none; }
.mobile-actions-enter-active, .mobile-actions-leave-active { transition: opacity .2s ease, transform .2s var(--motion-ease); }
.mobile-actions-enter-from, .mobile-actions-leave-to { opacity: 0; transform: translateY(12px); }
@media (max-width: 760px) {
  .user-profile-page { padding: 22px 12px 154px; }
  .profile-identity { grid-template-columns: 1fr; justify-items: center; gap: 12px; padding: 4px 0 22px; text-align: center; }
  .identity-avatar-wrap { width: 98px; height: 98px; }
  .identity-name-row, .profile-meta { justify-content: center; }
  .bio { margin-right: auto; margin-left: auto; }
  .profile-actions { display: none; }
  .stats-row { gap: 8px; margin: 14px 0; }
  .stat-card { min-height: 70px; padding: 10px; column-gap: 6px; }
  .stat-card > svg { display: none; }
  .stat-card span { font-size: 12px; }
  .stat-card strong { font-size: 18px; }
  .profile-content-grid { grid-template-columns: 1fr; }
  .profile-rail { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .creator-card { grid-column: 1 / -1; }
  .feed-heading, .post-item { padding-right: 15px; padding-left: 15px; }
  .mobile-profile-actions { position: fixed; z-index: 35; right: 14px; bottom: calc(82px + env(safe-area-inset-bottom)); left: 14px; display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, .8fr) 44px; gap: 8px; padding: 8px; border: 1px solid rgba(255,255,255,.7); border-radius: 18px; background: rgba(251,251,248,.94); box-shadow: 0 14px 34px rgba(18,21,19,.17); backdrop-filter: blur(12px); }
  .mobile-profile-actions .message-button, .mobile-profile-actions .follow-button { min-width: 0; }
}
@media (max-width: 420px) { .profile-rail { grid-template-columns: 1fr; } .creator-card { grid-column: auto; } .post-topline time { max-width: 130px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; } }
@media (prefers-reduced-motion: reduce) { .mobile-actions-enter-active, .mobile-actions-leave-active { transition: none; } }
</style>
