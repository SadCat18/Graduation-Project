<script setup>
import { computed, reactive, watch } from 'vue'
import { chinaDate, label } from '../../training/display'
const props = defineProps({ templates: Array, disabled: Boolean })
const emit = defineEmits(['preview','change','template'])
const form = reactive({ template: '', goal: '', selfLevel: '', trainingDays: [], sessionMinutes: 20, startDate: chinaDate() })
const selected = computed(() => props.templates?.find(item => `${item.id}@${item.version}` === form.template))
watch(form, () => emit('change'))
watch(() => form.template, () => emit('template', selected.value))
function submit() {
  if (!selected.value) return
  emit('preview', { templateId: selected.value.id, templateVersion: selected.value.version, goal: form.goal.trim(), selfLevel: form.selfLevel, trainingDays: [...form.trainingDays].sort((a,b) => a-b), sessionMinutes: Number(form.sessionMinutes), startDate: form.startDate })
}
</script>
<template>
  <form class="training-form" @submit.prevent="submit">
    <fieldset :disabled="disabled" style="display:contents">
      <label>已维护模板<select v-model="form.template" required><option value="">请选择模板与版本</option><option v-for="item in templates" :key="`${item.id}-${item.version}`" :value="`${item.id}@${item.version}`">{{ item.title }} · 版本 {{ item.version }}</option></select></label>
      <label>我的训练目标<input v-model="form.goal" required minlength="1" maxlength="100" /></label>
      <label>当前水平（用户自评）<select v-model="form.selfLevel" required><option value="">请明确选择自评</option><option v-for="level in ['BEGINNER','FOUNDATION','INTERMEDIATE']" :key="level" :value="level">{{ label(level) }}</option></select></label>
      <label>单次可用分钟<input v-model.number="form.sessionMinutes" type="number" min="5" max="120" step="1" required /></label>
      <label>计划开始日期（中国日期）<input v-model="form.startDate" type="date" :min="chinaDate()" required /></label>
      <fieldset><legend>每周可训练日（至少一天）</legend><label v-for="(day,index) in ['一','二','三','四','五','六','日']" :key="index"><input v-model="form.trainingDays" type="checkbox" :value="index+1" />周{{ day }}</label></fieldset>
      <p>时间上限是产品输入校验，不是医学或专业强度建议。先查看排期，再明确确认建立；不会自动完成任务或调用AI。</p>
      <button class="btn-primary" :disabled="!form.trainingDays.length">预览训练安排</button>
    </fieldset>
  </form>
</template>
