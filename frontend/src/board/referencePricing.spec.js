import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Assistant from '../views/BoardAssistantPage.vue'
import Plans from '../views/BoardPlansPage.vue'
import Panel from '../views/admin/BoardAssistantPanel.vue'
import Evaluation from '../components/board/BoardEvaluation.vue'
import Demand from '../components/board/BoardDemandForm.vue'
import { basicFields, newDraft, recordPayload } from './adminFields'
import { clone, isReference, labelState, priceLabel, referenceNotice } from './state'
import examples from '../../tests/fixtures/board/reference-600.json'
import snapshots from '../../tests/fixtures/board/reference-snapshots.json'
const { api } = vi.hoisted(() => ({ api: Object.fromEntries(['recommend','compare','evaluate','replacements','save','plans','plan','records','createRecord','updateRecord','activate','trial'].map(k => [k,vi.fn()])) }))
vi.mock('../api/board', () => ({ boardApi: api }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }), onBeforeRouteLeave: vi.fn() }))
let wrapper
const button = text => wrapper.findAll('button').find(b => b.text() === text)
const mountPage = Page => mount(Page, { global: { stubs: { RouterLink: true } } })
const response = () => clone(examples.ACTION.response)
async function recommend() {
  wrapper.findComponent(Demand).vm.$emit('submit',examples.ACTION.request)
  await flushPromises()
}
describe('REFERENCE-01 frontend mocks replay recorded H2 shapes; no live HTTP/catalog acceptance', () => {
  beforeEach(() => { vi.resetAllMocks(); localStorage.clear(); sessionStorage.clear(); api.recommend.mockResolvedValue(response()) })
  afterEach(() => wrapper?.unmount())
  it('shows three recorded complete references with notice, assumptions, source/date and partial nonempty bucket', async () => {
    wrapper = mountPage(Assistant); await recommend()
    for (const amount of ['580.00','323.00','446.00']) expect(wrapper.text()).toContain(`参考估价 ¥${amount}`)
    expect(wrapper.text()).not.toContain('¥720.00'); expect(wrapper.text()).toContain(referenceNotice)
    expect(wrapper.text()).toContain('采集 2026-10-09'); expect(wrapper.text()).toContain('内部配件与包装未独立核验')
    expect(wrapper.find('a[href="https://www.dobyheart.com/"]').exists()).toBe(true)
    expect(api.recommend).toHaveBeenCalledWith(examples.ACTION.request)
    await button('配件组装（3）').trigger('click')
    expect(wrapper.text()).toContain('已知参考小计 ¥786.20'); expect(wrapper.text()).toContain('螺丝与螺母')
    expect(wrapper.text()).toContain('资料不足'); expect(wrapper.text()).not.toContain('暂无可推荐方案')
    await button('查看方案 1').trigger('click')
    expect(wrapper.text()).toContain('仅保存为草稿'); expect(wrapper.text()).not.toContain('参考预算剩余')
  })
  it('renders explicit synthetic 100-yuan budget control using recorded cheapest price, without automatically raising budget', async () => {
    const data = response(), minimum = data.complete.plans.find(p => p.totalCents === 32300)
    minimum.demand.budgetCents = 10000; minimum.budgetDeltaCents = -22300
    data.complete = { state:'BUDGET_INSUFFICIENT',plans:[],minimumFeasiblePlan:minimum,shortfallCents:22300 }
    api.recommend.mockResolvedValue(data); wrapper = mountPage(Assistant)
    wrapper.findComponent(Demand).vm.$emit('submit',{...examples.ACTION.request,budgetCents:10000}); await flushPromises()
    expect(wrapper.text()).toContain('参考估价 ¥323.00'); expect(wrapper.text()).toContain('参考预算差额 ¥223.00')
    expect(wrapper.text()).toContain('不会自动提高预算')
  })
  it('keeps unknown fees unpriced; synthetic recorded-compatible check is shown as pending', () => {
    const partial = response().assembly.plans[0]
    // Positive display control only: recorded original partial checks remain insufficient.
    partial.checks.push({state:'RECORDED_COMPATIBLE',type:'DECK_TRUCK_FIT',explanation:'SYNTHETIC display control'})
    wrapper = mount(Evaluation,{props:{evaluation:partial}})
    expect(wrapper.text()).toContain('运费 未知，未计入'); expect(wrapper.text()).toContain('税费 未知，未计入')
    expect(wrapper.text()).toContain('购买项参考小计 未计价'); expect(wrapper.text()).not.toContain('¥0.00')
    expect(wrapper.text()).toContain('按资料匹配，待确认'); expect(wrapper.text()).toContain('采集 2026-10-09')
  })
  it('compares reference total against partial subtotal and explicitly saves complete reference using current version', async () => {
    localStorage.setItem('skate_token','TEST'); localStorage.setItem('skate_role','USER')
    wrapper = mountPage(Assistant); await recommend(); await button('加入对比').trigger('click')
    await button('配件组装（3）').trigger('click'); await button('加入对比').trigger('click')
    api.compare.mockResolvedValue({evaluations:[response().complete.plans[0],response().assembly.plans[0]]})
    await button('重新计算并对比').trigger('click'); await flushPromises()
    expect(wrapper.find('table').text()).toContain('参考估价 ¥580.00'); expect(wrapper.find('table').text()).toContain('已知参考小计 ¥786.20')
    await button('成品整板（3）').trigger('click'); await button('查看方案 1').trigger('click')
    api.evaluate.mockResolvedValue(response().complete.plans[0]); api.save.mockResolvedValue(snapshots.saveResponse)
    await wrapper.find('form.board-item').trigger('submit'); await flushPromises(); expect(api.save).not.toHaveBeenCalled()
    await button('确认保存当前结果').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('已保存参考估价方案')
    expect(api.save.mock.calls[0][0].expectedCalculationVersion).toBe(response().complete.plans[0].calculationVersion)
    expect(api.save.mock.calls[0][0]).not.toHaveProperty('pricingMode')
  })
  it('replacement candidate shows reference subtotal/assumptions and retains ordinary version check', async () => {
    wrapper = mountPage(Assistant); await recommend(); await button('配件组装（3）').trigger('click'); await button('查看方案 1').trigger('click')
    const candidate = response().assembly.plans[1]
    api.replacements.mockResolvedValue({total:1,list:[candidate]}); api.evaluate.mockResolvedValue(candidate)
    await button('替换轮子').trigger('click'); await flushPromises(); expect(wrapper.text()).toContain(referenceNotice)
    await button('检查替换 1').trigger('click'); expect(api.evaluate).not.toHaveBeenCalled()
    await button('确认单项替换并重算').trigger('click'); await flushPromises()
    expect(api.evaluate.mock.calls[0][0].expectedCalculationVersion).toBe(candidate.calculationVersion)
    expect(wrapper.text()).toContain('已知参考小计'); expect(wrapper.text()).toContain('仅保存为草稿')
  })
  it('reference snapshots retain original 580 while synthetic one-cent revision appears only in latest section', async () => {
    localStorage.setItem('skate_token','TEST'); localStorage.setItem('skate_role','USER')
    api.plans.mockResolvedValue({total:1,list:[snapshots.saveResponse]}); api.plan.mockResolvedValue(snapshots.detailAfterSyntheticRevision)
    wrapper = mountPage(Plans); await flushPromises(); expect(wrapper.text()).toContain('参考估价 ¥580.00')
    await button(`查看${snapshots.saveResponse.name}`).trigger('click'); await flushPromises()
    expect(wrapper.get('[data-testid="saved-snapshot"]').text()).toContain('参考估价 ¥580.00')
    expect(wrapper.get('[data-testid="latest-evaluation"]').text()).toContain('参考估价 ¥580.01')
  })
  it('legacy snapshot lacking mode preserves exact semantics without rewriting input', () => {
    const legacy = {kind:'COMPLETE',totalCents:1000,snapshotState:'MANUFACTURER_COMPLETE',items:[],checks:[]}, before = clone(legacy)
    wrapper = mount(Evaluation,{props:{evaluation:legacy}})
    expect(wrapper.text()).toContain('总价 ¥10.00'); expect(wrapper.text()).toContain('厂家成品')
    expect(wrapper.text()).not.toContain(referenceNotice); expect(legacy).toEqual(before)
    expect(isReference(legacy)).toBe(false); expect(priceLabel({...legacy,pricingMode:'EXACT'})).toBe('总价')
    expect(labelState('REFERENCE_ESTIMATE')).toBe('参考估价')
  })
  it('five structured drafts preserve explicit reference assumptions; RECORDED activation uses versioned endpoint without local exact gate', async () => {
    for (const resource of Object.keys(basicFields)) {
      expect(basicFields[resource].map(f=>f.key)).toEqual(expect.arrayContaining(['pricingMode','priceAssumptions']))
      expect(newDraft(resource).pricingMode).toBeNull()
      expect(recordPayload({...newDraft(resource),pricingMode:'REFERENCE',priceAssumptions:['原包装估算'],version:2},true)).toMatchObject({pricingMode:'REFERENCE',priceAssumptions:['原包装估算'],expectedVersion:2})
    }
    localStorage.setItem('skate_role','ADMIN')
    const row={key:'REFERENCE-RECORDED',version:2,active:false,verificationStatus:'RECORDED',pricingMode:'REFERENCE',priceAssumptions:['原包装估算'],sources:[{url:'https://example.invalid',collectedDate:'2026-10-09'}],eligibility:{referencePriceEligible:true,exactPriceEligible:false}}
    api.records.mockResolvedValue({total:1,list:[row]}); api.activate.mockResolvedValue({...row,active:true})
    wrapper = mountPage(Panel); await flushPromises(); expect(wrapper.text()).toContain('参考估价'); expect(wrapper.text()).toContain('2026-10-09')
    await button('启用REFERENCE-RECORDED').trigger('click'); await button('确认启停').trigger('click'); await flushPromises()
    expect(api.activate).toHaveBeenCalledWith('products',row.key,{active:true,expectedVersion:2}); expect(wrapper.text()).toContain('记录已启用')
  })
})
