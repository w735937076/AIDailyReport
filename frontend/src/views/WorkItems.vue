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
import WorkItemDetailDrawer from '../components/WorkItemDetailDrawer.vue'

const typeLabels = {
  FEATURE: '功能开发',
  BUG_FIX: 'Bug修复',
  REFACTOR: '重构优化',
  DOCS: '文档更新',
}

const typeOptions = ['', 'FEATURE', 'BUG_FIX', 'REFACTOR', 'DOCS']

const filters = ref({
  workDate: todayInShanghai(),
  repoName: '',
  type: '',
})
const repoOptions = ref([])
const err = ref('')
const msg = ref('')

const {
  running: collectRunning,
  job: collectJob,
  status: collectStatus,
  error: collectError,
  run: runCollect,
  resumePolling,
  takePendingCompletion,
} = useJobRunner({ scope: 'collect' })

watch(collectError, (v) => {
  if (v) err.value = v
})

const pager = usePagination(
  ({ page, size }) =>
    api.workItems.list({
      page,
      size,
      workDate: filters.value.workDate || undefined,
      repoName: filters.value.repoName || undefined,
      type: filters.value.type || undefined,
    }),
  10,
)

const detailOpen = ref(false)
const detailLoading = ref(false)
const detailItem = ref(null)

const showCollectPanel = computed(
  () => collectRunning.value || collectStatus.value === 'COMPLETED' || collectStatus.value === 'FAILED',
)

function summarize(text, max = 48) {
  const t = (text || '').trim().replace(/\s+/g, ' ')
  if (!t) return '—'
  return t.length > max ? `${t.slice(0, max)}…` : t
}

function applyFilters() {
  pager.page.value = 1
  pager.load()
}

function resetFilters() {
  filters.value = { workDate: todayInShanghai(), repoName: '', type: '' }
  applyFilters()
}

async function collectNow() {
  err.value = ''
  msg.value = ''
  try {
    const finished = await runCollect(() => api.collect.now())
    const r = finished?.result
  const text =
      finished?.message ||
      (r
        ? `采集完成：${r.reposProcessed} 个仓库，新增 ${r.commitsCollected} 条提交，解析 ${r.commitsParsed} 条工作项`
        : '采集完成')
    msg.value = text
    await pager.load()
  } catch {
    /* panel + err */
  }
}

async function onCollectJobSettled() {
  const pending = takePendingCompletion()
  if (pending) {
    await pager.load()
    msg.value = pending.message || '采集完成'
  }
}

async function openDetail(row) {
  detailOpen.value = true
  detailLoading.value = true
  detailItem.value = null
  try {
    detailItem.value = await api.workItems.get(row.id)
  } catch (e) {
    err.value = e.message
    detailOpen.value = false
  } finally {
    detailLoading.value = false
  }
}

onMounted(async () => {
  try {
    const { repos } = await api.workItems.repos()
    repoOptions.value = repos || []
  } catch {
    /* optional */
  }
  await resumePolling()
  await onCollectJobSettled()
  pager.load().catch((e) => {
    err.value = e.message
  })
})
</script>

<template>
  <PageHeader
    title="工作项"
    subtitle="浏览 AI 从 Git 提交解析出的结构化工作项，可按日期、仓库、类型筛选。"
  />

  <AppAlert :message="err" type="err" />
  <AppAlert :message="msg" type="ok" />
  <AppAlert :message="pager.error.value" type="err" />

  <section class="card page-stack">
    <div class="filter-bar filter-bar--aligned">
      <div class="filter-field">
        <label class="field-label">工作日期</label>
        <input v-model="filters.workDate" type="date" :disabled="collectRunning" />
      </div>
      <div class="filter-field" style="min-width: 11rem">
        <label class="field-label">仓库</label>
        <select v-model="filters.repoName" :disabled="collectRunning">
          <option value="">全部</option>
          <option v-for="r in repoOptions" :key="r" :value="r">{{ r }}</option>
        </select>
      </div>
      <div class="filter-field" style="min-width: 8.5rem">
        <label class="field-label">类型</label>
        <select v-model="filters.type" :disabled="collectRunning">
          <option v-for="t in typeOptions" :key="t || 'all'" :value="t">
            {{ t ? typeLabels[t] || t : '全部' }}
          </option>
        </select>
      </div>
      <div class="filter-field" style="min-width: 6rem">
        <label class="field-label">采集状态</label>
        <p style="margin: 0; min-height: var(--control-h); display: flex; align-items: center">
          <StatusChip :status="collectStatus" />
        </p>
      </div>
      <div class="filter-actions">
        <button type="button" class="btn" :disabled="collectRunning" @click="collectNow">
          <span v-if="collectRunning" class="spinner spinner--accent" />
          {{ collectRunning ? '采集中…' : '立即采集' }}
        </button>
        <button type="button" class="btn" :disabled="collectRunning" @click="applyFilters">查询</button>
        <button type="button" class="btn btn-secondary" :disabled="collectRunning" @click="resetFilters">
          重置
        </button>
        <RouterLink to="/scheduler" class="btn btn-ghost">定时采集</RouterLink>
      </div>
    </div>

    <JobProgressPanel :job="collectJob" :visible="showCollectPanel" prominent />

    <LoadingBlock v-if="pager.loading.value" />
    <EmptyState
      v-else-if="!pager.items.value.length"
      title="暂无工作项"
      hint="使用「立即采集」或完成 Git 项目配置后采集，数据会出现在这里。"
      icon="📋"
    />
    <div v-else class="table-wrap">
      <table class="data-table work-item-table">
        <thead>
          <tr>
            <th class="col-wi-date">日期</th>
            <th class="col-wi-repo">仓库</th>
            <th class="col-wi-type">类型</th>
            <th class="col-wi-module">模块</th>
            <th class="col-wi-summary">计划 / 做法摘要</th>
            <th class="col-wi-progress">进度</th>
            <th class="col-wi-conf">置信度</th>
            <th class="col-wi-actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in pager.items.value" :key="row.id" class="report-row" @click="openDetail(row)">
            <td class="col-wi-date">{{ row.workDate }}</td>
            <td class="col-wi-repo cell-ellipsis" :title="row.repoName">{{ row.repoName || '—' }}</td>
            <td class="col-wi-type">
              <span class="chip draft">{{ typeLabels[row.type] || row.type }}</span>
            </td>
            <td class="col-wi-module cell-ellipsis" :title="row.module">{{ row.module || '—' }}</td>
            <td class="col-wi-summary cell-muted">
              {{ summarize(row.plan) }} / {{ summarize(row.practice) }}
            </td>
            <td class="col-wi-progress cell-ellipsis">{{ row.progress || '—' }}</td>
            <td class="col-wi-conf">{{ row.confidence != null ? row.confidence : '—' }}</td>
            <td class="col-wi-actions">
              <button type="button" class="btn btn-ghost btn-sm" @click.stop="openDetail(row)">详情</button>
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
      @update:page="pager.goToPage"
      @update:size="pager.changeSize"
    />
  </section>

  <WorkItemDetailDrawer
    :open="detailOpen"
    :loading="detailLoading"
    :item="detailItem"
    @close="detailOpen = false"
  />
</template>
