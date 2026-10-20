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

const mounted = []

async function mountPage() {
  const wrapper = mount(MessagesPage, {
    attachTo: document.body,
    global: {
      stubs: {
        AppIcon: true
      }
    }
  })
  mounted.push(wrapper)
  await flushPromises()
  return wrapper
}

async function submitMessage(wrapper, content) {
  await wrapper.get('textarea').setValue(content)
  await wrapper.get('form.composer').trigger('submit')
}

beforeEach(() => {
  for (const fn of Object.values(api)) fn.mockReset()
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
  for (const wrapper of mounted.splice(0)) if (wrapper.exists()) wrapper.unmount()
  vi.restoreAllMocks()
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

function deferred() {
  let resolve, reject
  const promise = new Promise((yes,no) => { resolve=yes; reject=no })
  return {promise,resolve,reject}
}
const secondConversation = {...conversation,conversationId:2,otherUserId:8,otherUsername:'小周'}
const secondMessage = {...initialMessage,messageId:20,conversationId:2,senderId:8,content:'B新线程'}
function twoConversations() { return {list:[conversation,secondConversation],total:2,totalPages:1} }
async function selectB(wrapper) { await wrapper.findAll('button.conversation-item')[1].trigger('click'); await flushPromises() }
function bPage() { return messagePage({list:[secondMessage],nextBeforeMessageId:20,hasMore:true}) }

describe('SYNTHETIC messages remediation; mocked APIs do not prove server acceptance', () => {
  it('retries failed page 2, commits success before page 3, and deduplicates overlap', async () => {
    route.params.conversationId = undefined
    const pages=[]; let attempts=0
    api.privateConversations.mockImplementation(async ({page}) => {
      pages.push(page)
      if(page===2 && ++attempts===1) throw new Error('第二页暂不可用')
      return {list:page===1?[conversation]:page===2?[conversation,secondConversation]:[{...conversation,conversationId:3,otherUsername:'第三页'}],total:3,totalPages:3}
    })
    const wrapper=await mountPage()
    await wrapper.get('button.load-more').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('第二页暂不可用'); expect(wrapper.get('button.load-more').attributes('disabled')).toBeUndefined()
    await wrapper.get('button.load-more').trigger('click'); await flushPromises()
    expect(wrapper.findAll('.conversation-item')).toHaveLength(2)
    await wrapper.get('button.load-more').trigger('click'); await flushPromises()
    expect(pages).toEqual([1,2,2,3]); expect(wrapper.findAll('.conversation-item')).toHaveLength(3)
  })

  it('late A success cannot replace B messages/cursor, mark A read, or refresh B list', async () => {
    const a=deferred(); api.privateConversations.mockResolvedValue(twoConversations())
    api.privateMessages.mockImplementation(id=>id===1?a.promise:Promise.resolve(bPage()))
    const wrapper=await mountPage(); await selectB(wrapper)
    const calls=api.privateConversations.mock.calls.length
    a.resolve(messagePage({nextBeforeMessageId:10,hasMore:true})); await flushPromises()
    expect(wrapper.get('.thread-identity h2').text()).toBe('小周')
    expect(wrapper.text()).toContain('B新线程'); expect(wrapper.text()).not.toContain(initialMessage.content)
    expect(wrapper.vm.nextBeforeMessageId).toBe(20)
    expect(api.privateConversations).toHaveBeenCalledTimes(calls)
    expect(api.readPrivateConversation.mock.calls).toEqual([[2,{lastReadMessageId:20}]])
  })

  it('late A failure/finally cannot set B error or clear B loading', async () => {
    const a=deferred(), b=deferred(); api.privateConversations.mockResolvedValue(twoConversations())
    api.privateMessages.mockImplementation(id=>id===1?a.promise:b.promise)
    const wrapper=await mountPage(); await selectB(wrapper)
    a.reject(new Error('过期A失败')); await flushPromises()
    expect(wrapper.vm.loadingMessages).toBe(true); expect(wrapper.text()).not.toContain('过期A失败')
    b.resolve(bPage()); await flushPromises()
    expect(wrapper.text()).toContain('B新线程'); expect(wrapper.vm.loadingMessages).toBe(false)
  })

  it('a read receipt already dispatched for A cannot trigger a later refresh/scroll against B', async () => {
    const readA=deferred(); api.privateConversations.mockResolvedValue(twoConversations())
    api.privateMessages.mockImplementation(async id=>id===1?messagePage():bPage())
    api.readPrivateConversation.mockImplementation(id=>id===1?readA.promise:Promise.resolve({}))
    const wrapper=await mountPage(); await selectB(wrapper)
    const calls=api.privateConversations.mock.calls.length
    readA.resolve({}); await flushPromises()
    expect(api.privateConversations).toHaveBeenCalledTimes(calls)
    expect(wrapper.get('.thread-identity h2').text()).toBe('小周'); expect(wrapper.vm.nextBeforeMessageId).toBe(20)
    expect(api.readPrivateConversation.mock.calls).toEqual([[1,{lastReadMessageId:10}],[2,{lastReadMessageId:20}]])
  })

  it('late A list refresh is discarded after B is selected', async () => {
    const listA=deferred(); let count=0
    api.privateConversations.mockImplementation(()=>++count===2?listA.promise:Promise.resolve(twoConversations()))
    api.privateMessages.mockImplementation(async id=>id===1?messagePage():bPage())
    const wrapper=await mountPage(); await selectB(wrapper)
    listA.resolve({list:[conversation],total:1,totalPages:1}); await flushPromises()
    expect(wrapper.get('.thread-identity h2').text()).toBe('小周')
    expect(wrapper.findAll('.conversation-item')).toHaveLength(2); expect(wrapper.text()).toContain('B新线程')
  })

  it.each(['resolve','reject'])('late older A %s cannot merge into B or clear B older lock', async outcome => {
    const oldA=deferred(), oldB=deferred(); api.privateConversations.mockResolvedValue(twoConversations())
    api.privateMessages.mockImplementation((id,params)=>params.beforeMessageId
      ? (id===1?oldA.promise:oldB.promise)
      : Promise.resolve(id===1?messagePage({nextBeforeMessageId:10,hasMore:true}):bPage()))
    const wrapper=await mountPage(); await wrapper.get('button.load-older').trigger('click')
    await selectB(wrapper); await wrapper.get('button.load-older').trigger('click')
    oldA[outcome](outcome==='resolve'?messagePage({list:[{...initialMessage,content:'过期A旧消息'}]}):new Error('过期A旧消息失败'))
    await flushPromises()
    expect(wrapper.vm.loadingMoreMessages).toBe(true); expect(wrapper.vm.nextBeforeMessageId).toBe(20)
    expect(wrapper.text()).not.toContain('过期A')
    oldB.resolve(messagePage({list:[{...secondMessage,messageId:15,content:'B更早消息'}]})); await flushPromises()
    expect(wrapper.text()).toContain('B更早消息'); expect(wrapper.vm.loadingMoreMessages).toBe(false)
  })

  it.each(['resolve','reject'])('late send A %s cannot alter B sending/error or refresh its list', async outcome => {
    const sendA=deferred(), sendB=deferred(); api.privateConversations.mockResolvedValue(twoConversations())
    api.privateMessages.mockImplementation(async id=>id===1?messagePage():bPage())
    api.sendPrivateMessage.mockImplementation(id=>id===1?sendA.promise:sendB.promise)
    const wrapper=await mountPage(); await submitMessage(wrapper,'A待发送')
    await selectB(wrapper); await submitMessage(wrapper,'B待发送')
    const calls=api.privateConversations.mock.calls.length
    sendA[outcome](outcome==='resolve'?{...initialMessage,messageId:30,content:'A待发送'}:new Error('过期A发送失败'))
    await flushPromises()
    expect(wrapper.vm.sendingMessage).toBe(true); expect(wrapper.text()).not.toContain('A待发送'); expect(wrapper.text()).not.toContain('过期A发送失败')
    expect(api.privateConversations).toHaveBeenCalledTimes(calls)
    sendB.resolve({...secondMessage,messageId:31,content:'B待发送'}); await flushPromises()
    expect(wrapper.vm.sendingMessage).toBe(false); expect(wrapper.text()).toContain('B待发送')
  })

  it.each(['resolve','reject'])('late poll A %s cannot merge/mark B or emit stale warnings', async outcome => {
    const pollA=deferred(); api.privateConversations.mockResolvedValue(twoConversations())
    api.privateMessages.mockImplementation(async id=>id===1?messagePage():bPage())
    const wrapper=await mountPage(), warn=vi.spyOn(console,'warn').mockImplementation(()=>{})
    api.privateMessages.mockImplementation(id=>id===1?pollA.promise:Promise.resolve(bPage()))
    const pending=wrapper.vm.pollForUpdates(); await flushPromises(); await selectB(wrapper)
    const reads=api.readPrivateConversation.mock.calls.length, lists=api.privateConversations.mock.calls.length
    pollA[outcome](outcome==='resolve'?messagePage({list:[{...initialMessage,messageId:99,content:'过期A轮询'}]}):new Error('过期A轮询失败'))
    await pending; await flushPromises()
    expect(wrapper.text()).not.toContain('过期A'); expect(wrapper.text()).toContain('B新线程')
    expect(api.readPrivateConversation).toHaveBeenCalledTimes(reads); expect(api.privateConversations).toHaveBeenCalledTimes(lists)
    expect(warn).not.toHaveBeenCalled()
  })

  it('warns once per continuous polling failure and resets after recovery; manual refresh still reports errors', async () => {
    const wrapper=await mountPage(), warn=vi.spyOn(console,'warn').mockImplementation(()=>{})
    api.privateConversations.mockRejectedValue(new Error('SECRET_TOKEN PRIVATE_MESSAGE USER_DETAILS'))
    await wrapper.vm.pollForUpdates(); await wrapper.vm.pollForUpdates(); await wrapper.vm.pollForUpdates()
    expect(warn.mock.calls).toEqual([['[Messages] 轮询失败，将在下一周期重试。']])
    expect(wrapper.text()).not.toContain('SECRET_TOKEN')
    api.privateConversations.mockResolvedValue(conversationPage()); await wrapper.vm.pollForUpdates()
    api.privateConversations.mockRejectedValue(new Error('SECRET_TOKEN')); await wrapper.vm.pollForUpdates()
    expect(warn).toHaveBeenCalledTimes(2)
    api.privateMessages.mockRejectedValue(new Error('手动刷新失败'))
    await wrapper.get('[aria-label="刷新当前对话"]').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('手动刷新失败'); expect(warn).toHaveBeenCalledTimes(2)
  })

  it('unmount invalidates a pending initial thread and never starts a late polling timer', async () => {
    vi.useFakeTimers(); const a=deferred(); api.privateMessages.mockReturnValue(a.promise)
    const wrapper=await mountPage(); wrapper.unmount()
    a.resolve(messagePage()); await flushPromises()
    expect(api.readPrivateConversation).not.toHaveBeenCalled(); expect(vi.getTimerCount()).toBe(0)
  })
})
