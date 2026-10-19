const { test, expect } = require('@playwright/test')
const fs = require('node:fs')
const path = require('node:path')

const apiResponses = []
const adminObservations = []
const responseReads = []
const transportFaults = []
const failedRequests = []

function matches(response, pathname, method) {
  return new URL(response.url()).pathname === pathname && response.request().method() === method
}

function observeApi(page) {
  page.on('response', (response) => {
    const url = new URL(response.url())
    if (!url.pathname.startsWith('/api/')) return
    const entry = { method: response.request().method(), path: url.pathname, status: response.status() }
    apiResponses.push(entry)
    if (response.headers()['content-type']?.includes('application/json')) {
      responseReads.push(response.json().then((body) => {
        entry.apiCode = body.code ?? null
        entry.errorCode = body.data?.errorCode ?? null
        if (body.code && body.code !== 200) entry.message = body.message ?? ''
      }).catch(() => {}))
    }
  })
}

test('T07 real browser auth continuation, comparison, replacement, admin gates, and legacy smoke', async ({ page, browser }) => {
  if (!process.env.T07_USER_NAME || !process.env.T07_USER_PASSWORD ||
      !process.env.T07_ADMIN_ACCOUNT || !process.env.T07_ADMIN_PASSWORD ||
      !process.env.T07_EVIDENCE_DIR) {
    throw new Error('T07 synthetic login credentials and evidence directory are required')
  }
  observeApi(page)
  page.on('requestfailed', (request) => {
    const url = new URL(request.url())
    if (url.pathname.startsWith('/api/')) failedRequests.push({ method: request.method(), path: url.pathname, failure: request.failure()?.errorText || '' })
  })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/board-assistant')
  await expect(page.getByRole('heading', { name: '选板助手' })).toBeVisible()

  // Keyboard submit and narrow viewport are part of the real-browser smoke.
  await page.getByLabel('人民币预算（元）').fill('110.00')
  const recommendResponsePromise = page.waitForResponse((response) =>
    matches(response, '/api/skate/board-assistant/recommend', 'POST'))
  await page.getByLabel('人民币预算（元）').press('Enter')
  const recommendation = await (await recommendResponsePromise).json()
  expect(recommendation.code).toBe(200)
  expect(recommendation.data.complete.plans.length).toBeGreaterThan(0)
  expect(recommendation.data.assembly.plans.length).toBeGreaterThan(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
  await expect(page.getByRole('heading', { name: '推荐结果' })).toBeVisible()
  await page.screenshot({ path: path.join(process.env.T07_EVIDENCE_DIR, 'T07-edge-recommendation.png'), fullPage: true })

  // Compare one complete board and one assembly through the actual Spring endpoint.
  await page.getByRole('button', { name: '加入对比' }).first().click()
  await page.getByRole('tab', { name: /配件组装/ }).click()
  await page.getByRole('button', { name: '加入对比' }).first().click()
  const compareResponsePromise = page.waitForResponse((response) =>
    matches(response, '/api/skate/board-assistant/compare', 'POST'))
  await page.getByRole('button', { name: '重新计算并对比' }).click()
  const compareResponse = await (await compareResponsePromise).json()
  expect(compareResponse.code).toBe(200)
  expect(compareResponse.data.evaluations.map((item) => item.kind)).toEqual(['COMPLETE', 'ASSEMBLY'])
  await expect(page.locator('.board-comparison')).toContainText('成品整板 / 厂家安装')
  await expect(page.locator('.board-comparison')).toContainText('组装 / 需自行安装')

  await page.getByRole('button', { name: '查看方案 1' }).click()
  await expect(page.getByTestId('selected-plan')).toBeVisible()
  const replacementResponsePromise = page.waitForResponse((response) =>
    matches(response, '/api/skate/board-assistant/replacements', 'POST'))
  await page.getByRole('button', { name: '替换板面' }).click()
  const replacementResponse = await (await replacementResponsePromise).json()
  expect(replacementResponse.code).toBe(200)
  const replacement = replacementResponse.data.list.find((candidate) =>
    candidate.selection.items.some((item) => item.category === 'DECK' && item.variantKey === 'SYN-V-DECK-ALT'))
  expect(replacement).toBeTruthy()
  const altCard = page.locator('article.board-item').filter({ hasText: 'SYNTHETIC DECK ALT' }).last()
  await altCard.getByRole('button', { name: /检查替换/ }).click()
  await expect(page.getByRole('heading', { name: '确认替换差异' })).toBeVisible()
  await expect(page.getByText(/板面：SYN-V-DECK → SYN-V-DECK-ALT/).first()).toBeVisible()
  const evaluateReplacementPromise = page.waitForResponse((response) =>
    matches(response, '/api/skate/board-assistant/evaluate', 'POST'))
  await page.getByRole('button', { name: '确认单项替换并重算' }).click()
  const replacementEvaluation = await (await evaluateReplacementPromise).json()
  expect(replacementEvaluation.code).toBe(200)
  expect(replacementEvaluation.data.selection.items.find((item) => item.category === 'DECK').variantKey).toBe('SYN-V-DECK-ALT')
  expect(replacementEvaluation.data.totalCents).toBe(1797)
  await page.screenshot({ path: path.join(process.env.T07_EVIDENCE_DIR, 'T07-edge-replacement.png'), fullPage: true })

  await page.getByLabel('备注').fill('T07 browser continuation; synthetic fixture only')
  await page.getByRole('button', { name: '重新计算并准备保存' }).click()
  await expect(page).toHaveURL(/\/login\?returnTo=/)
  expect(new URL(page.url()).searchParams.get('returnTo')).toBe('/board-assistant')
  const continuation = await page.evaluate(() => JSON.parse(sessionStorage.getItem('board-plan-continuation-v1')))
  expect(continuation.selection.items.find((item) => item.category === 'DECK').variantKey).toBe('SYN-V-DECK-ALT')
  expect(continuation).not.toHaveProperty('totalCents')

  await page.getByPlaceholder('请输入用户名或手机号').fill(process.env.T07_USER_NAME)
  await page.getByPlaceholder('请输入密码', { exact: true }).fill(process.env.T07_USER_PASSWORD)
  await page.getByPlaceholder('请输入验证码').fill('T07X')
  const userLoginPromise = page.waitForResponse((response) =>
    matches(response, '/api/auth/login/user', 'POST'))
  const resumedEvaluationPromise = page.waitForResponse((response) =>
    matches(response, '/api/skate/board-assistant/evaluate', 'POST'))
  await page.getByRole('button', { name: '立即登录' }).first().click()
  const userLogin = await (await userLoginPromise).json()
  expect(userLogin.code).toBe(200)
  const resumedEvaluation = await (await resumedEvaluationPromise).json()
  expect(resumedEvaluation.code).toBe(200)
  expect(resumedEvaluation.data.selection.items.find((item) => item.category === 'DECK').variantKey).toBe('SYN-V-DECK-ALT')
  expect(resumedEvaluation.data.totalCents).toBe(1797)
  await expect(page.locator('.board-notice[role="status"]')).toContainText('已恢复选择并按当前目录重算')

  const prepareSavePromise = page.waitForResponse((response) =>
    matches(response, '/api/skate/board-assistant/evaluate', 'POST'))
  await page.getByRole('button', { name: '重新计算并准备保存' }).click()
  expect((await (await prepareSavePromise).json()).code).toBe(200)
  const saveResponsePromise = page.waitForResponse((response) =>
    matches(response, '/api/user/board-plans', 'POST'))
  await page.getByRole('button', { name: '确认保存当前结果' }).click()
  const saved = await (await saveResponsePromise).json()
  expect(saved.code).toBe(200)
  expect(saved.data.totalCents).toBe(1797)
  await expect(page.locator('.board-notice[role="status"]')).toContainText('已保存方案')

  const plansListPromise = page.waitForResponse((response) =>
    matches(response, '/api/user/board-plans', 'GET'))
  await page.locator('a[href="/profile/board-plans"]').first().click()
  expect((await (await plansListPromise).json()).code).toBe(200)
  await expect(page.getByRole('heading', { name: '我的方案' })).toBeVisible()
  await page.getByRole('button', { name: '查看我的选板方案' }).click()
  await expect(page.getByTestId('saved-snapshot')).toContainText('17.97')
  await expect(page.getByTestId('latest-evaluation')).toBeVisible()
  await page.screenshot({ path: path.join(process.env.T07_EVIDENCE_DIR, 'T07-edge-saved-snapshot.png'), fullPage: true })

  // Fetch a real replacement response, delay delivery, and verify that returning to a newer
  // recommendation prevents the old response from restoring the replacement panel.
  await page.goto('/board-assistant')
  await page.getByLabel('人民币预算（元）').fill('110.00')
  const setupRecommend = page.waitForResponse((response) => matches(response, '/api/skate/board-assistant/recommend', 'POST'))
  await page.getByLabel('人民币预算（元）').press('Enter')
  await setupRecommend
  await page.getByRole('tab', { name: /配件组装/ }).click()
  await page.getByRole('button', { name: '查看方案 1' }).click()
  let replacementFetchedResolve
  let replacementDeliveredResolve
  const replacementFetched = new Promise((resolve) => { replacementFetchedResolve = resolve })
  const replacementDelivered = new Promise((resolve) => { replacementDeliveredResolve = resolve })
  await page.route('**/api/skate/board-assistant/replacements', async (route) => {
    const upstream = await route.fetch()
    const body = await upstream.json()
    transportFaults.push({ scenario: 'REAL_HTTP_WITH_TRANSPORT_FAULT_INJECTION', operation: 'replacement response delayed after real Spring response', upstreamHttpStatus: upstream.status(), upstreamApiCode: body.code })
    replacementFetchedResolve()
    await new Promise((resolve) => setTimeout(resolve, 900))
    await route.fulfill({ response: upstream })
    replacementDeliveredResolve()
  })
  const delayedReplacementResponse = page.waitForResponse((response) => matches(response, '/api/skate/board-assistant/replacements', 'POST'))
  await page.getByRole('button', { name: '替换板面' }).click()
  await replacementFetched
  await page.getByRole('button', { name: '返回推荐结果' }).click()
  await page.getByLabel('人民币预算（元）').fill('20.00')
  const latestRecommendationResponse = page.waitForResponse((response) => matches(response, '/api/skate/board-assistant/recommend', 'POST'))
  await page.getByLabel('人民币预算（元）').press('Enter')
  const latestRecommendation = await (await latestRecommendationResponse).json()
  expect(latestRecommendation.code).toBe(200)
  const recommendationCard = page.locator('.card').filter({ has: page.getByRole('heading', { name: '推荐结果' }) })
  await expect(recommendationCard).toContainText('预算 ¥20.00')
  await replacementDelivered
  await delayedReplacementResponse
  await page.unroute('**/api/skate/board-assistant/replacements')
  await expect(page.getByRole('heading', { name: '推荐结果' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '替换板面' })).toHaveCount(0)
  await expect(recommendationCard).toContainText('预算 ¥20.00')
  transportFaults.push({ scenario: 'REAL_HTTP_WITH_TRANSPORT_FAULT_INJECTION', operation: 'stale replacement response did not overwrite newer recommendation', expectedBudgetCents: 2000, result: 'passed' })

  // The real API commits the save, then the browser response is deliberately dropped.
  // The UI must report an unknown outcome and let the user verify the plan in My Plans.
  await page.getByRole('tab', { name: /配件组装/ }).click()
  await page.getByRole('button', { name: '查看方案 1' }).click()
  const unknownPlanName = 'T07真实HTTP传输结果未知核对'
  await page.getByLabel('方案名称').fill(unknownPlanName)
  await page.getByLabel('备注').fill('SYNTHETIC only; real local HTTP response is intentionally dropped after commit')
  const prepareForUnknownSave = page.waitForResponse((response) => matches(response, '/api/skate/board-assistant/evaluate', 'POST'))
  await page.getByRole('button', { name: '重新计算并准备保存' }).click()
  expect((await prepareForUnknownSave).status()).toBe(200)
  let committedSave
  await page.route('**/api/user/board-plans', async (route) => {
    if (route.request().method() !== 'POST') return route.continue()
    const upstream = await route.fetch()
    committedSave = await upstream.json()
    transportFaults.push({ scenario: 'REAL_HTTP_WITH_TRANSPORT_FAULT_INJECTION', operation: 'save response dropped after real commit', upstreamHttpStatus: upstream.status(), upstreamApiCode: committedSave.code, persistedKey: committedSave.data?.key || null })
    await route.abort('connectionreset')
  })
  const failedSaveRequest = page.waitForEvent('requestfailed', (request) =>
    new URL(request.url()).pathname === '/api/user/board-plans' && request.method() === 'POST')
  await page.getByRole('button', { name: '确认保存当前结果' }).click()
  await failedSaveRequest
  await page.unroute('**/api/user/board-plans')
  expect(committedSave?.code).toBe(200)
  expect(committedSave?.data?.key).toBeTruthy()
  await expect(page.getByRole('alert')).toContainText('本次保存结果未知')
  await expect(page.locator('.board-page')).not.toContainText('已保存方案')
  const persistedPlansResponse = page.waitForResponse((response) => matches(response, '/api/user/board-plans', 'GET'))
  await page.getByRole('link', { name: '先查看我的方案核对记录' }).click()
  const persistedPlans = await (await persistedPlansResponse).json()
  expect(persistedPlans.code).toBe(200)
  expect(JSON.stringify(persistedPlans.data)).toContain(unknownPlanName)
  await expect(page.getByRole('heading', { name: '我的方案' })).toBeVisible()
  transportFaults.push({ scenario: 'REAL_HTTP_WITH_TRANSPORT_FAULT_INJECTION', operation: 'persisted plan verified in My Plans after response loss', planName: unknownPlanName, result: 'passed' })

  // Small old-route smoke only: no AI prompt/provider call or write action is triggered.
  const routeChecks = [
    { path: '/skate', heading: '滑板专业', apiPaths: ['/api/skate/tricks'] },
    { path: '/community', heading: '看看大家今天练了什么', apiPaths: ['/api/public/news'] },
    { path: '/activities', heading: '同城约板活动', apiPaths: ['/api/public/places'] },
    { path: '/profile', heading: 't07_user', apiPaths: ['/api/user/profile'] },
    { path: '/ai-coach', heading: 'AI 滑板老师', apiPaths: ['/api/ai/coach/sessions'] }
  ]
  const oldRouteSmoke = []
  for (const route of routeChecks) {
    const endpointResponses = (route.apiPaths || [])
      .filter((apiPath) => !apiResponses.some((entry) => entry.path === apiPath && entry.status === 200))
      .map((apiPath) => page.waitForResponse((response) => new URL(response.url()).pathname === apiPath))
    await page.goto(route.path)
    await expect(page.getByRole('heading', { name: route.heading, exact: true })).toBeVisible()
    await Promise.all(endpointResponses)
    oldRouteSmoke.push({ path: route.path, heading: route.heading, rendered: true })
  }

  // A separate browser context exercises actual admin login and UI requests.
  const adminContext = await browser.newContext()
  try {
    const adminPage = await adminContext.newPage()
    observeApi(adminPage)
    await adminPage.goto('/login')
    await adminPage.getByRole('button', { name: '管理员登录' }).click()
    await adminPage.getByPlaceholder('请输入管理员账号').fill(process.env.T07_ADMIN_ACCOUNT)
    await adminPage.getByPlaceholder('请输入密码', { exact: true }).fill(process.env.T07_ADMIN_PASSWORD)
    await adminPage.getByPlaceholder('请输入验证码').fill('T07X')
    const adminLoginPromise = adminPage.waitForResponse((response) =>
      matches(response, '/api/auth/login/admin', 'POST'))
    await adminPage.getByRole('button', { name: '立即登录' }).first().click()
    const adminLogin = await (await adminLoginPromise).json()
    expect(adminLogin.code).toBe(200)
    await expect(adminPage).toHaveURL(/\/admin$/)
    const initialProductsPromise = adminPage.waitForResponse((response) =>
      matches(response, '/api/admin/board-assistant/products', 'GET'))
    await adminPage.getByRole('button', { name: /选板数据维护/ }).click()
    await expect(adminPage.locator('.board-page').getByRole('heading', { name: '选板数据维护' })).toBeVisible()
    await initialProductsPromise
    await adminPage.getByRole('button', { name: '新增产品草稿' }).click()
    await adminPage.getByLabel('稳定编号').fill('T07-UI-DRAFT')
    await adminPage.getByLabel('原始记录编号').fill('T07-UI-DRAFT')
    await adminPage.getByLabel('类别').selectOption('DECK')
    await adminPage.getByLabel('品牌').fill('SYNTHETIC')
    await adminPage.getByLabel('型号').fill('T07 UI draft')
    await adminPage.getByLabel('说明').fill('Synthetic test draft; not a product')
    await adminPage.getByLabel('图片授权状态').selectOption('UNKNOWN')
    await adminPage.getByRole('button', { name: '添加来源' }).click()
    await adminPage.getByLabel('来源链接').fill('https://example.invalid/t07-only')
    await adminPage.getByLabel('来源标题').fill('T07 合成测试来源（未核实）')
    await adminPage.getByLabel('采集时间（ISO8601）').fill('2026-10-10T00:00:00Z')
    await adminPage.getByLabel('核实状态（须有证据才能选择 VERIFIED）').selectOption('RECORDED')
    await adminPage.getByLabel('可复核证据位置').fill('synthetic://T07-UI-DRAFT')
    await adminPage.getByLabel('依据与待核实事项').fill('T07 test fixture only; no real source evidence')
    const createDraftPromise = adminPage.waitForResponse((response) =>
      matches(response, '/api/admin/board-assistant/products', 'POST'))
    await adminPage.getByRole('button', { name: '保存草稿' }).click()
    const createdDraft = await (await createDraftPromise).json()
    expect(createdDraft.code).toBe(200)
    expect(createdDraft.data.key).toBe('T07-UI-DRAFT')
    expect(createdDraft.data.active).toBe(false)
    adminObservations.push({ action: 'create draft through admin UI', apiCode: createdDraft.code, key: createdDraft.data.key, version: createdDraft.data.version })

    await adminPage.getByLabel('维护备注').fill('T07 synthetic draft edited through real admin UI')
    const updateDraftPromise = adminPage.waitForResponse((response) =>
      matches(response, '/api/admin/board-assistant/products/T07-UI-DRAFT', 'PATCH'))
    await adminPage.getByRole('button', { name: '保存编辑' }).click()
    const updatedDraft = await (await updateDraftPromise).json()
    expect(updatedDraft.code).toBe(200)
    expect(updatedDraft.data.version).toBe(2)
    adminObservations.push({ action: 'edit draft through admin UI', apiCode: updatedDraft.code, key: updatedDraft.data.key, version: updatedDraft.data.version })

    const draftListPromise = adminPage.waitForResponse((response) =>
      matches(response, '/api/admin/board-assistant/products', 'GET'))
    await adminPage.getByRole('button', { name: '返回目录（放弃未保存修改）' }).click()
    await draftListPromise
    const draftCard = adminPage.locator('article.board-item').filter({ hasText: 'T07-UI-DRAFT' }).first()
    await expect(draftCard).toContainText('T07 合成测试来源（未核实）')
    await adminPage.screenshot({ path: path.join(process.env.T07_EVIDENCE_DIR, 'T07-edge-admin-draft.png'), fullPage: true })

    // Test-only concurrent edit: leave the real UI's version 2 card stale, then verify its activation is rejected.
    const adminToken = await adminPage.evaluate(() => localStorage.getItem('skate_token'))
    const bumpResult = await adminPage.evaluate(async (token) => {
      const response = await fetch('/api/admin/board-assistant/products/T07-UI-DRAFT', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ expectedVersion: 2, notes: 'T07 concurrent version bump fixture' })
      })
      return { status: response.status, body: await response.json() }
    }, adminToken)
    expect(bumpResult.status).toBe(200)
    expect(bumpResult.body.data.version).toBe(3)
    adminObservations.push({ action: 'test-only concurrent draft version bump', status: bumpResult.status, apiCode: bumpResult.body.code, newVersion: 3 })

    const staleActivationPromise = adminPage.waitForResponse((response) =>
      matches(response, '/api/admin/board-assistant/products/T07-UI-DRAFT/activation', 'POST'))
    await draftCard.getByRole('button', { name: '启用T07-UI-DRAFT' }).click()
    await adminPage.getByRole('button', { name: '确认启停' }).click()
    const staleActivation = await (await staleActivationPromise).json()
    expect(staleActivation.data.errorCode).toBe('VERSION_CONFLICT')
    await expect(adminPage.locator('.board-page [role="alert"]')).toContainText('记录已被更新')

    const refreshedProductsPromise = adminPage.waitForResponse((response) =>
      matches(response, '/api/admin/board-assistant/products', 'GET'))
    await adminPage.getByRole('button', { name: '刷新目录' }).click()
    await refreshedProductsPromise
    const refreshedDraftCard = adminPage.locator('article.board-item').filter({ hasText: 'T07-UI-DRAFT' }).first()
    await expect(refreshedDraftCard).toContainText('版本 3')
    const failedActivationPromise = adminPage.waitForResponse((response) =>
      matches(response, '/api/admin/board-assistant/products/T07-UI-DRAFT/activation', 'POST'))
    await refreshedDraftCard.getByRole('button', { name: '启用T07-UI-DRAFT' }).click()
    await adminPage.getByRole('button', { name: '确认启停' }).click()
    const failedActivation = await (await failedActivationPromise).json()
    expect(failedActivation.data.errorCode).toBe('SELECTION_INVALID')
    await expect(adminPage.locator('.board-page [role="alert"]')).toContainText('未通过维护或启用校验')
    adminObservations.push(
      { action: 'stale draft activation', apiCode: staleActivation.code, errorCode: staleActivation.data.errorCode, currentVersion: staleActivation.data.currentVersion },
      { action: 'unverified source activation gate', apiCode: failedActivation.code, errorCode: failedActivation.data.errorCode, remainsDraft: true }
    )

    await adminPage.getByLabel('人民币预算（元）').fill('110.00')
    const trialPromise = adminPage.waitForResponse((response) =>
      matches(response, '/api/admin/board-assistant/trial', 'POST'))
    await adminPage.getByRole('button', { name: '运行试算' }).click()
    const trial = await (await trialPromise).json()
    expect(trial.code).toBe(200)
    expect(trial.data.assembly.plans.length).toBeGreaterThan(0)
    adminObservations.push({ action: 'trial through admin UI', apiCode: trial.code, completePlanCount: trial.data.complete.plans.length, assemblyPlanCount: trial.data.assembly.plans.length })
    await expect(adminPage.locator('.board-page')).toContainText('成品整板 · 找到方案')
    await expect(adminPage.locator('.board-page')).toContainText('组装 · 找到方案')
    await adminPage.screenshot({ path: path.join(process.env.T07_EVIDENCE_DIR, 'T07-edge-admin-trial.png'), fullPage: true })
  } finally {
    await adminContext.close()
  }

  await Promise.all(responseReads)
  const requiredRealEndpoints = [
    '/api/auth/login/user',
    '/api/skate/board-assistant/recommend',
    '/api/skate/board-assistant/compare',
    '/api/skate/board-assistant/replacements',
    '/api/user/board-plans',
    '/api/admin/board-assistant/products',
    '/api/admin/board-assistant/trial'
  ]
  for (const endpoint of requiredRealEndpoints) {
    expect(apiResponses.some((entry) => entry.path === endpoint && entry.status === 200), `${endpoint} returned real HTTP 200`).toBeTruthy()
  }
  const requiredLegacySmokeEndpoints = [
    '/api/skate/tricks',
    '/api/public/news',
    '/api/public/places',
    '/api/user/profile',
    '/api/ai/coach/sessions',
    '/api/admin/activities',
    '/api/admin/analytics'
  ]
  for (const endpoint of requiredLegacySmokeEndpoints) {
    expect(apiResponses.some((entry) => entry.path === endpoint && entry.status === 200 && entry.apiCode === 200), `${endpoint} returned application code 200`).toBeTruthy()
  }
  fs.writeFileSync(path.join(process.env.T07_EVIDENCE_DIR, 'T07-browser-observations.json'), JSON.stringify({
    evidenceKind: transportFaults.length ? 'REAL_HTTP_WITH_TRANSPORT_FAULT_INJECTION' : 'REAL_LOCAL_BROWSER_TO_VUE_PROXY_TO_SPRING_HTTP',
    apiResponses,
    failedRequests,
    transportFaults,
    adminObservations,
    oldRouteSmoke,
    continuationFlow: 'anonymous save action -> sessionStorage selection only -> real LoginPage user login via HTTP with T07X captcha test double -> server re-evaluation -> explicit reconfirmation -> persisted H2 snapshot',
    fixtureProvenance: 'Synthetic H2 only; all catalog items, prices, sources, and plans in the browser acceptance flow are labeled synthetic; real normalized catalog is tested separately in the HTTP integration test',
    mocks: 'No Playwright route interception for auth, catalog, calculation, replacement, comparison, plan, admin, or trial API routes; only captcha storage is a test double'
  }, null, 2))
})

test('T07 linked wheel adjustment requires explicit confirmation over real HTTP', async ({ page }) => {
  if (!process.env.T07_USER_NAME || !process.env.T07_USER_PASSWORD || !process.env.T07_EVIDENCE_DIR) {
    throw new Error('T07 synthetic login credentials and evidence directory are required')
  }
  const linkedResponses = []
  const linkedRequests = []
  const linkedSaveRequests = []
  page.on('request', (request) => {
    const url = new URL(request.url())
    if (request.method() !== 'POST') return
    try {
      if (url.pathname === '/api/skate/board-assistant/evaluate') linkedRequests.push(JSON.parse(request.postData() || '{}'))
      if (url.pathname === '/api/user/board-plans') linkedSaveRequests.push(JSON.parse(request.postData() || '{}'))
    } catch {}
  })
  page.on('response', async (response) => {
    const url = new URL(response.url())
    if (!url.pathname.startsWith('/api/')) return
    const entry = { method: response.request().method(), path: url.pathname, status: response.status() }
    if (response.headers()['content-type']?.includes('application/json')) {
      try {
        const body = await response.json()
        entry.apiCode = body.code ?? null
        if (url.pathname.endsWith('/evaluate')) entry.validationState = body.data?.validationState ?? null
      } catch {}
    }
    linkedResponses.push(entry)
  })
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名或手机号').fill(process.env.T07_USER_NAME)
  await page.getByPlaceholder('请输入密码', { exact: true }).fill(process.env.T07_USER_PASSWORD)
  await page.getByPlaceholder('请输入验证码').fill('T07X')
  const loginResponse = page.waitForResponse((response) => matches(response, '/api/auth/login/user', 'POST'))
  await page.getByRole('button', { name: '立即登录' }).first().click()
  expect((await (await loginResponse).json()).code).toBe(200)

  const continuation = {
    demand: { budgetCents: 2000, purpose: 'ACTION', preference: 'VALUE', recommendationType: 'ASSEMBLY', shoeSize: null },
    selection: { kind: 'ASSEMBLY', templateKey: 'SYN-T', items: [
      { category: 'DECK', variantKey: 'SYN-V-DECK' },
      { category: 'TRUCK', variantKey: 'SYN-V-TRUCK' },
      { category: 'WHEEL', variantKey: 'SYN-V-WHEEL' },
      { category: 'BEARING', variantKey: 'SYN-V-BEARING' },
      { category: 'GRIP', variantKey: 'SYN-V-GRIP' },
      { category: 'HARDWARE', variantKey: 'SYN-V-HARDWARE' }
    ] },
    name: 'T07 linked adjustment synthetic flow',
    note: 'SYNTHETIC only'
  }
  await page.evaluate((value) => sessionStorage.setItem('board-plan-continuation-v1', JSON.stringify(value)), continuation)
  await page.goto('/board-assistant')
  await expect(page.getByTestId('selected-plan')).toBeVisible()
  await expect(page.getByRole('button', { name: '替换轮子' })).toBeVisible()
  const initialEvaluations = linkedResponses.filter((entry) => entry.path === '/api/skate/board-assistant/evaluate' && entry.method === 'POST').length
  const replacementResponse = page.waitForResponse((response) => matches(response, '/api/skate/board-assistant/replacements', 'POST'))
  await page.getByRole('button', { name: '替换轮子' }).click()
  const replacements = await (await replacementResponse).json()
  expect(replacements.code).toBe(200)
  const altWheel = page.locator('article.board-item').filter({ hasText: 'SYNTHETIC WHEEL ALT' }).first()
  await expect(altWheel).toBeVisible()
  await altWheel.getByRole('button', { name: /检查替换/ }).click()
  await expect(page.getByRole('heading', { name: '确认替换差异' })).toBeVisible()
  const adjustmentConfirmation = page.locator('section.board-notice').filter({ has: page.getByRole('heading', { name: '确认替换差异' }) })
  await expect(adjustmentConfirmation).toContainText('SYN-V-RISER')
  expect(linkedResponses.filter((entry) => entry.path === '/api/skate/board-assistant/evaluate' && entry.method === 'POST')).toHaveLength(initialEvaluations)

  const confirmedEvaluation = page.waitForResponse((response) => matches(response, '/api/skate/board-assistant/evaluate', 'POST'))
  await page.getByRole('button', { name: '确认建议 1 并重算' }).click()
  const result = await (await confirmedEvaluation).json()
  expect(result.code).toBe(200)
  expect(result.data.errorCode, 'UI reused the candidate version after adding the riser; this fresh linked selection should be recalculated').toBeUndefined()
  expect(result.data.validationState).toBe('VERIFIED')
  expect(result.data.selection.items).toEqual(expect.arrayContaining([
    expect.objectContaining({ category: 'WHEEL', variantKey: 'SYN-V-WHEEL-ALT' }),
    expect.objectContaining({ category: 'RISER', variantKey: 'SYN-V-RISER' })
  ]))

  const confirmationRequest = linkedRequests.at(-1)
  expect(confirmationRequest).not.toHaveProperty('expectedCalculationVersion')
  const linkedPlanName = 'T07联动轮子垫片本人保存核对'
  await page.getByLabel('方案名称').fill(linkedPlanName)
  await page.getByLabel('备注').fill('SYNTHETIC only; linked adjustment re-evaluated over real local HTTP')
  const prepareSaveResponse = page.waitForResponse((response) => matches(response, '/api/skate/board-assistant/evaluate', 'POST'))
  await page.getByRole('button', { name: '重新计算并准备保存' }).click()
  const prepared = await (await prepareSaveResponse).json()
  expect(prepared.code).toBe(200)
  expect(linkedRequests.at(-1).expectedCalculationVersion).toBe(result.data.calculationVersion)
  const freshSaveToken = prepared.data.calculationVersion
  expect(freshSaveToken).toBeTruthy()

  const saveResponse = page.waitForResponse((response) => matches(response, '/api/user/board-plans', 'POST'))
  await page.getByRole('button', { name: '确认保存当前结果' }).click()
  const saved = await (await saveResponse).json()
  expect(saved.code).toBe(200)
  expect(linkedSaveRequests.at(-1).expectedCalculationVersion).toBe(freshSaveToken)
  expect(saved.data.totalCents).toBe(prepared.data.totalCents)
  await expect(page.locator('.board-notice[role="status"]')).toContainText('已保存方案')

  const plansListResponse = page.waitForResponse((response) => matches(response, '/api/user/board-plans', 'GET'))
  await page.getByRole('link', { name: '查看我的方案', exact: true }).click()
  const plans = await (await plansListResponse).json()
  expect(plans.code).toBe(200)
  const savedPlan = plans.data.list.find((plan) => plan.key === saved.data.key && plan.name === linkedPlanName)
  expect(savedPlan).toBeTruthy()
  const ownerReadResponse = page.waitForResponse((response) => {
    const url = new URL(response.url())
    return response.request().method() === 'GET' && url.pathname === `/api/user/board-plans/${encodeURIComponent(saved.data.key)}`
  })
  await page.locator('article.board-item').filter({ hasText: linkedPlanName }).getByRole('button', { name: `查看${linkedPlanName}` }).click()
  const ownerRead = await (await ownerReadResponse).json()
  expect(ownerRead.code).toBe(200)
  expect(ownerRead.data.snapshot.key).toBe(saved.data.key)
  expect(ownerRead.data.snapshot.totalCents).toBe(prepared.data.totalCents)
  expect(ownerRead.data.snapshot.selection.items).toEqual(expect.arrayContaining([
    expect.objectContaining({ category: 'WHEEL', variantKey: 'SYN-V-WHEEL-ALT' }),
    expect.objectContaining({ category: 'RISER', variantKey: 'SYN-V-RISER' })
  ]))
  await expect(page.getByTestId('saved-snapshot')).toContainText('SYNTHETIC WHEEL ALT')
  await expect(page.getByTestId('saved-snapshot')).toContainText('SYNTHETIC RISER')
  await page.screenshot({ path: path.join(process.env.T07_EVIDENCE_DIR, 'T07-edge-linked-adjustment.png'), fullPage: true })
  fs.writeFileSync(path.join(process.env.T07_EVIDENCE_DIR, 'T07-linked-browser-observations.json'), JSON.stringify({
    evidenceKind: 'REAL_LOCAL_BROWSER_TO_VUE_PROXY_TO_SPRING_HTTP',
    fixtureProvenance: 'SYNTHETIC H2 catalog and rules only; no real product, quote, or fitment claim',
    linkedWheelReplacement: 'real HTTP offered alternate wheel; proposed riser was displayed and applied only after explicit confirmation',
    confirmationRequest,
    confirmationResponse: result,
    freshConfirmationVersion: result.data.calculationVersion,
    prepareSaveRequest: linkedRequests.at(-1),
    prepareSaveResponse: prepared,
    formalSaveRequest: linkedSaveRequests.at(-1),
    formalSaveResponse: saved,
    ownerPlanListFound: Boolean(savedPlan),
    ownerReadResponse: ownerRead,
    apiResponses: linkedResponses
  }, null, 2))
})
