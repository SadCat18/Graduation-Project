<script setup>
import { isReference, labelState, money, optionLabel, priceLabel, reasonLabel, referenceNotice, safeSource, sourceDate } from '../../board/state'
defineProps({ evaluation: { type: Object, required: true }, sources: { type: Boolean, default: true } })
</script>
<template>
  <div class="board-pricing">
    <p><strong>{{ priceLabel(evaluation) }} {{ money(evaluation.totalCents == null ? evaluation.knownSubtotalCents : evaluation.totalCents) }}</strong></p>
    <p v-if="evaluation.totalCents == null">资料不足，不能判断完整预算。</p>
    <template v-if="sources && evaluation.totalCents == null">
      <p v-for="(missing,i) in evaluation.missing || []" :key="`missing-${i}`">资料缺口：{{ reasonLabel(missing, isReference(evaluation)) }}</p>
      <p v-for="(check,i) in evaluation.checks || []" :key="`check-${i}`">{{ labelState(check.state) }}：{{ reasonLabel(check.explanation || check.message) }}</p>
    </template>
    <template v-if="isReference(evaluation)">
      <p class="muted">{{ referenceNotice }}</p>
      <ul v-if="evaluation.priceAssumptions?.length"><li v-for="(assumption,i) in evaluation.priceAssumptions" :key="i">{{ assumption }}</li></ul>
      <template v-if="sources"><div v-for="(source,i) in (evaluation.items || []).flatMap(item => [...(item.sources || []), ...(item.quoteSources || [])])" :key="i" class="board-source">
        <a v-if="safeSource(source.url)" :href="safeSource(source.url)" target="_blank" rel="noopener noreferrer">{{ source.title || '查看来源' }}</a><span v-else>{{ source.title || '未提供来源链接' }}</span>
        <span> · 采集 {{ sourceDate(source) }} · {{ optionLabel(source.verificationStatus) || '待核实' }}</span>
      </div></template>
    </template>
  </div>
</template>
