<script setup>
import { reactive, watch } from 'vue'
import { parseBudget } from '../../board/state'
const props = defineProps({ initial: Object, busy: Boolean, action: { type: String, default: '查找方案' } })
const emit = defineEmits(['submit'])
const form = reactive({ budget: props.initial ? (props.initial.budgetCents / 100).toFixed(2) : '', purpose: props.initial?.purpose || 'ACTION', preference: props.initial?.preference || 'UNSURE', recommendationType: props.initial?.recommendationType || 'BOTH', shoeValue: props.initial?.shoeSize?.value || '', shoeSystem: props.initial?.shoeSize?.system || 'CN' })
const errors = reactive({ budget: '', shoe: '' })
watch(() => form.budget, () => { errors.budget = '' })
watch(() => form.shoeValue, () => { errors.shoe = '' })
function submit() {
  errors.budget = ''; errors.shoe = ''
  let budgetCents
  try { budgetCents = parseBudget(form.budget) } catch (e) { errors.budget = e.message }
  const shoe = form.shoeValue === '' ? null : Number(form.shoeValue)
  if (shoe !== null && (!Number.isFinite(shoe) || shoe <= 0 || shoe > 100)) errors.shoe = '请填写有效鞋码（大于0且不超过100）'
  if (errors.budget || errors.shoe) return
  emit('submit', { budgetCents, purpose: form.purpose, preference: form.preference, recommendationType: form.recommendationType, shoeSize: shoe === null ? null : { value: shoe, system: form.shoeSystem } })
}
</script>
<template>
  <form class="board-form" @submit.prevent="submit">
    <label>人民币预算（元）<input v-model="form.budget" inputmode="decimal" aria-describedby="budget-help budget-error" :aria-invalid="!!errors.budget" placeholder="例如 600.00" /><small id="budget-help">请输入大于0的人民币金额（元），最多两位小数。</small><span v-if="errors.budget" id="budget-error" role="alert">{{ errors.budget }}</span></label>
    <label>主要用途<select v-model="form.purpose"><option value="ACTION">基础动作练习</option><option value="CRUISE">日常滑行</option><option value="BALANCED">两者兼顾</option></select></label>
    <label>偏好<select v-model="form.preference"><option value="EASY_START">容易上手</option><option value="COMFORT">滑行舒适</option><option value="VALUE">价格实惠</option><option value="UNSURE">不确定</option></select></label>
    <label>推荐类型<select v-model="form.recommendationType"><option value="BOTH">都可以</option><option value="COMPLETE">成品整板</option><option value="ASSEMBLY">自己组装</option></select></label>
    <label>鞋码（选填）<input v-model="form.shoeValue" inputmode="decimal" aria-describedby="shoe-help shoe-error" :aria-invalid="!!errors.shoe" placeholder="仅记录，不参与排序" /><small id="shoe-help">仅记录鞋码，不参与尺寸推断或排序。</small><span v-if="errors.shoe" id="shoe-error" role="alert">{{ errors.shoe }}</span></label>
    <label>尺码体系<select v-model="form.shoeSystem"><option>CN</option><option>EU</option><option>US</option><option>UK</option></select></label>
    <p class="muted">双翘滑板范围。鞋码暂不用于尺寸推断；推荐不会强制花完预算。</p>
    <button class="btn-primary" :disabled="busy">{{ busy ? '计算中…' : action }}</button>
  </form>
</template>
