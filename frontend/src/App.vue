<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">◎</span>
        <span>AIDailyReport</span>
      </div>
      <nav class="nav">
        <router-link to="/"><span class="nav-icon">◆</span>报告总览</router-link>
        <router-link to="/projects"><span class="nav-icon">⎇</span>Git 项目</router-link>
        <router-link to="/ai"><span class="nav-icon">✦</span>AI 设置</router-link>
        <router-link to="/scheduler"><span class="nav-icon">⏱</span>定时采集</router-link>
        <router-link to="/generate"><span class="nav-icon">▶</span>生成报告</router-link>
      </nav>
      <p class="sidebar-foot">Git → 工作项 → AI 报告</p>
    </aside>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>
