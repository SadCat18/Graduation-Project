import catalog from '../../../docs/equipment-data/normalized-equipment.reference.v2.json'
import { recordPayload } from './adminFields'
import Checks from '../components/board/BoardChecks.vue'
import { mount, flushPromises } from '@vue/test-utils'
import { expect, it, vi } from 'vitest'
import { categoryMatches } from '../utils/publicCategories'
import { itemName, quoteUseLabel, temporalLabel, referenceBudgetLowerBound } from './presentation'
import { labelState, reasonLabel } from './state'
import Evaluation from '../components/board/BoardEvaluation.vue'
import Facts from '../components/board/BoardItemFacts.vue'
import examples from '../../tests/fixtures/public-remediation-20261023/reference-examples.json'
import categories from '../../tests/fixtures/public-remediation-20261023/category-examples.json'
const metadata=categories['GET /api/public/posts/categories']
const m=vi.hoisted(()=>({variant:vi.fn()}))
vi.mock('../api/board',()=>({boardApi:{variant:m.variant}}))
it('eight canonical topics, strict aliases and organizational tags remain distinct',()=>{
  expect(metadata.filterOptions).toHaveLength(8)
  for(const selected of ['装备','装备讨论'])for(const raw of ['装备','装备讨论'])expect(categoryMatches(raw,selected,metadata)).toBe(true)
  expect(categoryMatches('文化','经验分享',metadata)).toBe(true)
  expect(categoryMatches('拍摄','文化',metadata)).toBe(false)
  expect(categoryMatches('文化','文化',metadata)).toBe(true)
  expect(categoryMatches('尚未知标签','经验分享',metadata)).toBe(true)
  expect(categoryMatches('尚未知标签','文化',metadata)).toBe(false)
})
it('expiry changes lower bound from actual service values; historical snapshot remains immutable',()=>{
  const old=examples.historicalEffectiveBeforeExpiry,current=examples.sameCatalogAfterExpiry,before=JSON.stringify(examples)
  expect(old.knownSubtotalCents).toBe(78620);expect(current.knownSubtotalCents).toBe(67100)
  expect(referenceBudgetLowerBound(old)).toBe(18620);expect(referenceBudgetLowerBound(current)).toBe(7100)
  const w=mount(Evaluation,{props:{evaluation:{...old,historicalCopy:true}}});expect(w.text()).toContain('历史方案复制');expect(w.text()).toContain('786.20');expect(w.text()).toContain('186.20');expect(w.text()).toContain('完整总价未知');w.unmount()
  expect(JSON.stringify(examples)).toBe(before)
})
it('only server eligible quotes count as current; unbounded is not permanent and v1 missing flags remain uncertain',()=>{
  expect(quoteUseLabel({referencePriceEligible:true,referenceTemporalStatus:'UNBOUNDED'})).toBe('当前参考')
  expect(temporalLabel('UNBOUNDED')).toContain('未标到期');expect(temporalLabel('UNBOUNDED')).not.toContain('永久')
  expect(quoteUseLabel({referencePriceEligible:false,referenceTemporalStatus:'CURRENT'})).toContain('不计入')
  expect(quoteUseLabel({amountCents:7650,referenceTemporalStatus:'EXPIRED'})).toContain('历史价格')
  expect(quoteUseLabel({amountCents:7650})).toContain('待确认')
})
it('public names preserve real model/spec identity while hiding implementation slugs and future enums',()=>{
  expect(itemName({label:'Krux K5 Standard / size-8-0-k5-standard'})).toBe('Krux K5 Standard')
  expect(itemName({label:'Original Hardware（Phillips/Allen） / purchase-variant-unresolved'})).toBe('Original Hardware（Phillips/Allen）')
  expect(itemName({label:'Spitfire Formula Four / Classic'})).toBe('Spitfire Formula Four / Classic')
  expect(labelState('OVER_BUDGET')).toBe('超出预算');expect(reasonLabel('OVER_BUDGET')).toBe('超出当前预算');expect(reasonLabel('NEW_INTERNAL_CODE')).not.toContain('NEW_INTERNAL_CODE')
})
it('Krux advice and per-field provenance do not upgrade current size or entire record',()=>{
  const row=examples.v2KruxAdvice.variant,before=JSON.stringify(row),w=mount(Facts,{props:{item:{...row,category:'TRUCK'}}})
  expect(w.text()).toContain('7.75–8.25');expect(w.text()).toContain('不是硬性');expect(w.text()).toContain('不代表整条记录已核验');expect(w.find('dl').text()).toContain('未知');expect(w.findAll('a').some(a=>a.attributes('href')==='https://kruxtrucks.com/size-guide')).toBe(true);expect(w.text()).not.toContain('MANUFACTURER_ADVICE');expect(w.text()).not.toContain('TRUCK-006--');expect(JSON.stringify(row)).toBe(before);w.unmount()
})
it('Allen packaging distinguishes nuts and bolts; Dooks quantity stays unknown',()=>{
  const w=mount(Facts,{props:{item:{...examples.v2Allen1Inch.variant,category:'HARDWARE'}}});expect(w.text()).toContain('每包螺栓数');expect(w.text()).toContain('低头螺母');expect(w.text()).toContain('25.4');w.unmount()
  const d=mount(Facts,{props:{item:{category:'RISER',specs:{heightMm:3.175},packaging:{padsPerSet:null}}}});expect(d.text()).toContain('3.175');expect(d.text()).toContain('每套垫片数未知');d.unmount()
})
it('Phillips length and DBH wheel contact dimensions remain unknown without upgrading whole records',()=>{
  const p=mount(Facts,{props:{item:{...examples.v2PhillipsUnknownLength.variant,category:'HARDWARE'}}});expect(p.text()).toContain('十字');expect(p.find('dl').text()).toContain('未知');expect(p.text()).not.toContain('PHILLIPS');p.unmount()
  const wheel=mount(Facts,{props:{item:{...examples.v2DbhUnknownWheelWidth.variant,category:'WHEEL'}}});expect(wheel.text()).toContain('52');expect(wheel.find('dl').text()).toContain('接地面（mm）未知');expect(wheel.find('dl').text()).toContain('宽（mm）未知');wheel.unmount()
})
it('v1 facts render without optional metadata and still keep missing size unknown',()=>{
  const w=mount(Facts,{props:{item:{category:'WHEEL',specs:{diameterMm:52,widthMm:null},sources:[{title:'原记录',url:'https://example.invalid',collectedDate:'2026-10-09'}]}}});expect(w.text()).toContain('52');expect(w.text()).toContain('未知');expect(w.text()).toContain('2026-10-09');expect(w.text()).not.toContain('已核实');w.unmount()
})
it('source lookup is explicit, retryable and separate from original historical amounts',async()=>{
  m.variant.mockReset();m.variant.mockRejectedValueOnce(new Error('fixture failure')).mockResolvedValueOnce(examples.endedBearingQuote)
  const w=mount(Facts,{props:{item:{variantKey:examples.endedBearingQuote.variant.key,category:'BEARING',specs:{},amountCents:7650}}})
  expect(m.variant).not.toHaveBeenCalled();await w.find('button').trigger('click');await flushPromises();expect(w.find('[role="alert"]').text()).toContain('原方案记录仍保留');expect(w.text()).not.toContain('暂无价格来源')
  await w.find('button').trigger('click');await flushPromises();expect(w.text()).toContain('历史价格，不计入当前参考');expect(w.text()).toContain('76.50');expect(w.text()).not.toContain('EXPIRED');expect(w.text()).not.toContain('RECORDED');expect(w.text()).toContain('未独立核验零售包装');expect(w.props('item').amountCents).toBe(7650);expect(m.variant).toHaveBeenCalledTimes(2);w.unmount()
})
it('late source failure/finally cannot erase newer size loading or replace new facts',async()=>{
  let oldReject,newResolve
  m.variant.mockReset().mockImplementationOnce(()=>new Promise((_,r)=>{oldReject=r})).mockImplementationOnce(()=>new Promise(r=>{newResolve=r}))
  const w=mount(Facts,{props:{item:{category:'TRUCK',variantKey:'old'}}});await w.find('button').trigger('click');await w.setProps({item:{category:'TRUCK',variantKey:examples.v2KruxAdvice.variant.key}});await w.find('button').trigger('click');oldReject(new Error('late failure'));await flushPromises();expect(w.text()).toContain('正在读取来源资料');expect(w.find('[role="alert"]').exists()).toBe(false)
  newResolve(examples.v2KruxAdvice);await flushPromises();expect(w.text()).toContain('7.75–8.25');expect(w.text()).not.toContain('late failure');w.unmount()
})

it('reference assumption prose preserves risk and source identity without internal keys',()=>{
  const w=mount(Evaluation,{props:{evaluation:examples.historicalEffectiveBeforeExpiry}})
  expect(w.text()).not.toContain('BRG-008--8-pack');expect(w.text()).not.toContain('RECORDED')
  expect(w.text()).toContain('未独立核验零售包装');expect(w.text()).toContain('不同规格实际售价需自行查找');w.unmount()
})

it('R01 real failure rule text keeps quantity and boundary uncertainty without internal instructions',()=>{
  const rules=catalog.rules.filter(r=>r.explanation?.includes('契约必需件') || r.explanation?.includes('禁止执行为通过结论'))
  expect(rules.length).toBeGreaterThan(1)
  const checks=rules.map(r=>({...r,state:'INSUFFICIENT_DATA'})),before=JSON.stringify(checks)
  const w=mount(Checks,{props:{checks}})
  expect(w.text()).toContain('是否需要垫片及实际搭配是否适用，仍需确认')
  expect(w.text()).toContain('上下限尺寸是否适用、结构及使用范围尚未核实')
  expect(w.text()).toContain('不能据此确认搭配兼容');expect(w.text()).toContain('非厂家适配依据')
  for(const term of ['契约','任务书','端点包含性','禁止执行'])expect(w.text()).not.toContain(term)
  for(const a of w.findAll('a'))expect(checks.flatMap(c=>c.sources||[]).some(s=>{try{return new URL(s.url).href===a.attributes('href')}catch{return false}})).toBe(true)
  expect(JSON.stringify(checks)).toBe(before);w.unmount()
})
it('R01 all four recorded source limitations retain their specific facts and source links',()=>{
  const expectations=[['HTTP403','来源网站拒绝访问','52 mm／100A 仅为原采集记录','轮宽与接地面仍未知'],['Independent Phillips','安全连接失败','产品系列范围','具体规格'],['Set/5 Set Box','未说明每套有几片','三种厚度','不能代替包装数量依据'],['8.0 exact','桥高尚未单独核实','仅适用于该来源','按一对桥估算']]
  for(const [prefix,...phrases] of expectations){
    const item=[...catalog.variants,...catalog.products].find(v=>v.fieldEvidence?.some(e=>e.limitations?.some(l=>l.startsWith(prefix))))
    expect(item).toBeTruthy();const before=JSON.stringify(item),w=mount(Facts,{props:{item}})
    for(const phrase of phrases)expect(w.text()).toContain(phrase)
    expect(w.text()).not.toContain(prefix)
    for(const e of item.fieldEvidence)if(e.url)expect(w.findAll('a').some(a=>a.attributes('href')===e.url)).toBe(true)
    expect(JSON.stringify(item)).toBe(before);w.unmount()
  }
  const w=mount(Facts,{props:{item:{fieldEvidence:[{field:'widthMm',value:null,limitations:['原来源另有特殊使用限制，需逐项确认。']}]}}})
  expect(w.text()).toContain('原来源另有特殊使用限制，需逐项确认。');w.unmount()
})
it('R02 admin note edits preserve actual v2 extensions, unknown length and optimistic version',()=>{
  const original=catalog.variants.find(v=>v.key==='HW-001--phillips-length-unresolved'),before=JSON.stringify(original)
  const payload=recordPayload({...original,notes:'维护备注回归'},true)
  expect(payload.fieldEvidence).toEqual(original.fieldEvidence);expect(payload.identity).toEqual(original.identity)
  expect(payload.specs.lengthMm).toBeNull();expect(payload.expectedVersion).toBe(original.version);expect(payload.notes).toBe('维护备注回归')
  for(const row of [...catalog.products,...catalog.variants,...catalog.quotes]){
    const sent=recordPayload({...row,notes:'仅修改备注'},true)
    for(const key of ['fieldEvidence','identity','packaging','compatibilityAdvice','historicalOnly','replacements','promotion'])if(key in row)expect(sent[key]).toEqual(row[key])
  }
  expect(JSON.stringify(original)).toBe(before)
})
