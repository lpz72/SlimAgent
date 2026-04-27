<template>
  <button v-if="user" class="user-chip" type="button" style="display: block" @click="$emit('open-profile')">
    {{ user.nickname || user.username }} · 资料
  </button>
</template>

<script setup lang="ts">
import type { AuthUser } from '@/types'

defineProps<{ user: AuthUser | null }>()
defineEmits<{ 'open-profile': [] }>()
</script>
