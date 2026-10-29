<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { boardApi } from '../../api/board'
import { publicLimitation, publicAssumption, factNatureLabel, itemSpecs, specValue, specsLabels, temporalLabel, quoteUseLabel } from '../../board/presentation'
import { publicSourceTitle, money, optionLabel, safeSource, sourceDate } from '../../board/state'
const props = defineProps({ item: { type:Object, required:true }, lookup: { type:Boolean, default:true } })
const current=ref(null), loading=ref(false), error=ref('')
let sequence=0
watch(() => props.item, () => { sequence++;current.value=null;error.value='';loading.value=false })
onBeforeUnmount(() => { sequence++ })
async function loadCurrent() {
  if (loading.value) return
  const id=++sequence,key=props.item.variantKey;loading.value=true;error.value='';current.value=null
  try { const data=await boardApi.variant(key); if(id!==sequence)return; if(data?.variant?.key!==key || !Array.isArray(data.displayQuotes))throw new Error('资料格式不可用');current.value={...data.variant,category:props.item.category,fieldEvidence:[...(data.product?.fieldEvidence || []),...(data.variant.fieldEvidence || [])],displayQuotes:data.displayQuotes,sources:data.variant.sources || data.sources || []} }
  catch { if(id===sequence)error.value='当前来源资料暂不可用，请重试；原方案记录仍保留。' }
  finally { if(id===sequence)loading.value=false }
}
const evidence = computed(() => props.item.fieldEvidence || [])
const fieldLabels = { ...specsLabels, suggestedDeckRangeInch:'建议板宽范围（inch）', pageDefaultHeightMm:'页面默认型号高度（mm）', retailUnit:'该销售来源单位', boltsPerPack:'每包螺栓数', nutsPerPack:'每包螺母数', nutProfile:'螺母形态', padsPerSet:'每套垫片数', graphic:'图案', graphicName:'图案', headType:'头型' }
function scope(e) {
  const a=e.appliesTo || {}
  return [a.model, a.sizeCode && `型号 ${a.sizeCode}`, a.pageDefaultAxleWidthInch != null && `页面默认轴宽 ${a.pageDefaultAxleWidthInch} inch`, a.market === 'OVERSEAS' && '海外销售来源', a.notAppliedToVariantKey === (props.item.variantKey || props.item.key) && '不适用于当前所选规格', !a.model && !a.sizeCode && !a.pageDefaultAxleWidthInch && (a.variantKey ? '本规格' : a.productKey ? '本产品系列' : '以原来源所列范围为准')].filter(Boolean).join(' · ')
}
function valueText(v) { return Array.isArray(v) ? v.map(specValue).join('–') : v === 'one truck' ? '单只桥' : v && typeof v==='object' ? '详见对应来源' : specValue(v) }
</script>
<template>
  <dl class="board-specs"><template v-for="spec in itemSpecs(item)" :key="spec.key"><dt>{{ spec.label }}</dt><dd>{{ spec.value }}</dd></template></dl>
  <p v-if="item.historicalOnly">此为旧规格记录，仅供历史查阅。原记录包含多个图案或未定规格，请从替换候选明确选择新的图案或规格后重算。</p>
  <p v-if="item.identity?.graphicName || item.identity?.graphic">图案：{{ item.identity.graphicName || item.identity.graphic }}。仅为原采集身份，不表示厂家SKU已核验。</p>
  <p v-if="item.compatibilityAdvice?.suggestedDeckMinInch != null">厂家建议板宽 {{ item.compatibilityAdvice.suggestedDeckMinInch }}–{{ item.compatibilityAdvice.suggestedDeckMaxInch ?? '待确认' }} inch。此为建议，不是硬性适配边界；仍需检查实际搭配。</p>
  <dl v-if="item.packaging" class="board-specs"><template v-for="(v,k) in item.packaging" :key="k"><template v-if="fieldLabels[k]"><dt>{{ fieldLabels[k] }}</dt><dd>{{ valueText(v) }}</dd></template></template></dl>
  <p v-if="item.displayQuotes && !item.displayQuotes.length">暂无价格来源，价格未知，不按0元计入。</p>
  <details v-if="item.displayQuotes?.length"><summary>参考与历史价格来源</summary><article v-for="(quote,i) in item.displayQuotes" :key="i"><p>{{ quoteUseLabel(quote) }} · {{ money(quote.amountCents) }} · {{ temporalLabel(quote.referenceTemporalStatus) }}</p><p v-for="(assumption,j) in quote.priceAssumptions || []" :key="j">{{ publicAssumption(assumption) }}</p><a v-for="(source,j) in (quote.sources || []).filter(s=>safeSource(s.url))" :key="j" :href="safeSource(source.url)" target="_blank" rel="noopener noreferrer">{{ publicSourceTitle(source.title, '查看报价来源') }} · {{ sourceDate(source) }}</a></article></details>
  <details v-if="evidence.length"><summary>逐项核对来源与适用范围</summary><article v-for="(e,i) in evidence" :key="i" class="board-source">
    <p>{{ fieldLabels[e.field] || '来源所述资料' }}：{{ valueText(e.value) }} · {{ factNatureLabel(e.factNature) }}</p>
    <p>适用范围：{{ scope(e) }}。本项{{ optionLabel(e.verificationStatus) || '待核实' }}；独立核对时间 {{ e.checkedAt || '未提供' }}。不代表整条记录已核验。</p>
    <a v-if="safeSource(e.url)" :href="safeSource(e.url)" target="_blank" rel="noopener noreferrer">查看本项原来源</a>
    <p v-if="e.factNature === 'PAGE_DEFAULT_ONLY'">页面默认型号的高度、重量和价格不能套用到未核对的其他型号。</p>
    <p v-if="e.factNature === 'RETAILER_PACKAGING_ONLY'">海外销售包装不改变国内报价原有的按对参考假设。</p>
    <ul v-if="e.limitations?.length"><li v-for="(limit,j) in e.limitations" :key="j">{{ publicLimitation(limit) }}</li></ul>
  </article></details>
  <div v-for="(source,i) in [...(item.sources || []), ...(item.quoteSources || [])]" :key="i" class="board-source"><a v-if="safeSource(source.url)" :href="safeSource(source.url)" target="_blank" rel="noopener noreferrer">{{ publicSourceTitle(source.title) }}</a><span v-else>{{ publicSourceTitle(source.title, '未提供来源链接') }}</span><span> · 采集 {{ sourceDate(source) }} · {{ optionLabel(source.verificationStatus) || '待核实' }}</span></div>
  <template v-if="lookup && item.variantKey"><button type="button" :disabled="loading" @click="loadCurrent">{{ loading ? '当前来源资料加载中…' : current ? '重新核对当前来源资料' : '查看当前规格与历史报价' }}</button><p v-if="loading" role="status">正在读取来源资料…</p><p v-if="error" role="alert">{{ error }}</p><details v-if="current" open><summary>当前来源资料（不会改写上方原方案）</summary><BoardItemFacts :item="current" :lookup="false" /></details></template>
</template>
