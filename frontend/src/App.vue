<template>
  <div class="app-layout">
    <Sidebar :histories="chatHistories" @new-chat="handleNewChat" @load-history="handleLoadHistory" @delete-history="handleDeleteHistory" />
    <main class="main-content">
      <AIOpsButton v-if="featureFlags.aiOps" @trigger="handleAIOps" />
      <ChatContainer
        :messages="messages"
        :mode="currentMode"
        :is-streaming="isStreaming"
        :is-admin="isAdmin"
        :scroll-token="scrollToken"
        @send="handleSend"
        @select-mode="handleSelectMode"
        @upload="handleUpload"
      />
    </main>
  </div>

  <AuthModal :visible="authModalVisible" @login="handleLogin" @register="handleRegister" />
  <UserChip :user="currentUser" @open-profile="handleOpenProfile" />
  <ProfilePanel :visible="profilePanelVisible" :profile="profile" @close="closeProfilePanel" @save="handleSaveProfile" @logout="handleLogout" />
  <LoadingOverlay :visible="loadingVisible" :text="loadingText" :subtext="loadingSubtext" />
</template>

<script setup lang="ts">
import { message } from 'ant-design-vue'
import { onMounted } from 'vue'
import AIOpsButton from '@/components/ai-ops/AIOpsButton.vue'
import AuthModal from '@/components/auth/AuthModal.vue'
import ChatContainer from '@/components/chat/ChatContainer.vue'
import LoadingOverlay from '@/components/common/LoadingOverlay.vue'
import Sidebar from '@/components/layout/Sidebar.vue'
import ProfilePanel from '@/components/profile/ProfilePanel.vue'
import UserChip from '@/components/profile/UserChip.vue'
import { featureFlags } from '@/config/featureFlags'
import { useAuth } from '@/composables/useAuth'
import { useChat } from '@/composables/useChat'
import { useProfile } from '@/composables/useProfile'
import { useUpload } from '@/composables/useUpload'
import type { ChatMode } from '@/types'

const { currentUser, initialProfile, authModalVisible, isAdmin, checkAuthStatus, login, registerAndLogin, logout } = useAuth()
const { profile, profilePanelVisible, assignProfile, openProfilePanel, closeProfilePanel, saveProfile } = useProfile()
const { loadingVisible, loadingText, loadingSubtext, upload } = useUpload()
const {
  currentMode,
  isStreaming,
  messages,
  chatHistories,
  scrollToken,
  addMessage,
  loadSessionSummaries,
  loadChatHistory,
  deleteChatHistory,
  newChat,
  selectMode,
  sendMessage,
  triggerAIOps,
  resetAfterLogout,
} = useChat()

onMounted(async () => {
  const authed = await checkAuthStatus()
  if (initialProfile.value) assignProfile(initialProfile.value)
  if (authed) {
    try {
      await loadSessionSummaries()
    } catch (error) {
      notifyError(error, '加载会话列表失败')
    }
  }
})

async function handleLogin(username: string, password: string) {
  if (!username || !password) {
    message.error('请输入用户名和密码')
    return
  }
  try {
    await login(username, password)
    message.success('登录成功')
    await loadSessionSummaries()
  } catch (error) {
    notifyError(error, '登录失败，账号或密码错误')
  }
}

async function handleRegister(username: string, password: string, nickname?: string) {
  if (!username || !password) {
    message.error('请输入用户名和密码')
    return
  }
  try {
    await registerAndLogin(username, password, nickname)
    message.success('登录成功')
    await loadSessionSummaries()
  } catch (error) {
    notifyError(error, '注册失败')
  }
}

async function handleLogout() {
  try {
    await logout()
  } finally {
    resetAfterLogout()
    closeProfilePanel()
  }
}

async function handleOpenProfile() {
  try {
    await openProfilePanel()
  } catch (error) {
    notifyError(error, '加载资料失败')
  }
}

async function handleSaveProfile() {
  try {
    await saveProfile()
    message.success('资料已保存')
    closeProfilePanel()
  } catch (error) {
    notifyError(error, '保存失败')
  }
}

async function handleSend(content: string) {
  if (!currentUser.value) {
    message.error('请先登录后再对话')
    authModalVisible.value = true
    return
  }
  try {
    await sendMessage(content)
  } catch (error) {
    notifyError(error, '发送消息失败')
    addMessage('assistant', `抱歉，发送消息时出现错误：${getErrorMessage(error)}`)
  }
}

function handleSelectMode(mode: ChatMode) {
  try {
    selectMode(mode)
    message.info(`已切换到${mode === 'stream' ? '流式' : '快速'}模式`)
  } catch (error) {
    notifyError(error, '切换模式失败')
  }
}

function handleNewChat() {
  try {
    newChat()
  } catch (error) {
    notifyError(error, '新建对话失败')
  }
}

async function handleLoadHistory(id: string) {
  try {
    await loadChatHistory(id)
  } catch (error) {
    notifyError(error, '加载历史对话失败')
  }
}

async function handleDeleteHistory(id: string) {
  try {
    await deleteChatHistory(id)
    message.success('会话已删除')
  } catch (error) {
    notifyError(error, '删除历史对话失败')
  }
}

async function handleUpload(file: File) {
  try {
    const result = await upload(file, currentUser.value)
    addMessage('assistant', result)
  } catch (error) {
    notifyError(error, '文件上传失败')
  }
}

async function handleAIOps() {
  try {
    await triggerAIOps()
  } catch (error) {
    notifyError(error, '智能运维分析失败')
  }
}

function notifyError(error: unknown, fallback: string) {
  message.error(getErrorMessage(error) || fallback)
}

function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : String(error || '')
}
</script>
