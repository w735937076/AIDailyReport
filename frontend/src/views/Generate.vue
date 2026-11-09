<script setup>
import { ref } from 'vue'
import { api } from '../api'
import PageHeader from '../components/PageHeader.vue'
import AppAlert from '../components/AppAlert.vue'
import StatusChip from '../components/StatusChip.vue'

const date = ref(new Date().toISOString().slice(0, 10))
const tomorrowPlan = ref('')
const nonCodeWork = ref('')
const status = ref('IDLE')
const msg = ref('')
const err = ref('')
const loading = ref(false)

async function run(fn, label) {
  loading.value = true
  err.value = ''
  msg.value = ''
  status.value = 'RUNNING'
  try {
    const body = { date: date.value, tomorrowPlan: tomorrowPlan.value, nonCodeWork: nonCodeWork.value }
    const result = await fn(body)
    status.value = 'COMPLETED'
    msg.value = `${label}成功${result?.excelPath ? '：' + result.excelPath : ''}`
  } catch (e) {
    status.value = 'FAILED'
    err.value = e.message
  } finally {
    loading.value = false
  }
}

async function exportExcel() {
  err.value = ''
  try {
    const { path } = await api.reports.exportDailyExcel(date.value)
    window.open(api.downloadUrl(path), '_blank')
  } catch (e) {
    err.value = e.message
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
        <input v-model="date" type="date" />
      </div>
      <div>
        <label class="field-label">任务状态</label>
        <p style="margin:0;padding:0.6rem 0">
          <StatusChip :status="status" />
        </p>
      </div>
    </div>
    <label class="field-label">今日非代码工作（可选）</label>
    <textarea v-model="nonCodeWork" placeholder="会议、文档、沟通等" />
    <label class="field-label">明日计划（日报用）</label>
    <textarea v-model="tomorrowPlan" placeholder="每行一条" />
    <div class="action-group">
      <button type="button" class="btn" :disabled="loading" @click="run(api.reports.generateDaily, '日报')">生成日报</button>
      <button type="button" class="btn" :disabled="loading" @click="run(api.reports.generateWeekly, '周报')">生成周报</button>
      <button type="button" class="btn" :disabled="loading" @click="run(api.reports.generateMonthly, '月报')">生成月报</button>
      <button type="button" class="btn btn-secondary" :disabled="loading" @click="exportExcel">导出日报 Excel</button>
    </div>
  </section>
</template>
