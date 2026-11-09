<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  title: { type: String, default: '' },
  fullText: { type: String, default: '' },
  excelPath: { type: String, default: '' },
})

const emit = defineEmits(['close', 'download-excel', 'copied'])

const copied = ref(false)
let copiedTimer = null

const displayText = computed(() => props.fullText || '（暂无正文内容）')

watch(
  () => props.open,
  (v) => {
    if (!v) {
      copied.value = false
      if (copiedTimer) clearTimeout(copiedTimer)
    }
  },
)

function onBackdrop(e) {
  if (e.target === e.currentTarget) emit('close')
}

function onKeydown(e) {
  if (e.key === 'Escape') emit('close')
}

async function copyAll() {
  const text = props.fullText || ''
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    const el = document.createElement('textarea')
    el.value = text
    el.style.position = 'fixed'
    el.style.opacity = '0'
    document.body.appendChild(el)
    el.select()
    document.execCommand('copy')
    document.body.removeChild(el)
  }
  copied.value = true
  emit('copied')
  if (copiedTimer) clearTimeout(copiedTimer)
  copiedTimer = setTimeout(() => {
    copied.value = false
  }, 2500)
}
</script>

<template>
  <Teleport to="body">
    <Transition name="drawer-backdrop">
      <div
        v-if="open"
        class="report-drawer-backdrop"
        role="presentation"
        @click="onBackdrop"
        @keydown="onKeydown"
      >
        <Transition name="drawer-panel">
          <aside v-if="open" class="report-drawer" role="dialog" aria-modal="true" :aria-label="title">
            <header class="report-drawer__head">
              <div>
                <p class="report-drawer__eyebrow">报告预览</p>
                <h2 class="report-drawer__title">{{ title }}</h2>
              </div>
              <button type="button" class="btn btn-ghost btn-sm" aria-label="关闭" @click="emit('close')">关闭</button>
            </header>

            <div class="report-drawer__toolbar">
              <button type="button" class="btn" :disabled="loading" @click="copyAll">复制全文</button>
              <span v-if="copied" class="report-drawer__toast" role="status">已复制</span>
              <button
                v-if="excelPath"
                type="button"
                class="btn btn-ghost btn-sm"
                @click="emit('download-excel')"
              >
                下载 Excel
              </button>
            </div>

            <div class="report-drawer__body">
              <div v-if="loading" class="loading-block">
                <span class="spinner spinner--accent" />
                <span>加载报告内容…</span>
              </div>
              <pre v-else class="report-drawer__text" tabindex="0">{{ displayText }}</pre>
            </div>
          </aside>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>
