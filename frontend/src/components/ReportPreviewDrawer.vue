<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { api } from '../api'
import { parseReportSections } from '../utils/parseReportSections'
import { writeClipboard } from '../utils/clipboard'
import { buildReportFullText } from '../utils/reportText'

const props = defineProps({
  open: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  title: { type: String, default: '' },
  reportId: { type: Number, default: null },
  reportKind: { type: String, default: 'daily' },
  content: { type: String, default: '' },
  tomorrowPlan: { type: String, default: '' },
  excelPath: { type: String, default: '' },
})

const emit = defineEmits(['close', 'download-excel', 'copied', 'saved'])

const editing = ref(false)
const saving = ref(false)
const editContent = ref('')
const editTomorrow = ref('')
const savedContent = ref('')
const savedTomorrow = ref('')

const copiedAll = ref(false)
const copiedSectionId = ref(null)
const savedFlash = ref(false)
const saveErrorFlash = ref(false)
const contentTextarea = ref(null)
const tomorrowTextarea = ref(null)
let flashTimer = null
let suppressPendingSave = false
/** Set on textarea blur; cleared after auto-save or explicit save. */
const pendingAutoSaveAfterClick = ref(false)

const isDaily = computed(() => props.reportKind === 'daily')

const activeFullText = computed(() =>
  isDaily.value
    ? buildReportFullText(editContent.value, editTomorrow.value)
    : (editContent.value || '').trim(),
)

const sections = computed(() => parseReportSections(activeFullText.value))
const hasStructuredSections = computed(
  () => sections.value.length > 0 && sections.value.some((s) => s.title),
)

const dirty = computed(() => {
  if (isDaily.value) {
    return editContent.value !== savedContent.value || editTomorrow.value !== savedTomorrow.value
  }
  return editContent.value !== savedContent.value
})

function resetDraft() {
  editContent.value = props.content || ''
  editTomorrow.value = props.tomorrowPlan || ''
  savedContent.value = editContent.value
  savedTomorrow.value = editTomorrow.value
  editing.value = false
  copiedAll.value = false
  copiedSectionId.value = null
  savedFlash.value = false
  saveErrorFlash.value = false
  pendingAutoSaveAfterClick.value = false
}

watch(
  () => [props.open, props.content, props.tomorrowPlan],
  ([open]) => {
    if (open) resetDraft()
  },
)

function flashCopied(kind, sectionId) {
  if (kind === 'all') {
    copiedAll.value = true
    copiedSectionId.value = null
  } else {
    copiedSectionId.value = sectionId
    copiedAll.value = false
  }
  emit('copied')
  clearFlashTimer()
  flashTimer = setTimeout(() => {
    copiedAll.value = false
    copiedSectionId.value = null
  }, 2500)
}

function flashSaved(report) {
  saveErrorFlash.value = false
  savedFlash.value = true
  emit('saved', report)
  clearFlashTimer()
  flashTimer = setTimeout(() => {
    savedFlash.value = false
  }, 2500)
}

function flashSaveError(message) {
  savedFlash.value = false
  saveErrorFlash.value = true
  clearFlashTimer()
  flashTimer = setTimeout(() => {
    saveErrorFlash.value = false
  }, 3000)
  if (message) console.warn(message)
}

function clearFlashTimer() {
  if (flashTimer) {
    clearTimeout(flashTimer)
    flashTimer = null
  }
}

async function tryClose() {
  pendingAutoSaveAfterClick.value = false
  if (dirty.value) {
    await persistChanges({ exitEdit: true })
  }
  if (dirty.value) {
    if (!window.confirm('有未保存的修改，确定关闭吗？')) return
  }
  emit('close')
}

async function onBackdrop(e) {
  if (e.target === e.currentTarget) await tryClose()
}

function onKeydown(e) {
  if (e.key === 'Escape') tryClose()
}

function isTomorrowSection(section) {
  const t = section?.title
  return t === '【明日计划】' || t === '明日计划'
}

async function enterEditMode(focusTarget = 'content') {
  if (props.loading || editing.value) return
  pendingAutoSaveAfterClick.value = false
  editing.value = true
  await nextTick()
  const el = focusTarget === 'tomorrow' ? tomorrowTextarea.value : contentTextarea.value
  el?.focus()
}

function onEditBlur(event) {
  if (suppressPendingSave) return
  const next = event.relatedTarget instanceof Element ? event.relatedTarget : null
  if (next?.classList.contains('report-edit-area')) {
    pendingAutoSaveAfterClick.value = false
    return
  }
  if (next?.closest('.report-drawer__toolbar')) {
    pendingAutoSaveAfterClick.value = false
    return
  }
  pendingAutoSaveAfterClick.value = editing.value && dirty.value
}

function onDrawerClick(event) {
  if (!pendingAutoSaveAfterClick.value || !dirty.value || saving.value || !editing.value) return
  const target = event.target
  if (!(target instanceof Element)) return
  if (target.closest('.report-edit-area')) return
  if (target.closest('.report-drawer__toolbar')) return
  pendingAutoSaveAfterClick.value = false
  void persistChanges({ exitEdit: false })
}

async function finishEdit() {
  suppressPendingSave = true
  pendingAutoSaveAfterClick.value = false
  try {
    if (dirty.value) {
      await persistChanges({ exitEdit: true })
    } else {
      editing.value = false
    }
  } finally {
    await nextTick()
    suppressPendingSave = false
  }
}

function onSectionDblClick(section) {
  enterEditMode(isTomorrowSection(section) ? 'tomorrow' : 'content')
}

function onPlainBodyDblClick() {
  enterEditMode('content')
}

async function copyAll() {
  await writeClipboard(activeFullText.value || '')
  flashCopied('all')
}

async function copySection(section) {
  const body = section.body || ''
  if (!body.trim()) return
  await writeClipboard(body)
  flashCopied('section', section.id)
}

async function persistChanges({ exitEdit = false, manual = false } = {}) {
  if (!props.reportId || saving.value || !dirty.value) {
    if (exitEdit) editing.value = false
    return
  }
  saving.value = true
  try {
    let updated
    if (isDaily.value) {
      updated = await api.reports.updateDaily(props.reportId, {
        content: editContent.value,
        tomorrowPlan: editTomorrow.value,
      })
    } else {
      updated = await api.reports.updatePeriod(props.reportId, {
        content: editContent.value,
      })
    }
    editContent.value = updated.content || ''
    if (isDaily.value) editTomorrow.value = updated.tomorrowPlan || ''
    savedContent.value = editContent.value
    savedTomorrow.value = editTomorrow.value
    if (exitEdit) editing.value = false
    pendingAutoSaveAfterClick.value = false
    flashSaved(updated)
  } catch (e) {
    const msg = e.message || '保存失败'
    if (manual) window.alert(msg)
    else flashSaveError(msg)
  } finally {
    saving.value = false
  }
}

async function save() {
  suppressPendingSave = true
  pendingAutoSaveAfterClick.value = false
  try {
    await persistChanges({ exitEdit: true, manual: true })
  } finally {
    await nextTick()
    suppressPendingSave = false
  }
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
          <aside
            v-if="open"
            class="report-drawer"
            role="dialog"
            aria-modal="true"
            :aria-label="title"
            @click.capture="onDrawerClick"
          >
            <header class="report-drawer__head">
              <div>
                <p class="report-drawer__eyebrow">报告预览</p>
                <h2 class="report-drawer__title">{{ title }}</h2>
              </div>
              <button type="button" class="btn btn-ghost btn-sm" aria-label="关闭" @click="tryClose">关闭</button>
            </header>

            <div class="report-drawer__toolbar">
              <button type="button" class="btn" :disabled="loading" @click="copyAll">复制全文</button>
              <span v-if="copiedAll" class="report-drawer__toast" role="status">已复制</span>
              <span v-if="savedFlash" class="report-drawer__toast" role="status">已保存</span>
              <span v-if="saveErrorFlash" class="report-drawer__toast report-drawer__toast--err" role="status">保存失败</span>
              <span v-if="dirty" class="report-drawer__dirty">未保存</span>
              <button
                v-if="editing"
                type="button"
                class="btn btn-secondary btn-sm"
                :disabled="loading || saving"
                @click="finishEdit"
              >
                完成编辑
              </button>
              <button
                v-else
                type="button"
                class="btn btn-ghost btn-sm"
                :disabled="loading || saving"
                @click="enterEditMode('content')"
              >
                编辑
              </button>
              <button
                type="button"
                class="btn btn-sm"
                :disabled="loading || saving || !dirty"
                @click="save"
              >
                {{ saving ? '保存中…' : '保存' }}
              </button>
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
              <template v-else>
                <p v-if="!editing" class="report-drawer__hint">双击正文可编辑；改完后点击输入框外任意处即自动保存（离开输入框的那次点击即可）</p>
                <template v-if="editing">
                  <label class="field-label">正文</label>
                  <textarea
                    ref="contentTextarea"
                    v-model="editContent"
                    class="report-edit-area"
                    rows="16"
                    spellcheck="false"
                    @blur="onEditBlur"
                  />
                  <template v-if="isDaily">
                    <label class="field-label" style="margin-top: 0.85rem">明日计划</label>
                    <textarea
                      ref="tomorrowTextarea"
                      v-model="editTomorrow"
                      class="report-edit-area"
                      rows="6"
                      spellcheck="false"
                      @blur="onEditBlur"
                    />
                  </template>
                </template>
                <template v-else-if="hasStructuredSections">
                  <section v-for="section in sections" :key="section.id" class="report-section">
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
                    <pre
                      class="report-section__text report-body--dblclick"
                      @dblclick="onSectionDblClick(section)"
                    >{{ section.body || '（无内容）' }}</pre>
                  </section>
                </template>
                <pre
                  v-else
                  class="report-drawer__text report-body--dblclick"
                  tabindex="0"
                  @dblclick="onPlainBodyDblClick"
                >{{ activeFullText || '（暂无正文内容）' }}</pre>
              </template>
            </div>
          </aside>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>
