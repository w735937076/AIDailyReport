<script setup>
import { computed } from 'vue'
import { usePerformanceJob, useReportJob, useCollectJob, useInterviewJob } from './composables/useJobRunner'

const perfJob = usePerformanceJob()
const interviewJob = useInterviewJob()
const reportJob = useReportJob()
const collectJob = useCollectJob()

const perfBusy = computed(() => perfJob.preflight.value || perfJob.running.value)
const interviewBusy = computed(() => interviewJob.preflight.value || interviewJob.running.value)
const reportBusy = computed(() => reportJob.running.value)
const collectBusy = computed(() => collectJob.running.value)
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">AR</span>
        <span>AIDailyReport</span>
      </div>
      <nav class="nav">
        <router-link to="/" class="nav-link-busy">
          <span class="nav-icon">▣</span>报告总览
          <span v-if="reportBusy" class="nav-pill-live" title="报告生成中">生成中</span>
        </router-link>
        <router-link to="/work-items" class="nav-link-busy">
          <span class="nav-icon">☰</span>工作项
          <span v-if="collectBusy" class="nav-pill-live" title="Git 采集中">采集中</span>
        </router-link>
        <router-link to="/projects"><span class="nav-icon">⌥</span>Git 项目</router-link>
        <router-link to="/ai"><span class="nav-icon">◇</span>AI 设置</router-link>
        <router-link to="/scheduler"><span class="nav-icon">◷</span>定时采集</router-link>
        <router-link to="/generate"><span class="nav-icon">↗</span>生成报告</router-link>
        <router-link to="/performance" class="nav-link-busy">
          <span class="nav-icon">★</span>绩效评估
          <span v-if="perfBusy" class="nav-pill-live" title="绩效表生成中">生成中</span>
        </router-link>
        <router-link to="/interview" class="nav-link-busy">
          <span class="nav-icon">◆</span>绩效面谈
          <span v-if="interviewBusy" class="nav-pill-live" title="面谈表生成中">生成中</span>
        </router-link>
      </nav>
      <p class="sidebar-foot">Git → 工作项 → AI 报告</p>
    </aside>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>
