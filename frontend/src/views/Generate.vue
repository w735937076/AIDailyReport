<script setup>
import { ref, watch } from 'vue'
import { api } from '../api'
import PageHeader from '../components/PageHeader.vue'
import AppAlert from '../components/AppAlert.vue'
import StatusChip from '../components/StatusChip.vue'
import JobProgressPanel from '../components/JobProgressPanel.vue'
import { useJobRunner } from '../composables/useJobRunner'

const date = ref(new Date().toISOString().slice(0, 10))
const tomorrowPlan = ref('')
const nonCodeWork = ref('')
const msg = ref('')
const err = ref('')
const activeLabel = ref('')
const { running, job, status, error: jobError, run } = useJobRunner()
let dismissTimer = null

watch(jobError, (v) => {
  if (v) err.value = v
})

function flashSuccess(text) {
  msg.value = text
  if (dismissTimer) clearTimeout(dismissTimer)
  dismissTimer = setTimeout(() => {
    msg.value = ''
  }, 5000)
}

function body() {
  return { date: date.value, tomorrowPlan: tomorrowPlan.value, nonCodeWork: nonCodeWork.value }
}

async function generateDaily() {
  await runReport(api.reports.generateDaily, '日报')
}

async function generateWeekly() {
  await runReport(api.reports.generateWeekly, '周报')
}

async function generateMonthly() {
  await runReport(api.reports.generateMonthly, '月报')
}

async function runReport(fn, label) {
  err.value = ''
  msg.value = ''
  activeLabel.value = label
  try {
    const finished = await run(() => fn(body()))
    const path = finished?.result?.excelPath
    flashSuccess(`${label}生成成功${path ? '：' + path : ''}`)
  } catch {
    /* panel + err */
  } finally {
    activeLabel.value = ''
  }
}

async function exportExcel() {
  err.value = ''
  msg.value = ''
  activeLabel.value = '导出'
  try {
    const finished = await run(() => api.reports.exportDailyExcel(date.value))
    const path = finished?.result?.path
    if (path) window.open(api.downloadUrl(path), '_blank')
    flashSuccess('Excel 导出完成')
  } catch {
    /* panel + err */
  } finally {
    activeLabel.value = ''
  }
}
</script>

<template>
  <PageHeader
    title="生成报告"
    subtitle="基于已解析的工作项生成日报、周报或月报，并支持导出 Excel。"
  />

  <AppAlert :message="err" type="err" />
  <AppAlert :message="msg" type="ok" />

  <section class="card">
    <h2 class="card-title">参数</h2>
    <div class="row">
      <div>
        <label class="field-label">基准日期</label>
        <input v-model="date" type="date" :disabled="running" />
      </div>
      <div>
        <label class="field-label">任务状态</label>
        <p style="margin:0;padding:0.6rem 0">
          <StatusChip :status="status" />
        </p>
      </div>
    </div>
    <label class="field-label">今日非代码工作（可选）</label>
    <textarea v-model="nonCodeWork" :disabled="running" placeholder="会议、文档、沟通等" />
    <label class="field-label">明日计划（日报用）</label>
    <textarea v-model="tomorrowPlan" :disabled="running" placeholder="每行一条" />
    <div class="action-group">
      <button type="button" class="btn" :disabled="running" @click="generateDaily">
        <span v-if="running && activeLabel === '日报'" class="spinner spinner--accent" />
        {{ running && activeLabel === '日报' ? '生成中…' : '生成日报' }}
      </button>
      <button type="button" class="btn" :disabled="running" @click="generateWeekly">
        <span v-if="running && activeLabel === '周报'" class="spinner spinner--accent" />
        {{ running && activeLabel === '周报' ? '生成中…' : '生成周报' }}
      </button>
      <button type="button" class="btn" :disabled="running" @click="generateMonthly">
        <span v-if="running && activeLabel === '月报'" class="spinner spinner--accent" />
        {{ running && activeLabel === '月报' ? '生成中…' : '生成月报' }}
      </button>
      <button type="button" class="btn btn-secondary" :disabled="running" @click="exportExcel">
        <span v-if="running && activeLabel === '导出'" class="spinner spinner--accent" />
        {{ running && activeLabel === '导出' ? '导出中…' : '导出日报 Excel' }}
      </button>
    </div>
    <JobProgressPanel :job="job" :visible="running || status === 'COMPLETED' || status === 'FAILED'" />
  </section>
</template>
