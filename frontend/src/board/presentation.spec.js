import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import Demand from '../components/board/BoardDemandForm.vue'
import Pricing from '../components/board/BoardPricing.vue'
import Evaluation from '../components/board/BoardEvaluation.vue'
import Checks from '../components/board/BoardChecks.vue'
import Assistant from '../views/BoardAssistantPage.vue'
import { itemSpecs, referenceBudgetLowerBound, planSection, checkGroups } from './presentation'
import { parseBudget, clone } from './state'
import reference from '../../tests/fixtures/board/reference-600.json'
const m=vi.hoisted(()=>({api:{recommend:vi.fn(),compare:vi.fn()},router:{push:vi.fn()}}))
vi.mock('../api/board',()=>({boardApi:m.api}))
vi.mock('vue-router',()=>({useRouter:()=>m.router,onBeforeRouteLeave:vi.fn()}))
let w
const button=text=>w.findAll('button').find(b=>b.text()===text)
beforeEach(()=>{vi.resetAllMocks();sessionStorage.clear();localStorage.clear()})
afterEach(()=>w?.unmount())
it('F02 errors clear only on own field edits and revalidate',async()=>{
 w=mount(Demand);const inputs=w.findAll('input');await inputs[0].setValue('0');await inputs[1].setValue('101');await w.find('form').trigger('submit');expect(w.find('#budget-error').text()).toContain('元');expect(w.find('#shoe-error').exists()).toBe(true)
 expect(inputs[0].attributes('aria-describedby')).toContain('budget-error');await inputs[0].setValue('323');expect(w.find('#budget-error').exists()).toBe(false);expect(w.find('#shoe-error').exists()).toBe(true)
 await inputs[1].setValue('');expect(w.find('#shoe-error').exists()).toBe(false);await w.find('form').trigger('submit');expect(w.emitted('submit')[0][0]).toMatchObject({budgetCents:32300,shoeSize:null})
 await inputs[0].setValue('1.001');await w.find('form').trigger('submit');expect(w.find('#budget-error').text()).toContain('两位小数');await inputs[0].setValue('');expect(w.find('#budget-error').exists()).toBe(false);await w.find('form').trigger('submit');expect(w.find('#budget-error').exists()).toBe(true)
})
it.each([['323',32300],['322.99',32299],['100',10000],['0.29',29]])('F02 exact cents %s',(input,cents)=>expect(parseBudget(input)).toBe(cents))
it('F02 shoe is recorded with no inferred width or rank',async()=>{
 w=mount(Demand);await w.findAll('input')[0].setValue('322.99');await w.findAll('input')[1].setValue('42');await w.find('form').trigger('submit');expect(w.emitted('submit')[0][0]).toEqual({budgetCents:32299,purpose:'ACTION',preference:'UNSURE',recommendationType:'BOTH',shoeSize:{value:42,system:'CN'}})
})
it.each([12.7,6.35,3.175,0,null,undefined])('F08 shared specs thickness %s',value=>{
 const item={category:'RISER',variantKey:'SYN-RISER',specs:{heightMm:value}},spec=itemSpecs(item)[0];expect(spec).toMatchObject({label:'厚度（mm）',value:value==null?'未知':String(value)});w=mount(Evaluation,{props:{evaluation:{kind:'ASSEMBLY',items:[item]}}});expect(w.find('dl').text()).toContain('厚度（mm）');expect(w.find('dd').text()).toBe(spec.value)
})
it('F08 hardware precision, false, missing',()=>{
 expect(itemSpecs({category:'HARDWARE',specs:{lengthMm:25.4,nutsIncluded:false}})).toEqual(expect.arrayContaining([{key:'lengthMm',label:'长（mm）',value:'25.4'},{key:'nutsIncluded',label:'含螺母',value:'否'}]));expect(itemSpecs({category:'HARDWARE'})).toContainEqual({key:'lengthMm',label:'长（mm）',value:'未知'})
})
const partial=()=>({kind:'ASSEMBLY',pricingMode:'REFERENCE',totalCents:null,budgetDeltaCents:null,knownSubtotalCents:78620,demand:{budgetCents:60000},missing:['HARDWARE:VALID_QUOTE_REQUIRED'],checks:[]})
it('F09 known 78620/60000 gives 186.20 but leaves total/delta null',()=>{
 const p=partial(),before=clone(p);w=mount(Pricing,{props:{evaluation:p}});expect(w.text()).toContain('按当前参考明细，已知部分至少超预算186.20元');expect(w.text()).toContain('完整总价未知');expect(w.text()).toContain('资料缺口');expect(p).toEqual(before)
})
it.each([78620,80000,undefined,null,-1,1.5,Number.MAX_SAFE_INTEGER+1])('F09 invalid/equal/bigger budget %s never implies excess',budget=>expect(referenceBudgetLowerBound({...partial(),demand:{budgetCents:budget}})).toBeNull())
it('F09 exact prices, totals and invalid cents excluded',()=>{
 for(const p of [{...partial(),pricingMode:'EXACT'},{...partial(),totalCents:90000},{...partial(),knownSubtotalCents:-1},{...partial(),knownSubtotalCents:'78620'}])expect(referenceBudgetLowerBound(p)).toBeNull()
})
const valid=()=>({...partial(),priceComplete:true,totalCents:78620,budgetDeltaCents:-18620,missing:[],validationState:'REFERENCE_ESTIMATE'})
it('D01 strict frozen predicate rejects each malformed, missing, blocked or unknown input',()=>{
 expect(planSection(valid())).toBe('reference')
 for(const change of [{kind:'UNKNOWN'},{priceComplete:false},{priceComplete:undefined},{totalCents:null},{totalCents:1.5},{totalCents:-1},{missing:['gap']},{missing:undefined},{validationState:'INSUFFICIENT_DATA'},{checks:[{state:'INSUFFICIENT_DATA'}]},{checks:[{state:'FUTURE_STATE'}]},{checks:undefined},{pricingMode:'FUTURE_MODE'}])expect(planSection({...valid(),...change})).toBe('pending')
 expect(planSection({...valid(),checks:[{state:'INCOMPATIBLE'}]})).toBe('incompatible');expect(planSection({...valid(),validationState:'INCOMPATIBLE'})).toBe('incompatible');expect(planSection({...valid(),pricingMode:'EXACT',validationState:'VERIFIED'})).toBe('exact')
})
it('D01 recorded response has empty complete assembly, all pending retained with sources and lower bound',async()=>{
 m.api.recommend.mockResolvedValue(clone(reference.ACTION.response));w=mount(Assistant,{global:{stubs:{RouterLink:true}}});w.findComponent(Demand).vm.$emit('submit',reference.ACTION.request);await flushPromises();await button('配件组装（3）').trigger('click');expect(w.find('[data-section="reference"]').exists()).toBe(false);expect(w.find('[data-section="pending"]').findAll('.board-actions')).toHaveLength(3);expect(w.text()).toContain('186.20');expect(w.text()).toContain('完整总价未知')
})
it('D01 full/pending/incompatible groups preserve identities; budget overflow stays explicit',async()=>{
 const full=valid(),pending={...partial(),validationState:'INSUFFICIENT_DATA'},bad={...valid(),validationState:'INCOMPATIBLE'};[full,pending,bad].forEach((p,i)=>{p.selection={kind:'ASSEMBLY',templateKey:`SYN-${i}`,items:[]};p.items=[]})
 m.api.recommend.mockResolvedValue({assembly:{plans:[full,pending,bad],state:'DATA_INSUFFICIENT'},complete:{plans:[],state:'NOT_REQUESTED'}});w=mount(Assistant,{global:{stubs:{RouterLink:true}}});w.findComponent(Demand).vm.$emit('submit',{budgetCents:60000,recommendationType:'ASSEMBLY'});await flushPromises();expect(w.find('[data-section="reference"]').text()).toContain('超出预算');expect(w.find('[data-section="pending"]').text()).toContain('完整总价未知');expect(w.find('[data-section="incompatible"]').text()).toContain('明确不兼容')
})
it('F08 comparison uses same formatter with 3.175/12.7/0/unknown',async()=>{
 const items=[3.175,12.7,0,null].map((n,i)=>({category:'RISER',variantKey:`SYN-${i}`,specs:{heightMm:n}})),plan={...valid(),selection:{kind:'ASSEMBLY',items:[]},items};m.api.recommend.mockResolvedValue({assembly:{plans:[plan]},complete:{plans:[]}});m.api.compare.mockResolvedValue({evaluations:[plan]});w=mount(Assistant,{global:{stubs:{RouterLink:true}}});w.findComponent(Demand).vm.$emit('submit',{budgetCents:60000,recommendationType:'ASSEMBLY'});await flushPromises();await button('加入对比').trigger('click');await button('重新计算并对比').trigger('click');await flushPromises();expect(w.find('table').text()).toContain('厚度（mm） 3.175');expect(w.find('table').text()).toContain('厚度（mm） 0');expect(w.find('table').text()).toContain('厚度（mm） 未知')
})
it('UX01 grouping keeps all differing signatures and original checks, blocker outside details',()=>{
 const checks=[{type:'A',ruleKey:'r1',ruleVersion:1,state:'INSUFFICIENT_DATA',scope:'a',explanation:'同文说明',sources:[{url:'https://example.invalid/a'}]},{type:'B',ruleKey:'r2',ruleVersion:2,state:'INSUFFICIENT_DATA',scope:'b',explanation:'同文说明',sources:[{url:'https://example.invalid/b'}]},{type:'A',ruleKey:'r1',ruleVersion:3,state:'PASS',scope:'a',explanation:'同文说明'}],before=clone(checks);expect(checkGroups(checks).flatMap(g=>g.checks)).toEqual(checks);w=mount(Checks,{props:{checks}});expect(w.findAll('article')).toHaveLength(3);expect(w.findAll('a').map(a=>a.attributes('href'))).toEqual(['https://example.invalid/a','https://example.invalid/b']);expect(w.find('.board-error').text()).toContain('同文说明');expect(w.text()).not.toContain('版本 3');expect(w.text()).not.toContain('r1');expect(w.findAll('summary')).toHaveLength(2);expect(checks).toEqual(before)
})

it('UX01 safety advice remains visible outside collapsed evidence',()=>{
 w=mount(Checks,{props:{checks:[{state:'ADVICE',type:'USAGE_ADVICE',explanation:'佩戴护具并控制速度'}]}});expect(w.find('.board-notice').text()).toContain('佩戴护具并控制速度');expect(w.find('.board-notice').element.tagName).toBe('SUMMARY')
})
