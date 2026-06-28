<template>
  <section class="user-profile-page">
    <div v-if="loading" class="panel-card loading-card">正在加载用户空间...</div>

    <div v-else-if="error" class="panel-card empty-state">
      <p class="eyebrow">USER SPACE</p>
      <h1>用户不存在</h1>
      <p>{{ error }}</p>
      <button class="btn-soft" type="button" @click="router.push('/community')">返回社区</button>
    </div>

    <template v-else>
      <section class="profile-hero">
        <div class="hero-mark" aria-hidden="true">SKATEHUB</div>
        <img class="profile-avatar" :src="normalizeMediaUrl(profile.avatar) || defaultAvatar" alt="" />
        <div class="profile-main">
          <p class="eyebrow">USER SPACE</p>
          <h1>{{ profile.username || '匿名滑手' }}</h1>
          <p class="bio">{{ profile.bio || '这个滑手还没有填写简介。' }}</p>
          <div class="profile-meta">
            <span>Lv{{ profile.level || 1 }}</span>
            <span>{{ profile.skateStyle || '未设置风格' }}</span>
            <span>{{ profile.gender || '未设置性别' }}</span>
          </div>
        </div>
        <div class="profile-actions">
          <button v-if="isSelf" class="btn-primary" type="button" @click="router.push('/profile')">进入个人中心</button>
          <template v-else>
            <button class="btn-soft" type="button" :disabled="actionLoading" @click="toggleFollow">
              {{ followStatus?.following ? (followStatus?.mutual ? '互相关注' : '已关注') : '关注' }}
            </button>
            <button class="btn-primary" type="button" :disabled="actionLoading" @click="startConversation">私信</button>
          </template>
        </div>
      </section>

      <section class="stats-row">
        <div><strong>{{ profile.postCount || 0 }}</strong><span>帖子</span></div>
        <div><strong>{{ profile.followerCount || 0 }}</strong><span>粉丝</span></div>
        <div><strong>{{ profile.followingCount || 0 }}</strong><span>关注</span></div>
      </section>

      <section class="post-section">
        <div class="section-heading">
          <div>
            <p class="eyebrow">POSTS</p>
            <h2>TA 的帖子</h2>
          </div>
          <span>{{ posts.length }} / {{ total }}</span>
        </div>

        <div v-if="postsLoading" class="panel-card loading-card">正在加载帖子...</div>
        <div v-else-if="postError" class="panel-card empty-state">{{ postError }}</div>
        <div v-else-if="!posts.length" class="panel-card empty-state">TA 还没有发布帖子。</div>

        <article
          v-for="post in posts"
          :key="post.postId"
          class="post-item"
          @click="router.push(`/community/post/${post.postId}`)"
        >
          <div>
            <h3>{{ post.title }}</h3>
            <p>{{ post.category || '未分类' }} · {{ formatTime(post.createTime) }}</p>
          </div>
          <div class="post-metrics">
            <span>{{ post.likeCount || 0 }} 赞</span>
            <span>{{ post.commentCount || 0 }} 评</span>
            <span>{{ post.collectCount || 0 }} 藏</span>
          </div>
        </article>
      </section>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { getToken } from '../utils/auth'
import { normalizeMediaUrl } from '../utils/url'

const route = useRoute()
const router = useRouter()
const defaultAvatar = 'https://api.dicebear.com/7.x/thumbs/svg?seed=skatehub'

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

const targetUserId = computed(() => Number(route.params.id))
const isLoggedIn = computed(() => Boolean(getToken()))
const isSelf = computed(() => {
  const currentUserId = Number(currentUser.value?.userId || 0)
  return Boolean(currentUserId && currentUserId === targetUserId.value)
})

function requireLogin() {
  if (isLoggedIn.value) return true
  router.push('/login')
  return false
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
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
    if (isLoggedIn.value && !isSelf.value) {
      followStatus.value = await api.followStatus(targetUserId.value)
    } else {
      followStatus.value = null
    }
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

async function loadAll() {
  await loadProfile()
  if (!error.value) await loadPosts()
}

onMounted(loadAll)
watch(() => route.params.id, loadAll)
</script>

<style scoped>
.user-profile-page {
  max-width: 1120px;
  margin: 0 auto;
  padding: 28px 18px 56px;
}

.profile-hero {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 22px;
  align-items: center;
  overflow: hidden;
  padding: 26px;
  border: 1px solid rgba(15, 23, 42, .12);
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, .95), rgba(248, 250, 252, .9)),
    repeating-linear-gradient(135deg, rgba(17, 24, 39, .06) 0 1px, transparent 1px 12px);
  box-shadow: 0 18px 45px rgba(15, 23, 42, .08);
}

.hero-mark {
  position: absolute;
  right: 20px;
  bottom: 12px;
  color: rgba(15, 23, 42, .05);
  font-size: clamp(42px, 9vw, 118px);
  font-weight: 900;
  line-height: 1;
  pointer-events: none;
}

.profile-avatar {
  position: relative;
  z-index: 1;
  width: 96px;
  height: 96px;
  border: 3px solid #111827;
  border-radius: 50%;
  object-fit: cover;
  background: #fff;
}

.profile-main,
.profile-actions {
  position: relative;
  z-index: 1;
}

.eyebrow {
  margin: 0 0 6px;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: .08em;
  text-transform: uppercase;
}

.profile-main h1,
.section-heading h2 {
  margin: 0;
  color: #111827;
}

.profile-main h1 {
  font-size: 30px;
  line-height: 1.2;
}

.bio {
  max-width: 620px;
  margin: 10px 0;
  color: #475569;
  line-height: 1.7;
}

.profile-meta,
.post-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: #64748b;
}

.profile-meta span,
.post-metrics span {
  padding: 4px 8px;
  border: 1px solid rgba(15, 23, 42, .1);
  border-radius: 999px;
  background: rgba(255, 255, 255, .74);
}

.profile-actions {
  display: flex;
  gap: 10px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin: 16px 0 22px;
}

.stats-row div,
.panel-card,
.post-item {
  border: 1px solid rgba(15, 23, 42, .1);
  border-radius: 8px;
  background: #fff;
}

.stats-row div {
  padding: 16px;
}

.stats-row strong {
  display: block;
  color: #111827;
  font-size: 24px;
}

.stats-row span {
  color: #64748b;
}

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 14px;
}

.post-item {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 18px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: transform .18s ease, box-shadow .18s ease;
}

.post-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 30px rgba(15, 23, 42, .08);
}

.post-item h3 {
  margin: 0 0 8px;
  color: #111827;
}

.post-item p {
  margin: 0;
  color: #64748b;
}

.panel-card {
  padding: 22px;
}

.loading-card,
.empty-state {
  text-align: center;
  color: #64748b;
}

.btn-primary,
.btn-soft {
  min-width: 86px;
  padding: 10px 14px;
  border: 1px solid #111827;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 800;
}

.btn-primary {
  background: #111827;
  color: #fff;
}

.btn-soft {
  background: #fff;
  color: #111827;
}

.btn-primary:disabled,
.btn-soft:disabled {
  cursor: not-allowed;
  opacity: .55;
}

@media (max-width: 720px) {
  .profile-hero {
    grid-template-columns: 1fr;
    text-align: center;
  }

  .profile-avatar {
    margin: 0 auto;
  }

  .profile-actions,
  .profile-meta,
  .post-metrics {
    justify-content: center;
  }

  .stats-row {
    grid-template-columns: 1fr;
  }

  .post-item {
    flex-direction: column;
  }
}
</style>
