<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api'

const daily = ref([])
const weekly = ref([])
const monthly = ref([])
const msg = ref('')
const err = ref('')

onMounted(async () => {
  try {
    daily.value = await api.reports.dailyList()
    weekly.value = await api.reports.periodList('WEEK')
    monthly.value = await api.reports.periodList('MONTH')
  } catch (e) {
    err.value = e.message
  }
})

function download(path) {
  if (!path) return
  window.open(api.downloadUrl(path), '_blank')
}
</script>

<template>
  <h1>报告总览</h1>
  <p v-if="err" class="toast err">{{ err }}</p>
  <p v-if="msg" class="toast ok">{{ msg }}</p>

  <section class="card">
    <h2 style="margin-top:0;font-size:1.1rem">日报</h2>
    <table v-if="daily.length">
      <thead>
        <tr><th>日期</th><th>状态</th><th>导出</th></tr>
      </thead>
      <tbody>
        <tr v-for="r in daily" :key="r.id">
          <td>{{ r.reportDate }}</td>
          <td><span class="badge">{{ r.status }}</span></td>
          <td>
            <button class="btn btn-secondary" v-if="r.excelPath" @click="download(r.excelPath)">下载</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else style="color:var(--muted)">暂无日报，请在「生成报告」中创建。</p>
  </section>

  <section class="card">
    <h2 style="margin-top:0;font-size:1.1rem">周报</h2>
    <table v-if="weekly.length">
      <thead>
        <tr><th>周期</th><th>状态</th><th>导出</th></tr>
      </thead>
      <tbody>
        <tr v-for="r in weekly" :key="r.id">
          <td>{{ r.startDate }} ~ {{ r.endDate }}</td>
          <td><span class="badge">{{ r.status }}</span></td>
          <td>
            <button class="btn btn-secondary" v-if="r.excelPath" @click="download(r.excelPath)">下载</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else style="color:var(--muted)">暂无周报。</p>
  </section>

  <section class="card">
    <h2 style="margin-top:0;font-size:1.1rem">月报</h2>
    <table v-if="monthly.length">
      <thead>
        <tr><th>周期</th><th>状态</th><th>导出</th></tr>
      </thead>
      <tbody>
        <tr v-for="r in monthly" :key="r.id">
          <td>{{ r.startDate }} ~ {{ r.endDate }}</td>
          <td><span class="badge">{{ r.status }}</span></td>
          <td>
            <button class="btn btn-secondary" v-if="r.excelPath" @click="download(r.excelPath)">下载</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else style="color:var(--muted)">暂无月报。</p>
  </section>
</template>
