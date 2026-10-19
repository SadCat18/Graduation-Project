<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { boardApi } from '../../api/board'
import { getRole } from '../../utils/auth'
import { clone, errorText, labelState, money, optionLabel, reasonLabel, requestGate, safeSource, sourceDate, isReference, referenceNotice } from '../../board/state'
import { basicFields, conditionFields, newDraft, outcomeFields, quantityFields, recordPayload, resources, scopeFields, sourceFields, specFields, templateFields, withUnknownSpecs } from '../../board/adminFields'
import BoardFields from '../../components/board/BoardFields.vue'
import BoardDemandForm from '../../components/board/BoardDemandForm.vue'
import BoardEvaluation from '../../components/board/BoardEvaluation.vue'
import '../../board/board.css'
const gate=requestGate(), resource=ref('products'), page=ref(1), total=ref(0), rows=ref([]), record=ref(null), editing=ref(false), busy=ref(false), error=ref(''), notice=ref(''), trial=ref(null), specCategory=ref(''), activateConfirm=ref(null)
const ruleFields=computed(()=>conditionFields[record.value?.type] || [])
const extraJson=ref(''), jsonError=ref('')
function allowed() { if(getRole() !== 'ADMIN') { error.value='选板数据维护仅限管理员'; return false } return true }
async function load(nextResource=resource.value,nextPage=1) {
  if(!allowed()) return
  const id=gate.next(); busy.value=true; error.value=''; notice.value=''; record.value=null; activateConfirm.value=null; rows.value=[]; resource.value=nextResource; page.value=nextPage
  try { const data=await boardApi.records(nextResource,{page:nextPage,size:10}); if(gate.current(id)) { rows.value=data.list; total.value=data.total } }
  catch(e) { if(gate.current(id)) error.value=errorText(e) }
  finally { if(gate.current(id)) busy.value=false }
}
function edit(row=null) {
  gate.invalidate(); busy.value=false; error.value=''; notice.value=''; editing.value=!!row; activateConfirm.value=null
  record.value=row ? clone(row) : newDraft(resource.value)
  if(resource.value === 'variants') {
    record.value.specs ||= {}; record.value.rawSpec ||= {}
    specCategory.value = Object.keys(specFields).find(category=>specFields[category].every(field=>field.key in record.value.specs)) || ''
  }
  if(resource.value === 'rules') { record.value.scope ||= {}; record.value.conditions ||= {}; record.value.outcome ||= {} }
  if(resource.value === 'templates') record.value.selectionConditions ||= {}
  extraJson.value=JSON.stringify(recordPayload(record.value,editing.value),null,2); jsonError.value=''
}
function applyExtra() {
  try {
    const parsed=JSON.parse(extraJson.value)
    if(!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('须为对象')
    record.value={...record.value,...parsed}; jsonError.value=''; notice.value='已应用高级字段，请检查表单后保存。'
  } catch(e) { jsonError.value=`高级字段格式错误：${e.message}` }
}
async function save() {
  if(busy.value || !allowed()) return
  const id=gate.next(); busy.value=true; error.value=''; notice.value=''
  try {
    if(resource.value === 'variants' && !specCategory.value) throw new Error('请按所属产品选择具体规格类别，不能猜测类别')
    const payload=recordPayload(resource.value === 'variants' ? withUnknownSpecs(record.value,specCategory.value) : record.value,editing.value)
    const data=editing.value ? await boardApi.updateRecord(resource.value,record.value.key,payload) : await boardApi.createRecord(resource.value,payload)
    if(!gate.current(id)) return
    if(!data?.key) throw new Error('未返回记录凭据，无法确认保存成功')
    edit(data); notice.value='已保存记录。启用需另行确认并通过服务端所选计价模式的来源、规格、报价和规则门槛。'
  } catch(e) { if(gate.current(id)) { error.value=errorText(e); if(e.errorCode === 'VERSION_CONFLICT') notice.value=`当前版本 ${e.data.currentVersion}，请重新加载。你的未保存编辑仍保留。` } }
  finally { if(gate.current(id)) busy.value=false }
}
async function activate() {
  if(!activateConfirm.value || busy.value || !allowed()) return
  const row=activateConfirm.value, id=gate.next(); busy.value=true; error.value=''; notice.value=''
  try {
    const data=await boardApi.activate(resource.value,row.key,{active:!row.active,expectedVersion:row.version})
    if(!gate.current(id)) return
    if(!data?.key) throw new Error('未返回记录凭据，不能确认启停成功')
    rows.value=rows.value.map(r=>r.key === data.key ? data:r); activateConfirm.value=null; notice.value=data.active ? '记录已启用' : '记录已停用'
  } catch(e) { if(gate.current(id)) error.value=errorText(e) }
  finally { if(gate.current(id)) busy.value=false }
}
async function runTrial(demand) {
  if(!allowed()) return
  const id=gate.next(); busy.value=true; error.value=''; trial.value=null
  try { const data=await boardApi.trial(demand); if(gate.current(id)) trial.value=data }
  catch(e) { if(gate.current(id)) error.value=errorText(e) }
  finally { if(gate.current(id)) busy.value=false }
}
function addSource() { record.value.sources ||= []; record.value.sources.push({url:'',title:'',collectedAt:null,verificationStatus:'RECORDED',evidenceRef:'',note:''}) }
onMounted(()=>load())
onBeforeUnmount(()=>gate.invalidate())
</script>
<template>
  <div class="board-page"><header><h2>选板数据维护</h2><p>先维护草稿，再核对来源和有效范围，最后单独启用。未知数值留空，不用默认阈值或假来源补齐。参考模式可使用已记录资料，精确模式仍需原核实门槛；启用由服务端按模式校验。</p></header>
    <p v-if="busy" role="status">处理中…</p><p v-if="error" role="alert" class="board-error">{{ error }}</p><p v-if="notice" role="status" class="board-notice">{{ notice }}</p>
    <div class="board-tabs" role="tablist" aria-label="选板资源"><button v-for="(title,key) in resources" :key="key" role="tab" :aria-selected="resource === key" :disabled="busy" @click="load(key)">{{ title }}</button></div>
    <section v-if="!record" class="card"><div class="board-actions"><h3>{{ resources[resource] }}目录</h3><button :disabled="busy" @click="edit()">新增{{ resources[resource] }}草稿</button><button :disabled="busy" @click="load(resource,page)">刷新目录</button></div><p v-if="!busy && !rows.length">暂无记录。</p><article v-for="row in rows" :key="row.key" class="board-item"><h4>{{ row.model || row.label || row.explanation || row.key }}</h4><p>{{ row.key }} · 版本 {{ row.version }} · {{ row.active ? '已启用' : '草稿 / 停用' }} · {{ optionLabel(row.verificationStatus) || '按来源核对' }} · {{ isReference(row) ? '参考估价' : '精确计价规则' }}</p><p v-if="resource === 'quotes'">{{ row.displayPrice || money(row.amountCents) }} · 包装 {{ row.packageQuantity ?? '未知' }} {{ row.packageUnit }} · 有效至 {{ row.validUntil || '未知' }}</p><div v-for="(source,i) in row.sources || []" :key="i" class="board-source"><a v-if="safeSource(source.url)" :href="safeSource(source.url)" target="_blank" rel="noopener noreferrer">{{ source.title || '来源' }}</a><span> · {{ sourceDate(source) }} · {{ source.verificationStatus }} · {{ source.evidenceRef }}</span></div><p v-if="isReference(row)" class="muted">{{ referenceNotice }}</p><ul v-if="row.priceAssumptions?.length"><li v-for="(assumption,i) in row.priceAssumptions" :key="i">{{ assumption }}</li></ul><button :disabled="busy" @click="edit(row)">编辑{{ row.key }}</button><button :disabled="busy" @click="activateConfirm = row">{{ row.active ? '停用' : '启用' }}{{ row.key }}</button></article>
      <div v-if="activateConfirm" class="board-notice"><p>确认{{ activateConfirm.active ? '停用' : '启用' }} {{ activateConfirm.key }}？服务端将检查版本与依赖，不自动级联修改。</p><button :disabled="busy" @click="activate">确认启停</button><button :disabled="busy" @click="activateConfirm = null">取消启停</button></div>
      <div class="board-actions"><button :disabled="busy || page === 1" @click="load(resource,page-1)">上一页资源</button><span>第{{ page }}页 / {{ total }}条</span><button :disabled="busy || page*10 >= total" @click="load(resource,page+1)">下一页资源</button></div>
    </section>
    <form v-else class="card board-editor" @submit.prevent="save"><h3>{{ editing ? '编辑' : '新增草稿' }} · {{ resources[resource] }}</h3><label>稳定编号<input v-model="record.key" :readonly="editing" required /></label><p v-if="editing">基于版本 {{ record.version }} 编辑；启用状态另行维护。</p><BoardFields v-model="record" :fields="basicFields[resource]" />
      <fieldset v-if="resource === 'variants'"><legend>具体规格</legend><label>规格类别（按所属产品核对）<select v-model="specCategory" aria-label="规格类别（按所属产品核对）"><option value="">请按所属产品选择</option><option v-for="(_,key) in specFields" :key="key" :value="key">{{ optionLabel(key) }}</option></select></label><BoardFields v-if="specCategory" v-model="record.specs" :fields="specFields[specCategory]" /></fieldset>
      <template v-if="resource === 'rules'"><fieldset><legend>适用范围（同类OR、跨类AND）</legend><BoardFields v-model="record.scope" :fields="scopeFields" /></fieldset><fieldset><legend>有来源的规则条件</legend><p>不得自行编造范围；切换类型不会静默删除原字段，须核对高级字段和服务端校验。</p><BoardFields v-model="record.conditions" :fields="ruleFields" /><BoardFields v-if="record.type === 'COMPLETENESS'" :model-value="record.conditions.requiredQuantities || {}" :fields="quantityFields" @update:model-value="record.conditions.requiredQuantities = $event" /></fieldset><fieldset><legend>结果说明（不代替实际执行）</legend><BoardFields v-model="record.outcome" :fields="outcomeFields" /></fieldset></template>
      <fieldset v-if="resource === 'templates'"><legend>模板候选与依赖规则</legend><BoardFields v-model="record.selectionConditions" :fields="templateFields" /></fieldset>
      <fieldset><legend>来源与核实证据</legend><article v-for="(source,i) in record.sources || []" :key="i" class="board-item"><BoardFields :model-value="source" :fields="sourceFields" @update:model-value="record.sources[i] = $event" /><button type="button" :disabled="busy" @click="record.sources.splice(i,1)">移除来源 {{ i+1 }}</button></article><button type="button" :disabled="busy" @click="addSource">添加来源</button></fieldset>
      <label>维护备注<textarea v-model="record.notes" /></label><details><summary>高级字段（原始规格、原价与扩展字段保留）</summary><p>常规维护使用上方结构化字段。高级对象修改需主动应用；保存会保留未修改字段。</p><button type="button" @click="extraJson = JSON.stringify(recordPayload(record,editing),null,2)">同步当前表单到高级字段</button><label>高级对象<textarea v-model="extraJson" rows="12" /></label><button type="button" @click="applyExtra">应用高级字段</button><p v-if="jsonError" role="alert">{{ jsonError }}</p></details>
      <div class="board-actions"><button class="btn-primary" :disabled="busy">保存{{ editing ? '编辑' : '草稿' }}</button><button type="button" :disabled="busy" @click="load(resource,page)">返回目录（放弃未保存修改）</button></div>
    </form>
    <section class="card"><h3>推荐试算与筛除原因</h3><BoardDemandForm :busy="busy" action="运行试算" @submit="runTrial" /><template v-if="trial"><p v-if="trial.searchTruncated">搜索已截断，最低价格仅代表本次搜索范围。</p><section v-for="key in ['complete','assembly']" :key="key" class="board-item"><h4>{{ key === 'complete' ? '成品整板' : '组装' }} · {{ labelState(trial[key]?.state) }}</h4><p v-for="(count,reason) in trial[key]?.excludedCounts || {}" :key="reason">{{ reasonLabel(reason) }}：{{ count }}</p><p v-for="missing in trial[key]?.missing || []" :key="missing">缺口：{{ reasonLabel(missing) }}</p><p v-if="!trial[key]?.plans?.length">没有符合方案。</p><BoardEvaluation v-for="(p,i) in trial[key]?.plans || []" :key="i" :evaluation="p" /><p v-if="trial[key]?.minimumFeasiblePlan">本次搜索内最低{{ isReference(trial[key].minimumFeasiblePlan) ? '参考价' : '价' }} {{ money(trial[key].minimumFeasiblePlan.totalCents) }} · 差额 {{ money(trial[key].shortfallCents) }}</p></section></template></section>
  </div>
</template>
