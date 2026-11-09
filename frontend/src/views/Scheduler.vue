<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api'
import PageHeader from '../components/PageHeader.vue'
import AppAlert from '../components/AppAlert.vue'
import StatusChip from '../components/StatusChip.vue'

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
const saving = ref(false)

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
  saving.value = true
  try {
    await api.scheduler.save(form.value)
    msg.value = '调度配置已保存（默认每天 18:00 采集）'
  } catch (e) {
    err.value = e.message
  } finally {
    saving.value = false
  }
}

async function collectNow() {
  loading.value = true
  err.value = ''
  msg.value = ''
  collectStatus.value = 'RUNNING'
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
  <PageHeader
    title="定时采集"
    subtitle="按计划从已启用的 Git 项目拉取提交并由 AI 解析为工作项；也可随时手动触发。"
  />

  <AppAlert :message="err" type="err" />
  <AppAlert :message="msg" type="ok" />

  <div class="row" style="grid-template-columns:1fr 1fr">
    <section class="card">
      <h2 class="card-title">调度规则</h2>
      <label class="field-check">
        <input type="checkbox" v-model="form.enabled" />
        启用定时 Git 采集
      </label>
      <label class="field-label">Cron 表达式（Spring 6 字段）</label>
      <input v-model="form.cron" placeholder="0 0 18 * * ?" />
      <p class="field-hint">默认：每天 18:00（Asia/Shanghai）</p>
      <label class="field-label">时区</label>
      <input v-model="form.timezone" />
      <label class="field-label">Git 作者过滤（可选）</label>
      <input v-model="form.gitFilterUsername" placeholder="与 git config user.name 一致" />
      <button type="button" class="btn" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存配置' }}</button>
    </section>

    <section class="card">
      <h2 class="card-title">手动采集</h2>
      <p class="field-hint" style="margin-bottom:1.25rem">立即从所有已启用项目执行一轮采集与 AI 解析。</p>
      <button type="button" class="btn" :disabled="loading" @click="collectNow">
        {{ loading ? '采集中…' : '立即采集' }}
      </button>
      <p v-if="collectStatus !== 'IDLE'" style="margin-top:1rem;display:flex;align-items:center;gap:0.5rem">
        状态 <StatusChip :status="collectStatus" />
      </p>
    </section>
  </div>
</template>

<style scoped>
.field-hint {
  font-size: 0.85rem;
  color: var(--muted);
  margin: -0.5rem 0 1rem;
}
</style>
