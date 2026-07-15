<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import SkateboardSketch from '../components/SkateboardSketch.vue'
import { api } from '../api'
import { normalizeMediaUrl } from '../utils/url'

const router = useRouter()
const route = useRoute()

const typingStates = [
  '正在拆解你的问题，先把动作重点理清楚。',
  '正在匹配更适合新手的练习建议和节奏。',
  '马上整理成更好执行的回答发给你。'
]

const quickQuestionPool = [
  '新手如何选择第一块滑板？',
  'Ollie 总是跳不起来怎么办？',
  '摔倒时怎么保护手腕和膝盖？',
  '我适合参加什么同城活动？',
  '滑板轴承应该怎么保养？',
  '怎么判断自己是 Regular 还是 Goofy？',
  '练习前怎么热身比较好？',
  'Kickflip 前需要先会哪些动作？',
  '雨后路面还能滑吗？',
  '一个人练滑板怎么安排计划？',
  '滑板场新手应该避开什么区域？',
  '拍动作视频用什么角度更容易看清？'
]

const quickQuestions = ref([])
const quickQuestionBatchKey = ref(0)
const quickRefreshing = ref(false)
let quickQuestionQueue = []
let quickRefreshTimer = null

const categoryRules = [
  { label: '入门', match: ['新手', '选择', 'Regular', 'Goofy'] },
  { label: '动作', match: ['Ollie', 'Kickflip', '跳'] },
  { label: '安全', match: ['摔倒', '保护', '热身', '雨后'] },
  { label: '装备', match: ['滑板', '轴承'] },
  { label: '计划', match: ['计划', '安排'] },
  { label: '场地', match: ['活动', '滑板场'] },
  { label: '拍摄', match: ['视频', '角度'] }
]

const sessions = ref([])
const sessionsLoading = ref(false)
const sessionsError = ref('')
const activeSessionId = ref('')
const currentTitle = ref('新对话')
const messages = ref([welcomeMessage()])
const messagesLoading = ref(false)
const messagesError = ref('')
const inputValue = ref('')
const sending = ref(false)
const mobilePanel = ref('chat')
const isMobile = ref(false)
const chatBodyRef = ref(null)
const relatedContent = ref(emptyRelated())
const typingStateIndex = ref(0)
const deletingSession = ref('')
let typingStateTimer = null

const activeSession = computed(() =>
  sessions.value.find(item => item.sessionId === activeSessionId.value) || null
)

const hasRelatedContent = computed(() =>
  relatedContent.value.posts.length ||
  relatedContent.value.videos.length ||
  relatedContent.value.activities.length
)

const currentTypingState = computed(() => typingStates[typingStateIndex.value] || typingStates[0])

function welcomeMessage() {
  return {
    id: `welcome-${Date.now()}`,
    role: 'assistant',
    content: '你好，我是 AI 滑板老师。你可以问我入门动作、装备选择、练习步骤或安全问题，我会尽量分步骤讲清楚。',
    suggestions: [],
    createTime: ''
  }
}

function emptyRelated() {
  return { posts: [], videos: [], activities: [] }
}

function quickQuestionLabel(question) {
  return categoryRules.find(item => item.match.some(keyword => question.includes(keyword)))?.label || '提问'
}

function shuffleQuestions(list) {
  return [...list].sort(() => Math.random() - 0.5)
}

function refillQuickQuestionQueue(excludedQuestions = []) {
  const excludedSet = new Set(excludedQuestions)
  const preferredPool = quickQuestionPool.filter(item => !excludedSet.has(item))
  quickQuestionQueue = shuffleQuestions(preferredPool.length ? preferredPool : quickQuestionPool)
}

function trackProductEvent(eventType, targetType = 'AI_COACH', targetId = null) {
  api.trackProductEvent({ eventType, targetType, targetId, sourcePage: 'ai-coach' }).catch(() => {})
}

function pickQuickQuestions() {
  const nextQuestions = []
  while (nextQuestions.length < 4 && quickQuestionPool.length) {
    if (!quickQuestionQueue.length) {
      refillQuickQuestionQueue([...quickQuestions.value, ...nextQuestions])
    }
    const nextQuestion = quickQuestionQueue.shift()
    if (!nextQuestion || nextQuestions.includes(nextQuestion)) continue
    nextQuestions.push(nextQuestion)
  }
  quickQuestions.value = nextQuestions
  quickQuestionBatchKey.value += 1
}

function refreshQuickQuestions() {
  if (sending.value || messagesLoading.value || quickRefreshing.value || document.hidden) return
  quickRefreshing.value = true
  pickQuickQuestions()
  window.clearTimeout(quickRefreshTimer)
  quickRefreshTimer = window.setTimeout(() => {
    quickRefreshing.value = false
  }, 360)
}

function extractFirstJsonObjectText(text) {
  const start = text.indexOf('{')
  if (start < 0) return ''
  let inString = false
  let escaped = false
  let depth = 0
  for (let index = start; index < text.length; index += 1) {
    const current = text[index]
    if (escaped) {
      escaped = false
      continue
    }
    if (current === '\\' && inString) {
      escaped = true
      continue
    }
    if (current === '"') {
      inString = !inString
      continue
    }
    if (inString) continue
    if (current === '{') {
      depth += 1
    } else if (current === '}') {
      depth -= 1
      if (depth === 0) return text.slice(start, index + 1)
    }
  }
  return ''
}

function normalizeCoachDisplayContent(content) {
  const text = String(content || '').trim()
  if (!text || !text.includes('"reply"')) return text
  const jsonText = extractFirstJsonObjectText(text)
  if (!jsonText) return text
  try {
    const parsed = JSON.parse(jsonText)
    return typeof parsed?.reply === 'string' && parsed.reply.trim()
      ? parsed.reply.trim()
      : text
  } catch (error) {
    return text
  }
}

function formatCoachContent(content) {
  const normalized = String(content || '').replace(/\r\n/g, '\n').trim()
  if (!normalized) return []

  let stepIndex = 0
  return normalized
    .split('\n')
    .map(line => line.trim())
    .filter(Boolean)
    .map((line) => {
      const numbered = line.match(/^(\d+)[.、]\s*(.+)$/)
      const bullet = line.match(/^[-•]\s*(.+)$/)
      const text = numbered?.[2] || bullet?.[1] || line
      const isSafety = /注意|安全|护具|摔|受伤|休息|停止|热身|风险/.test(text)
      const looksLikeStep = !!numbered || !!bullet || /^(先|再|然后|最后|第一|第二|第三|第四)/.test(text)

      if (isSafety) return { type: 'tip', text, index: '!' }
      if (looksLikeStep) {
        stepIndex += 1
        return { type: 'step', text, index: numbered?.[1] || stepIndex }
      }
      return { type: 'text', text, index: '' }
    })
}

function isCoachHistoryApiUnavailable(error) {
  const status = error?.response?.status
  const message = String(error?.response?.data?.message || error?.message || '').toLowerCase()
  return status === 404 || message.includes('no static resource') || message.includes('/api/ai/coach/sessions')
}

function formatSessionTime(value) {
  if (!value) return '刚刚更新'
  return String(value).replace('T', ' ').slice(0, 16)
}

function formatActivityTime(value) {
  if (!value) return '时间待定'
  return String(value).replace('T', ' ').slice(0, 16)
}

function syncViewport() {
  isMobile.value = window.innerWidth <= 960
  if (!isMobile.value) {
    mobilePanel.value = 'chat'
  }
}

function canSendText(text) {
  return !sending.value && String(text || '').trim().length > 0
}

function createMessage(role, content, extra = {}) {
  return {
    id: `${role}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    role,
    content,
    suggestions: Array.isArray(extra.suggestions) ? extra.suggestions : [],
    references: Array.isArray(extra.references) ? extra.references : [],
    createTime: extra.createTime || ''
  }
}

function startTypingStateMotion() {
  stopTypingStateMotion()
  typingStateIndex.value = 0
  if (typingStates.length <= 1) return
  typingStateTimer = window.setInterval(() => {
    typingStateIndex.value = (typingStateIndex.value + 1) % typingStates.length
  }, 1800)
}

function stopTypingStateMotion() {
  if (typingStateTimer) {
    window.clearInterval(typingStateTimer)
    typingStateTimer = null
  }
}

function applyRelatedContent(result) {
  relatedContent.value = {
    posts: Array.isArray(result?.relatedPosts) ? result.relatedPosts : [],
    videos: Array.isArray(result?.relatedVideos) ? result.relatedVideos : [],
    activities: Array.isArray(result?.relatedActivities) ? result.relatedActivities : []
  }
}

function resetConversation() {
  activeSessionId.value = ''
  currentTitle.value = '新对话'
  messages.value = [welcomeMessage()]
  messagesError.value = ''
  inputValue.value = ''
  relatedContent.value = emptyRelated()
}

function confirmDeleteSession(sessionId) {
  if (sending.value || messagesLoading.value) return
  if (!window.confirm('确定删除这段对话？删除后无法恢复。')) return
  deleteSession(sessionId)
}

async function deleteSession(sessionId) {
  deletingSession.value = sessionId
  try {
    await api.aiCoachDeleteSession(sessionId)
    if (activeSessionId.value === sessionId) {
      resetConversation()
    }
    await loadSessions(false)
  } catch (error) {
    alert(error?.message || '删除失败，请稍后重试')
  } finally {
    deletingSession.value = ''
  }
}

async function scrollToBottom() {
  await nextTick()
  if (!chatBodyRef.value) return
  chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
}

async function loadSessions(keepCurrent = true) {
  sessionsLoading.value = true
  sessionsError.value = ''
  try {
    const result = await api.aiCoachSessions()
    sessions.value = Array.isArray(result) ? result : []

    if (!keepCurrent) return
    if (activeSessionId.value && sessions.value.some(item => item.sessionId === activeSessionId.value)) {
      const current = sessions.value.find(item => item.sessionId === activeSessionId.value)
      currentTitle.value = current?.title || currentTitle.value
      return
    }
    if (!activeSessionId.value && sessions.value.length) {
      await openSession(sessions.value[0].sessionId)
    }
  } catch (error) {
    if (isCoachHistoryApiUnavailable(error)) {
      sessions.value = []
      sessionsError.value = ''
    } else {
      sessionsError.value = error?.message || '历史会话加载失败，请稍后重试。'
    }
  } finally {
    sessionsLoading.value = false
  }
}

async function openSession(sessionId) {
  if (!sessionId || sessionId === activeSessionId.value || messagesLoading.value) return
  activeSessionId.value = sessionId
  currentTitle.value = sessions.value.find(item => item.sessionId === sessionId)?.title || '历史对话'
  messagesLoading.value = true
  messagesError.value = ''
  relatedContent.value = emptyRelated()
  if (isMobile.value) {
    mobilePanel.value = 'chat'
  }

  try {
    const result = await api.aiCoachSessionMessages(sessionId)
    currentTitle.value = result?.title || currentTitle.value
    messages.value = (result?.messages || []).map(item => ({
      id: `${item.role}-${item.createTime || Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      role: String(item.role || '').toUpperCase() === 'ASSISTANT' ? 'assistant' : 'user',
      content: item.content || '',
      createTime: item.createTime || '',
      suggestions: []
    }))
    if (!messages.value.length) {
      messages.value = [welcomeMessage()]
    }
    await scrollToBottom()
  } catch (error) {
    messagesError.value = isCoachHistoryApiUnavailable(error)
      ? ''
      : (error?.message || '历史消息加载失败，请稍后重试。')
    messages.value = [welcomeMessage()]
  } finally {
    messagesLoading.value = false
  }
}

async function sendMessage(text = inputValue.value) {
  const content = String(text || '').trim()
  if (!canSendText(content)) return

  messages.value.push(createMessage('user', content))
  inputValue.value = ''
  sending.value = true
  messagesError.value = ''
  startTypingStateMotion()
  await scrollToBottom()

  try {
    const result = await api.aiCoachChat({
      message: content,
      sessionId: activeSessionId.value || undefined
    })
    trackProductEvent('ASK_AI_COACH')

    if (result?.sessionId) {
      activeSessionId.value = result.sessionId
    }

    messages.value.push(
      createMessage(
        'assistant',
        result?.reply || '我这次没有成功组织出有效回答，你可以换一个更具体的问题再试试。',
        { suggestions: result?.suggestions || [], references: result?.references || [] }
      )
    )

    applyRelatedContent(result || {})
    await loadSessions(false)
    if (activeSessionId.value) {
      const current = sessions.value.find(item => item.sessionId === activeSessionId.value)
      currentTitle.value = current?.title || currentTitle.value
    }

    loadRelatedContentAsync(content, result?.reply || '')
  } catch (error) {
    messages.value.push(
      createMessage('assistant', error?.message || 'AI 滑板老师暂时开小差了，请稍后再试。')
    )
    relatedContent.value = emptyRelated()
  } finally {
    stopTypingStateMotion()
    sending.value = false
    await scrollToBottom()
  }
}

async function loadRelatedContentAsync(question, reply) {
  try {
    const result = await api.aiCoachRelatedContent({ question, reply })
    if (result) {
      applyRelatedContent(result)
    }
  } catch (error) {
    // Related content is optional; the chat answer should not fail because of it.
  }
}

async function sendQuickQuestion(question) {
  if (!canSendText(question)) return
  await sendMessage(question)
}

function startNewConversation() {
  if (sending.value || messagesLoading.value) return
  resetConversation()
  if (isMobile.value) {
    mobilePanel.value = 'chat'
  }
}

async function handleEnter(event) {
  if (event.shiftKey) return
  event.preventDefault()
  await sendMessage()
}

function goPostDetail(postId) {
  if (!postId) return
  router.push(`/community/post/${postId}`)
}

function goActivities() {
  router.push('/activities')
}

function openVideo(video) {
  if (video?.videoId) {
    router.push({
      path: `/videos/${video.videoId}`,
      query: { from: 'ai-coach' }
    })
    return
  }
  const target = normalizeMediaUrl(video?.url)
  if (!target) return
  window.location.href = target
}

function openReference(reference) {
  if (reference?.routePath) {
    router.push(reference.routePath)
  }
}

onMounted(async () => {
  syncViewport()
  window.addEventListener('resize', syncViewport)
  if (isMobile.value) {
    mobilePanel.value = 'sessions'
  }
  pickQuickQuestions()
  if (route.query.q && !inputValue.value) {
    inputValue.value = String(route.query.q).slice(0, 500)
  }
  await loadSessions()
  await scrollToBottom()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport)
  stopTypingStateMotion()
  window.clearTimeout(quickRefreshTimer)
})
</script>

<template>
  <div class="coach-shell">
    <section class="card coach-hero">
      <div class="hero-copy">
        <p class="hero-kicker">AI COACH</p>
        <h1>AI 滑板老师</h1>
        <p class="hero-text">
          连续追问动作、装备、训练计划和安全问题。回答后会补充站内帖子、教学视频和同城活动，让建议可以马上继续看、继续练。
        </p>
      </div>
      <div class="hero-panel">
        <div class="hero-stat">
          <span>对话能力</span>
          <strong>上下文追问、分步骤讲解、风险提醒、练习计划延展</strong>
        </div>
        <div class="hero-stat">
          <span>推荐来源</span>
          <strong>社区帖子、教学视频、同城约板活动，全部来自站内真实内容</strong>
        </div>
      </div>
    </section>

    <div class="mobile-switch">
      <button type="button" class="btn-soft" :class="{ active: mobilePanel === 'sessions' }" @click="mobilePanel = 'sessions'">
        会话列表
      </button>
      <button type="button" class="btn-soft" :class="{ active: mobilePanel === 'chat' }" @click="mobilePanel = 'chat'">
        当前对话
      </button>
    </div>

    <div class="coach-layout">
      <aside v-show="!isMobile || mobilePanel === 'sessions'" class="card session-card">
        <div class="section-head session-head">
          <div>
            <h2>历史会话</h2>
            <p class="muted">回看旧问题，继续之前的训练话题。</p>
          </div>
          <button type="button" class="btn-primary new-session-btn" @click="startNewConversation">
            <AppIcon name="plus" :size="14" />
            新建对话
          </button>
        </div>

        <p v-if="sessionsLoading" class="muted">正在加载历史会话...</p>
        <p v-else-if="sessionsError" class="error">{{ sessionsError }}</p>

        <div v-else-if="!sessions.length" class="empty-state session-empty">
          <p>你还没有历史会话。</p>
          <button type="button" class="btn-soft" @click="startNewConversation">开始第一段对话</button>
        </div>

        <div v-else class="session-list">
          <div
            v-for="item in sessions"
            :key="item.sessionId"
            :class="['session-item', { active: item.sessionId === activeSessionId }]"
          >
            <button type="button" class="session-item-main" @click="openSession(item.sessionId)">
              <span class="session-title">{{ item.title || '未命名会话' }}</span>
              <span class="session-time">{{ formatSessionTime(item.updateTime) }}</span>
            </button>
            <button
              type="button"
              class="session-delete-btn"
              :disabled="deletingSession === item.sessionId"
              :title="'删除 ' + (item.title || '这段对话')"
              @click.stop="confirmDeleteSession(item.sessionId)"
            >
              <AppIcon name="x" :size="13" />
            </button>
          </div>
        </div>
      </aside>

      <section v-show="!isMobile || mobilePanel === 'chat'" class="chat-column">
        <div class="card chat-card">
          <div class="section-head chat-head">
            <div>
              <h2>{{ currentTitle }}</h2>
              <p class="muted">
                {{ activeSession ? `最近更新：${formatSessionTime(activeSession.updateTime)}` : '发送第一条消息后会自动创建会话。' }}
              </p>
            </div>
            <span class="tag">
              <AppIcon name="skateboard" :size="13" />
              教学模式
            </span>
          </div>

          <div ref="chatBodyRef" class="chat-body">
            <p v-if="messagesLoading" class="muted">正在加载历史消息...</p>
            <p v-else-if="messagesError" class="error">{{ messagesError }}</p>

            <template v-else>
              <div
                v-for="item in messages"
                :key="item.id"
                :class="['message-row', `message-${item.role}`]"
              >
                <div class="avatar-chip">
                  <AppIcon :name="item.role === 'assistant' ? 'skateboard' : 'user'" :size="16" />
                </div>
                <div class="message-card">
                  <div class="message-meta">
                    <div class="message-role">{{ item.role === 'assistant' ? 'AI 滑板老师' : '我' }}</div>
                    <span v-if="item.createTime" class="message-time">{{ formatSessionTime(item.createTime) }}</span>
                  </div>
                  <p v-if="item.role === 'user'" class="message-content">{{ item.content }}</p>
                  <div v-else class="message-content coach-answer">
                    <p
                      v-for="(block, index) in formatCoachContent(normalizeCoachDisplayContent(item.content))"
                      :key="`${item.id}-answer-${index}`"
                      :class="['coach-answer-line', `is-${block.type}`]"
                    >
                      <span v-if="block.type !== 'text'" class="answer-index">{{ block.index }}</span>
                      <span>{{ block.text }}</span>
                    </p>
                  </div>
                  <div v-if="item.role === 'assistant' && item.references?.length" class="coach-references">
                    <span class="coach-references-title">参考资料</span>
                    <div class="coach-reference-list">
                      <button
                        v-for="reference in item.references"
                        :key="`${reference.sourceType}-${reference.sourceId}-${reference.title}`"
                        type="button"
                        class="coach-reference-card"
                        :disabled="!reference.routePath"
                        @click="openReference(reference)"
                      >
                        <span class="coach-reference-type">{{ reference.sourceType || 'SOURCE' }}</span>
                        <span class="coach-reference-title">{{ reference.title }}</span>
                      </button>
                    </div>
                  </div>
                  <div v-if="item.role === 'assistant' && item.suggestions?.length" class="suggestion-wrap">
                    <span class="suggestion-title">后续练习建议</span>
                    <div class="suggestion-list">
                      <button
                        v-for="suggestion in item.suggestions"
                        :key="suggestion"
                        type="button"
                        class="btn-soft suggestion-chip"
                        :disabled="sending"
                        @click="sendQuickQuestion(suggestion)"
                      >
                        {{ suggestion }}
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              <div v-if="sending" class="message-row message-assistant">
                <div class="avatar-chip">
                  <AppIcon name="skateboard" :size="16" />
                </div>
                <div class="message-card typing-card">
                  <div class="coach-skate-loader" aria-hidden="true">
                    <span class="coach-loader-track"></span>
                    <span class="coach-loader-board">
                      <SkateboardSketch class="coach-loader-sketch" />
                    </span>
                  </div>
                  <span class="typing-label">正在组织回答</span>
                  <p class="typing-copy">{{ currentTypingState }}</p>
                  <div class="typing-track" aria-hidden="true"><span></span></div>
                  <div class="typing-dots" aria-label="AI 正在思考">
                    <span></span>
                    <span></span>
                    <span></span>
                  </div>
                </div>
              </div>
            </template>
          </div>

          <div class="composer">
            <TransitionGroup
              name="quick-suggestion"
              tag="div"
              class="quick-strip"
              :class="{ 'is-refreshing': quickRefreshing }"
            >
              <button
                v-for="question in quickQuestions"
                :key="`${quickQuestionBatchKey}-${question}`"
                type="button"
                class="btn-soft quick-pill"
                :disabled="sending || messagesLoading"
                @click="sendQuickQuestion(question)"
              >
                <small class="quick-pill-tag">{{ quickQuestionLabel(question) }}</small>
                <span>{{ question }}</span>
              </button>
              <button
                key="refresh-action"
                type="button"
                :class="['btn-soft', 'refresh-pill', { 'is-refreshing': quickRefreshing }]"
                :disabled="sending || messagesLoading || quickRefreshing"
                @click="refreshQuickQuestions"
              >
                <AppIcon name="refresh" :size="13" />
                换一批
              </button>
            </TransitionGroup>

            <textarea
              v-model="inputValue"
              :disabled="sending || messagesLoading"
              placeholder="例如：我刚开始练 Ollie，总是前脚带不起来，应该先改哪个动作？"
              @keydown.enter="handleEnter"
            />
            <div class="composer-bar">
              <p class="muted">Enter 发送，Shift + Enter 换行。</p>
              <div class="composer-actions">
                <button type="button" class="btn-soft" :disabled="sending || messagesLoading" @click="startNewConversation">
                  新建对话
                </button>
                <button
                  type="button"
                  class="btn-primary send-btn"
                  :disabled="!canSendText(inputValue) || messagesLoading"
                  @click="sendMessage()"
                >
                  <AppIcon name="comment" :size="15" />
                  {{ sending ? '发送中...' : '发送问题' }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <aside v-if="hasRelatedContent" class="card related-card">
          <div class="section-head related-head">
            <div>
              <h3>站内相关内容</h3>
              <p class="muted">根据这次提问，补充一些可以直接继续看的真实内容。</p>
            </div>
          </div>

          <div v-if="relatedContent.posts.length" class="related-block">
            <div class="related-label">相关帖子</div>
            <button
              v-for="post in relatedContent.posts"
              :key="post.postId"
              type="button"
              class="related-item"
              @click="goPostDetail(post.postId)"
            >
              <span class="related-title">{{ post.title }}</span>
              <span class="related-meta">{{ post.category || '社区帖子' }} / {{ formatSessionTime(post.createTime) }}</span>
              <span v-if="post.content" class="related-desc">{{ post.content }}</span>
            </button>
          </div>

          <div v-if="relatedContent.videos.length" class="related-block">
            <div class="related-label">相关视频</div>
            <button
              v-for="video in relatedContent.videos"
              :key="video.videoId"
              type="button"
              class="related-item"
              @click="openVideo(video)"
            >
              <span class="related-title">{{ video.title }}</span>
              <span class="related-meta">教学视频 / {{ formatSessionTime(video.createTime) }}</span>
              <span v-if="video.intro" class="related-desc">{{ video.intro }}</span>
            </button>
          </div>

          <div v-if="relatedContent.activities.length" class="related-block">
            <div class="related-label">相关活动</div>
            <button
              v-for="activity in relatedContent.activities"
              :key="activity.activityId"
              type="button"
              class="related-item"
              @click="goActivities()"
            >
              <span class="related-title">{{ activity.title }}</span>
              <span class="related-meta">
                {{ activity.city || '未设置城市' }}{{ activity.district ? ` / ${activity.district}` : '' }}
                / {{ formatActivityTime(activity.activityTime) }}
              </span>
              <span v-if="activity.activityType" class="related-desc">{{ activity.activityType }}</span>
            </button>
          </div>
        </aside>
      </section>
    </div>
  </div>
</template>

<style scoped>
.coach-shell {
  display: grid;
  gap: 18px;
}

.coach-hero {
  position: relative;
  overflow: hidden;
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(240px, 0.62fr);
  gap: 16px;
  padding: 22px;
  background:
    radial-gradient(circle at top left, rgba(183, 210, 90, 0.22), transparent 34%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(241, 244, 239, 0.94));
  box-shadow: var(--shadow-md);
}

.coach-hero::after {
  content: '';
  position: absolute;
  inset: auto -70px -85px auto;
  width: 240px;
  height: 240px;
  border-radius: 36px;
  transform: rotate(18deg);
  border: 1px solid rgba(18, 21, 19, 0.08);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.5), rgba(18, 21, 19, 0.05));
}

.hero-copy,
.hero-panel {
  position: relative;
  z-index: 1;
}

.hero-kicker {
  margin: 0;
  color: var(--accent);
  letter-spacing: 0.22em;
  font-size: 11px;
  font-weight: 800;
}

.hero-copy h1 {
  margin: 8px 0 10px;
  font-size: clamp(30px, 3.4vw, 46px);
  line-height: 1.06;
}

.hero-text {
  margin: 0;
  max-width: 640px;
  color: var(--text-soft);
  font-size: 15px;
  line-height: 1.7;
}

.hero-panel {
  display: grid;
  gap: 10px;
  align-content: start;
}

.hero-stat {
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: rgba(255, 255, 255, 0.84);
  padding: 13px 14px;
  box-shadow: 0 10px 22px rgba(18, 21, 19, 0.06);
}

.hero-stat span {
  display: block;
  color: var(--text-muted);
  font-size: 11px;
  margin-bottom: 4px;
}

.hero-stat strong {
  color: var(--text);
  line-height: 1.4;
  font-size: 14px;
}

.mobile-switch {
  display: none;
  gap: 8px;
}

.mobile-switch button {
  flex: 1;
  min-height: 42px;
}

.mobile-switch .active {
  background: var(--surface-muted);
  border-color: var(--text-soft);
}

.coach-layout {
  display: grid;
  grid-template-columns: minmax(280px, 0.82fr) minmax(0, 1.7fr);
  gap: 16px;
  align-items: start;
}

.session-card,
.chat-card,
.related-card {
  display: grid;
  gap: 14px;
}

.session-card {
  min-height: 650px;
  align-content: start;
}

.chat-column {
  display: grid;
  gap: 16px;
}

.chat-card {
  min-height: 620px;
}

.session-head,
.chat-head,
.related-head {
  margin-bottom: 0;
}

.new-session-btn {
  white-space: nowrap;
}

.session-list {
  display: grid;
  gap: 10px;
}

.session-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 6px;
  align-items: center;
  border-radius: 16px;
}

.session-item-main {
  display: grid;
  gap: 4px;
  text-align: left;
  justify-items: start;
  padding: 14px 15px;
  border-radius: 14px;
  border: none;
  background: transparent;
  width: 100%;
  cursor: pointer;
  color: inherit;
  font: inherit;
}

.session-delete-btn {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  color: var(--text-muted);
  opacity: 0;
  transition: opacity 0.15s ease, background-color 0.15s ease, color 0.15s ease;
  flex-shrink: 0;
}

.session-item:hover .session-delete-btn {
  opacity: 1;
}

.session-delete-btn:hover {
  background: rgba(239, 68, 68, 0.12);
  color: #dc2626;
}

.session-item.active {
  background: linear-gradient(180deg, rgba(21, 25, 23, 0.96), rgba(45, 51, 47, 0.94));
  color: #f8fafc;
  border-color: rgba(21, 25, 23, 0.7);
  box-shadow: 0 14px 28px rgba(18, 21, 19, 0.16);
}

.session-title {
  font-weight: 700;
  line-height: 1.45;
}

.session-time {
  font-size: 12px;
  color: var(--text-muted);
}

.session-item.active .session-time {
  color: rgba(248, 250, 252, 0.78);
}

.session-empty {
  display: grid;
  gap: 10px;
}

.chat-body {
  display: grid;
  align-content: start;
  gap: 14px;
  min-height: 420px;
  max-height: 68vh;
  overflow: auto;
  padding: 4px 8px 4px 0;
  scroll-behavior: smooth;
}

.message-row {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}

.message-user {
  grid-template-columns: minmax(0, 1fr) 42px;
}

.message-user .avatar-chip {
  order: 2;
}

.message-user .message-card {
  order: 1;
  justify-self: end;
  background: linear-gradient(180deg, rgba(21, 25, 23, 0.96), rgba(45, 51, 47, 0.94));
  color: #f8fafc;
}

.message-user .message-role,
.message-user .message-content,
.message-user .message-time {
  color: inherit;
}

.avatar-chip {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(241, 245, 249, 0.82));
  border: 1px solid rgba(203, 213, 225, 0.75);
  color: var(--primary);
}

.message-card {
  width: min(100%, 760px);
  min-width: 0;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(249, 250, 251, 0.96));
  padding: 14px 15px;
  box-shadow: 0 10px 22px rgba(18, 21, 19, 0.05);
  animation: messageRise 0.34s var(--motion-ease) both;
}

.message-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.message-role {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--text-muted);
}

.message-time {
  font-size: 12px;
  color: var(--text-muted);
}

.message-content {
  margin: 8px 0 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
  line-height: 1.72;
  color: var(--text);
}

.coach-answer {
  display: grid;
  gap: 8px;
  white-space: normal;
}

.coach-answer-line {
  display: flex;
  gap: 8px;
  margin: 0;
  min-width: 0;
  max-width: 100%;
  overflow-wrap: anywhere;
  line-height: 1.7;
}

.coach-answer-line span:last-child {
  min-width: 0;
}

.coach-answer-line.is-step,
.coach-answer-line.is-tip {
  padding: 8px 10px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.7);
}

.coach-answer-line.is-tip {
  border-color: rgba(14, 165, 233, 0.22);
  background: rgba(14, 165, 233, 0.08);
}

.answer-index {
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  margin-top: 2px;
  border-radius: 999px;
  background: var(--text);
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}

.coach-references {
  display: grid;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(148, 163, 184, 0.2);
}

.coach-references-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-muted);
}

.coach-reference-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.coach-reference-card {
  display: grid;
  gap: 3px;
  max-width: 260px;
  padding: 9px 11px;
  border: 1px solid rgba(8, 122, 109, 0.18);
  border-radius: 12px;
  background: rgba(8, 122, 109, 0.07);
  color: var(--text);
  text-align: left;
  cursor: pointer;
}

.coach-reference-card:disabled {
  cursor: default;
  opacity: 0.72;
}

.coach-reference-type {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
  color: #087a6d;
}

.coach-reference-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 700;
}

.suggestion-wrap {
  margin-top: 12px;
  display: grid;
  gap: 8px;
}

.suggestion-title {
  font-size: 12px;
  color: var(--text-muted);
}

.suggestion-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.suggestion-chip {
  min-height: 36px;
}

.typing-card {
  width: fit-content;
  min-width: 220px;
  max-width: min(100%, 360px);
  background:
    radial-gradient(circle at top right, rgba(14, 165, 233, 0.1), transparent 42%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(244, 248, 255, 0.96));
}

.coach-skate-loader {
  position: relative;
  height: 42px;
  margin-bottom: 6px;
  overflow: hidden;
  border-radius: 14px;
  background:
    radial-gradient(circle at 20% 18%, rgba(183, 210, 90, 0.22), transparent 34%),
    linear-gradient(90deg, rgba(8, 122, 109, 0.08), rgba(14, 165, 233, 0.08));
}

.coach-loader-track {
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: 12px;
  height: 3px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.22);
}

.coach-loader-track::before {
  content: '';
  position: absolute;
  inset: 0;
  width: 46%;
  border-radius: inherit;
  background: linear-gradient(90deg, rgba(8, 122, 109, 0), rgba(8, 122, 109, 0.9), rgba(14, 165, 233, 0));
  animation: coachTrackSweep var(--skate-motion-duration) infinite cubic-bezier(0.16, 1, 0.3, 1);
}

.coach-loader-board {
  position: absolute;
  left: 14px;
  bottom: 0;
  width: 92px;
  height: 40px;
  transform-origin: 50% 90%;
  filter: drop-shadow(0 10px 10px rgba(18, 21, 19, 0.16));
  animation: coachBoardRide var(--skate-motion-duration) infinite cubic-bezier(0.16, 1, 0.3, 1);
}

.coach-loader-sketch {
  width: 100%;
  height: auto;
  --sketch-deck-fill: rgba(255, 255, 255, 0.98);
  --sketch-line: rgba(31, 41, 55, 0.84);
  --sketch-line-soft: rgba(31, 41, 55, 0.62);
}

.typing-label {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  margin-top: 8px;
  padding: 4px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #0369a1;
  background: rgba(14, 165, 233, 0.12);
}

.typing-copy {
  margin: 10px 0 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-soft);
}

.typing-track {
  position: relative;
  overflow: hidden;
  width: 100%;
  height: 6px;
  margin-top: 12px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.14);
}

.typing-track span {
  display: block;
  width: 34%;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, rgba(56, 189, 248, 0.12), rgba(56, 189, 248, 0.72), rgba(183, 210, 90, 0.48));
  animation: typingTrackSlide 1.8s infinite ease-in-out;
}

.typing-dots {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
}

.typing-dots span {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: rgba(18, 21, 19, 0.3);
  animation: dotPulse 1s infinite ease-in-out;
}

.typing-dots span:nth-child(2) {
  animation-delay: 0.15s;
}

.typing-dots span:nth-child(3) {
  animation-delay: 0.3s;
}

.composer {
  display: grid;
  gap: 10px;
  margin-top: auto;
  padding: 12px;
  border-top: 1px solid var(--line);
  border-radius: 18px;
  background:
    radial-gradient(circle at 14% 0%, rgba(8, 122, 109, 0.08), transparent 32%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.94));
}

.quick-strip {
  position: relative;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  min-height: 42px;
  padding-right: 112px;
  align-items: flex-start;
}

.quick-strip.is-refreshing .quick-pill {
  pointer-events: none;
}

.quick-pill {
  display: grid;
  justify-items: start;
  gap: 2px;
  min-height: 42px;
  max-width: 270px;
  padding-block: 7px;
  text-align: left;
}

.quick-pill span {
  overflow: hidden;
  max-width: 100%;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-pill-tag {
  color: var(--text-muted);
  font-size: 10px;
  font-weight: 800;
  line-height: 1;
}

.refresh-pill {
  position: absolute;
  top: 0;
  right: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 96px;
  min-height: 42px;
  z-index: 2;
}

.refresh-pill.is-refreshing :deep(svg) {
  animation: refreshSpin 0.36s ease;
}

.quick-suggestion-enter-active,
.quick-suggestion-leave-active {
  transition: opacity 0.26s var(--motion-ease), transform 0.26s var(--motion-ease), filter 0.26s ease;
}

.quick-suggestion-enter-from,
.quick-suggestion-leave-to {
  opacity: 0;
  transform: translateY(8px) scale(0.96);
  filter: blur(4px);
}

.quick-suggestion-move {
  transition: transform 0.26s var(--motion-ease);
}

.composer textarea {
  min-height: 132px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.96));
  border-radius: 14px;
}

.composer-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.composer-bar .muted {
  margin: 0;
}

.composer-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.send-btn {
  min-width: 140px;
  min-height: 44px;
}

.related-card {
  background:
    radial-gradient(circle at top right, rgba(8, 122, 109, 0.09), transparent 35%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(246, 249, 248, 0.96));
}

.related-block {
  display: grid;
  gap: 10px;
}

.related-label {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-muted);
}

.related-item {
  display: grid;
  gap: 6px;
  justify-items: start;
  text-align: left;
  padding: 14px 15px;
  border-radius: 16px;
}

.related-title {
  font-weight: 700;
  line-height: 1.5;
}

.related-meta {
  font-size: 12px;
  color: var(--text-muted);
}

.related-desc {
  font-size: 13px;
  color: var(--text-soft);
  line-height: 1.55;
}

@keyframes dotPulse {
  0%,
  80%,
  100% {
    transform: translateY(0);
    opacity: 0.35;
  }
  40% {
    transform: translateY(-2px);
    opacity: 1;
  }
}

@keyframes typingTrackSlide {
  0% {
    transform: translateX(-110%);
  }
  55%,
  100% {
    transform: translateX(160%);
  }
}

@keyframes messageRise {
  from {
    opacity: 0;
    transform: translateY(8px) scale(0.992);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes refreshSpin {
  to {
    rotate: 360deg;
  }
}

@keyframes coachTrackSweep {
  0% {
    transform: translateX(-120%);
    opacity: 0;
  }
  18%,
  72% {
    opacity: 1;
  }
  100% {
    transform: translateX(245%);
    opacity: 0;
  }
}

@keyframes coachBoardRide {
  0% {
    transform: translateX(0) rotate(-5deg);
    opacity: 0;
  }
  18% {
    opacity: 1;
  }
  60% {
    transform: translateX(148px) rotate(4deg);
    opacity: 1;
  }
  100% {
    transform: translateX(224px) rotate(-3deg);
    opacity: 0;
  }
}

@media (max-width: 1080px) {
  .coach-hero {
    grid-template-columns: 1fr;
  }

  .coach-layout {
    grid-template-columns: 300px minmax(0, 1fr);
  }
}

@media (max-width: 960px) {
  .mobile-switch {
    display: flex;
  }

  .coach-layout {
    grid-template-columns: 1fr;
  }

  .session-card,
  .chat-card {
    min-height: auto;
  }

  .chat-body {
    max-height: none;
  }
}

@media (max-width: 768px) {
  .coach-hero {
    padding: 16px;
  }

  .composer-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .composer {
    margin-inline: -2px;
    padding: 10px;
    border-radius: 14px;
  }

  .quick-strip {
    flex-wrap: nowrap;
    overflow-x: auto;
    padding-right: 0;
    padding-bottom: 3px;
    scrollbar-width: none;
  }

  .quick-strip::-webkit-scrollbar {
    display: none;
  }

  .quick-pill,
  .refresh-pill {
    flex: 0 0 auto;
    max-width: 240px;
  }

  .refresh-pill {
    position: sticky;
    right: 0;
    top: 0;
    min-width: 100px;
    box-shadow: -12px 0 18px rgba(248, 250, 252, 0.92);
  }

  .composer textarea {
    min-height: 108px;
  }

  .composer-actions {
    width: 100%;
    flex-direction: column;
  }

  .composer-actions button,
  .send-btn {
    width: 100%;
  }

  .message-row,
  .message-user {
    grid-template-columns: 34px minmax(0, 1fr);
    gap: 9px;
  }

  .message-user .avatar-chip,
  .message-user .message-card {
    order: initial;
  }

  .avatar-chip {
    width: 34px;
    height: 34px;
    border-radius: 11px;
  }

  .message-meta {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  .message-card {
    padding: 12px;
    border-radius: 15px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .coach-loader-track::before,
  .coach-loader-board,
  .typing-track span,
  .typing-dots span,
  .message-card,
  .refresh-pill.is-refreshing :deep(svg) {
    animation: none;
  }
}
</style>
