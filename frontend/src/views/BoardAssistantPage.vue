<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { boardApi } from '../api/board'
import { getRole, getToken } from '../utils/auth'
import BoardDemandForm from '../components/board/BoardDemandForm.vue'
import BoardEvaluation from '../components/board/BoardEvaluation.vue'
import { itemName, itemSpecs, planSection } from '../board/presentation'
import BoardChecks from '../components/board/BoardChecks.vue'
import BoardPricing from '../components/board/BoardPricing.vue'
import { categoryLabels, clearContinuation, clone, errorText, labelState, money, isReference, optionLabel, reasonLabel, planIdentity, readContinuation, requestGate, selectionDiff, storeContinuation } from '../board/state'
import '../board/board.css'
import { focusContent } from '../utils/pageFocus'
const router = useRouter()
const gate = requestGate()
const compareGate = requestGate(), comparing = ref(false), saving = ref(false), saveUnknown = ref(sessionStorage.getItem('board-plan-save-unresolved-v1') === 'true')
const demand = ref(null), results = ref(null), selected = ref(null), tab = ref('complete'), busy = ref(false), error = ref(''), notice = ref('')
const comparison = ref([]), compared = ref(null), replacements = ref(null), replacementCategory = ref(''), replacementPage = ref(1), pending = ref(null)
const name = ref('我的选板方案'), note = ref(''), saveReady = ref(false)
const resume = readContinuation()
const resultHeading = ref(null), detailPanel = ref(null)
let sourceIdentity = null
function focusAfterRender(id, target) { nextTick(() => { if (gate.current(id)) focusContent(target()) }) }
function displayDifference(key, category) {
 const candidates=[...(selected.value?.items || []),...(replacements.value?.list || []).flatMap(p=>p.items || [])]
 const item=candidates.find(i=>i.variantKey===key)
 return item ? itemName(item) : key==='移除' || key==='未选择' ? key : `${categoryLabels[category] || '配件'}规格已变更，重算后查看详情`
}
const bucket = computed(() => results.value?.[tab.value])
const bucketLabels = { complete: '成品整板', assembly: '配件组装' }
const sectionLabels = { reference: '完整参考组装（参考估价）', referenceComplete: '成品参考结果（按原资料分类）', exact: '精确价 / 厂家成品结果', pending: '资料待补 / 待确认', incompatible: '明确不兼容（阻断）' }
const planSections = computed(() => Object.entries(sectionLabels).filter(([key]) => key !== (tab.value === 'assembly' ? 'referenceComplete' : 'reference')).map(([key, title]) => ({ key, title, plans: (bucket.value?.plans || []).map((plan, index) => ({ plan, index })).filter(({ plan }) => planSection(plan) === key) })).filter(section => section.plans.length))
const minimumComplete = computed(() => bucket.value?.minimumFeasiblePlan && !['pending', 'incompatible'].includes(planSection(bucket.value.minimumFeasiblePlan)))
const comparisonKeys = computed(() => new Set(comparison.value.map(planIdentity)))
const emptyBucketMessage = computed(() => {
  if (bucket.value?.state === 'BUDGET_INSUFFICIENT') return '当前预算内没有完整可行方案。不会自动提高预算。'
  if (bucket.value?.state === 'NOT_REQUESTED') return '此类别未参与本次推荐，可修改推荐类型。'
  return '暂无可推荐方案。缺价、缺规格或缺规则会影响结果，资料不足不能视为预算不足。'
})
function isCompared(plan) { return comparisonKeys.value.has(planIdentity(plan)) }
function installationLabel(plan) {
  if (plan.kind !== 'COMPLETE') return '组装 / 需自行安装'
  return isReference(plan) ? '成品整板 / 按原资料分类' : '成品整板 / 厂家安装'
}
function itemSpecText(item) { return itemSpecs(item).map(spec => `${spec.label} ${spec.value}`).join(' · ') }
function replacementLabel(plan) {
  return itemName(plan.items?.find(item => item.category === replacementCategory.value))
}
function clearComparison() { compareGate.invalidate(); comparing.value = false; compared.value = null }
function invalidate() { if (saving.value) return; gate.invalidate(); clearComparison(); busy.value = false; pending.value = null; saveReady.value = false }
function warnBeforeUnload(event) { if (saving.value) { event.preventDefault(); event.returnValue = '' } }
function acknowledgeUnknown() {
  if (saving.value) return
  sessionStorage.removeItem('board-plan-save-unresolved-v1'); clearContinuation(); saveUnknown.value = false
  close(); results.value = null; notice.value = '已结束本次未知操作。请重新查找方案；不会自动重试旧保存。'
}
onBeforeRouteLeave(() => { if (saving.value) { notice.value = '保存正在处理中，请等待本次结果后离开，勿重复提交。'; return false } return true })
onBeforeUnmount(() => { window.removeEventListener('beforeunload', warnBeforeUnload); invalidate() })
function context() { return { demand: demand.value, selection: selected.value?.selection, name: name.value, note: note.value } }
function login() {
  try { storeContinuation(context()); router.push({ path: '/login', query: { returnTo: '/board-assistant' } }) }
  catch { error.value = '无法保留当前方案，请允许浏览器会话存储后重试' }
}
function fail(e) {
  error.value = errorText(e)
  if (e.data?.evaluation) { selected.value = e.data.evaluation; pending.value = null; saveReady.value = false; replacements.value = null }
  if (e.status === 401 && selected.value) login()
}
async function recommend(nextDemand) {
  if (saving.value) return
  clearComparison()
  const id = gate.next(); busy.value = true; error.value = ''; notice.value = ''; demand.value = clone(nextDemand)
  selected.value = null; pending.value = null; saveReady.value = false; replacements.value = null; comparison.value = []; compared.value = null; results.value = null
  try { const data = await boardApi.recommend(nextDemand); if (gate.current(id)) { results.value = data; tab.value = nextDemand.recommendationType === 'ASSEMBLY' ? 'assembly' : 'complete'; focusAfterRender(id, () => resultHeading.value) } }
  catch (e) { if (gate.current(id)) fail(e) }
  finally { if (gate.current(id)) busy.value = false }
}
function open(plan) { if (saving.value) return; invalidate(); sourceIdentity = planIdentity(plan); error.value = ''; notice.value = ''; selected.value = clone(plan); replacements.value = null; replacementCategory.value = ''; const id=gate.next(); focusAfterRender(id, () => detailPanel.value?.querySelector('h3')) }
function close() { if (saving.value) return; invalidate(); selected.value = null; replacements.value = null; error.value = ''; notice.value = ''; const id=gate.next(); focusAfterRender(id, () => [...document.querySelectorAll('[data-plan-id]')].find(el=>el.dataset.planId===sourceIdentity)?.querySelector('button') || resultHeading.value) }
function toggleCompare(plan) {
  if (saving.value) return
  const key = planIdentity(plan), index = comparison.value.findIndex(p => planIdentity(p) === key)
  if (index >= 0) comparison.value.splice(index, 1)
  else if (comparison.value.length < 3) comparison.value.push(clone(plan))
  else error.value = '最多比较三套方案，可先移除已选方案'
  clearComparison()
}
async function compare() {
  if (saving.value || comparing.value) return
  const id = compareGate.next(); comparing.value = true; compared.value = null; error.value = ''
  try { const data = await boardApi.compare(comparison.value.map(p => ({ demand: p.demand || demand.value, selection: p.selection }))); if (compareGate.current(id)) compared.value = data.evaluations }
  catch (e) { if (compareGate.current(id)) error.value = errorText(e) }
  finally { if (compareGate.current(id)) comparing.value = false }
}
async function loadReplacements(category, page = 1) {
  if (saving.value) return
  const id = gate.next(); busy.value = true; error.value = ''; pending.value = null; saveReady.value = false; replacements.value = null; replacementCategory.value = category; replacementPage.value = page
  const selection = clone(selected.value.selection)
  try { const data = await boardApi.replacements({ demand: demand.value, selection, category, page, size: 10 }); if (gate.current(id)) replacements.value = data }
  catch (e) { if (gate.current(id)) fail(e) }
  finally { if (gate.current(id)) busy.value = false }
}
function propose(plan) {
  if (saving.value) return
  invalidate(); error.value = ''
  pending.value = { selection: clone(plan.selection), version: plan.calculationVersion, adjustments: plan.proposedAdjustments || [], requiresConfirmation: plan.requiresConfirmation }
}
async function apply(selection = pending.value?.selection, version = pending.value?.version, forSave = false) {
  if (!selection || saving.value) return
  const id = gate.next(); busy.value = true; error.value = ''; notice.value = ''; saveReady.value = false
  try {
    const data = await boardApi.evaluate({ demand: demand.value, selection, ...(version ? { expectedCalculationVersion: version } : {}) })
    if (!gate.current(id)) return
    selected.value = data; replacements.value = null; pending.value = null; comparison.value = []; clearComparison()
    if (data.requiresConfirmation) { pending.value = { selection: clone(data.selection), version: data.calculationVersion, adjustments: data.proposedAdjustments || [], requiresConfirmation: true }; notice.value = '需要连带调整，请检查建议差异；未自动修改其他配件。' }
    else { saveReady.value = forSave && data.validationState !== 'INCOMPATIBLE'; notice.value = forSave ? '已按最新目录重算，请检查下方结果后确认保存。' : '已重新计算整套方案，请检查结果。' }
  } catch (e) { if (gate.current(id)) fail(e) }
  finally { if (gate.current(id)) busy.value = false }
}
async function prepareSave() {
  if (saving.value || saveUnknown.value) return
  if (!getToken()) { login(); return }
  if (getRole() !== 'USER') { error.value = '个人方案仅限用户身份，管理员请使用用户账号登录'; return }
  await apply(selected.value.selection, selected.value.calculationVersion, true)
}
async function save() {
  if (!saveReady.value || busy.value || saving.value || saveUnknown.value) return
  if (!getToken()) { login(); return }
  if (getRole() !== 'USER') { error.value = '个人方案仅限用户身份'; return }
  try { storeContinuation(context()); sessionStorage.setItem('board-plan-save-unresolved-v1', 'true') }
  catch { error.value = '无法保留保存状态，本次未发送保存请求，请允许浏览器会话存储后重试'; return }
  // Preserve this write's result; navigation and local mutations are locked until it settles.
  saving.value = true; busy.value = true; error.value = ''; notice.value = ''; saveReady.value = false
  try {
    const data = await boardApi.save({ name: name.value.trim(), note: note.value, demand: demand.value, selection: selected.value.selection, expectedCalculationVersion: selected.value.calculationVersion })
    if (!data?.key) throw new Error('服务端未返回保存凭据，无法确认保存成功')
    sessionStorage.removeItem('board-plan-save-unresolved-v1'); clearContinuation(); notice.value = `已保存${data.snapshotState === 'DRAFT' ? '资料不足草稿' : data.snapshotState === 'REFERENCE_ESTIMATE' ? '参考估价方案' : '方案'}，可在我的方案查看。`
  } catch (e) {
    if (e.errorCode || [400,401,403,404].includes(e.status)) { sessionStorage.removeItem('board-plan-save-unresolved-v1'); fail(e) }
    else { saveUnknown.value = true; error.value = ''; notice.value = '' }
  } finally { saving.value = false; busy.value = false }
}
onMounted(async () => {
  window.addEventListener('beforeunload', warnBeforeUnload)
  if (!resume) return
  demand.value = resume.demand; name.value = resume.name || '我的选板方案'; note.value = resume.note || ''
  const id = gate.next(); busy.value = true
  try {
    const data = await boardApi.evaluate({ demand: resume.demand, selection: resume.selection })
    if (gate.current(id)) { selected.value = data; notice.value = '已恢复选择并按当前目录重算。请重新检查并确认，尚未保存。' }
  } catch (e) { if (gate.current(id)) fail(e) }
  finally { if (gate.current(id)) busy.value = false }
})
</script>
<template>
  <div class="board-page">
    <header class="board-head">
      <div>
        <p class="muted">
          第一套双翘滑板
        </p>
        <h1>
          选板助手
        </h1>
        <p>
          按预算与有依据的规则，比较成品整板和配件组装。
        </p>
      </div>
      <RouterLink to="/profile/board-plans">
        我的方案
      </RouterLink>
    </header>
    <section class="card">
      <h2>
        你的需求
      </h2>
      <BoardDemandForm :initial="resume?.demand" :busy="busy || saving" @submit="recommend" />
    </section>
    <p v-if="busy || comparing" role="status" aria-live="polite">
      正在读取当前资料并计算…
    </p>
    <p v-if="saving" class="board-notice" role="status">
      保存正在处理中，请等待本次结果后离开，勿重复提交。
    </p>
    <section v-if="saveUnknown" class="board-error">
      <p role="alert">
        本次保存结果未知，可能已经完成。本页已暂停再次保存，请先到我的方案核对记录；不要盲目重试。
      </p>
      <RouterLink to="/profile/board-plans">
        先查看我的方案核对记录
      </RouterLink>
      <button @click="acknowledgeUnknown">
        我已核对记录，结束本次未知操作
      </button>
    </section>
    <p v-if="error" class="board-error" role="alert">
      {{ error }}
    </p>
    <p v-if="notice" class="board-notice" role="status">
      {{ notice }}
    </p>
    <section v-if="results && !selected" class="card">
      <h2 ref="resultHeading" tabindex="-1">
        推荐结果
      </h2>
      <p>
        找到 {{ (results.complete?.plans?.length || 0) + (results.assembly?.plans?.length || 0) }} 套候选 · 预算 {{ money(demand.budgetCents) }} · 用途 {{ optionLabel(demand.purpose) }} · 偏好 {{ optionLabel(demand.preference) }}
      </p>
      <p v-if="results.searchTruncated" class="board-notice">
        搜索已截断。结果与最低价仅代表本次搜索范围，不能视为全库最低。
      </p>
      <div class="board-tabs" role="tablist" aria-label="方案类别">
        <button v-for="(title, key) in bucketLabels" :id="`board-tab-${key}`" :key="key" role="tab" :aria-selected="tab === key" :aria-controls="`board-bucket-${key}`" @click="tab = key">
          {{ title }}（{{ results[key]?.plans?.length || 0 }}）
        </button>
      </div>
      <div v-if="bucket" :id="`board-bucket-${tab}`" role="tabpanel" :aria-labelledby="`board-tab-${tab}`">
        <p>
          {{ labelState(bucket.state) }}
        </p>
        <p v-if="bucket.searchTruncated">
          此类别搜索已截断，仅显示搜索内结果。
        </p>
        <div v-if="!bucket.plans?.length" class="board-notice">
          <p>
            {{ emptyBucketMessage }}
          </p>
          <ul>
            <li v-for="m in bucket.missing || []" :key="m">
              {{ reasonLabel(m) }}
            </li>
          </ul>
        </div>
        <p v-for="(count, reason) in Object.fromEntries(Object.entries(bucket.excludedCounts || {}).filter(([,count]) => count > 0))" :key="reason">
          筛除原因 {{ reasonLabel(reason) }}：{{ count }}
        </p>
        <article v-if="minimumComplete" class="board-item" :data-plan-id="planIdentity(bucket.minimumFeasiblePlan)">
          <h3>
            本次搜索内最低可行方案
          </h3>
          <BoardPricing :evaluation="bucket.minimumFeasiblePlan" />
          <p>
            {{ isReference(bucket.minimumFeasiblePlan) ? '参考预算差额' : '预算差额' }} {{ money(bucket.shortfallCents) }}
          </p>
          <button :disabled="saving" @click="open(bucket.minimumFeasiblePlan)">
            查看最低方案
          </button>
        </article>
        <section v-for="section in planSections" :key="section.key" :data-section="section.key">
          <h3>{{ section.title }}（{{ section.plans.length }}）</h3>

          <div class="board-grid">
          <article v-for="{ plan, index: i } in section.plans" :key="planIdentity(plan)" :data-plan-id="planIdentity(plan)" class="board-item">
            <h3>
              {{ tab === 'complete' ? '成品整板' : '组装方案' }} {{ i + 1 }}
            </h3>
            <BoardPricing :evaluation="plan" />
            <p>{{ labelState(plan.validationState) }}</p>
            <p v-if="plan.budgetDeltaCents < 0" class="board-error">超出预算 {{ money(Math.abs(plan.budgetDeltaCents)) }}，不属于预算内可用方案。</p>
            <p v-for="reason in plan.reasons || []" :key="reason">
              {{ reasonLabel(reason) }}
            </p>
            <p>
              {{ (plan.items || []).map(itemName).join(' · ') }}
            </p>
            <div class="board-actions">
              <button :disabled="saving" @click="open(plan)">
                查看方案 {{ i + 1 }}
              </button>
              <button :disabled="saving" :aria-pressed="isCompared(plan)" @click="toggleCompare(plan)">
                {{ isCompared(plan) ? '移除对比' : '加入对比' }}
              </button>
            </div>
          </article>
          </div>
        </section>
      </div>
    </section>
    <section v-if="comparison.length" class="card">
      <h2>
        跨类别对比（{{ comparison.length }}/3）
      </h2>
      <button :disabled="busy || comparing || saving" @click="compare">
        重新计算并对比
      </button>
      <button v-for="(p,i) in comparison" :key="planIdentity(p)" :disabled="saving" @click="toggleCompare(p)">
        移除 {{ i+1 }}
      </button>
      <div v-if="compared" class="board-comparison">
        <table>
          <caption>
            各方案重新计算价格与差异
          </caption>
          <thead>
            <tr>
              <th>
                项目
              </th>
              <th v-for="(p,i) in compared" :key="i">
                方案 {{ i+1 }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <th>
                类型 / 安装
              </th>
              <td v-for="(p,i) in compared" :key="i">
                {{ installationLabel(p) }}
              </td>
            </tr>
            <tr>
              <th>
                总价 / 状态
              </th>
              <td v-for="(p,i) in compared" :key="i">
                <BoardPricing :evaluation="p" />
                {{ labelState(p.validationState) }}
              </td>
            </tr>
            <tr>
              <th>
                用途 / 限制
              </th>
              <td v-for="(p,i) in compared" :key="i">
                {{ optionLabel(p.demand?.purpose) }}
                <p v-for="r in p.reasons || []" :key="r">
                  {{ reasonLabel(r) }}
                </p>
              </td>
            </tr>
            <tr>
              <th>
                尺寸 / 轮子
              </th>
              <td v-for="(p,i) in compared" :key="i">
                <p v-for="item in p.items || []" :key="item.variantKey">
                  {{ categoryLabels[item.category] }} {{ itemSpecText(item) }}
                </p>
              </td>
            </tr>
            <tr>
              <th>
                待确认事项
              </th>
              <td v-for="(p,i) in compared" :key="i">
                <p v-for="m in p.missing || []" :key="m">
                  {{ reasonLabel(m, isReference(p)) }}
                </p>
                <BoardChecks :checks="p.checks || []" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
    <section v-if="selected" ref="detailPanel" class="card" data-testid="selected-plan">
      <button :disabled="saving" @click="close">
        返回推荐结果
      </button>
      <BoardEvaluation :evaluation="selected" />
      <div v-if="selected.kind === 'ASSEMBLY'" class="board-actions">
        <button v-for="item in selected.selection?.items || []" :key="item.category" :disabled="saving" @click="loadReplacements(item.category)">
          替换{{ categoryLabels[item.category] }}
        </button>
      </div>
      <section v-if="replacements" class="board-item">
        <h3>
          替换{{ categoryLabels[replacementCategory] }}
        </h3>
        <p v-if="!replacements.list?.length">
          没有可用替换候选。
        </p>
        <p v-if="replacements.searchTruncated">
          替换搜索已截断。
        </p>
        <p v-for="(count, reason) in Object.fromEntries(Object.entries(replacements.excludedCounts || {}).filter(([,count]) => count > 0))" :key="reason">
          {{ reasonLabel(reason) }}：{{ count }}
        </p>
        <article v-for="(p,i) in replacements.list || []" :key="planIdentity(p)" class="board-item">
          <p>
            {{ replacementLabel(p) }} · {{ labelState(p.validationState) }}
          </p>
          <p v-for="diff in selectionDiff(selected.selection,p.selection)" :key="diff.category">
            {{ categoryLabels[diff.category] }}：{{ displayDifference(diff.before, diff.category) }} → {{ displayDifference(diff.after, diff.category) }}
          </p>
          <BoardPricing :evaluation="p" />
          <button @click="propose(p)">
            检查替换 {{ i+1 }}
          </button>
        </article>
        <div class="board-actions">
          <button :disabled="replacementPage === 1 || busy" @click="loadReplacements(replacementCategory,replacementPage-1)">
            上一页候选
          </button>
          <span>
            第{{ replacementPage }}页 / {{ replacements.total }}条
          </span>
          <button :disabled="replacementPage*10 >= replacements.total || busy" @click="loadReplacements(replacementCategory,replacementPage+1)">
            下一页候选
          </button>
        </div>
      </section>
      <section v-if="pending" class="board-notice">
        <h3>
          确认替换差异
        </h3>
        <p v-for="diff in selectionDiff(selected.selection,pending.selection)" :key="diff.category">
          {{ categoryLabels[diff.category] }}：{{ displayDifference(diff.before, diff.category) }} → {{ displayDifference(diff.after, diff.category) }}
        </p>
        <button v-if="!pending.requiresConfirmation" :disabled="busy" @click="apply()">
          确认单项替换并重算
        </button>
        <p v-else>
          需要连带调整，当前方案尚未修改；请选择有完整选择的建议，确认后才重算。
        </p>
        <article v-for="(a,i) in pending.adjustments" :key="i">
          <p>
            {{ reasonLabel(a.message || a.reasonCode) }}
          </p>
          <p v-for="diff in selectionDiff(selected.selection,a.selection)" :key="diff.category">
            {{ categoryLabels[diff.category] }}：{{ displayDifference(diff.before, diff.category) }} → {{ displayDifference(diff.after, diff.category) }}
          </p>
          <button v-if="a.selection" :disabled="busy" @click="apply(a.selection, null)">
            确认建议 {{ i+1 }} 并重算
          </button>
        </article>
        <button @click="invalidate">
          取消替换
        </button>
      </section>
      <form class="board-form board-item" @submit.prevent="prepareSave">
        <label>
          方案名称
          <input v-model="name" :disabled="saving" required maxlength="80" />
        </label>
        <label>
          备注
          <textarea v-model="note" :disabled="saving" maxlength="1000" />
        </label>
        <p v-if="selected.validationState === 'INSUFFICIENT_DATA'">
          资料不足仅保存为草稿，不能视为完整价格或搭配通过。
        </p>
        <button :disabled="busy || saving || saveUnknown || !!pending || selected.validationState === 'INCOMPATIBLE'">
          重新计算并准备保存
        </button>
        <button v-if="saveReady" type="button" class="btn-primary" :disabled="busy || !name.trim()" @click="save">
          确认保存当前结果
        </button>
      </form>
      <RouterLink to="/profile/board-plans">
        查看我的方案
      </RouterLink>
    </section>
  </div>
</template>
