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

const isLoggedIn = computed(() => !!getToken())
const postId = computed(() => Number(route.params.id))

function parseImages(images) {
  if (!images) return []
  return String(images)
    .split(',')
    .map(item => item.trim())
    .map(item => normalizeMediaUrl(item))
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
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  const content = commentInput.value.trim()
  if (!content) return

  postingComment.value = true
  try {
    await api.createComment(postId.value, { content, parentId: 0 })
    commentInput.value = ''
    await loadPostDetail()
  } finally {
    postingComment.value = false
  }
}

async function toggleLike() {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  await api.likePost(postId.value)
  await loadPostDetail()
}

async function toggleCollect() {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  await api.collectPost(postId.value)
  await loadPostDetail()
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
        <p class="muted">作者：{{ post.authorName }} · {{ post.createTime?.replace('T', ' ') }}</p>
        <p class="post-content">{{ post.content }}</p>

        <div v-if="parseImages(post.images).length" class="post-images">
          <a
            v-for="img in parseImages(post.images)"
            :key="img"
            :href="img"
            target="_blank"
            rel="noopener noreferrer"
          >
            <img :src="img" alt="帖子图片" />
          </a>
        </div>

        <div class="inline action-row">
          <button class="btn-soft" @click="toggleLike">
            <AppIcon name="like" :size="14" />
            点赞 {{ post.likeCount || 0 }}
          </button>
          <button class="btn-soft" @click="toggleCollect">
            <AppIcon name="collect" :size="14" />
            收藏 {{ post.collectCount || 0 }}
          </button>
          <span class="muted">评论 {{ post.commentCount || comments.length }}</span>
        </div>
      </template>
    </div>

    <div class="card">
      <div class="section-head"><h3>评论区</h3></div>
      <div v-if="!comments.length" class="empty-state">还没有评论，快来抢沙发吧。</div>
      <div v-for="item in comments" :key="item.commentId" class="list-item">
        <p><strong>{{ item.username }}：</strong>{{ item.content }}</p>
        <p class="muted">{{ item.createTime?.replace('T', ' ') }}</p>
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
  max-width: 960px;
  margin: 0 auto;
}

.post-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.post-head h2 {
  margin: 0;
}

.post-content {
  white-space: pre-wrap;
}

.post-images {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 8px;
}

.post-images img {
  width: 100%;
  height: 110px;
  border-radius: var(--radius-md);
  object-fit: cover;
  border: 1px solid var(--line);
}

.action-row {
  margin-top: 8px;
}

.comment-row {
  margin-top: 10px;
  align-items: stretch;
}

.comment-row input {
  flex: 1;
}

@media (max-width: 768px) {
  .post-images {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .comment-row {
    flex-direction: column;
  }
}
</style>
