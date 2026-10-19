import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Page from './BoardPlansPage.vue'
const {api,router}=vi.hoisted(()=>({api:{plans:vi.fn(),plan:vi.fn(),rename:vi.fn(),copy:vi.fn(),remove:vi.fn()},router:{replace:vi.fn()}}))
vi.mock('../api/board',()=>({boardApi:api}))
vi.mock('vue-router',()=>({useRouter:()=>router}))
const snapshot={key:'TEST-S',name:'合成历史方案',note:'',totalCents:1000,kind:'COMPLETE',createdAt:'2026-10-10T00:00:00Z',snapshotState:'MANUFACTURER_COMPLETE',items:[],checks:[]}
let wrapper
const button=text=>wrapper.findAll('button').find(b=>b.text()===text)
describe('SYNTHETIC personal snapshots frontend',()=>{
  beforeEach(async()=>{vi.resetAllMocks();localStorage.setItem('skate_token','test');localStorage.setItem('skate_role','USER');api.plans.mockResolvedValue({list:[snapshot],total:1});api.plan.mockResolvedValue({snapshot,latestEvaluation:{...snapshot,totalCents:2000,validationState:'MANUFACTURER_COMPLETE'}});wrapper=mount(Page,{global:{stubs:{RouterLink:true}}});await flushPromises()})
  afterEach(()=>wrapper.unmount())
  it('separates snapshot and latest price, copy remains historical',async()=>{
    await button('查看合成历史方案').trigger('click');await flushPromises();expect(wrapper.get('[data-testid="saved-snapshot"]').text()).toContain('¥10.00');expect(wrapper.get('[data-testid="latest-evaluation"]').text()).toContain('¥20.00')
    api.copy.mockResolvedValue({...snapshot,key:'TEST-COPY',historicalCopy:true,copiedFromKey:'TEST-S'});await button('复制历史方案').trigger('click');await flushPromises();expect(wrapper.text()).toContain('价格保留原快照');expect(wrapper.get('[data-testid="saved-snapshot"]').text()).toContain('¥10.00')
  })
  it('deletion requires confirmation and failure never reports success',async()=>{
    await button('查看合成历史方案').trigger('click');await flushPromises();await button('删除方案').trigger('click');expect(api.remove).not.toHaveBeenCalled();api.remove.mockRejectedValue(new Error('断网'));await button('确认删除').trigger('click');await flushPromises();expect(wrapper.text()).toContain('断网');expect(wrapper.text()).not.toContain('方案已删除')
  })
  it('admin identity is rejected locally and no personal API is called',async()=>{
    wrapper.unmount();api.plans.mockClear();localStorage.setItem('skate_role','ADMIN');wrapper=mount(Page,{global:{stubs:{RouterLink:true}}});await flushPromises();expect(api.plans).not.toHaveBeenCalled();expect(wrapper.text()).toContain('管理员不能访问')
  })
})
