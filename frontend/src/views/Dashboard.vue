<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '../api'
import { usePagination } from '../composables/usePagination'
import { useJobRunner } from '../composables/useJobRunner'
import { todayInShanghai } from '../utils/dateShanghai'
import PageHeader from '../components/PageHeader.vue'
import Pagination from '../components/Pagination.vue'
import AppAlert from '../components/AppAlert.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingBlock from '../components/LoadingBlock.vue'
import StatusChip from '../components/StatusChip.vue'
import JobProgressPanel from '../components/JobProgressPanel.vue'
import DailyGenerateDialog from '../components/DailyGenerateDialog.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import ReportPreviewDrawer from '../components/ReportPreviewDrawer.vue'

const tab = ref('daily')
const msg = ref('')
const err = ref('')
const genDate = ref(todayInShanghai())
const generateLabel = ref('')
const dailyDialogOpen = ref(false)
const dailyNonCodeWork = ref('')
const dailyTomorrowPlan = ref('')
const deleteOpen = ref(false)
const deleteLoading = ref(false)
/** @type {import('vue').Ref<{ id: number, kind: string, label: string } | null>} */
const deleteTarget = ref(null)

const {
  running: genRunning,
  job: genJob,
  status: genStatus,
  error: genError,
  run: runGenerate,
  resumePolling,
  takePendingCompletion,
} = useJobRunner({ scope: 'report' })

watch(genError, (v) => {
  if (v) err.value = v
})

const dailyPager = usePagination(({ page, size }) => api.reports.dailyList(page, size), 10)
const weeklyPager = usePagination(({ page, size }) => api.reports.periodList('WEEK', page, size), 10)
const monthlyPager = usePagination(({ page, size }) => api.reports.periodList('MONTH', page, size), 10)

const activePager = computed(() =>
  tab.value === 'daily' ? dailyPager : tab.value === 'weekly' ? weeklyPager : monthlyPager,
)

watch(tab, () => activePager.value.load())

const tabLabels = { daily: '日报', weekly: '周报', monthly: '月报' }

function reportBody(overrides = {}) {
  return {
    date: genDate.value,
    tomorrowPlan: '',
    nonCodeWork: '',
    ...overrides,
  }
}

async function refreshLists() {
  await Promise.all([dailyPager.load(), weeklyPager.load(), monthlyPager.load()])
}

function requestGenerate(kind = tab.value) {
  if (kind === 'daily') {
    dailyDialogOpen.value = true
    return
  }
  generateForTab(kind)
}

function closeDailyDialog() {
  if (!genRunning.value) dailyDialogOpen.value = false
}

async function onDailyDialogConfirm({ nonCodeWork, tomorrowPlan }) {
  dailyDialogOpen.value = false
  dailyNonCodeWork.value = nonCodeWork
  dailyTomorrowPlan.value = tomorrowPlan
  await generateForTab('daily', { nonCodeWork, tomorrowPlan })
}

async function generateForTab(kind = tab.value, dailyFields = null) {
  err.value = ''
  msg.value = ''
  const label = tabLabels[kind]
  generateLabel.value = label
  const body =
    kind === 'daily' && dailyFields
      ? reportBody({
          nonCodeWork: dailyFields.nonCodeWork ?? '',
          tomorrowPlan: dailyFields.tomorrowPlan ?? '',
        })
      : reportBody()
  const fnMap = {
    daily: () => api.reports.generateDaily(body),
    weekly: () => api.reports.generateWeekly(body),
    monthly: () => api.reports.generateMonthly(body),
  }
  try {
    await runGenerate(() => fnMap[kind]())
    await refreshLists()
    msg.value = `${label}生成成功`
  } catch {
    /* panel + err */
  } finally {
    generateLabel.value = ''
  }
}

async function onReportJobSettled() {
  const pending = takePendingCompletion()
  if (pending) {
    await refreshLists()
    msg.value = '报告生成完成'
  }
}

onMounted(async () => {
  await resumePolling()
  await onReportJobSettled()
  refreshLists().catch((e) => {
    err.value = e.message
  })
})

const previewOpen = ref(false)
const previewLoading = ref(false)
const previewTitle = ref('')
const previewExcel = ref('')
const previewId = ref(null)
const previewKind = ref('daily')
const previewContent = ref('')
const previewTomorrow = ref('')
let dismissTimer = null

function flashOk(text) {
  msg.value = text
  if (dismissTimer) clearTimeout(dismissTimer)
  dismissTimer = setTimeout(() => {
    msg.value = ''
  }, 3000)
}

async function openPreview(row) {
  err.value = ''
  previewOpen.value = true
  previewLoading.value = true
  previewContent.value = ''
  previewTomorrow.value = ''
  previewExcel.value = row.excelPath || ''
  previewId.value = row.id
  previewKind.value = tab.value === 'daily' ? 'daily' : 'period'

  try {
    let report
    if (tab.value === 'daily') {
      previewTitle.value = `日报 · ${row.reportDate}`
      report = await api.reports.getDailyById(row.id)
      previewContent.value = report.content || ''
      previewTomorrow.value = report.tomorrowPlan || ''
    } else {
      const label = tab.value === 'weekly' ? '周报' : '月报'
      previewTitle.value = `${label} · ${row.startDate} ~ ${row.endDate}`
      report = await api.reports.getPeriodById(row.id)
      previewContent.value = report.content || ''
      previewExcel.value = report?.excelPath || previewExcel.value
    }
  } catch (e) {
    previewOpen.value = false
    err.value = e.message
  } finally {
    previewLoading.value = false
  }
}

function onSaved(report) {
  if (report) {
    previewContent.value = report.content || ''
    if (previewKind.value === 'daily') previewTomorrow.value = report.tomorrowPlan || ''
  }
  flashOk('已保存')
}

function downloadExcel() {
  if (!previewExcel.value) return
  window.open(api.downloadUrl(previewExcel.value), '_blank')
}

function onPageChange(p) {
  activePager.value.goToPage(p)
}

function onSizeChange(s) {
  activePager.value.changeSize(s)
}

const showGenPanel = computed(
  () => genRunning.value || genStatus.value === 'COMPLETED' || genStatus.value === 'FAILED',
)

const deleteDialogMessage = computed(() => {
  const t = deleteTarget.value
  if (!t) return ''
  return `确定删除「${t.label}」吗？数据库记录及 output 目录下的导出文件将被移除，此操作不可撤销。`
})

function reportRowLabel(row) {
  if (tab.value === 'daily') {
    return `日报 · ${row.reportDate}`
  }
  const type = tab.value === 'weekly' ? '周报' : '月报'
  return `${type} · ${row.startDate} ~ ${row.endDate}`
}

function requestDelete(row) {
  deleteTarget.value = {
    id: row.id,
    kind: tab.value,
    label: reportRowLabel(row),
  }
  deleteOpen.value = true
}

function cancelDelete() {
  if (deleteLoading.value) return
  deleteOpen.value = false
  deleteTarget.value = null
}

async function syncAfterDelete() {
  await refreshLists()
  await activePager.value.load()
}

async function onDeleteConfirm() {
  const target = deleteTarget.value
  if (!target) return
  deleteLoading.value = true
  err.value = ''
  try {
    if (target.kind === 'daily') {
      await api.reports.deleteDaily(target.id)
    } else {
      await api.reports.deletePeriod(target.id)
    }
    if (previewOpen.value && previewId.value === target.id) {
      previewOpen.value = false
    }
    deleteOpen.value = false
    deleteTarget.value = null
    flashOk('已删除')
    await syncAfterDelete()
  } catch (e) {
    const gone = e?.message && (e.message.includes('不存在') || e.message.includes('404'))
    if (gone) {
      deleteOpen.value = false
      deleteTarget.value = null
      await syncAfterDelete()
    } else {
      err.value = e.message
    }
  } finally {
    deleteLoading.value = false
  }
}
</script>

<template>
  <PageHeader
    title="报告总览"
    subtitle="在线预览与编辑报告正文，按段落复制或一键复制全文；Excel 导出为次要选项。"
  />

  <AppAlert :message="err" type="err" />
  <AppAlert :message="msg" type="ok" />

  <section class="card">
    <h2 class="card-title">生成报告</h2>
    <p class="card-hint">与「生成报告」页相同流程；离开本页后进度仍会在侧边栏显示。</p>
    <div class="generate-toolbar">
      <div class="filter-field">
        <label class="field-label">基准日期</label>
        <input v-model="genDate" type="date" :disabled="genRunning" />
      </div>
      <div class="filter-field" style="min-width: 7rem">
        <label class="field-label">任务状态</label>
        <p style="margin: 0; min-height: var(--control-h); display: flex; align-items: center">
          <StatusChip :status="genStatus" />
        </p>
      </div>
      <div class="generate-toolbar__actions">
        <button
          type="button"
          class="btn"
          :disabled="genRunning"
          :title="`生成当前标签：${tabLabels[tab]}`"
          @click="requestGenerate()"
        >
          <span v-if="genRunning && generateLabel === tabLabels[tab]" class="spinner spinner--accent" />
          {{ genRunning && generateLabel === tabLabels[tab] ? '生成中…' : `生成${tabLabels[tab]}` }}
        </button>
        <button type="button" class="btn btn-secondary" :disabled="genRunning" @click="requestGenerate('daily')">
          日报
        </button>
        <button type="button" class="btn btn-secondary" :disabled="genRunning" @click="requestGenerate('weekly')">
          周报
        </button>
        <button type="button" class="btn btn-secondary" :disabled="genRunning" @click="requestGenerate('monthly')">
          月报
        </button>
        <RouterLink to="/generate" class="btn btn-ghost">更多选项</RouterLink>
      </div>
    </div>
    <JobProgressPanel :job="genJob" :visible="showGenPanel" prominent />
  </section>

  <div class="stat-row">
    <div v-for="(pager, key) in { daily: dailyPager, weekly: weeklyPager, monthly: monthlyPager }" :key="key" class="stat-card">
      <div class="stat-label">{{ tabLabels[key] }}</div>
      <div class="stat-value">{{ pager.total.value }}</div>
    </div>
  </div>

  <section class="card">
    <div class="tabs" role="tablist">
      <button
        v-for="t in ['daily', 'weekly', 'monthly']"
        :key="t"
        type="button"
        class="tab"
        :class="{ active: tab === t }"
        @click="tab = t"
      >{{ tabLabels[t] }}</button>
    </div>

    <LoadingBlock v-if="activePager.loading.value" />
    <template v-else>
      <AppAlert :message="activePager.error.value" type="err" />

      <EmptyState
        v-if="!activePager.items.value.length"
        :title="`暂无${tabLabels[tab]}`"
        hint="使用上方「生成报告」或前往「生成报告」页创建第一条记录。"
        icon="📄"
      />

      <div v-else class="table-wrap">
        <table class="data-table report-table">
          <thead>
            <tr>
              <th class="col-report-date">{{ tab === 'daily' ? '日期' : '周期' }}</th>
              <th class="col-report-status">状态</th>
              <th class="col-report-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="r in activePager.items.value"
              :key="r.id"
              class="report-row"
              tabindex="0"
              @click="openPreview(r)"
              @keydown.enter="openPreview(r)"
            >
              <td v-if="tab === 'daily'" class="col-report-date">{{ r.reportDate }}</td>
              <td v-else class="col-report-date">{{ r.startDate }} ~ {{ r.endDate }}</td>
              <td class="col-report-status"><StatusChip :status="r.status" /></td>
              <td class="col-report-actions">
                <div class="toolbar-actions">
                  <button type="button" class="btn btn-ghost btn-sm" @click.stop="openPreview(r)">预览</button>
                  <button type="button" class="btn btn-danger btn-sm" @click.stop="requestDelete(r)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <Pagination
        :page="activePager.page.value"
        :size="activePager.size.value"
        :total="activePager.total.value"
        @update:page="onPageChange"
        @update:size="onSizeChange"
      />
    </template>
  </section>

  <ConfirmDialog
    :open="deleteOpen"
    title="删除报告"
    :message="deleteDialogMessage"
    confirm-label="删除"
    cancel-label="取消"
    danger
    :loading="deleteLoading"
    @confirm="onDeleteConfirm"
    @cancel="cancelDelete"
  />

  <DailyGenerateDialog
    v-model:open="dailyDialogOpen"
    v-model:non-code-work="dailyNonCodeWork"
    v-model:tomorrow-plan="dailyTomorrowPlan"
    :loading="genRunning"
    @confirm="onDailyDialogConfirm"
    @cancel="closeDailyDialog"
  />

  <ReportPreviewDrawer
    :open="previewOpen"
    :loading="previewLoading"
    :title="previewTitle"
    :report-id="previewId"
    :report-kind="previewKind"
    :content="previewContent"
    :tomorrow-plan="previewTomorrow"
    :excel-path="previewExcel"
    @close="previewOpen = false"
    @download-excel="downloadExcel"
    @copied="flashOk('已复制')"
    @saved="onSaved"
  />
</template>
