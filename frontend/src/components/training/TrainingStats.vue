<script setup>
import { percentage, label } from '../../training/display'
defineProps({ stats: Object })
</script>
<template>
  <section aria-label="三类训练统计">
    <h3>训练统计：三种口径分别看</h3>
    <p v-if="!stats">统计资料尚未读取。</p>
    <template v-else>
      <div class="training-stats"><p><strong>{{ stats.practiceMinutes ?? '—' }} 分钟</strong><br />练习量 · {{ stats.practiceCount ?? '—' }} 条记录</p><p><strong>{{ percentage(stats.completionRate) }}</strong><br />根任务完成 {{ stats.completedTasks ?? '—' }} / {{ stats.totalTasks ?? '—' }}</p><div><strong>动作自评</strong><p v-for="(count,state) in stats.selfAssessmentSummary || {}" :key="state">{{ label(state) }}：{{ count }}</p></div></div>
      <p>{{ stats.definition }}</p><p>练习分钟与完成率不表示动作能力；用户自评不是专业认证。分段不额外增加根任务完成数。</p>
    </template>
  </section>
</template>
