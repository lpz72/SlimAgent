<template>
  <div class="chat-history-list">
    <div v-for="history in histories" :key="history.id" class="history-item" @click="$emit('load-history', history.id)">
      <div class="history-item-content">
        <span class="history-item-title">{{ history.title }}</span>
      </div>
      <button class="history-item-delete" type="button" title="删除" @click.stop="$emit('delete-history', history.id)">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ChatHistory } from '@/types'

defineProps<{ histories: ChatHistory[] }>()
defineEmits<{ 'load-history': [id: string]; 'delete-history': [id: string] }>()
</script>
