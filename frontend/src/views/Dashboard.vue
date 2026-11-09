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

function download(path) {
  if (!path) return
  window.open(api.downloadUrl(path), '_blank')
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
  <PageHeader title="报告总览" subtitle="浏览已生成的日报、周报、月报，并下载导出文件。" />

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
        <table class="data-table table-fluid">
          <thead>
            <tr>
              <th class="col-path">{{ tab === 'daily' ? '日期' : '周期' }}</th>
              <th class="col-status">状态</th>
              <th class="col-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in activePager.items.value" :key="r.id">
              <td v-if="tab === 'daily'" class="col-path">{{ r.reportDate }}</td>
              <td v-else class="col-path">{{ r.startDate }} ~ {{ r.endDate }}</td>
              <td class="col-status"><StatusChip :status="r.status" /></td>
              <td class="col-actions">
                <button
                  v-if="r.excelPath"
                  type="button"
                  class="btn btn-ghost btn-sm"
                  @click="download(r.excelPath)"
                >下载</button>
                <span v-else class="text-muted">—</span>
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
</template>

<style scoped>
.text-muted {
  color: var(--muted);
  font-size: 0.85rem;
}
</style>
