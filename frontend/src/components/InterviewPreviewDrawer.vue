<script setup>
import { computed, ref, watch } from 'vue'
import { api } from '../api'

const props = defineProps({
  open: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  title: { type: String, default: '' },
  reviewId: { type: Number, default: null },
  content: { type: Object, default: () => ({}) },
  excelPath: { type: String, default: '' },
})

const emit = defineEmits(['close', 'download-excel', 'saved'])

const editing = ref(false)
const saving = ref(false)
const fields = [
  { key: 'answerF3', label: '做得较好的方面 (F3)' },
  { key: 'answerF4', label: '成功经验 (F4)' },
  { key: 'answerF5', label: '需改善的方面 (F5)' },
  { key: 'answerF6', label: '困难与解决 (F6)' },
  { key: 'answerF8', label: '后续目标与计划 (F8)' },
  { key: 'answerF9', label: '意见建议 (F9)' },
]

const draft = ref({})
const saved = ref({})
const savedFlash = ref(false)
let flashTimer = null

const dirty = computed(() => fields.some((f) => (draft.value[f.key] || '') !== (saved.value[f.key] || '')))

function resetDraft() {
  const c = props.content || {}
  draft.value = {
    answerF3: c.answerF3 || '',
    answerF4: c.answerF4 || '',
    answerF5: c.answerF5 || '',
    answerF6: c.answerF6 || '',
    answerF8: c.answerF8 || '',
    answerF9: c.answerF9 || '无',
  }
  saved.value = { ...draft.value }
  editing.value = false
  savedFlash.value = false
}

watch(
  () => [props.open, props.content],
  ([open]) => {
    if (open) resetDraft()
  },
)

async function persistChanges({ exitEdit } = {}) {
  if (!dirty.value) {
    if (exitEdit) editing.value = false
    return
  }
  saving.value = true
  try {
    const updated = await api.interview.update(props.reviewId, { ...draft.value })
    const parsed = JSON.parse(updated.contentJson || '{}')
    draft.value = {
      answerF3: parsed.answerF3 || '',
      answerF4: parsed.answerF4 || '',
      answerF5: parsed.answerF5 || '',
      answerF6: parsed.answerF6 || '',
      answerF8: parsed.answerF8 || '',
      answerF9: parsed.answerF9 || '无',
    }
    saved.value = { ...draft.value }
    savedFlash.value = true
    emit('saved', updated)
    if (exitEdit) editing.value = false
    clearTimeout(flashTimer)
    flashTimer = setTimeout(() => {
      savedFlash.value = false
    }, 2000)
  } finally {
    saving.value = false
  }
}

async function tryClose() {
  if (dirty.value) await persistChanges({ exitEdit: true })
  emit('close')
}

function onDrawerClick(e) {
  if (!editing.value) return
  if (e.target.tagName === 'TEXTAREA') return
  persistChanges({ exitEdit: true })
}

function startEdit() {
  editing.value = true
}
</script>

<template>
  <Teleport to="body">
    <Transition name="drawer-backdrop">
      <div v-if="open" class="report-drawer-backdrop" role="presentation" @click="tryClose">
        <Transition name="drawer-panel">
          <aside
            v-if="open"
            class="report-drawer"
            role="dialog"
            aria-modal="true"
            :aria-label="title"
            @click="onDrawerClick"
          >
            <header class="report-drawer__head">
              <div>
                <p class="report-drawer__eyebrow">面谈预览</p>
                <h2 class="report-drawer__title">{{ title }}</h2>
              </div>
              <button type="button" class="btn btn-ghost btn-sm" aria-label="关闭" @click.stop="tryClose">关闭</button>
            </header>
            <div class="report-drawer__toolbar" @click.stop>
              <button type="button" class="btn btn-ghost btn-sm" :disabled="loading || saving" @click="startEdit">
                编辑
              </button>
              <button v-if="excelPath" type="button" class="btn btn-sm" @click="emit('download-excel')">下载 Excel</button>
              <span v-if="savedFlash" class="report-drawer__toast" role="status">已保存</span>
              <span v-if="dirty" class="report-drawer__dirty">未保存</span>
            </div>
            <div v-if="loading" class="report-drawer__body report-drawer__hint">加载中…</div>
            <div v-else class="report-drawer__body">
              <section v-for="f in fields" :key="f.key" class="perf-field" @dblclick="startEdit">
                <h3 class="perf-field__label">{{ f.label }}</h3>
                <textarea
                  v-if="editing"
                  v-model="draft[f.key]"
                  rows="6"
                  class="report-edit-area"
                  @click.stop
                />
                <pre v-else class="report-drawer__text perf-pre">{{ draft[f.key] || '（空）' }}</pre>
              </section>
            </div>
          </aside>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.perf-field {
  margin-bottom: 1.25rem;
}
.perf-field__label {
  font-size: 0.9rem;
  margin: 0 0 0.5rem;
  color: var(--muted);
}
.perf-pre {
  max-height: 200px;
  overflow: auto;
}
</style>
