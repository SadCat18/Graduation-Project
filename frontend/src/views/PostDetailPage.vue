<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { getToken } from '../utils/auth'
import { normalizeMediaUrl } from '../utils/url'

const route = useRoute()
const router = useRouter()

const post = ref(null)
const comments = ref([])
const loading = ref(false)
const error = ref('')
const commentInput = ref('')
const postingComment = ref(false)
const REPORT_REASONS = ['广告', '辱骂', '人身攻击', '虚假信息', '违法违规', '其他']

const isLoggedIn = computed(() => !!getToken())
const postId = computed(() => Number(route.params.id))

function showActionError(e, fallback = '操作失败，请稍后重试') {
  alert(e?.message || fallback)
}

function parseImages(images) {
  if (!images) return []
  return String(images)
    .split(',')
    .map(item => normalizeMediaUrl(item.trim()))
    .filter(Boolean)
}

async function loadPostDetail() {
  if (!Number.isFinite(postId.value)) {
    error.value = '帖子参数错误'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const [postData, commentData] = await Promise.all([
      api.postDetail(postId.value),
      api.comments(postId.value)
    ])
    post.value = postData
    comments.value = commentData || []
  } catch (e) {
    error.value = e?.message || '帖子加载失败'
  } finally {
    loading.value = false
  }
}

async function sendComment() {
  if (!isLoggedIn.value) return router.push('/login')
  const content = commentInput.value.trim()
  if (!content) return
  postingComment.value = true
  try {
    await api.createComment(postId.value, { content, parentId: 0 })
    commentInput.value = ''
    await loadPostDetail()
  } catch (e) {
    showActionError(e, '评论发送失败，请稍后重试')
  } finally {
    postingComment.value = false
  }
}

async function toggleLike() {
  if (!isLoggedIn.value) return router.push('/login')
  try {
    await api.likePost(postId.value)
    await loadPostDetail()
  } catch (e) {
    showActionError(e, '点赞失败，请稍后重试')
  }
}

async function toggleCollect() {
  if (!isLoggedIn.value) return router.push('/login')
  try {
    await api.collectPost(postId.value)
    await loadPostDetail()
  } catch (e) {
    showActionError(e, '收藏失败，请稍后重试')
  }
}

async function toggleWatchLater() {
  if (!isLoggedIn.value) return router.push('/login')
  try {
    await api.watchLaterPost(postId.value)
    await loadPostDetail()
  } catch (e) {
    showActionError(e, '操作失败，请稍后重试')
  }
}

function chooseReportReason() {
  const tip = REPORT_REASONS.map((item, idx) => `${idx + 1}. ${item}`).join('\n')
  const picked = window.prompt(`请选择举报原因，输入序号：\n${tip}`, '1')
  const idx = Number(picked) - 1
  if (!Number.isInteger(idx) || idx < 0 || idx >= REPORT_REASONS.length) return ''
  return REPORT_REASONS[idx]
}

async function reportPost() {
  if (!isLoggedIn.value) return router.push('/login')
  const reason = chooseReportReason()
  if (!reason) return
  const detail = window.prompt('可补充举报说明（可留空）', '') || ''
  try {
    await api.createReport({ targetType: 'POST', targetId: postId.value, reason, detail })
    alert('举报已提交，感谢反馈')
  } catch (e) {
    showActionError(e, '举报失败，请稍后重试')
  }
}

async function reportComment(commentId) {
  if (!isLoggedIn.value) return router.push('/login')
  const reason = chooseReportReason()
  if (!reason) return
  const detail = window.prompt('可补充举报说明（可留空）', '') || ''
  try {
    await api.createReport({ targetType: 'COMMENT', targetId: commentId, reason, detail })
    alert('举报已提交，感谢反馈')
  } catch (e) {
    showActionError(e, '举报失败，请稍后重试')
  }
}

onMounted(loadPostDetail)
</script>

<template>
  <div class="detail-wrap">
    <div class="card">
      <div class="section-head">
        <button class="btn-soft" @click="$router.push('/community')">
          <AppIcon name="community" :size="15" />
          返回帖子列表
        </button>
      </div>

      <p v-if="loading" class="muted">加载中...</p>
      <p v-if="error" class="error">{{ error }}</p>

      <template v-if="post && !loading">
        <div class="post-head">
          <h2>{{ post.title }}</h2>
          <span class="tag">{{ post.category || '未分类' }}</span>
        </div>
        <p class="muted">作者：{{ post.authorName }} · Lv{{ post.authorLevel || 1 }} · {{ post.createTime?.replace('T', ' ') }}</p>
        <p class="post-content">{{ post.content }}</p>

        <div v-if="parseImages(post.images).length" class="post-images">
          <a
            v-for="img in parseImages(post.images)"
            :key="img"
            :href="img"
            target="_blank"
            rel="noreferrer"
          >
            <img :src="img" alt="帖子图片" loading="lazy" decoding="async" />
          </a>
        </div>

        <div class="inline action-row">
          <button class="btn-soft" @click="toggleLike">点赞 {{ post.likeCount || 0 }}</button>
          <button class="btn-soft" @click="toggleCollect">收藏 {{ post.collectCount || 0 }}</button>
          <button class="btn-soft" @click="toggleWatchLater">稍后再看</button>
          <button class="btn-soft" @click="reportPost">举报帖子</button>
          <span class="muted">评论 {{ post.commentCount || comments.length }}</span>
        </div>
      </template>
    </div>

    <div class="card">
      <div class="section-head"><h3>评论区</h3></div>
      <div v-if="!comments.length" class="empty-state">还没有评论，快来抢沙发吧。</div>
      <div v-for="item in comments" :key="item.commentId" class="list-item">
        <p><strong>{{ item.username }}：</strong>{{ item.content }}</p>
        <div class="inline">
          <p class="muted">{{ item.createTime?.replace('T', ' ') }}</p>
          <button class="btn-soft" @click="reportComment(item.commentId)">举报</button>
        </div>
      </div>

      <div class="inline comment-row">
        <input v-model="commentInput" placeholder="写评论..." @keyup.enter="sendComment" />
        <button class="btn-primary" :disabled="postingComment" @click="sendComment">
          {{ postingComment ? '发送中...' : '发送' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.detail-wrap {
  display: grid;
  gap: 14px;
  max-width: 980px;
  margin: 0 auto;
}

.detail-wrap > .card {
  overflow: hidden;
}

.post-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.post-head h2 {
  margin: 0;
  line-height: 1.28;
}

.post-head + .muted {
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}

.post-content {
  white-space: pre-wrap;
  line-height: 1.8;
  word-break: break-word;
}

.post-images {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.post-images a {
  display: block;
  min-width: 0;
}

.post-images img {
  width: 100%;
  height: 136px;
  border-radius: 10px;
  object-fit: cover;
  border: 1px solid var(--line);
  background: var(--surface-muted);
}

.action-row {
  align-items: center;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.action-row .btn-soft {
  min-height: 38px;
}

.action-row .muted {
  margin-left: auto;
  white-space: nowrap;
}

.list-item {
  padding: 12px 0;
  border-bottom: 1px solid var(--line);
}

.list-item:last-of-type {
  border-bottom: 0;
}

.list-item p {
  line-height: 1.7;
  word-break: break-word;
}

.comment-row {
  margin-top: 14px;
  align-items: stretch;
}

.comment-row input {
  flex: 1;
}

.comment-row .btn-primary {
  min-width: 104px;
}

@media (max-width: 640px) {
  .post-head,
  .action-row,
  .comment-row {
    align-items: stretch;
    flex-direction: column;
  }

  .post-images {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .post-images img {
    height: 118px;
  }

  .action-row .muted {
    margin-left: 0;
  }

  .action-row .btn-soft,
  .comment-row .btn-primary {
    width: 100%;
  }
}
</style>
