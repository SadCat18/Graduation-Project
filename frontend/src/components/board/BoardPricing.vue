<script setup>
import { publicAssumption, referenceBudgetLowerBound } from '../../board/presentation'
import BoardChecks from './BoardChecks.vue'
import { isReference, labelState, money, optionLabel, priceLabel, reasonLabel, referenceNotice, safeSource, sourceDate } from '../../board/state'
defineProps({ evaluation: { type: Object, required: true }, sources: { type: Boolean, default: true } })
</script>
<template>
  <div class="board-pricing">
    <p><strong>{{ priceLabel(evaluation) }} {{ money(evaluation.totalCents == null ? evaluation.knownSubtotalCents : evaluation.totalCents) }}</strong></p>
    <p v-if="evaluation.totalCents == null">完整总价未知；资料不足，不能判断完整预算。</p>
    <p v-if="referenceBudgetLowerBound(evaluation) != null" class="board-error" role="status">按当前参考明细，已知部分至少超预算{{ (referenceBudgetLowerBound(evaluation) / 100).toFixed(2) }}元</p>
    <template v-if="sources">
      <p v-for="(missing,i) in evaluation.missing || []" :key="`missing-${i}`">资料缺口：{{ reasonLabel(missing, isReference(evaluation)) }}</p>
      <BoardChecks :checks="evaluation.checks || []" />
    </template>
    <template v-if="isReference(evaluation)">
      <p class="muted">{{ referenceNotice }}</p>
      <details v-if="evaluation.priceAssumptions?.length"><summary>参考估价假设</summary><ul><li v-for="(assumption,i) in evaluation.priceAssumptions" :key="i">{{ publicAssumption(assumption, evaluation.items || []) }}</li></ul></details>
      <details v-if="sources"><summary>查看配件来源</summary><div v-for="(source,i) in (evaluation.items || []).flatMap(item => [...(item.sources || []), ...(item.quoteSources || [])])" :key="i" class="board-source">
        <a v-if="safeSource(source.url)" :href="safeSource(source.url)" target="_blank" rel="noopener noreferrer">{{ source.title || '查看来源' }}</a><span v-else>{{ source.title || '未提供来源链接' }}</span>
        <span> · 采集 {{ sourceDate(source) }} · {{ optionLabel(source.verificationStatus) || '待核实' }}</span>
      </div></details>
    </template>
  </div>
</template>
