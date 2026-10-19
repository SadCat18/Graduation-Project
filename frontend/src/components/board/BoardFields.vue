<script setup>
import { optionLabel } from '../../board/state'
const props = defineProps({ modelValue: {type:Object,default:()=>({})}, fields: {type:Array,required:true} })
const emit = defineEmits(['update:modelValue'])
function update(field, value) {
  let parsed = value === '' ? null : value
  if(field.type === 'number' && value !== '') parsed = Number(value)
  if(field.type === 'bool' && value !== '') parsed = value === 'true'
  if(field.type === 'list') parsed = value.split(/[,，\n]/).map(s=>s.trim()).filter(Boolean)
  emit('update:modelValue',{...props.modelValue,[field.key]:parsed})
}
</script>
<template>
  <div class="board-form"><label v-for="field in fields" :key="field.key">{{ field.label }}
    <select v-if="field.type === 'choice'" :aria-label="field.label" :value="modelValue?.[field.key] ?? ''" @change="update(field,$event.target.value)"><option value="">未知 / 未填写</option><option v-if="modelValue?.[field.key] && !field.options.includes(modelValue[field.key])" :value="modelValue[field.key]">{{ modelValue[field.key] }}（保留原值）</option><option v-for="option in field.options" :key="option" :value="option">{{ optionLabel(option) }}</option></select>
    <select v-else-if="field.type === 'bool'" :aria-label="field.label" :value="modelValue?.[field.key] == null ? '' : String(modelValue[field.key])" @change="update(field,$event.target.value)"><option value="">未知</option><option value="true">是</option><option value="false">否</option></select>
    <textarea v-else-if="field.type === 'list'" :aria-label="field.label" :value="(modelValue?.[field.key] || []).join(', ')" @input="update(field,$event.target.value)" />
    <input v-else :aria-label="field.label" :value="modelValue?.[field.key] ?? ''" :type="field.type === 'number' ? 'number' : 'text'" :step="field.integer ? 1 : 'any'" :max="field.integer ? Number.MAX_SAFE_INTEGER : undefined" @input="update(field,$event.target.value)" />
  </label></div>
</template>
