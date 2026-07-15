<template>
  <section ref="pageRoot" class="messages-page">
    <header class="messages-header">
      <div>
        <p class="eyebrow">MESSAGES</p>
        <h1>私信</h1>
      </div>
      <button class="btn-soft" type="button" :disabled="loadingConversations" @click="loadConversations({ keepSelection: true })">
        <AppIcon name="refresh" :size="15" />
        刷新
      </button>
    </header>

    <div class="messages-layout" :class="{ 'has-thread': selectedConversationId }">
      <aside ref="conversationList" class="conversation-list" aria-label="私信会话列表">
        <div class="list-heading">
          <h2>会话</h2>
          <span>{{ conversationTotal }}</span>
        </div>

        <div class="conversation-tools">
          <label class="search-box">
            <AppIcon name="search" :size="15" />
            <input
              v-model="conversationKeyword"
              type="search"
              placeholder="搜索用户名或最近消息"
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

        <p v-if="conversationError" class="inline-error">{{ conversationError }}</p>
        <p v-if="loadingConversations && !conversations.length" class="muted">加载中...</p>
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
          <img class="conversation-avatar" :src="avatarFor(conversation)" alt="" />
          <span class="conversation-copy">
            <span class="conversation-topline">
              <strong>{{ conversation.otherUsername || '滑手' }}</strong>
              <small>{{ formatTime(conversation.lastMessageTime) }}</small>
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
          <AppIcon name="message" :size="28" />
          <strong>选择一个会话</strong>
          <p>私信会以独立对话页呈现，方便连续沟通。</p>
        </div>

        <template v-else>
          <div class="thread-header">
            <button class="thread-back" type="button" @click="router.push('/messages')">
              <AppIcon name="arrow-left" :size="16" />
            </button>
            <img class="thread-avatar" :src="avatarFor(activeConversation)" alt="" />
            <div>
              <h2>{{ activeConversation?.otherUsername || '私信会话' }}</h2>
              <p>{{ activeConversation?.lastMessageTime ? `最近消息 ${formatTime(activeConversation.lastMessageTime)}` : '开始新的对话' }}</p>
            </div>
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
            <div
              v-for="message in messages"
              :key="message.clientId || message.messageId"
              class="message-row"
              :class="{ mine: isMine(message), failed: message.deliveryStatus === 'failed' }"
            >
              <div class="message-bubble">
                <p>{{ message.content }}</p>
                <span>
                  {{ formatTime(message.createTime) }}
                  <em v-if="message.deliveryStatus === 'sending'">发送中</em>
                  <em v-else-if="message.deliveryStatus === 'failed'">发送失败</em>
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
            <button v-if="hasNewMessages" class="new-message-tip" type="button" @click="scrollToBottom">
              有新消息
            </button>
          </div>

          <p v-if="sendError" class="composer-error">{{ sendError }}</p>
          <form class="composer" @submit.prevent="sendMessage">
            <label class="composer-input">
              <textarea
                v-model="messageInput"
                maxlength="1000"
                rows="2"
                placeholder="输入私信内容，Ctrl / Cmd + Enter 发送"
                :disabled="sendingMessage"
                @keydown.ctrl.enter.prevent="sendMessage"
                @keydown.meta.enter.prevent="sendMessage"
              ></textarea>
              <span>{{ remainingChars }} / 1000</span>
            </label>
            <button ref="sendButton" class="btn-primary" type="submit" :disabled="sendDisabled">
              <AppIcon name="message" :size="15" />
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

function scrollToBottom() {
  nextTick(() => {
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
    currentUserId.value = user?.userId ?? null
  } catch (_) {
    currentUserId.value = null
  }
}

async function refreshConversations(options = {}) {
  const page = options.page || conversationPage.value
  const result = normalizeConversationPage(await api.privateConversations({
    page,
    size: CONVERSATION_PAGE_SIZE,
    keyword: conversationKeyword.value.trim(),
    unreadOnly: unreadOnly.value
  }))
  conversationTotal.value = result.total
  hasMoreConversations.value = page < result.totalPages
  conversations.value = options.append ? [...conversations.value, ...result.list] : result.list
  await nextTick()
  animateUnreadBadges()
}

async function loadConversations(options = {}) {
  if (options.resetPage) {
    conversationPage.value = 1
  }
  loadingConversations.value = true
  conversationError.value = ''
  try {
    await refreshConversations({ page: conversationPage.value })
    const routeConversationId = normalizeConversationId(route.params.conversationId)
    const keptConversationId = options.keepSelection ? normalizeConversationId(selectedConversationId.value) : null
    const nextConversationId = routeConversationId || keptConversationId
    if (nextConversationId) {
      await selectConversation(nextConversationId, { syncRoute: Boolean(routeConversationId), skipConversationRefresh: true })
    } else {
      selectedConversationId.value = null
      messages.value = []
    }
  } catch (error) {
    conversationError.value = error?.message || '会话加载失败，请稍后重试'
  } finally {
    loadingConversations.value = false
  }
}

async function loadMoreConversations() {
  if (loadingConversations.value || !hasMoreConversations.value) return
  loadingConversations.value = true
  try {
    conversationPage.value += 1
    await refreshConversations({ page: conversationPage.value, append: true })
  } catch (error) {
    conversationError.value = error?.message || '更多会话加载失败'
  } finally {
    loadingConversations.value = false
  }
}

async function selectConversation(conversationId, options = {}) {
  const normalizedId = normalizeConversationId(conversationId)
  if (!normalizedId) return
  selectedConversationId.value = normalizedId
  messageError.value = ''
  sendError.value = ''
  hasNewMessages.value = false

  if (options.syncRoute !== false && String(route.params.conversationId || '') !== String(normalizedId)) {
    router.push(`/messages/${normalizedId}`)
  }

  await loadMessages(normalizedId)
}

async function markVisibleMessagesRead(conversationId) {
  const lastReadMessageId = latestVisibleMessageId()
  if (!lastReadMessageId) return
  await api.readPrivateConversation(conversationId, { lastReadMessageId })
}

async function loadMessages(conversationId) {
  const normalizedId = normalizeConversationId(conversationId)
  if (!normalizedId) return
  loadingMessages.value = true
  messageError.value = ''
  try {
    const page = normalizeMessagePage(await api.privateMessages(normalizedId, { size: MESSAGE_PAGE_SIZE }))
    messages.value = page.list
    nextBeforeMessageId.value = page.nextBeforeMessageId
    hasMoreMessages.value = page.hasMore
    await markVisibleMessagesRead(normalizedId)
    await refreshConversations()
    scrollToBottom()
    animateThreadSwitch()
    await nextTick()
    animateRecentMessages()
  } catch (error) {
    messageError.value = error?.message || '消息加载失败，请稍后重试'
  } finally {
    loadingMessages.value = false
  }
}

async function loadOlderMessages() {
  if (!selectedConversationId.value || !hasMoreMessages.value || loadingMoreMessages.value) return
  const scroller = messageScroller.value
  const previousHeight = scroller?.scrollHeight || 0
  loadingMoreMessages.value = true
  try {
    const page = normalizeMessagePage(await api.privateMessages(selectedConversationId.value, {
      beforeMessageId: nextBeforeMessageId.value,
      size: MESSAGE_PAGE_SIZE
    }))
    messages.value = [...page.list, ...messages.value]
    nextBeforeMessageId.value = page.nextBeforeMessageId
    hasMoreMessages.value = page.hasMore
    await nextTick()
    if (scroller) {
      scroller.scrollTop = scroller.scrollHeight - previousHeight
    }
  } catch (error) {
    messageError.value = error?.message || '更早消息加载失败'
  } finally {
    loadingMoreMessages.value = false
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
  const optimistic = buildOptimisticMessage(content, existingMessage)
  const clientKey = optimistic.clientId
  if (existingMessage) {
    messages.value = messages.value.map(item => item.clientId === clientKey ? optimistic : item)
  } else {
    messages.value.push(optimistic)
  }
  scrollToBottom()
  sendingMessage.value = true
  sendError.value = ''
  try {
    animateSendPress()
    const result = await api.sendPrivateMessage(selectedConversationId.value, { content })
    const sentMessage = { ...(result?.message || result), deliveryStatus: 'sent' }
    messages.value = messages.value.map(item => item.clientId === clientKey ? sentMessage : item)
    await refreshConversations()
    await nextTick()
    animateRecentMessages()
  } catch (error) {
    messages.value = messages.value.map(item => item.clientId === clientKey
      ? { ...item, deliveryStatus: 'failed' }
      : item)
    sendError.value = error?.message || '发送失败，请重试'
  } finally {
    sendingMessage.value = false
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
  if (document.hidden || loadingMessages.value || loadingConversations.value) return
  try {
    await refreshConversations()
    if (!selectedConversationId.value) return
    const wasAtBottom = isNearBottom()
    const page = normalizeMessagePage(await api.privateMessages(selectedConversationId.value, { size: MESSAGE_PAGE_SIZE }))
    const added = mergeMessages(page.list)
    if (added) {
      await nextTick()
      animateRecentMessages()
      if (wasAtBottom) {
        await markVisibleMessagesRead(selectedConversationId.value)
        scrollToBottom()
        await refreshConversations()
      } else {
        hasNewMessages.value = true
      }
    }
  } catch (_) {
    // Polling is best-effort; manual refresh keeps the explicit recovery path.
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
      await selectConversation(normalizedId, { syncRoute: false })
    } else {
      selectedConversationId.value = null
      messages.value = []
      hasNewMessages.value = false
    }
  }
)

onMounted(async () => {
  await loadCurrentUser()
  await loadConversations()
  await nextTick()
  animatePageEnter()
  document.addEventListener('visibilitychange', handleVisibilityChange)
  startPolling()
})

onBeforeUnmount(() => {
  stopPolling()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  const targets = scopedElements('.messages-header, .conversation-list, .message-thread, .conversation-item, .thread-header, .message-scroll, .composer, .message-row, .unread-dot')
  if (sendButton.value) targets.push(sendButton.value)
  gsap.killTweensOf(targets)
})
</script>

<style scoped>
.messages-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 26px 18px 56px;
}

.messages-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}

.messages-header h1,
.list-heading h2,
.thread-header h2 {
  margin: 0;
  color: #111827;
}

.eyebrow {
  margin: 0 0 6px;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: .08em;
}

.messages-layout {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  min-height: min(720px, calc(100vh - 190px));
  border: 1px solid rgba(15, 23, 42, .1);
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 18px 45px rgba(15, 23, 42, .08);
}

.conversation-list {
  min-width: 0;
  border-right: 1px solid rgba(15, 23, 42, .1);
  background:
    linear-gradient(180deg, rgba(248, 250, 252, .95), rgba(255, 255, 255, .96)),
    repeating-linear-gradient(135deg, rgba(15, 23, 42, .035) 0 1px, transparent 1px 13px);
  padding: 14px;
  overflow: auto;
}

.list-heading,
.thread-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.list-heading {
  margin-bottom: 12px;
}

.list-heading span,
.unread-dot {
  display: inline-grid;
  place-items: center;
  min-width: 24px;
  height: 24px;
  padding: 0 7px;
  border-radius: 999px;
  background: #111827;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}

.conversation-tools {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  margin-bottom: 10px;
}

.search-box,
.unread-filter {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  border: 1px solid rgba(15, 23, 42, .12);
  border-radius: 8px;
  background: rgba(255, 255, 255, .84);
  color: #475569;
}

.search-box {
  padding: 0 10px;
}

.search-box input {
  width: 100%;
  min-width: 0;
  min-height: 38px;
  border: 0;
  outline: 0;
  background: transparent;
  font: inherit;
}

.unread-filter {
  min-height: 38px;
  padding: 0 10px;
  font-size: 13px;
  font-weight: 800;
}

.conversation-item {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 72px;
  margin-bottom: 8px;
  padding: 10px;
  border: 1px solid rgba(15, 23, 42, .1);
  border-radius: 8px;
  background: rgba(255, 255, 255, .86);
  color: #111827;
  text-align: left;
  cursor: pointer;
  transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease;
}

.conversation-item:hover,
.conversation-item.active {
  border-color: rgba(17, 24, 39, .34);
  box-shadow: 0 10px 24px rgba(15, 23, 42, .08);
  transform: translateY(-1px);
}

.conversation-avatar,
.thread-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: 1px solid rgba(15, 23, 42, .12);
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

.conversation-topline small,
.conversation-preview,
.muted,
.thread-header p,
.thread-empty p {
  color: #64748b;
}

.message-thread {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto auto;
  min-width: 0;
  background:
    radial-gradient(circle at 100% 0%, rgba(20, 184, 166, .07), transparent 32%),
    #f8fafc;
}

.thread-empty {
  grid-row: 1 / -1;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  min-height: 420px;
  color: #111827;
  text-align: center;
}

.thread-empty p {
  margin: 0;
}

.thread-header {
  justify-content: flex-start;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(15, 23, 42, .1);
  background: rgba(255, 255, 255, .88);
}

.thread-header p {
  margin: 4px 0 0;
  font-size: 13px;
}

.thread-back {
  display: none;
  width: 36px;
  height: 36px;
  border: 1px solid rgba(15, 23, 42, .12);
  border-radius: 8px;
  background: #fff;
}

.message-scroll {
  position: relative;
  display: grid;
  align-content: start;
  gap: 10px;
  min-height: 0;
  overflow: auto;
  padding: 18px;
}

.message-row {
  display: flex;
}

.message-row.mine {
  justify-content: flex-end;
}

.message-bubble {
  max-width: min(72%, 560px);
  padding: 10px 12px;
  border: 1px solid rgba(15, 23, 42, .1);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 18px rgba(15, 23, 42, .06);
}

.message-row.mine .message-bubble {
  background: #111827;
  color: #fff;
}

.message-row.failed .message-bubble {
  border-color: rgba(220, 38, 38, .34);
  background: #fff7f7;
  color: #111827;
}

.message-bubble p {
  margin: 0;
  line-height: 1.65;
  word-break: break-word;
}

.message-bubble span {
  display: flex;
  gap: 8px;
  margin-top: 5px;
  color: #94a3b8;
  font-size: 12px;
}

.message-bubble em {
  font-style: normal;
  font-weight: 800;
}

.retry-link {
  margin-top: 6px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #dc2626;
  cursor: pointer;
  font-weight: 800;
}

.composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  padding: 12px 16px 14px;
  border-top: 1px solid rgba(15, 23, 42, .1);
  background: rgba(255, 255, 255, .92);
}

.composer-input {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.composer textarea {
  min-width: 0;
  min-height: 52px;
  max-height: 150px;
  resize: vertical;
  border: 1px solid rgba(15, 23, 42, .14);
  border-radius: 8px;
  padding: 9px 12px;
  font: inherit;
  line-height: 1.5;
}

.composer-input span {
  justify-self: end;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.composer-error {
  margin: 0;
  padding: 9px 16px 0;
  color: #b91c1c;
  background: rgba(255, 255, 255, .92);
  font-size: 13px;
  font-weight: 700;
}

.btn-primary,
.btn-soft,
.load-more,
.load-older,
.new-message-tip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 40px;
  padding: 9px 13px;
  border: 1px solid #111827;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 800;
}

.btn-primary,
.new-message-tip {
  background: #111827;
  color: #fff;
}

.btn-soft,
.load-more,
.load-older {
  background: #fff;
  color: #111827;
}

.load-more,
.load-older {
  width: 100%;
  min-height: 36px;
  margin: 6px 0;
  border-color: rgba(15, 23, 42, .14);
  font-size: 13px;
}

.new-message-tip {
  position: sticky;
  bottom: 8px;
  justify-self: center;
  min-height: 34px;
  padding: 7px 12px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, .2);
}

.btn-primary:disabled,
.btn-soft:disabled,
.load-more:disabled,
.load-older:disabled,
.retry-link:disabled {
  cursor: not-allowed;
  opacity: .55;
}

.empty-tip,
.inline-error {
  border: 1px dashed rgba(15, 23, 42, .18);
  border-radius: 8px;
  padding: 14px;
  color: #64748b;
  background: rgba(255, 255, 255, .62);
}

.inline-error {
  border-color: rgba(220, 38, 38, .28);
  color: #b91c1c;
  background: rgba(254, 242, 242, .78);
}

@media (max-width: 760px) {
  .messages-page {
    padding: 18px 12px 92px;
  }

  .messages-layout {
    display: block;
    min-height: calc(100vh - 210px);
  }

  .conversation-list {
    border-right: 0;
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
    min-height: calc(100vh - 210px);
  }

  .message-bubble {
    max-width: 88%;
  }

  .composer,
  .conversation-tools {
    grid-template-columns: 1fr;
  }
}
</style>
