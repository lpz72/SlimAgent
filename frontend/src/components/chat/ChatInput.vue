<template>
  <div class="chat-input-container">
    <div class="input-group-wrapper">
      <div class="input-wrapper">
        <textarea ref="textareaEl" v-model="message" rows="1" placeholder="问问智能减脂助手" maxlength="1000" class="message-input" :disabled="disabled" @input="resizeTextarea" @keydown.enter.exact.prevent="submit" />
        <div class="input-bottom-bar">
          <ToolsMenu :is-admin="isAdmin" @upload="$emit('upload', $event)" />
          <div class="right-actions">
            <ModeSelector :model-value="mode" @update:model-value="$emit('select-mode', $event)" />
            <button class="send-btn-circle" type="button" title="发送" :disabled="disabled" @click="submit">
              <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M22 2L11 13M22 2L15 22L11 13M22 2L2 9L11 13" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import ModeSelector from './ModeSelector.vue'
import ToolsMenu from './ToolsMenu.vue'
import type { ChatMode } from '@/types'

defineProps<{ mode: ChatMode; disabled: boolean; isAdmin: boolean }>()
const emit = defineEmits<{ send: [message: string]; 'select-mode': [mode: ChatMode]; upload: [file: File] }>()
const message = ref('')
const textareaEl = ref<HTMLTextAreaElement | null>(null)

function resizeTextarea() {
  const textarea = textareaEl.value
  if (!textarea) return
  textarea.style.height = 'auto'
  textarea.style.height = `${textarea.scrollHeight}px`
}

async function submit() {
  if (!message.value.trim()) return
  emit('send', message.value)
  message.value = ''
  await nextTick()
  resizeTextarea()
}
</script>
