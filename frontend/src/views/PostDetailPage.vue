<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { getToken } from '../utils/auth'
import { normalizeCommentThreads } from '../utils/communityComments'
import { normalizeMediaUrl } from '../utils/url'

const route = useRoute()
const router = useRouter()

const post = ref(null)
const comments = ref([])
const loading = ref(false)
const error = ref('')
const commentInput = ref('')
const replyInputs = ref({})
const replyingCommentId = ref(null)
const postingComment = ref(false)
const REPORT_REASONS = ['广告', '辱骂', '人身攻击', '虚假信息', '违法违规', '其他']

const isLoggedIn = computed(() => !!getToken())
const postId = computed(() => Number(route.params.id))
const commentWarnings = computed(() => buildCommentWarnings(commentInput.value))

function buildCommentWarnings(value) {
  const text = String(value || '').trim()
  const compact = text.replace(/\s+/g, '')
  const warnings = []
  if (compact && compact.length < 2) warnings.push('评论过短，建议补充完整表达。')
  if ((text.match(/https?:\/\/|www\./gi) || []).length > 1) warnings.push('评论包含多个链接，可能触发审核关注。')
  if (/(.)\1{8,}|(.{2,8})\2{3,}/.test(compact)) warnings.push('检测到重复文本，请整理后再发送。')
  if (/约架|骚扰|赌博|贷款|代刷|外挂|casino|loan/i.test(text)) warnings.push('包含敏感词，请确认符合社区规则。')
  return warnings
}

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
    comments.value = normalizeCommentThreads(commentData || [])
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

function replyKey(commentId) {
  return String(commentId)
}

function goUserProfile(userId) {
  if (!userId) return
  router.push(`/users/${userId}`)
}

function setReplyDraft(commentId, value) {
  replyInputs.value = {
    ...replyInputs.value,
    [replyKey(commentId)]: value
  }
}

async function sendReply(rootComment, targetComment = rootComment) {
  if (!isLoggedIn.value) return router.push('/login')
  const key = replyKey(rootComment.commentId)
  const content = String(replyInputs.value[key] || '').trim()
  if (!content) return
  replyingCommentId.value = rootComment.commentId
  try {
    await api.replyComment(rootComment.commentId, {
      content,
      parentId: rootComment.commentId,
      replyToUserId: targetComment.userId
    })
    setReplyDraft(rootComment.commentId, '')
    await loadPostDetail()
  } catch (e) {
    showActionError(e, '回复发送失败，请稍后重试')
  } finally {
    replyingCommentId.value = null
  }
}

async function toggleCommentLike(commentId) {
  if (!isLoggedIn.value) return router.push('/login')
  try {
    await api.likeComment(commentId)
    await loadPostDetail()
  } catch (e) {
    showActionError(e, '评论点赞失败，请稍后重试')
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
        <div class="author-line">
          <button class="author-link" type="button" @click="goUserProfile(post.userId)">
            {{ post.authorName || '匿名滑手' }} · Lv{{ post.authorLevel || 1 }}
          </button>
          <p class="muted">{{ post.createTime?.replace('T', ' ') }}</p>
        </div>
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
      <div v-for="item in comments" :key="item.commentId" class="comment-card">
        <div class="comment-main">
          <p>
            <button class="comment-author" type="button" @click="goUserProfile(item.userId)">
              {{ item.username || '匿名滑手' }}
            </button>
            ：{{ item.content }}
          </p>
          <div class="inline comment-actions">
            <p class="muted">{{ item.createTime?.replace('T', ' ') }}</p>
            <button class="btn-soft" @click="toggleCommentLike(item.commentId)">赞 {{ item.likeCount || 0 }}</button>
            <button class="btn-soft" @click="setReplyDraft(item.commentId, `回复 @${item.username}：`)">回复</button>
            <button class="btn-soft" @click="reportComment(item.commentId)">举报</button>
          </div>
        </div>

        <div v-if="item.replies?.length" class="reply-list">
          <div v-for="reply in item.replies" :key="reply.commentId" class="reply-item">
            <p>
              <button class="comment-author" type="button" @click="goUserProfile(reply.userId)">
                {{ reply.username || '匿名滑手' }}
              </button>
              <span v-if="reply.replyToUsername" class="muted"> 回复 @{{ reply.replyToUsername }}</span>
              ：{{ reply.content }}
            </p>
            <div class="inline comment-actions">
              <p class="muted">{{ reply.createTime?.replace('T', ' ') }}</p>
              <button class="btn-soft" @click="toggleCommentLike(reply.commentId)">赞 {{ reply.likeCount || 0 }}</button>
              <button class="btn-soft" @click="setReplyDraft(item.commentId, `回复 @${reply.username}：`)">回复</button>
              <button class="btn-soft" @click="reportComment(reply.commentId)">举报</button>
            </div>
          </div>
        </div>

        <div class="inline reply-row">
          <input
            :value="replyInputs[String(item.commentId)] || ''"
            placeholder="回复这条评论..."
            @input="setReplyDraft(item.commentId, $event.target.value)"
            @keyup.enter="sendReply(item)"
          />
          <button class="btn-soft" :disabled="replyingCommentId === item.commentId" @click="sendReply(item)">
            {{ replyingCommentId === item.commentId ? '回复中...' : '回复' }}
          </button>
        </div>
        <ul v-if="buildCommentWarnings(replyInputs[String(item.commentId)]).length" class="comment-warnings">
          <li v-for="warning in buildCommentWarnings(replyInputs[String(item.commentId)])" :key="warning">{{ warning }}</li>
        </ul>
      </div>

      <div class="inline comment-row">
        <input v-model="commentInput" placeholder="写评论..." @keyup.enter="sendComment" />
        <button class="btn-primary" :disabled="postingComment" @click="sendComment">
          {{ postingComment ? '发送中...' : '发送' }}
        </button>
      </div>
      <ul v-if="commentWarnings.length" class="comment-warnings">
        <li v-for="warning in commentWarnings" :key="warning">{{ warning }}</li>
      </ul>
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

.author-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px 14px;
  margin-top: 10px;
}

.author-line .muted {
  margin: 0;
}

.author-link,
.comment-author {
  border: 0;
  padding: 0;
  background: transparent;
  color: #111827;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.author-link:hover,
.comment-author:hover {
  text-decoration: underline;
}

.post-content {
  margin: 18px 0;
  white-space: pre-wrap;
  line-height: 1.85;
}

.post-images {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin: 16px 0;
}

.post-images a {
  display: block;
  overflow: hidden;
  border-radius: 8px;
  border: 1px solid rgba(15, 23, 42, .08);
  background: #f8fafc;
}

.post-images img {
  display: block;
  width: 100%;
  height: 220px;
  object-fit: cover;
}

.action-row {
  padding-top: 8px;
}

.comment-card {
  padding: 14px 0;
  border-bottom: 1px solid rgba(15, 23, 42, .08);
}

.comment-card:last-of-type {
  border-bottom: 0;
}

.comment-card p {
  margin: 0;
  line-height: 1.75;
}

.comment-main {
  display: grid;
  gap: 8px;
}

.comment-actions {
  gap: 8px;
}

.reply-list {
  display: grid;
  gap: 10px;
  margin: 12px 0 0 18px;
  padding-left: 14px;
  border-left: 3px solid rgba(56, 189, 248, .25);
}

.reply-item {
  display: grid;
  gap: 8px;
  padding: 10px;
  border-radius: 8px;
  background: #f8fafc;
}

.reply-row {
  margin-top: 12px;
  gap: 8px;
}

.reply-row input {
  flex: 1;
}

.comment-row {
  align-items: stretch;
  margin-top: 16px;
}

.comment-row input {
  flex: 1;
}

.comment-warnings {
  display: grid;
  gap: 4px;
  margin: 8px 0 0;
  padding: 8px 10px 8px 26px;
  border: 1px solid #fde68a;
  border-radius: 10px;
  background: #fffbeb;
  color: #78350f;
  font-size: 13px;
}

.comment-row .btn-primary {
  width: 100px;
}

@media (max-width: 640px) {
  .post-head,
  .author-line,
  .comment-row,
  .reply-row {
    align-items: stretch;
    flex-direction: column;
  }

  .post-images {
    grid-template-columns: 1fr;
  }

  .post-images img {
    height: 190px;
  }

  .comment-row .btn-primary,
  .reply-row .btn-soft {
    width: 100%;
  }

  .reply-list {
    margin-left: 0;
  }
}
</style>
