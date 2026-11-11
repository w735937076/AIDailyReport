<script setup>
import { onUnmounted, toRefs, watch } from 'vue'

const open = defineModel('open', { type: Boolean, default: false })
const nonCodeWork = defineModel('nonCodeWork', { type: String, default: '' })
const tomorrowPlan = defineModel('tomorrowPlan', { type: String, default: '' })

const props = defineProps({
  title: { type: String, default: '生成日报' },
  subtitle: { type: String, default: '填写以下内容后，AI 将据此生成日报正文（可留空）。' },
  confirmLabel: { type: String, default: '开始生成' },
  cancelLabel: { type: String, default: '取消' },
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['confirm', 'cancel'])
const { loading, title, subtitle, confirmLabel, cancelLabel } = toRefs(props)

function onBackdrop(e) {
  if (e.target === e.currentTarget && !loading.value) emit('cancel')
}

function onKeydown(e) {
  if (e.key === 'Escape' && !loading.value) emit('cancel')
}

watch(open, (v) => {
  if (v) document.body.classList.add('confirm-dialog-open')
  else document.body.classList.remove('confirm-dialog-open')
})

onUnmounted(() => {
  document.body.classList.remove('confirm-dialog-open')
})

function onConfirm() {
  emit('confirm', {
    nonCodeWork: nonCodeWork.value,
    tomorrowPlan: tomorrowPlan.value,
  })
}
</script>

<template>
  <Teleport to="body">
    <Transition name="confirm-fade">
      <div
        v-if="open"
        class="confirm-backdrop"
        role="presentation"
        @click="onBackdrop"
        @keydown="onKeydown"
      >
        <Transition name="confirm-pop">
          <div
            v-if="open"
            class="confirm-dialog confirm-dialog--form"
            role="dialog"
            aria-modal="true"
            aria-labelledby="daily-generate-dialog-title"
            tabindex="-1"
            @click.stop
          >
            <header class="confirm-dialog__head">
              <h2 id="daily-generate-dialog-title" class="confirm-dialog__title">{{ title }}</h2>
              <p v-if="subtitle" class="confirm-dialog__hint">{{ subtitle }}</p>
            </header>
            <div class="confirm-dialog__body">
              <label class="field-label">今日非代码工作（可选）</label>
              <textarea
                v-model="nonCodeWork"
                :disabled="loading"
                rows="4"
                placeholder="会议、文档、沟通等"
              />
              <label class="field-label">明日计划</label>
              <textarea
                v-model="tomorrowPlan"
                :disabled="loading"
                rows="4"
                placeholder="每行一条"
              />
            </div>
            <footer class="confirm-dialog__actions">
              <button type="button" class="btn btn-secondary" :disabled="loading" @click="emit('cancel')">
                {{ cancelLabel }}
              </button>
              <button type="button" class="btn primary" :disabled="loading" @click="onConfirm">
                {{ confirmLabel }}
              </button>
            </footer>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>
