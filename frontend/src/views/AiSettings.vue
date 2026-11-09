<script setup>
import { onMounted, ref } from 'vue'
import { api, UNCHANGED_SECRET } from '../api'
import { usePagination } from '../composables/usePagination'
import PageHeader from '../components/PageHeader.vue'
import Pagination from '../components/Pagination.vue'
import AppAlert from '../components/AppAlert.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingBlock from '../components/LoadingBlock.vue'

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
const saving = ref(false)

const pager = usePagination(({ page, size }) => api.ai.listProviders(page, size), 10)

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

async function loadAll() {
  const p = await api.ai.getPolicy()
  if (p) Object.assign(policy.value, p)
  await pager.load()
}

onMounted(() => loadAll().catch((e) => (err.value = e.message)))

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
  saving.value = true
  try {
    const body = { ...form.value }
    if (editingId.value) {
      await api.ai.updateProvider(editingId.value, body)
      msg.value = '已更新提供商'
    } else {
      await api.ai.createProvider(body)
      msg.value = '已添加提供商'
    }
    await pager.refresh()
    resetForm()
  } catch (e) {
    err.value = e.message
  } finally {
    saving.value = false
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
  await pager.refresh()
}

async function remove(row) {
  if (!confirm(`删除「${row.name}」？`)) return
  await api.ai.removeProvider(row.id)
  await pager.refresh()
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
  await pager.refresh()
}
</script>

<template>
  <PageHeader
    title="AI 设置"
    subtitle="可配置多个 OpenAI 兼容提供商。数字越小优先级越高；失败或冷却时自动切换下一项。"
  />

  <AppAlert :message="err" type="err" />
  <AppAlert :message="msg" type="ok" />

  <div class="split-layout">
    <aside class="card form-panel">
      <h2 class="card-title">{{ editingId ? '编辑提供商' : '新增提供商' }}</h2>
      <label class="field-label">名称</label>
      <input v-model="form.name" placeholder="MiniMax / DeepSeek / OpenAI" />
      <label class="field-label">API Key {{ form.apiKeyConfigured ? '（已配置，用 __UNCHANGED__ 保留）' : '' }}</label>
      <input v-model="form.apiKey" type="password" :placeholder="form.apiKeyConfigured ? UNCHANGED_SECRET : 'sk-...'" />
      <label class="field-label">Base URL</label>
      <input v-model="form.baseUrl" />
      <label class="field-label">模型</label>
      <input v-model="form.model" />
      <div class="row">
        <div>
          <label class="field-label">连接超时 (ms)</label>
          <input v-model.number="form.connectTimeoutMs" type="number" />
        </div>
        <div>
          <label class="field-label">读取超时 (ms)</label>
          <input v-model.number="form.readTimeoutMs" type="number" />
        </div>
      </div>
      <div class="row">
        <div>
          <label class="field-label">优先级（越小越先）</label>
          <input v-model.number="form.priority" type="number" />
        </div>
        <label class="field-check" style="align-self:flex-end;padding-bottom:1rem">
          <input type="checkbox" v-model="form.enabled" />
          启用
        </label>
      </div>
      <p class="action-group">
        <button type="button" class="btn" :disabled="saving" @click="saveProvider">保存提供商</button>
        <button v-if="editingId" type="button" class="btn btn-secondary" @click="resetForm">取消</button>
      </p>
    </aside>

    <section class="card list-panel">
      <h2 class="card-title">已配置的提供商</h2>
      <LoadingBlock v-if="pager.loading.value" />
      <EmptyState v-else-if="!pager.items.value.length" title="暂无提供商" hint="请添加至少一个并配置 API Key。" icon="✦" />
      <template v-else>
        <div class="table-wrap">
          <table class="data-table table-fluid">
            <thead>
              <tr>
                <th class="col-priority">优先级</th>
                <th class="col-name">名称</th>
                <th class="col-model">模型</th>
                <th class="col-path">Base URL</th>
                <th class="col-status">密钥</th>
                <th class="col-status">状态</th>
                <th class="col-actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in pager.items.value" :key="p.id">
                <td class="col-priority">
                  <button type="button" class="btn btn-ghost btn-icon" title="提高优先级" @click="nudgePriority(p, -10)">↑</button>
                  {{ p.priority }}
                  <button type="button" class="btn btn-ghost btn-icon" title="降低优先级" @click="nudgePriority(p, 10)">↓</button>
                </td>
                <td class="col-name">{{ p.name }}</td>
                <td class="col-model">{{ p.model }}</td>
                <td class="col-path cell-ellipsis cell-muted" :title="p.baseUrl">{{ p.baseUrl }}</td>
                <td class="col-status">{{ p.apiKeyConfigured ? '已配置' : '未配置' }}</td>
                <td class="col-status">
                  <button type="button" class="btn btn-secondary btn-sm" @click="toggleEnabled(p)">
                    {{ p.enabled ? '启用' : '停用' }}
                  </button>
                </td>
                <td class="col-actions">
                  <div class="toolbar-actions">
                    <button type="button" class="btn btn-ghost btn-sm" @click="edit(p)">编辑</button>
                    <button type="button" class="btn btn-danger btn-sm" @click="remove(p)">删除</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <Pagination
          :page="pager.page.value"
          :size="pager.size.value"
          :total="pager.total.value"
          @update:page="pager.goToPage"
          @update:size="pager.changeSize"
        />
      </template>
    </section>
  </div>

  <section class="card">
    <h2 class="card-title">全局重试 / 冷却</h2>
    <div class="row">
      <div>
        <label class="field-label">单提供商最大重试（链末生效）</label>
        <input v-model.number="policy.maxRetries" type="number" />
      </div>
      <div>
        <label class="field-label">重试间隔 (ms)</label>
        <input v-model.number="policy.retryDelayMs" type="number" />
      </div>
    </div>
    <div class="row">
      <div>
        <label class="field-label">失败阈值（触发冷却）</label>
        <input v-model.number="policy.failureThreshold" type="number" />
      </div>
      <div>
        <label class="field-label">冷却时长 (ms)</label>
        <input v-model.number="policy.cooldownMs" type="number" />
      </div>
    </div>
    <button type="button" class="btn" @click="savePolicy">保存策略</button>
  </section>
</template>