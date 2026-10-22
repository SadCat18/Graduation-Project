<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { getRole } from '../../utils/auth'
import { trainingApi, ADMIN_TRAINING as root, pathKey } from '../../api/training'
import { useTrainingWork } from '../../training/work'
import { label } from '../../training/display'
import TrainingSources from '../../components/training/TrainingSources.vue'
import TrainingStatus from '../../components/training/TrainingStatus.vue'
import '../../training/training.css'
const work = useTrainingWork(root)
const list = ref(null), selected = ref(null), editor = ref(''), parsed = ref(null), syntaxError = ref(''), reason = ref(''), targetStatus = ref('PUBLISHED'), safetyCorrection = ref(false), confirmed = ref(false), editing = ref(false)
const dirty = computed(() => selected.value && editor.value.trim() !== JSON.stringify(selected.value,null,2).trim())
const locked = computed(() => !work.ready.value || work.writing.value || !!work.pending.value)
watch(editor, () => { parsed.value = null; syntaxError.value = ''; confirmed.value = false })
watch([targetStatus,reason,safetyCorrection], () => { confirmed.value = false })
watch(work.ready, ready => { if (!ready) { list.value = null; selected.value = null; editor.value = ''; parsed.value = null; editing.value = false } })
function setDocument(data) { selected.value = data; editor.value = JSON.stringify(data,null,2); parsed.value = null; reason.value = ''; safetyCorrection.value = false; confirmed.value = false; editing.value = true }
async function load(page = 1) {
  if (work.writing.value) return
  if (!work.ready.value) await work.init()
  if (getRole() !== 'ADMIN') return
  work.read('list', () => trainingApi.get(`${root}/templates`, { page, size:10 }), data => { list.value = data })
}
async function open(item, latest = false) {
  if (locked.value) return
  work.invalidate(); selected.value = null; parsed.value = null; editing.value = false
  await work.read('document', () => trainingApi.get(`${root}/templates/${pathKey(item.id)}`, latest ? undefined : { version:item.version }), setDocument)
}
function newDocument(copy = false) {
  if (locked.value) return
  work.invalidate(); const text = copy ? editor.value : JSON.stringify({title:'',goal:'',level:'BEGINNER',limitations:'',sources:[],tasks:[],rules:{}},null,2)
  selected.value = null; editor.value = text; parsed.value = null; editing.value = true; confirmed.value = false
}
function validate() {
  try { const value = JSON.parse(editor.value); if (!value || Array.isArray(value) || typeof value !== 'object') throw new Error('必须是JSON对象'); parsed.value = value; syntaxError.value = ''; return value }
  catch (e) { parsed.value = null; syntaxError.value = `JSON语法未通过：${e.message}`; return null }
}
function success(data) { setDocument(data); load(list.value?.page || 1) }
function save() {
  const document = validate(); if (!document) return
  const body = { document }, method = selected.value ? 'PUT' : 'POST', path = selected.value ? `${root}/templates/${pathKey(selected.value.id)}` : `${root}/templates`
  if (selected.value) body.expectedVersion = selected.value.version
  work.write(selected.value ? '保存新版本草稿' : '创建草稿',path,body,success,method)
}
function publish() {
  if (dirty.value || !selected.value || !confirmed.value || !reason.value.trim()) return
  const body = { expectedVersion:selected.value.version, status:targetStatus.value, reason:reason.value.trim() }
  if (targetStatus.value === 'DISABLED') body.safetyCorrection = safetyCorrection.value
  work.write(targetStatus.value === 'PUBLISHED' ? '发布模板版本' : '停用模板',`${root}/templates/${pathKey(selected.value.id)}/publication`,body,success)
}
async function refresh() { if (selected.value) await open(selected.value,true); await load(list.value?.page || 1) }
onMounted(async () => { await work.init(); await load() })
</script>
<template>
  <main class="training-page">
    <header class="training-head"><h1>训练内容与版本管理</h1><RouterLink to="/admin">返回管理后台</RouterLink></header>
    <p>管理员文档编辑覆盖模板、任务、自查、固定指导和规则映射。草稿可不完整；发布必须经服务端检查，本地JSON语法通过不等于发布成功。</p>
    <TrainingStatus :work="work" @retry="work.retry(success)" @refresh="refresh" />
    <p v-if="getRole() !== 'ADMIN'" role="alert">仅管理员可访问内容维护。</p>
    <p v-if="work.busy.list || work.busy.document" role="status">正在读取训练内容…</p>
    <button :disabled="locked" @click="newDocument(false)">新建内容草稿</button>
    <section class="card"><h2>所有版本</h2><p v-if="list && !list.list.length">暂无模板，可建立草稿。</p><article v-for="item in list?.list || []" :key="`${item.id}-${item.version}`" class="training-item"><h3>{{ item.title }} · 版本 {{ item.version }} · {{ label(item.status) }}</h3><p v-if="item.safetyCorrected" class="training-warning">存在安全纠正</p><button :disabled="locked" @click="open(item)">查看并编辑此版本</button></article><div v-if="list" class="training-actions"><button :disabled="work.writing.value || list.page <= 1" @click="load(list.page-1)">上一页</button><span>第 {{ list.page }} 页 · 共 {{ list.total }} 条</span><button :disabled="work.writing.value || list.page * list.size >= list.total" @click="load(list.page+1)">下一页</button></div></section>
    <section v-if="editing" class="card">
      <h2>{{ selected ? `所读版本 ${selected.version} · ${label(selected.status)}` : '新草稿' }}</h2><p>保存编辑将建立新的草稿版本，不原位覆盖历史或已采用的个人计划。只有真实响应确认保存成功。</p>
      <label>完整教学文档 JSON<textarea v-model="editor" class="training-json" spellcheck="false" :disabled="locked" /></label>
      <p v-if="syntaxError" role="alert">{{ syntaxError }}</p><div class="training-actions"><button :disabled="locked" @click="validate">检查JSON语法并预览</button><button :disabled="locked" class="btn-primary" @click="save">{{ selected ? '保存为新版本草稿' : '保存新草稿' }}</button><button :disabled="locked" @click="newDocument(true)">以当前文档另建模板</button></div>
      <section v-if="parsed" aria-label="文档发布前预览"><h3>{{ parsed.title || '未填写标题' }}</h3><p>{{ parsed.goal }} · {{ parsed.limitations }}</p><TrainingSources :sources="parsed.sources || []" /><details v-for="item in parsed.tasks || []" :key="item.id"><summary>{{ item.title || item.id }}</summary><p>{{ item.goal }}</p><ol><li v-for="(step,index) in item.steps || []" :key="index">{{ step }}</li></ol><p v-for="(note,index) in item.safetyNotes || []" :key="index">{{ note }}</p><TrainingSources :sources="item.sources || []" /><pre>{{ JSON.stringify({ selfChecks:item.selfChecks, prerequisites:item.prerequisites, guidance:item.guidance, advanceCondition:item.advanceCondition },null,2) }}</pre></details><details><summary>固定规则映射</summary><pre>{{ JSON.stringify(parsed.rules,null,2) }}</pre></details></section>
      <form v-if="selected" class="training-form" @submit.prevent="publish"><h3>发布 / 停用已保存版本</h3><p>未保存的JSON修改不会被此操作发布，请先保存并核对版本。停用保留历史；安全纠正会由服务端停用所有关联版本、使建议失效并通知受影响计划。</p><label>目标状态<select v-model="targetStatus" :disabled="locked"><option value="PUBLISHED">发布当前已保存版本</option><option value="DISABLED">停用模板</option></select></label><label>操作理由<textarea v-model="reason" minlength="1" maxlength="1000" required :disabled="locked" /></label><label v-if="targetStatus === 'DISABLED'"><input v-model="safetyCorrection" type="checkbox" :disabled="locked" />这是安全纠正，请通知受影响计划并停用关联版本</label><label><input v-model="confirmed" type="checkbox" required :disabled="locked" />我已核对已保存版本、来源、限制与本次影响</label><button :disabled="locked || dirty || !confirmed || !reason.trim()">确认{{ targetStatus === 'PUBLISHED' ? '发布' : '停用' }}已保存版本</button></form>
    </section>
  </main>
</template>
