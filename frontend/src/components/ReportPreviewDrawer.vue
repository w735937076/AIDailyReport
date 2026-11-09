<script setup>
import { computed, ref, watch } from 'vue'
import { parseReportSections } from '../utils/parseReportSections'
import { writeClipboard } from '../utils/clipboard'

const props = defineProps({
  open: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  title: { type: String, default: '' },
  fullText: { type: String, default: '' },
  excelPath: { type: String, default: '' },
})

const emit = defineEmits(['close', 'download-excel', 'copied'])

const copiedAll = ref(false)
const copiedSectionId = ref(null)
let copiedTimer = null

const sections = computed(() => parseReportSections(props.fullText))
const hasStructuredSections = computed(
  () => sections.value.length > 0 && sections.value.some((s) => s.title),
)

watch(
  () => props.open,
  (v) => {
    if (!v) {
      copiedAll.value = false
      copiedSectionId.value = null
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

function flashCopied(kind, sectionId) {
  if (kind === 'all') {
    copiedAll.value = true
    copiedSectionId.value = null
  } else {
    copiedSectionId.value = sectionId
    copiedAll.value = false
  }
  emit('copied')
  if (copiedTimer) clearTimeout(copiedTimer)
  copiedTimer = setTimeout(() => {
    copiedAll.value = false
    copiedSectionId.value = null
  }, 2500)
}

async function copyAll() {
  await writeClipboard(props.fullText || '')
  flashCopied('all')
}

async function copySection(section) {
  const body = section.body || ''
  if (!body.trim()) return
  await writeClipboard(body)
  flashCopied('section', section.id)
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
              <span v-if="copiedAll" class="report-drawer__toast" role="status">已复制</span>
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
              <template v-else-if="hasStructuredSections">
                <section
                  v-for="section in sections"
                  :key="section.id"
                  class="report-section"
                >
                  <header v-if="section.title" class="report-section__head">
                    <h3 class="report-section__title">{{ section.title }}</h3>
                    <button
                      type="button"
                      class="btn btn-ghost btn-sm report-section__copy"
                      :disabled="!section.body.trim()"
                      @click="copySection(section)"
                    >
                      {{ copiedSectionId === section.id ? '已复制' : '复制' }}
                    </button>
                  </header>
                  <pre class="report-section__text">{{ section.body || '（无内容）' }}</pre>
                </section>
              </template>
              <pre v-else class="report-drawer__text" tabindex="0">{{ fullText || '（暂无正文内容）' }}</pre>
            </div>
          </aside>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>
