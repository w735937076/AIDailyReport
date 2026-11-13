<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api'
import { usePagination } from '../composables/usePagination'
import PageHeader from '../components/PageHeader.vue'
import Pagination from '../components/Pagination.vue'
import AppAlert from '../components/AppAlert.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingBlock from '../components/LoadingBlock.vue'

const form = ref({ name: '', localPath: '', remoteUrl: '', branch: 'main', enabled: true })
const editingId = ref(null)
const msg = ref('')
const err = ref('')
const saving = ref(false)

const pager = usePagination(({ page, size }) => api.projects.list(page, size), 10)

onMounted(() => pager.load().catch((e) => (err.value = e.message)))

function resetForm() {
  editingId.value = null
  form.value = { name: '', localPath: '', remoteUrl: '', branch: 'main', enabled: true }
}

async function save() {
  err.value = ''
  saving.value = true
  try {
    if (editingId.value) {
      await api.projects.update(editingId.value, form.value)
      msg.value = '项目已更新'
    } else {
      await api.projects.create(form.value)
      msg.value = '项目已创建'
    }
    await pager.refresh()
    resetForm()
  } catch (e) {
    err.value = e.message
  } finally {
    saving.value = false
  }
}

function edit(p) {
  editingId.value = p.id
  form.value = {
    name: p.name,
    localPath: p.localPath || '',
    remoteUrl: p.remoteUrl || '',
    branch: p.branch || 'main',
    enabled: p.enabled !== false,
  }
}

async function remove(p) {
  if (!confirm(`删除项目「${p.name}」？`)) return
  await api.projects.remove(p.id)
  await pager.refresh()
  msg.value = '已删除'
}
</script>

<template>
  <PageHeader
    title="Git 项目"
    subtitle="管理本地或远程仓库来源，仅启用的项目会参与定时与手动采集。"
  />

  <AppAlert :message="err" type="err" />
  <AppAlert :message="msg" type="ok" />

  <div class="split-layout">
    <aside class="card form-panel">
      <h2 class="card-title">{{ editingId ? '编辑项目' : '新增项目' }}</h2>
      <label class="field-label">名称</label>
      <input v-model="form.name" placeholder="例如：主仓库" />
      <label class="field-label">本地路径</label>
      <input v-model="form.localPath" placeholder="/path/to/repo 或 E:/code/project" />
      <label class="field-label">远程 URL（可选）</label>
      <input v-model="form.remoteUrl" placeholder="https://github.com/org/repo.git" />
      <label class="field-label">分支</label>
      <input v-model="form.branch" />
      <label class="field-check">
        <input type="checkbox" v-model="form.enabled" />
        启用采集
      </label>
      <p class="action-group">
        <button type="button" class="btn" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
        <button v-if="editingId" type="button" class="btn btn-secondary" @click="resetForm">取消</button>
      </p>
    </aside>

    <section class="card list-panel">
      <h2 class="card-title">项目列表</h2>
      <LoadingBlock v-if="pager.loading.value" />
      <EmptyState
        v-else-if="!pager.items.value.length"
        title="还没有项目"
        hint="可从 config.example.yaml 首次导入，或在此新增。"
        icon="⎇"
      />
      <template v-else>
        <div class="table-wrap">
          <table class="data-table table-fluid">
            <thead>
              <tr>
                <th class="col-name">名称</th>
                <th class="col-path">路径 / 远程</th>
                <th class="col-branch">分支</th>
                <th class="col-status">状态</th>
                <th class="col-actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in pager.items.value" :key="p.id">
                <td class="col-name">{{ p.name }}</td>
                <td class="col-path cell-ellipsis cell-muted" :title="p.localPath || p.remoteUrl">{{ p.localPath || p.remoteUrl }}</td>
                <td class="col-branch">{{ p.branch }}</td>
                <td class="col-status">
                  <span class="chip" :class="p.enabled ? 'on' : 'off'">{{ p.enabled ? '启用' : '停用' }}</span>
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
</template>
