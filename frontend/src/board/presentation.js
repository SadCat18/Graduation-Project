import { isReference, categoryLabels } from './state'
export function itemName(item) {
  const label = String(item?.label || '').replace(/\s+\/\s+[a-z0-9]+(?:[-_][a-z0-9]+)+[-_]?$/i, '').trim()
  return label && !/^[A-Z0-9-]+--/.test(label) ? label : `${categoryLabels[item?.category] || '配件'}（规格待确认）`
}
export const temporalLabel = status => ({ UNBOUNDED:'资料未标到期，实际价格请向来源确认', CURRENT:'资料在所标参考期间内', EXPIRED:'历史价格，已退出当前参考', BOUNDARY_UNCERTAIN:'结束时间边界待确认，不计入当前参考', UNKNOWN:'时效待确认，不计入当前参考' }[status] || '时效资料未提供')
export const factNatureLabel = value => ({ MANUFACTURER_ADVICE:'厂家建议，非硬性适配边界', PAGE_DEFAULT_ONLY:'仅适用于来源页所选默认型号', FAMILY_FACT:'产品系列通用资料', RETAILER_PACKAGING_ONLY:'仅适用于该销售来源包装', UNVERIFIED:'尚未独立核实', ORIGINAL_RECORD:'原始采集资料' }[value] || '按所列来源及范围理解')
export const quoteUseLabel = quote => quote.referencePriceEligible === true ? '当前参考' : quote.historicalOnly || quote.referenceTemporalStatus === 'EXPIRED' ? '历史价格，不计入当前参考' : '参考资格待确认，不计入当前参考'
export const guideSpecText = value => String(value || '参数资料暂未提供').replace(/\b(widthInch|lengthInch|axleWidthInch|heightMm|diameterMm|lengthMm)\b/g, key => specsLabels[key]).replace(/英寸/g, 'inch')
export const specValue = value => value == null ? '未知' : typeof value === 'boolean' ? (value ? '是' : '否') : ({ STANDARD:'标准结构', ALLEN:'内六角', PHILLIPS:'十字', LOW_PROFILE:'低头螺母', UNKNOWN:'未知' }[value] || String(value))
export const specsLabels = { widthInch: '宽（inch）', lengthInch: '长（inch）', wheelbaseInch: '轴距（inch）', axleWidthInch: '轴宽（inch）', heightMm: '高（mm）', diameterMm: '直径（mm）', hardnessValue: '硬度数值', hardnessScale: '硬度体系', widthMm: '宽（mm）', contactPatchMm: '接地面（mm）', lengthMm: '长（mm）', sizeCode: '型号', construction: '结构', deckMinInch: '适用板宽下限（inch）', deckMaxInch: '适用板宽上限（inch）', standard: '标准', nutsIncluded: '含螺母', headType: '头型', shape: '形状', deckWidthInch: '板宽（inch）', manufacturerComplete: '厂家整板' }
export function itemSpecs(item) {
  const specs = { ...(item.specs || {}) }
  if (item.category === 'RISER' && !Object.hasOwn(specs, 'heightMm')) specs.heightMm = null
  if (item.category === 'HARDWARE' && !Object.hasOwn(specs, 'lengthMm')) specs.lengthMm = null
  return Object.entries(specs).filter(([key]) => specsLabels[key]).map(([key, value]) => ({ key, label: item.category === 'RISER' && key === 'heightMm' ? '厚度（mm）' : specsLabels[key], value: specValue(value) }))
}
const validCents = value => Number.isSafeInteger(value) && value >= 0
export function referenceBudgetLowerBound(evaluation) {
  const budget = evaluation?.demand?.budgetCents, known = evaluation?.knownSubtotalCents
  return isReference(evaluation) && evaluation.totalCents == null && validCents(budget) && validCents(known) && known > budget ? known - budget : null
}
export function planSection(plan) {
  if (!['COMPLETE', 'ASSEMBLY'].includes(plan?.kind)) return 'pending'
  const checks = plan?.checks, missing = plan?.missing
  if (plan?.validationState === 'INCOMPATIBLE' || checks?.some?.(check => check.state === 'INCOMPATIBLE')) return 'incompatible'
  const blocked = !Array.isArray(checks) || checks.some(check => !['PASS', 'ADVICE', 'VERIFIED', 'REFERENCE_ESTIMATE', 'MANUFACTURER_COMPLETE', 'RECORDED_COMPATIBLE'].includes(check.state))
  if (plan?.priceComplete !== true || !validCents(plan?.totalCents) || !Array.isArray(missing) || missing.length || blocked) return 'pending'
  if (isReference(plan)) return plan.kind === 'ASSEMBLY' && plan.validationState === 'REFERENCE_ESTIMATE' ? 'reference' : plan.kind === 'COMPLETE' && ['REFERENCE_ESTIMATE', 'MANUFACTURER_COMPLETE'].includes(plan.validationState) ? 'referenceComplete' : 'pending'
  if (plan.pricingMode != null && plan.pricingMode !== 'EXACT') return 'pending'
  return ['VERIFIED', 'MANUFACTURER_COMPLETE'].includes(plan.validationState) ? 'exact' : 'pending'
}
// Group presentation only; original checks (including every type/key/version/scope/source) stay intact.
export function checkGroups(checks = []) {
  const groups = new Map()
  for (const check of checks) {
    const key = `${check.state || 'UNKNOWN'}:${check.explanation || check.message || ''}`
    if (!groups.has(key)) groups.set(key, { state: check.state, explanation: check.explanation || check.message, checks: [] })
    groups.get(key).checks.push(check)
  }
  return [...groups.values()]
}
export const isBlockingCheck = check => ['INCOMPATIBLE', 'INSUFFICIENT_DATA', 'DATA_INSUFFICIENT'].includes(check.state) || !['PASS', 'ADVICE', 'VERIFIED', 'REFERENCE_ESTIMATE', 'MANUFACTURER_COMPLETE', 'RECORDED_COMPATIBLE'].includes(check.state)

export function publicAssumption(value, items = []) {
  return String(value || '').replace(/^([A-Z0-9]+-[A-Z0-9]+--[^:]+):\s*/, (_, key) => {
    const item = items.find(item => item.variantKey === key)
    return `${item ? itemName(item) : '配件参考说明'}：`
  }).replace(/\bRECORDED\b/g, '已采集记录（未独立核验）')
}

// Translate known source-review notes at the presentation boundary only. Keep unfamiliar facts visible.
const publicLimitations = {
  'HTTP403. Do not label report DBH graphics/52mm100A claims independently verified. Existing52mm/100A remains RECORDED; width/contact patch remain unknown.': '来源网站拒绝访问，暂未独立核实 DBH 图案、52 mm 直径及 100A 硬度；52 mm／100A 仅为原采集记录，轮宽与接地面仍未知。',
  'Independent Phillips URL failed TLS. Allen page mentions Phillips at family scope; do not call the separate Phillips variant page verified.': 'Phillips 独立来源页面因安全连接失败未能核实。Allen 页面只在产品系列范围提及 Phillips，不能据此确认该 Phillips 具体规格。',
  'Set/5 Set Box is not evidence of pads per set. Existing candidate already has all three exact metric thicknesses.': '来源中的“Set／5 Set Box”未说明每套有几片，不能据此确定每套数量；三种厚度已有对应毫米数值记录，厚度信息不能代替包装数量依据。',
  '8.0 exact variant height not separately verified here. Official store unit does not silently overwrite a domestic quote explicitly estimated as a pair.': '8.0 规格的桥高尚未单独核实。官方店的销售单位仅适用于该来源，国内报价仍保留原有按一对桥估算的说明。'
}
export const publicLimitation = value => publicLimitations[String(value || '').trim()] || String(value || '')
