<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api'

const projects = ref([])
const form = ref({ name: '', localPath: '', remoteUrl: '', branch: 'main', enabled: true })
const editingId = ref(null)
const msg = ref('')
const err = ref('')

async function load() {
  projects.value = await api.projects.list()
}

onMounted(() => load().catch((e) => (err.value = e.message)))

function resetForm() {
  editingId.value = null
  form.value = { name: '', localPath: '', remoteUrl: '', branch: 'main', enabled: true }
}

async function save() {
  err.value = ''
  try {
    if (editingId.value) {
      await api.projects.update(editingId.value, form.value)
      msg.value = '项目已更新'
    } else {
      await api.projects.create(form.value)
      msg.value = '项目已创建'
    }
    await load()
    resetForm()
  } catch (e) {
    err.value = e.message
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
  await load()
  msg.value = '已删除'
}
</script>

<template>
  <h1>Git 项目</h1>
  <p v-if="err" class="toast err">{{ err }}</p>
  <p v-if="msg" class="toast ok">{{ msg }}</p>

  <div class="card">
    <h2 style="margin-top:0;font-size:1.05rem">{{ editingId ? '编辑项目' : '新增项目' }}</h2>
    <label>名称</label>
    <input v-model="form.name" placeholder="例如：主仓库" />
    <div class="row">
      <div>
        <label>本地路径</label>
        <input v-model="form.localPath" placeholder="/path/to/repo 或 E:/code/project" />
      </div>
      <div>
        <label>远程 URL（可选）</label>
        <input v-model="form.remoteUrl" placeholder="https://github.com/org/repo.git" />
      </div>
    </div>
    <label>分支</label>
    <input v-model="form.branch" />
    <label><input type="checkbox" v-model="form.enabled" style="width:auto;margin-right:0.5rem" />启用采集</label>
    <button class="btn" @click="save">保存</button>
    <button v-if="editingId" class="btn btn-secondary" style="margin-left:0.5rem" @click="resetForm">取消</button>
  </div>

  <div class="card">
    <table v-if="projects.length">
      <thead>
        <tr><th>名称</th><th>路径 / 远程</th><th>分支</th><th>状态</th><th></th></tr>
      </thead>
      <tbody>
        <tr v-for="p in projects" :key="p.id">
          <td>{{ p.name }}</td>
          <td style="max-width:280px;overflow:hidden;text-overflow:ellipsis">{{ p.localPath || p.remoteUrl }}</td>
          <td>{{ p.branch }}</td>
          <td>{{ p.enabled ? '启用' : '停用' }}</td>
          <td>
            <button class="btn btn-secondary" @click="edit(p)">编辑</button>
            <button class="btn btn-danger" style="margin-left:0.35rem" @click="remove(p)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else style="color:var(--muted)">还没有项目。可从仓库根目录的 config.example.yaml 导入后在此管理。</p>
  </div>
</template>
