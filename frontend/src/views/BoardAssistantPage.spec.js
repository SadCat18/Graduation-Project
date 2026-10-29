import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import BoardAssistantPage from './BoardAssistantPage.vue'
import BoardDemandForm from '../components/board/BoardDemandForm.vue'
import { storeContinuation } from '../board/state'
const {api,router}=vi.hoisted(()=>({api:{recommend:vi.fn(),evaluate:vi.fn(),save:vi.fn(),replacements:vi.fn(),compare:vi.fn()},router:{push:vi.fn()}}))
vi.mock('../api/board',()=>({boardApi:api}))
vi.mock('vue-router',()=>({useRouter:()=>router,onBeforeRouteLeave:guard=>{router.leaveGuard=guard}}))
const demand={budgetCents:10000,purpose:'ACTION',preference:'VALUE',recommendationType:'BOTH'}
const evaluation={kind:'ASSEMBLY',demand,selection:{kind:'ASSEMBLY',templateKey:'TEST',items:[{category:'WHEEL',variantKey:'TEST-W1'}]},items:[{category:'WHEEL',label:'TEST轮',variantKey:'TEST-W1',requiredQuantity:4,packageQuantity:4,packageCount:1}],totalCents:1000,calculationVersion:'new',validationState:'VERIFIED',checks:[],missing:[]}
const response={complete:{state:'DATA_INSUFFICIENT',plans:[]},assembly:{state:'READY',plans:[evaluation]}}
let wrapper
const button=text=>wrapper.findAll('button').find(b=>b.text()===text)
async function openPlan(){wrapper.findComponent(BoardDemandForm).vm.$emit('submit',demand);await flushPromises();await button('配件组装（1）').trigger('click');await button('查看方案 1').trigger('click')}
describe('SYNTHETIC frontend assistant state (not real catalog acceptance)',()=>{
  beforeEach(()=>{vi.resetAllMocks();localStorage.clear();sessionStorage.clear();api.recommend.mockResolvedValue(response);api.evaluate.mockResolvedValue(evaluation);wrapper=mount(BoardAssistantPage,{global:{stubs:{RouterLink:true}}})})
  afterEach(()=>wrapper.unmount())
  it('retains each category no-result explanation and requires login for save',async()=>{
    wrapper.findComponent(BoardDemandForm).vm.$emit('submit',demand);await flushPromises()
    expect(wrapper.text()).toContain('资料不足不能视为预算不足')
    await button('配件组装（1）').trigger('click');await button('查看方案 1').trigger('click');await wrapper.find('form.board-item').trigger('submit');await flushPromises()
    expect(router.push).toHaveBeenCalledWith({path:'/login',query:{returnTo:'/board-assistant'}});expect(api.save).not.toHaveBeenCalled()
  })
  it('restores login context, recalculates without old token and never automatically saves',async()=>{
    wrapper.unmount();storeContinuation({demand,selection:evaluation.selection,name:'历史上下文',note:'备注',expectedCalculationVersion:'stale'})
    localStorage.setItem('skate_token','user');localStorage.setItem('skate_role','USER');wrapper=mount(BoardAssistantPage,{global:{stubs:{RouterLink:true}}});await flushPromises()
    expect(api.evaluate).toHaveBeenCalledWith({demand,selection:evaluation.selection});expect(api.save).not.toHaveBeenCalled();expect(wrapper.text()).toContain('尚未保存')
    await wrapper.find('form.board-item').trigger('submit');await flushPromises();api.save.mockResolvedValue({key:'saved',snapshotState:'VERIFIED'});await button('确认保存当前结果').trigger('click');await flushPromises();expect(api.save.mock.calls[0][0].expectedCalculationVersion).toBe('new')
  })
  it('price conflict displays fresh result and requires prepare/confirm again',async()=>{
    localStorage.setItem('skate_token','user');localStorage.setItem('skate_role','USER');await openPlan();await wrapper.find('form.board-item').trigger('submit');await flushPromises()
    api.save.mockRejectedValue({errorCode:'PRICE_CHANGED',data:{evaluation:{...evaluation,totalCents:2000,calculationVersion:'changed'}}});await button('确认保存当前结果').trigger('click');await flushPromises()
    expect(wrapper.text()).toContain('¥20.00');expect(wrapper.text()).toContain('重新确认');expect(button('确认保存当前结果')).toBeUndefined();expect(wrapper.text()).not.toContain('已保存方案')
  })
  it('a response arriving after return cannot repopulate detail',async()=>{
    await openPlan();let finish;api.replacements.mockImplementation(()=>new Promise(resolve=>{finish=resolve}))
    await button('替换轮子').trigger('click');await button('返回推荐结果').trigger('click');finish({total:1,list:[evaluation]});await flushPromises();expect(wrapper.find('[data-testid="selected-plan"]').exists()).toBe(false);expect(wrapper.text()).not.toContain('检查替换')
  })
  it('linked adjustment is only evaluated after explicit confirmation',async()=>{
    localStorage.setItem('skate_token','user');localStorage.setItem('skate_role','USER')
    await openPlan();const suggestion={...evaluation.selection,items:[{category:'WHEEL',variantKey:'SYN-V-WHEEL-ALT'},{category:'RISER',variantKey:'SYN-V-RISER'}]}
    const linked={...evaluation,calculationVersion:'CANDIDATE-OLD',selection:{...evaluation.selection,items:[{category:'WHEEL',variantKey:'SYN-V-WHEEL-ALT'}]},requiresConfirmation:true,proposedAdjustments:[{reasonCode:'ADJUSTMENT_REQUIRED',selection:suggestion}]}
    const fresh={...evaluation,selection:suggestion,calculationVersion:'LINKED-FRESH',requiresConfirmation:false}
    // This contract double rejects an old selection token; unconditional 200 would hide T07-R1.
    api.evaluate.mockImplementation(async payload=>{
      expect(payload.selection).toEqual(suggestion)
      if(payload.expectedCalculationVersion && payload.expectedCalculationVersion !== 'LINKED-FRESH') throw {errorCode:'PRICE_CHANGED',data:{evaluation:fresh}}
      return fresh
    })
    api.save.mockImplementation(async payload=>{expect(payload.selection).toEqual(suggestion);expect(payload.expectedCalculationVersion).toBe('LINKED-FRESH');return {key:'saved',snapshotState:'VERIFIED'}})
    api.replacements.mockResolvedValue({total:1,list:[linked]});await button('替换轮子').trigger('click');await flushPromises();await button('检查替换 1').trigger('click')
    expect(api.evaluate).not.toHaveBeenCalled();expect(wrapper.text()).toContain('垫片规格已变更');expect(wrapper.text()).not.toContain('SYN-V-RISER');await button('确认建议 1 并重算').trigger('click');await flushPromises()
    expect(api.evaluate.mock.calls[0][0].selection.items).toHaveLength(2);expect(api.evaluate.mock.calls[0][0]).not.toHaveProperty('expectedCalculationVersion');expect(wrapper.text()).not.toContain('价格已变化')
    await wrapper.find('form.board-item').trigger('submit');await flushPromises();expect(api.evaluate.mock.calls[1][0].expectedCalculationVersion).toBe('LINKED-FRESH');await button('确认保存当前结果').trigger('click');await flushPromises();expect(api.save).toHaveBeenCalledTimes(1)
  })
  it('single-item replacement still checks its candidate version and shows price conflict',async()=>{
    await openPlan();const single={...evaluation,calculationVersion:'SINGLE-CANDIDATE',selection:{...evaluation.selection,items:[{category:'WHEEL',variantKey:'SYN-V-WHEEL-ALT'}]}}
    api.replacements.mockResolvedValue({total:1,list:[single]});api.evaluate.mockRejectedValue({errorCode:'PRICE_CHANGED',data:{evaluation:{...single,calculationVersion:'SINGLE-NEW'}}})
    await button('替换轮子').trigger('click');await flushPromises();await button('检查替换 1').trigger('click');await button('确认单项替换并重算').trigger('click');await flushPromises();expect(api.evaluate.mock.calls[0][0].expectedCalculationVersion).toBe('SINGLE-CANDIDATE');expect(wrapper.text()).toContain('价格已变化');expect(api.save).not.toHaveBeenCalled()
  })
  it('incomplete continuation remains a visibly priced-null draft',async()=>{
    wrapper.unmount();storeContinuation({demand,selection:evaluation.selection,name:'合成资料不足草稿'})
    api.evaluate.mockResolvedValue({...evaluation,totalCents:null,knownSubtotalCents:500,validationState:'INSUFFICIENT_DATA',missing:['WHEEL:VALID_QUOTE_REQUIRED']})
    localStorage.setItem('skate_token','user');localStorage.setItem('skate_role','USER');wrapper=mount(BoardAssistantPage,{global:{stubs:{RouterLink:true}}});await flushPromises()
    expect(wrapper.text()).toContain('已知小计 ¥5.00');expect(wrapper.text()).toContain('仅保存为草稿');expect(wrapper.text()).toContain('缺少有效')
  })
  it('clears a successful comparison before retry and keeps it cleared on failure',async()=>{
    wrapper.findComponent(BoardDemandForm).vm.$emit('submit',demand);await flushPromises();await button('配件组装（1）').trigger('click');await button('加入对比').trigger('click')
    api.compare.mockResolvedValue({evaluations:[evaluation]});await button('重新计算并对比').trigger('click');await flushPromises();expect(wrapper.find('table').exists()).toBe(true)
    api.compare.mockRejectedValue(new Error('比较失败'));await button('重新计算并对比').trigger('click');expect(wrapper.find('table').exists()).toBe(false);await flushPromises();expect(wrapper.find('table').exists()).toBe(false);expect(wrapper.text()).toContain('比较失败')
  })
  it('removing a selected plan discards the comparison in flight',async()=>{
    wrapper.findComponent(BoardDemandForm).vm.$emit('submit',demand);await flushPromises();await button('配件组装（1）').trigger('click');await button('加入对比').trigger('click');let finish
    api.compare.mockImplementation(()=>new Promise(resolve=>{finish=resolve}));await button('重新计算并对比').trigger('click');await button('移除 1').trigger('click');finish({evaluations:[evaluation]});await flushPromises();expect(wrapper.find('table').exists()).toBe(false)
  })
  it('locks local return, route leave and repeated submission until save result arrives',async()=>{
    localStorage.setItem('skate_token','user');localStorage.setItem('skate_role','USER');await openPlan();await wrapper.find('form.board-item').trigger('submit');await flushPromises();let finish
    api.save.mockImplementation(()=>new Promise(resolve=>{finish=resolve}));await button('确认保存当前结果').trigger('click');expect(button('返回推荐结果').attributes('disabled')).toBeDefined();await button('返回推荐结果').trigger('click');await wrapper.find('form.board-item').trigger('submit');expect(api.save).toHaveBeenCalledTimes(1);expect(router.leaveGuard()).toBe(false)
    const leaving=new Event('beforeunload',{cancelable:true});window.dispatchEvent(leaving);expect(leaving.defaultPrevented).toBe(true)
    finish({key:'SAVED',snapshotState:'VERIFIED'});await flushPromises();expect(wrapper.text()).toContain('已保存方案');expect(wrapper.find('[data-testid="selected-plan"]').exists()).toBe(true);expect(router.leaveGuard()).toBe(true)
  })
  it('uncertain write outcome blocks retry and persists across remount',async()=>{
    localStorage.setItem('skate_token','user');localStorage.setItem('skate_role','USER');await openPlan();await wrapper.find('form.board-item').trigger('submit');await flushPromises();api.save.mockRejectedValue(new Error('timeout'));await button('确认保存当前结果').trigger('click');await flushPromises();expect(wrapper.text()).toContain('本次保存结果未知');expect(wrapper.text()).not.toContain('timeout');await wrapper.find('form.board-item').trigger('submit');expect(api.save).toHaveBeenCalledTimes(1)
    wrapper.unmount();wrapper=mount(BoardAssistantPage,{global:{stubs:{RouterLink:true}}});await flushPromises();expect(wrapper.text()).toContain('本次保存结果未知');expect(button('重新计算并准备保存').attributes('disabled')).toBeDefined();await button('我已核对记录，结束本次未知操作').trigger('click');expect(wrapper.find('[data-testid="selected-plan"]').exists()).toBe(false);expect(api.save).toHaveBeenCalledTimes(1)
  })
})
