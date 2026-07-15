const { test, expect } = require('@playwright/test')

const apiResponse = data => ({
  code: 200,
  message: 'ok',
  data
})

test('user logs in, opens private messages, and sends a message', async ({ page }) => {
  let loginPayload
  let sentPayload

  await page.route('**/api/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname

    if (path === '/api/auth/captcha') {
      await route.fulfill({
        status: 200,
        contentType: 'image/svg+xml',
        body: '<svg xmlns="http://www.w3.org/2000/svg" width="120" height="40"><text x="8" y="28">1234</text></svg>'
      })
      return
    }
    if (path === '/api/auth/login/user') {
      loginPayload = request.postDataJSON()
      await route.fulfill({ json: apiResponse({ token: 'e2e-token', role: 'USER', name: '端到端滑手' }) })
      return
    }
    if (path === '/api/user/profile') {
      await route.fulfill({ json: apiResponse({ userId: 7, username: '端到端滑手' }) })
      return
    }
    if (path === '/api/messages/unread-count') {
      await route.fulfill({ json: apiResponse({ unreadCount: 1 }) })
      return
    }
    if (path === '/api/messages/conversations' && request.method() === 'GET') {
      await route.fulfill({
        json: apiResponse({
          list: [{
            conversationId: 1,
            otherUserId: 9,
            otherUsername: '小林',
            lastMessageContent: '上次见',
            lastMessageTime: '2026-07-15T12:00:00',
            unreadCount: 1
          }],
          total: 1,
          totalPages: 1
        })
      })
      return
    }
    if (path === '/api/messages/conversations/1/messages') {
      await route.fulfill({
        json: apiResponse({
          list: [{
            messageId: 10,
            conversationId: 1,
            senderId: 9,
            receiverId: 7,
            content: '今晚去刷街吗？',
            createTime: '2026-07-15T12:00:00'
          }],
          nextBeforeMessageId: null,
          hasMore: false
        })
      })
      return
    }
    if (path === '/api/messages/conversations/1/read') {
      await route.fulfill({ json: apiResponse({}) })
      return
    }
    if (path === '/api/messages/conversations/1/send') {
      sentPayload = request.postDataJSON()
      await route.fulfill({
        json: apiResponse({
          messageId: 11,
          conversationId: 1,
          senderId: 7,
          receiverId: 9,
          content: sentPayload.content,
          createTime: '2026-07-15T12:01:00'
        })
      })
      return
    }

    await route.fulfill({ json: apiResponse([]) })
  })

  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名或手机号').fill('e2e-user')
  await page.getByPlaceholder('请输入密码', { exact: true }).fill('E2e-pass-123')
  await page.getByPlaceholder('请输入验证码').fill('1234')
  await page.locator('.login-scene .submit-btn').click()

  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByText('端到端滑手', { exact: true })).toBeVisible()
  expect(loginPayload).toMatchObject({
    account: 'e2e-user',
    password: 'E2e-pass-123',
    captchaCode: '1234',
    loginType: 'username'
  })

  await page.locator('header').getByRole('link', { name: /私信/ }).click()
  await page.getByRole('button', { name: /小林/ }).click()
  await expect(page.getByText('今晚去刷街吗？')).toBeVisible()

  await page.getByPlaceholder('输入私信内容，Ctrl / Cmd + Enter 发送').fill('收到，七点见')
  await page.getByRole('button', { name: '发送', exact: true }).click()

  await expect(page.locator('.message-row.mine .message-bubble p')).toHaveText('收到，七点见')
  expect(sentPayload).toEqual({ content: '收到，七点见' })
})
