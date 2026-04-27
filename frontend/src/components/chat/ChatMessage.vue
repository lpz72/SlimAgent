<template>
  <div class="message" :class="[message.type, { streaming: message.streaming, 'aiops-message': message.aiOps }]">
    <div v-if="message.type === 'assistant'" class="message-avatar">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" fill="white" />
      </svg>
    </div>
    <div class="message-content-wrapper">
      <div v-if="message.loading" class="message-content loading-message-content">
        <span>{{ message.content }}</span>
        <span class="loading-spinner-icon">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z" fill="currentColor" opacity="0.2" />
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10c1.54 0 3-.36 4.28-1l-1.5-2.6C13.64 19.62 12.84 20 12 20c-4.41 0-8-3.59-8-8s3.59-8 8-8c.84 0 1.64.38 2.18 1l1.5-2.6C13 2.36 12.54 2 12 2z" fill="currentColor" />
          </svg>
        </span>
      </div>
      <div v-else-if="message.type === 'assistant'" ref="contentEl" class="message-content" v-html="renderedContent"></div>
      <div v-else class="message-content">{{ message.content }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { highlightCodeBlocks, renderMarkdown } from '@/utils/markdown'
import type { ChatMessage } from '@/types'

const props = defineProps<{ message: ChatMessage }>()
const contentEl = ref<HTMLElement | null>(null)
const renderedContent = computed(() => renderMarkdown(props.message.content))

watch(
  () => props.message.content,
  async () => {
    await nextTick()
    if (contentEl.value) highlightCodeBlocks(contentEl.value)
  },
  { immediate: true },
)
</script>
