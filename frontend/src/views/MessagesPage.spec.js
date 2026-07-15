import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MessagesPage from './MessagesPage.vue'

const { route, router, api } = vi.hoisted(() => ({
  route: { params: { conversationId: '1' } },
  router: { push: vi.fn(), replace: vi.fn() },
  api: {
    profile: vi.fn(),
    privateConversations: vi.fn(),
    privateMessages: vi.fn(),
    readPrivateConversation: vi.fn(),
    sendPrivateMessage: vi.fn()
  }
}))

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router
}))

vi.mock('../api', () => ({ api }))

vi.mock('gsap', () => {
  const timeline = { from: vi.fn().mockReturnThis() }
  return {
    gsap: {
      utils: { toArray: vi.fn(() => []) },
      timeline: vi.fn(() => timeline),
      fromTo: vi.fn(),
      killTweensOf: vi.fn()
    }
  }
})

const conversation = {
  conversationId: 1,
  otherUserId: 9,
  otherUsername: '小林',
  lastMessageContent: '上次见',
  lastMessageTime: '2026-07-15T12:00:00',
  unreadCount: 1
}

const initialMessage = {
  messageId: 10,
  conversationId: 1,
  senderId: 9,
  receiverId: 7,
  content: '今晚去刷街吗？',
  createTime: '2026-07-15T12:00:00'
}

function conversationPage() {
  return { list: [conversation], total: 1, totalPages: 1 }
}

function messagePage(overrides = {}) {
  return {
    list: [initialMessage],
    nextBeforeMessageId: null,
    hasMore: false,
    ...overrides
  }
}

async function mountPage() {
  const wrapper = mount(MessagesPage, {
    attachTo: document.body,
    global: {
      stubs: {
        AppIcon: true
      }
    }
  })
  await flushPromises()
  return wrapper
}

async function submitMessage(wrapper, content) {
  await wrapper.get('textarea').setValue(content)
  await wrapper.get('form.composer').trigger('submit')
}

beforeEach(() => {
  route.params.conversationId = '1'
  api.profile.mockResolvedValue({ userId: 7 })
  api.privateConversations.mockImplementation(async () => conversationPage())
  api.privateMessages.mockImplementation(async () => messagePage())
  api.readPrivateConversation.mockResolvedValue({})
  api.sendPrivateMessage.mockResolvedValue({
    messageId: 11,
    conversationId: 1,
    senderId: 7,
    receiverId: 9,
    content: '收到，七点见',
    createTime: '2026-07-15T12:01:00'
  })
  Object.defineProperty(document, 'hidden', { configurable: true, value: false })
})

afterEach(() => {
  document.body.innerHTML = ''
  vi.useRealTimers()
})

describe('MessagesPage runtime behavior', () => {
  it('loads the selected conversation and marks the latest visible message read', async () => {
    const wrapper = await mountPage()

    expect(api.profile).toHaveBeenCalledOnce()
    expect(api.privateConversations).toHaveBeenCalledWith({
      page: 1,
      size: 20,
      keyword: '',
      unreadOnly: false
    })
    expect(api.privateMessages).toHaveBeenCalledWith(1, { size: 30 })
    expect(api.readPrivateConversation).toHaveBeenCalledWith(1, { lastReadMessageId: 10 })
    expect(wrapper.text()).toContain('今晚去刷街吗？')

    wrapper.unmount()
  })

  it('prepends older messages using the server cursor', async () => {
    api.privateMessages.mockImplementation(async (_conversationId, params) => {
      if (params.beforeMessageId === 10) {
        return messagePage({
          list: [{ ...initialMessage, messageId: 5, content: '这是更早的消息' }],
          nextBeforeMessageId: null,
          hasMore: false
        })
      }
      return messagePage({ nextBeforeMessageId: 10, hasMore: true })
    })
    const wrapper = await mountPage()

    await wrapper.get('button.load-older').trigger('click')
    await flushPromises()

    expect(api.privateMessages).toHaveBeenCalledWith(1, { beforeMessageId: 10, size: 30 })
    expect(wrapper.findAll('.message-bubble p').map(node => node.text())).toEqual([
      '这是更早的消息',
      '今晚去刷街吗？'
    ])

    wrapper.unmount()
  })

  it('renders an optimistic message before the send request completes', async () => {
    let resolveSend
    api.sendPrivateMessage.mockReturnValue(new Promise(resolve => {
      resolveSend = resolve
    }))
    const wrapper = await mountPage()

    await submitMessage(wrapper, '收到，七点见')

    expect(wrapper.text()).toContain('收到，七点见')
    expect(wrapper.text()).toContain('发送中')
    expect(api.sendPrivateMessage).toHaveBeenCalledWith(1, { content: '收到，七点见' })

    resolveSend({
      messageId: 11,
      conversationId: 1,
      senderId: 7,
      receiverId: 9,
      content: '收到，七点见',
      createTime: '2026-07-15T12:01:00'
    })
    await flushPromises()

    expect(wrapper.text()).not.toContain('发送中')
    wrapper.unmount()
  })

  it('shows a failed delivery and retries the same optimistic message', async () => {
    api.sendPrivateMessage
      .mockRejectedValueOnce(new Error('网络暂不可用'))
      .mockResolvedValueOnce({
        messageId: 12,
        conversationId: 1,
        senderId: 7,
        receiverId: 9,
        content: '再试一次',
        createTime: '2026-07-15T12:02:00'
      })
    const wrapper = await mountPage()

    await submitMessage(wrapper, '再试一次')
    await flushPromises()

    expect(wrapper.text()).toContain('发送失败')
    expect(wrapper.text()).toContain('网络暂不可用')

    await wrapper.get('button.retry-link').trigger('click')
    await flushPromises()

    expect(api.sendPrivateMessage).toHaveBeenCalledTimes(2)
    expect(wrapper.find('button.retry-link').exists()).toBe(false)
    expect(wrapper.findAll('.message-bubble p').filter(node => node.text() === '再试一次')).toHaveLength(1)

    wrapper.unmount()
  })

  it('stops polling while the page is hidden and restarts when visible', async () => {
    vi.useFakeTimers()
    const wrapper = await mountPage()
    expect(vi.getTimerCount()).toBe(1)

    Object.defineProperty(document, 'hidden', { configurable: true, value: true })
    document.dispatchEvent(new Event('visibilitychange'))
    expect(vi.getTimerCount()).toBe(0)

    Object.defineProperty(document, 'hidden', { configurable: true, value: false })
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    expect(vi.getTimerCount()).toBe(1)

    wrapper.unmount()
    expect(vi.getTimerCount()).toBe(0)
  })
})
