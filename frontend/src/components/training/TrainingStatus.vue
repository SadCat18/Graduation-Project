<script setup>
import { displayText } from '../../training/display'
defineProps({ work: Object, blocked: Boolean })
const emit = defineEmits(['retry','refresh'])
</script>
<template>
  <section aria-label="操作状态" aria-live="polite">
    <p v-if="work.notice.value" role="status">{{ work.notice.value }}</p>
    <p v-for="(message,key) in work.errors" v-show="message" :key="key" role="alert">{{ message }}</p>
    <p v-if="work.writing.value" role="status">正在等待服务端确认，请勿重复提交。</p>
    <div v-if="work.pending.value" class="training-warning">
      <strong>{{ work.pending.value.label }}：结果尚待核对</strong>
      <p>同一次操作保留原内容，请先恢复该操作，再开始新的提交。修改表单不会修改这次重试内容。</p>
      <button type="button" :disabled="work.writing.value || blocked || !work.ready.value" @click="emit('retry')">重试原操作</button>
    </div>
    <p v-if="work.failure.value?.status === 409">请刷新后检查最新版本与允许操作，不会自动接受或重发。</p>
    <div v-if="work.failure.value?.data?.legacyConflict">
      <p>旧训练计划尚在进行中，请先到原训练入口明确退出，再重新建立计划。</p><RouterLink to="/skate">查看旧训练计划</RouterLink>
    </div>
    <button type="button" :disabled="work.writing.value || blocked" @click="emit('refresh')">刷新训练资料</button>
  </section>
</template>
