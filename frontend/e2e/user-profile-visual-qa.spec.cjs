const path = require('path')
const { test, expect } = require('@playwright/test')

const response = data => ({ code: 200, message: 'ok', data })

async function waitForImages(page) {
  await page.waitForFunction(() => Array.from(document.images).every(image => image.complete))
}

async function mockProfileApi(page) {
  let following = false
  await page.addInitScript(() => {
    localStorage.setItem('skate_token', 'profile-visual-token')
    localStorage.setItem('skate_role', 'USER')
    localStorage.setItem('skate_name', '小陈')
  })

  await page.route('**/api/**', async route => {
    const request = route.request()
    const pathname = new URL(request.url()).pathname
    const profile = {
      userId: 9,
      username: '阿拓',
      avatar: 'https://api.dicebear.com/7.x/thumbs/svg?seed=atuo',
      bio: '热爱街式滑板，分享每一次落地与灵感。',
      level: 12,
      skateStyle: '街式滑板',
      gender: '男',
      postCount: 236,
      followerCount: 12800,
      followingCount: 328,
      creatorSummary: { hasCreatorContent: true, qualityScore: 92, videoCount: 18, tutorialPostCount: 9, totalEngagement: 3820 }
    }

    if (pathname === '/api/user/profile') return route.fulfill({ json: response({ userId: 7, username: '小陈' }) })
    if (pathname === '/api/messages/unread-count') return route.fulfill({ json: response({ unreadCount: 0 }) })
    if (pathname === '/api/public/users/9') return route.fulfill({ json: response(profile) })
    if (pathname === '/api/users/9/follow-status') return route.fulfill({ json: response({ following, mutual: false }) })
    if (pathname === '/api/public/users/9/posts') {
      return route.fulfill({ json: response({
        total: 2,
        list: [
          { postId: 31, title: '设计不是风格，而是解决问题的方式', category: '设计灵感', createTime: '2026-07-15T12:30:00', likeCount: 462, commentCount: 128, collectCount: 34 },
          { postId: 32, title: '如何让用户愿意长期留在你的产品里？', category: '产品思考', createTime: '2026-07-14T17:20:00', likeCount: 328, commentCount: 86, collectCount: 21 }
        ]
      }) })
    }
    if (pathname === '/api/users/9/follow' && request.method() === 'POST') {
      following = true
      return route.fulfill({ json: response({ following, mutual: false }) })
    }
    if (pathname === '/api/messages/conversations' && request.method() === 'POST') return route.fulfill({ json: response({ conversationId: 42 }) })
    return route.fulfill({ json: response({}) })
  })
}

test('renders the selected community-profile direction on desktop', async ({ page }) => {
  const errors = []
  page.on('console', message => message.type() === 'error' && errors.push(message.text()))
  await page.setViewportSize({ width: 1440, height: 1024 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await mockProfileApi(page)
  await page.goto('/users/9')
  await expect(page.getByRole('heading', { name: '阿拓' })).toBeVisible()
  await expect(page.getByRole('button', { name: '私信', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '更多操作' }).click()
  await expect(page.getByRole('menuitem', { name: '复制主页链接' })).toBeVisible()
  await waitForImages(page)
  await page.screenshot({ path: path.resolve(__dirname, '../../design-qa-assets/user-profile-desktop-1440x1024.png'), fullPage: false })
  expect(errors).toEqual([])
})

test('renders responsive action bar and follow interaction on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await mockProfileApi(page)
  await page.goto('/users/9')
  await expect(page.getByRole('heading', { name: '阿拓' })).toBeVisible()
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight))
  await expect(page.locator('.mobile-profile-actions')).toBeVisible()
  await page.locator('.mobile-profile-actions').getByRole('button', { name: '关注', exact: true }).click()
  await expect(page.locator('.mobile-profile-actions').getByRole('button', { name: '已关注', exact: true })).toBeVisible()
  await waitForImages(page)
  await page.screenshot({ path: path.resolve(__dirname, '../../design-qa-assets/user-profile-mobile-390x844.png'), fullPage: false })
})
