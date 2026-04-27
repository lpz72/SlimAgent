<template>
  <div v-if="visible" class="auth-modal" style="display: flex">
    <div class="auth-card">
      <div class="auth-kicker">智能减脂 Agent</div>
      <h1>登录后开始个性化对话</h1>
      <p>系统会结合你的结构化资料、短期对话和长期记忆生成更贴合的减脂建议。</p>
      <div class="auth-tabs">
        <button class="auth-tab" :class="{ active: !isRegister }" type="button" @click="isRegister = false">登录</button>
        <button class="auth-tab" :class="{ active: isRegister }" type="button" @click="isRegister = true">注册</button>
      </div>
      <input v-model.trim="username" class="auth-input" placeholder="用户名" autocomplete="username" />
      <input v-model="password" class="auth-input" placeholder="密码" type="password" autocomplete="current-password" />
      <input v-if="isRegister" v-model.trim="nickname" class="auth-input" placeholder="昵称（注册时可选）" />
      <button class="auth-submit" type="button" @click="submit">{{ isRegister ? '注册并登录' : '登录' }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ login: [username: string, password: string]; register: [username: string, password: string, nickname?: string] }>()
const isRegister = ref(false)
const username = ref('')
const password = ref('')
const nickname = ref('')
function submit() {
  if (isRegister.value) emit('register', username.value, password.value, nickname.value)
  else emit('login', username.value, password.value)
}
void props
</script>
