<template>
  <section class="messages-page">
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
      <aside class="conversation-list" aria-label="私信会话列表">
        <div class="list-heading">
          <h2>会话</h2>
          <span>{{ conversations.length }}</span>
        </div>

        <p v-if="loadingConversations" class="muted">加载中...</p>
        <div v-else-if="!conversations.length" class="empty-tip">
          暂无私信会话。进入其他用户主页后可以发起私信。
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
      </aside>

      <section class="message-thread" aria-label="私信对话">
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

          <div ref="messageScroller" class="message-scroll">
            <p v-if="loadingMessages" class="muted">消息加载中...</p>
            <div v-else-if="!messages.length" class="empty-tip">这段会话还没有消息。</div>
            <div
              v-for="message in messages"
              :key="message.messageId"
              class="message-row"
              :class="{ mine: isMine(message) }"
            >
              <div class="message-bubble">
                <p>{{ message.content }}</p>
                <span>{{ formatTime(message.createTime) }}</span>
              </div>
            </div>
          </div>

          <form class="composer" @submit.prevent="sendMessage">
            <input
              v-model="messageInput"
              type="text"
              maxlength="500"
              placeholder="输入私信内容"
              :disabled="sendingMessage"
            />
            <button class="btn-primary" type="submit" :disabled="sendingMessage || !messageInput.trim()">
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
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { normalizeMediaUrl } from '../utils/url'

const route = useRoute()
const router = useRouter()
const defaultAvatar = 'https://api.dicebear.com/7.x/thumbs/svg?seed=skatehub-message'

const conversations = ref([])
const messages = ref([])
const selectedConversationId = ref(null)
const currentUserId = ref(null)
const loadingConversations = ref(false)
const loadingMessages = ref(false)
const sendingMessage = ref(false)
const messageInput = ref('')
const messageScroller = ref(null)

const activeConversation = computed(() => {
  const selectedId = Number(selectedConversationId.value)
  return conversations.value.find(item => Number(item.conversationId) === selectedId) || null
})

function normalizeList(value) {
  if (Array.isArray(value)) return value
  if (Array.isArray(value?.list)) return value.list
  return []
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

function isMine(message) {
  if (currentUserId.value != null) {
    return Number(message.senderId) === Number(currentUserId.value)
  }
  return activeConversation.value
    ? Number(message.senderId) !== Number(activeConversation.value.otherUserId)
    : false
}

async function loadCurrentUser() {
  try {
    const user = await api.profile()
    currentUserId.value = user?.userId ?? null
  } catch (_) {
    currentUserId.value = null
  }
}

async function refreshConversations() {
  conversations.value = normalizeList(await api.privateConversations())
}

async function loadConversations(options = {}) {
  loadingConversations.value = true
  try {
    await refreshConversations()
    const routeConversationId = normalizeConversationId(route.params.conversationId)
    const keptConversationId = options.keepSelection ? normalizeConversationId(selectedConversationId.value) : null
    const nextConversationId = routeConversationId || keptConversationId
    if (nextConversationId) {
      await selectConversation(nextConversationId, { syncRoute: Boolean(routeConversationId) })
    } else {
      selectedConversationId.value = null
      messages.value = []
    }
  } finally {
    loadingConversations.value = false
  }
}

async function selectConversation(conversationId, options = {}) {
  const normalizedId = normalizeConversationId(conversationId)
  if (!normalizedId) return
  selectedConversationId.value = normalizedId

  if (options.syncRoute !== false && String(route.params.conversationId || '') !== String(normalizedId)) {
    router.push(`/messages/${normalizedId}`)
  }

  await loadMessages(normalizedId)
}

async function loadMessages(conversationId) {
  const normalizedId = normalizeConversationId(conversationId)
  if (!normalizedId) return
  loadingMessages.value = true
  try {
    messages.value = normalizeList(await api.privateMessages(normalizedId))
    await api.readPrivateConversation(normalizedId)
    await refreshConversations()
    await nextTick()
    if (messageScroller.value) {
      messageScroller.value.scrollTop = messageScroller.value.scrollHeight
    }
  } finally {
    loadingMessages.value = false
  }
}

async function sendMessage() {
  const content = messageInput.value.trim()
  if (!selectedConversationId.value || !content) return
  sendingMessage.value = true
  try {
    await api.sendPrivateMessage(selectedConversationId.value, { content })
    messageInput.value = ''
    await loadMessages(selectedConversationId.value)
  } catch (e) {
    alert(e?.message || '私信发送失败，请稍后重试')
  } finally {
    sendingMessage.value = false
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
    }
  }
)

onMounted(async () => {
  await loadCurrentUser()
  await loadConversations()
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
  grid-template-rows: auto minmax(0, 1fr) auto;
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

.message-bubble p {
  margin: 0;
  line-height: 1.65;
  word-break: break-word;
}

.message-bubble span {
  display: block;
  margin-top: 5px;
  color: #94a3b8;
  font-size: 12px;
}

.composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  padding: 14px 16px;
  border-top: 1px solid rgba(15, 23, 42, .1);
  background: rgba(255, 255, 255, .92);
}

.composer input {
  min-width: 0;
  min-height: 42px;
  border: 1px solid rgba(15, 23, 42, .14);
  border-radius: 8px;
  padding: 0 12px;
  font: inherit;
}

.btn-primary,
.btn-soft {
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

.btn-primary {
  background: #111827;
  color: #fff;
}

.btn-soft {
  background: #fff;
  color: #111827;
}

.btn-primary:disabled,
.btn-soft:disabled {
  cursor: not-allowed;
  opacity: .55;
}

.empty-tip {
  border: 1px dashed rgba(15, 23, 42, .18);
  border-radius: 8px;
  padding: 14px;
  color: #64748b;
  background: rgba(255, 255, 255, .62);
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

  .composer {
    grid-template-columns: 1fr;
  }
}
</style>
