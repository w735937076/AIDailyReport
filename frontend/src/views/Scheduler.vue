<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api'

const form = ref({
  enabled: true,
  cron: '0 0 18 * * ?',
  timezone: 'Asia/Shanghai',
  gitFilterUsername: '',
})
const collectStatus = ref('IDLE')
const msg = ref('')
const err = ref('')
const loading = ref(false)

onMounted(async () => {
  try {
    const data = await api.scheduler.get()
    if (data) Object.assign(form.value, data)
  } catch (e) {
    err.value = e.message
  }
})

async function save() {
  err.value = ''
  try {
    await api.scheduler.save(form.value)
    msg.value = '调度配置已保存（默认每天 18:00 采集）'
  } catch (e) {
    err.value = e.message
  }
}

async function collectNow() {
  loading.value = true
  err.value = ''
  msg.value = ''
  try {
    const result = await api.collect.now()
    msg.value = result.message || '采集完成'
    collectStatus.value = 'COMPLETED'
  } catch (e) {
    err.value = e.message
    collectStatus.value = 'FAILED'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <h1>定时采集</h1>
  <p v-if="err" class="toast err">{{ err }}</p>
  <p v-if="msg" class="toast ok">{{ msg }}</p>

  <div class="card">
    <label><input type="checkbox" v-model="form.enabled" style="width:auto;margin-right:0.5rem" />启用定时 Git 采集</label>
    <label>Cron 表达式（Spring 6 字段）</label>
    <input v-model="form.cron" placeholder="0 0 18 * * ?" />
    <p style="color:var(--muted);font-size:0.85rem;margin:-0.5rem 0 0.85rem">默认：每天 18:00（Asia/Shanghai）</p>
    <label>时区</label>
    <input v-model="form.timezone" />
    <label>Git 作者过滤（可选，只采集该用户名的提交）</label>
    <input v-model="form.gitFilterUsername" placeholder="与 git config user.name 一致" />
    <button class="btn" @click="save">保存</button>
  </div>

  <div class="card">
    <h2 style="margin-top:0;font-size:1.05rem">手动采集</h2>
    <p style="color:var(--muted)">从所有已启用的项目中拉取提交并由 AI 解析为工作项。</p>
    <button class="btn" :disabled="loading" @click="collectNow">{{ loading ? '采集中…' : '立即采集' }}</button>
    <p v-if="collectStatus !== 'IDLE'" style="margin-top:0.75rem">状态：<span class="badge">{{ collectStatus }}</span></p>
  </div>
</template>
