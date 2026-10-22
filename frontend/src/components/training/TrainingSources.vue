<script setup>
import { label } from '../../training/display'
defineProps({ sources: { type: Array, default: () => [] } })
</script>
<template>
  <div class="training-sources">
    <p v-if="!sources.length" class="muted">暂无可查看的教学来源，不补造依据。</p>
    <details v-for="(source,index) in sources" :key="`${source.id}-${source.version}-${index}`">
      <summary>查看来源：{{ source.title || source.id }} · 版本 {{ source.version ?? '未知' }}</summary>
      <p>状态：{{ label(source.status) }} · 本站文字资料</p>
      <blockquote>{{ source.excerpt || '未提供摘录' }}</blockquote>
      <p>原文定位（说明）：{{ source.locator || '未提供定位' }}</p>
      <p>{{ source.limitations }}</p>
      <p class="muted">文字资料不代表视频理解、专业认证或动作能力认证。</p>
    </details>
  </div>
</template>
