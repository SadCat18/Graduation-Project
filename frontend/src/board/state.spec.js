import { beforeEach, describe, expect, it } from 'vitest'
import { clone, CONTEXT_KEY, errorText, money, parseBudget, readContinuation, reasonLabel, requestGate, safeBoardReturn, safeSource, selectionDiff, storeContinuation } from './state'
import { recordPayload } from './adminFields'
describe('board trust boundaries', () => {
  beforeEach(()=>sessionStorage.clear())
  it('uses exact integer cents and rejects malformed or overflowing budgets',()=>{
    expect(parseBudget('0.29')).toBe(29); expect(parseBudget('100.01')).toBe(10001)
    for(const bad of ['0','-1','1.001','1e3','NaN','9007199254740992']) expect(()=>parseBudget(bad)).toThrow()
    expect(money(null)).toBe('无法完整计价')
  })
  it('accepts only two exact internal login return routes',()=>{
    for(const bad of ['https://evil.invalid','//evil.invalid','/board-assistant?redirect=evil','/profile','/board-assistant/../admin',['/board-assistant']]) expect(safeBoardReturn(bad)).toBe('')
    expect(safeBoardReturn('/board-assistant')).toBe('/board-assistant')
  })
  it('continuation retains selection and text but never a stale token or total',()=>{
    const context={ demand:{budgetCents:20},selection:{items:[]},name:'n',note:'m',token:'stale',expectedCalculationVersion:'old',totalCents:1,ownerId:9 }
    storeContinuation(context); expect(readContinuation()).toEqual({demand:context.demand,selection:context.selection,name:'n',note:'m'})
    sessionStorage.setItem(CONTEXT_KEY,'broken'); expect(readContinuation()).toBeNull()
  })
  it('late requests and responses after returning are invalidated',()=>{
    const gate=requestGate(), first=gate.next(), second=gate.next()
    expect(gate.current(first)).toBe(false); expect(gate.current(second)).toBe(true); gate.invalidate(); expect(gate.current(second)).toBe(false)
  })
  it('shows additions, removals and changed items without mutating selections',()=>{
    const first={items:[{category:'WHEEL',variantKey:'a'},{category:'GRIP',variantKey:'g'}]}, original=clone(first)
    expect(selectionDiff(first,{items:[{category:'WHEEL',variantKey:'b'},{category:'RISER',variantKey:'r'}]})).toHaveLength(3)
    expect(first).toEqual(original)
  })
  it('draft patch carries expected version and preserves nested unknown/raw fields',()=>{
    const row={key:'q',version:3,active:true,catalogVersion:'current',rawPrice:{unknown:1},sources:[{unknown:2}],amountCents:null}
    expect(recordPayload(row,true)).toEqual({key:'q',expectedVersion:3,rawPrice:{unknown:1},sources:[{unknown:2}],amountCents:null})
    expect(row.active).toBe(true)
  })
  it('preserves gate issue detail and excludes executable source URLs',()=>{
    expect(errorText({errorCode:'SELECTION_INVALID',data:{errors:[{path:'quotes.validUntil',detail:'有效期缺失'}]}})).toContain('有效期缺失')
    expect(safeSource('javascript:alert(1)')).toBe(''); expect(safeSource('https://example.invalid')).toBeTruthy()
  })
  it('maps known structured gaps without inventing meaning for unknown reasons',()=>{
    expect(reasonLabel('TRUCK:VALID_QUOTE_REQUIRED')).toContain('桥：缺少有效')
    expect(reasonLabel('ACTIVE_TEMPLATE_OR_VERIFIED_DATA_REQUIRED')).toBe('缺少已启用模板或已核实器材资料')
    expect(reasonLabel('NEW_FUTURE_CODE')).toBe('相关资料待确认')
    expect(reasonLabel('SYNTHETIC_ONLY')).toBe('SYNTHETIC_ONLY')
  })
})
