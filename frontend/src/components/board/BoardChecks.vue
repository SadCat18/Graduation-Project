<script setup>
import { computed } from 'vue'
import { checkGroups, isBlockingCheck } from '../../board/presentation'
import { labelState, reasonLabel, ruleLabels, safeSource, sourceDate } from '../../board/state'
const props = defineProps({ checks: { type: Array, default: () => [] } })
const groups = computed(() => checkGroups(props.checks))
</script>
<template>
  <div class="board-checks">
    <p v-if="!checks.length">暂无完整检查依据。</p>
    <div v-for="(group, index) in groups" :key="index">
      <p v-if="isBlockingCheck(group)" class="board-error">{{ labelState(group.state) }}：{{ reasonLabel(group.explanation) }}</p>
      <p v-if="group.state === 'ADVICE'" class="board-notice">使用限制与建议：{{ reasonLabel(group.explanation) }}</p>
      <details>
        <summary>{{ labelState(group.state) }}：{{ reasonLabel(group.explanation || '暂无说明') }}（{{ group.checks.length }} 条依据）</summary>
        <article v-for="(check, i) in group.checks" :key="i" class="board-item">
          <strong>{{ labelState(check.state) }} · {{ ruleLabels[check.type] || reasonLabel(check.type) }}</strong>
          <p>{{ reasonLabel(check.explanation || check.message || '暂无说明') }}</p>
          <p>规则 {{ check.ruleKey ?? '未提供' }} / 版本 {{ check.ruleVersion ?? '未知' }} · 类型 {{ check.type ?? '未知' }} · 状态 {{ check.state ?? '未知' }}</p>
          <p v-if="check.scope != null">范围 {{ check.scope }}</p>
          <p v-if="check.sourceScope != null">来源范围 {{ check.sourceScope }}</p>
          <div v-for="(source, j) in check.sources || []" :key="j" class="board-source"><a v-if="safeSource(source.url)" :href="safeSource(source.url)" target="_blank" rel="noopener noreferrer">{{ source.title || '规则来源' }}</a><span v-else>{{ source.title || '未提供来源链接' }}</span><span> · {{ sourceDate(source) }} · {{ source.verificationStatus || '待核实' }}</span></div>
          <details><summary>完整原检查记录</summary><pre>{{ JSON.stringify(check, null, 2) }}</pre></details>
        </article>
      </details>
    </div>
  </div>
</template>
