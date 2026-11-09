<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api } from '../api'
import { usePagination } from '../composables/usePagination'
import PageHeader from '../components/PageHeader.vue'
import Pagination from '../components/Pagination.vue'
import AppAlert from '../components/AppAlert.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingBlock from '../components/LoadingBlock.vue'
import StatusChip from '../components/StatusChip.vue'
import ReportPreviewDrawer from '../components/ReportPreviewDrawer.vue'

const tab = ref('daily')
const msg = ref('')
const err = ref('')

const dailyPager = usePagination(({ page, size }) => api.reports.dailyList(page, size), 10)
const weeklyPager = usePagination(({ page, size }) => api.reports.periodList('WEEK', page, size), 10)
const monthlyPager = usePagination(({ page, size }) => api.reports.periodList('MONTH', page, size), 10)

const activePager = computed(() =>
  tab.value === 'daily' ? dailyPager : tab.value === 'weekly' ? weeklyPager : monthlyPager,
)

watch(tab, () => activePager.value.load())

onMounted(() => {
  Promise.all([dailyPager.load(), weeklyPager.load(), monthlyPager.load()]).catch((e) => {
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

const tabLabels = { daily: '日报', weekly: '周报', monthly: '月报' }
</script>

<template>
  <PageHeader
    title="报告总览"
    subtitle="在线预览与编辑报告正文，按段落复制或一键复制全文；Excel 导出为次要选项。"
  />

  <AppAlert :message="err" type="err" />
  <AppAlert :message="msg" type="ok" />

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
        hint="前往「生成报告」创建第一条记录。"
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
                <button type="button" class="btn btn-ghost btn-sm" @click.stop="openPreview(r)">预览</button>
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
