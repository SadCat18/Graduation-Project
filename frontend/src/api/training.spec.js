import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { trainingHttp, trainingApi, TrainingError, resultUnknown, trainingErrorText } from './training'
import { setAuth, getToken } from '../utils/auth'
const response=(status,body)=>async config=>({status,data:body,config,headers:{},statusText:''})
let adapter
beforeEach(()=>{adapter=trainingHttp.defaults.adapter;localStorage.clear();sessionStorage.clear()})
afterEach(()=>{trainingHttp.defaults.adapter=adapter})
it('success null, version zero and string template/task identifiers remain exact',async()=>{
 trainingHttp.defaults.adapter=response(200,{code:200,data:null});expect(await trainingApi.get('/api/user/training-guidance/current')).toBeNull()
 trainingHttp.defaults.adapter=response(200,{code:200,data:{id:'basic-foundations',version:0,instanceId:'root-uuid'}});expect(await trainingApi.get('/x')).toEqual({id:'basic-foundations',version:0,instanceId:'root-uuid'})
})
it('HTTP200 business failure retains code/data including legacy conflict',async()=>{
 trainingHttp.defaults.adapter=response(200,{code:409,message:'conflict',data:{errorCode:'PLAN_CONFLICT',legacyConflict:{userPlanIds:[7]}}});await expect(trainingApi.get('/x')).rejects.toMatchObject({status:409,errorCode:'PLAN_CONFLICT',data:{legacyConflict:{userPlanIds:[7]}}})
})
it('real HTTP errors preserve structured payload; 403 does not clear identity',async()=>{
 setAuth('SYNTHETIC','USER','test');trainingHttp.defaults.adapter=async()=>{throw {response:{status:409,data:{message:'changed',data:{errorCode:'PLAN_VERSION_CONFLICT'}}}}};await expect(trainingApi.get('/x')).rejects.toMatchObject({status:409,errorCode:'PLAN_VERSION_CONFLICT'});trainingHttp.defaults.adapter=async()=>{throw {response:{status:403,data:{code:403,message:'denied'}}}};await expect(trainingApi.get('/x')).rejects.toMatchObject({status:403});expect(getToken()).toBe('SYNTHETIC')
})
it('unknown timeout stays unknown without guessing from Chinese error text',async()=>{
 trainingHttp.defaults.adapter=async()=>{throw {code:'ECONNABORTED',message:'timeout'}};let error;try{await trainingApi.get('/x')}catch(e){error=e}expect(error.status).toBeUndefined();expect(error.transportCode).toBe('ECONNABORTED');expect(resultUnknown(error)).toBe(true);expect(resultUnknown(new TrainingError('请求超时',400,{errorCode:'VALIDATION_ERROR'}))).toBe(false)
})
it('AI error is separate and sensitive raw server text is not exposed as Provider detail',()=>{
 expect(trainingErrorText(new TrainingError('raw provider failure',503,{errorCode:'AI_UNAVAILABLE'}))).toContain('已保存记录');expect(trainingErrorText(new TrainingError('raw provider failure',503,{errorCode:'AI_UNAVAILABLE'}))).not.toContain('raw provider')
})
it('request auth and AI confirmation body are transmitted without automatic retry',async()=>{
 setAuth('SYNTHETIC','USER','test');let sent;trainingHttp.defaults.adapter=async config=>{sent=config;return {status:200,config,headers:{},data:{code:200,data:{reply:'ok'}}}};await trainingApi.explain('/api/user/training-guidance/plans/1/ai-explain',{recordId:'uuid',question:'q',contextHash:'hash',confirmed:true});expect(sent.headers.Authorization).toBe('Bearer SYNTHETIC');expect(sent.timeout).toBe(60000);expect(JSON.parse(sent.data)).toEqual({recordId:'uuid',question:'q',contextHash:'hash',confirmed:true})
})

it('request headers bind synchronously to submitting identity; late old 401 cannot sign out new identity',async()=>{
 setAuth('SYNTHETIC_A','USER','a');let finish,sent;trainingHttp.defaults.adapter=config=>{sent=config;return new Promise(resolve=>{finish=resolve})};const pending=trainingApi.get('/x');setAuth('SYNTHETIC_B','USER','b');expect(sent.headers.Authorization).toBe('Bearer SYNTHETIC_A');finish({status:200,config:sent,headers:{},data:{code:401,message:'old expired'}});await expect(pending).rejects.toMatchObject({status:401});expect(getToken()).toBe('SYNTHETIC_B')
})
it('current identity 401 expires only that request identity',async()=>{
 setAuth('SYNTHETIC_A','USER','a');trainingHttp.defaults.adapter=response(200,{code:401,message:'expired'});await expect(trainingApi.get('/x')).rejects.toMatchObject({status:401});expect(getToken()).toBe('')
})
