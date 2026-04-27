<template>
  <aside class="sidebar">
    <div class="sidebar-header">
      <h2 class="sidebar-title">智能减脂助手</h2>
    </div>
    <div class="sidebar-content">
      <button class="new-chat-btn" type="button" @click="$emit('new-chat')">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M12 5V19M5 12H19" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
        </svg>
        <span>新建对话</span>
      </button>
      <div class="chat-history-section">
        <div class="history-header">
          <span>近期对话</span>
        </div>
        <ChatHistoryList :histories="histories" @load-history="$emit('load-history', $event)" @delete-history="$emit('delete-history', $event)" />
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import ChatHistoryList from '@/components/chat/ChatHistoryList.vue'
import type { ChatHistory } from '@/types'

defineProps<{ histories: ChatHistory[] }>()
defineEmits<{ 'new-chat': []; 'load-history': [id: string]; 'delete-history': [id: string] }>()
</script>
