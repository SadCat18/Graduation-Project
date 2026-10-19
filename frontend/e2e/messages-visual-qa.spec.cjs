const path = require('path')
const { test, expect } = require('@playwright/test')

// Retain failed-resource diagnostics and isolate only the confirmed external image.
test.beforeEach(async ({ page }) => {
  page.on('requestfailed', request => console.log('CI_RESOURCE_FAILURE', JSON.stringify({ url:request.url(),error:request.failure()?.errorText })))
  page.on('console', message => { if (message.type() === 'error') console.log('CI_CONSOLE_LOCATION', JSON.stringify(message.location())) })
  await page.route('https://www.beian.gov.cn/img/ghs.png', route => route.fulfill({ contentType:'image/svg+xml', body:decodeURIComponent(avatar.split(',')[1]) }))
})
const avatar = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64"><rect width="64" height="64" rx="16" fill="#d7e8df"/><circle cx="32" cy="24" r="12" fill="#087a6d"/><path d="M12 60a20 20 0 0 1 40 0" fill="#087a6d"/></svg>')

const apiResponse = data => ({ code: 200, message: 'ok', data })

const conversations = [
  { conversationId: 1, otherUserId: 9, otherUsername: '阿拓', otherAvatar: avatar, lastMessageContent: '明天下午一点没问题，我到时候带上板。', lastMessageTime: '2026-07-16T14:32:00', unreadCount: 2 },
  { conversationId: 2, otherUserId: 10, otherUsername: '阿K', otherAvatar: avatar, lastMessageContent: '收到，到时候见！', lastMessageTime: '2026-07-16T13:18:00', unreadCount: 1 },
  { conversationId: 3, otherUserId: 11, otherUsername: '板仔', otherAvatar: avatar, lastMessageContent: '新视频太帅了，学到了', lastMessageTime: '2026-07-15T18:20:00', unreadCount: 0 },
  { conversationId: 4, otherUserId: 12, otherUsername: '南城滑手', otherAvatar: avatar, lastMessageContent: '下周六有比赛，一起去吗？', lastMessageTime: '2026-07-15T17:04:00', unreadCount: 0 },
  { conversationId: 5, otherUserId: 13, otherUsername: '小川', otherAvatar: avatar, lastMessageContent: '哈哈哈，太狠了', lastMessageTime: '2026-07-14T21:42:00', unreadCount: 0 },
  { conversationId: 6, otherUserId: 14, otherUsername: '滑板思思', otherAvatar: avatar, lastMessageContent: '谢谢你的建议！', lastMessageTime: '2026-07-14T19:11:00', unreadCount: 0 },
  { conversationId: 7, otherUserId: 15, otherUsername: '阿诺', otherAvatar: avatar, lastMessageContent: '你发的那个板场在哪里？', lastMessageTime: '2026-07-12T16:30:00', unreadCount: 0 }
]

let messages = [
  { messageId: 10, conversationId: 1, senderId: 9, receiverId: 7, content: '最近有空一起去金海湾滑板公园吗？', createTime: '2026-07-16T13:20:00' },
  { messageId: 11, conversationId: 1, senderId: 7, receiverId: 9, content: '可以啊，我这几天都比较闲。', createTime: '2026-07-16T13:21:00' },
  { messageId: 12, conversationId: 1, senderId: 9, receiverId: 7, content: '我想练练新动作，想请你帮我看看。', createTime: '2026-07-16T13:21:00' },
  { messageId: 13, conversationId: 1, senderId: 7, receiverId: 9, content: '没问题，周末还是工作日？', createTime: '2026-07-16T13:22:00' },
  { messageId: 14, conversationId: 1, senderId: 9, receiverId: 7, content: '明天下午一点怎么样？天气看起来不错。', createTime: '2026-07-16T13:23:00' },
  { messageId: 15, conversationId: 1, senderId: 7, receiverId: 9, content: '明天下午一点没问题，我到时候带上板。', createTime: '2026-07-16T13:24:00' },
  { messageId: 16, conversationId: 1, senderId: 9, receiverId: 7, content: '好嘞，那我先到，等你！', createTime: '2026-07-16T13:24:00' }
]

async function mockPrivateMessages(page) {
  await page.addInitScript(() => {
    localStorage.setItem('skate_token', 'visual-qa-token')
    localStorage.setItem('skate_role', 'USER')
    localStorage.setItem('skate_name', '小陈')
  })

  await page.route('**/api/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const apiPath = url.pathname

    if (apiPath === '/api/user/profile') {
      await route.fulfill({ json: apiResponse({ userId: 7, username: '小陈' }) })
      return
    }
    if (apiPath === '/api/messages/unread-count') {
      await route.fulfill({ json: apiResponse({ unreadCount: 3 }) })
      return
    }
    if (apiPath === '/api/messages/conversations' && request.method() === 'GET') {
      const keyword = String(url.searchParams.get('keyword') || '').trim()
      const unreadOnly = url.searchParams.get('unreadOnly') === 'true'
      const list = conversations.filter(item => {
        const matchesKeyword = !keyword || `${item.otherUsername}${item.lastMessageContent}`.includes(keyword)
        return matchesKeyword && (!unreadOnly || item.unreadCount > 0)
      })
      await route.fulfill({ json: apiResponse({ list, total: list.length, totalPages: 1 }) })
      return
    }
    if (apiPath === '/api/messages/conversations/1/messages') {
      await route.fulfill({ json: apiResponse({ list: messages, nextBeforeMessageId: null, hasMore: false }) })
      return
    }
    if (apiPath === '/api/messages/conversations/1/read') {
      await route.fulfill({ json: apiResponse({}) })
      return
    }
    if (apiPath === '/api/messages/conversations/1/send') {
      const payload = request.postDataJSON()
      const sentMessage = {
        messageId: messages.length + 20,
        conversationId: 1,
        senderId: 7,
        receiverId: 9,
        content: payload.content,
        createTime: '2026-07-16T14:35:00'
      }
      messages = [...messages, sentMessage]
      await route.fulfill({ json: apiResponse(sentMessage) })
      return
    }

    await route.fulfill({ json: apiResponse([]) })
  })
}

async function waitForImages(page) {
  await page.waitForFunction(() => Array.from(document.images).every(image => image.complete))
}

test('captures the populated desktop private-message design', async ({ page }) => {
  const consoleErrors = []
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })

  await page.setViewportSize({ width: 1440, height: 1024 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await mockPrivateMessages(page)
  await page.goto('/messages/1')
  await expect(page.getByRole('heading', { name: '阿拓' })).toBeVisible()
  await expect(page.getByText('最近有空一起去金海湾滑板公园吗？')).toBeVisible()

  const search = page.getByPlaceholder('搜索会话')
  await search.fill('阿')
  await expect(page.getByRole('button', { name: /阿K/ })).toBeVisible()
  await search.fill('')

  const composer = page.getByPlaceholder('输入消息...')
  await composer.fill('好，明天见！')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.locator('.message-row.mine .message-bubble p').last()).toHaveText('好，明天见！')

  await waitForImages(page)
  const layoutBox = await page.locator('.messages-layout').boundingBox()
  const composerBox = await page.locator('.composer').boundingBox()
  expect(layoutBox.width).toBeGreaterThan(1400)
  expect(composerBox.y + composerBox.height).toBeLessThanOrEqual(layoutBox.y + layoutBox.height + 1)
  await page.screenshot({
    path: path.resolve(__dirname, '../../design-qa-assets/messages-desktop-1440x1024.png'),
    fullPage: false
  })

  expect(consoleErrors).toEqual([])
})

test('captures the mobile conversation and back navigation', async ({ page }) => {
  const consoleErrors = []
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })

  await page.setViewportSize({ width: 390, height: 844 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await mockPrivateMessages(page)
  await page.goto('/messages/1')
  await expect(page.getByRole('heading', { name: '阿拓' })).toBeVisible()
  await waitForImages(page)
  await page.screenshot({
    path: path.resolve(__dirname, '../../design-qa-assets/messages-mobile-390x844.png'),
    fullPage: false
  })

  await page.getByRole('button', { name: '返回会话列表' }).click()
  await expect(page.getByPlaceholder('搜索会话')).toBeVisible()
  expect(consoleErrors).toEqual([])
})
