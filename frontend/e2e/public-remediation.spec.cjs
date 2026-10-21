const { test, expect } = require('@playwright/test')
const fs = require('fs'), path = require('path')
// Actual UI/browser; all API business data below are explicit local test doubles.
// H2 contract examples do not establish listening HTTP/MySQL/production acceptance.
const contracts = require('../tests/fixtures/public-remediation-20261021.json')
const recorded = require('../tests/fixtures/board/reference-600.json')
const svg='<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><rect width="80" height="80" fill="#ddd"/></svg>'
const envelope=data=>({code:200,message:'SYNTHETIC_BROWSER_ONLY',data})
const clone=value=>JSON.parse(JSON.stringify(value))
async function setup(page,custom=()=>undefined) {
 const errors=[];page.on('pageerror',e=>errors.push(e.message))
 page.on('console',message=>{
  if(message.type()!=='error')return
  const location=message.location().url || ''
  // Only this deliberate HTTP503 test resource may report its expected load failure.
  if(location.endsWith('/regions/china-area-5.0.1.json') && message.text().includes('503'))return
  errors.push(`console: ${message.text()} @ ${location}`)
 })
 await page.route('**/*',async route=>{
  const u=new URL(route.request().url())
  if(!['127.0.0.1','localhost'].includes(u.hostname)) {await route.fulfill({contentType:'image/svg+xml',body:svg});return}
  if(!u.pathname.startsWith('/api/'))return route.continue()
  const result=await custom(u,route.request(),route)
  if(result===true)return
  if(result!==undefined)return route.fulfill({json:envelope(result)})
  let data=[]
  if(['/api/public/posts','/api/public/activities','/api/activities','/api/skate/equipments'].includes(u.pathname))data={total:0,list:[]}
  if(u.pathname==='/api/public/posts/categories')return route.fulfill({json:contracts[u.pathname]})
  if(u.pathname==='/api/skate/equipments/categories')return route.fulfill({json:contracts[u.pathname]})
  if(u.pathname==='/api/public/users/3')data={userId:3,username:'SYNTHETIC用户',followerCount:0,avatar:'/test-original-avatar.svg'}
  if(u.pathname==='/api/public/users/3/posts')data={total:0,list:[]}
  if(u.pathname==='/api/skate/training-plans')data=[{planId:3,planName:'SYNTHETIC七天计划',durationDays:7}]
  if(u.pathname==='/api/skate/training-plans/3')return route.fulfill({json:contracts[u.pathname]})
  if(u.pathname==='/api/skate/board-assistant/recommend')data=clone(recorded.ACTION.response)
  if(u.pathname==='/api/public/places')data=[{placeId:3,name:'SYNTHETIC场地',score:4.9,reviewCount:203,localReviewSummary:{score:null,count:0,source:'LOCAL_REVIEWS'}}]
  if(u.pathname==='/api/public/places/3')data={placeId:3,name:'SYNTHETIC场地',score:4.9,reviewCount:203,localReviewSummary:{score:null,count:0,source:'LOCAL_REVIEWS'},obstacleTags:['rail'],safetyTips:['Check lighting before night sessions.']}
  if(u.pathname==='/api/public/bulletins/all')data=[{bulletinId:1,title:'SYNTHETIC 本周六活动',content:'本周六集合',createTime:'2026-05-01T12:00:00'}]
  if(u.pathname==='/api/public/bulletins/1')data={bulletinId:1,title:'SYNTHETIC 本周六活动',content:'本周六集合',createTime:'2026-05-01T12:00:00'}
  return route.fulfill({json:envelope(data)})
 })
 return errors
}
test('F01 map denied + region retry + parent switch + exact historical activity filter',async({page})=>{
 const requests=[];const errors=await setup(page,(u)=>{
  if(u.pathname==='/api/public/activities'){
   requests.push(Object.fromEntries(u.searchParams));const matched=u.searchParams.get('city')==='成都市'&&u.searchParams.get('district')==='郫都区'&&u.searchParams.get('expired')==='true'
   return {total:matched?1:0,list:matched?[{activityId:6,title:'SYNTHETIC replay of real activity region',city:'成都市',district:'郫都区',activityTime:'2026-01-01T12:00:00',status:'ENDED',activityStatus:'ENDED'}]:[]}
  }
 })
 let requestsForRegion=0
 await page.route('**/regions/china-area-5.0.1.json',async route=>{requestsForRegion++;if(requestsForRegion===1)return route.fulfill({status:503,body:'test failure'});return route.continue()})
 await page.goto('/activities');await expect(page.getByRole('button',{name:'重试省份资料'})).toBeVisible();await page.getByRole('button',{name:'重试省份资料'}).click()
 await page.getByLabel('省份（用于选择城市）').selectOption('四川省');await expect(page.getByLabel('城市',{exact:true})).toBeEnabled();await page.getByLabel('城市',{exact:true}).selectOption('成都市');await expect(page.getByLabel('区县')).toBeEnabled();await page.getByLabel('区县').selectOption('郫都区')
 await page.getByRole('button',{name:'加载地图',exact:true}).click();await expect(page.getByLabel('省份（用于选择城市）')).toBeEnabled()
 await page.locator('select').last().selectOption('expired');await page.getByRole('button',{name:'筛选',exact:true}).click();await expect(page.getByText('SYNTHETIC replay of real activity region')).toBeVisible()
 expect(requests.at(-1)).toMatchObject({city:'成都市',district:'郫都区',expired:'true'});expect(requests.at(-1)).not.toHaveProperty('province')
 await page.getByLabel('省份（用于选择城市）').selectOption('重庆市');await expect(page.getByLabel('城市',{exact:true})).toBeEnabled();await page.getByLabel('城市',{exact:true}).selectOption('重庆市');await expect(page.getByLabel('区县').locator('option').filter({hasText:'巫山县'})).toHaveCount(1)
 expect(errors).toEqual([])
})
test('F02/F08/F09/D01 real DOM fields, zero/precision comparison and null-price lower bound',async({page})=>{
 const errors=await setup(page,(u,req)=>{
  if(u.pathname.endsWith('/board-assistant/compare'))return {evaluations:[{...clone(recorded.ACTION.response.assembly.plans[0]),items:[{category:'RISER',variantKey:'SYN-R',specs:{heightMm:3.175}},{category:'RISER',variantKey:'SYN-R0',specs:{heightMm:0}}]}]}
 })
 await page.goto('/board-assistant');const budget=page.getByPlaceholder('例如 600.00')
 await budget.fill('0');await page.getByRole('button',{name:'查找方案',exact:true}).click();await expect(page.locator('#budget-error')).toContainText('元');await budget.fill('600');await expect(page.locator('#budget-error')).toHaveCount(0)
 await page.getByRole('button',{name:'查找方案',exact:true}).click();await page.getByRole('tab',{name:'配件组装（3）'}).click();await expect(page.locator('[data-section="reference"]')).toContainText('当前没有完整参考组装方案');await expect(page.locator('[data-section="pending"]')).toContainText('已知部分至少超预算186.20元');await expect(page.locator('[data-section="pending"]')).toContainText('完整总价未知')
 await page.getByRole('button',{name:'加入对比',exact:true}).first().click();await page.getByRole('button',{name:'重新计算并对比'}).click();await expect(page.locator('table')).toContainText('厚度（mm） 3.175');await expect(page.locator('table')).toContainText('厚度（mm） 0');expect(errors).toEqual([])
})
test('F03/F06/UX02 coverage, business-code category failure retry and truthful source link',async({page})=>{
 let categories=0;const errors=await setup(page,async(u,_req,route)=>{
  if(u.pathname==='/api/skate/equipments/categories'&&++categories===1){await route.fulfill({json:{code:500,message:'SYNTHETIC_CATEGORIES_FAILED'}});return true}
  if(u.pathname==='/api/skate/equipments')return {total:60,list:[{equipment:{equipmentId:1,equipmentName:'SYNTHETIC工具',category:'工具',buyUrl:'https://www.dobyheart.com/'}}]}
 })
 await page.goto('/skate');await expect(page.getByText('提交动作打卡需要登录，填写前请先登录；本页不会自动保存输入。')).toBeVisible();await page.getByRole('button',{name:'训练计划',exact:true}).click();await expect(page.getByLabel('任务资料覆盖')).toContainText('计划7天，已有3天任务，剩余资料待补充');await expect(page.locator('.task-row')).toHaveCount(3)
 await page.getByRole('button',{name:'装备库',exact:true}).click();await expect(page.getByRole('button',{name:'重试装备分类'})).toBeVisible();await page.getByRole('button',{name:'重试装备分类'}).click();await expect(page.getByLabel('装备分类').locator('option[value="工具"]')).toHaveCount(1);await expect(page.getByLabel('装备分类').locator('option[value="砂纸"]')).toHaveCount(1);await expect(page.getByRole('link',{name:'站点首页'})).toHaveAttribute('href','https://www.dobyheart.com/');expect(errors).toEqual([])
})
test('F04 same summary list/detail and failed reviews are not zero, raw tags remain associated',async({page})=>{
 let reviews=0;const errors=await setup(page,async(u,_req,route)=>{
  if(u.pathname==='/api/public/places/3/reviews'&&++reviews===1){await route.fulfill({json:{code:500,message:'SYNTHETIC_REVIEW_FAILED'}});return true}
 })
 await page.goto('/activities');await expect(page.getByText('暂无本站评分')).toBeVisible();await expect(page.getByText('203 条评价')).toHaveCount(0)
 await page.setViewportSize({width:390,height:844});await page.goto('/places/3');await expect(page.getByRole('button',{name:'重试评价'})).toBeVisible();await expect(page.getByText('还没有评价，来发布第一条吧。')).toHaveCount(0);await expect(page.getByText('暂无本站评分').first()).toBeVisible();await page.getByRole('button',{name:'重试评价'}).click();await expect(page.getByText('还没有评价，来发布第一条吧。')).toBeVisible();await expect(page.getByText('栏杆',{exact:true})).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);expect(errors).toEqual([])
})
test('F05 full category keys, exact history filter and publish free suggestions remain usable',async({page})=>{
 let selected;const errors=await setup(page,u=>{if(u.pathname==='/api/public/posts'){selected=u.searchParams.get('category');return {total:0,list:[]}}})
 await page.goto('/community');await expect(page.getByRole('button',{name:'装备讨论',exact:true})).toBeVisible();await page.getByRole('button',{name:'装备讨论',exact:true}).click();await expect.poll(()=>selected).toBe('装备讨论');await page.getByRole('button',{name:'全部',exact:true}).click();await expect.poll(()=>selected).toBe('');expect(errors).toEqual([])
})
test('F07 image original/fallback failures stop, zero fans and official beian text/link retained',async({page})=>{
 const errors=await setup(page);let avatars=0,decoration=0
 await page.route('**/test-original-avatar.svg',async r=>{avatars++;await r.fulfill({contentType:'image/svg+xml',body:'invalid image'})})
 await page.route('**/fallback/avatar.svg',async r=>{avatars++;await r.fulfill({contentType:'image/svg+xml',body:'invalid image'})})
 await page.route('**/www.beian.gov.cn/img/ghs.png',async r=>{decoration++;await r.fulfill({contentType:'image/png',body:'invalid image'})})
 await page.route('**/fallback/decoration.svg',async r=>{decoration++;await r.fulfill({contentType:'image/svg+xml',body:'invalid image'})})
 await page.goto('/users/3');await expect(page.getByText('暂无粉丝',{exact:true})).toBeVisible();await expect(page.locator('.profile-avatar')).toBeHidden();await expect(page.locator('.police-icon')).toBeHidden();expect(avatars).toBe(2);expect(decoration).toBe(2);await expect(page.getByRole('link',{name:'浙公网安备33080202000739号'})).toHaveAttribute('href','https://beian.mps.gov.cn/#/query/webSearch?code=33080202000739');expect(errors).toEqual([])
})
test('UX02 absolute bulletin date and accessible carousel current name',async({page})=>{
 const errors=await setup(page);await page.goto('/bulletins');await expect(page.getByText(/2026-05-01.*历史活动通知/).first()).toBeVisible();await page.goto('/bulletins/1');await expect(page.getByText(/2026-05-01.*历史活动通知/)).toBeVisible();await page.goto('/');await expect(page.locator('.dots button').first()).toHaveAttribute('aria-current','true');await expect(page.locator('.dots button').first()).toHaveAccessibleName(/切换到第1张/);expect(errors).toEqual([])
})

test('protected budgets 323/322.99/100, type filter and reset comparison on fresh demand (synthetic UI only)',async({page})=>{
 const observed=[];const errors=await setup(page,(u,req)=>{
  if(u.pathname.endsWith('/board-assistant/recommend')){
   const demand=req.postDataJSON();observed.push(demand);const data=clone(recorded.ACTION.response)
   for(const bucket of [data.complete,data.assembly]) for(const p of bucket.plans){p.demand=demand;if(p.totalCents!=null)p.budgetDeltaCents=demand.budgetCents-p.totalCents}
   if(demand.budgetCents<32300){const minimum=data.complete.plans.find(p=>p.totalCents===32300);minimum.demand.budgetCents=demand.budgetCents;minimum.budgetDeltaCents=demand.budgetCents-32300;data.complete={state:'BUDGET_INSUFFICIENT',plans:[],minimumFeasiblePlan:minimum,shortfallCents:32300-demand.budgetCents}}else{data.complete.plans=data.complete.plans.filter(p=>p.totalCents<=demand.budgetCents)}
   if(demand.recommendationType==='ASSEMBLY')data.complete={state:'NOT_REQUESTED',plans:[]}
   return data
  }
  if(u.pathname.endsWith('/board-assistant/compare'))return {evaluations:[clone(recorded.ACTION.response.complete.plans[1])]}
 })
 await page.setViewportSize({width:390,height:844});await page.goto('/board-assistant');await page.getByPlaceholder('例如 600.00').fill('323');await page.getByPlaceholder('仅记录，不参与排序').fill('42');await page.getByRole('button',{name:'查找方案',exact:true}).click();await expect(page.getByText('参考估价 ¥323.00',{exact:true})).toBeVisible()
 await page.getByRole('button',{name:'加入对比',exact:true}).first().click();await page.getByRole('button',{name:'重新计算并对比'}).click();await expect(page.locator('table')).toBeVisible()
 await page.getByPlaceholder('例如 600.00').fill('322.99');await page.getByRole('button',{name:'查找方案',exact:true}).click();await expect(page.getByText('参考预算差额 ¥0.01',{exact:true})).toBeVisible();await expect(page.locator('table')).toHaveCount(0)
 await page.getByPlaceholder('例如 600.00').fill('100');await page.getByRole('button',{name:'查找方案',exact:true}).click();await expect(page.getByText('参考预算差额 ¥223.00',{exact:true})).toBeVisible();await page.getByLabel('推荐类型').selectOption('ASSEMBLY');await page.getByRole('button',{name:'查找方案',exact:true}).click();await expect(page.getByRole('tab',{name:'配件组装（3）'})).toHaveAttribute('aria-selected','true')
 expect(observed.map(x=>x.budgetCents)).toEqual([32300,32299,10000,10000]);expect(observed.at(-1)).toMatchObject({recommendationType:'ASSEMBLY',shoeSize:{value:42,system:'CN'}});expect(observed.at(-1)).not.toHaveProperty('widthInch');expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);expect(errors).toEqual([])
})
