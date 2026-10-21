const {test,expect}=require('@playwright/test')
const fs=require('node:fs'),path=require('node:path')
const USER='/api/user/training-guidance',ADMIN='/api/admin/training-guidance'
const identities=JSON.parse(fs.readFileSync(process.env.REVIEW_IDENTITIES,'utf8'))
async function setup(page,role='USER',identity=null,validationPublication=false,injectedRequests=new Set()){
 const errors=[],events=[],pending=new Set(),responses=[]
 await page.addInitScript(({token,role})=>{localStorage.setItem('skate_token',token);localStorage.setItem('skate_role',role);localStorage.setItem('skate_name','isolated review')},{token:identities[identity||(role==='ADMIN'?'admin':'user')],role})
 page.on('pageerror',e=>errors.push('pageerror '+e.message))
 page.on('console',m=>{if(m.type()==='error' && !(m.text().includes('503')&&m.location().url.endsWith('/ai-explain')) && !(validationPublication&&m.text().includes('400')&&m.location().url.endsWith('/publication')) && !([...injectedRequests].some(r=>r.url()===m.location().url)&&m.text().includes('ERR_FAILED')))errors.push('console '+m.text())})
 page.on('request',r=>{if(new URL(r.url()).pathname.startsWith('/api/'))pending.add(r)})
 page.on('requestfinished',r=>pending.delete(r));page.on('requestfailed',r=>{pending.delete(r);if(new URL(r.url()).pathname.startsWith('/api/')&&!injectedRequests.has(r))errors.push('transport '+new URL(r.url()).pathname+' '+r.failure()?.errorText)})
 page.on('response',r=>{const p=new URL(r.url()).pathname;if(p.startsWith(USER)||p.startsWith(ADMIN))responses.push((async()=>{const body=await r.json();events.push({method:r.request().method(),path:p,status:r.status(),body});if(r.status()!==200 && !(p.endsWith('/ai-explain')&&r.status()===503) && !(validationPublication&&p.endsWith('/publication')&&r.status()===400&&body.data?.errorCode==='VALIDATION_ERROR'))errors.push('unexpected API '+p+' '+r.status());await r.finished()})())})
 await page.route('**/*',route=>['127.0.0.1','localhost'].includes(new URL(route.request().url()).hostname)?route.continue():route.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"/>'}))
 const drain=async()=>{await expect.poll(()=>pending.size).toBe(0);await Promise.all(responses)}
 return {events,errors,drain,save:async name=>{await Promise.allSettled(responses);fs.writeFileSync(path.join(process.env.REVIEW_EVIDENCE,name+'.json'),JSON.stringify({layer:'REAL_MYSQL_LISTENING_HTTP_JWT_BROWSER_LOCAL_PROVIDER_DOUBLE',apiBodiesMocked:false,events,errors},null,2))}}
}
test('real guided workflow, source, partial, split acceptance, failed AI, explicit resume and history',async({page})=>{
 const o=await setup(page)
 try{
  await page.setViewportSize({width:390,height:844});await page.goto('/training-guidance')
  await page.getByLabel('已维护模板').selectOption('basic-foundations@1');await page.getByLabel('我的训练目标').fill('本地独立验收基础训练');await page.getByLabel('当前水平（用户自评）').selectOption('BEGINNER')
  for(const d of ['周一','周二','周三','周四','周五','周六','周日'])await page.getByLabel(d,{exact:true}).check()
  await page.getByLabel('计划开始日期（中国日期）').fill('2026-10-22')
  await page.getByRole('button',{name:'预览训练安排',exact:true}).click();await expect(page.getByLabel('排期预览')).toBeVisible()
  expect(o.events.filter(e=>e.path===USER+'/plans'&&e.method==='POST')).toHaveLength(0)
  await page.getByRole('button',{name:'确认建立计划',exact:true}).click();await expect(page.getByText(/计划版本 0/)).toBeVisible()
  await page.getByRole('button',{name:/^查看任务：/}).first().click();await expect(page.getByLabel('任务教学与反馈')).toBeVisible()
  await page.getByLabel('任务教学与反馈').getByText(/查看来源：/).first().click();await expect(page.getByText(/database\/seed-ai-coach/).first()).toBeVisible();expect(await page.locator('a[href*="database/"]').count()).toBe(0)
  await page.getByLabel('完成情况').selectOption('PARTIAL');await page.getByLabel('实际练习分钟').fill('4');await page.getByLabel('主观难度').selectOption('HARD')
  const task=o.events.findLast(e=>/\/tasks\//.test(e.path)).body.data
  for(const q of task.selfChecks)await page.getByLabel(q.question).selectOption('UNSURE')
  await page.getByLabel('时间不足',{exact:true}).check();await page.getByLabel('动作困难',{exact:true}).check();await page.getByLabel('补充记录').fill('时间不足，转向重心困难；仅本地合成反馈')
  await page.getByRole('button',{name:'提交本次练习',exact:true}).click();await expect(page.getByLabel('本次训练回顾')).toBeVisible()
  await o.drain();let feedback=o.events.filter(e=>e.path.endsWith('/feedback'));expect(feedback).toHaveLength(1);expect(feedback[0].body.data.record.expAwarded).toBe(0);expect(feedback[0].body.data.plan.stats.completedTasks).toBe(0)
  await page.getByRole('button',{name:'待确认建议',exact:true}).click();await page.getByRole('button',{name:'查看建议前后差异'}).first().click();await expect(page.getByLabel('安排前后差异')).toContainText('调整后')
  await page.getByRole('button',{name:'接受此建议'}).click();await expect(page.getByText(/计划版本 2/)).toBeVisible()
  await page.getByLabel('这个动作遇到困难：想问的问题').fill('LOCAL_PROVIDER_FAIL：按本站资料如何自查？');await page.getByRole('button',{name:'预览带入的问题与资料'}).click();await expect(page.getByLabel('AI上下文预览')).toBeVisible()
  expect(o.events.filter(e=>e.path.endsWith('/ai-explain'))).toHaveLength(0)
  await page.getByRole('button',{name:'确认请求AI解释'}).click();await expect(page.getByRole('alert').filter({hasText:'AI解释暂不可用'})).toBeVisible();expect(o.events.filter(e=>e.path.endsWith('/feedback'))).toHaveLength(1)
  await page.getByRole('button',{name:'暂停计划',exact:true}).click();await page.getByRole('button',{name:'确认暂停',exact:true}).click();await page.getByRole('button',{name:'恢复计划',exact:true}).click();await page.getByLabel('恢复前重新自评').selectOption('BEGINNER');await page.getByLabel(/我已重新自评并了解安全提醒/).check();await page.getByRole('button',{name:'确认恢复',exact:true}).click();await expect(page.getByText(/计划版本 4/)).toBeVisible()
  await page.getByRole('button',{name:'练习历史',exact:true}).click();await expect(page.getByRole('button',{name:'查看练习回顾'})).toBeVisible();await page.getByRole('button',{name:'调整历史',exact:true}).click();await expect(page.getByRole('table',{name:'调整前后安排（只读预览）'})).toHaveCount(3);await expect(page.getByRole('table',{name:'调整前后安排（只读预览）'}).first()).toBeVisible()
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await o.drain();expect(o.errors).toEqual([])
 }finally{await o.save('browser-user')}
})

test('unknown real committed feedback response recovers the identical request without duplicate practice',async({page,request})=>{
 const injected=new Set(),o=await setup(page,'USER','unknown',false,injected),headers={Authorization:'Bearer '+identities.unknown},sent=[];let firstResult
 try{
  const response=await request.post(new URL(USER+'/plans',process.env.REF_BACKEND).href,{headers,data:{requestKey:crypto.randomUUID(),templateId:'basic-foundations',templateVersion:1,goal:'隔离未知请求恢复',selfLevel:'BEGINNER',trainingDays:[1,2,3,4,5,6,7],sessionMinutes:20,startDate:'2026-10-22'}});expect(response.status()).toBe(200)
  await page.goto('/training-guidance');await page.getByRole('button',{name:/^查看任务：/}).first().click();await expect(page.getByLabel('任务教学与反馈')).toBeVisible();await o.drain()
  const task=o.events.findLast(e=>/\/tasks\//.test(e.path)).body.data
  await page.getByLabel('完成情况').selectOption('PARTIAL');await page.getByLabel('实际练习分钟').fill('2');await page.getByLabel('主观难度').selectOption('MODERATE');for(const q of task.selfChecks)await page.getByLabel(q.question).selectOption('UNSURE')
  await page.route('**/api/user/training-guidance/plans/*/feedback',async route=>{sent.push(route.request().postDataJSON());if(sent.length===1){const upstream=await route.fetch();expect(upstream.status()).toBe(200);firstResult=(await upstream.json()).data;injected.add(route.request());return route.abort('failed')}return route.continue()})
  await page.getByRole('button',{name:'提交本次练习',exact:true}).dblclick();await expect(page.getByText(/本次结果未知/)).toBeVisible();expect(sent).toHaveLength(1);await expect(page.getByLabel('本次训练回顾')).toHaveCount(0)
  await page.getByRole('button',{name:'重试原操作'}).click();await expect(page.getByLabel('本次训练回顾')).toBeVisible();await o.drain();expect(sent).toHaveLength(2);expect(sent[1]).toEqual(sent[0]);expect(injected.size).toBe(1)
  const replay=o.events.findLast(e=>e.path.endsWith('/feedback')).body.data;expect(replay).toEqual(firstResult);expect(replay.plan.stats.practiceCount).toBe(1);expect(replay.record.expAwarded).toBe(0);expect(o.errors).toEqual([])
  fs.writeFileSync(path.join(process.env.REVIEW_EVIDENCE,'browser-unknown-fault.json'),JSON.stringify({fault:'one intentionally dropped already committed real feedback response',sameOriginalBodyAndKey:JSON.stringify(sent[0])===JSON.stringify(sent[1]),practiceCount:replay.plan.stats.practiceCount,recordId:replay.record.id,injectedFailureCount:injected.size},null,2))
 }finally{await o.save('browser-unknown-recovery')}
})

test('publication validation from real API stays a visible error, plus keyboard form navigation',async({page})=>{
 const o=await setup(page,'ADMIN',null,true)
 try{
  await page.goto('/admin/training-guidance');await page.getByRole('button',{name:'查看并编辑此版本'}).filter({hasText:'查看'}).first().click()
  const editor=page.getByLabel('完整教学文档 JSON');await expect(editor).not.toHaveValue('');const doc=JSON.parse(await editor.inputValue());doc.tasks[0].sources[0].locator='docs/not-a-registered-source.md:1-2'
  await page.getByRole('button',{name:'以当前文档另建模板'}).click();await editor.fill(JSON.stringify(doc));await page.getByRole('button',{name:'保存新草稿',exact:true}).click();await expect(page.getByRole('heading',{name:/所读版本 1 · 草稿/})).toBeVisible()
  await page.getByLabel('操作理由').fill('隔离验证：此来源应拒绝');await page.getByLabel('操作理由').focus();await page.keyboard.press('Tab');await expect(page.getByLabel(/我已核对已保存版本/)).toBeFocused();await page.keyboard.press('Space')
  await page.getByRole('button',{name:'确认发布已保存版本'}).click();await expect(page.getByRole('alert').filter({hasText:'来源不存在'})).toBeVisible();await expect(page.getByRole('heading',{name:/所读版本 1 · 草稿/})).toBeVisible();await o.drain();expect(o.events.findLast(e=>e.path.endsWith('/publication')).status).toBe(400);expect(o.errors).toEqual([])
 }finally{await o.save('browser-publication-validation')}
})

test('reject real pending suggestion keeps schedule and practice history',async({page,request})=>{
 const o=await setup(page,'USER','reject'),headers={Authorization:'Bearer '+identities.reject}
 const call=async(p,b)=>{const response=await request.post(new URL(p,process.env.REF_BACKEND).href,{headers,data:b});expect(response.status()).toBe(200);const body=await response.json();expect(body.code).toBe(200);return body.data}
 try{
  let p=await call(USER+'/plans',{requestKey:crypto.randomUUID(),templateId:'basic-foundations',templateVersion:1,goal:'仅隔离拒绝场景',selfLevel:'BEGINNER',trainingDays:[1,2,3,4,5,6,7],sessionMinutes:20,startDate:'2026-10-22'})
  const task=p.tasks[0],r=await call(USER+'/plans/'+p.id+'/feedback',{requestKey:crypto.randomUUID(),expectedVersion:p.version,instanceId:task.id,practiceDate:'2026-10-22',completion:'PARTIAL',actualMinutes:2,difficulty:'HARD',selfChecks:task.selfChecks.map(q=>({questionId:q.id,answer:'UNSURE'})),reasons:['MOVEMENT_DIFFICULTY'],note:'隔离拒绝场景'})
  const before=r.plan.tasks.map(t=>({id:t.id,date:t.date,segments:t.segments}))
  await page.goto('/training-guidance');await page.getByRole('button',{name:'待确认建议',exact:true}).click();await page.getByRole('button',{name:'查看建议前后差异'}).first().click();await page.getByRole('button',{name:'拒绝此建议'}).click();await expect(page.getByText(/计划版本 2/)).toBeVisible();await o.drain()
  const d=o.events.findLast(e=>e.path.endsWith('/decision')).body.data;expect(d.suggestion.status).toBe('REJECTED');expect(d.plan.tasks.map(t=>({id:t.id,date:t.date,segments:t.segments}))).toEqual(before);expect(d.plan.stats.practiceCount).toBe(1);expect(o.errors).toEqual([])
 }finally{await o.save('browser-reject')}
})
test('real admin distinct identity creates and publishes immutable version, rejects invalid publication and disables safely',async({page})=>{
 const o=await setup(page,'ADMIN')
 try{
  await page.goto('/admin/training-guidance');await page.locator('article').filter({has:page.getByRole('heading',{name:/版本 1 · 已发布/})}).getByRole('button',{name:'查看并编辑此版本'}).click();await page.getByRole('button',{name:'以当前文档另建模板'}).click();await page.getByRole('button',{name:'保存新草稿',exact:true}).click();await expect(page.getByRole('heading',{name:/所读版本 1/})).toBeVisible()
  await page.getByRole('button',{name:'保存为新版本草稿'}).click();await expect(page.getByRole('heading',{name:/所读版本 2/})).toBeVisible();await page.getByRole('button',{name:'检查JSON语法并预览'}).click();await expect(page.getByLabel('文档发布前预览')).toBeVisible()
  await page.getByLabel('操作理由').fill('隔离测试已核对登记来源');await page.getByLabel(/我已核对已保存版本/).check();await page.getByRole('button',{name:'确认发布已保存版本'}).click();await expect(page.getByRole('heading',{name:/所读版本 2 · 已发布/})).toBeVisible()
  await page.getByLabel('目标状态').selectOption('DISABLED');await page.getByLabel('操作理由').fill('本地隔离安全纠正');await page.getByLabel(/这是安全纠正/).check();await page.getByLabel(/我已核对已保存版本/).check();await page.getByRole('button',{name:'确认停用已保存版本'}).click();await expect(page.getByRole('heading',{name:/所读版本 2 · 已停用/})).toBeVisible();await o.drain();expect(o.errors).toEqual([])
 }finally{await o.save('browser-admin')}
})

test('late real task response cannot replace the newer task selected while the first is pending',async({page})=>{
 const o=await setup(page);let release,captured;const gate=new Promise(r=>release=r),held=new Promise(r=>captured=r);let ordinal=0;const delivery=[]
 try{
  await page.goto('/training-guidance');await expect(page.getByRole('button',{name:/^查看任务：/})).toHaveCount(4);await o.drain()
  await page.route('**/api/user/training-guidance/plans/*/tasks/*',async route=>{const n=++ordinal;const response=await route.fetch();expect(response.status()).toBe(200);if(n===1){captured();await gate}await route.fulfill({response});delivery.push(n)})
  const buttons=page.getByRole('button',{name:/^查看任务：/});await buttons.nth(0).click();await held;await buttons.nth(1).click()
  await expect(page.getByLabel('任务教学与反馈').getByRole('heading',{name:'基础低速滑行',exact:true})).toBeVisible();expect(delivery).toEqual([2]);release();await expect.poll(()=>delivery.length).toBe(2);await o.drain();await page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))
  await expect(page.getByLabel('任务教学与反馈').getByRole('heading',{name:'基础低速滑行',exact:true})).toBeVisible();expect(delivery).toEqual([2,1]);expect(o.errors).toEqual([])
 }finally{release?.();await o.save('browser-task-race')}
})
