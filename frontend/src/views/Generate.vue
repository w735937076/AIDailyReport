<script setup>
import { ref } from 'vue'
import { api } from '../api'

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
  try {
    const { path } = await api.reports.exportDailyExcel(date.value)
    window.open(api.downloadUrl(path), '_blank')
  } catch (e) {
    err.value = e.message
  }
}
</script>

<template>
  <h1>生成报告</h1>
  <p v-if="err" class="toast err">{{ err }}</p>
  <p v-if="msg" class="toast ok">{{ msg }}</p>

  <div class="card">
    <label>基准日期</label>
    <input v-model="date" type="date" />
    <label>今日非代码工作（可选）</label>
    <textarea v-model="nonCodeWork" placeholder="会议、文档、沟通等" />
    <label>明日计划（日报用）</label>
    <textarea v-model="tomorrowPlan" placeholder="每行一条" />
    <p v-if="status !== 'IDLE'">状态：<span class="badge">{{ status }}</span></p>
    <div style="display:flex;flex-wrap:wrap;gap:0.5rem;margin-top:0.5rem">
      <button class="btn" :disabled="loading" @click="run(api.reports.generateDaily, '日报')">生成日报</button>
      <button class="btn" :disabled="loading" @click="run(api.reports.generateWeekly, '周报')">生成周报</button>
      <button class="btn" :disabled="loading" @click="run(api.reports.generateMonthly, '月报')">生成月报</button>
      <button class="btn btn-secondary" @click="exportExcel">导出日报 Excel</button>
    </div>
  </div>
</template>
