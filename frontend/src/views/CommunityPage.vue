<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import SkateboardSketch from '../components/SkateboardSketch.vue'
import { api } from '../api'
import { usePublicCategories } from '../utils/publicCategories'
import { getAuthChangedEventName, getRole, getToken } from '../utils/auth'
import { loadCachedPublic } from '../utils/requestCache'
import { parseImages } from '../utils/url'

defineOptions({ name: 'CommunityPage' })

const router = useRouter()

const posts = ref([])
const page = ref(1)
const pageSize = 6
const total = ref(0)
const notices = ref([])
const newsList = ref([])
const loading = ref(false)
const error = ref('')
let postsSequence = 0, disposed = false
function invalidatePostsRead() { postsSequence++; loading.value = false }
window.addEventListener(getAuthChangedEventName(), invalidatePostsRead)
onBeforeUnmount(() => {
  disposed = true
  invalidatePostsRead()
  window.removeEventListener(getAuthChangedEventName(), invalidatePostsRead)
})
const commentInputs = reactive({})
const commentMap = reactive({})
const postUiState = reactive({})
const keyword = ref('')
const selectedCategory = ref('')
const selectedSort = ref('latest')
const { options: categoryOptions, loading: categoriesLoading, error: categoriesError, load: loadCategories } = usePublicCategories('filter')
const sortOptions = [
  { label: '最新', value: 'latest' },
  { label: '热门', value: 'hot' },
  { label: '推荐', value: 'recommended' },
  { label: '高赞', value: 'likes' },
  { label: '热评', value: 'comments' }
]
const isLoggedIn = computed(() => !!getToken())
const totalPages = computed(() => Math.max(1, Math.ceil((total.value || 0) / pageSize)))
const listSummary = computed(() => {
  const categoryText = selectedCategory.value === '' ? '全部分类' : selectedCategory.value
  const sortText = sortOptions.find(item => item.value === selectedSort.value)?.label || '最新'
  return `${categoryText} · ${sortText}排序 · 共 ${total.value || 0} 条`
})

function goLogin(message = '请先登录后再进行互动操作') {
  alert(message)
  router.push('/login')
}

function showActionError(e, fallback = '操作失败，请稍后重试') {
  alert(e?.message || fallback)
}


function goPostDetail(postId) {
  router.push(`/community/post/${postId}`)
}

function goNewsDetail(newsId) {
  if (!newsId) return
  router.push(`/news/${newsId}`)
}


function postState(postId) {
  if (!postUiState[postId]) postUiState[postId] = { liked: false, collected: false, watchedLater: false }
  return postUiState[postId]
}

function postImages(item) {
  return parseImages(item?.images).slice(0, 3)
}

async function selectCategory(category) {
  if (selectedCategory.value === category) return
  selectedCategory.value = category
}

function syncPostUiState(list) {
  ;(list || []).forEach((item) => {
    postState(item.postId)
  })
}

function updatePostLocally(postId, updater) {
  posts.value = posts.value.map((item) => {
    if (item.postId !== postId) return item
    return updater(item)
  })
}

async function loadPosts() {
  if (disposed) return
  const sequence = ++postsSequence
  const context = Object.freeze({ category: selectedCategory.value, sort: selectedSort.value, keyword: keyword.value.trim(), page: page.value, token: getToken(), role: getRole() })
  const current = () => !disposed && sequence === postsSequence && context.token === getToken() && context.role === getRole()
  loading.value = true
  error.value = ''
  try {
    let nextPosts, nextTotal
    if (context.sort === 'recommended' && context.token) {
      const rec = await api.recommendations()
      if (!current()) return
      let list = rec?.posts || []
      if (context.keyword) {
        const kw = context.keyword.toLowerCase()
        list = list.filter(item => String(item.title || '').toLowerCase().includes(kw) || String(item.content || '').toLowerCase().includes(kw))
      }
      if (context.category) list = list.filter(item => item.category === context.category)
      nextTotal = list.length
      const start = (context.page - 1) * pageSize
      nextPosts = list.slice(start, start + pageSize)
    } else {
      const res = await api.posts({ page: context.page, size: pageSize, keyword: context.keyword, category: context.category, sort: context.sort === 'recommended' ? 'hot' : context.sort })
      if (!current()) return
      nextTotal = Number(res.total || 0)
      nextPosts = res.list || []
    }
    if (!current()) return
    total.value = nextTotal
    posts.value = nextPosts
    syncPostUiState(nextPosts)
    if (context.page > totalPages.value) {
      page.value = totalPages.value
      await loadPosts()
    }
  } catch (e) {
    if (current()) error.value = e?.message || '帖子加载失败'
  } finally {
    if (current()) loading.value = false
  }
}

async function applyKeywordSearch() {
  page.value = 1
  await loadPosts()
}

async function changePage(nextPage) {
  if (nextPage < 1 || nextPage > totalPages.value || nextPage === page.value) return
  page.value = nextPage
  await loadPosts()
}

async function loadSideData() {
  const [noticeResult, newsResult] = await Promise.allSettled([
    loadCachedPublic('public:notices', () => api.notices()),
    loadCachedPublic('public:news', () => api.news())
  ])
  notices.value = noticeResult.status === 'fulfilled' ? (noticeResult.value || []) : []
  newsList.value = newsResult.status === 'fulfilled' ? (newsResult.value || []) : []
}

async function deletePost(postId) {
  if (!getToken()) return goLogin()
  try {
    await api.deletePost(postId)
    posts.value = posts.value.filter(item => item.postId !== postId)
    total.value = Math.max(0, total.value - 1)
  } catch (e) {
    showActionError(e, '删除失败，请稍后重试')
  }
}

async function toggleLike(postId) {
  if (!getToken()) return goLogin()
  try {
    const result = await api.likePost(postId)
    postState(postId).liked = !!result?.active
    updatePostLocally(postId, (item) => ({
      ...item,
      likeCount: Number(result?.likeCount ?? item.likeCount ?? 0)
    }))
  } catch (e) {
    showActionError(e, '收藏失败，请稍后重试')
  }
}

async function toggleCollect(postId) {
  if (!getToken()) return goLogin()
  try {
    const result = await api.collectPost(postId)
    postState(postId).collected = !!result?.active
    updatePostLocally(postId, (item) => ({
      ...item,
      collectCount: Number(result?.collectCount ?? item.collectCount ?? 0)
    }))
  } catch (e) {
    showActionError(e, '操作失败，请稍后重试')
  }
}

async function toggleWatchLater(postId) {
  if (!getToken()) return goLogin()
  try {
    const result = await api.watchLaterPost(postId)
    postState(postId).watchedLater = !!result?.active
  } catch (e) {
    showActionError(e, '操作失败，请稍后重试')
  }
}

async function loadComments(postId) {
  try {
    commentMap[postId] = await api.comments(postId)
  } catch (e) {
    showActionError(e, '评论加载失败，请稍后重试')
  }
}

async function toggleComments(postId) {
  if (commentMap[postId]) {
    delete commentMap[postId]
    return
  }
  await loadComments(postId)
}

async function sendComment(postId) {
  if (!getToken()) return goLogin()
  if (!commentInputs[postId]) return
  try {
    await api.createComment(postId, { content: commentInputs[postId], parentId: 0 })
    commentInputs[postId] = ''
    await loadComments(postId)
    updatePostLocally(postId, (item) => ({
      ...item,
      commentCount: Number(item.commentCount || 0) + 1
    }))
  } catch (e) {
    showActionError(e, '评论发送失败，请稍后重试')
  }
}

watch([selectedCategory, selectedSort], async () => {
  page.value = 1
  await loadPosts()
}, { flush: 'sync' })

onMounted(async () => {
  await Promise.allSettled([
    loadPosts(),
    loadCategories(),
    loadSideData()
  ])
})
</script>

<template>
  <div class="page-grid community-page">
    <section>
      <div class="card community-hero">
        <div>
          <p class="hero-sub">社区内容广场</p>
          <h2>看看大家今天练了什么</h2>
          <p class="muted">按技巧、装备、路线和同城内容筛选，快速找到值得看的帖子。</p>
        </div>
        <button class="btn-primary" @click="isLoggedIn ? $router.push('/community/publish') : $router.push('/login')">
          <AppIcon name="plus" :size="15" />
          发布新帖子
        </button>
      </div>

      <div class="card">
        <div class="section-head">
          <div>
            <p class="hero-sub">POST FEED</p>
            <h3>帖子广场</h3>
            <p class="muted">{{ listSummary }}</p>
          </div>
          <button class="btn-soft" @click="loadPosts">
            <AppIcon name="refresh" :size="15" />
            刷新
          </button>
        </div>

        <div class="feed-toolbar">
          <div class="search-line">
            <input
              v-model="keyword"
              placeholder="搜索标题、正文、路线或装备..."
              @keyup.enter="applyKeywordSearch"
            />
            <button class="btn-primary" @click="applyKeywordSearch">
              <AppIcon name="search" :size="15" />
              搜索
            </button>
          </div>
          <p v-if="categoriesLoading" role="status">分类加载中…</p>
          <p v-if="categoriesError" role="alert">分类加载失败：{{ categoriesError }} <button type="button" @click="loadCategories">重试分类</button></p>
          <div class="chip-row" aria-label="帖子分类">
            <button type="button" :class="['filter-chip', { active: selectedCategory === '' }]" @click="selectCategory('')">全部</button>
            <button
              v-for="c in categoryOptions"
              :key="c.key"
              type="button"
              :class="['filter-chip', { active: selectedCategory === c.key }]"
              @click="selectCategory(c.key)"
            >
              {{ c.label }}
            </button>
          </div>
          <div class="sort-row" aria-label="排序方式">
            <button
              v-for="s in sortOptions"
              :key="s.value"
              type="button"
              :class="['sort-chip', { active: selectedSort === s.value }]"
              @click="selectedSort = s.value"
            >
              {{ s.label }}
            </button>
          </div>
        </div>

        <div v-if="loading" class="skate-loading-state">
          <div class="skate-loading-board"><SkateboardSketch class="skate-loading-sketch" /></div>
          <p>正在滑向最新帖子...</p>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <div v-if="!loading && !posts.length" class="empty-state">
          <p>暂无帖子，换个筛选条件或发布第一条滑板动态。</p>
        </div>

        <div v-if="!loading && posts.length" class="motion-list post-motion-list">
          <div
            v-for="(item, index) in posts"
            :key="item.postId"
            :class="['post-item', 'post-card-item', 'motion-card', { 'has-images': postImages(item).length }]"
            :style="{ '--motion-index': index }"
          >
            <div v-if="postImages(item).length" class="post-cover-strip" @click="goPostDetail(item.postId)">
              <img
                v-for="img in postImages(item)"
                :key="img"
                :src="img"
                alt="帖子图片"
                class="clickable-image"
                loading="lazy"
                decoding="async"
              />
            </div>
            <div class="post-main">
              <div class="post-body">
                <div class="post-header">
                  <div class="post-title-block">
                    <div class="post-tags">
                      <span v-if="item.isTop === '1'" class="tag hot-tag">置顶</span>
                      <span class="tag">{{ item.category || '未分类' }}</span>
                    </div>
                    <h4 class="clickable-title" @click="goPostDetail(item.postId)">{{ item.title }}</h4>
                  </div>
                  <button class="btn-soft detail-btn" @click="goPostDetail(item.postId)">查看详情</button>
                </div>
                <p class="post-meta">
                  {{ item.authorName || '匿名滑手' }} · Lv{{ item.authorLevel || 1 }} · {{ item.createTime?.replace('T', ' ') }}
                </p>
                <p class="post-content">{{ item.content }}</p>
              </div>

              <div class="post-engagement">
                <div class="post-stats-row">
                  <span>赞 {{ item.likeCount || 0 }}</span>
                  <span>收藏 {{ item.collectCount || 0 }}</span>
                  <span>评论 {{ item.commentCount || 0 }}</span>
                </div>

                <div class="inline actions-row">
                  <button class="btn-soft" :class="{ 'success-action': postState(item.postId).liked }" @click="toggleLike(item.postId)">
                    <AppIcon name="like" :size="14" />
                    {{ postState(item.postId).liked ? '已赞' : '点赞' }}
                  </button>
                  <button class="btn-soft" :class="{ 'success-action': postState(item.postId).collected }" @click="toggleCollect(item.postId)">
                    <AppIcon name="collect" :size="14" />
                    {{ postState(item.postId).collected ? '已收藏' : '收藏' }}
                  </button>
                  <button class="btn-soft" :class="{ 'success-action': postState(item.postId).watchedLater }" @click="toggleWatchLater(item.postId)">
                    {{ postState(item.postId).watchedLater ? '已稍后看' : '稍后再看' }}
                  </button>
                  <button class="btn-soft" @click="toggleComments(item.postId)">
                    <AppIcon name="comment" :size="14" />
                    {{ commentMap[item.postId] ? '收起评论' : '评论' }}
                  </button>
                </div>
              </div>

              <div v-if="commentMap[item.postId]" class="comment-panel">
                <div class="comment-block">
                  <div v-if="!commentMap[item.postId].length" class="comment-empty">还没有评论，来抢第一条。</div>
                  <div v-for="c in commentMap[item.postId]" :key="c.commentId" class="comment-item">
                    <strong>{{ c.username }}：</strong>{{ c.content }}
                  </div>
                </div>

                <div v-if="isLoggedIn" class="inline comment-input-row">
                  <input v-model="commentInputs[item.postId]" placeholder="写评论..." />
                  <button class="btn-primary" @click="sendComment(item.postId)">发送</button>
                </div>
                <div v-else class="login-comment-row">
                  <button @click="$router.push('/login')">登录后评论</button>
                </div>
              </div>
            </div>
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
        <div class="motion-list side-motion-list">
          <div
            v-for="(n, index) in notices.slice(0, 6)"
            :key="n.noticeId"
            class="list-item motion-card"
            :style="{ '--motion-index': index }"
          >
            <strong>{{ n.title }}</strong>
            <p class="muted">{{ n.content }}</p>
          </div>
        </div>
      </div>
      <div class="card">
        <div class="section-head"><h3>滑板资讯</h3></div>
        <div class="motion-list side-motion-list">
          <div
            v-for="(n, index) in newsList.slice(0, 8)"
            :key="n.newsId"
            class="list-item clickable-news-item motion-card"
            :style="{ '--motion-index': index }"
            @click="goNewsDetail(n.newsId)"
          >
            <strong>{{ n.title }}</strong>
            <p class="muted">{{ n.category || '未分类' }}</p>
          </div>
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
  grid-template-columns: 1fr;
  gap: 16px;
  align-items: start;
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 16px;
  background: #fff;
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.post-card-item.has-images {
  grid-template-columns: minmax(240px, 320px) minmax(0, 1fr);
}

.post-main {
  min-width: 0;
  display: grid;
  gap: 12px;
  align-self: stretch;
}

.post-cover-strip {
  align-self: start;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  cursor: pointer;
}

.post-cover-strip img {
  width: 100%;
  height: 88px;
  object-fit: cover;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: var(--surface-muted);
}

.post-cover-strip img:first-child {
  grid-column: 1 / -1;
  height: 190px;
}

.post-body {
  min-width: 0;
  display: grid;
  gap: 9px;
}

.post-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.post-header h4 {
  margin: 0;
}

.post-title-block {
  min-width: 0;
  display: grid;
  gap: 8px;
}

.post-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.hot-tag {
  background: #111827;
  color: #fff;
  border-color: #111827;
}

.clickable-title {
  cursor: pointer;
  font-size: 20px;
  line-height: 1.32;
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.clickable-title:hover {
  text-decoration: underline;
}

.clickable-news-item {
  cursor: pointer;
}

.clickable-news-item:hover strong {
  text-decoration: underline;
}

.post-content {
  margin: 0;
  white-space: pre-wrap;
  color: #1f2937;
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.post-meta {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}

.clickable-image {
  cursor: pointer;
}

.post-engagement {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding-top: 2px;
}

.post-stats-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.post-stats-row span {
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 4px 9px;
  background: #f8fafc;
  color: var(--text-soft);
  font-size: 12px;
}

.actions-row {
  gap: 8px;
  margin-left: auto;
}

.actions-row button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 38px;
}

.detail-btn {
  flex: 0 0 auto;
  min-height: 40px;
  padding-inline: 14px;
}

.comment-panel {
  display: grid;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.comment-block {
  border: 1px solid var(--line);
  background: var(--surface-muted);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
}

.comment-empty {
  color: var(--text-muted);
  font-size: 14px;
}

.comment-item {
  border-bottom: 1px dashed #d1d5db;
  padding: 7px 0;
  font-size: 14px;
}

.comment-item:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.comment-input-row {
  align-items: stretch;
  flex-wrap: nowrap;
}

.comment-input-row input {
  flex: 1;
}

.login-comment-row {
  display: flex;
  justify-content: flex-start;
}

.pagination-bar {
  margin-top: 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.feed-toolbar {
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 12px;
  background: #f8fafc;
  display: grid;
  gap: 10px;
  margin-bottom: 12px;
}

.search-line {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto;
  gap: 8px;
}

.chip-row,
.sort-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.filter-chip,
.sort-chip {
  border: 1px solid var(--line);
  border-radius: 999px;
  background: #fff;
  color: var(--text-soft);
  padding: 7px 11px;
  min-height: 34px;
}

.filter-chip.active,
.sort-chip.active {
  background: #111827;
  color: #fff;
  border-color: #111827;
}

.detail-btn {
  white-space: nowrap;
}

@media (max-width: 768px) {
  .community-hero {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
    padding: 16px;
  }

  .community-hero h2 {
    font-size: 24px;
    line-height: 1.18;
  }

  .community-hero .btn-primary {
    width: 100%;
    min-height: 42px;
  }

  .pagination-bar {
    flex-direction: column;
    align-items: flex-start;
  }

  .feed-toolbar,
  .search-line {
    grid-template-columns: 1fr;
  }

  .feed-toolbar {
    padding: 10px;
    gap: 9px;
    border-radius: 14px;
  }

  .chip-row,
  .sort-row {
    flex-wrap: nowrap;
    overflow-x: auto;
    padding-bottom: 4px;
    scrollbar-width: none;
  }

  .chip-row::-webkit-scrollbar,
  .sort-row::-webkit-scrollbar {
    display: none;
  }

  .filter-chip,
  .sort-chip {
    flex: 0 0 auto;
    white-space: nowrap;
  }

  .post-card-item,
  .post-card-item.has-images {
    grid-template-columns: 1fr;
    padding: 12px;
    border-radius: 16px;
  }

  .post-cover-strip img:first-child {
    height: 190px;
  }

  .post-engagement {
    align-items: flex-start;
  }

  .post-header {
    flex-wrap: wrap;
    gap: 10px;
  }

  .clickable-title {
    font-size: 18px;
    word-break: break-word;
  }

  .post-content {
    -webkit-line-clamp: 3;
  }

  .detail-btn {
    width: 100%;
  }

  .actions-row {
    flex-wrap: wrap;
    margin-left: 0;
  }

  .search-line input,
  .search-line button,
  .filter-chip,
  .sort-chip,
  .actions-row button,
  .comment-input-row button,
  .comment-input-row input {
    min-height: 42px;
  }

  .actions-row > * {
    flex: 1 1 calc(50% - 6px);
  }
}

@media (max-width: 480px) {
  .post-cover-strip {
    grid-template-columns: 1fr 1fr;
  }

  .post-cover-strip img,
  .post-cover-strip img:first-child {
    height: 112px;
  }

  .post-card-item,
  .post-card-item.has-images {
    padding: 10px;
  }

  .post-tags {
    gap: 5px;
  }

  .post-stats-row {
    display: grid;
    grid-template-columns: 1fr 1fr;
    width: 100%;
  }

  .post-stats-row span {
    text-align: center;
  }

  .comment-input-row {
    flex-wrap: wrap;
  }

  .comment-input-row input,
  .comment-input-row button {
    flex: 1 1 100%;
  }

  .actions-row > * {
    flex: 1 1 100%;
  }

  .pagination-bar span {
    word-break: break-word;
  }
}
</style>
