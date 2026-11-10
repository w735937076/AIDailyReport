<script setup>
const props = defineProps({ status: { type: String, default: '' } })

const labels = {
  EXPORTED: '已导出',
  GENERATED: '已生成',
  DRAFT: '草稿',
  RUNNING: '进行中',
  COMPLETED: '已完成',
  PENDING: '待处理',
  FAILED: '失败',
  IDLE: '空闲',
}

const cls = () => {
  const s = (props.status || '').toLowerCase()
  if (['exported', 'completed'].includes(s)) return 'exported'
  if (s === 'running') return 'running'
  if (s === 'failed') return 'failed'
  if (s === 'generated') return 'generated'
  if (s === 'draft') return 'draft'
  return 'draft'
}
</script>

<template>
  <span class="chip" :class="cls()">{{ labels[status] || status || '—' }}</span>
</template>
