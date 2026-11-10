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
  { key: 'kpiD5', label: '岗位 KPI (D5)' },
  { key: 'auxiliaryD12', label: '其它辅助绩效 (D12)' },
  { key: 'attitudeD15', label: '工作态度 (D15)' },
  { key: 'overallB38', label: '综合评估 (B38)' },
]

const draft = ref({})
const saved = ref({})
const savedFlash = ref(false)
let flashTimer = null

const dirty = computed(() => fields.some((f) => (draft.value[f.key] || '') !== (saved.value[f.key] || '')))

function resetDraft() {
  const c = props.content || {}
  draft.value = {
    kpiD5: c.kpiD5 || '',
    auxiliaryD12: c.auxiliaryD12 || '',
    attitudeD15: c.attitudeD15 || '',
    overallB38: c.overallB38 || '',
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
    const updated = await api.performance.update(props.reviewId, { ...draft.value })
    const parsed = JSON.parse(updated.contentJson || '{}')
    draft.value = {
      kpiD5: parsed.kpiD5 || '',
      auxiliaryD12: parsed.auxiliaryD12 || '',
      attitudeD15: parsed.attitudeD15 || '',
      overallB38: parsed.overallB38 || '',
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
                <p class="report-drawer__eyebrow">绩效预览</p>
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
                  rows="10"
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
  max-height: 240px;
  overflow: auto;
}
</style>
