<script setup>
defineProps({ label: { type: String, default: '加载中…' } })
</script>

<template>
  <div class="loading-block" role="status">
    <span class="spinner" />
    <span>{{ label }}</span>
  </div>
</template>
