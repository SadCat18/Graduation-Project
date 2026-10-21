const {test,expect}=require('@playwright/test')
const fs=require('node:fs')
const ids=JSON.parse(fs.readFileSync(process.env.PUBLIC_REVIEW_IDS,'utf8'))
// API traffic goes to the real isolated application/security/H2 server. Only external media
// and the explicitly labelled region failure are substituted; these do not prove live media.
async function setup(page){
 const errors=[]
 page.on('pageerror',e=>errors.push(e.message))
 page.on('console',message=>{
  if(message.type()!=='error')return
  const location=message.location().url||''
  if(location.endsWith('/regions/china-area-5.0.1.json')&&message.text().includes('503'))return
  errors.push(`${message.text()} @ ${location}`)
 })
 await page.route('**/*',route=>{
  const url=new URL(route.request().url())
  if(['127.0.0.1','localhost'].includes(url.hostname))return route.continue()
  return route.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" fill="#ddd"/></svg>'})
 })
 return errors
}
test('real HTTP training coverage, same-source ratings and complete equipment/category endpoints',async({page})=>{
 const errors=await setup(page)
 await page.goto('/skate')
 await expect(page.getByText('提交动作打卡需要登录，填写前请先登录；本页不会自动保存输入。')).toBeVisible()
 await page.getByRole('button',{name:'训练计划',exact:true}).click()
 await page.getByRole('button',{name:/^ISOLATED seven-day review/}).click()
 await expect(page.getByLabel('任务资料覆盖')).toContainText('计划7天，已有3天任务，剩余资料待补充')
 await expect(page.getByLabel('任务资料覆盖')).toContainText('4、5、6、7')
 await expect(page.locator('.task-row')).toHaveCount(3)
 await page.getByRole('button',{name:'装备库',exact:true}).click()
 await expect(page.getByLabel('装备分类').locator('option[value="工具"]')).toHaveCount(1)
 await expect(page.getByLabel('装备分类').locator('option[value="砂纸"]')).toHaveCount(1)
 await page.getByLabel('装备分类').selectOption('工具')
 await expect(page.getByText('ISOLATED 工具',{exact:true})).toBeVisible()
 await page.getByLabel('装备分类').selectOption('')
 await page.goto(`/places/${ids.zeroPlace}`)
 await expect(page.getByText('暂无本站评分').first()).toBeVisible()
 await expect(page.getByText('还没有评价，来发布第一条吧。')).toBeVisible()
 await expect(page.getByText('203 条评价')).toHaveCount(0)
 await page.goto(`/places/${ids.ratedPlace}`)
 await expect(page.getByText('2 条本站评价').first()).toBeVisible()
 await expect(page.locator('.review-card')).toHaveCount(2)
 expect(errors).toEqual([])
})
test('recorded board API: exact budgets, null total lower bound, distinct thicknesses, reset and shoe recording',async({page})=>{
 const errors=await setup(page),requests=[]
 page.on('request',request=>{if(request.url().endsWith('/api/skate/board-assistant/recommend'))requests.push(request.postDataJSON())})
 await page.goto('/board-assistant')
 const budget=page.getByPlaceholder('例如 600.00')
 await budget.fill('0');await page.getByRole('button',{name:'查找方案',exact:true}).click();await expect(page.locator('#budget-error')).toContainText('元')
 await budget.fill('600');await expect(page.locator('#budget-error')).toHaveCount(0)
 await page.getByPlaceholder('仅记录，不参与排序').fill('42')
 await page.getByRole('button',{name:'查找方案',exact:true}).click()
 await page.getByRole('tab',{name:'配件组装（3）'}).click()
 await expect(page.locator('[data-section="reference"]')).toContainText('当前没有完整参考组装方案')
 await expect(page.locator('[data-section="pending"]')).toContainText('已知部分至少超预算186.20元')
 await expect(page.locator('[data-section="pending"]')).toContainText('完整总价未知')
 const add=page.getByRole('button',{name:'加入对比',exact:true});await add.nth(0).click();await add.nth(0).click()
 await page.getByRole('button',{name:'重新计算并对比'}).click()
 await expect(page.locator('table')).toContainText('厚度（mm） 12.7');await expect(page.locator('table')).toContainText('厚度（mm） 3.175')
 expect(requests[0].shoeSize).toEqual({value:42,system:'CN'})
 await page.getByLabel('推荐类型').selectOption('COMPLETE')
 for(const [amount,expected] of [['323.00','323.00'],['322.99','0.01'],['100','223.00']]){
  await budget.fill(amount);await page.getByRole('button',{name:'查找方案',exact:true}).click()
  await expect(page.getByRole('tab',{name:/成品整板/})).toHaveAttribute('aria-selected','true')
  await expect(page.locator('table')).toHaveCount(0)
  await expect(page.locator('.board-page')).toContainText(expected)
 }
 expect(requests.slice(1).every(r=>r.recommendationType==='COMPLETE')).toBe(true)
 expect(errors).toEqual([])
})
test('regions independent real static snapshot, controlled failure retry, exact HTTP query and full historical keys',async({page})=>{
 const errors=await setup(page);let count=0,query
 await page.route('**/regions/china-area-5.0.1.json',route=>++count===1?route.fulfill({status:503,body:'ISOLATED_REGION_FAILURE'}):route.continue())
 page.on('request',request=>{const url=new URL(request.url());if(url.pathname==='/api/public/activities')query=Object.fromEntries(url.searchParams)})
 await page.goto('/activities');await page.getByRole('button',{name:'重试省份资料'}).click()
 await page.getByLabel('省份（用于选择城市）').selectOption('四川省');await page.getByLabel('城市',{exact:true}).selectOption('成都市');await page.getByLabel('区县').selectOption('郫都区')
 await page.locator('select').last().selectOption('expired');await page.getByRole('button',{name:'筛选',exact:true}).click()
 await expect.poll(()=>query).toMatchObject({city:'成都市',district:'郫都区',expired:'true'});expect(query).not.toHaveProperty('province')
 await page.getByLabel('省份（用于选择城市）').selectOption('重庆市');await page.getByLabel('城市',{exact:true}).selectOption('重庆市');await expect(page.getByLabel('区县').locator('option').filter({hasText:'巫山县'})).toHaveCount(1)
 await page.goto('/community');await expect(page.getByRole('button',{name:'ISOLATED history 30',exact:true})).toBeVisible();await page.getByRole('button',{name:'装备讨论',exact:true}).click();await expect(page.locator('.post-card-item').first()).toContainText('装备讨论')
 await page.goto(`/users/${ids.user}`);await expect(page.getByText('暂无粉丝',{exact:true})).toBeVisible();await expect(page.locator('.avatar-stack')).toHaveCount(0)
 expect(errors).toEqual([])
})
