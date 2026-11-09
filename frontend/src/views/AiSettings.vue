<script setup>
import { onMounted, ref } from 'vue'
import { api, UNCHANGED_SECRET } from '../api'

const providers = ref([])
const policy = ref({
  maxRetries: 3,
  retryDelayMs: 1000,
  failureThreshold: 2,
  cooldownMs: 120000,
})
const form = ref(emptyForm())
const editingId = ref(null)
const msg = ref('')
const err = ref('')

function emptyForm() {
  return {
    name: '',
    apiKey: '',
    baseUrl: 'https://api.openai.com/v1',
    model: 'gpt-4o-mini',
    connectTimeoutMs: 5000,
    readTimeoutMs: 60000,
    enabled: true,
    priority: 100,
    apiKeyConfigured: false,
  }
}

async function load() {
  providers.value = await api.ai.listProviders()
  const p = await api.ai.getPolicy()
  if (p) Object.assign(policy.value, p)
}

onMounted(() => load().catch((e) => (err.value = e.message)))

function resetForm() {
  editingId.value = null
  form.value = emptyForm()
}

function edit(row) {
  editingId.value = row.id
  form.value = {
    name: row.name,
    apiKey: UNCHANGED_SECRET,
    baseUrl: row.baseUrl,
    model: row.model,
    connectTimeoutMs: row.connectTimeoutMs,
    readTimeoutMs: row.readTimeoutMs,
    enabled: row.enabled,
    priority: row.priority,
    apiKeyConfigured: row.apiKeyConfigured,
  }
}

async function saveProvider() {
  err.value = ''
  try {
    const body = { ...form.value }
    if (editingId.value) {
      await api.ai.updateProvider(editingId.value, body)
      msg.value = '已更新提供商'
    } else {
      await api.ai.createProvider(body)
      msg.value = '已添加提供商'
    }
    await load()
    resetForm()
  } catch (e) {
    err.value = e.message
  }
}

async function savePolicy() {
  err.value = ''
  try {
    await api.ai.savePolicy(policy.value)
    msg.value = '全局重试策略已保存'
  } catch (e) {
    err.value = e.message
  }
}

async function toggleEnabled(row) {
  await api.ai.setEnabled(row.id, !row.enabled)
  await load()
}

async function remove(row) {
  if (!confirm(`删除「${row.name}」？`)) return
  await api.ai.removeProvider(row.id)
  await load()
  msg.value = '已删除'
}

async function nudgePriority(row, delta) {
  await api.ai.updateProvider(row.id, {
    name: row.name,
    baseUrl: row.baseUrl,
    model: row.model,
    connectTimeoutMs: row.connectTimeoutMs,
    readTimeoutMs: row.readTimeoutMs,
    enabled: row.enabled,
    priority: (row.priority || 100) + delta,
    apiKey: UNCHANGED_SECRET,
  })
  await load()
}
</script>

<template>
  <h1>AI 设置</h1>
  <p style="color:var(--muted);margin-top:-0.75rem">
    可配置多个 OpenAI 兼容提供商；<strong>数字越小优先级越高</strong>（先尝试）。失败或冷却中会自动切换下一个已启用配置。
  </p>
  <p v-if="err" class="toast err">{{ err }}</p>
  <p v-if="msg" class="toast ok">{{ msg }}</p>

  <div class="card">
    <h2 style="margin-top:0;font-size:1.05rem">{{ editingId ? '编辑提供商' : '新增提供商' }}</h2>
    <label>名称</label>
    <input v-model="form.name" placeholder="MiniMax / DeepSeek / OpenAI" />
    <label>API Key {{ form.apiKeyConfigured ? '（已配置，留 __UNCHANGED__ 不修改）' : '' }}</label>
    <input v-model="form.apiKey" type="password" :placeholder="form.apiKeyConfigured ? UNCHANGED_SECRET : 'sk-...'" />
    <label>Base URL</label>
    <input v-model="form.baseUrl" />
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
        <label>优先级（越小越先）</label>
        <input v-model.number="form.priority" type="number" />
      </div>
      <div style="display:flex;align-items:flex-end">
        <label><input type="checkbox" v-model="form.enabled" style="width:auto;margin-right:0.5rem" />启用</label>
      </div>
    </div>
    <button class="btn" @click="saveProvider">保存提供商</button>
    <button v-if="editingId" class="btn btn-secondary" style="margin-left:0.5rem" @click="resetForm">取消</button>
  </div>

  <div class="card">
    <h2 style="margin-top:0;font-size:1.05rem">已配置的提供商</h2>
    <table v-if="providers.length">
      <thead>
        <tr>
          <th>优先级</th>
          <th>名称</th>
          <th>模型</th>
          <th>Base URL</th>
          <th>密钥</th>
          <th>状态</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="p in providers" :key="p.id">
          <td>
            <button class="btn btn-secondary" style="padding:0.2rem 0.45rem" @click="nudgePriority(p, -10)">↑</button>
            {{ p.priority }}
            <button class="btn btn-secondary" style="padding:0.2rem 0.45rem" @click="nudgePriority(p, 10)">↓</button>
          </td>
          <td>{{ p.name }}</td>
          <td>{{ p.model }}</td>
          <td style="max-width:200px;overflow:hidden;text-overflow:ellipsis">{{ p.baseUrl }}</td>
          <td>{{ p.apiKeyConfigured ? '已配置' : '未配置' }}</td>
          <td>
            <button class="btn btn-secondary" @click="toggleEnabled(p)">{{ p.enabled ? '启用' : '停用' }}</button>
          </td>
          <td>
            <button class="btn btn-secondary" @click="edit(p)">编辑</button>
            <button class="btn btn-danger" style="margin-left:0.35rem" @click="remove(p)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else style="color:var(--muted)">暂无提供商，请添加至少一个并填写 API Key。</p>
  </div>

  <div class="card">
    <h2 style="margin-top:0;font-size:1.05rem">全局重试 / 冷却</h2>
    <div class="row">
      <div>
        <label>单提供商最大重试（最后一个提供商时生效）</label>
        <input v-model.number="policy.maxRetries" type="number" />
      </div>
      <div>
        <label>重试间隔 (ms)</label>
        <input v-model.number="policy.retryDelayMs" type="number" />
      </div>
    </div>
    <div class="row">
      <div>
        <label>失败阈值（触发冷却）</label>
        <input v-model.number="policy.failureThreshold" type="number" />
      </div>
      <div>
        <label>冷却时长 (ms)</label>
        <input v-model.number="policy.cooldownMs" type="number" />
      </div>
    </div>
    <button class="btn" @click="savePolicy">保存策略</button>
  </div>
</template>
