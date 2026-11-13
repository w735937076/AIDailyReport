<script setup>
defineProps({
  open: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  item: { type: Object, default: null },
})
const emit = defineEmits(['close'])

const typeLabels = {
  FEATURE: '功能开发',
  BUG_FIX: 'Bug修复',
  REFACTOR: '重构优化',
  DOCS: '文档更新',
}
</script>

<template>
  <Teleport to="body">
    <Transition name="drawer-backdrop">
      <div v-if="open" class="report-drawer-backdrop" role="presentation" @click.self="emit('close')">
        <Transition name="drawer-panel">
          <aside v-if="open" class="report-drawer" role="dialog" aria-modal="true" aria-label="工作项详情">
            <header class="report-drawer__head">
              <div>
                <p class="report-drawer__eyebrow">工作项详情</p>
                <h2 class="report-drawer__title">
                  {{ item?.workDate }} · {{ item?.repoName || '—' }}
                </h2>
              </div>
              <button type="button" class="btn btn-ghost btn-sm" @click="emit('close')">关闭</button>
            </header>
            <div class="report-drawer__body">
              <div v-if="loading" class="loading-block">
                <span class="spinner spinner--accent" />
                <span>加载中…</span>
              </div>
              <template v-else-if="item">
                <dl class="work-item-meta">
                  <div><dt>类型</dt><dd>{{ typeLabels[item.type] || item.type }}</dd></div>
                  <div><dt>模块</dt><dd>{{ item.module || '—' }}</dd></div>
                  <div><dt>进度</dt><dd>{{ item.progress || '—' }}</dd></div>
                  <div><dt>置信度</dt><dd>{{ item.confidence != null ? item.confidence : '—' }}</dd></div>
                  <div class="work-item-meta__wide"><dt>提交</dt><dd class="mono">{{ item.commitId || '—' }}</dd></div>
                </dl>
                <label class="field-label">工作计划</label>
                <pre class="report-section__text">{{ item.plan || '（无）' }}</pre>
                <label class="field-label">具体做法</label>
                <pre class="report-section__text">{{ item.practice || '（无）' }}</pre>
                <label class="field-label">预计完成</label>
                <pre class="report-section__text">{{ item.estimate || '（无）' }}</pre>
                <template v-if="item.aiRawResponse">
                  <label class="field-label">AI 原始响应</label>
                  <pre class="report-section__text report-section__text--raw">{{ item.aiRawResponse }}</pre>
                </template>
              </template>
            </div>
          </aside>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.work-item-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.65rem 1rem;
  margin: 0 0 1rem;
  font-size: 0.8125rem;
}
.work-item-meta__wide {
  grid-column: 1 / -1;
}
.work-item-meta dt {
  margin: 0;
  color: var(--faint);
  font-size: 0.6875rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.work-item-meta dd {
  margin: 0.15rem 0 0;
  color: var(--text);
}
.mono {
  font-family: ui-monospace, Menlo, monospace;
  font-size: 0.75rem;
  word-break: break-all;
}
.report-section__text--raw {
  max-height: 14rem;
  overflow: auto;
}
</style>
