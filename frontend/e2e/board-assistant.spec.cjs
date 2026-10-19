// APIs are mocked. F01 uses synthetic fixtures; REFERENCE-01 replays recorded H2 examples
// with explicit synthetic controls. This suite never validates live prices or server permissions.
const { test, expect } = require('@playwright/test')
const fs = require('fs')
const path = require('path')
const fixtures = path.resolve(__dirname, '../tests/fixtures/board')
const evidence = path.resolve(__dirname, '../test-results/board')
fs.mkdirSync(evidence, {recursive:true})
const apiExamples = JSON.parse(fs.readFileSync(path.join(fixtures, 'B01-api-examples.json'), 'utf8'))
const snapshotExamples = JSON.parse(fs.readFileSync(path.join(fixtures, 'B01-snapshot-examples.json'), 'utf8'))
const referenceExamples = JSON.parse(fs.readFileSync(path.join(fixtures, 'reference-600.json'), 'utf8'))
const referenceSnapshots = JSON.parse(fs.readFileSync(path.join(fixtures, 'reference-snapshots.json'), 'utf8'))
const referenceResponse = () => clone(referenceExamples.ACTION.response)
const referenceNotice = '按已采集资料估算，实际价格和库存请通过来源自行确认。'
const clone = value => JSON.parse(JSON.stringify(value))
const envelope = data => ({ code: 200, message: 'SYNTHETIC UI fixture only', data })
const evaluation = () => clone(apiExamples.responses.evaluate)
const recommend = () => clone(apiExamples.responses.recommend)
const bucket = state => ({ state,plans:[],missing:['SYNTHETIC_ONLY: missing verified source'],excludedCounts:{DATA_INSUFFICIENT:1} })
const calls = () => ({save:[],evaluate:[],replace:[],compare:[],admin:[]})
async function mockApi(page, observed, handler) {
  await page.route('**/api/**', async route => {
    const req = route.request(), url = new URL(req.url()), p = url.pathname, body = req.method() === 'GET' ? null : req.postDataJSON()
    const custom = handler ? await handler({p,body,req,route}) : undefined
    if(custom === true) return
    if(custom !== undefined) { await route.fulfill({json:envelope(custom)}); return }
    if(p === '/api/auth/captcha') { await route.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="120" height="40"><text x="8" y="28">1234</text></svg>'}); return }
    let data = {total:0,list:[]}
    if(p === '/api/auth/login/user') data = {token:'SYNTHETIC-UI-TOKEN',role:'USER',name:'合成测试滑手'}
    if(p.endsWith('/board-assistant/recommend')) data = recommend()
    if(p.endsWith('/board-assistant/evaluate')) { observed.evaluate.push(body); data = evaluation() }
    if(p.endsWith('/board-assistant/replacements')) { observed.replace.push(body); data = clone(apiExamples.responses.linkedReplacement) }
    if(p.endsWith('/board-assistant/compare')) { observed.compare.push(body); data={evaluations:body.plans.map(p=>p.selection.kind === 'COMPLETE' ? recommend().complete.plans[0] : evaluation())} }
    if(p === '/api/user/board-plans' && req.method() === 'POST') { observed.save.push(body); data={...clone(snapshotExamples.saveResponse),name:body.name,note:body.note,key:'SYN-UI-SAVED'} }
    if(p === '/api/user/board-plans' && req.method() === 'GET') data={total:1,list:[clone(snapshotExamples.saveResponse)]}
    if(p.startsWith('/api/user/board-plans/')) {
      if(req.method() === 'GET') data={snapshot:clone(snapshotExamples.saveResponse),latestEvaluation:{...evaluation(),totalCents:9000}}
      if(req.method() === 'PATCH') data={...clone(snapshotExamples.saveResponse),...body}
      if(p.endsWith('/copy')) data={...clone(snapshotExamples.copyResponse),key:'SYN-UI-COPY',historicalCopy:true}
      if(req.method() === 'DELETE') data=null
    }
    if(p.endsWith('/unread-count')) data={unreadCount:0}
    if(['/api/admin/reports','/api/admin/bulletins','/api/admin/places/pending','/api/admin/place-reviews','/api/admin/news'].includes(p)) data=[]
    if(p.endsWith('/stats') || p.endsWith('/analytics')) data={trend7d:[],hotPosts:[],hotActivities:[],bulletinTypeRatio:[]}
    await route.fulfill({json:envelope(data)})
  })
}
async function signIn(page,role='USER') {
  await page.addInitScript(role=>{localStorage.setItem('skate_token','SYNTHETIC-UI-TOKEN');localStorage.setItem('skate_role',role);localStorage.setItem('skate_name','合成测试身份')},role)
}
async function findPlans(page) {
  await page.goto('/board-assistant');await page.getByLabel('人民币预算（元）').fill('1000.00');await page.getByRole('button',{name:'查找方案',exact:true}).click();await expect(page.getByRole('heading',{name:'推荐结果',exact:true})).toBeVisible()
}
async function assembly(page) {await page.getByRole('tab',{name:/配件组装/}).click();await page.getByRole('button',{name:'查看方案 1',exact:true}).click()}
async function shot(page,name) {await page.evaluate(()=>window.scrollTo(0,0));await page.screenshot({path:path.join(evidence,`F01-${name}.png`),fullPage:true})}

test('SYNTHETIC anonymous → login → recalculate → explicit save; conflict reconfirmation',async({page})=>{
  const observed=calls();let conflict=true
  await mockApi(page,observed,({p,body,req})=>{
    if(p === '/api/user/board-plans' && req.method()==='POST' && conflict) {observed.save.push(body);conflict=false;return {errorCode:'PRICE_CHANGED',requiresConfirmation:true,evaluation:{...evaluation(),calculationVersion:'SYN-CHANGED',totalCents:2500}}}
  })
  await findPlans(page);await assembly(page)
  await page.getByLabel('方案名称',{exact:true}).fill('合成续接方案');await page.getByLabel('备注',{exact:true}).fill('仅mock证据')
  await page.getByRole('button',{name:'重新计算并准备保存',exact:true}).click();await expect(page).toHaveURL(/login\?returnTo/)
  const stored=await page.evaluate(()=>JSON.parse(sessionStorage.getItem('board-plan-continuation-v1')));expect(stored.name).toBe('合成续接方案');expect(stored).not.toHaveProperty('expectedCalculationVersion')
  await page.getByPlaceholder('请输入用户名或手机号').fill('SYNTHETIC');await page.getByPlaceholder('请输入密码',{exact:true}).fill('test-only');await page.getByPlaceholder('请输入验证码').fill('1234');await page.locator('.login-scene').getByRole('button',{name:'立即登录',exact:true}).click()
  await expect(page).toHaveURL(/board-assistant$/);await expect(page.getByText('已恢复选择并按当前目录重算。请重新检查并确认，尚未保存。')).toBeVisible();expect(observed.save).toHaveLength(0);expect(observed.evaluate[0]).not.toHaveProperty('expectedCalculationVersion')
  await page.getByRole('button',{name:'重新计算并准备保存',exact:true}).click();await page.getByRole('button',{name:'确认保存当前结果',exact:true}).click();await expect(page.getByRole('alert')).toContainText('价格已变化');await expect(page.getByRole('button',{name:'确认保存当前结果',exact:true})).toHaveCount(0)
  await shot(page,'login-price-conflict')
  await page.getByRole('button',{name:'重新计算并准备保存',exact:true}).click();await page.getByRole('button',{name:'确认保存当前结果',exact:true}).click();await expect(page.getByText('已保存方案，可在我的方案查看。')).toBeVisible();expect(observed.save).toHaveLength(2);expect(observed.save[1]).not.toHaveProperty('totalCents')
})

test('SYNTHETIC category gaps, budget/truncation and maximum three cross-category comparisons',async({page})=>{
  const observed=calls();const response=recommend(), additional=clone(response.assembly.plans[0]);additional.selection.items[0].variantKey='SYN-ALTERNATIVE';additional.calculationVersion='SYN-ALTERNATIVE';response.assembly.plans.push(additional);response.searchTruncated=true
  await mockApi(page,observed,({p})=>p.endsWith('/recommend') ? response : undefined)
  await findPlans(page);await expect(page.getByText('搜索已截断。结果与最低价仅代表本次搜索范围，不能视为全库最低。')).toBeVisible();await page.getByRole('button',{name:'加入对比',exact:true}).first().click();await page.getByRole('tab',{name:/配件组装/}).click();await page.getByRole('button',{name:'加入对比',exact:true}).first().click();await page.getByRole('button',{name:'加入对比',exact:true}).first().click();await page.getByRole('button',{name:'加入对比',exact:true}).click();await expect(page.getByRole('alert')).toContainText('最多比较三套');await page.getByRole('button',{name:'重新计算并对比'}).click();await expect(page.getByRole('table')).toBeVisible();expect(observed.compare[0].plans).toHaveLength(3);expect(new Set(observed.compare[0].plans.map(p=>p.selection.kind)).size).toBe(2)
  await shot(page,'comparison')
})

test('SYNTHETIC linked suggestions require confirmation; newest replacement wins; late return ignored',async({page})=>{
  const observed=calls();let replaceCount=0, lateResolve;const late=new Promise(resolve=>{lateResolve=resolve})
  await mockApi(page,observed,async({p,body})=>{
    if(p.endsWith('/replacements')) {observed.replace.push(body);replaceCount++;if(replaceCount === 1) {await late;return {total:0,list:[],excludedCounts:{OLD_RESPONSE:1}}}return clone(apiExamples.responses.linkedReplacement)}
  })
  await findPlans(page);await assembly(page);await page.getByRole('button',{name:'替换轮子',exact:true}).click();await expect.poll(()=>replaceCount).toBe(1);await page.getByRole('button',{name:'替换轮子',exact:true}).click();await page.getByRole('button',{name:'检查替换 1',exact:true}).click();expect(observed.evaluate).toHaveLength(0);await expect(page.getByText('需要连带调整，当前方案尚未修改；请选择有完整选择的建议，确认后才重算。')).toBeVisible();await shot(page,'linked-confirmation');lateResolve();await expect(page.getByText('OLD_RESPONSE')).toHaveCount(0)
  await page.getByRole('button',{name:'确认建议 1 并重算',exact:true}).click();await expect.poll(()=>observed.evaluate.length).toBe(1);expect(observed.evaluate[0].selection.items.some(i=>i.category==='RISER')).toBe(true)
  let release;const waiting=new Promise(resolve=>{release=resolve});await page.route('**/api/skate/board-assistant/replacements',async route=>{await waiting;await route.fulfill({json:envelope(clone(apiExamples.responses.linkedReplacement))})});await page.getByRole('button',{name:'替换轮子',exact:true}).click();await page.getByRole('button',{name:'返回推荐结果',exact:true}).click();release();await expect(page.getByTestId('selected-plan')).toHaveCount(0)
})

test('SYNTHETIC snapshot/latest, rename, historical copy and confirmed deletion',async({page})=>{
  const observed=calls();await signIn(page);await mockApi(page,observed);await page.goto('/profile/board-plans');await page.getByRole('button',{name:/查看合成测试方案/}).click();await expect(page.getByTestId('saved-snapshot')).toContainText('¥17.47');await expect(page.getByTestId('latest-evaluation')).toContainText('¥90.00');await shot(page,'snapshot-latest');await page.getByLabel('方案名称',{exact:true}).fill('新名称');await page.getByRole('button',{name:'保存名称与备注',exact:true}).click();await expect(page.getByText('名称与备注已更新')).toBeVisible();await page.getByRole('button',{name:'复制历史方案',exact:true}).click();await expect(page.getByTestId('saved-snapshot')).toContainText('历史价格复制自');await page.getByRole('button',{name:'删除方案',exact:true}).click();await page.getByRole('button',{name:'确认删除',exact:true}).click();await expect(page.getByText('方案已删除',{exact:true})).toBeVisible()
})

test('SYNTHETIC admin structured fields, five resources, version conflict, activation gates and trial',async({page})=>{
  const observed=calls();await signIn(page,'ADMIN')
  const row={key:'SYN-UI-P',version:1,active:false,category:'DECK',sourceRecordId:'SYN-UI-P',brand:'TEST',model:'合成目录草稿',description:'synthetic only',sources:[{url:'https://example.invalid',title:'合成来源',collectedAt:null,verificationStatus:'RECORDED',evidenceRef:'SYNTHETIC-only',note:'not real'}],suitabilityTags:[],levelTags:[],legacyEquipmentId:null,imageSource:null,imageLicenseStatus:'UNKNOWN'}
  await mockApi(page,observed,({p,body,req})=>{
    if(p.startsWith('/api/admin/board-assistant/')) {observed.admin.push({p,body,method:req.method()});if(p.endsWith('/activation')) return {errorCode:'SELECTION_INVALID',errors:[{path:'sources',detail:'来源尚未核实，不能启用'}]};if(req.method()==='PATCH') return {errorCode:'VERSION_CONFLICT',currentVersion:2,current:row};if(p.endsWith('/trial')) return {complete:bucket('DATA_INSUFFICIENT'),assembly:bucket('DATA_INSUFFICIENT'),searchTruncated:true};return {total:11,list:[row]}}
  })
  await page.goto('/admin');await page.getByRole('button',{name:/选板数据维护/}).click();await expect(page.getByRole('heading',{name:'选板数据维护',exact:true,level:2})).toBeVisible();await page.getByRole('button',{name:'启用SYN-UI-P',exact:true}).click();await page.getByRole('button',{name:'确认启停',exact:true}).click();await expect(page.getByRole('alert')).toContainText('来源尚未核实');await shot(page,'admin-activation-gate')
  await page.getByRole('button',{name:'取消启停',exact:true}).click();await page.getByRole('button',{name:'编辑SYN-UI-P',exact:true}).click();await page.getByRole('button',{name:'保存编辑',exact:true}).click();await expect(page.getByRole('alert')).toContainText('记录已被更新');expect(observed.admin.find(c=>c.method==='PATCH').body.expectedVersion).toBe(1)
  await page.getByRole('button',{name:'返回目录（放弃未保存修改）',exact:true}).click();await page.getByRole('button',{name:'下一页资源',exact:true}).click();await expect.poll(()=>observed.admin.filter(c=>c.method==='GET').length).toBeGreaterThan(1)
  for(const title of ['具体规格','报价','搭配规则','组装模板']) {await page.getByRole('tab',{name:title,exact:true}).click();await expect(page.getByRole('heading',{name:`${title}目录`,exact:true})).toBeVisible()}
  await page.getByRole('tab',{name:'搭配规则',exact:true}).click();await page.getByRole('button',{name:'新增搭配规则草稿',exact:true}).click();await page.getByLabel('规则类型',{exact:true}).selectOption('WHEEL_TRUCK_RISER');await expect(page.getByLabel('轮径下限（mm）',{exact:true})).toBeVisible();await expect(page.getByLabel('轮径下限（mm）',{exact:true})).toHaveValue('');await shot(page,'admin-rule-fields');await page.getByRole('button',{name:'返回目录（放弃未保存修改）',exact:true}).click();await page.getByLabel('人民币预算（元）').fill('1000');await page.getByRole('button',{name:'运行试算',exact:true}).click();await expect(page.getByText('缺口：SYNTHETIC_ONLY: missing verified source')).toHaveCount(2)
})

test('SYNTHETIC mobile keyboard, independent empty state and original navigation retained',async({page})=>{
  const observed=calls();await page.setViewportSize({width:390,height:844});await mockApi(page,observed,({p})=>p.endsWith('/recommend') ? {complete:bucket('DATA_INSUFFICIENT'),assembly:bucket('NO_CANDIDATE'),searchTruncated:false} : undefined);await page.goto('/board-assistant')
  const budget=page.getByLabel('人民币预算（元）');await budget.focus();await budget.press('1');await budget.press('0');await budget.press('0');await budget.press('0');await budget.press('Tab');await expect(page.getByLabel('主要用途')).toBeFocused();await page.getByRole('button',{name:'查找方案',exact:true}).focus();await page.keyboard.press('Enter');await expect(page.getByRole('heading',{name:'推荐结果',exact:true})).toBeVisible();await expect(page.getByText('资料不足不能视为预算不足',{exact:false})).toBeVisible();await page.getByRole('tab',{name:/配件组装/}).focus();await page.keyboard.press('Enter');await expect(page.getByText('暂无候选',{exact:true})).toBeVisible();await expect(page.getByRole('navigation',{name:'移动端主导航'}).getByRole('link',{name:'AI',exact:true})).toBeVisible();await expect(page.getByRole('navigation',{name:'移动端主导航'}).getByRole('link',{name:'选板助手'})).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);await shot(page,'mobile-keyboard-empty')
})

test('SYNTHETIC all five resource draft forms submit structured data; mobile admin keyboard',async({page})=>{
  const observed=calls(), created=[];await signIn(page,'ADMIN')
  await mockApi(page,observed,({p,body,req})=>{
    if(p.startsWith('/api/admin/board-assistant/')) {
      if(req.method()==='POST') {created.push({p,body});return {...body,version:1,active:false}}
      return {list:[],total:0}
    }
  })
  await page.goto('/admin');await page.getByRole('button',{name:/选板数据维护/}).click()
  for(const [resource,title] of Object.entries({products:'产品',variants:'具体规格',quotes:'报价',rules:'搭配规则',templates:'组装模板'})) {
    await page.getByRole('tab',{name:title,exact:true}).click();await page.getByRole('button',{name:`新增${title}草稿`,exact:true}).click();await page.getByLabel('稳定编号',{exact:true}).fill(`SYN-UI-${resource}`)
    if(resource==='products') {await page.getByLabel('类别',{exact:true}).selectOption('DECK');await page.getByLabel('原始记录编号',{exact:true}).fill('SYN-P');await page.getByLabel('品牌',{exact:true}).fill('合成品牌');await page.getByLabel('型号',{exact:true}).fill('合成型号')}
    if(resource==='variants') {await page.getByLabel('规格类别（按所属产品核对）',{exact:true}).selectOption('DECK');await page.getByLabel('所属产品编号',{exact:true}).fill('SYN-P');await page.getByLabel('具体规格名称',{exact:true}).fill('合成规格')}
    if(resource==='quotes') {await page.getByLabel('来源产品编号',{exact:true}).fill('SYN-P');await page.getByLabel('币种',{exact:true}).selectOption('CNY')}
    if(resource==='rules') {await page.getByLabel('规则类型',{exact:true}).selectOption('DECK_TRUCK_FIT');await page.getByLabel('规则级别',{exact:true}).selectOption('HARD')}
    if(resource==='templates') await page.getByLabel('模板用途',{exact:true}).selectOption('ACTION_BEGINNER')
    await page.getByRole('button',{name:'添加来源',exact:true}).click();await page.getByLabel('来源链接',{exact:true}).fill('https://example.invalid/SYNTHETIC');await page.getByLabel('来源标题',{exact:true}).fill('SYNTHETIC TEST ONLY');await page.getByLabel('可复核证据位置',{exact:true}).fill('SYNTHETIC_TEST_ONLY')
    await page.getByRole('button',{name:'保存草稿',exact:true}).click();await expect(page.getByRole('button',{name:'保存编辑',exact:true})).toBeVisible();await page.getByRole('button',{name:'返回目录（放弃未保存修改）',exact:true}).click()
  }
  expect(created).toHaveLength(5);expect(created[1].body.specs.widthInch).toBeNull();expect(created[2].body.amountCents).toBeNull();for(const {body} of created){expect(body).not.toHaveProperty('active');expect(body).not.toHaveProperty('version');expect(body.sources[0].verificationStatus).toBe('RECORDED')}
  await page.setViewportSize({width:390,height:844});await page.getByRole('tab',{name:'报价',exact:true}).click();await page.getByRole('button',{name:'新增报价草稿',exact:true}).click();const key=page.getByLabel('稳定编号',{exact:true});await key.focus();await page.keyboard.press('Tab');await expect(page.getByLabel('来源产品编号',{exact:true})).toBeFocused();expect(await page.evaluate(()=>document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);await shot(page,'admin-mobile-keyboard')
})

test('SYNTHETIC failed evaluation and save never show success; admin cannot save personal plans',async({page})=>{
  const observed=calls();await signIn(page);await mockApi(page,observed,async({p,route})=>{
    if(p.endsWith('/evaluate')) {await route.fulfill({status:500,json:{code:500,message:'合成失败响应'}});return true}
  });await findPlans(page);await assembly(page);await page.getByRole('button',{name:'重新计算并准备保存',exact:true}).click();await expect(page.getByRole('alert')).toContainText('合成失败响应');await expect(page.getByRole('button',{name:'确认保存当前结果',exact:true})).toHaveCount(0);expect(observed.save).toHaveLength(0)
  await page.evaluate(()=>localStorage.setItem('skate_role','ADMIN'));await page.getByRole('button',{name:'重新计算并准备保存',exact:true}).click();await expect(page.getByRole('alert')).toContainText('仅限用户身份');expect(observed.save).toHaveLength(0)
})

test('SYNTHETIC FIX R1 failed recompare clears old table; removal discards in-flight collection',async({page})=>{
  const observed=calls();let attempts=0,finish;const delayed=new Promise(resolve=>{finish=resolve})
  await mockApi(page,observed,async({p,body,route})=>{
    if(p.endsWith('/compare')) {attempts++;if(attempts===2){await route.fulfill({status:500,json:{code:500,message:'合成重新比较失败'}});return true}if(attempts===3)await delayed;return {evaluations:body.plans.map(()=>evaluation())}}
  })
  await findPlans(page);await page.getByRole('button',{name:'加入对比',exact:true}).first().click();await page.getByRole('tab',{name:/配件组装/}).click();await page.getByRole('button',{name:'加入对比',exact:true}).first().click();await page.getByRole('button',{name:'重新计算并对比'}).click();await expect(page.getByRole('table')).toBeVisible();await page.getByRole('button',{name:'重新计算并对比'}).click();await expect(page.getByRole('alert')).toContainText('合成重新比较失败');await expect(page.getByRole('table')).toHaveCount(0)
  await page.getByRole('button',{name:'重新计算并对比'}).click();await expect.poll(()=>attempts).toBe(3);await page.getByRole('button',{name:'移除 1',exact:true}).click();finish();await expect(page.getByRole('heading',{name:'跨类别对比（1/3）'})).toBeVisible();await expect(page.getByRole('table')).toHaveCount(0);await page.screenshot({path:path.join(evidence,'F01-FIX-01-comparison.png'),fullPage:true})
})

test('SYNTHETIC FIX R2 delayed save locks return/navigation and duplicate click; feedback retained',async({page})=>{
  const observed=calls();let writes=0,finish;const delayed=new Promise(resolve=>{finish=resolve})
  await signIn(page);await mockApi(page,observed,async({p,req})=>{if(p==='/api/user/board-plans'&&req.method()==='POST'){writes++;await delayed;return {...clone(snapshotExamples.saveResponse),key:'SYN-FIX-SAVED'}}})
  await findPlans(page);await assembly(page);await page.getByRole('button',{name:'重新计算并准备保存',exact:true}).click();const save=page.getByRole('button',{name:'确认保存当前结果',exact:true});await save.dblclick();await expect.poll(()=>writes).toBe(1);await expect(page.getByRole('button',{name:'返回推荐结果'})).toBeDisabled();await page.getByRole('navigation').first().getByRole('link',{name:'社区帖子'}).click();await expect(page).toHaveURL(/board-assistant$/);await expect(page.getByTestId('selected-plan')).toBeVisible();await expect(page.getByRole('button',{name:'替换轮子'})).toBeDisabled();await page.screenshot({path:path.join(evidence,'F01-FIX-01-saving-lock.png'),fullPage:true});finish();await expect(page.getByText('已保存方案，可在我的方案查看。')).toBeVisible();expect(writes).toBe(1);await page.getByRole('button',{name:'返回推荐结果'}).click();await expect(page.getByTestId('selected-plan')).toHaveCount(0)
})

test('SYNTHETIC FIX R2 unknown network outcome pauses saves and survives return',async({page})=>{
  const observed=calls();let writes=0;await signIn(page);await mockApi(page,observed,async({p,req,route})=>{if(p==='/api/user/board-plans'&&req.method()==='POST'){writes++;await route.abort('failed');return true}})
  await findPlans(page);await assembly(page);await page.getByRole('button',{name:'重新计算并准备保存'}).click();await page.getByRole('button',{name:'确认保存当前结果'}).click();await expect(page.getByRole('alert')).toContainText('本次保存结果未知');await expect(page.getByRole('button',{name:'重新计算并准备保存'})).toBeDisabled();await page.reload();await expect(page.getByRole('alert')).toContainText('本次保存结果未知');expect(writes).toBe(1);await page.getByRole('link',{name:'我的方案',exact:true}).click();await expect(page).toHaveURL(/profile\/board-plans$/)
})

// REFERENCE-01: recorded H2 shapes replayed over mocked browser routes, not live acceptance.
async function findReferences(page,budget='600') {
  await page.goto('/board-assistant');await page.getByRole('combobox',{name:'偏好',exact:true}).selectOption('EASY_START');await page.getByLabel('人民币预算（元）').fill(budget);await page.getByRole('button',{name:'查找方案',exact:true}).click();await expect(page.getByRole('heading',{name:'推荐结果',exact:true})).toBeVisible()
}
test('REFERENCE-01 mock replay: 600 references, dates/fees, compare, explicit save and immutable snapshot',async({page})=>{
  const observed=calls();await signIn(page);const complete=referenceResponse().complete.plans[0]
  await mockApi(page,observed,({p,body,req})=>{
    if(p.endsWith('/recommend'))return referenceResponse()
    if(p.endsWith('/compare'))return {evaluations:[complete,referenceResponse().assembly.plans[0]]}
    if(p.endsWith('/evaluate'))return complete
    if(p==='/api/user/board-plans'&&req.method()==='POST'){observed.save.push(body);return clone(referenceSnapshots.saveResponse)}
    if(p==='/api/user/board-plans'&&req.method()==='GET')return {total:1,list:[clone(referenceSnapshots.saveResponse)]}
    if(p.startsWith('/api/user/board-plans/')&&req.method()==='GET')return clone(referenceSnapshots.detailAfterSyntheticRevision)
  })
  await findReferences(page)
  for(const amount of ['580.00','323.00','446.00'])await expect(page.getByText(`参考估价 ¥${amount}`,{exact:true})).toBeVisible()
  await expect(page.getByText(referenceNotice,{exact:true})).toHaveCount(3);await expect(page.getByText(/采集 2026-10-09/).first()).toBeVisible();await expect(page.locator('a[href="https://www.dobyheart.com/"]').first()).toBeVisible()
  await page.screenshot({path:path.join(evidence,'REFERENCE-01-frontend-600-desktop.png'),fullPage:true})
  await page.getByRole('button',{name:'加入对比',exact:true}).first().click();await page.getByRole('tab',{name:/配件组装/}).click();await page.getByRole('button',{name:'加入对比',exact:true}).first().click();await page.getByRole('button',{name:'重新计算并对比',exact:true}).click()
  await expect(page.getByRole('table')).toContainText('参考估价 ¥580.00');await expect(page.getByRole('table')).toContainText('已知参考小计 ¥786.20');await page.getByRole('table').screenshot({path:path.join(evidence,'REFERENCE-01-frontend-comparison.png')})
  await page.getByRole('tab',{name:/成品整板/}).click();await page.getByRole('button',{name:'查看方案 1',exact:true}).click();await expect(page.getByTestId('selected-plan')).toContainText('运费 未知，未计入');await expect(page.getByTestId('selected-plan')).toContainText('税费 未知，未计入')
  await page.getByRole('button',{name:'重新计算并准备保存',exact:true}).click();await expect(page.getByRole('button',{name:'确认保存当前结果',exact:true})).toBeVisible();expect(observed.save).toHaveLength(0);await page.getByRole('button',{name:'确认保存当前结果',exact:true}).click();await expect(page.getByText('已保存参考估价方案，可在我的方案查看。')).toBeVisible();expect(observed.save[0].expectedCalculationVersion).toBe(complete.calculationVersion);expect(observed.save[0]).not.toHaveProperty('pricingMode')
  await page.goto('/profile/board-plans');await page.getByRole('button',{name:`查看${referenceSnapshots.saveResponse.name}`,exact:true}).click();await expect(page.getByTestId('saved-snapshot')).toContainText('参考估价 ¥580.00');await expect(page.getByTestId('latest-evaluation')).toContainText('参考估价 ¥580.01');await page.screenshot({path:path.join(evidence,'REFERENCE-01-frontend-snapshot-synthetic-cent.png'),fullPage:true})
})
test('REFERENCE-01 mock replay: synthetic 100 budget control and incomplete mobile replacement/draft',async({page})=>{
  const observed=calls();await signIn(page);await page.setViewportSize({width:390,height:844})
  await mockApi(page,observed,({p,body,req})=>{
    if(p.endsWith('/recommend')){const data=referenceResponse();if(body.budgetCents===10000){const minimum=data.complete.plans.find(x=>x.totalCents===32300);minimum.demand.budgetCents=10000;minimum.budgetDeltaCents=-22300;data.complete={state:'BUDGET_INSUFFICIENT',plans:[],minimumFeasiblePlan:minimum,shortfallCents:22300}}return data}
    if(p.endsWith('/replacements'))return {total:1,list:[referenceResponse().assembly.plans[1]]}
    if(p.endsWith('/evaluate')){observed.evaluate.push(body);return referenceResponse().assembly.plans[1]}
    if(p==='/api/user/board-plans'&&req.method()==='POST')return {...referenceResponse().assembly.plans[1],key:'SYNTHETIC-REFERENCE-DRAFT',snapshotState:'DRAFT'}
  })
  await findReferences(page,'100');await expect(page.getByText('参考估价 ¥323.00',{exact:true})).toBeVisible();await expect(page.getByText('参考预算差额 ¥223.00',{exact:true})).toBeVisible();await page.screenshot({path:path.join(evidence,'REFERENCE-01-frontend-100-synthetic-mobile.png'),fullPage:true})
  await page.getByLabel('人民币预算（元）').fill('600');await page.getByRole('button',{name:'查找方案',exact:true}).click();await expect(page.getByRole('tab',{name:/成品整板（3）/})).toBeVisible();await page.getByRole('tab',{name:/配件组装/}).click();await expect(page.getByText('已知参考小计 ¥786.20',{exact:true})).toHaveCount(3);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);await page.screenshot({path:path.join(evidence,'REFERENCE-01-frontend-partial-mobile.png'),fullPage:true})
  await page.getByRole('button',{name:'查看方案 1',exact:true}).click();await expect(page.getByTestId('selected-plan')).toContainText('仅保存为草稿');await expect(page.getByTestId('selected-plan')).not.toContainText('¥0.00');await page.getByRole('button',{name:'替换轮子',exact:true}).click();await page.getByRole('button',{name:'检查替换 1',exact:true}).click();expect(observed.evaluate).toHaveLength(0)
  await page.getByRole('button',{name:'确认单项替换并重算',exact:true}).click();await expect(page.getByText('已重新计算整套方案，请检查结果。')).toBeVisible();expect(observed.evaluate[0].expectedCalculationVersion).toBe(referenceResponse().assembly.plans[1].calculationVersion);await page.getByRole('button',{name:'重新计算并准备保存',exact:true}).click();await page.getByRole('button',{name:'确认保存当前结果',exact:true}).click();await expect(page.getByText('已保存资料不足草稿，可在我的方案查看。')).toBeVisible()
})
test('REFERENCE-01 mock admin: five reference fields and versioned RECORDED activation',async({page})=>{
  const observed=calls(),created=[];await signIn(page,'ADMIN')
  const row={key:'SYNTHETIC-REF',version:2,active:false,category:'DECK',pricingMode:'REFERENCE',verificationStatus:'RECORDED',priceAssumptions:['SYNTHETIC reference assumption'],sources:[{url:'https://example.invalid',collectedDate:'2026-10-09',verificationStatus:'RECORDED'}],eligibility:{referencePriceEligible:true,exactPriceEligible:false}}
  await mockApi(page,observed,({p,body,req})=>{if(p.startsWith('/api/admin/board-assistant/')){observed.admin.push({p,body,method:req.method()});if(p.endsWith('/activation'))return {...row,active:true};if(req.method()==='POST'){created.push({p,body});return {...body,version:1,active:false}}return {total:1,list:[row]}}})
  await page.goto('/admin');await page.getByRole('button',{name:/选板数据维护/}).click();await page.getByRole('button',{name:'启用SYNTHETIC-REF',exact:true}).click();await page.getByRole('button',{name:'确认启停',exact:true}).click();await expect(page.getByText('记录已启用',{exact:true})).toBeVisible();expect(observed.admin.find(x=>x.p.endsWith('/activation')).body).toEqual({active:true,expectedVersion:2})
  for(const [resource,title] of Object.entries({products:'产品',variants:'具体规格',quotes:'报价',rules:'搭配规则',templates:'组装模板'})){
    await page.getByRole('tab',{name:title,exact:true}).click();await page.getByRole('button',{name:`新增${title}草稿`,exact:true}).click();await page.getByLabel('稳定编号',{exact:true}).fill(`SYNTHETIC-REF-${resource}`);if(resource==='variants')await page.getByLabel('规格类别（按所属产品核对）',{exact:true}).selectOption('DECK')
    await page.getByLabel('计价模式（未填写沿用精确规则）',{exact:true}).selectOption('REFERENCE');await page.getByLabel('估算假设（每行一项）',{exact:true}).fill('SYNTHETIC reference assumption\n包装待确认');await page.getByRole('button',{name:'保存草稿',exact:true}).click();await expect(page.getByRole('button',{name:'保存编辑',exact:true})).toBeVisible();if(resource==='quotes')await page.screenshot({path:path.join(evidence,'REFERENCE-01-frontend-admin-reference.png'),fullPage:true});await page.getByRole('button',{name:'返回目录（放弃未保存修改）',exact:true}).click()
  }
  expect(created).toHaveLength(5);for(const {body} of created){expect(body.pricingMode).toBe('REFERENCE');expect(body.priceAssumptions).toEqual(['SYNTHETIC reference assumption','包装待确认']);expect(body).not.toHaveProperty('active')}
})
