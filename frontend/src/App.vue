<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">AIDailyReport</div>
      <nav class="nav">
        <router-link to="/">报告总览</router-link>
        <router-link to="/projects">Git 项目</router-link>
        <router-link to="/ai">AI 设置</router-link>
        <router-link to="/scheduler">定时采集</router-link>
        <router-link to="/generate">生成报告</router-link>
      </nav>
    </aside>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>
