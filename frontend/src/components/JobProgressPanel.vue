<script setup>
import { computed } from 'vue'

const props = defineProps({
  job: { type: Object, default: null },
  visible: { type: Boolean, default: false },
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
</script>

<template>
  <Transition name="job-panel">
    <div v-if="visible && job" class="job-panel" :class="`job-panel--${tone}`" role="status" aria-live="polite">
      <div class="job-panel__head">
        <span class="spinner spinner--accent" v-if="job.status === 'RUNNING' || job.status === 'PENDING'" />
        <span class="job-panel__msg">{{ job.message || '处理中…' }}</span>
        <span v-if="job.status === 'FAILED' && job.error" class="job-panel__err">失败：{{ job.error }}</span>
      </div>
      <div class="job-panel__track" aria-hidden="true">
        <div class="job-panel__fill" :style="{ width: barWidth }" />
      </div>
    </div>
  </Transition>
</template>
