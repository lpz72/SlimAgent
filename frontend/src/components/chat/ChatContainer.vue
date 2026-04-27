<template>
  <div class="chat-container" :class="{ centered: messages.length === 0 }">
    <div v-if="messages.length === 0" class="welcome-greeting">
      <p>你好！我是智能减脂小助手</p>
    </div>
    <div ref="messagesEl" class="chat-messages">
      <ChatMessage v-for="(item, index) in messages" :key="`${index}-${item.timestamp || ''}`" :message="item" />
    </div>
    <ChatInput :mode="mode" :disabled="isStreaming" :is-admin="isAdmin" @send="$emit('send', $event)" @select-mode="$emit('select-mode', $event)" @upload="$emit('upload', $event)" />
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import ChatInput from './ChatInput.vue'
import ChatMessage from './ChatMessage.vue'
import type { ChatMessage as ChatMessageType, ChatMode } from '@/types'

const props = defineProps<{
  messages: ChatMessageType[]
  mode: ChatMode
  isStreaming: boolean
  isAdmin: boolean
  scrollToken: number
}>()

defineEmits<{ send: [message: string]; 'select-mode': [mode: ChatMode]; upload: [file: File] }>()

const messagesEl = ref<HTMLElement | null>(null)

watch(
  () => [props.messages.length, props.scrollToken],
  async () => {
    await nextTick()
    if (messagesEl.value) messagesEl.value.scrollTop = messagesEl.value.scrollHeight
  },
  { deep: true },
)
</script>
