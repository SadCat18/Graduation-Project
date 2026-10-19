const { test, expect } = require('@playwright/test')

const ok = data => ({ code: 200, message: 'success', data })

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('skate_token', 'admin-e2e-token')
    localStorage.setItem('skate_role', 'ADMIN')
    localStorage.setItem('skate_name', '运营管理员')
  })

  await page.route('**/api/admin/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const now = Date.now()
    const currentTrend = [
      [7, 5, 2, 8], [8, 6, 2, 9], [9, 7, 3, 10], [8, 6, 2, 8],
      [10, 8, 3, 11], [11, 9, 4, 12], [12, 10, 4, 14]
    ].map((values, index) => ({
      date: `2026-07-${String(10 + index).padStart(2, '0')}`,
      newUsers: values[0], newPosts: values[1], newActivities: values[2], newSigns: values[3]
    }))
    const previousTrend = currentTrend.map(item => ({
      ...item,
      newUsers: item.newUsers + 2,
      newPosts: item.newPosts + 1,
      newActivities: item.newActivities,
      newSigns: item.newSigns + 3
    }))
    const trend30d = Array.from({ length: 30 }, (_, index) => ({
      ...currentTrend[index % currentTrend.length],
      date: new Date(Date.UTC(2026, 5, 17 + index)).toISOString().slice(0, 10)
    }))
    const previousTrend30d = trend30d.map(item => ({
      ...item,
      newUsers: item.newUsers - 1,
      newPosts: Math.max(0, item.newPosts - 1)
    }))

    const responses = {
      '/api/admin/stats': {
        userTotal: 1284, postTotal: 368, commentTotal: 2916, activityTotal: 84,
        noticeTotal: 12, bulletinTotal: 126, newsTotal: 92, bannerTotal: 6, signTotal: 438
      },
      '/api/admin/analytics': {
        trend7d: currentTrend,
        previousTrend7d: previousTrend,
        trend30d,
        previousTrend30d,
        activityConversion: { rate: 18.6, signs: 93, views: 500 },
        activityConversion30d: { rate: 22.5, signs: 270, views: 1200 },
        handlingEfficiency: { averageHours: 5.4, sampleSize: 48, windowDays: 30 },
        productTrend7d: [],
        hotPosts: [{ postId: 8, title: '新手碗池入门路线', hotScore: 96, likeCount: 38, commentCount: 20 }],
        hotActivities: [{ activityId: 7, title: '周末街式公开课', signNum: 32, maxNum: 40 }],
        bulletinTypeRatio: [{ type: '场地动态', count: 18 }, { type: '赛事', count: 12 }]
      },
      '/api/admin/activities': {
        total: 2,
        list: [
          { activityId: 1, title: '周末碗池交流', reviewStatus: '0', createTime: new Date(now - 2 * 3600000).toISOString() },
          { activityId: 2, title: '街式练习', reviewStatus: '0', createTime: new Date(now - 3 * 3600000).toISOString() }
        ]
      },
      '/api/admin/reports': [
        { reportId: 1, targetType: 'POST', targetId: 99, targetTitle: '风险帖子', reason: '违法违规', detail: '存在明显安全风险', status: '0', createTime: new Date(now - 3 * 3600000).toISOString() },
        { reportId: 2, targetType: 'POST', targetId: 99, targetTitle: '风险帖子', reason: '广告', detail: '重复引流', status: '0', createTime: new Date(now - 28 * 3600000).toISOString() }
      ],
      '/api/admin/bulletins': [
        { bulletinId: 1, title: '场地临时关闭', status: '0', createTime: new Date(now - 4 * 3600000).toISOString() }
      ],
      '/api/admin/places/pending': [
        { contributionId: 1, name: '滨江滑板点', createTime: new Date(now - 5 * 3600000).toISOString() }
      ],
      '/api/admin/place-reviews': [
        { reviewId: 1, content: '场地评价待核实', createTime: new Date(now - 2 * 3600000).toISOString() }
      ],
      '/api/admin/news': [
        { newsId: 1, title: '赛事资讯', status: '0' },
        { newsId: 2, title: '装备动态', status: '0' },
        { newsId: 3, title: '技巧教学', status: '1' }
      ]
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok(responses[path] ?? []))
    })
  })
})

test('运营驾驶舱按手册顺序呈现核心信息', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/admin')

  await expect(page.getByRole('heading', { name: '先处理最急的事项' })).toBeVisible()
  await expect(page.locator('.health-strip')).toHaveCount(1)
  await expect(page.locator('.priority-item')).toHaveCount(4)
  await expect(page.locator('.kpi-card')).toHaveCount(4)
  await expect(page.locator('.trend-chart canvas')).toHaveCount(1)
  await expect(page.locator('.funnel-chart canvas')).toHaveCount(1)
  await expect(page.getByText('需要立即处置', { exact: true })).toBeVisible()
  await expect(page.getByText('18.6%', { exact: true })).toBeVisible()

  const kpiBottom = await page.locator('.dashboard-kpis').evaluate(element => element.getBoundingClientRect().bottom)
  expect(kpiBottom).toBeLessThanOrEqual(900)

  await page.screenshot({ path: testInfo.outputPath('admin-workbench.png'), fullPage: true })

  await page.getByRole('button', { name: '近 30 天' }).click()
  await expect(page.getByRole('heading', { name: '近 30 天运营趋势' })).toBeVisible()
  await expect(page.getByText('22.5%', { exact: true })).toBeVisible()
})

test('运营驾驶舱在窄屏下无横向溢出', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/admin')
  await expect(page.getByRole('heading', { name: '先处理最急的事项' })).toBeVisible()
  const dimensions = await page.evaluate(() => ({ width: document.documentElement.scrollWidth, viewport: window.innerWidth }))
  expect(dimensions.width).toBeLessThanOrEqual(dimensions.viewport)

  const priorityTop = await page.locator('.dashboard-priority').evaluate(element => element.getBoundingClientRect().top)
  const kpiTop = await page.locator('.dashboard-kpis').evaluate(element => element.getBoundingClientRect().top)
  const trendTop = await page.locator('.dashboard-trend').evaluate(element => element.getBoundingClientRect().top)
  const quickTop = await page.locator('.dashboard-quick').evaluate(element => element.getBoundingClientRect().top)
  expect(priorityTop).toBeLessThan(kpiTop)
  expect(kpiTop).toBeLessThan(trendTop)
  expect(trendTop).toBeLessThan(quickTop)
})

test('风险卡跳转会带入待处理筛选', async ({ page }) => {
  await page.goto('/admin')
  await page.locator('.dashboard-priority').getByRole('button', { name: /举报处置/ }).click()
  await expect(page.getByRole('heading', { name: '举报管理' })).toBeVisible()
  await expect(page.locator('.report-filter-row select').first()).toHaveValue('pending')
})

test('同类型待审内容可批量处理并获得页面内反馈', async ({ page }) => {
  await page.goto('/admin')
  await page.locator('.dashboard-priority').getByRole('button', { name: /审核待办/ }).click()
  await expect(page.getByRole('heading', { name: '审核中心' })).toBeVisible()

  await page.locator('.review-card input[type="checkbox"]').first().check()
  await expect(page.getByText(/已选 1 条 · 活动 · 待审核 1 条/)).toBeVisible()

  page.once('dialog', dialog => dialog.accept())
  await page.getByRole('button', { name: '批量通过' }).click()
  await expect(page.locator('.admin-toast-item')).toContainText('批量通过完成：成功 1 条')
})
