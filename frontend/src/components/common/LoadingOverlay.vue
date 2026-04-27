<template>
  <div v-if="visible" class="loading-overlay" style="display: flex">
    <div class="loading-content">
      <div class="loading-spinner"></div>
      <div class="loading-text">{{ text }}</div>
      <div class="loading-subtext">{{ subtext }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ visible: boolean; text: string; subtext: string }>()
</script>
