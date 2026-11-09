<script setup>
const props = defineProps({ status: { type: String, default: '' } })

const labels = {
  EXPORTED: '已导出',
  GENERATED: '已生成',
  DRAFT: '草稿',
  RUNNING: '进行中',
  COMPLETED: '完成',
  FAILED: '失败',
  IDLE: '空闲',
}
</script>

<template>
  <span class="chip" :class="(status || '').toLowerCase()">{{ labels[status] || status || '—' }}</span>
</template>
