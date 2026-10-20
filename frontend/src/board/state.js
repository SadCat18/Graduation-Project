export const categoryLabels = { DECK: '板面', TRUCK: '桥', WHEEL: '轮子', BEARING: '轴承', GRIP: '砂纸', HARDWARE: '螺丝与螺母', RISER: '垫片', COMPLETE: '成品整板' }
export const states = { REFERENCE_ESTIMATE: '参考估价', RECORDED_COMPATIBLE: '按资料匹配，待确认', VERIFIED: '符合已知规则', MANUFACTURER_COMPLETE: '厂家成品', INSUFFICIENT_DATA: '资料不足', DATA_INSUFFICIENT: '资料不足', INCOMPATIBLE: '明确不兼容', PASS: '符合已知规则', ADVICE: '使用建议', READY: '找到方案', OK: '找到方案', NO_CANDIDATE: '暂无候选', BUDGET_INSUFFICIENT: '预算不足', NOT_REQUESTED: '未请求此类别', DRAFT: '资料不足草稿' }
export const labelState = value => states[value] || value || '待确认'
export const ruleLabels = { DECK_TRUCK_FIT:'板面与桥适配', WHEEL_TRUCK_RISER:'轮子、桥与垫片', RISER_HARDWARE_LENGTH:'垫片与螺丝长度', GRIP_COVERAGE:'砂纸覆盖', COMPLETENESS:'配件完整性', USAGE_ADVICE:'用途建议' }
const reasonLabels = {
  ...states, ...categoryLabels, ...ruleLabels,
  ACTIVE_TEMPLATE_OR_VERIFIED_DATA_REQUIRED:'缺少已启用模板或已核实器材资料',
  VALID_QUOTE_REQUIRED:'缺少有效且规格、费用及包装明确的报价', MISSING_QUOTE:'缺少报价',
  MISSING_COMPONENT:'缺少必需配件', MISSING_SPEC:'缺少关键规格', MISSING_RULE:'缺少适用规则依据',
  INACTIVE_OR_UNVERIFIED:'已停用或尚未核实', OUTSIDE_TEMPLATE:'不在模板适用范围',
  PRICE_OVERFLOW:'金额超出可计算范围', RULE_CONFLICT:'适用规则相互冲突',
  TEMPLATE_PURPOSE_MISMATCH:'模板用途与当前需求不一致', MANUFACTURER_COMPLETE_FACT_REQUIRED:'缺少厂家完整整板依据',
  SAVED_SELECTION_NO_LONGER_AVAILABLE:'保存时选择当前已不可用', ADJUSTMENT_REQUIRED:'需要连带调整',
  PRICE_CHANGED:'价格已变化', CATALOG_CHANGED:'器材或规则已变化', VERSION_CONFLICT:'记录版本已变化',
  SELECTION_INVALID:'未通过维护校验', BUDGET_EXCEEDED:'超出当前预算', QUOTE_EXPIRED:'报价已过期', COMPATIBILITY_PENDING:'搭配资料待确认', MISSING_PRICE_NOT_ZERO:'缺价项未按零元计入'
}
export function reasonLabel(value, reference = false) {
  const text=String(value || '')
  if(text.includes('SYNTHETIC')) return text // Visible fixture evidence label; never real catalog data.
  if(reference && text.endsWith('VALID_QUOTE_REQUIRED')) return text.split(':').map(part => part === 'VALID_QUOTE_REQUIRED' ? '缺少可用参考报价' : reasonLabels[part] || part).join('：')
  if(reasonLabels[text]) return reasonLabels[text]
  const parts=text.split(':')
  if(parts.length > 1 && parts.some(p=>reasonLabels[p])) return parts.map(p=>reasonLabels[p] || p).join('：')
  return /^[A-Z_]+$/.test(text) ? `待确认说明（${text}）` : text
}
export const optionLabel = value => ({...categoryLabels,...ruleLabels,RECORDED:'已记录，待核实',VERIFIED:'已核实（需证据）',INSUFFICIENT:'资料不足',HARD:'硬性检查',ADVICE:'使用建议',REFERENCE:'参考估价',EXACT:'精确价',RANGE:'区间价',FROM:'起步价',UNKNOWN:'未知',CN:'国内',OVERSEAS:'海外',REQUIRED:'必需',OPTIONAL:'选用',NONE:'来源明确无需',ACTION:'基础动作练习',CRUISE:'日常滑行',BALANCED:'两者兼顾',ACTION_BEGINNER:'基础动作入门',CRUISE_BEGINNER:'日常滑行入门',BALANCED_BEGINNER:'综合入门',ASSEMBLY:'组装',EASY_START:'容易上手',COMFORT:'滑行舒适',VALUE:'价格实惠',UNSURE:'不确定',UNAUTHORIZED:'未获授权',AUTHORIZED:'已获授权',BOLT_NUT_PAIR:'一组螺丝与螺母',SOURCE_DEPENDENT:'按适用来源决定',PASS:'符合已知规则',INCOMPATIBLE:'明确不兼容',INSUFFICIENT_DATA:'资料不足'}[value] || value)
export const money = cents => Number.isSafeInteger(cents) ? `¥${(cents / 100).toFixed(2)}` : '无法完整计价'
export const isReference = evaluation => evaluation?.pricingMode === 'REFERENCE'
export const referenceNotice = '按已采集资料估算，实际价格和库存请通过来源自行确认。'
export const priceLabel = evaluation => evaluation?.totalCents == null ? (isReference(evaluation) ? '已知参考小计' : '已知小计') : (isReference(evaluation) ? '参考估价' : '总价')
export const sourceDate = source => source.collectedAt || source.collectedDate || '未知'
// Contract: callers supply plain JSON request/response data; undefined, dates and cyclic objects are outside this domain.
export const clone = value => JSON.parse(JSON.stringify(value))
export function parseBudget(value) {
  const text = String(value).trim()
  if (!/^\d+(\.\d{1,2})?$/.test(text)) throw new Error('预算请输入正数，最多两位小数')
  const [yuan, fraction = ''] = text.split('.')
  const cents = Number(yuan) * 100 + Number(fraction.padEnd(2, '0'))
  if (!Number.isSafeInteger(cents) || cents <= 0) throw new Error('预算须为有效正整数分')
  return cents
}
// A single sequence for all mutations prevents late recommend/replace/save responses.
export function requestGate() {
  let sequence = 0
  return { next: () => ++sequence, current: id => id === sequence, invalidate: () => ++sequence }
}
export const planIdentity = plan => JSON.stringify(plan.selection)
export function selectionDiff(before, after) {
  const old = new Map((before?.items || []).map(i => [i.category, i.variantKey]))
  const next = new Map((after?.items || []).map(i => [i.category, i.variantKey]))
  return [...new Set([...old.keys(), ...next.keys()])].filter(c => old.get(c) !== next.get(c)).map(category => ({ category, before: old.get(category) || '未选择', after: next.get(category) || '移除' }))
}
export const CONTEXT_KEY = 'board-plan-continuation-v1'
export function storeContinuation(context) {
  // No token, ownerId, total or calculation version is stored across authentication.
  sessionStorage.setItem(CONTEXT_KEY, JSON.stringify({ demand: context.demand, selection: context.selection, name: context.name, note: context.note }))
}
export function readContinuation() {
  try {
    const raw = JSON.parse(sessionStorage.getItem(CONTEXT_KEY))
    return raw?.demand && raw?.selection ? raw : null
  } catch { return null }
}
export function clearContinuation() { sessionStorage.removeItem(CONTEXT_KEY) }
export function safeBoardReturn(value) {
  return value === '/board-assistant' || value === '/profile/board-plans' ? value : ''
}
export function errorText(error) {
  const codes = { PRICE_CHANGED: '价格已变化，请查看新结果并重新确认', CATALOG_CHANGED: '器材或规则已变化，请查看新结果并重新确认', VERSION_CONFLICT: '记录已被更新，请重新加载后编辑', SELECTION_INVALID: '未通过维护或启用校验', INCOMPATIBLE: '当前方案不兼容，不能保存', ADJUSTMENT_REQUIRED: '需要确认连带调整', DATA_INSUFFICIENT: '资料不足' }
  const issues = error?.data?.errors || []
  return [codes[error?.errorCode] || error?.message || '操作未完成', ...issues.map(i => `${i.path || ''} ${i.detail || i.code || ''}`)].join('；')
}
export function safeSource(url) { try { const u = new URL(url); return ['https:', 'http:'].includes(u.protocol) ? u.href : '' } catch { return '' } }
