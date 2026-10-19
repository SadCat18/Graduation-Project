<script setup>
import AppIcon from '../../../components/AppIcon.vue'

defineProps({
  icon: { type: String, default: 'stats' },
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '当前范围内没有可展示的数据。' },
  actionLabel: { type: String, default: '' }
})

defineEmits(['action'])
</script>

<template>
  <div class="admin-empty-state" role="status">
    <span class="admin-empty-icon"><AppIcon :name="icon" :size="22" /></span>
    <strong>{{ title }}</strong>
    <p>{{ description }}</p>
    <button v-if="actionLabel" type="button" class="btn-soft" @click="$emit('action')">
      <AppIcon name="refresh" :size="14" />
      {{ actionLabel }}
    </button>
  </div>
</template>

<style scoped>
.admin-empty-state {
  min-height: 180px;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 8px;
  padding: 20px;
  color: #687067;
  text-align: center;
}

.admin-empty-icon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: #eef2ef;
  color: #687067;
}

.admin-empty-state strong { color: #26312c; }
.admin-empty-state p { margin: 0; max-width: 280px; font-size: 13px; }
</style>
