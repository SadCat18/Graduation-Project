import { beforeEach, describe, expect, it } from 'vitest'
import { boardApi, boardHttp } from './board'
import { getToken, setAuth } from '../utils/auth'
import { storeContinuation, readContinuation } from '../board/state'
const adapter=(body,status=200)=>async config=>({data:body,status,statusText:'',headers:{},config})
describe('board adapter structured outcomes',()=>{
  beforeEach(()=>{localStorage.clear();sessionStorage.clear()})
  it('treats HTTP200 business conflicts as errors with the new evaluation',async()=>{
    boardHttp.defaults.adapter=adapter({code:200,data:{errorCode:'PRICE_CHANGED',evaluation:{totalCents:200},requiresConfirmation:true}})
    await expect(boardApi.save({})).rejects.toMatchObject({status:200,errorCode:'PRICE_CHANGED',data:{evaluation:{totalCents:200}}})
  })
  it('never calls a non-success envelope a success',async()=>{
    boardHttp.defaults.adapter=adapter({code:400,message:'bad',data:{errors:[{path:'name'}]}})
    await expect(boardApi.save({})).rejects.toMatchObject({status:400,data:{errors:[{path:'name'}]}})
  })
  it('403 preserves auth; 401 clears auth but preserves board continuation',async()=>{
    setAuth('test','USER','n'); storeContinuation({demand:{budgetCents:100},selection:{items:[]},name:'n'})
    boardHttp.defaults.adapter=async()=>{throw {response:{status:403,data:{message:'denied'}}}}
    await expect(boardApi.plans({})).rejects.toMatchObject({status:403}); expect(getToken()).toBe('test')
    boardHttp.defaults.adapter=async()=>{throw {response:{status:401,data:{message:'expired'}}}}
    await expect(boardApi.plans({})).rejects.toMatchObject({status:401}); expect(getToken()).toBe(''); expect(readContinuation()?.name).toBe('n')
  })
  it('uses auth header and encodes resource keys',async()=>{
    setAuth('test','USER','n'); let sent
    boardHttp.defaults.adapter=async config=>{sent=config; return {data:{code:200,data:{key:'k'}},status:200,headers:{},config}}
    await boardApi.plan('a/b'); expect(sent.url).toContain('a%2Fb'); expect(sent.headers.Authorization).toBe('Bearer test')
  })
})
