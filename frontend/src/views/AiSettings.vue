<script setup>
import { onMounted, ref } from 'vue'
import { api, UNCHANGED_SECRET } from '../api'

const form = ref({
  apiKey: UNCHANGED_SECRET,
  baseUrl: 'https://api.openai.com/v1',
  model: 'gpt-4o-mini',
  connectTimeoutMs: 5000,
  readTimeoutMs: 60000,
  maxRetries: 3,
  retryDelayMs: 1000,
  failureThreshold: 2,
  cooldownMs: 120000,
  fallbackEnabled: false,
  fallbackApiKey: UNCHANGED_SECRET,
  fallbackBaseUrl: '',
  fallbackModel: '',
  fallbackConnectTimeoutMs: 5000,
  fallbackReadTimeoutMs: 60000,
})
const primaryConfigured = ref(false)
const fallbackConfigured = ref(false)
const msg = ref('')
const err = ref('')

onMounted(async () => {
  try {
    const data = await api.ai.get()
    primaryConfigured.value = data.primaryKeyConfigured
    fallbackConfigured.value = data.fallbackKeyConfigured
    Object.assign(form.value, {
      baseUrl: data.baseUrl,
      model: data.model,
      connectTimeoutMs: data.connectTimeoutMs,
      readTimeoutMs: data.readTimeoutMs,
      maxRetries: data.maxRetries,
      retryDelayMs: data.retryDelayMs,
      failureThreshold: data.failureThreshold,
      cooldownMs: data.cooldownMs,
      fallbackEnabled: data.fallbackEnabled,
      fallbackBaseUrl: data.fallbackBaseUrl || '',
      fallbackModel: data.fallbackModel || '',
      fallbackConnectTimeoutMs: data.fallbackConnectTimeoutMs,
      fallbackReadTimeoutMs: data.fallbackReadTimeoutMs,
      apiKey: UNCHANGED_SECRET,
      fallbackApiKey: UNCHANGED_SECRET,
    })
  } catch (e) {
    err.value = e.message
  }
})

async function save() {
  err.value = ''
  try {
    const data = await api.ai.save(form.value)
    primaryConfigured.value = data.primaryKeyConfigured
    fallbackConfigured.value = data.fallbackKeyConfigured
    form.value.apiKey = UNCHANGED_SECRET
    form.value.fallbackApiKey = UNCHANGED_SECRET
    msg.value = 'AI 配置已保存（使用 OpenAI Chat Completions 协议）'
  } catch (e) {
    err.value = e.message
  }
}
</script>

<template>
  <h1>AI 设置</h1>
  <p style="color:var(--muted);margin-top:-0.75rem">兼容 OpenAI、DeepSeek、MiniMax、本地代理等（修改 Base URL 与模型即可）。</p>
  <p v-if="err" class="toast err">{{ err }}</p>
  <p v-if="msg" class="toast ok">{{ msg }}</p>

  <div class="card">
    <h2 style="margin-top:0;font-size:1.05rem">主模型</h2>
    <label>API Key {{ primaryConfigured ? '（已配置，留空不修改）' : '' }}</label>
    <input v-model="form.apiKey" type="password" :placeholder="primaryConfigured ? UNCHANGED_SECRET : 'sk-...'" />
    <label>Base URL</label>
    <input v-model="form.baseUrl" placeholder="https://api.openai.com/v1" />
    <label>模型</label>
    <input v-model="form.model" />
    <div class="row">
      <div>
        <label>连接超时 (ms)</label>
        <input v-model.number="form.connectTimeoutMs" type="number" />
      </div>
      <div>
        <label>读取超时 (ms)</label>
        <input v-model.number="form.readTimeoutMs" type="number" />
      </div>
    </div>
    <div class="row">
      <div>
        <label>最大重试</label>
        <input v-model.number="form.maxRetries" type="number" />
      </div>
      <div>
        <label>失败切换阈值</label>
        <input v-model.number="form.failureThreshold" type="number" />
      </div>
    </div>
    <button class="btn" @click="save">保存配置</button>
  </div>

  <div class="card">
    <h2 style="margin-top:0;font-size:1.05rem">备用模型（可选）</h2>
    <label><input type="checkbox" v-model="form.fallbackEnabled" style="width:auto;margin-right:0.5rem" />启用备用</label>
    <label>备用 API Key {{ fallbackConfigured ? '（已配置）' : '' }}</label>
    <input v-model="form.fallbackApiKey" type="password" />
    <label>备用 Base URL</label>
    <input v-model="form.fallbackBaseUrl" />
    <label>备用模型</label>
    <input v-model="form.fallbackModel" />
    <button class="btn" @click="save">保存配置</button>
  </div>
</template>
