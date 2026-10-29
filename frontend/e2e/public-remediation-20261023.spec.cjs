const {test,expect}=require('@playwright/test')
const reference=require('../tests/fixtures/public-remediation-20261023/reference-examples.json')
const metadata=require('../tests/fixtures/public-remediation-20261023/category-examples.json')['GET /api/public/posts/categories']
const clone=v=>JSON.parse(JSON.stringify(v))
const categories=['板面','桥','轮子','轴承','砂纸','螺丝','垫片','头盔','护具','工具']
// UI contract tests: recorded isolated-H2 examples + explicit synthetic lists, never live service acceptance.
async function setup(page,custom,logged=false){
 const calls=[],errors=[]
 if(logged)await page.addInitScript(()=>{localStorage.setItem('skate_token','SYNTHETIC_ONLY');localStorage.setItem('skate_role','USER')})
 page.on('pageerror',e=>errors.push(e.message))
 page.on('console',m=>{if(m.type()==='error' && !(m.location().url.endsWith('/api/public/news')&&m.text().includes('503')))errors.push(m.text())})
 await page.route('**/*',async route=>{
  const req=route.request(),u=new URL(req.url()),p=u.pathname
  if(!['localhost','127.0.0.1'].includes(u.hostname))return route.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"/>'})
  if(!p.startsWith('/api/'))return route.continue()
  calls.push({p,query:Object.fromEntries(u.searchParams),body:req.method()==='GET'?null:req.postDataJSON()})
  const override=custom ? await custom({p,u,req,route}) : undefined
  if(override===true)return
  let data=override
  if(data===undefined){
   data=[]
   if(['/api/public/posts','/api/public/activities','/api/activities'].includes(p))data={list:[],total:0}
   if(p==='/api/public/posts/categories')data=metadata
   if(p==='/api/skate/equipments/categories')data=categories.map(key=>({key,label:key}))
   if(p==='/api/skate/equipments')data={list:Array.from({length:18},(_,i)=>({equipment:{equipmentId:i+1,category:categories[i%categories.length],equipmentName:`类别选购示例 ${i+1}`,description:'示意知识资料，不是商品核价',specParams:'widthInch 8.0',buyUrl:'https://example.invalid/'}})),total:18}
   if(p.endsWith('/board-assistant/recommend'))data={complete:{state:'NOT_REQUESTED',plans:[]},assembly:{state:'DATA_INSUFFICIENT',plans:[clone(reference.sameCatalogAfterExpiry)]}}
   if(p.includes('/board-assistant/variants/')){const key=decodeURIComponent(p.split('/variants/')[1]);data=key.startsWith('TRUCK-006')?reference.v2KruxAdvice:key.startsWith('BRG-008')?reference.endedBearingQuote:key.startsWith('HW-001')?reference.v2Allen1Inch:reference.ordinaryDeckReference}
  }
  await route.fulfill({json:{code:200,message:'SYNTHETIC_UI_WITH_RECORDED_H2_EXAMPLES',data}})
 })
 return {calls,errors}
}
async function noOverflow(page){expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)}
async function screenshot(page,testInfo,name){await page.screenshot({path:testInfo.outputPath(name+'.png'),fullPage:false})}

test('four groups work by keyboard and anchors; direct tabs survive refresh and history',async({page},info)=>{
 const observed=await setup(page);await page.setViewportSize({width:1165,height:850});await page.goto('/skate?tab=equipment')
 await expect(page.locator('.equipment-card')).toHaveCount(18);await expect(page.locator('.equipment-card img')).toHaveCount(0);await expect(page.locator('.equipment-card').first()).toContainText('非商品照片');await expect(page.locator('.equipment-card a').first()).toHaveText('站点首页')
 const nav=page.getByRole('navigation',{name:'主导航',exact:true}),learn=nav.getByRole('button',{name:'学滑板',exact:true})
 await learn.focus();await page.keyboard.press('Space');await expect(learn).toHaveAttribute('aria-expanded','true');await page.keyboard.press('Tab');await expect(nav.getByRole('link',{name:'学习路径及动作库'})).toBeFocused();await page.keyboard.press('Escape');await expect(learn).toBeFocused();await expect(learn).toHaveAttribute('aria-expanded','false')
 await learn.press('Enter');await nav.getByRole('link',{name:'训练计划',exact:true}).click();await expect(page).toHaveURL(/tab=training/);await expect(page.getByRole('tab',{name:'训练计划',exact:true})).toHaveAttribute('aria-selected','true');await page.reload();await expect(page.getByRole('tab',{name:'训练计划',exact:true})).toHaveAttribute('aria-selected','true');await page.goBack();await expect(page).toHaveURL(/tab=equipment/);await expect(page.locator('.equipment-card')).toHaveCount(18)
 await nav.getByRole('button',{name:'社区',exact:true}).click();await nav.getByRole('link',{name:'滑板资讯',exact:true}).click();await expect(page).toHaveURL(/\/#skate-news$/);await expect(page.locator('#skate-news')).toBeFocused();expect((await page.locator('#skate-news').boundingBox()).y).toBeGreaterThan(60)
 await page.getByRole('link',{name:'滑板知识网站首页'}).click();await expect(page).toHaveURL(/\/$/)
 await page.goto('/skate?tab=invalid');await expect(page.getByRole('tab',{name:'动作打卡'})).toHaveAttribute('aria-selected','true');await screenshot(page,info,'desktop-navigation');expect(observed.errors).toEqual([])
})

test('mobile group disclosure and category guides stay usable without overflow',async({page},info)=>{
 const observed=await setup(page)
 for(const width of [768,390]){
  await page.setViewportSize({width,height:844});await page.goto('/skate?tab=equipment');await expect(page.locator('.equipment-card')).toHaveCount(18)
  const nav=page.getByRole('navigation',{name:'移动端主导航'});await nav.getByRole('button',{name:'选装备',exact:true}).click();await expect(nav.getByRole('link',{name:'我的方案'})).toBeVisible();await page.keyboard.press('Escape');await expect(nav.getByRole('button',{name:'选装备',exact:true})).toBeFocused();await noOverflow(page);await screenshot(page,info,'guides-'+width)
 }
 expect(observed.errors).toEqual([])
})

test('long result details and return preserve stable card focus; optional inputs survive folding',async({page},info)=>{
 const current=clone(reference.sameCatalogAfterExpiry),plans=Array.from({length:18},(_,i)=>({...clone(current),selection:{...clone(current.selection),templateKey:'SYNTHETIC-'+i}}))
 const observed=await setup(page,({p})=>p.endsWith('/board-assistant/recommend')?{complete:{state:'NOT_REQUESTED',plans:[]},assembly:{state:'DATA_INSUFFICIENT',plans}}:undefined)
 await page.goto('/board-assistant');await page.getByText('补充信息（选填）',{exact:true}).click();await page.getByLabel('鞋码（选填）').fill('42');await page.getByLabel('尺码体系').selectOption('EU');await page.getByText('补充信息（选填）',{exact:true}).click();await page.getByLabel('人民币预算（元）').fill('600');await page.getByLabel('推荐类型').selectOption('ASSEMBLY');await page.getByRole('button',{name:'查找方案',exact:true}).click()
 const heading=page.getByRole('heading',{name:'推荐结果',exact:true});await expect(heading).toBeFocused();await expect(page.getByText(/找到 18 套候选/)).toBeVisible();expect(observed.calls.find(c=>c.p.endsWith('/recommend')).body.shoeSize).toEqual({value:42,system:'EU'})
 const source=page.getByRole('button',{name:'查看方案 18',exact:true});await source.scrollIntoViewIfNeeded();await source.click();const detail=page.getByTestId('selected-plan');await expect(detail.getByRole('heading',{name:'组装方案',exact:true})).toBeFocused();expect((await detail.getByRole('heading',{name:'组装方案',exact:true}).boundingBox()).y).toBeGreaterThan(60)
 await expect(detail).toContainText('已知部分至少超预算71.00元');await expect(detail).toContainText('完整总价未知');await expect(detail).not.toContainText('OVER_BUDGET');await expect(detail).not.toContainText('purchase-variant-unresolved');await expect(detail).not.toContainText('quoteKey')
 await detail.getByText('查看配件规格、包装与来源',{exact:true}).click();await detail.getByRole('button',{name:'查看当前规格与历史报价'}).first().click();await expect(detail.getByText('当前来源资料（不会改写上方原方案）')).toBeVisible();await detail.getByText('参考与历史价格来源',{exact:true}).click();await expect(detail).toContainText('历史价格，不计入当前参考');await screenshot(page,info,'source-history')
 await detail.getByRole('button',{name:'返回推荐结果'}).click();await expect(source).toBeFocused();await noOverflow(page);expect(observed.errors).toEqual([])
})

test('recommended topic expansion, exact legacy filters and shareable pagination use same metadata',async({page})=>{
 const rows=[...Array.from({length:7},(_,i)=>({postId:i+1,title:`练习装备 ${i}`,category:i%2?'装备':'装备讨论',content:'练习'})),{postId:8,title:'练习文化',category:'文化'},{postId:9,title:'练习拍摄',category:'拍摄'}]
 const observed=await setup(page,({p})=>p==='/api/user/recommendations'?{posts:[...rows,rows[0]]}:undefined,true)
 await page.goto('/community?category=装备&sort=recommended&page=2&keyword=练习');await expect(page.locator('.post-card-item')).toHaveCount(1);await expect(page.getByText(/推荐排序 · 共 7 条/)).toBeVisible();await page.reload();await expect(page.locator('.post-card-item')).toHaveCount(1)
 await page.getByText('更多标签',{exact:false}).click();await page.getByRole('button',{name:'文化',exact:true}).click();await expect(page).toHaveURL(/category=.*sort=recommended.*page=1/);await expect(page.locator('.post-card-item')).toHaveCount(1);await expect(page.locator('.post-card-item')).toContainText('练习文化');await expect(page.getByText(/推荐排序 · 共 1 条/)).toBeVisible()
 await page.getByRole('button',{name:'经验分享',exact:true}).click();await expect(page.locator('.post-card-item')).toHaveCount(2);await page.goBack();await expect(page.locator('.post-card-item')).toHaveCount(1);await expect(page.locator('.post-card-item')).toContainText('练习文化');expect(observed.errors).toEqual([])
})

test('home deferred, in-flight, error, retry and true empty states remain distinct',async({page},info)=>{
 let primaryRelease,newsRelease,newsCount=0
 const primary=new Promise(r=>{primaryRelease=r}),news=new Promise(r=>{newsRelease=r})
 const observed=await setup(page,async({p,route})=>{
  if(p==='/api/public/posts')await primary
  if(p==='/api/public/news'){newsCount++;if(newsCount===1){await news;await route.fulfill({status:503,json:{code:503,message:'SYNTHETIC_FAILURE'}});return true}return []}
 })
 await page.goto('/');await expect(page.getByText('滑板资讯加载中…').first()).toBeVisible();expect(newsCount).toBe(0);await expect(page.getByText('暂无滑板资讯数据。')).toHaveCount(0);primaryRelease();await expect.poll(()=>newsCount).toBe(1);await expect(page.getByText('滑板资讯加载中…').first()).toBeVisible();newsRelease();await expect(page.getByRole('alert').first()).toContainText('资讯暂时加载失败');await screenshot(page,info,'home-error');await page.getByRole('button',{name:'重试资讯'}).first().click();await expect(page.getByText('暂无滑板资讯数据。')).toBeVisible();expect(newsCount).toBe(2);expect(observed.errors).toEqual([])
})

test('low budget and expired fixed selection stay separate from unknown total and historical notices',async({page},info)=>{
 const observed=await setup(page,({p,req})=>p.endsWith('/board-assistant/recommend')&&req.postDataJSON().budgetCents===100?{complete:{state:'BUDGET_INSUFFICIENT',plans:[]},assembly:{state:'BUDGET_INSUFFICIENT',plans:[]}}:undefined)
 await page.goto('/board-assistant');await page.getByLabel('人民币预算（元）').fill('1');await page.getByRole('button',{name:'查找方案',exact:true}).click();await expect(page.getByText('当前预算内没有完整可行方案。不会自动提高预算。')).toBeVisible();await expect(page.locator('[data-section]')).toHaveCount(0)
 await page.getByLabel('人民币预算（元）').fill('600');await page.getByLabel('推荐类型').selectOption('ASSEMBLY');await page.getByRole('button',{name:'查找方案',exact:true}).click();await expect(page.getByText('按当前参考明细，已知部分至少超预算71.00元')).toBeVisible();await screenshot(page,info,'partial-budget')
 await page.goto('/activities');for(const width of [1165,768,390]){await page.setViewportSize({width,height:844});await noOverflow(page);if(width===1165)expect((await page.getByLabel('省份（用于选择城市）').boundingBox()).width).toBeGreaterThan(140);await screenshot(page,info,'regions-'+width)}
 expect(observed.errors).toEqual([])
})
test('historical relative-date activities stay archived while evergreen material can lead',async({page},info)=>{
 const observed=await setup(page,({p})=>p==='/api/public/bulletins/all'?[{bulletinId:1,title:'本周六集合练习',content:'本周六来练习',createTime:'2026-05-19T12:00:00'},{bulletinId:2,title:'常青资料：出门前安全检查',content:'核对护具和路面',createTime:'2026-03-01T12:00:00'}]:undefined)
 await page.goto('/bulletins');await expect(page.locator('.top-card')).toContainText('常青资料：出门前安全检查');await expect(page.locator('.historical-bulletins')).toContainText('本周六集合练习');await expect(page.locator('.historical-bulletins')).toContainText('2026-05-19');await expect(page.locator('.historical-bulletins')).toContainText('实际活动日期请核对原文');await screenshot(page,info,'historical-notices');expect(observed.errors).toEqual([])
})


test('R01 real review text is user-facing across list detail compare and replacement; source facts stay immutable',async({page},info)=>{
 const catalog=require('../../docs/equipment-data/normalized-equipment.reference.v2.json')
 const plan=clone(reference.sameCatalogAfterExpiry)
 plan.checks=catalog.rules.filter(r=>r.explanation?.includes('契约必需件') || r.explanation?.includes('禁止执行为通过结论')).filter((r,i,a)=>a.findIndex(x=>x.explanation===r.explanation)===i).map(r=>({...clone(r),state:'INSUFFICIENT_DATA'}))
 const wheel=catalog.variants.find(v=>v.fieldEvidence?.some(e=>e.limitations?.some(l=>l.startsWith('HTTP403'))))
 plan.items=plan.items.map(item=>item.category==='WHEEL'?{...item,fieldEvidence:clone(wheel.fieldEvidence)}:item)
 const before=JSON.stringify(plan)
 const observed=await setup(page,({p})=>p.endsWith('/recommend')?{complete:{state:'NOT_REQUESTED',plans:[]},assembly:{state:'DATA_INSUFFICIENT',plans:[plan]}}:p.endsWith('/compare')?{evaluations:[plan]}:p.endsWith('/replacements')?{list:[plan],total:1}:undefined)
 await page.goto('/board-assistant');await page.getByLabel('人民币预算（元）').fill('600');await page.getByLabel('推荐类型').selectOption('ASSEMBLY');await page.getByRole('button',{name:'查找方案',exact:true}).click()
 await expect(page.getByText('已按方案所需配件数量核对；是否需要垫片及实际搭配是否适用，仍需确认。',{exact:false}).first()).toBeVisible()
 await page.getByRole('button',{name:'加入对比',exact:true}).first().click();await page.getByRole('button',{name:'重新计算并对比'}).click()
 await expect(page.locator('.board-comparison')).toContainText('不能据此确认搭配兼容')
 await page.getByRole('button',{name:'查看方案 1',exact:true}).click();const detail=page.getByTestId('selected-plan')
 await detail.getByText('查看配件规格、包装与来源',{exact:true}).click()
 const wheelArticle=detail.locator('article.board-item').filter({has:page.getByRole('heading',{name:/轮子 ·/})})
 await wheelArticle.getByText('逐项核对来源与适用范围',{exact:true}).click()
 await expect(wheelArticle).toContainText('来源网站拒绝访问');await expect(wheelArticle).toContainText('轮宽与接地面仍未知')
 await expect(wheelArticle.getByRole('link',{name:'查看本项原来源'})).toHaveAttribute('href',wheel.fieldEvidence[0].url)
 await expect(detail).toContainText('完整总价未知');await expect(detail).toContainText('至少超预算71.00元')
 await detail.getByRole('button',{name:'替换轮子',exact:true}).click();await expect(detail).toContainText('第1页 / 1条')
 for(const term of ['契约','任务书','端点包含性','禁止执行','HTTP403','Do not label'])await expect(page.locator('body')).not.toContainText(term)
 expect(JSON.stringify(plan)).toBe(before);expect(observed.errors).toEqual([])
 await wheelArticle.scrollIntoViewIfNeeded();await screenshot(page,info,'r01-public-source')
})
