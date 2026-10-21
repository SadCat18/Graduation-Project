<script setup>
import { label, displayText } from '../../training/display'
defineProps({ value: Object, tasks: Array })
</script>
<template>
  <section v-if="value" aria-label="安排前后差异">
    <p>{{ displayText(value.diff) }}</p>
    <div class="training-table"><table><caption>调整前后安排（只读预览）</caption><thead><tr><th>阶段</th><th>计划状态</th><th>任务与日期、分钟</th></tr></thead><tbody><tr v-for="(snapshot,key) in { before: value.before, after: value.after }" :key="key"><th>{{ key === 'before' ? '调整前' : '调整后' }}</th><td>{{ label(snapshot?.status) }}</td><td><div v-for="task in snapshot?.tasks || []" :key="task.id"><strong>{{ tasks?.find(item => item.id === task.id)?.title || '关联任务' }}</strong> · {{ label(task.status) }} · {{ task.unlocked ? '已解锁' : '尚未解锁' }} · {{ task.date }}<p v-for="segment in task.segments || []" :key="segment.id">{{ segment.date }} · {{ segment.minutes }} 分钟 · {{ label(segment.status) }}</p></div></td></tr></tbody></table></div>
    <p>历史练习保留。分段分钟是剩余安排，不是已练分钟，也不增加任务完成分母。</p>
  </section>
</template>
