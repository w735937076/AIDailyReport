<script setup>
defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, default: '请确认' },
  message: { type: String, default: '' },
  confirmLabel: { type: String, default: '确定' },
  cancelLabel: { type: String, default: '取消' },
  danger: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['confirm', 'cancel'])

function onBackdrop(e) {
  if (e.target === e.currentTarget) emit('cancel')
}

function onKeydown(e) {
  if (e.key === 'Escape') emit('cancel')
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
            class="confirm-dialog"
            role="alertdialog"
            aria-modal="true"
            :aria-labelledby="open ? 'confirm-dialog-title' : undefined"
            tabindex="-1"
            @click.stop
          >
            <header class="confirm-dialog__head">
              <h2 id="confirm-dialog-title" class="confirm-dialog__title">{{ title }}</h2>
            </header>
            <p class="confirm-dialog__message">{{ message }}</p>
            <footer class="confirm-dialog__actions">
              <button
                type="button"
                class="btn btn-secondary"
                :disabled="loading"
                @click="emit('cancel')"
              >
                {{ cancelLabel }}
              </button>
              <button
                type="button"
                class="btn"
                :class="danger ? 'btn-danger' : 'primary'"
                :disabled="loading"
                @click="emit('confirm')"
              >
                {{ confirmLabel }}
              </button>
            </footer>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>
