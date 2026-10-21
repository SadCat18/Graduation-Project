<script setup>
import { categoryLabels, labelState, money, optionLabel, reasonLabel, ruleLabels, safeSource, sourceDate, isReference } from '../../board/state'
import { itemSpecs } from '../../board/presentation'
import BoardChecks from './BoardChecks.vue'
import BoardPricing from './BoardPricing.vue'
defineProps({ evaluation: { type: Object, required: true }, title: String })
</script>
<template>
  <section class="board-evaluation">
    <h3>{{ title || (evaluation.kind === 'COMPLETE' ? '成品整板' : '组装方案') }}</h3>
    <BoardPricing :evaluation="evaluation" :sources="false" /><p>{{ labelState(evaluation.snapshotState || evaluation.validationState) }}</p>
    <p v-if="evaluation.totalCents != null && evaluation.budgetDeltaCents != null">{{ isReference(evaluation) ? '参考' : '' }}{{ evaluation.budgetDeltaCents >= 0 ? '预算剩余' : '超出预算' }} {{ money(Math.abs(evaluation.budgetDeltaCents)) }}</p>
    <p v-if="evaluation.historicalCopy">历史方案复制：保留原价格，不代表当前有效价格。</p>
    <ul v-if="evaluation.reasons?.length"><li v-for="reason in evaluation.reasons" :key="reason">{{ reasonLabel(reason) }}</li></ul>
    <ul v-if="evaluation.missing?.length"><li v-for="reason in evaluation.missing" :key="reason">资料缺口：{{ reasonLabel(reason, isReference(evaluation)) }}</li></ul>
    <h4>规则解释与检查</h4>
    <BoardChecks :checks="evaluation.checks || []" />
    <details><summary>查看配件规格、包装与来源</summary>
    <article v-for="item in evaluation.items || []" :key="item.variantKey" class="board-item">
      <h4>{{ categoryLabels[item.category] }} · {{ item.label || item.variantKey }}</h4>
      <dl class="board-specs"><template v-for="spec in itemSpecs(item)" :key="spec.key"><dt>{{ spec.label }}</dt><dd>{{ spec.value }}</dd></template></dl>
      <p>所需 {{ item.requiredQuantity ?? '未知' }} 件；每包装 {{ item.packageQuantity ?? '未知' }} 件（{{ item.packageUnit || '以来源说明为准' }}）；购买 {{ item.packageCount ?? '未知' }} 包</p>
      <p>包装{{ isReference(evaluation) ? '参考价' : '价' }} {{ item.amountCents == null ? '未计价' : money(item.amountCents) }}；运费 {{ item.shippingCents == null ? '未知，未计入' : money(item.shippingCents) }}；税费 {{ item.taxCents == null ? '未知，未计入' : money(item.taxCents) }}；购买项{{ isReference(evaluation) ? '参考小计' : '合计' }} {{ item.lineTotalCents == null ? '未计价' : money(item.lineTotalCents) }}</p>
      <p v-if="item.quoteKey">报价 {{ item.quoteKey }} / 版本 {{ item.quoteVersion }}</p>
      <div v-for="(source, i) in [...(item.sources || []), ...(item.quoteSources || [])]" :key="i" class="board-source">
        <a v-if="safeSource(source.url)" :href="safeSource(source.url)" target="_blank" rel="noopener noreferrer">{{ source.title || '查看来源' }}</a><span v-else>{{ source.title || '未提供来源链接' }}</span>
        <span> · 采集 {{ sourceDate(source) }} · {{ optionLabel(source.verificationStatus) || '待核实' }}</span>
      </div>
    </article>
    </details>
    <p v-if="evaluation.kind === 'ASSEMBLY'" class="muted">咬轮仍受桥松紧、胶套与使用方式影响。符合已知规则不保证风险为零。</p>
  </section>
</template>
