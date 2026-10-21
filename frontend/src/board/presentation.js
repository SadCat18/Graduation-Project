import { isReference } from './state'
export const specsLabels = { widthInch: '宽（inch）', lengthInch: '长（inch）', wheelbaseInch: '轴距（inch）', axleWidthInch: '轴宽（inch）', heightMm: '高（mm）', diameterMm: '直径（mm）', hardnessValue: '硬度数值', hardnessScale: '硬度体系', widthMm: '宽（mm）', contactPatchMm: '接地面（mm）', lengthMm: '长（mm）', sizeCode: '型号', construction: '结构', deckMinInch: '适用板宽下限（inch）', deckMaxInch: '适用板宽上限（inch）', standard: '标准', nutsIncluded: '含螺母', headType: '头型', shape: '形状', deckWidthInch: '板宽（inch）', manufacturerComplete: '厂家整板' }
export function itemSpecs(item) {
  const specs = { ...(item.specs || {}) }
  if (item.category === 'RISER' && !Object.hasOwn(specs, 'heightMm')) specs.heightMm = null
  if (item.category === 'HARDWARE' && !Object.hasOwn(specs, 'lengthMm')) specs.lengthMm = null
  return Object.entries(specs).map(([key, value]) => ({ key, label: item.category === 'RISER' && key === 'heightMm' ? '厚度（mm）' : specsLabels[key] || key, value: value == null ? '未知' : String(value) }))
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
