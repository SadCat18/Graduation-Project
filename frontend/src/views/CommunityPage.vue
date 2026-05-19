<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { getToken } from '../utils/auth'
import { normalizeMediaUrl } from '../utils/url'

const router = useRouter()

const posts = ref([])
const page = ref(1)
const pageSize = 6
const total = ref(0)
const notices = ref([])
const newsList = ref([])
const loading = ref(false)
const error = ref('')
const commentInputs = reactive({})
const commentMap = reactive({})
const isLoggedIn = computed(() => !!getToken())
const totalPages = computed(() => Math.max(1, Math.ceil((total.value || 0) / pageSize)))

function goLogin(message = '请先登录后再进行互动操作') {
  alert(message)
  router.push('/login')
}

function parseImages(images) {
  if (!images) return []
  return String(images)
    .split(',')
    .map(item => item.trim())
    .map(item => normalizeMediaUrl(item))
    .filter(Boolean)
}

function goPostDetail(postId) {
  router.push(`/community/post/${postId}`)
}

async function loadPosts() {
  loading.value = true
  error.value = ''
  try {
    const res = await api.posts({ page: page.value, size: pageSize })
    total.value = Number(res.total || 0)
    posts.value = res.list || []
    if (page.value > totalPages.value) {
      page.value = totalPages.value
      await loadPosts()
      return
    }
  } catch (e) {
    error.value = e.message || '帖子加载失败'
  } finally {
    loading.value = false
  }
}

async function changePage(nextPage) {
  if (nextPage < 1 || nextPage > totalPages.value || nextPage === page.value) return
  page.value = nextPage
  await loadPosts()
}

async function loadSideData() {
  notices.value = await api.notices()
  newsList.value = await api.news()
}

async function deletePost(postId) {
  if (!getToken()) return goLogin()
  await api.deletePost(postId)
  await loadPosts()
}

async function toggleLike(postId) {
  if (!getToken()) return goLogin()
  await api.likePost(postId)
  await loadPosts()
}

async function toggleCollect(postId) {
  if (!getToken()) return goLogin()
  await api.collectPost(postId)
  await loadPosts()
}

async function loadComments(postId) {
  commentMap[postId] = await api.comments(postId)
}

async function sendComment(postId) {
  if (!getToken()) return goLogin()
  if (!commentInputs[postId]) return
  await api.createComment(postId, { content: commentInputs[postId], parentId: 0 })
  commentInputs[postId] = ''
  await loadComments(postId)
  await loadPosts()
}

onMounted(async () => {
  await loadPosts()
  await loadSideData()
})
</script>

<template>
  <div class="page-grid community-page">
    <section>
      <div class="card community-hero">
        <div>
          <p class="hero-sub">社区内容广场</p>
          <h2>社区帖子广场</h2>
          <p class="muted">先看内容，再决定发帖。帖子浏览优先，发布独立跳转。</p>
        </div>
        <button class="btn-primary" @click="isLoggedIn ? $router.push('/community/publish') : $router.push('/login')">
          <AppIcon name="plus" :size="15" />
          发布新帖子
        </button>
      </div>

      <div class="card">
        <div class="section-head">
          <h3>最新帖子</h3>
          <button class="btn-soft" @click="loadPosts">
            <AppIcon name="refresh" :size="15" />
            刷新
          </button>
        </div>

        <p v-if="loading" class="muted">加载中...</p>
        <p v-if="error" class="error">{{ error }}</p>
        <p v-if="!loading && !posts.length" class="muted">暂无帖子</p>

        <div v-for="item in posts" :key="item.postId" class="post-item post-card-item">
          <div class="post-header">
            <h4 class="clickable-title" @click="goPostDetail(item.postId)">{{ item.title }}</h4>
            <span class="tag">{{ item.category || '未分类' }}</span>
          </div>
          <p class="muted">作者：{{ item.authorName }} · {{ item.createTime?.replace('T', ' ') }}</p>
          <p class="post-content">{{ item.content }}</p>

          <div v-if="parseImages(item.images).length" class="post-images">
            <img
              v-for="img in parseImages(item.images)"
              :key="img"
              :src="img"
              alt="帖子图片"
              class="clickable-image"
              @click="goPostDetail(item.postId)"
            />
          </div>

          <div class="inline actions-row">
            <button class="btn-soft" @click="toggleLike(item.postId)">
              <AppIcon name="like" :size="14" />
              点赞 {{ item.likeCount }}
            </button>
            <button class="btn-soft" @click="toggleCollect(item.postId)">
              <AppIcon name="collect" :size="14" />
              收藏 {{ item.collectCount }}
            </button>
            <button class="btn-soft" @click="loadComments(item.postId)">
              <AppIcon name="comment" :size="14" />
              评论 {{ item.commentCount }}
            </button>
            <button class="btn-soft" @click="goPostDetail(item.postId)">查看详情</button>
            <button v-if="isLoggedIn" class="btn-danger" @click="deletePost(item.postId)">删除</button>
          </div>

          <div v-if="commentMap[item.postId]" class="comment-block">
            <div v-for="c in commentMap[item.postId]" :key="c.commentId" class="comment-item">
              <strong>{{ c.username }}：</strong>{{ c.content }}
            </div>
          </div>

          <div v-if="isLoggedIn" class="inline comment-input-row">
            <input v-model="commentInputs[item.postId]" placeholder="写评论..." />
            <button class="btn-primary" @click="sendComment(item.postId)">发送</button>
          </div>
          <div v-else class="inline">
            <button @click="$router.push('/login')">登录后评论</button>
          </div>
        </div>

        <div class="pagination-bar">
          <button class="btn-soft" :disabled="page <= 1 || loading" @click="changePage(page - 1)">上一页</button>
          <span class="muted">第 {{ page }} / {{ totalPages }} 页（共 {{ total }} 条）</span>
          <button class="btn-soft" :disabled="page >= totalPages || loading" @click="changePage(page + 1)">下一页</button>
        </div>
      </div>
    </section>

    <aside>
      <div class="card">
        <div class="section-head"><h3>平台公告</h3></div>
        <div v-for="n in notices.slice(0, 6)" :key="n.noticeId" class="list-item">
          <strong>{{ n.title }}</strong>
          <p class="muted">{{ n.content }}</p>
        </div>
      </div>
      <div class="card">
        <div class="section-head"><h3>滑板资讯</h3></div>
        <div v-for="n in newsList.slice(0, 8)" :key="n.newsId" class="list-item">
          <strong>{{ n.title }}</strong>
          <p class="muted">{{ n.category || '未分类' }}</p>
        </div>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.community-page {
  align-items: start;
}

.community-hero {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: center;
  border: 1px solid var(--line-strong);
}

.hero-sub {
  margin: 0;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--text-muted);
}

.community-hero h2 {
  margin: 6px 0;
}

.post-card-item {
  display: grid;
  gap: 10px;
}

.post-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.post-header h4 {
  margin: 0;
}

.clickable-title {
  cursor: pointer;
}

.clickable-title:hover {
  text-decoration: underline;
}

.post-content {
  margin: 0;
  white-space: pre-wrap;
}

.post-images {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 8px;
}

.post-images img {
  width: 100%;
  height: 96px;
  object-fit: cover;
  border-radius: var(--radius-md);
  border: 1px solid var(--line-strong);
}

.clickable-image {
  cursor: pointer;
}

.actions-row {
  margin-top: 2px;
}

.comment-block {
  border: 1px solid var(--line-strong);
  background: linear-gradient(180deg, #ffffff 0%, #edf4ff 100%);
  border-radius: var(--radius-md);
  padding: 10px 12px;
}

.comment-item {
  border-bottom: 1px dashed #9db7da;
  padding: 7px 0;
  font-size: 14px;
}

.post-card-item {
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-md);
  padding: 12px;
  background: linear-gradient(180deg, #ffffff 0%, #edf4ff 100%);
}

.comment-item:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.comment-input-row {
  align-items: stretch;
}

.comment-input-row input {
  flex: 1;
}

.pagination-bar {
  margin-top: 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

@media (max-width: 768px) {
  .community-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .pagination-bar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>


