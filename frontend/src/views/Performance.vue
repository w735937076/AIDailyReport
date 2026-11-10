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
import PerformancePreviewDrawer from '../components/PerformancePreviewDrawer.vue'
import { useJobRunner } from '../composables/useJobRunner'
import { usePagination } from '../composables/usePagination'

const yearMonth = ref(new Date().toISOString().slice(0, 7))
const msg = ref('')
const err = ref('')
const configOpen = ref(false)
const config = ref({})
const generating = ref(false)
const pager = usePagination(({ page, size }) => api.performance.list(page, size), 10)
const {
  running,
  job,
  status,
  error: jobError,
  run,
  beginPending,
  updatePending,
  cancelPending,
  failPending,
} = useJobRunner()

const drawerOpen = ref(false)
const drawerLoading = ref(false)
const active = ref(null)
const activeContent = ref({})

let dismissTimer = null

const panelVisible = computed(
  () => generating.value || running.value || status.value === 'COMPLETED' || status.value === 'FAILED',
)

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
  config.value = await api.performance.getConfig()
}

onMounted(async () => {
  err.value = ''
  try {
    await loadConfig()
  } catch (e) {
    err.value = e.message || '加载绩效配置失败'
  }
  await pager.load()
})

async function saveConfig() {
  err.value = ''
  msg.value = ''
  try {
    config.value = await api.performance.saveConfig(config.value)
    configOpen.value = false
    msg.value = '绩效配置已保存'
  } catch (e) {
    err.value = e.message || '保存配置失败'
  }
}

async function openPreview(row) {
  drawerOpen.value = true
  drawerLoading.value = true
  active.value = row
  try {
    const full = await api.performance.get(row.id)
    active.value = full
    activeContent.value = parseContent(full.contentJson)
  } catch (e) {
    err.value = e.message || '加载绩效详情失败'
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

function formatReviewMonth(ym) {
  if (!ym || !/^\d{4}-\d{2}$/.test(ym)) return ym || '—'
  const [y, m] = ym.split('-')
  return `${y}年${Number(m)}月`
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

async function onSaved(updated) {
  active.value = updated
  activeContent.value = parseContent(updated.contentJson)
  await pager.load()
  flashSuccess('已保存并重新导出 Excel')
}

async function generate() {
  if (generating.value || running.value) return
  err.value = ''
  msg.value = ''
  generating.value = true
  beginPending(`正在准备 ${yearMonth.value} 绩效评估…`, 8)

  let replaceExisting = false
  try {
    updatePending('正在检查当月是否已有记录…', 12)
    const exists = await api.performance.exists(yearMonth.value)
    if (exists) {
      updatePending('等待确认是否覆盖已有记录…', 18)
      if (!window.confirm(`${yearMonth.value} 已有记录，确认覆盖重新生成？`)) {
        cancelPending()
        generating.value = false
        return
      }
      replaceExisting = true
    }
    updatePending('正在提交生成任务…', 22)
  } catch (e) {
    const m = e.message || '检查当月记录失败'
    failPending('检查当月记录失败', m)
    err.value = m
    generating.value = false
    return
  }

  try {
    const finished = await run(() =>
      api.performance.generate({ yearMonth: yearMonth.value, replaceExisting }),
    )
    const id = finished?.result?.id
    flashSuccess(`绩效生成完成${id ? '（记录 #' + id + '）' : ''}`)
    await pager.load()
    if (id) {
      const row = pager.items.value.find((r) => r.id === id) || { id }
      await openPreview(row)
    }
  } catch (e) {
    if (!jobError.value) {
      err.value = e.message || '生成绩效失败'
    }
  } finally {
    generating.value = false
  }
}
</script>

<template>
  <PageHeader
    title="绩效评估"
    subtitle="按月汇总 Git 与工作项，生成长文绩效评估并填充官方 Excel 模板（独立于月报）。"
  />

  <AppAlert :message="err" type="err" />
  <AppAlert :message="msg" type="ok" />

  <section class="card">
    <h2 class="card-title">生成</h2>
    <div class="row">
      <div>
        <label class="field-label">月份</label>
        <input v-model="yearMonth" type="month" :disabled="generating || running" />
      </div>
      <div>
        <label class="field-label">任务状态</label>
        <p style="margin: 0; padding: 0.6rem 0">
          <StatusChip :status="status" />
        </p>
      </div>
      <div class="actions">
        <button type="button" class="btn primary" :disabled="generating || running" @click="generate">
          <span v-if="generating || running" class="spinner spinner--accent" />
          {{ generating || running ? '生成中…' : '生成绩效表' }}
        </button>
        <button type="button" class="btn" :disabled="generating || running" @click="configOpen = !configOpen">
          配置
        </button>
      </div>
    </div>
    <JobProgressPanel :job="job" :visible="panelVisible" />
  </section>

  <section v-if="configOpen" class="card">
    <h2 class="card-title">绩效配置</h2>
    <div class="grid-2">
      <label>部门 <input v-model="config.department" /></label>
      <label>岗位 <input v-model="config.role" /></label>
      <label>被评估人 <input v-model="config.assessee" /></label>
      <label>评估人 <input v-model="config.assessor" /></label>
      <label class="span-2">模板路径 <input v-model="config.templatePath" /></label>
      <label class="span-2">本地模板目录（MonthPerformance） <input v-model="config.templateSearchDir" /></label>
      <label> KPI 分 <input v-model.number="config.scoreKpi" type="number" /></label>
      <label> 辅助分 <input v-model.number="config.scoreAuxiliary" type="number" /></label>
      <label> 态度分 <input v-model.number="config.scoreAttitude" type="number" /></label>
      <label> 其它分 <input v-model.number="config.scoreOther" type="number" /></label>
    </div>
    <button type="button" class="btn primary" @click="saveConfig">保存配置</button>
  </section>

  <section class="card page-stack">
    <h2 class="card-title">历史记录</h2>

    <LoadingBlock v-if="pager.loading.value" />
    <template v-else>
      <EmptyState
        v-if="!pager.items.value.length"
        title="暂无绩效记录"
        hint="在上方选择月份并点击「生成绩效表」，完成后会显示在这里。"
        icon="★"
      />

      <div v-else class="table-wrap">
        <table class="data-table table-fluid performance-table">
          <thead>
            <tr>
              <th class="col-perf-month">月份</th>
              <th class="col-perf-author">被评估人</th>
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
              <td class="col-perf-month">{{ formatReviewMonth(row.reviewMonth) }}</td>
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

  <PerformancePreviewDrawer
    :open="drawerOpen"
    :loading="drawerLoading"
    :title="active ? `${active.reviewMonth} 绩效` : ''"
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
</style>

<style>
.data-table.performance-table .col-perf-month {
  width: 9rem;
  font-weight: 500;
  color: var(--text);
}
.data-table.performance-table .col-perf-author {
  width: 22%;
  max-width: 12rem;
}
.data-table.performance-table .col-perf-status {
  width: 7rem;
}
.data-table.performance-table .col-perf-updated {
  width: 11rem;
  font-size: 0.8125rem;
}
.data-table.performance-table .col-perf-actions {
  width: 11rem;
  text-align: right;
  white-space: nowrap;
}
.data-table.performance-table .col-perf-actions .btn + .btn {
  margin-left: 0.25rem;
}
</style>
