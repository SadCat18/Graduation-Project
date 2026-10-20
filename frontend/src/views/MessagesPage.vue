<template>
  <section ref="pageRoot" class="messages-page">
    <div class="messages-layout" :class="{ 'has-thread': selectedConversationId }">
      <aside ref="conversationList" class="conversation-list" aria-label="私信会话列表">
        <header class="messages-header">
          <div>
            <p class="eyebrow">MESSAGES</p>
            <h1>私信</h1>
          </div>
          <button
            class="icon-button"
            type="button"
            :disabled="loadingConversations"
            aria-label="刷新会话"
            title="刷新会话"
            @click="loadConversations({ keepSelection: true })"
          >
            <AppIcon name="refresh" :size="17" />
          </button>
        </header>

        <div class="conversation-tools">
          <label class="search-box">
            <AppIcon name="search" :size="17" />
            <input
              v-model="conversationKeyword"
              type="search"
              placeholder="搜索会话"
              @input="loadConversations({ keepSelection: true, resetPage: true })"
            />
          </label>
          <label class="unread-filter">
            <input
              v-model="unreadOnly"
              type="checkbox"
              @change="loadConversations({ keepSelection: true, resetPage: true })"
            />
            未读
          </label>
        </div>

        <div class="list-heading">
          <h2>全部会话</h2>
          <span>{{ conversationTotal }}</span>
        </div>

        <p v-if="conversationError" class="inline-error">{{ conversationError }}</p>
        <p v-if="loadingConversations && !conversations.length" class="muted list-status">会话加载中...</p>
        <div v-else-if="!conversations.length" class="empty-tip">
          暂无匹配会话。进入其他用户主页后可以发起私信。
        </div>

        <button
          v-for="conversation in conversations"
          :key="conversation.conversationId"
          type="button"
          class="conversation-item"
          :class="{ active: Number(selectedConversationId) === Number(conversation.conversationId) }"
          @click="selectConversation(conversation.conversationId)"
        >
          <span class="avatar-wrap">
            <img class="conversation-avatar" :src="avatarFor(conversation)" alt="" />
            <i v-if="conversation.unreadCount" aria-hidden="true"></i>
          </span>
          <span class="conversation-copy">
            <span class="conversation-topline">
              <strong>{{ conversation.otherUsername || '滑手' }}</strong>
              <small>{{ formatConversationTime(conversation.lastMessageTime) }}</small>
            </span>
            <span class="conversation-preview">{{ conversation.lastMessageContent || '还没有消息' }}</span>
          </span>
          <span v-if="conversation.unreadCount" class="unread-dot">{{ conversation.unreadCount }}</span>
        </button>

        <button
          v-if="hasMoreConversations"
          class="load-more"
          type="button"
          :disabled="loadingConversations"
          @click="loadMoreConversations"
        >
          加载更多会话
        </button>
      </aside>

      <section ref="threadPanel" class="message-thread" aria-label="私信对话">
        <div v-if="!selectedConversationId" class="thread-empty">
          <span class="thread-empty-icon"><AppIcon name="message" :size="26" /></span>
          <strong>选择一个会话</strong>
          <p>从左侧选择一位滑友，继续你们的对话。</p>
        </div>

        <template v-else>
          <div class="thread-header">
            <button class="thread-back icon-button" type="button" aria-label="返回会话列表" @click="router.push('/messages')">
              <AppIcon name="arrow-left" :size="16" />
            </button>
            <img class="thread-avatar" :src="avatarFor(activeConversation)" alt="" />
            <div class="thread-identity">
              <h2>{{ activeConversation?.otherUsername || '私信会话' }}</h2>
              <p>{{ activeConversation?.lastMessageTime ? `最近消息 ${formatConversationTime(activeConversation.lastMessageTime)}` : '开始新的对话' }}</p>
            </div>
            <button
              class="thread-refresh icon-button"
              type="button"
              :disabled="loadingMessages"
              aria-label="刷新当前对话"
              title="刷新当前对话"
              @click="loadMessages(selectedConversationId)"
            >
              <AppIcon name="refresh" :size="17" />
            </button>
          </div>

          <div ref="messageScroller" class="message-scroll" @scroll="handleMessageScroll">
            <button
              v-if="hasMoreMessages"
              class="load-older"
              type="button"
              :disabled="loadingMoreMessages"
              @click="loadOlderMessages"
            >
              {{ loadingMoreMessages ? '加载中...' : '加载更早消息' }}
            </button>
            <p v-if="loadingMessages" class="muted">消息加载中...</p>
            <p v-else-if="messageError" class="inline-error">{{ messageError }}</p>
            <div v-else-if="!messages.length" class="empty-tip">这段会话还没有消息。</div>
            <div v-if="messages.length && !loadingMessages && !messageError" class="date-divider">
              <span>今天</span>
            </div>
            <template v-if="!loadingMessages && !messageError">
              <div
                v-for="message in messages"
                :key="message.clientId || message.messageId"
                class="message-row"
                :class="{ mine: isMine(message), failed: message.deliveryStatus === 'failed' }"
              >
                <img v-if="!isMine(message)" class="message-avatar" :src="avatarFor(activeConversation)" alt="" />
                <div class="message-stack">
                  <div class="message-bubble">
                    <p>{{ message.content }}</p>
                  </div>
                  <span class="message-meta">
                    {{ formatMessageTime(message.createTime) }}
                    <em v-if="message.deliveryStatus === 'sending'">发送中...</em>
                    <em v-else-if="message.deliveryStatus === 'failed'">发送失败</em>
                    <em v-else-if="isMine(message)">已发送</em>
                  </span>
                  <button
                    v-if="message.deliveryStatus === 'failed'"
                    class="retry-link"
                    type="button"
                    :disabled="sendingMessage"
                    @click="retryMessage(message)"
                  >
                    重试
                  </button>
                </div>
              </div>
            </template>
            <button v-if="hasNewMessages" class="new-message-tip" type="button" @click="scrollToBottom()">
              有新消息
            </button>
          </div>

          <p v-if="sendError" class="composer-error">{{ sendError }}</p>
          <form class="composer" @submit.prevent="sendMessage">
            <label class="composer-input">
              <textarea
                v-model="messageInput"
                maxlength="1000"
                rows="1"
                placeholder="输入消息..."
                :disabled="sendingMessage"
                @keydown.ctrl.enter.prevent="sendMessage"
                @keydown.meta.enter.prevent="sendMessage"
              ></textarea>
              <span class="composer-meta">
                <small>Ctrl / Cmd + Enter 发送</small>
                <b>{{ remainingChars }} / 1000</b>
              </span>
            </label>
            <button ref="sendButton" class="btn-primary" type="submit" :disabled="sendDisabled">
              {{ sendingMessage ? '发送中...' : '发送' }}
            </button>
          </form>
        </template>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { gsap } from 'gsap'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { normalizeMediaUrl } from '../utils/url'

const route = useRoute()
const router = useRouter()
const defaultAvatar = 'https://api.dicebear.com/7.x/thumbs/svg?seed=skatehub-message'
const MESSAGE_PAGE_SIZE = 30
const CONVERSATION_PAGE_SIZE = 20
const POLL_INTERVAL_MS = 10000

const conversations = ref([])
const messages = ref([])
const selectedConversationId = ref(null)
const currentUserId = ref(null)
const loadingConversations = ref(false)
const loadingMessages = ref(false)
const loadingMoreMessages = ref(false)
const sendingMessage = ref(false)
const conversationError = ref('')
const messageError = ref('')
const sendError = ref('')
const messageInput = ref('')
const conversationKeyword = ref('')
const unreadOnly = ref(false)
const conversationPage = ref(1)
const conversationTotal = ref(0)
const hasMoreConversations = ref(false)
const nextBeforeMessageId = ref(null)
const hasMoreMessages = ref(false)
const hasNewMessages = ref(false)
const pageRoot = ref(null)
const conversationList = ref(null)
const threadPanel = ref(null)
const messageScroller = ref(null)
const sendButton = ref(null)
let pollTimer = null
let disposed = false
let threadEpoch = 0
let messageLoadSequence = 0
let conversationLoadSequence = 0
let conversationRequestSequence = 0
let pollInFlight = false
let pollFailureReported = false

function captureThread() { return { id: selectedConversationId.value, epoch: threadEpoch } }
function isCurrentThread(context) {
  return !disposed && context.epoch === threadEpoch && context.id === selectedConversationId.value
}
function changeThread(id) {
  ++threadEpoch
  ++messageLoadSequence
  selectedConversationId.value = id
  messages.value = []
  nextBeforeMessageId.value = null
  hasMoreMessages.value = false
  loadingMessages.value = false
  loadingMoreMessages.value = false
  sendingMessage.value = false
  messageError.value = ''
  sendError.value = ''
  hasNewMessages.value = false
}

const activeConversation = computed(() => {
  const selectedId = Number(selectedConversationId.value)
  return conversations.value.find(item => Number(item.conversationId) === selectedId) || null
})

const remainingChars = computed(() => messageInput.value.length)
const sendDisabled = computed(() => sendingMessage.value || !messageInput.value.trim() || messageInput.value.length > 1000)

function normalizeList(value) {
  if (Array.isArray(value)) return value
  if (Array.isArray(value?.list)) return value.list
  return []
}

function normalizeConversationPage(value) {
  return {
    list: normalizeList(value),
    total: Number(value?.total ?? normalizeList(value).length),
    totalPages: Number(value?.totalPages ?? 1)
  }
}

function normalizeMessagePage(value) {
  return {
    list: normalizeList(value),
    nextBeforeMessageId: value?.nextBeforeMessageId ?? null,
    hasMore: Boolean(value?.hasMore)
  }
}

function normalizeConversationId(value) {
  const id = Number(value)
  return Number.isFinite(id) && id > 0 ? id : null
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

function formatMessageTime(value) {
  return value ? String(value).replace('T', ' ').slice(11, 16) : ''
}

function formatConversationTime(value) {
  if (!value) return ''
  const normalized = String(value).replace('T', ' ')
  const now = new Date()
  const today = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0')
  ].join('-')
  return normalized.startsWith(today) ? normalized.slice(11, 16) : normalized.slice(5, 10)
}

function avatarFor(conversation) {
  return normalizeMediaUrl(conversation?.otherAvatar) || defaultAvatar
}

function prefersReducedMotion() {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true
}

function scopedElements(selector) {
  return pageRoot.value ? gsap.utils.toArray(selector, pageRoot.value) : []
}

function animatePageEnter() {
  if (prefersReducedMotion()) return
  const conversationItems = scopedElements('.conversation-item').slice(0, 8)
  const header = scopedElements('.messages-header')
  const timeline = gsap.timeline({ defaults: { ease: 'power2.out' } })

  timeline
    .from(header, { y: 12, opacity: 0, duration: 0.24 }, 0)
    .from(conversationList.value, { x: -14, opacity: 0, duration: 0.28 }, 0.04)
    .from(threadPanel.value, { y: 16, opacity: 0, duration: 0.32 }, 0.08)

  if (conversationItems.length) {
    timeline.from(conversationItems, { y: 10, opacity: 0, duration: 0.2, stagger: 0.035 }, 0.12)
  }
}

function animateThreadSwitch() {
  if (prefersReducedMotion() || !threadPanel.value) return
  gsap.fromTo(
    scopedElements('.thread-header, .message-scroll, .composer'),
    { y: 10, opacity: 0 },
    { y: 0, opacity: 1, duration: 0.24, stagger: 0.045, ease: 'power2.out', overwrite: 'auto' }
  )
}

function animateRecentMessages() {
  if (prefersReducedMotion()) return
  const recentMessages = scopedElements('.message-row').slice(-12)
  if (!recentMessages.length) return
  gsap.fromTo(
    recentMessages,
    { y: 12, scale: 0.985, opacity: 0 },
    { y: 0, scale: 1, opacity: 1, duration: 0.22, stagger: 0.025, ease: 'power2.out', overwrite: 'auto' }
  )
}

function animateUnreadBadges() {
  if (prefersReducedMotion()) return
  const badges = scopedElements('.unread-dot')
  if (!badges.length) return
  gsap.fromTo(
    badges,
    { scale: 0.75, opacity: 0.45 },
    { scale: 1, opacity: 1, duration: 0.22, ease: 'back.out(2)', overwrite: 'auto' }
  )
}

function animateSendPress() {
  if (prefersReducedMotion() || !sendButton.value) return
  gsap.fromTo(
    sendButton.value,
    { scale: 0.96 },
    { scale: 1, duration: 0.18, ease: 'back.out(2.2)', overwrite: 'auto' }
  )
}

function isMine(message) {
  if (currentUserId.value != null) {
    return Number(message.senderId) === Number(currentUserId.value)
  }
  return activeConversation.value
    ? Number(message.senderId) !== Number(activeConversation.value.otherUserId)
    : false
}

function latestVisibleMessageId() {
  const ids = messages.value
    .map(item => Number(item.messageId))
    .filter(id => Number.isFinite(id) && id > 0)
  return ids.length ? Math.max(...ids) : null
}

function isNearBottom() {
  const el = messageScroller.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < 96
}

function scrollToBottom(context = captureThread()) {
  nextTick(() => {
    if (!isCurrentThread(context)) return
    if (messageScroller.value) {
      messageScroller.value.scrollTop = messageScroller.value.scrollHeight
    }
    hasNewMessages.value = false
  })
}

function mergeMessages(incoming) {
  const existing = new Set(messages.value.map(item => String(item.messageId || item.clientId)))
  const fresh = incoming.filter(item => !existing.has(String(item.messageId || item.clientId)))
  if (!fresh.length) return 0
  messages.value = [...messages.value, ...fresh].sort((a, b) => Number(a.messageId || 0) - Number(b.messageId || 0))
  return fresh.length
}

function handleMessageScroll() {
  if (isNearBottom()) {
    hasNewMessages.value = false
  }
}

async function loadCurrentUser() {
  try {
    const user = await api.profile()
    if (!disposed) currentUserId.value = user?.userId ?? null
  } catch (_) {
    if (!disposed) currentUserId.value = null
  }
}

async function refreshConversations(options = {}) {
  const page = options.page || conversationPage.value
  const request = ++conversationRequestSequence
  const current = () => !disposed && request === conversationRequestSequence && (!options.guard || options.guard())
  let data
  try {
    data = await api.privateConversations({ page, size: CONVERSATION_PAGE_SIZE, keyword: conversationKeyword.value.trim(), unreadOnly: unreadOnly.value })
  } catch (error) {
    if (!current()) return false
    throw error
  }
  if (!current()) return false
  const result = normalizeConversationPage(data)
  conversationTotal.value = result.total
  hasMoreConversations.value = page < result.totalPages
  conversationPage.value = page
  conversations.value = options.append
    ? [...new Map([...conversations.value, ...result.list].map(item => [String(item.conversationId), item])).values()]
    : result.list
  await nextTick()
  if (!current()) return false
  animateUnreadBadges()
  return true
}

async function loadConversations(options = {}) {
  if (options.resetPage) conversationPage.value = 1
  const request = ++conversationLoadSequence
  const context = captureThread()
  const current = () => request === conversationLoadSequence && isCurrentThread(context)
  loadingConversations.value = true
  conversationError.value = ''
  try {
    if (!await refreshConversations({ page: conversationPage.value, guard: current }) || !current()) return
    const routeConversationId = normalizeConversationId(route.params.conversationId)
    const keptConversationId = options.keepSelection ? normalizeConversationId(selectedConversationId.value) : null
    const nextConversationId = routeConversationId || keptConversationId
    if (nextConversationId) await selectConversation(nextConversationId, { syncRoute: Boolean(routeConversationId), skipConversationRefresh: true })
    else changeThread(null)
  } catch (error) {
    if (current()) conversationError.value = error?.message || '会话加载失败，请稍后重试'
  } finally {
    if (!disposed && request === conversationLoadSequence) loadingConversations.value = false
  }
}

async function loadMoreConversations() {
  if (loadingConversations.value || !hasMoreConversations.value) return
  const request = ++conversationLoadSequence
  const current = () => !disposed && request === conversationLoadSequence
  const nextPage = conversationPage.value + 1
  loadingConversations.value = true
  conversationError.value = ''
  try {
    // The page cursor is committed by refreshConversations only after a successful response.
    await refreshConversations({ page: nextPage, append: true, guard: current })
  } catch (error) {
    if (current()) conversationError.value = error?.message || '更多会话加载失败'
  } finally {
    if (current()) loadingConversations.value = false
  }
}

async function selectConversation(conversationId, options = {}) {
  const normalizedId = normalizeConversationId(conversationId)
  if (!normalizedId || disposed) return
  if (normalizedId !== selectedConversationId.value) changeThread(normalizedId)
  messageError.value = ''
  sendError.value = ''
  hasNewMessages.value = false
  if (options.syncRoute !== false && String(route.params.conversationId || '') !== String(normalizedId)) router.push(`/messages/${normalizedId}`)
  await loadMessages(normalizedId)
}

async function markVisibleMessagesRead(context, current = () => isCurrentThread(context)) {
  if (!current()) return
  const lastReadMessageId = latestVisibleMessageId()
  if (lastReadMessageId) await api.readPrivateConversation(context.id, { lastReadMessageId })
}

async function loadMessages(conversationId) {
  const normalizedId = normalizeConversationId(conversationId)
  if (!normalizedId || normalizedId !== selectedConversationId.value || disposed) return
  const context = captureThread(), request = ++messageLoadSequence
  const current = () => isCurrentThread(context) && request === messageLoadSequence
  loadingMessages.value = true
  loadingMoreMessages.value = false
  messageError.value = ''
  try {
    const page = normalizeMessagePage(await api.privateMessages(normalizedId, { size: MESSAGE_PAGE_SIZE }))
    if (!current()) return
    messages.value = page.list
    nextBeforeMessageId.value = page.nextBeforeMessageId
    hasMoreMessages.value = page.hasMore
    await markVisibleMessagesRead(context, current)
    if (!current()) return
    if (!await refreshConversations({ guard: current }) || !current()) return
    scrollToBottom(context)
    animateThreadSwitch()
    await nextTick()
    if (current()) animateRecentMessages()
  } catch (error) {
    if (current()) messageError.value = error?.message || '消息加载失败，请稍后重试'
  } finally {
    if (current()) loadingMessages.value = false
  }
}

async function loadOlderMessages() {
  if (!selectedConversationId.value || !hasMoreMessages.value || loadingMoreMessages.value || loadingMessages.value) return
  const context = captureThread(), load = messageLoadSequence
  const current = () => isCurrentThread(context) && load === messageLoadSequence
  const scroller = messageScroller.value, previousHeight = scroller?.scrollHeight || 0
  loadingMoreMessages.value = true
  try {
    const page = normalizeMessagePage(await api.privateMessages(context.id, { beforeMessageId: nextBeforeMessageId.value, size: MESSAGE_PAGE_SIZE }))
    if (!current()) return
    messages.value = [...page.list, ...messages.value]
    nextBeforeMessageId.value = page.nextBeforeMessageId
    hasMoreMessages.value = page.hasMore
    await nextTick()
    if (current() && scroller) scroller.scrollTop = scroller.scrollHeight - previousHeight
  } catch (error) {
    if (current()) messageError.value = error?.message || '更早消息加载失败'
  } finally {
    if (current()) loadingMoreMessages.value = false
  }
}

function buildOptimisticMessage(content, existingMessage = null) {
  return {
    ...(existingMessage || {}),
    clientId: existingMessage?.clientId || `local-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    messageId: existingMessage?.messageId || null,
    conversationId: selectedConversationId.value,
    senderId: currentUserId.value,
    receiverId: activeConversation.value?.otherUserId,
    content,
    readStatus: '0',
    status: 'NORMAL',
    deliveryStatus: 'sending',
    createTime: existingMessage?.createTime || new Date().toISOString()
  }
}

async function sendOptimistic(content, existingMessage = null) {
  const context = captureThread()
  const current = () => isCurrentThread(context)
  const optimistic = buildOptimisticMessage(content, existingMessage), clientKey = optimistic.clientId
  if (existingMessage) messages.value = messages.value.map(item => item.clientId === clientKey ? optimistic : item)
  else messages.value.push(optimistic)
  scrollToBottom(context)
  sendingMessage.value = true
  sendError.value = ''
  try {
    animateSendPress()
    const result = await api.sendPrivateMessage(context.id, { content })
    if (!current()) return
    const sentMessage = { ...(result?.message || result), deliveryStatus: 'sent' }
    messages.value = messages.value.map(item => item.clientId === clientKey ? sentMessage : item)
    if (!await refreshConversations({ guard: current }) || !current()) return
    await nextTick()
    if (current()) animateRecentMessages()
  } catch (error) {
    if (!current()) return
    messages.value = messages.value.map(item => item.clientId === clientKey ? { ...item, deliveryStatus: 'failed' } : item)
    sendError.value = error?.message || '发送失败，请重试'
  } finally {
    if (current()) sendingMessage.value = false
  }
}

async function sendMessage() {
  const content = messageInput.value.trim()
  if (!selectedConversationId.value || !content || sendDisabled.value) return
  messageInput.value = ''
  await sendOptimistic(content)
}

async function retryMessage(message) {
  if (!selectedConversationId.value || sendingMessage.value) return
  await sendOptimistic(message.content, message)
}

async function pollForUpdates() {
  if (disposed || document.hidden || loadingMessages.value || loadingConversations.value || pollInFlight) return
  const context = captureThread(), load = messageLoadSequence
  const current = () => isCurrentThread(context) && load === messageLoadSequence
  pollInFlight = true
  try {
    if (!await refreshConversations({ guard: current }) || !current()) return
    if (context.id) {
      const wasAtBottom = isNearBottom()
      const page = normalizeMessagePage(await api.privateMessages(context.id, { size: MESSAGE_PAGE_SIZE }))
      if (!current()) return
      const added = mergeMessages(page.list)
      if (added) {
        await nextTick()
        if (!current()) return
        animateRecentMessages()
        if (wasAtBottom) {
          await markVisibleMessagesRead(context, current)
          if (!current()) return
          scrollToBottom(context)
          if (!await refreshConversations({ guard: current }) || !current()) return
        } else hasNewMessages.value = true
      }
    }
    if (current()) pollFailureReported = false
  } catch (_) {
    if (current() && !pollFailureReported) {
      pollFailureReported = true
      console.warn('[Messages] 轮询失败，将在下一周期重试。')
    }
  } finally {
    pollInFlight = false
  }
}

function startPolling() {
  stopPolling()
  pollTimer = window.setInterval(pollForUpdates, POLL_INTERVAL_MS)
}

function stopPolling() {
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

function handleVisibilityChange() {
  if (document.hidden) {
    stopPolling()
  } else {
    startPolling()
    pollForUpdates()
  }
}

watch(
  () => route.params.conversationId,
  async (conversationId) => {
    const normalizedId = normalizeConversationId(conversationId)
    if (normalizedId) {
      if (normalizedId !== selectedConversationId.value) await selectConversation(normalizedId, { syncRoute: false })
    } else {
      changeThread(null)
    }
  }
)

onMounted(async () => {
  await loadCurrentUser()
  if (disposed) return
  await loadConversations()
  await nextTick()
  if (disposed) return
  animatePageEnter()
  document.addEventListener('visibilitychange', handleVisibilityChange)
  startPolling()
})

onBeforeUnmount(() => {
  disposed = true
  ++threadEpoch
  ++conversationLoadSequence
  ++conversationRequestSequence
  stopPolling()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  const targets = scopedElements('.messages-header, .conversation-list, .message-thread, .conversation-item, .thread-header, .message-scroll, .composer, .message-row, .unread-dot')
  if (sendButton.value) targets.push(sendButton.value)
  gsap.killTweensOf(targets)
})
</script>

<style scoped>
.messages-page {
  width: 100%;
  max-width: none;
  margin: 0;
  padding: 0;
}

.messages-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 2px 2px 18px;
}

.messages-header h1,
.list-heading h2,
.thread-header h2 {
  margin: 0;
  color: var(--text);
}

.messages-header h1 {
  font-size: 27px;
  line-height: 1.15;
  letter-spacing: -0.03em;
}

.eyebrow {
  margin: 0 0 4px;
  color: var(--accent);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: .14em;
}

.messages-layout {
  display: grid;
  grid-template-columns: clamp(360px, 30vw, 430px) minmax(0, 1fr);
  height: calc(100vh - 65px);
  min-height: 650px;
  border: 0;
  border-bottom: 1px solid var(--line);
  border-radius: 0;
  overflow: hidden;
  background: var(--surface);
  box-shadow: none;
}

.conversation-list {
  display: flex;
  flex-direction: column;
  min-width: 0;
  border-right: 1px solid var(--line);
  background: #fbfcfa;
  padding: 28px 24px 20px;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--line-strong) transparent;
}

.list-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 10px 4px 8px;
}

.list-heading h2 {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: .06em;
  text-transform: uppercase;
}

.list-heading span {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 700;
}

.unread-dot {
  display: inline-grid;
  place-items: center;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--accent);
  color: #fff;
  font-size: 11px;
  font-weight: 800;
}

.conversation-tools {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  margin-bottom: 4px;
}

.search-box,
.unread-filter {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: 11px;
  background: #fff;
  color: var(--text-muted);
  transition: border-color .18s ease, box-shadow .18s ease;
}

.search-box {
  padding: 0 12px;
}

.search-box:focus-within {
  border-color: rgba(8, 122, 109, .42);
  box-shadow: 0 0 0 3px rgba(8, 122, 109, .08);
}

.search-box input {
  width: 100%;
  min-width: 0;
  min-height: 44px;
  border: 0;
  outline: 0;
  background: transparent;
  font: inherit;
  font-size: 14px;
  box-shadow: none;
}

.unread-filter {
  min-height: 44px;
  padding: 0 12px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.unread-filter:has(input:checked) {
  border-color: rgba(8, 122, 109, .32);
  background: rgba(8, 122, 109, .08);
  color: var(--accent);
}

.unread-filter input {
  width: 14px;
  height: 14px;
  margin: 0;
  accent-color: var(--accent);
}

.icon-button {
  display: inline-grid;
  place-items: center;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 11px;
  background: #fff;
  color: var(--text-soft);
  box-shadow: none;
}

.icon-button::before,
.conversation-item::before,
.retry-link::before {
  display: none;
}

.conversation-item {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  width: calc(100% + 48px);
  min-height: 76px;
  margin: 0 0 0 -24px;
  padding: 12px 24px;
  border: 0;
  border-top: 1px solid var(--line);
  border-radius: 0;
  background: transparent;
  color: var(--text);
  box-shadow: none;
  text-align: left;
  cursor: pointer;
  transition: background-color .18s ease, color .18s ease;
}

.conversation-item:last-of-type {
  border-bottom: 1px solid var(--line);
}

.conversation-item:hover,
.conversation-item.active {
  border-color: var(--line);
  background: rgba(8, 122, 109, .07);
  box-shadow: none;
  transform: none;
}

.conversation-item.active::after {
  content: '';
  position: absolute;
  left: 0;
  top: 12px;
  bottom: 12px;
  width: 3px;
  border-radius: 0 4px 4px 0;
  background: var(--accent);
}

.avatar-wrap {
  position: relative;
  display: inline-flex;
}

.avatar-wrap i {
  position: absolute;
  right: -1px;
  bottom: 1px;
  width: 10px;
  height: 10px;
  border: 2px solid #fbfcfa;
  border-radius: 50%;
  background: var(--accent-warm);
}

.conversation-avatar,
.thread-avatar {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  border: 1px solid var(--line);
  object-fit: cover;
  background: #fff;
}

.conversation-copy {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.conversation-topline {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}

.conversation-topline strong,
.conversation-preview {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-topline strong {
  font-size: 14px;
  font-weight: 800;
}

.conversation-topline small,
.conversation-preview,
.muted,
.thread-header p,
.thread-empty p {
  color: var(--text-muted);
}

.conversation-topline small {
  flex: 0 0 auto;
  font-size: 11px;
}

.conversation-preview {
  font-size: 13px;
}

.list-status {
  padding: 12px 4px;
}

.message-thread {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto auto;
  min-width: 0;
  background: #fff;
}

.thread-empty {
  grid-row: 1 / -1;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  min-height: 420px;
  color: var(--text);
  text-align: center;
}

.thread-empty-icon {
  display: inline-grid;
  place-items: center;
  width: 54px;
  height: 54px;
  margin-bottom: 4px;
  border-radius: 50%;
  background: rgba(8, 122, 109, .1);
  color: var(--accent);
}

.thread-empty strong {
  font-size: 17px;
}

.thread-empty p {
  margin: 0;
  font-size: 14px;
}

.thread-header {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
  min-height: 108px;
  padding: 20px 30px;
  border-bottom: 1px solid var(--line);
  background: rgba(255, 255, 255, .96);
}

.thread-identity {
  min-width: 0;
  flex: 1;
}

.thread-header h2 {
  overflow: hidden;
  font-size: 17px;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.thread-header p {
  margin: 3px 0 0;
  font-size: 12px;
}

.thread-back {
  display: none;
}

.message-scroll {
  position: relative;
  display: grid;
  align-content: start;
  gap: 14px;
  min-height: 0;
  overflow: auto;
  padding: 22px clamp(30px, 5vw, 76px) 30px;
  background: #fdfdfb;
  scrollbar-width: thin;
  scrollbar-color: var(--line-strong) transparent;
}

.date-divider {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  margin: 2px 0 8px;
  color: var(--text-muted);
  font-size: 12px;
}

.date-divider::before,
.date-divider::after {
  content: '';
  height: 1px;
  flex: 1;
  background: var(--line);
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
}

.message-row.mine {
  justify-content: flex-end;
}

.message-avatar {
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  border: 1px solid var(--line);
  border-radius: 50%;
  object-fit: cover;
  background: #fff;
}

.message-stack {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: min(72%, 600px);
}

.message-row.mine .message-stack {
  align-items: flex-end;
}

.message-bubble {
  max-width: 100%;
  padding: 11px 14px;
  border: 0;
  border-radius: 5px 14px 14px 14px;
  background: #f1f3f0;
  color: var(--text);
  box-shadow: none;
}

.message-row.mine .message-bubble {
  border-radius: 14px 5px 14px 14px;
  background: var(--accent);
  color: #fff;
}

.message-row.failed .message-bubble {
  outline: 1px solid rgba(185, 28, 28, .28);
  background: #fff7f5;
  color: var(--text);
}

.message-bubble p {
  margin: 0;
  font-size: 14px;
  line-height: 1.65;
  word-break: break-word;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 11px;
}

.message-meta em {
  color: inherit;
  font-style: normal;
  font-weight: 700;
}

.message-row.failed .message-meta em {
  color: var(--danger);
}

.retry-link {
  min-height: auto;
  margin-top: 3px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--danger);
  box-shadow: none;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
}

.composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 12px;
  padding: 16px 30px 18px;
  border-top: 1px solid var(--line);
  background: #fff;
}

.composer-input {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.composer textarea {
  min-width: 0;
  min-height: 48px;
  max-height: 132px;
  resize: vertical;
  border: 1px solid var(--line);
  border-radius: 11px;
  padding: 12px 14px;
  font: inherit;
  font-size: 14px;
  line-height: 1.5;
}

.composer-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-muted);
  font-size: 12px;
}

.composer-meta small,
.composer-meta b {
  font-size: inherit;
}

.composer-meta b {
  font-weight: 700;
}

.composer-error {
  margin: 0;
  padding: 9px 30px 0;
  color: var(--danger);
  background: #fff;
  font-size: 13px;
  font-weight: 700;
}

.composer .btn-primary,
.load-more,
.load-older,
.new-message-tip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 44px;
  padding: 10px 18px;
  border: 1px solid var(--accent);
  border-radius: 11px;
  cursor: pointer;
  font-weight: 800;
}

.composer .btn-primary,
.new-message-tip {
  background: var(--accent);
  color: #fff;
}

.load-more,
.load-older {
  background: #fff;
  color: var(--text-soft);
}

.load-more,
.load-older {
  width: 100%;
  min-height: 36px;
  margin: 10px 0 0;
  border-color: var(--line);
  box-shadow: none;
  font-size: 13px;
}

.load-older {
  justify-self: center;
  width: auto;
  margin: 0 auto 4px;
  padding: 7px 13px;
}

.new-message-tip {
  position: sticky;
  bottom: 8px;
  justify-self: center;
  min-height: 34px;
  padding: 7px 12px;
  box-shadow: 0 10px 24px rgba(8, 122, 109, .2);
}

.btn-primary:disabled,
.load-more:disabled,
.load-older:disabled,
.retry-link:disabled {
  cursor: not-allowed;
  opacity: .55;
}

.empty-tip,
.inline-error {
  border: 1px dashed var(--line-strong);
  border-radius: 11px;
  padding: 14px;
  color: var(--text-muted);
  background: rgba(255, 255, 255, .7);
  font-size: 13px;
}

.inline-error {
  border-color: rgba(220, 38, 38, .28);
  color: var(--danger);
  background: rgba(254, 242, 242, .78);
}

@media (max-width: 980px) {
  .messages-page {
    padding: 0 0 108px;
  }

  .messages-layout {
    grid-template-columns: minmax(300px, 340px) minmax(0, 1fr);
  }

  .message-scroll {
    padding-inline: 22px;
  }
}

@media (max-width: 760px) {
  .messages-page {
    padding: 12px 10px 108px;
  }

  .messages-layout {
    display: block;
    height: calc(100vh - 156px);
    min-height: 560px;
    border-radius: 14px;
  }

  .conversation-list {
    height: 100%;
    border-right: 0;
    padding: 18px 14px;
  }

  .conversation-item {
    width: calc(100% + 28px);
    margin-left: -14px;
    padding-inline: 14px;
  }

  .messages-layout.has-thread .conversation-list {
    display: none;
  }

  .messages-layout:not(.has-thread) .message-thread {
    display: none;
  }

  .thread-back {
    display: inline-grid;
    place-items: center;
  }

  .message-thread {
    height: 100%;
    min-height: 0;
  }

  .thread-header {
    min-height: 72px;
    padding: 12px 14px;
  }

  .thread-avatar {
    width: 40px;
    height: 40px;
  }

  .message-scroll {
    padding: 16px 14px 22px;
  }

  .message-stack {
    max-width: 84%;
  }

  .composer {
    gap: 8px;
    padding: 12px;
  }

  .composer-meta small {
    display: none;
  }

  .composer-meta {
    justify-content: flex-end;
  }

  .composer .btn-primary {
    min-width: 66px;
    padding-inline: 14px;
  }

  .conversation-tools {
    grid-template-columns: minmax(0, 1fr) auto;
  }
}

@media (max-width: 420px) {
  .unread-filter {
    padding-inline: 10px;
  }

  .message-avatar {
    display: none;
  }

  .message-stack {
    max-width: 88%;
  }
}
</style>
