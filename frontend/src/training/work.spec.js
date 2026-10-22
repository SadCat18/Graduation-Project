import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { webcrypto } from 'node:crypto'
import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { useTrainingWork } from './work'
import { trainingApi, TrainingError, USER_TRAINING as root } from '../api/training'
import { setAuth } from '../utils/auth'
vi.mock('vue-router',()=>({onBeforeRouteLeave:vi.fn()}))
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {promise,resolve,reject}}
let w, work
async function setup(prefix=root){w=mount(defineComponent({setup(){work=useTrainingWork(prefix);return()=>null}}));await work.init()}
beforeEach(()=>{vi.stubGlobal('crypto',webcrypto);localStorage.clear();sessionStorage.clear();setAuth('SYNTHETIC_A','USER','test')})
afterEach(()=>{w?.unmount();vi.restoreAllMocks();vi.unstubAllGlobals()})
it('unknown write reuses exact original key, payload and version despite form mutation; new intent gets new key',async()=>{
 await setup();const payload={expectedVersion:0,actualMinutes:4};const send=vi.spyOn(trainingApi,'send').mockRejectedValueOnce(new TrainingError('timeout',undefined,undefined,'ECONNABORTED')).mockResolvedValue({plan:{id:1,version:1}});await work.write('提交练习',root+'/plans/1/feedback',payload,vi.fn());const original=JSON.parse(JSON.stringify(send.mock.calls[0][0]));expect(work.pending.value.payload.expectedVersion).toBe(0);payload.actualMinutes=99;const commit=vi.fn();await work.retry(commit);expect(send.mock.calls[1][0]).toEqual(original);expect(commit).toHaveBeenCalledTimes(1);expect(work.pending.value).toBeNull();await work.write('再练一次',root+'/plans/1/feedback',{expectedVersion:1,actualMinutes:1},vi.fn());expect(send.mock.calls[2][0].payload.requestKey).not.toBe(original.payload.requestKey)
})
it('double click sends once; same task response only commits before leaving its generation',async()=>{
 await setup();const d=deferred(),commit=vi.fn(),send=vi.spyOn(trainingApi,'send').mockImplementation(()=>d.promise);const first=work.write('提交',root+'/plans/1/feedback',{expectedVersion:0},commit);await work.write('重复',root+'/plans/1/feedback',{expectedVersion:0},commit);expect(send).toHaveBeenCalledTimes(1);work.invalidate();d.resolve({plan:{id:1}});await first;expect(commit).not.toHaveBeenCalled()
})
it('unknown survives unmount/remount within same identity and retains exact original version',async()=>{
 await setup();vi.spyOn(trainingApi,'send').mockRejectedValueOnce(new TrainingError('network'));await work.write('提交',root+'/plans/1/feedback',{expectedVersion:0,actualMinutes:4});const original=JSON.parse(JSON.stringify(work.pending.value));w.unmount();await setup();expect(work.pending.value).toEqual(original);expect(work.pending.value.payload.expectedVersion).toBe(0)
})
it('different user cannot restore or receive previous user write/read; old finally cannot clear new read',async()=>{
 await setup();const d=deferred(),commit=vi.fn();vi.spyOn(trainingApi,'send').mockImplementation(()=>d.promise);const p=work.write('提交',root+'/plans/1/feedback',{expectedVersion:0},commit);setAuth('SYNTHETIC_B','USER','b');await work.init();expect(work.pending.value).toBeNull();d.resolve({plan:{id:1}});await p;expect(commit).not.toHaveBeenCalled();expect(work.writing.value).toBe(false)
})
it.each(['success','failure'])('read generation protects late %s and finally after task switch',async kind=>{
 await setup();const a=deferred(),b=deferred(),commit=vi.fn();const p=work.read('task',()=>a.promise,commit);const q=work.read('task',()=>b.promise,commit);if(kind==='failure')a.reject(new TrainingError('old',400));else a.resolve({id:'old'});await p;expect(work.busy.task).toBe(true);expect(work.errors.task).toBe('');b.resolve({id:'new'});await q;expect(commit).toHaveBeenCalledTimes(1);expect(commit).toHaveBeenCalledWith({id:'new'});expect(work.busy.task).toBe(false)
})
it('409 is structured, not unknown, and can show refresh/legacy conflict without fabricating success',async()=>{
 await setup();const error=new TrainingError('conflict',409,{errorCode:'PLAN_CONFLICT',legacyConflict:{userPlanIds:[7]}});vi.spyOn(trainingApi,'send').mockRejectedValue(error);const commit=vi.fn();await work.write('建立',root+'/plans',{goal:'test'},commit);expect(work.failure.value.data.legacyConflict.userPlanIds).toEqual([7]);expect(work.pending.value).toBeNull();expect(work.errors.write).toContain('已有未终结计划');expect(commit).not.toHaveBeenCalled()
})
it('storage failure before dispatch never sends an untracked write',async()=>{
 await setup();vi.spyOn(Storage.prototype,'setItem').mockImplementation(()=>{throw new Error('blocked')});const send=vi.spyOn(trainingApi,'send');await work.write('建立',root+'/plans',{goal:'test'});expect(send).not.toHaveBeenCalled();expect(work.errors.write).toContain('尚未发送')
})
it.each(['success','failure'])('unmount invalidates late write %s and preserves original recovery intent',async kind=>{
 await setup();const d=deferred(),commit=vi.fn();vi.spyOn(trainingApi,'send').mockImplementation(()=>d.promise);const p=work.write('提交',root+'/plans/1/feedback',{expectedVersion:0},commit);const original=JSON.parse(JSON.stringify(work.pending.value));w.unmount();if(kind==='success')d.resolve({record:{id:'r'}});else d.reject(new TrainingError('late'));await p;expect(commit).not.toHaveBeenCalled();await setup();expect(work.pending.value).toEqual(original)
})

it('a new pending operation clears previous success notice instead of implying new success',async()=>{
 await setup();const d=deferred();vi.spyOn(trainingApi,'send').mockResolvedValueOnce({id:1}).mockImplementationOnce(()=>d.promise);await work.write('第一次',root+'/plans',{goal:'one'});expect(work.notice.value).toContain('确认');const p=work.write('第二次',root+'/plans',{goal:'two'});expect(work.notice.value).toBe('');d.resolve({id:2});await p
})

it('wrong role cannot initialize admin work or submit through its controls',async()=>{
 await setup('/api/admin/training-guidance');expect(work.ready.value).toBe(false);const send=vi.spyOn(trainingApi,'send');await work.write('保存草稿','/api/admin/training-guidance/templates',{document:{title:'test'}});expect(send).not.toHaveBeenCalled();expect(work.errors.session).toContain('对应的已登录身份')
})
