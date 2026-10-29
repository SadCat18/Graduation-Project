<script setup>
import { computed } from 'vue'
import { checkGroups, isBlockingCheck } from '../../board/presentation'
import { publicSourceTitle, labelState, reasonLabel, ruleLabels, safeSource, sourceDate } from '../../board/state'
const props = defineProps({ checks: { type: Array, default: () => [] } })
const groups = computed(() => checkGroups(props.checks))
</script>
<template>
  <div class="board-checks">
    <p v-if="!checks.length">暂无完整检查依据。</p>
    <div v-for="(group, index) in groups" :key="index">
      <details>
        <summary :class="{ 'board-error': isBlockingCheck(group), 'board-notice': group.state === 'ADVICE' }">{{ labelState(group.state) }}：{{ reasonLabel(group.explanation || '暂无说明') }}（{{ group.checks.length }} 条依据）</summary>
        <article v-for="(check, i) in group.checks" :key="i" class="board-item">
          <strong>{{ labelState(check.state) }} · {{ ruleLabels[check.type] || reasonLabel(check.type) }}</strong>
          <p>{{ reasonLabel(check.explanation || check.message || '暂无说明') }}</p>
          <div v-for="(source, j) in check.sources || []" :key="j" class="board-source"><a v-if="safeSource(source.url)" :href="safeSource(source.url)" target="_blank" rel="noopener noreferrer">{{ publicSourceTitle(source.title, '规则来源') }}</a><span v-else>{{ publicSourceTitle(source.title, '未提供来源链接') }}</span><span> · {{ sourceDate(source) }} · {{ labelState(source.verificationStatus) }}</span></div>
        </article>
      </details>
    </div>
  </div>
</template>
