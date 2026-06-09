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
.detail-wrap { display: grid; gap: 14px; max-width: 960px; margin: 0 auto; }
.post-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.post-head h2 { margin: 0; }
.post-content { white-space: pre-wrap; }
.post-images { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 8px; }
.post-images img { width: 100%; height: 110px; border-radius: 10px; object-fit: cover; border: 1px solid var(--line); }
.action-row { margin-top: 8px; }
.comment-row { margin-top: 10px; align-items: stretch; }
.comment-row input { flex: 1; }
</style>
