<script setup>
import { reactive, ref } from 'vue'
import { parseBudget } from '../../board/state'
const props = defineProps({ initial: Object, busy: Boolean, action: { type: String, default: '查找方案' } })
const emit = defineEmits(['submit'])
const form = reactive({ budget: props.initial ? (props.initial.budgetCents / 100).toFixed(2) : '', purpose: props.initial?.purpose || 'ACTION', preference: props.initial?.preference || 'UNSURE', recommendationType: props.initial?.recommendationType || 'BOTH', shoeValue: props.initial?.shoeSize?.value || '', shoeSystem: props.initial?.shoeSize?.system || 'CN' })
const error = ref('')
function submit() {
  try {
    error.value = ''
    const budgetCents = parseBudget(form.budget)
    const shoe = form.shoeValue === '' ? null : Number(form.shoeValue)
    if (shoe !== null && (!Number.isFinite(shoe) || shoe <= 0 || shoe > 100)) throw new Error('请填写有效鞋码（0～100之间）')
    emit('submit', { budgetCents, purpose: form.purpose, preference: form.preference, recommendationType: form.recommendationType, shoeSize: shoe === null ? null : { value: shoe, system: form.shoeSystem } })
  } catch (e) { error.value = e.message }
}
</script>
<template>
  <form class="board-form" @submit.prevent="submit">
    <label>人民币预算（元）<input v-model="form.budget" inputmode="decimal" required placeholder="例如 600.00" /></label>
    <label>主要用途<select v-model="form.purpose"><option value="ACTION">基础动作练习</option><option value="CRUISE">日常滑行</option><option value="BALANCED">两者兼顾</option></select></label>
    <label>偏好<select v-model="form.preference"><option value="EASY_START">容易上手</option><option value="COMFORT">滑行舒适</option><option value="VALUE">价格实惠</option><option value="UNSURE">不确定</option></select></label>
    <label>推荐类型<select v-model="form.recommendationType"><option value="BOTH">都可以</option><option value="COMPLETE">成品整板</option><option value="ASSEMBLY">自己组装</option></select></label>
    <label>鞋码（选填）<input v-model="form.shoeValue" inputmode="decimal" placeholder="仅记录，不参与排序" /></label>
    <label>尺码体系<select v-model="form.shoeSystem"><option>CN</option><option>EU</option><option>US</option><option>UK</option></select></label>
    <p class="muted">双翘滑板范围。鞋码暂不用于尺寸推断；推荐不会强制花完预算。</p>
    <p v-if="error" role="alert">{{ error }}</p><button class="btn-primary" :disabled="busy">{{ busy ? '计算中…' : action }}</button>
  </form>
</template>
