<script setup>
import { computed, reactive, watch } from 'vue'
import { label } from '../../training/display'
const props = defineProps({ task: Object, date: String, disabled: Boolean })
const emit = defineEmits(['submit'])
const form = reactive({ practiceDate: props.date, completion: 'PARTIAL', actualMinutes: 1, difficulty: '', reasons: [], note: '', checks: {}, segmentId: '' })
watch(() => form.completion, value => { if (value === 'NOT_STARTED') { form.actualMinutes = 0; form.difficulty = '' } })
const segments = computed(() => (props.task?.segments || []).filter(item => item.status !== 'RETIRED'))
const minDate = computed(() => segments.value.find(item => item.id === form.segmentId)?.date || props.task?.date)
function submit() {
  const data = { instanceId: props.task.id, practiceDate: form.practiceDate, completion: form.completion, actualMinutes: Number(form.actualMinutes), difficulty: form.difficulty || null, reasons: [...form.reasons], note: form.note, selfChecks: (props.task.selfChecks || []).filter(item => form.checks[item.id]).map(item => ({ questionId: item.id, answer: form.checks[item.id] })) }
  if (form.segmentId) data.segmentId = form.segmentId
  emit('submit', data)
}
</script>
<template>
  <form class="training-form" @submit.prevent="submit">
    <fieldset :disabled="disabled" style="display:contents">
      <p>每次提交是一条新的练习记录，提交后不可直接修改或删除。再次练习可另记一次；同次网络重试不会另建记录。</p>
      <label>练习日期<input v-model="form.practiceDate" type="date" :min="minDate" :max="date" required /></label>
      <label>对应安排（选填）<select v-model="form.segmentId"><option value="">整体任务</option><option v-for="segment in segments" :key="segment.id" :value="segment.id">{{ segment.date }} · {{ segment.minutes }} 分钟 · {{ label(segment.status) }}</option></select></label>
      <label>完成情况<select v-model="form.completion"><option v-for="item in ['COMPLETED','PARTIAL','NOT_STARTED']" :key="item" :value="item">{{ label(item) }}</option></select></label>
      <label>实际练习分钟<input v-model.number="form.actualMinutes" type="number" :min="form.completion === 'NOT_STARTED' ? 0 : 1" :max="form.completion === 'NOT_STARTED' ? 0 : 240" step="1" required /></label>
      <label>主观难度<select v-model="form.difficulty" :required="form.completion !== 'NOT_STARTED'"><option value="">未填写</option><option v-for="item in ['EASY','MODERATE','HARD']" :key="item" :value="item">{{ label(item) }}</option></select></label>
      <fieldset><legend>任务专属自查（用户自评）</legend><label v-for="question in task.selfChecks || []" :key="question.id">{{ question.question }}<select v-model="form.checks[question.id]"><option value="">未回答</option><option v-for="answer in ['YES','NO','UNSURE','NOT_APPLICABLE']" :key="answer" :value="answer">{{ label(answer) }}</option></select></label></fieldset>
      <fieldset><legend>困难原因（可多选）</legend><label v-for="reason in ['TIME_SHORTAGE','VENUE_UNSUITABLE','MOVEMENT_DIFFICULTY','DISCOMFORT','OTHER']" :key="reason"><input v-model="form.reasons" type="checkbox" :value="reason" />{{ label(reason) }}</label></fieldset>
      <label>补充记录<textarea v-model="form.note" maxlength="1000" /></label>
      <p>身体不适请先停止相关练习；不适提醒会随记录保留，系统不提供诊断。分钟、完成情况和动作自评分别记录。</p>
      <button class="btn-primary">提交本次练习</button>
    </fieldset>
  </form>
</template>
