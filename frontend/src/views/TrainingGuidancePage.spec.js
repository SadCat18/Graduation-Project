import { mount, flushPromises } from '@vue/test-utils'
import { webcrypto } from 'node:crypto'
import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import User from './TrainingGuidancePage.vue'
import Admin from './admin/TrainingGuidanceAdminPage.vue'
import Feedback from '../components/training/TrainingFeedback.vue'
import Setup from '../components/training/TrainingSetup.vue'
import Sources from '../components/training/TrainingSources.vue'
import Stats from '../components/training/TrainingStats.vue'
import Status from '../components/training/TrainingStatus.vue'
import { TrainingError, USER_TRAINING as root, ADMIN_TRAINING as adminRoot } from '../api/training'
import { setAuth } from '../utils/auth'
import evidence from '../../tests/fixtures/training/service-examples.json'
const m=vi.hoisted(()=>({api:{get:vi.fn(),preview:vi.fn(),send:vi.fn(),explain:vi.fn()}}))
vi.mock('../api/training',async original=>({...await original(),trainingApi:m.api}))
vi.mock('vue-router',()=>({onBeforeRouteLeave:vi.fn()}))
const clone=v=>JSON.parse(JSON.stringify(v)), example=n=>clone(evidence.examples[n].response.data)
let w,plan
const button=text=>w.findAll('button').find(b=>b.text()===text)
async function settle(){await flushPromises();await new Promise(r=>setTimeout(r,10));await flushPromises()}
async function mountPage(C=User){w=mount(C,{global:{stubs:{RouterLink:true}}});await settle()}
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {promise,resolve,reject}}
beforeEach(()=>{
 vi.resetAllMocks();vi.stubGlobal('crypto',webcrypto);localStorage.clear();sessionStorage.clear();setAuth('SYNTHETIC_USER','USER','test');plan=example(3)
 m.api.get.mockImplementation(async(path,params)=>{
  if(path===root+'/templates')return example(0)
  if(path===root+'/current'||path===root+'/plans/'+plan.id)return clone(plan)
  if(path.startsWith(root+'/templates/'))return example(1)
  if(path.endsWith('/statistics'))return clone(plan.stats)
  if(path.endsWith('/suggestions'))return example(12)
  if(path.includes('/tasks/'))return {...example(7),allowedActions:['FEEDBACK']}
  if(path.endsWith('/records'))return example(10)
  if(path.includes('/records/'))return example(11)
  if(path.endsWith('/adjustments'))return example(14)
  if(path===root+'/plans')return {list:[clone(plan)],page:params.page,total:1,size:10}
  if(path.endsWith('/today'))return {...example(6),date:params.date,restDay:true,tasks:[]}
  if(path===adminRoot+'/templates')return example(21)
  if(path.startsWith(adminRoot+'/templates/'))return example(22)
  throw new Error('Unmapped synthetic test route '+path)
 })
 m.api.send.mockImplementation(async intent=>{
  if(intent.path.endsWith('/feedback')){const value=example(8);plan=value.plan;return value}
  if(intent.path.endsWith('/decision')){const value=example(13);plan=value.plan;return value}
  if(intent.path===root+'/plans'){plan=example(3);return clone(plan)}
  return {...clone(plan),version:plan.version+1,status:'PAUSED',allowedActions:['RESUME','QUIT']}
 })
 m.api.preview.mockImplementation(async path=>path.endsWith('/ai-context')?example(16):example(2));m.api.explain.mockRejectedValue(new TrainingError('unavailable',503,{errorCode:'AI_UNAVAILABLE'}))
})
afterEach(()=>{w?.unmount();vi.unstubAllGlobals()})
it('uses real-service shapes: version0, self-report statistics, rest day and readable source excerpts',async()=>{
 await mountPage();expect(w.text()).toContain('计划版本 0');expect(w.text()).toContain('0.0%');expect(w.text()).toContain('0 分钟');const taskButton=w.findAll('button').find(b=>b.text().startsWith('查看任务：'));await taskButton.trigger('click');await settle();expect(w.findComponent(Feedback).props('task').id).toBe(example(7).id);expect(w.findComponent(Sources).findAll('a')).toHaveLength(0);expect(w.text()).toContain(example(7).sources[0].excerpt);await button('查看该日安排').trigger('click');await settle();expect(w.text()).toContain('休息日，不算漏练')
})
it('preview is not create, creates with string template/version and never uses temporary task ids',async()=>{
 m.api.get.mockImplementationOnce(async()=>example(0)).mockImplementationOnce(async()=>null);await mountPage();const data={templateId:'basic-foundations',templateVersion:1,goal:'test',selfLevel:'BEGINNER',trainingDays:[2,4],sessionMinutes:20,startDate:'2026-10-22'};w.findComponent(Setup).vm.$emit('preview',data);await settle();expect(m.api.send).not.toHaveBeenCalled();expect(w.text()).toContain('排期预览（尚未建立）');await button('确认建立计划').trigger('click');await settle();const intent=m.api.send.mock.calls[0][0];expect(intent.payload).toMatchObject(data);expect(intent.payload).not.toHaveProperty('instanceId');expect(intent.payload).not.toHaveProperty('expectedVersion');expect(w.text()).toContain('计划版本 0')
})
it('feedback success shows fixed review and safety immediately; AI preview/confirm failure never resubmits feedback',async()=>{
 await mountPage();await w.findAll('button').find(b=>b.text().startsWith('查看任务：')).trigger('click');await settle();const feedback={instanceId:example(7).id,practiceDate:'2026-10-22',completion:'PARTIAL',actualMinutes:4,difficulty:'MODERATE',selfChecks:[],reasons:['TIME_SHORTAGE'],note:''};w.findComponent(Feedback).vm.$emit('submit',feedback);await settle();expect(m.api.send.mock.calls[0][0].payload.expectedVersion).toBe(0);expect(w.text()).toContain(example(8).review.message);expect(m.api.explain).not.toHaveBeenCalled();await w.find('textarea').setValue('站姿困难');await button('预览带入的问题与资料').trigger('click');await settle();expect(m.api.explain).not.toHaveBeenCalled();expect(w.text()).toContain('请确认后再请求AI解释');await button('确认请求AI解释').trigger('click');await settle();expect(m.api.send).toHaveBeenCalledTimes(1);expect(m.api.explain).toHaveBeenCalledTimes(1);expect(w.text()).toContain('已保存记录和固定指导不受影响');expect(w.text()).toContain(example(8).review.message);expect(button('确认请求AI解释')).toBeUndefined()
})
it('structured unknown retry retains same payload/version/key; another practice is a new intent',async()=>{
 await mountPage();await w.findAll('button').find(b=>b.text().startsWith('查看任务：')).trigger('click');await settle();m.api.send.mockRejectedValueOnce(new TrainingError('timeout'));w.findComponent(Feedback).vm.$emit('submit',{instanceId:example(7).id,practiceDate:'2026-10-22',completion:'PARTIAL',actualMinutes:4,difficulty:'MODERATE',selfChecks:[],reasons:[],note:''});await settle();const original=clone(m.api.send.mock.calls[0][0]);expect(w.text()).toContain('结果未知');await button('重试原操作').trigger('click');await settle();expect(m.api.send.mock.calls[1][0]).toEqual(original);expect(w.text()).toContain('操作已由服务端确认')
})
it('suggestion before/after stays explicit; decision sends current version and respects allowedActions',async()=>{
 await mountPage();await button('待确认建议').trigger('click');await settle();await button('查看建议前后差异').trigger('click');expect(w.text()).toContain('调整前');expect(w.text()).toContain('调整后');expect(w.text()).toContain('剩余安排');await button('拒绝此建议').trigger('click');await settle();expect(m.api.send.mock.calls[0][0].payload).toMatchObject({decision:'REJECT',expectedVersion:0});expect(m.api.send.mock.calls[0][0].path).toContain(example(12).list[0].id)
})
it('resume needs an explicit fresh self-assessment and safety acknowledgement body, no invented clearance',async()=>{
 plan={...plan,status:'PAUSED',allowedActions:['RESUME','QUIT']};await mountPage();await button('恢复计划').trigger('click');const select=w.findAll('select').find(x=>x.findAll('option').some(o=>o.text()==='请明确选择'));await select.setValue('BEGINNER');await w.find('input[type=checkbox]').setValue(true);await w.find('form').trigger('submit');await settle();expect(m.api.send.mock.calls[0][0].payload).toMatchObject({action:'RESUME',expectedVersion:0,selfLevel:'BEGINNER',safetyAcknowledged:true});expect(w.text()).not.toContain('症状已消失')
})
it('slow task A never replaces fast task B; identity change clears private data',async()=>{
 await mountPage();const a=deferred();m.api.get.mockImplementationOnce(()=>a.promise).mockResolvedValueOnce({...example(7),id:'other-root',title:'B任务'});const buttons=w.findAll('button').filter(b=>b.text().startsWith('查看任务：'));await buttons[0].trigger('click');await buttons[1].trigger('click');await settle();a.resolve({...example(7),title:'A旧任务'});await settle();expect(w.text()).toContain('B任务');expect(w.text()).not.toContain('A旧任务');setAuth('SYNTHETIC_OTHER','USER','other');await settle();expect(w.text()).not.toContain('B任务');expect(w.text()).not.toContain(plan.goal)
})
it('AI question edits invalidate preview; changing records keeps history and statistics independently paginated',async()=>{
 await mountPage();await button('练习历史').trigger('click');await settle();await button('查看练习回顾').trigger('click');await settle();await w.find('textarea').setValue('问题1');await button('预览带入的问题与资料').trigger('click');await settle();await w.find('textarea').setValue('问题2');await settle();expect(button('确认请求AI解释')).toBeUndefined();expect(m.api.explain).not.toHaveBeenCalled();await button('调整历史').trigger('click');await settle();expect(w.text()).toContain('调整前后安排');expect(m.api.get.mock.calls.some(([p])=>p.endsWith('/adjustments'))).toBe(true);await button('计划历史').trigger('click');await settle();expect(button('查看历史计划')).toBeTruthy()
})
it('admin JSON creates, versions, validates publication failure, then disables with safety correction',async()=>{
 setAuth('SYNTHETIC_ADMIN','ADMIN','admin');m.api.send.mockResolvedValueOnce(example(23)).mockResolvedValueOnce(example(24)).mockRejectedValueOnce(new TrainingError('来源未完整',400,{errorCode:'VALIDATION_ERROR'})).mockResolvedValueOnce({...example(25),status:'DISABLED',safetyCorrected:true});await mountPage(Admin);await button('查看并编辑此版本').trigger('click');await settle();await button('以当前文档另建模板').trigger('click');await button('保存新草稿').trigger('click');await settle();expect(m.api.send.mock.calls[0][0].payload).toHaveProperty('document');expect(m.api.send.mock.calls[0][0].payload).not.toHaveProperty('expectedVersion');await button('保存为新版本草稿').trigger('click');await settle();expect(m.api.send.mock.calls[1][0].payload.expectedVersion).toBe(1);expect(w.text()).toContain('所读版本 2');await w.findAll('textarea')[1].setValue('核对发布');await w.findAll('input[type=checkbox]').at(-1).setValue(true);await w.find('form').trigger('submit');await settle();expect(w.text()).toContain('来源未完整');expect(m.api.send.mock.calls[2][0].payload.expectedVersion).toBe(2);await w.find('select').setValue('DISABLED');await w.findAll('input[type=checkbox]')[0].setValue(true);await w.findAll('input[type=checkbox]').at(-1).setValue(true);await w.find('form').trigger('submit');await settle();expect(m.api.send.mock.calls[3][0].payload).toMatchObject({status:'DISABLED',safetyCorrection:true,expectedVersion:2});expect(w.text()).toContain('已停用')
})
it('admin invalid JSON never sends; unsaved edit cannot publish previous version accidentally',async()=>{
 setAuth('SYNTHETIC_ADMIN','ADMIN','admin');await mountPage(Admin);await button('查看并编辑此版本').trigger('click');await settle();await w.find('textarea').setValue('{bad');await button('保存为新版本草稿').trigger('click');await settle();expect(m.api.send).not.toHaveBeenCalled();expect(w.text()).toContain('JSON语法未通过');expect(button('确认发布已保存版本').attributes('disabled')).toBeDefined()
})
it('completion ratio zero is not missing and source locator is readable, never an invented external href',()=>{
 w=mount(Stats,{props:{stats:{completedTasks:0,totalTasks:4,completionRate:0,practiceMinutes:0,practiceCount:0,selfAssessmentSummary:{NOT_ASSESSED:4}}}});expect(w.text()).toContain('0.0%');expect(w.text()).toContain('0 分钟');w.unmount();w=mount(Sources,{props:{sources:example(1).sources}});expect(w.findAll('summary')).toHaveLength(4);expect(w.findAll('a')).toHaveLength(0);for(const s of example(1).sources){expect(w.text()).toContain(s.excerpt);expect(w.text()).toContain(s.locator)}
})

it('unknown old feedback cannot retry into an in-flight different history plan, without waiting for new plan',async()=>{
 await mountPage();await w.findAll('button').find(b=>b.text().startsWith('查看任务：')).trigger('click');await settle();m.api.send.mockRejectedValueOnce(new TrainingError('timeout'));w.findComponent(Feedback).vm.$emit('submit',{instanceId:example(7).id,practiceDate:'2026-10-22',completion:'PARTIAL',actualMinutes:4,difficulty:'MODERATE',selfChecks:[],reasons:[],note:''});await settle();expect(m.api.send).toHaveBeenCalledTimes(1)
 const other={...clone(plan),id:200020,goal:'另一个历史计划',status:'QUIT',allowedActions:[]};m.api.get.mockImplementationOnce(async()=>({list:[other],total:1,page:1,size:10}));await button('计划历史').trigger('click');await settle();const slow=deferred();m.api.get.mockImplementationOnce(()=>slow.promise);await button('查看历史计划').trigger('click');await flushPromises();expect(button('重试原操作').attributes('disabled')).toBeDefined();w.findComponent(Status).vm.$emit('retry');await flushPromises();expect(m.api.send).toHaveBeenCalledTimes(1);slow.resolve(other);await settle();expect(w.text()).toContain('另一个历史计划');await button('重试原操作').trigger('click');await settle();expect(w.text()).toContain('另一个历史计划');expect(w.text()).toContain('当前查看的计划已有变化')
})
it('clearing template selection invalidates in-flight old detail and preview',async()=>{
 await mountPage();await button('建立新计划').trigger('click');await settle();const slow=deferred();m.api.get.mockImplementationOnce(()=>slow.promise);w.findComponent(Setup).vm.$emit('template',example(0)[0]);await flushPromises();w.findComponent(Setup).vm.$emit('change');w.findComponent(Setup).vm.$emit('template',undefined);slow.resolve({...example(1),title:'迟到的旧模板'});await settle();expect(w.text()).not.toContain('迟到的旧模板');expect(button('确认建立计划')).toBeUndefined()
})

it.each(['records','suggestions','adjustments'])('history pagination %s requests current plan and exact page independently',async name=>{
 await mountPage();const titles={records:'练习历史',suggestions:'待确认建议',adjustments:'调整历史'};const prior=m.api.get.getMockImplementation();m.api.get.mockImplementation(async(path,params)=>path.endsWith('/'+name)?{list:[],total:30,page:params.page,size:10}:prior(path,params));await button(titles[name]).trigger('click');await settle();await button('下一页').trigger('click');await settle();expect(m.api.get.mock.calls.some(([path,params])=>path===root+'/plans/'+plan.id+'/'+name&&params.page===2&&params.size===10)).toBe(true);expect(w.text()).toContain('第 2 页 · 共 30 条')
})
it('resume does not send without the fresh self-assessment and acknowledgement',async()=>{
 plan={...plan,status:'PAUSED',allowedActions:['RESUME','QUIT']};await mountPage();await button('恢复计划').trigger('click');await w.find('form').trigger('submit');await settle();expect(m.api.send).not.toHaveBeenCalled()
})

it('cancelled resume does not silently reuse previous self-assessment acknowledgement',async()=>{
 plan={...plan,status:'PAUSED',allowedActions:['RESUME','QUIT']};await mountPage();await button('恢复计划').trigger('click');await w.find('select').setValue('BEGINNER');await w.find('input[type=checkbox]').setValue(true);await button('取消状态操作').trigger('click');await button('恢复计划').trigger('click');expect(w.find('select').element.value).toBe('');expect(w.find('input[type=checkbox]').element.checked).toBe(false)
})
