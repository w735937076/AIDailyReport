<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api } from '../api'
import PageHeader from '../components/PageHeader.vue'
import AppAlert from '../components/AppAlert.vue'
import StatusChip from '../components/StatusChip.vue'
import Pagination from '../components/Pagination.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingBlock from '../components/LoadingBlock.vue'
import JobProgressPanel from '../components/JobProgressPanel.vue'
import InterviewPreviewDrawer from '../components/InterviewPreviewDrawer.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { useJobRunner } from '../composables/useJobRunner'
import { usePagination } from '../composables/usePagination'

const yearMonth = ref(new Date().toISOString().slice(0, 7))
const interviewDate = ref('')
const msg = ref('')
const err = ref('')
const configOpen = ref(false)
const config = ref({})
const pager = usePagination(({ page, size }) => api.interview.list(page, size), 10)
const interviewJob = useJobRunner({ scope: 'interview' })
const {
  running,
  job,
  status,
  error: jobError,
  preflight,
  contextYearMonth,
  run,
  beginPending,
  updatePending,
  resumePolling,
  takePendingCompletion,
} = interviewJob

const checkingExists = ref(false)
const overwriteOpen = ref(false)
const overwriteMonthLabel = ref('')
let overwriteResolve = null

const drawerOpen = ref(false)
const drawerLoading = ref(false)
const active = ref(null)
const activeContent = ref({})

let dismissTimer = null

function formatMonth(ym) {
  if (!ym || !/^\d{4}-\d{2}$/.test(ym)) return ym || '—'
  const [y, m] = ym.split('-')
  return `${y}年${Number(m)}月`
}

function lastDayOfMonth(ym) {
  if (!ym || !/^\d{4}-\d{2}$/.test(ym)) return ''
  const [y, m] = ym.split('-').map(Number)
  const d = new Date(y, m, 0)
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${mm}-${dd}`
}

watch(yearMonth, (v) => {
  if (!interviewDate.value || interviewDate.value.startsWith(v.slice(0, 7))) {
    interviewDate.value = lastDayOfMonth(v)
  }
})

const busy = computed(() => preflight.value || running.value)

const panelVisible = computed(
  () => busy.value || status.value === 'COMPLETED' || status.value === 'FAILED',
)

const progressSubtitle = computed(() => {
  const ym = contextYearMonth.value || yearMonth.value
  return ym ? `${formatMonth(ym)} · 绩效面谈` : '绩效面谈'
})

watch(jobError, (v) => {
  if (v) err.value = v
})

watch(
  () => pager.error.value,
  (v) => {
    if (v) err.value = v
  },
)

function flashSuccess(text) {
  msg.value = text
  if (dismissTimer) clearTimeout(dismissTimer)
  dismissTimer = setTimeout(() => {
    msg.value = ''
  }, 6000)
}

function parseContent(json) {
  try {
    return JSON.parse(json || '{}')
  } catch {
    return {}
  }
}

async function loadConfig() {
  config.value = await api.interview.getConfig()
}

async function applyGenerateSuccess(finished) {
  if (!finished) return
  const id = finished?.result?.id
  flashSuccess(`面谈记录生成完成${id ? '（#' + id + '）' : ''}`)
  await pager.load()
  if (id) {
    const row = pager.items.value.find((r) => r.id === id) || { id }
    await openPreview(row)
  }
}

onMounted(async () => {
  err.value = ''
  if (contextYearMonth.value) {
    yearMonth.value = contextYearMonth.value
  }
  interviewDate.value = lastDayOfMonth(yearMonth.value)
  resumePolling()
  const pendingDone = takePendingCompletion()
  if (pendingDone) {
    await applyGenerateSuccess(pendingDone)
  }
  try {
    await loadConfig()
  } catch (e) {
    err.value = e.message || '加载面谈配置失败'
  }
  await pager.load()
})

async function saveConfig() {
  err.value = ''
  msg.value = ''
  try {
    config.value = await api.interview.saveConfig(config.value)
    configOpen.value = false
    msg.value = '面谈配置已保存'
  } catch (e) {
    err.value = e.message || '保存配置失败'
  }
}

async function openPreview(row) {
  drawerOpen.value = true
  drawerLoading.value = true
  active.value = row
  try {
    const full = await api.interview.get(row.id)
    active.value = full
    activeContent.value = parseContent(full.contentJson)
  } catch (e) {
    err.value = e.message || '加载面谈详情失败'
    drawerOpen.value = false
  } finally {
    drawerLoading.value = false
  }
}

function closeDrawer() {
  drawerOpen.value = false
}

function downloadExcel(path) {
  if (path) window.open(api.downloadUrl(path), '_blank')
}

function formatUpdatedAt(iso) {
  if (!iso) return '—'
  try {
    const d = new Date(iso)
    if (Number.isNaN(d.getTime())) return '—'
    return d.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return '—'
  }
}

function askOverwriteConfirm(ym) {
  overwriteMonthLabel.value = formatMonth(ym)
  overwriteOpen.value = true
  return new Promise((resolve) => {
    overwriteResolve = resolve
  })
}

function closeOverwriteConfirm(result) {
  overwriteOpen.value = false
  if (overwriteResolve) {
    overwriteResolve(result)
    overwriteResolve = null
  }
}

function onOverwriteConfirm() {
  closeOverwriteConfirm(true)
}

function onOverwriteCancel() {
  closeOverwriteConfirm(false)
}

async function onSaved(updated) {
  active.value = updated
  activeContent.value = parseContent(updated.contentJson)
  await pager.load()
  flashSuccess('已保存并重新导出 Excel')
}

async function generate() {
  if (busy.value) return
  err.value = ''
  msg.value = ''
  contextYearMonth.value = yearMonth.value

  let replaceExisting = false
  checkingExists.value = true
  try {
    const exists = await api.interview.exists(yearMonth.value)
    if (exists) {
      const ok = await askOverwriteConfirm(yearMonth.value)
      if (!ok) return
      replaceExisting = true
    }
  } catch (e) {
    err.value = e.message || '检查当月记录失败'
    return
  } finally {
    checkingExists.value = false
  }

  beginPending(`正在准备 ${formatMonth(yearMonth.value)} 绩效面谈…`, 8)
  updatePending('正在提交生成任务…', 22)

  try {
    const finished = await run(() =>
      api.interview.generate({
        yearMonth: yearMonth.value,
        replaceExisting,
        interviewDate: interviewDate.value || lastDayOfMonth(yearMonth.value),
      }),
    )
    takePendingCompletion()
    await applyGenerateSuccess(finished)
  } catch (e) {
    if (!jobError.value) {
      err.value = e.message || '生成面谈失败'
    }
  }
}
</script>

<template>
  <PageHeader
    title="绩效面谈"
    subtitle="按月依据工作证据生成「绩效面谈记录表」短篇回答，并填充官方 Excel（独立于绩效评估与月报）。"
  />

  <AppAlert :message="err" type="err" />
  <AppAlert :message="msg" type="ok" />

  <section class="card" :class="{ 'card--job-active': busy }">
    <h2 class="card-title">生成</h2>
    <div class="row">
      <div>
        <label class="field-label">月份</label>
        <input v-model="yearMonth" type="month" :disabled="busy || checkingExists" />
      </div>
      <div>
        <label class="field-label">面谈日期</label>
        <input v-model="interviewDate" type="date" :disabled="busy || checkingExists" />
      </div>
      <div>
        <label class="field-label">任务状态</label>
        <p style="margin: 0; padding: 0.6rem 0">
          <StatusChip :status="status" />
        </p>
      </div>
      <div class="actions">
        <button type="button" class="btn primary" :disabled="busy || checkingExists" @click="generate">
          <span v-if="busy || checkingExists" class="spinner spinner--accent" />
          {{ busy ? '生成中…' : checkingExists ? '检查中…' : '生成绩效面谈表' }}
        </button>
        <button type="button" class="btn" :disabled="busy || checkingExists" @click="configOpen = !configOpen">
          配置
        </button>
      </div>
    </div>
    <JobProgressPanel :job="job" :visible="panelVisible" prominent :subtitle="progressSubtitle" />
  </section>

  <section v-if="configOpen" class="card">
    <h2 class="card-title">面谈配置</h2>
    <p class="card-hint">姓名、部门、岗位写入 Excel 表头；入职日期写入 I2（勿写入面谈日期）。</p>
    <div class="grid-2">
      <label>部门 <input v-model="config.department" /></label>
      <label>岗位 <input v-model="config.role" /></label>
      <label>姓名 <input v-model="config.assessee" /></label>
      <label>入职日期 <input v-model="config.hireDate" type="date" placeholder="yyyy-MM-dd" /></label>
      <label class="span-2">模板路径 <input v-model="config.templatePath" /></label>
      <label class="span-2">本地模板目录（MonthFace） <input v-model="config.templateSearchDir" /></label>
    </div>
    <button type="button" class="btn primary" @click="saveConfig">保存配置</button>
  </section>

  <section class="card page-stack">
    <h2 class="card-title">历史记录</h2>

    <LoadingBlock v-if="pager.loading.value" />
    <template v-else>
      <EmptyState
        v-if="!pager.items.value.length"
        title="暂无面谈记录"
        hint="在上方选择月份并点击「生成绩效面谈表」。"
        icon="◆"
      />

      <div v-else class="table-wrap">
        <table class="data-table table-fluid performance-table">
          <thead>
            <tr>
              <th class="col-perf-month">月份</th>
              <th class="col-perf-author">姓名</th>
              <th class="col-perf-status">状态</th>
              <th class="col-perf-updated">更新时间</th>
              <th class="col-perf-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in pager.items.value"
              :key="row.id"
              class="report-row"
              tabindex="0"
              @click="openPreview(row)"
              @keydown.enter="openPreview(row)"
            >
              <td class="col-perf-month">{{ formatMonth(row.interviewMonth) }}</td>
              <td class="col-perf-author cell-ellipsis" :title="row.author">{{ row.author || '—' }}</td>
              <td class="col-perf-status">
                <StatusChip :status="row.status" />
              </td>
              <td class="col-perf-updated cell-muted">{{ formatUpdatedAt(row.updatedAt) }}</td>
              <td class="col-perf-actions">
                <button type="button" class="btn btn-ghost btn-sm" @click.stop="openPreview(row)">预览</button>
                <button
                  v-if="row.excelPath"
                  type="button"
                  class="btn btn-ghost btn-sm"
                  @click.stop="downloadExcel(row.excelPath)"
                >
                  下载 Excel
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <Pagination
        v-if="pager.items.value.length"
        :page="pager.page.value"
        :size="pager.size.value"
        :total="pager.total.value"
        @update:page="(p) => pager.goToPage(p)"
        @update:size="(s) => pager.changeSize(s)"
      />
    </template>
  </section>

  <ConfirmDialog
    :open="overwriteOpen"
    title="覆盖已有记录"
    :message="`${overwriteMonthLabel || '该月份'}已有面谈记录。覆盖后将重新调用 AI 生成并替换当前内容与 Excel，此操作不可撤销。`"
    confirm-label="覆盖并重新生成"
    cancel-label="取消"
    danger
    @confirm="onOverwriteConfirm"
    @cancel="onOverwriteCancel"
  />

  <InterviewPreviewDrawer
    :open="drawerOpen"
    :loading="drawerLoading"
    :title="active ? `${formatMonth(active.interviewMonth)} 绩效面谈` : ''"
    :review-id="active?.id"
    :content="activeContent"
    :excel-path="active?.excelPath"
    @close="closeDrawer"
    @download-excel="downloadExcel(active?.excelPath)"
    @saved="onSaved"
  />
</template>

<style scoped>
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem 1rem;
  margin-bottom: 1rem;
}
.grid-2 label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.85rem;
}
.span-2 {
  grid-column: span 2;
}
.actions {
  display: flex;
  align-items: flex-end;
  gap: 0.5rem;
}
.card--job-active {
  border-color: rgba(29, 78, 216, 0.15);
  box-shadow: 0 4px 20px rgba(29, 78, 216, 0.06);
}
</style>
