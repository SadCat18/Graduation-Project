<script setup>
import TrainingSources from './TrainingSources.vue'
import { displayText, label } from '../../training/display'
defineProps({ value: Object })
</script>
<template>
  <section v-if="value" aria-label="本次训练回顾">
    <h3>训练回顾</h3><p v-if="value.contentAvailable === false" class="training-warning">当前来源已停用或有安全纠正，历史记录快照保留，请勿继续采用失效指导。</p>
    <p v-for="(alert,index) in value.safetyAlerts || value.record?.safetyAlerts || []" :key="index" class="training-warning">安全提醒：{{ displayText(alert) }}</p>
    <p>练习日期 {{ value.record?.practiceDate }} · 实际 {{ value.record?.actualMinutes ?? '—' }} 分钟 · {{ label(value.record?.completion) }} · 主观 {{ label(value.record?.difficulty) }}</p>
    <p>动作自评：{{ label(value.record?.selfAssessment || value.review?.selfAssessment) }}。完成情况不等于掌握。</p>
    <p>原始记录时间 {{ value.record?.createdAt }}；任务快照按当时版本保存，记录不可直接修改或删除。</p>
    <p>{{ value.record?.note }}</p><p v-for="answer in value.record?.selfChecks || []" :key="answer.questionId">{{ value.record?.taskSnapshot?.selfChecks?.find(item => item.id === answer.questionId)?.question || answer.questionId }}：{{ label(answer.answer) }}</p>
    <h4>固定指导</h4><p>{{ value.review?.message || value.record?.review?.message || '暂无额外指导' }}</p><p>{{ value.review?.limitations || value.record?.review?.limitations }}</p>
    <TrainingSources :sources="value.review?.sources || value.record?.review?.sources || value.record?.taskSnapshot?.sources || []" />
    <details v-if="value.record?.taskSnapshot"><summary>查看记录时教学快照</summary><h4>{{ value.record.taskSnapshot.title }}</h4><ol><li v-for="(step,index) in value.record.taskSnapshot.steps || []" :key="index">{{ step }}</li></ol><TrainingSources :sources="value.record.taskSnapshot.sources || []" /></details>
  </section>
</template>
