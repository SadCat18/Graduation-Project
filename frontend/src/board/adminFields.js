const text = (key, label) => ({ key, label, type: 'text' })
const number = (key, label, integer = false) => ({ key, label, type: 'number', integer })
const choice = (key, label, options) => ({ key, label, type: 'choice', options: options.split(' ') })
const bool = (key, label) => ({ key, label, type: 'bool' })
const list = (key, label) => ({ key, label, type: 'list' })
export const verification = choice('verificationStatus', '核实状态（须有证据才能选择 VERIFIED）', 'RECORDED VERIFIED INSUFFICIENT')
export const resources = { products: '产品', variants: '具体规格', quotes: '报价', rules: '搭配规则', templates: '组装模板' }
export const basicFields = {
  products: [text('sourceRecordId','原始记录编号'),choice('category','类别','DECK TRUCK WHEEL BEARING GRIP HARDWARE RISER COMPLETE'),text('brand','品牌'),text('model','型号'),text('description','说明'),number('legacyEquipmentId','已匹配旧器材ID（选填）',true),list('suitabilityTags','用途标签（逗号分隔）'),list('levelTags','等级标签（逗号分隔）'),choice('imageLicenseStatus','图片授权状态','UNAUTHORIZED UNKNOWN AUTHORIZED')],
  variants: [text('productKey','所属产品编号'),text('label','具体规格名称'),verification],
  quotes: [text('sourceProductKey','来源产品编号'),text('variantKey','精确规格编号（展示报价可空）'),choice('currency','币种','CNY USD EUR GBP JPY'),choice('priceKind','价格类型','EXACT RANGE FROM UNKNOWN'),choice('market','购买市场','CN OVERSEAS UNKNOWN'),text('purchaseScope','购买费用口径'),number('amountCents','包装商品价（整数分）',true),text('displayPrice','展示原价文字'),number('packageQuantity','每包装件数',true),text('packageUnit','包装单位'),number('shippingCents','本购买项运费（整数分）',true),number('taxCents','本购买项税费（整数分）',true),bool('landedPriceConfirmed','国内费用口径已核实'),text('collectedAt','采集时间（带时区ISO8601）'),text('collectedDate','原始采集日（YYYY-MM-DD）'),text('validUntil','有效截止（带时区ISO8601）'),verification],
  rules: [choice('type','规则类型','DECK_TRUCK_FIT WHEEL_TRUCK_RISER RISER_HARDWARE_LENGTH GRIP_COVERAGE COMPLETENESS USAGE_ADVICE'),choice('severity','规则级别','HARD ADVICE'),text('explanation','规则解释'),verification],
  templates: [choice('purpose','模板用途','ACTION_BEGINNER CRUISE_BEGINNER BALANCED_BEGINNER'),list('requiredCategories','必需类别（逗号分隔）'),list('reasonCodes','理由编号（逗号分隔）'),verification]
}
for (const fields of Object.values(basicFields)) fields.push(choice('pricingMode','计价模式（未填写沿用精确规则）','EXACT REFERENCE'), list('priceAssumptions','估算假设（每行一项）'))
export const sourceFields = [text('url','来源链接'),text('title','来源标题'),text('collectedDate','来源采集日（YYYY-MM-DD）'),text('collectedAt','采集时间（ISO8601）'),verification,text('evidenceRef','可复核证据位置'),text('note','依据与待核实事项')]
export const scopeFields = [list('productKeys','适用产品编号（逗号分隔）'),list('variantKeys','适用规格编号（逗号分隔）'),text('construction','适用桥结构（未知留空）')]
const ends = [bool('minInclusive','包含区间下限'),bool('maxInclusive','包含区间上限')]
export const conditionFields = {
  DECK_TRUCK_FIT: [number('deckMinInch','适用板宽下限（inch）'),number('deckMaxInch','适用板宽上限（inch）'),...ends],
  WHEEL_TRUCK_RISER: [number('wheelMinMm','轮径下限（mm）'),number('wheelMaxMm','轮径上限（mm）'),...ends,choice('riserMode','垫片要求','REQUIRED OPTIONAL NONE'),number('riserMinMm','必需垫片高度下限（mm）')],
  RISER_HARDWARE_LENGTH: [number('riserMinMm','垫片高度下限（mm）'),number('riserMaxMm','垫片高度上限（mm）'),...ends,number('hardwareMinMm','螺丝长度下限（mm）'),number('hardwareMaxMm','螺丝长度上限（mm）')],
  GRIP_COVERAGE: [bool('compareWidth','检查宽度覆盖'),bool('compareLength','检查长度覆盖')],
  COMPLETENESS: [choice('hardwareUnit','固定件单位','BOLT_NUT_PAIR'),choice('riserRequirement','垫片要求依据','SOURCE_DEPENDENT')],
  USAGE_ADVICE: [choice('purpose','用途','ACTION CRUISE BALANCED'),choice('preference','有来源的偏好（选填）','EASY_START COMFORT VALUE UNSURE'),list('variantKeys','适用具体规格'),text('message','事实与建议')]
}
export const quantityFields = ['DECK','TRUCK','WHEEL','BEARING','GRIP','HARDWARE'].map(key => number(key, `${key} 所需件数`, true))
export const templateFields = [choice('kind','方案类型','ASSEMBLY'),list('eligibleVariantKeys','候选规格编号（逗号分隔）'),list('ruleKeys','规则编号（逗号分隔）'),bool('sourceScopePending','适用来源仍待核实')]
export const outcomeFields = [choice('state','声明结果（实际由服务端检查）','PASS INCOMPATIBLE ADVICE INSUFFICIENT_DATA'),text('message','结果说明')]
export const specFields = {
  DECK: [number('widthInch','板宽（inch）'),number('lengthInch','板长（inch）'),number('wheelbaseInch','轴距（inch）')],
  TRUCK: [text('sizeCode','型号编号（不作轴宽比较）'),text('construction','结构'),number('axleWidthInch','实际轴宽（inch）'),number('heightMm','桥高（mm）'),number('deckMinInch','官方板宽下限（inch）'),number('deckMaxInch','官方板宽上限（inch）')],
  WHEEL: [number('diameterMm','直径（mm）'),number('hardnessValue','硬度值'),choice('hardnessScale','硬度体系','A B'),number('widthMm','轮宽（mm）'),number('contactPatchMm','接地面（mm）'),text('shape','轮型')],
  BEARING: [text('standard','轴承标准')], GRIP: [number('widthInch','宽（inch）'),number('lengthInch','长（inch）')],
  HARDWARE: [number('lengthMm','螺丝长（mm）'),text('headType','头型'),bool('nutsIncluded','含螺母')], RISER: [number('heightMm','垫片高度（mm）')], COMPLETE: [number('deckWidthInch','板宽（inch）'),bool('manufacturerComplete','厂家完整整板')]
}
export function recordPayload(record, editing = false) {
  const result = JSON.parse(JSON.stringify(record))
  delete result.catalogVersion; delete result.id; delete result.active; delete result.version
  if(result.pricingMode == null || result.pricingMode === '') delete result.pricingMode
  if(editing) result.expectedVersion = record.version
  return result
}
export function newDraft(resource = 'products') {
  const draft = { key:'', sources:[], notes:'' }
  for(const field of basicFields[resource]) draft[field.key] = field.type === 'list' ? [] : field.type === 'text' ? '' : null
  if(resource === 'products') { draft.legacyEquipmentId = null; draft.imageSource = null; draft.imageLicenseStatus = 'UNKNOWN' }
  if(resource !== 'products') draft.verificationStatus = 'INSUFFICIENT'
  if(resource === 'variants') { draft.specs = {}; draft.rawSpec = {} }
  if(resource === 'quotes') { draft.priceKind = 'UNKNOWN'; draft.market = 'UNKNOWN'; draft.landedPriceConfirmed = false }
  if(resource === 'rules') { draft.scope = {}; draft.conditions = {}; draft.outcome = {} }
  if(resource === 'templates') { draft.selectionConditions = {eligibleVariantKeys:[],ruleKeys:[],sourceScopePending:true} }
  return draft
}
export function withUnknownSpecs(record, category) {
  const result = {...record, specs:{...record.specs}}
  for(const field of specFields[category] || []) if(!(field.key in result.specs)) result.specs[field.key] = null
  return result
}
