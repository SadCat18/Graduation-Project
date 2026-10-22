<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { getRole } from '../utils/auth'
import { trainingApi, USER_TRAINING as root, pathKey } from '../api/training'
import { useTrainingWork, clone } from '../training/work'
import { can, chinaDate, displayText, label } from '../training/display'
import TrainingSetup from '../components/training/TrainingSetup.vue'
import TrainingFeedback from '../components/training/TrainingFeedback.vue'
import TrainingStats from '../components/training/TrainingStats.vue'
import TrainingSources from '../components/training/TrainingSources.vue'
import TrainingReview from '../components/training/TrainingReview.vue'
import TrainingChanges from '../components/training/TrainingChanges.vue'
import TrainingStatus from '../components/training/TrainingStatus.vue'
import '../training/training.css'
const work = useTrainingWork(root)
const plan = ref(null), task = ref(null), templates = ref([]), templateDetail = ref(null), chosenTemplate = ref(null), preview = ref(null), previewPayload = ref(null)
const desiredPlanId = ref(null)
const tab = ref('today'), today = ref(null), viewDate = ref(''), stats = ref(null), review = ref(null), selectedSuggestion = ref(null)
const pages = reactive({ plans: null, records: null, suggestions: null, adjustments: null })
const question = ref(''), aiContext = ref(null), aiReply = ref(null), stateAction = ref(''), resumeLevel = ref(''), safetyAcknowledged = ref(false)
const tabs = { today: '今日与任务', records: '练习历史', suggestions: '待确认建议', adjustments: '调整历史', plans: '计划历史', setup: '建立新计划' }
const locked = computed(() => work.writing.value || !!work.busy.aiExplain)
const writeLocked = computed(() => !work.ready.value || locked.value || !!work.pending.value)
const planPath = () => `${root}/plans/${pathKey(plan.value.id)}`
const currentDate = computed(() => plan.value?.today?.date || chinaDate())
const allowedFeedback = computed(() => can(plan.value, 'FEEDBACK') && can(task.value, 'FEEDBACK'))
function clearAi() { work.cancel('aiContext'); work.cancel('aiExplain'); aiContext.value = null; aiReply.value = null }
watch(question, clearAi)
watch(work.ready, ready => { if (!ready) { desiredPlanId.value = null; plan.value = null; task.value = null; review.value = null; stats.value = null; today.value = null; templateDetail.value = null; templates.value = []; preview.value = null; for (const key in pages) pages[key] = null; aiContext.value = null; aiReply.value = null } })
function installPlan(data) {
  for (const key of ['records','suggestions','adjustments']) pages[key] = null
  desiredPlanId.value = data?.id ?? null
  plan.value = data; task.value = null; selectedSuggestion.value = null; stats.value = data?.stats || null; today.value = data?.today || null; viewDate.value = data?.today?.date || data?.settings?.startDate || ''
  stateAction.value = ''; resumeLevel.value = ''; safetyAcknowledged.value = false; clearAi()
}
async function loadPage(name, page = 1) {
  const path = name === 'plans' ? `${root}/plans` : plan.value ? `${planPath()}/${name}` : null
  if (!path) return
  await work.read(name, () => trainingApi.get(path, { page, size: 10 }), data => { pages[name] = data })
}
function panels() { if (!plan.value) return; const path = planPath(); work.read('stats', () => trainingApi.get(`${path}/statistics`), data => { stats.value = data }); loadPage('suggestions'); if (['records','adjustments'].includes(tab.value)) loadPage(tab.value) }
async function refresh() {
  if (locked.value) return
  if (!work.ready.value) await work.init()
  if (!work.ready.value || getRole() !== 'USER') return
  work.invalidate(); clearAi()
  work.read('templates', () => trainingApi.get(`${root}/templates`), data => { templates.value = data })
  const path = plan.value ? planPath() : `${root}/current`
  await work.read('plan', () => trainingApi.get(path), data => { installPlan(data); if (!data) tab.value = 'setup'; panels() })
  if (tab.value === 'plans') loadPage('plans')
}
function setTab(value) {
  if (locked.value) return
  work.invalidate(); tab.value = value; task.value = null; stateAction.value = ''; clearAi()
  if (['plans','records','suggestions','adjustments'].includes(value)) loadPage(value)
}
async function openPlan(value) {
  if (locked.value) return
  desiredPlanId.value = value.id
  work.invalidate(); review.value = null; plan.value = null; task.value = null; stats.value = null; today.value = null; for (const key of ['records','suggestions','adjustments']) pages[key] = null; tab.value = 'today'; clearAi()
  await work.read('plan', () => trainingApi.get(`${root}/plans/${pathKey(value.id)}`), data => { installPlan(data); panels() })
}
async function selectTask(value) {
  if (locked.value) return
  work.invalidate(); task.value = null; review.value = null; clearAi()
  const path = `${planPath()}/tasks/${pathKey(value.id)}`
  await work.read('task', () => trainingApi.get(path), data => { task.value = data })
}
function changeSetup() { preview.value = null; previewPayload.value = null; work.cancel('preview') }
function selectTemplate(value) {
  work.cancel('template')
  chosenTemplate.value = value; templateDetail.value = null
  if (value) work.read('template', () => trainingApi.get(`${root}/templates/${pathKey(value.id)}`, { version: value.version }), data => { templateDetail.value = data })
}
function previewPlan(data) { work.read('preview', () => trainingApi.preview(`${root}/plans/preview`, data), value => { preview.value = value; previewPayload.value = clone(data) }) }
function applyWrite(data, intent) {
  const nextPlan = data.plan || data, bound = intent.path.match(/\/plans\/([^/]+)\//)
  if ((bound && desiredPlanId.value != null && decodeURIComponent(bound[1]) !== String(desiredPlanId.value)) || (plan.value && nextPlan?.id === plan.value.id && nextPlan.version < plan.value.version)) { work.notice.value = '原操作已由服务端确认，当前查看的计划已有变化，请刷新对应计划核对。'; return }
  work.invalidate(); clearAi()
  if (intent.path.endsWith('/feedback')) { installPlan(data.plan); question.value = ''; review.value = data; tab.value = 'today' }
  else if (intent.path.endsWith('/decision')) { installPlan(data.plan); tab.value = 'suggestions' }
  else { installPlan(data); preview.value = null; previewPayload.value = null; tab.value = 'today' }
  panels()
}
function retryWrite() { if (locked.value || work.busy.plan) return; work.retry(applyWrite) }
function createPlan() { if (previewPayload.value) work.write('建立训练计划', `${root}/plans`, previewPayload.value, applyWrite) }
function feedback(data) { if (allowedFeedback.value) work.write(`提交${task.value.title}的本次练习`, `${planPath()}/feedback`, { ...data, expectedVersion: plan.value.version }, applyWrite) }
function chooseState(action) { stateAction.value = action; resumeLevel.value = ''; safetyAcknowledged.value = false }
function stateSubmit() {
  if (!can(plan.value, stateAction.value)) return
  const body = { action: stateAction.value, expectedVersion: plan.value.version }
  if (stateAction.value === 'RESUME') { if (!resumeLevel.value || !safetyAcknowledged.value) return; body.selfLevel = resumeLevel.value; body.safetyAcknowledged = safetyAcknowledged.value }
  work.write(`${label(stateAction.value)}训练计划`, `${planPath()}/state`, body, applyWrite)
}
function decision(value) { if (can(selectedSuggestion.value,value)) work.write(`${label(value)}调整建议`, `${planPath()}/suggestions/${pathKey(selectedSuggestion.value.id)}/decision`, { expectedVersion: plan.value.version, decision: value }, applyWrite) }
function openRecord(record) {
  if (locked.value) return
  work.invalidate(); clearAi(); review.value = null; question.value = ''
  work.read('record', () => trainingApi.get(`${planPath()}/records/${pathKey(record.id)}`), data => { review.value = data })
}
function loadToday() {
  if (!plan.value || !viewDate.value || viewDate.value < plan.value.settings.startDate) return
  const path = `${planPath()}/today`, date = viewDate.value
  today.value = null
  work.read('today', () => trainingApi.get(path, { date }), data => { today.value = data })
}
function previewAi() {
  if (!review.value?.record || !question.value.trim() || locked.value) return
  clearAi()
  const body = { recordId: review.value.record.id, question: question.value.trim() }, path = `${planPath()}/ai-context`
  work.read('aiContext', () => trainingApi.preview(path, body), data => { aiContext.value = data })
}
function explainAi() {
  const context = aiContext.value
  if (!context || locked.value || !context.contextHash) return
  const path = `${planPath()}/ai-explain`, body = { recordId: context.recordId, question: context.question, contextHash: context.contextHash, confirmed: true }
  work.read('aiExplain', () => trainingApi.explain(path, body), data => { aiReply.value = data; aiContext.value = null }, () => { aiContext.value = null })
}
onMounted(async () => { await work.init(); await refresh() })
</script>
<template>
  <main class="training-page">
    <header class="training-head"><div><p class="muted">训练执行与指导 · 文字教学与用户自评</p><h1>我的训练</h1></div><RouterLink to="/skate">返回原训练与动作打卡</RouterLink><RouterLink to="/ai-coach">AI教练</RouterLink></header>
    <p v-if="getRole() !== 'USER'">本训练入口面向用户账号；管理员请使用训练内容管理入口。</p>
    <TrainingStatus :work="work" :blocked="locked || !!work.busy.plan" @retry="retryWrite" @refresh="refresh" />
    <nav class="training-tabs" aria-label="训练区域"><button v-for="(name,key) in tabs" :key="key" type="button" :aria-current="tab === key ? 'page' : undefined" :disabled="locked || !work.ready.value" @click="setTab(key)">{{ name }}</button></nav>
    <p v-if="Object.values(work.busy).some(Boolean)" role="status">正在读取训练资料…</p>
    <section v-if="tab === 'setup'" class="card">
      <h2>建立个人训练计划</h2><p v-if="!work.busy.templates && !work.errors.templates && !templates.length">暂无可用模板，请稍后刷新；不会补造教学内容。</p>
      <p v-if="plan && ['IN_PROGRESS','PAUSED'].includes(plan.status)">已有未终结计划，可查看预览，但服务端会校验是否允许建立新计划。</p>
      <TrainingSetup :templates="templates" :disabled="writeLocked || work.busy.preview" @change="changeSetup" @template="selectTemplate" @preview="previewPlan" />
      <section v-if="templateDetail"><h3>{{ templateDetail.title }} · 版本 {{ templateDetail.version }}</h3><p>{{ templateDetail.limitations }}</p><TrainingSources :sources="templateDetail.sources || []" /><details v-for="item in templateDetail.tasks || []" :key="item.id"><summary>教学内容：{{ item.title }}</summary><ol><li v-for="(step,index) in item.steps || []" :key="index">{{ step }}</li></ol><TrainingSources :sources="item.sources || []" /></details></section>
      <section v-if="preview" aria-label="排期预览"><h3>排期预览（尚未建立）</h3><p>{{ preview.goal }} · {{ label(preview.selfLevel) }} · {{ preview.timezone }}</p><p>{{ preview.limitations }}</p><article v-for="item in preview.tasks || []" :key="item.id" class="training-item"><strong>{{ item.title }}</strong><p v-for="segment in item.segments || []" :key="segment.id">{{ segment.date }} · {{ segment.minutes }} 分钟</p></article><p>确认后由服务端重新校验和建立计划，不采用预览中的临时任务标识。</p><button class="btn-primary" :disabled="writeLocked" @click="createPlan">确认建立计划</button></section>
    </section>
    <section v-if="plan && tab !== 'setup' && tab !== 'plans'" class="card">
      <h2>{{ plan.goal }} · {{ label(plan.status) }}</h2><p>{{ plan.template?.title }} · 模板版本 {{ plan.template?.version }} · 计划版本 {{ plan.version }} · {{ plan.timezone }}</p><p>用户自评：{{ label(plan.selfLevel) }}；每周 {{ plan.settings?.trainingDays?.join('、') }}，单次 {{ plan.settings?.sessionMinutes }} 分钟。</p>
      <p v-for="(alert,index) in plan.safetyAlerts || []" :key="index" class="training-warning">安全提醒：{{ displayText(alert) }}</p><p v-if="plan.contentAvailable === false" class="training-warning">所用教学内容当前不可用，请查看安全提醒与历史快照。</p><p v-for="reason in plan.blockedReasons || []" :key="reason">{{ label(reason) }}</p>
      <TrainingStats :stats="stats" />
      <div class="training-actions"><button v-for="action in ['PAUSE','RESUME','QUIT']" :key="action" :disabled="writeLocked || !can(plan,action)" @click="chooseState(action)">{{ label(action) }}计划</button></div>
      <form v-if="stateAction" class="training-form" @submit.prevent="stateSubmit"><p>确认{{ label(stateAction) }}当前计划？历史记录将保留，不会自动叠加漏练量。</p><template v-if="stateAction === 'RESUME'"><label>恢复前重新自评<select v-model="resumeLevel" required><option value="">请明确选择</option><option v-for="level in ['BEGINNER','FOUNDATION','INTERMEDIATE']" :key="level" :value="level">{{ label(level) }}</option></select></label><label><input v-model="safetyAcknowledged" type="checkbox" required />我已重新自评并了解安全提醒（不表示症状消失或医疗许可）</label></template><button :disabled="writeLocked">确认{{ label(stateAction) }}</button><button type="button" :disabled="locked" @click="stateAction = ''">取消状态操作</button></form>
    </section>
    <section v-if="plan && tab === 'today'" class="card">
      <h2>今日与任务</h2><label>查看日期（Asia/Shanghai）<input v-model="viewDate" type="date" :min="plan.settings.startDate" :disabled="locked" /></label><button :disabled="locked || viewDate < plan.settings.startDate" @click="loadToday">查看该日安排</button>
      <p v-if="today">查看日期 {{ today.date }} · {{ today.timezone }}</p><p v-if="today?.notStarted">计划尚未开始，将于 {{ today.startsOn }} 开始；不算漏练。</p><p v-if="today?.paused">计划已暂停；暂停期间不算漏练。</p><p v-else-if="!today?.notStarted && today?.restDay">这天是休息日，不算漏练。</p><p v-if="today && !today.notStarted && !today.tasks?.length">该日没有训练任务。</p>
      <p v-if="today?.overdue?.length" class="training-warning">有 {{ today.overdue.length }} 项此前已安排但未完成的任务。不要自行叠加训练量，可查看建议。</p><div v-for="item in today?.overdue || []" :key="item.id">{{ item.title }} · {{ item.date }} · {{ label(item.status) }}</div>
      <h3>当日任务</h3><button v-for="item in today?.tasks || []" :key="item.id" :disabled="locked" @click="selectTask(item)">今日任务：{{ item.title }} · {{ label(item.status) }}</button><h3>全部逻辑任务</h3><button v-for="item in plan.tasks || []" :key="item.id" :disabled="locked" @click="selectTask(item)">查看任务：{{ item.title }} · {{ label(item.status) }} · {{ label(item.selfAssessment) }}</button>
      <section v-if="task" aria-label="任务教学与反馈" class="training-item"><h3>{{ task.title }}</h3><p>{{ task.goal }}</p><p>执行：{{ label(task.status) }}；动作自评：{{ label(task.selfAssessment) }}</p><p>前置：{{ (task.prerequisites || []).map(key => plan.tasks.find(item => item.definitionId === key)?.title || key).join('、') || '无额外前置' }}</p><ol><li v-for="(step,index) in task.steps || []" :key="index">{{ step }}</li></ol><p>{{ task.suggestedPractice }} · 资料建议 {{ task.suggestedMinutes }} 分钟</p><p v-for="(note,index) in task.safetyNotes || []" :key="index" class="training-warning">{{ note }}</p><p>{{ task.limitations }}</p><p>推进条件说明：{{ task.advanceCondition }}；实际允许操作仍由服务端决定。</p><TrainingSources :sources="task.sources || []" /><details><summary>查看困难对应的固定指导</summary><p v-for="(text,key) in task.guidance || {}" :key="key">{{ label(key) }}：{{ text }}</p></details><p v-for="reason in task.blockedReasons || []" :key="reason">{{ label(reason) }}</p><p v-for="segment in task.segments || []" :key="segment.id">安排 {{ segment.date }} · {{ segment.minutes }} 分钟 · {{ label(segment.status) }}（只读）</p><TrainingFeedback :key="`${task.id}-${plan.version}`" :task="task" :date="currentDate" :disabled="writeLocked || !allowedFeedback" @submit="feedback" /></section>
    </section>
    <section v-if="['plans','records','suggestions','adjustments'].includes(tab)" class="card">
      <h2>{{ tabs[tab] }}</h2><p v-if="tab !== 'plans' && !plan">请先选择当前或历史计划。</p><p v-if="pages[tab] && !pages[tab].list?.length">暂无此类记录。</p>
      <article v-for="item in pages[tab]?.list || []" :key="item.id" class="training-item">
        <template v-if="tab === 'plans'"><h3>{{ item.goal }} · {{ label(item.status) }}</h3><p>{{ item.createdAt }} · {{ item.timezone }}</p><button :disabled="locked" @click="openPlan(item)">查看历史计划</button></template>
        <template v-else-if="tab === 'records'"><h3>{{ item.taskSnapshot?.title || '练习记录' }}</h3><p>{{ item.practiceDate }} · {{ item.actualMinutes }} 分钟 · {{ label(item.completion) }} · {{ label(item.selfAssessment) }}</p><p>记录时间 {{ item.createdAt }}，不可直接修改。</p><button :disabled="locked" @click="openRecord(item)">查看练习回顾</button></template>
        <template v-else-if="tab === 'suggestions'"><h3>{{ label(item.type) }} · {{ label(item.status) }}</h3><p>依据 {{ label(item.ruleId) }} · 规则版本 {{ item.ruleVersion }} · 计划版本 {{ item.basePlanVersion }}</p><p>触发原因：{{ (item.triggerReasons || []).map(label).join('、') }}</p><p v-for="reason in item.blockedReasons || []" :key="reason">{{ label(reason) }}</p><button :disabled="locked" @click="selectedSuggestion = item">查看建议前后差异</button></template>
        <template v-else><h3>{{ label(item.operation) }}</h3><p>{{ item.createdAt }} · 计划版本 {{ item.fromVersion }} → {{ item.toVersion }} · 处理人 {{ item.actorId }}</p><TrainingChanges :value="item" :tasks="plan?.tasks || []" /></template>
      </article>
      <div v-if="pages[tab]" class="training-actions"><button :disabled="locked || work.busy[tab] || pages[tab].page <= 1" @click="loadPage(tab,pages[tab].page-1)">上一页</button><span>第 {{ pages[tab].page }} 页 · 共 {{ pages[tab].total }} 条</span><button :disabled="locked || work.busy[tab] || pages[tab].page * pages[tab].size >= pages[tab].total" @click="loadPage(tab,pages[tab].page+1)">下一页</button><button :disabled="locked" @click="loadPage(tab,pages[tab].page)">重读本页</button></div>
    </section>
    <section v-if="selectedSuggestion" class="card"><h2>确认建议：{{ label(selectedSuggestion.type) }}</h2><TrainingChanges :value="selectedSuggestion" :tasks="plan?.tasks || []" /><p v-for="reason in selectedSuggestion.blockedReasons || []" :key="reason">{{ label(reason) }}</p><button :disabled="writeLocked || !can(selectedSuggestion,'ACCEPT')" @click="decision('ACCEPT')">接受此建议</button><button :disabled="writeLocked || !can(selectedSuggestion,'REJECT')" @click="decision('REJECT')">拒绝此建议</button><button :disabled="locked" @click="selectedSuggestion = null">关闭建议预览</button></section>
    <section v-if="review" class="card"><TrainingReview :value="review" /><p>固定回顾已经可用。AI解释是独立操作，失败不影响已保存反馈。</p><label>这个动作遇到困难：想问的问题<textarea v-model="question" maxlength="1000" :disabled="locked" /></label><button :disabled="locked || !question.trim()" @click="previewAi">预览带入的问题与资料</button>
      <section v-if="aiContext" aria-label="AI上下文预览"><h3>请确认后再请求AI解释</h3><p>问题：{{ aiContext.question }}</p><p>上下文：{{ displayText(aiContext.contextSummary) }}</p><p>计划版本 {{ aiContext.planVersion }}</p><p v-for="(alert,index) in aiContext.safetyAlerts || []" :key="index" class="training-warning">{{ displayText(alert) }}</p><TrainingSources :sources="aiContext.references || []" /><p v-if="aiContext.contentAvailable === false || !aiContext.references?.length">缺少当前可用依据，不能伪造引用。</p><button :disabled="locked || !aiContext.contextHash || aiContext.contentAvailable === false || !aiContext.references?.length" @click="explainAi">确认请求AI解释</button></section>
      <section v-if="aiReply" aria-label="AI解释"><p>{{ aiReply.reply }}</p><p>{{ displayText(aiReply.limitations) }}</p><TrainingSources :sources="aiReply.references || []" /></section>
    </section>
    <p class="muted">教学来自已维护文字；自查是用户自报，不能作为视频动作理解、医学诊断或专业能力认证。</p>
  </main>
</template>
