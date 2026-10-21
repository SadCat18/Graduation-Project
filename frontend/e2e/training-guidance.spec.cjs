const {test,expect}=require('@playwright/test')
// Real browser + explicit recorded-service/synthetic API responses, never real Provider or business acceptance.
const examples=require('../tests/fixtures/training/service-examples.json').examples
const clone=v=>JSON.parse(JSON.stringify(v)),data=n=>clone(examples[n].response.data)
const USER='/api/user/training-guidance',ADMIN='/api/admin/training-guidance'
async function setup(page,{admin=false,active=false,discomfort=false,unknown=false}={}){
 const calls=[],errors=[],expectedFailures=[];let plan=discomfort?data(19).plan:active?data(8).plan:null,record=discomfort?data(19).record:active?data(8).record:null,document=null,unknownSent=false
 page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{
  if(m.type()!=='error')return
  const u=m.location().url||''
  if((u.endsWith('/ai-explain')||u.endsWith('/feedback'))&&m.text().includes('503')){expectedFailures.push(u);return}
  errors.push(m.text())
 })
 await page.addInitScript(admin=>{localStorage.setItem('skate_token','SYNTHETIC_TRAINING_ONLY');localStorage.setItem('skate_role',admin?'ADMIN':'USER');localStorage.setItem('skate_name','local fixture')},admin)
 await page.route('**/*',async route=>{
  const req=route.request(),u=new URL(req.url()),p=u.pathname
  if(!['127.0.0.1','localhost'].includes(u.hostname))return route.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"/>'})
  if(!p.startsWith('/api/'))return route.continue()
  const method=req.method(),body=method==='GET'?null:req.postDataJSON();calls.push({p,method,body,params:Object.fromEntries(u.searchParams)})
  let value=[]
  if(p===USER+'/templates')value=data(0)
  else if(p.startsWith(USER+'/templates/'))value=data(1)
  else if(p===USER+'/current')value=plan
  else if(p===USER+'/plans/preview'){expect(body).not.toHaveProperty('requestKey');expect(typeof body.templateId).toBe('string');value=data(2)}
  else if(p===USER+'/plans'&&method==='POST'){expect(body).not.toHaveProperty('instanceId');expect(body).not.toHaveProperty('expectedVersion');plan=data(3);value=plan}
  else if(p===USER+'/plans'&&method==='GET')value={list:plan?[plan]:[],page:Number(u.searchParams.get('page')),size:10,total:plan?1:0}
  else if(p.startsWith(USER+'/plans/')){
   if(p.endsWith('/feedback')){
    if(unknown&&!unknownSent){unknownSent=true;return route.fulfill({status:503,json:{code:503,message:'SYNTHETIC_UNKNOWN_RESULT',data:{errorCode:'UNAVAILABLE'}}})}
    expect(typeof body.instanceId).toBe('string');expect(body.expectedVersion).toBe(plan.version);const result=data(8);plan=result.plan;record=result.record;value=result
   }else if(p.endsWith('/statistics'))value=plan.stats
   else if(p.endsWith('/today'))value={...plan.today,date:u.searchParams.get('date')}
   else if(p.includes('/tasks/'))value=plan.tasks.find(t=>p.endsWith('/'+encodeURIComponent(t.id)))
   else if(p.endsWith('/records'))value={list:record?[record]:[],page:1,size:10,total:record?1:0}
   else if(p.includes('/records/'))value=discomfort?{record,review:record.review,safetyAlerts:record.safetyAlerts,suggestions:data(19).suggestions}:data(11)
   else if(p.endsWith('/suggestions'))value={list:discomfort?data(19).suggestions:record?data(12).list:[],total:record?1:0,page:1,size:10}
   else if(p.endsWith('/decision')){expect(body.expectedVersion).toBe(plan.version);const result=body.decision==='REJECT'?data(20):data(13);plan=result.plan;value=result}
   else if(p.endsWith('/adjustments'))value=data(14)
   else if(p.endsWith('/state')){
    expect(body.expectedVersion).toBe(plan.version)
    if(body.action==='PAUSE')plan={...plan,version:plan.version+1,status:'PAUSED',allowedActions:['RESUME','QUIT']}
    else if(body.action==='RESUME'){expect(body.selfLevel).toBe('BEGINNER');expect(body.safetyAcknowledged).toBe(true);plan=data(18)}
    else plan={...plan,status:'QUIT',version:plan.version+1,allowedActions:[]}
    value=plan
   }else if(p.endsWith('/ai-context'))value=data(16)
   else if(p.endsWith('/ai-explain')){expect(body.confirmed).toBe(true);return route.fulfill({status:503,json:{code:503,message:'SYNTHETIC_AI_UNAVAILABLE',data:{errorCode:'AI_UNAVAILABLE'}}})}
   else value=plan
  }else if(p===ADMIN+'/templates'&&method==='GET')value={list:[document||data(22)],total:1,page:Number(u.searchParams.get('page')),size:10}
  else if(p===ADMIN+'/templates'&&method==='POST'){document=data(23);value=document}
  else if(p.startsWith(ADMIN+'/templates/')){
   if(p.endsWith('/publication')){expect(body.expectedVersion).toBe(document.version);document={...document,status:body.status,safetyCorrected:!!body.safetyCorrection};value=document}
   else if(method==='PUT'){expect(body.expectedVersion).toBe(document.version);document={...data(24),status:'DRAFT'};value=document}
   else {document=document||data(22);value=document}
  }else if(p.endsWith('/unread-count'))value={unreadCount:0}
  return route.fulfill({json:{code:200,message:'SYNTHETIC_LOCAL_CONTRACT_ONLY',data:value}})
 })
 return {calls,errors,expectedFailures}
}
async function openTask(page){await page.getByRole('button',{name:/^查看任务：/}).first().click();await expect(page.getByLabel('任务教学与反馈')).toBeVisible()}
async function fillFeedback(page){
 const request=examples[8].request
 await page.getByLabel('完成情况').selectOption(request.completion);await page.getByLabel('主观难度').selectOption(request.difficulty);await page.getByLabel('实际练习分钟').fill(String(request.actualMinutes))
 for(const q of data(7).selfChecks){const answer=request.selfChecks.find(a=>a.questionId===q.id);if(answer)await page.getByLabel(q.question).selectOption(answer.answer)}
 await page.getByLabel('时间不足',{exact:true}).check();await page.getByLabel('动作困难',{exact:true}).check();await page.getByLabel('补充记录').fill(request.note)
}
test('complete client flow: preview/create, source, partial feedback, split acceptance, AI failure and explicit resume',async({page})=>{
 const observed=await setup(page);await page.setViewportSize({width:390,height:844});await page.goto('/training-guidance');await page.getByLabel('已维护模板').selectOption('basic-foundations@1');await page.getByLabel('我的训练目标').fill('记录基础练习');await page.getByLabel('当前水平（用户自评）').selectOption('BEGINNER');for(const day of ['周一','周二','周三','周四','周五','周六','周日'])await page.getByLabel(day,{exact:true}).check()
 await page.getByRole('button',{name:'预览训练安排',exact:true}).click();await expect(page.getByLabel('排期预览')).toBeVisible();expect(observed.calls.filter(c=>c.p===USER+'/plans'&&c.method==='POST')).toHaveLength(0);await page.getByRole('button',{name:'确认建立计划',exact:true}).click();await expect(page.getByText(/计划版本 0/)).toBeVisible();await openTask(page)
 await page.getByLabel('任务教学与反馈').getByText(/查看来源：/).first().click();await expect(page.getByText(data(7).sources[0].excerpt,{exact:true}).first()).toBeVisible();expect(await page.locator('a[href*="database/"]').count()).toBe(0);await fillFeedback(page);await page.getByRole('button',{name:'提交本次练习',exact:true}).click();await expect(page.getByLabel('本次训练回顾')).toContainText(data(8).review.message);expect(observed.calls.filter(c=>c.p.endsWith('/ai-explain'))).toHaveLength(0)
 await page.getByRole('button',{name:'待确认建议',exact:true}).click();await page.getByRole('button',{name:'查看建议前后差异'}).click();await expect(page.getByLabel('安排前后差异')).toContainText('调整前');await expect(page.getByLabel('安排前后差异')).toContainText('调整后');await page.getByRole('button',{name:'接受此建议'}).click();await expect(page.getByText(/计划版本 2/)).toBeVisible();await page.getByLabel('这个动作遇到困难：想问的问题').fill('站姿重心不稳如何自查？');await page.getByRole('button',{name:'预览带入的问题与资料'}).click();await expect(page.getByLabel('AI上下文预览')).toBeVisible();expect(observed.calls.filter(c=>c.p.endsWith('/ai-explain'))).toHaveLength(0);await page.getByRole('button',{name:'确认请求AI解释'}).click();await expect(page.getByRole('alert').filter({hasText:'AI解释暂不可用'})).toBeVisible();expect(observed.calls.filter(c=>c.p.endsWith('/feedback'))).toHaveLength(1)
 await page.getByRole('button',{name:'暂停计划',exact:true}).click();await page.getByRole('button',{name:'确认暂停',exact:true}).click();await page.getByRole('button',{name:'恢复计划',exact:true}).click();await page.getByLabel('恢复前重新自评').selectOption('BEGINNER');await page.getByLabel(/我已重新自评并了解安全提醒/).check();await page.getByRole('button',{name:'确认恢复',exact:true}).click();await expect(page.getByText(/计划版本 4/)).toBeVisible();await page.getByRole('button',{name:'练习历史',exact:true}).click();await expect(page.getByRole('button',{name:'查看练习回顾'})).toBeVisible();await page.getByRole('button',{name:'调整历史',exact:true}).click();await expect(page.getByRole('table',{name:'调整前后安排（只读预览）'})).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);expect(observed.errors).toEqual([])
})
test('unknown feedback retries exact original body; current discomfort suggestion can be rejected',async({page})=>{
 const unknown=await setup(page,{active:true,unknown:true});await page.goto('/training-guidance');await openTask(page);await fillFeedback(page);await page.getByRole('button',{name:'提交本次练习',exact:true}).click();await page.getByRole('button',{name:'重试原操作'}).click();await expect(page.getByLabel('本次训练回顾')).toBeVisible();const sent=unknown.calls.filter(c=>c.p.endsWith('/feedback'));expect(sent).toHaveLength(2);expect(sent[1].body).toEqual(sent[0].body);expect(unknown.errors).toEqual([])
 // Separate page/identity fixture demonstrates the recorded discomfort->reject scenario.
 await page.unroute('**/*');const rejected=await setup(page,{discomfort:true});await page.reload();await page.getByRole('button',{name:'待确认建议',exact:true}).click();await page.getByRole('button',{name:'查看建议前后差异'}).click();await page.getByRole('button',{name:'拒绝此建议'}).click();await expect(page.getByText(/计划版本 6/)).toBeVisible();const decision=rejected.calls.find(c=>c.p.endsWith('/decision'));expect(decision.body.decision).toBe('REJECT');expect(rejected.errors).toEqual([])
})
test('admin JSON save/new version/publication/disable safety correction are connected',async({page})=>{
 const observed=await setup(page,{admin:true});await page.goto('/admin/training-guidance');await page.getByRole('button',{name:'查看并编辑此版本'}).click();await page.getByRole('button',{name:'以当前文档另建模板'}).click();await page.getByRole('button',{name:'保存新草稿',exact:true}).click();await expect(page.getByRole('heading',{name:/所读版本 1/})).toBeVisible();await page.getByRole('button',{name:'保存为新版本草稿'}).click();await expect(page.getByRole('heading',{name:/所读版本 2/})).toBeVisible();await page.getByRole('button',{name:'检查JSON语法并预览'}).click();await expect(page.getByLabel('文档发布前预览')).toBeVisible();await page.getByLabel('操作理由').fill('已核对登记来源与版本');await page.getByLabel(/我已核对已保存版本/).check();await page.getByRole('button',{name:'确认发布已保存版本'}).click();await expect(page.getByRole('heading',{name:/所读版本 2 · 已发布/})).toBeVisible();await page.getByLabel('目标状态').selectOption('DISABLED');await page.getByLabel('操作理由').fill('安全纠正');await page.getByLabel(/这是安全纠正/).check();await page.getByLabel(/我已核对已保存版本/).check();await page.getByRole('button',{name:'确认停用已保存版本'}).click();await expect(page.getByRole('heading',{name:/所读版本 2 · 已停用/})).toBeVisible();expect(observed.calls.filter(c=>c.p.endsWith('/publication')).at(-1).body.safetyCorrection).toBe(true);expect(observed.errors).toEqual([])
})
