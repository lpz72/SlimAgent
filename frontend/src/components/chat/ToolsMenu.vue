<template>
  <div class="tools-btn-wrapper" :class="{ active: open }">
    <button v-if="isAdmin" class="tools-btn" type="button" title="更多选项" @click.stop="open = !open">
      <svg class="tools-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="12" cy="12" r="1.5" fill="currentColor" />
        <circle cx="19" cy="12" r="1.5" fill="currentColor" />
        <circle cx="5" cy="12" r="1.5" fill="currentColor" />
      </svg>
    </button>
    <div class="tools-menu">
      <div v-if="isAdmin" class="tools-menu-item" @click="pickFile">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        <span>上传文件</span>
      </div>
    </div>
    <input ref="fileInput" type="file" accept=".txt,.md,.markdown" style="display: none" @change="handleFileChange" />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

defineProps<{ isAdmin: boolean }>()
const emit = defineEmits<{ upload: [file: File] }>()
const open = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

function pickFile() {
  fileInput.value?.click()
  open.value = false
}

function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) emit('upload', file)
  target.value = ''
}

function close() {
  open.value = false
}

onMounted(() => document.addEventListener('click', close))
onBeforeUnmount(() => document.removeEventListener('click', close))
</script>
