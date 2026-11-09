<script setup>
import { computed } from 'vue'

const props = defineProps({
  page: { type: Number, required: true },
  size: { type: Number, required: true },
  total: { type: Number, required: true },
  sizeOptions: { type: Array, default: () => [10, 20, 50] },
})

const emit = defineEmits(['update:page', 'update:size'])

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
const rangeStart = computed(() => (props.total === 0 ? 0 : (props.page - 1) * props.size + 1))
const rangeEnd = computed(() => Math.min(props.total, props.page * props.size))

function setPage(p) {
  const next = Math.min(totalPages.value, Math.max(1, p))
  if (next !== props.page) emit('update:page', next)
}

function onSizeChange(e) {
  emit('update:size', Number(e.target.value))
}

const pageButtons = computed(() => {
  const tp = totalPages.value
  const cur = props.page
  const pages = []
  const add = (n) => pages.push(n)
  add(1)
  if (cur > 3) pages.push('…')
  for (let i = Math.max(2, cur - 1); i <= Math.min(tp - 1, cur + 1); i++) add(i)
  if (cur < tp - 2) pages.push('…')
  if (tp > 1) add(tp)
  return [...new Set(pages)]
})
</script>

<template>
  <div class="pager" v-if="total > 0 || page > 1">
    <div class="pager-meta">
      共 <strong>{{ total }}</strong> 条 · 第 {{ rangeStart }}–{{ rangeEnd }} 条
    </div>
    <div class="pager-controls">
      <select class="select-sm" :value="size" @change="onSizeChange" aria-label="每页条数">
        <option v-for="opt in sizeOptions" :key="opt" :value="opt">{{ opt }} 条/页</option>
      </select>
      <button type="button" class="btn-ghost btn-sm" :disabled="page <= 1" @click="setPage(page - 1)">上一页</button>
      <template v-for="(p, idx) in pageButtons" :key="idx + '-' + p">
        <span v-if="p === '…'" class="pager-ellipsis">…</span>
        <button
          v-else
          type="button"
          class="btn-ghost btn-sm"
          :class="{ active: p === page }"
          @click="setPage(p)"
        >{{ p }}</button>
      </template>
      <button type="button" class="btn-ghost btn-sm" :disabled="page >= totalPages" @click="setPage(page + 1)">下一页</button>
    </div>
  </div>
</template>
