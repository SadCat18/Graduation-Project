<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { getToken } from '../utils/auth'
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
const commentInputs = reactive({})
const commentMap = reactive({})
const postUiState = reactive({})
const keyword = ref('')
const selectedCategory = ref('全部')
const selectedSort = ref('latest')
const categoryOptions = ['全部', '新手教学', '技巧交流', '装备分享', '路线分享', '赛事讨论', '同城约板', '日常交流', '经验分享']
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
  const categoryText = selectedCategory.value === '全部' ? '全部分类' : selectedCategory.value
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
  loading.value = true
  error.value = ''
  try {
    const categoryParam = selectedCategory.value === '全部' ? '' : selectedCategory.value
    if (selectedSort.value === 'recommended' && getToken()) {
      const rec = await api.recommendations()
      let list = rec?.posts || []
      if (keyword.value.trim()) {
        const kw = keyword.value.trim().toLowerCase()
        list = list.filter(item => String(item.title || '').toLowerCase().includes(kw) || String(item.content || '').toLowerCase().includes(kw))
      }
      if (categoryParam) {
        list = list.filter(item => item.category === categoryParam)
      }
      total.value = list.length
      const start = (page.value - 1) * pageSize
      posts.value = list.slice(start, start + pageSize)
    } else {
      const res = await api.posts({
        page: page.value,
        size: pageSize,
        keyword: keyword.value.trim(),
        category: categoryParam,
        sort: selectedSort.value === 'recommended' ? 'hot' : selectedSort.value
      })
      total.value = Number(res.total || 0)
      posts.value = res.list || []
    }
    syncPostUiState(posts.value)
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
  const [noticeData, newsData] = await Promise.all([
    loadCachedPublic('public:notices', () => api.notices()),
    loadCachedPublic('public:news', () => api.news())
  ])
  notices.value = noticeData || []
  newsList.value = newsData || []
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
})

onMounted(async () => {
  await Promise.all([
    loadPosts(),
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
          <div class="chip-row" aria-label="帖子分类">
            <button
              v-for="c in categoryOptions"
              :key="c"
              type="button"
              :class="['filter-chip', { active: selectedCategory === c }]"
              @click="selectCategory(c)"
            >
              {{ c }}
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

        <p v-if="loading" class="muted">加载中...</p>
        <p v-if="error" class="error">{{ error }}</p>
        <p v-if="!loading && !posts.length" class="muted">暂无帖子</p>

        <div v-for="item in posts" :key="item.postId" :class="['post-item', 'post-card-item', { 'has-images': postImages(item).length }]">
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

          <div class="post-stats-row">
            <span>赞 {{ item.likeCount || 0 }}</span>
            <span>收藏 {{ item.collectCount || 0 }}</span>
            <span>评论 {{ item.commentCount || 0 }}</span>
          </div>

          <div class="inline actions-row">
            <button class="btn-soft" @click="toggleLike(item.postId)">
              <AppIcon name="like" :size="14" />
              {{ postState(item.postId).liked ? '已赞' : '点赞' }}
            </button>
            <button class="btn-soft" @click="toggleCollect(item.postId)">
              <AppIcon name="collect" :size="14" />
              {{ postState(item.postId).collected ? '已收藏' : '收藏' }}
            </button>
            <button class="btn-soft" @click="toggleWatchLater(item.postId)">
              {{ postState(item.postId).watchedLater ? '已加入稍后再看' : '稍后再看' }}
            </button>
            <button class="btn-soft" @click="loadComments(item.postId)">
              <AppIcon name="comment" :size="14" />
              看评论
            </button>
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
        <div
          v-for="n in newsList.slice(0, 8)"
          :key="n.newsId"
          class="list-item clickable-news-item"
          @click="goNewsDetail(n.newsId)"
        >
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
  gap: 14px;
  align-items: start;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 14px;
  background: #fff;
  box-shadow: var(--shadow-sm);
}

.post-card-item.has-images {
  grid-template-columns: minmax(180px, 260px) minmax(0, 1fr);
}

.post-cover-strip {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  cursor: pointer;
}

.post-cover-strip img {
  width: 100%;
  height: 112px;
  object-fit: cover;
  border-radius: 10px;
  border: 1px solid var(--line);
}

.post-cover-strip img:first-child {
  grid-column: 1 / -1;
  height: 150px;
}

.post-body {
  min-width: 0;
  display: grid;
  gap: 8px;
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
  line-height: 1.35;
  word-break: break-word;
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
  -webkit-line-clamp: 3;
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

.post-stats-row {
  grid-column: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.post-card-item.has-images .post-stats-row {
  grid-column: 2;
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
  grid-column: 1;
  margin-top: 2px;
}

.post-card-item.has-images .actions-row {
  grid-column: 2;
}

.comment-block {
  border: 1px solid var(--line);
  background: var(--surface-muted);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
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
  }

  .pagination-bar {
    flex-direction: column;
    align-items: flex-start;
  }

  .feed-toolbar,
  .search-line {
    grid-template-columns: 1fr;
  }

  .post-card-item {
    grid-template-columns: 1fr;
  }

  .post-stats-row,
  .actions-row {
    grid-column: 1;
  }

  .post-header {
    flex-wrap: wrap;
  }

  .clickable-title {
    word-break: break-word;
  }

  .actions-row {
    flex-wrap: wrap;
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
    height: 120px;
  }

  .actions-row > * {
    flex: 1 1 100%;
  }

  .pagination-bar span {
    word-break: break-word;
  }
}
</style>
