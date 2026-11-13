<script setup>
import { computed } from 'vue'

const props = defineProps({
  job: { type: Object, default: null },
  visible: { type: Boolean, default: false },
  prominent: { type: Boolean, default: false },
  subtitle: { type: String, default: '' },
})

const percent = computed(() => {
  const p = props.job?.percent
  if (typeof p === 'number' && p >= 0) return Math.min(100, p)
  return props.job?.status === 'COMPLETED' ? 100 : 8
})

const barWidth = computed(() => `${percent.value}%`)

const tone = computed(() => {
  const s = props.job?.status
  if (s === 'FAILED') return 'err'
  if (s === 'COMPLETED') return 'ok'
  return 'run'
})

const showPulse = computed(() => {
  const s = props.job?.status
  return s === 'RUNNING' || s === 'PENDING'
})
</script>

<template>
  <Transition name="job-panel">
    <div
      v-if="visible && job"
      class="job-panel"
      :class="[
        `job-panel--${tone}`,
        { 'job-panel--prominent': prominent, 'job-panel--pulse': showPulse && prominent },
      ]"
      role="status"
      aria-live="polite"
    >
      <div v-if="prominent" class="job-panel__top">
        <div class="job-panel__orb" aria-hidden="true">
          <span class="job-panel__orb-core" />
          <span v-if="showPulse" class="job-panel__orb-ring" />
        </div>
        <div class="job-panel__titles">
          <p v-if="subtitle" class="job-panel__sub">{{ subtitle }}</p>
          <p class="job-panel__msg job-panel__msg--lead">{{ job.message || '处理中…' }}</p>
        </div>
        <span class="job-panel__pct" aria-label="进度百分比">{{ percent }}%</span>
      </div>
      <div v-else class="job-panel__head">
        <span class="spinner spinner--accent" v-if="showPulse" />
        <span class="job-panel__msg">{{ job.message || '处理中…' }}</span>
        <span v-if="job.status === 'FAILED' && job.error" class="job-panel__err">失败：{{ job.error }}</span>
      </div>
      <div class="job-panel__track" aria-hidden="true">
        <div class="job-panel__fill" :style="{ width: barWidth }">
          <span v-if="prominent && showPulse" class="job-panel__shimmer" />
        </div>
      </div>
      <p v-if="prominent && job.status === 'FAILED' && job.error" class="job-panel__err job-panel__err--block">
        {{ job.error }}
      </p>
      <p v-else-if="prominent && job.status === 'COMPLETED'" class="job-panel__ok-hint">任务已完成</p>
    </div>
  </Transition>
</template>
