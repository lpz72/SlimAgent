<template>
  <div class="mode-selector-wrapper" :class="{ active: open }">
    <button class="mode-selector-btn" type="button" @click.stop="open = !open">
      <span>{{ modelValue === 'stream' ? '流式' : '快速' }}</span>
      <svg class="dropdown-arrow" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M6 9L12 15L18 9" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </button>
    <div class="mode-dropdown">
      <div class="dropdown-header">选择对话方式</div>
      <div class="dropdown-item" :class="{ active: modelValue === 'quick' }" @click="select('quick')">
        <div class="dropdown-item-main">
          <span>快速</span>
          <span class="badge-new">新</span>
        </div>
        <div class="dropdown-item-sub">快速对话</div>
      </div>
      <div class="dropdown-item" :class="{ active: modelValue === 'stream' }" @click="select('stream')">
        <div class="dropdown-item-main">
          <span>流式</span>
        </div>
        <div class="dropdown-item-sub">流式对话</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import type { ChatMode } from '@/types'

defineProps<{ modelValue: ChatMode }>()
const emit = defineEmits<{ 'update:model-value': [mode: ChatMode] }>()
const open = ref(false)

function select(mode: ChatMode) {
  emit('update:model-value', mode)
  open.value = false
}

function close() {
  open.value = false
}

onMounted(() => document.addEventListener('click', close))
onBeforeUnmount(() => document.removeEventListener('click', close))
</script>
