<script setup>
import { computed, onBeforeUnmount, watch, ref } from 'vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: 'AI 正在处理中'
  },
  subtitle: {
    type: String,
    default: '请稍候，系统正在整理内容并生成结果。'
  },
  steps: {
    type: Array,
    default: () => ['整理输入', '分析需求', '生成内容', '校正格式', '输出结果']
  }
})

const stageIndex = ref(0)
const progressValue = ref(12)
let stageTimer = null
let progressTimer = null

const displayedProgress = computed(() => Math.round(progressValue.value))

const activeStepIndex = computed(() => {
  const stepCount = Math.max(props.steps.length, 1)
  const ratio = displayedProgress.value / 100
  return Math.min(stepCount - 1, Math.floor(ratio * stepCount))
})

function clearTimers() {
  if (stageTimer) {
    clearInterval(stageTimer)
    stageTimer = null
  }
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
}

function nextProgressIncrement(current) {
  if (current < 28) return 3.2
  if (current < 48) return 2.4
  if (current < 68) return 1.5
  if (current < 82) return 0.9
  if (current < 90) return 0.45
  if (current < 95) return 0.18
  return 0
}

function syncStageWithProgress() {
  stageIndex.value = activeStepIndex.value
}

function startLoadingMotion() {
  clearTimers()
  stageIndex.value = 0
  progressValue.value = 12
  if (!props.visible) return

  progressTimer = window.setInterval(() => {
    const increment = nextProgressIncrement(progressValue.value)
    if (increment <= 0) {
      progressValue.value = Math.min(progressValue.value, 95)
      return
    }
    progressValue.value = Math.min(95, progressValue.value + increment)
    syncStageWithProgress()
  }, 520)

  stageTimer = window.setInterval(() => {
    syncStageWithProgress()
  }, 180)
}

watch(
  () => props.visible,
  (visible) => {
    document.body.style.overflow = visible ? 'hidden' : ''
    if (visible) {
      startLoadingMotion()
    } else {
      clearTimers()
      stageIndex.value = 0
      progressValue.value = 12
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  clearTimers()
  document.body.style.overflow = ''
})
</script>

<template>
  <transition name="ai-overlay-fade">
    <div v-if="visible" class="ai-overlay" role="status" aria-live="polite" aria-busy="true">
      <div class="ai-overlay-card">
        <div class="ai-spinner" aria-hidden="true">
          <span class="ai-spinner-ring"></span>
          <span class="ai-spinner-core"></span>
        </div>

        <div class="ai-overlay-copy">
          <p class="ai-eyebrow">AI ASSIST</p>
          <h3>{{ title }}</h3>
          <p class="ai-subtitle">{{ subtitle }}</p>
        </div>

        <div class="ai-progress">
          <div class="ai-progress-track">
            <div class="ai-progress-fill" :style="{ width: `${displayedProgress}%` }"></div>
          </div>
          <div class="ai-progress-labels">
            <span>处理中</span>
            <span>{{ displayedProgress }}%</span>
          </div>
        </div>

        <div class="ai-steps">
          <div
            v-for="(step, index) in steps"
            :key="step"
            class="ai-step"
            :class="{
              active: index === activeStepIndex,
              passed: index < stageIndex
            }"
          >
            <span class="ai-step-dot"></span>
            <span class="ai-step-text">{{ step }}</span>
          </div>
        </div>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.ai-overlay {
  position: fixed;
  inset: 0;
  z-index: 1200;
  background: rgba(15, 23, 42, 0.44);
  backdrop-filter: blur(12px);
  display: grid;
  place-items: center;
  padding: 20px;
}

.ai-overlay-card {
  width: min(460px, 100%);
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(248, 250, 252, 0.95));
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.18);
  padding: 24px 22px 20px;
  display: grid;
  gap: 16px;
}

.ai-spinner {
  position: relative;
  width: 58px;
  height: 58px;
  margin: 0 auto;
}

.ai-spinner-ring,
.ai-spinner-core {
  position: absolute;
  inset: 0;
  border-radius: 999px;
}

.ai-spinner-ring {
  border: 3px solid rgba(15, 23, 42, 0.1);
  border-top-color: rgba(15, 23, 42, 0.88);
  animation: ai-spin 1s linear infinite;
}

.ai-spinner-core {
  inset: 12px;
  background: radial-gradient(circle at 30% 30%, rgba(15, 23, 42, 0.14), rgba(15, 23, 42, 0.02));
  animation: ai-pulse 1.6s ease-in-out infinite;
}

.ai-overlay-copy {
  text-align: center;
}

.ai-eyebrow {
  margin: 0 0 8px;
  font-size: 11px;
  letter-spacing: 0.18em;
  color: var(--text-muted);
}

.ai-overlay-copy h3 {
  margin: 0;
  font-size: 22px;
  color: var(--text);
}

.ai-subtitle {
  margin: 8px 0 0;
  color: var(--text-muted);
  line-height: 1.7;
}

.ai-progress {
  display: grid;
  gap: 8px;
}

.ai-progress-track {
  height: 8px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(148, 163, 184, 0.22);
}

.ai-progress-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #0f172a, #334155, #64748b);
  transition: width 0.45s ease;
}

.ai-progress-labels {
  display: flex;
  justify-content: space-between;
  color: var(--text-muted);
  font-size: 12px;
}

.ai-steps {
  display: grid;
  gap: 10px;
}

.ai-step {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(148, 163, 184, 0.16);
  color: var(--text-muted);
  transition: all 0.25s ease;
}

.ai-step.active {
  border-color: rgba(15, 23, 42, 0.18);
  background: rgba(241, 245, 249, 0.96);
  color: var(--text);
  transform: translateY(-1px);
}

.ai-step.passed {
  color: var(--text-soft);
}

.ai-step-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.5);
  flex: 0 0 auto;
}

.ai-step.active .ai-step-dot {
  background: #0f172a;
  box-shadow: 0 0 0 6px rgba(15, 23, 42, 0.08);
}

.ai-step.passed .ai-step-dot {
  background: #475569;
}

.ai-step-text {
  line-height: 1.4;
}

.ai-overlay-fade-enter-active,
.ai-overlay-fade-leave-active {
  transition: opacity 0.2s ease;
}

.ai-overlay-fade-enter-from,
.ai-overlay-fade-leave-to {
  opacity: 0;
}

@keyframes ai-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes ai-pulse {
  0%,
  100% {
    transform: scale(0.92);
    opacity: 0.7;
  }

  50% {
    transform: scale(1);
    opacity: 1;
  }
}

@media (max-width: 640px) {
  .ai-overlay-card {
    padding: 22px 16px 18px;
  }

  .ai-overlay-copy h3 {
    font-size: 20px;
  }
}
</style>
