<script setup>
defineProps({
  type: { type: String, default: 'info' },
  message: { type: String, default: '' },
})
</script>

<template>
  <div v-if="message" class="alert" :class="type" role="alert">{{ message }}</div>
</template>
