import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import { clearLocalChatHistories } from '@/utils/storage'
import type { AuthUser, UserProfile } from '@/types'

const currentUser = ref<AuthUser | null>(null)
const initialProfile = ref<UserProfile | null>(null)
const authModalVisible = ref(false)

export function useAuth() {
  const isAdmin = computed(() => String(currentUser.value?.role || '').toUpperCase() === 'ADMIN')

  async function checkAuthStatus() {
    try {
      const data = await authApi.getCurrentUser()
      currentUser.value = data.user
      initialProfile.value = data.profile || null
      authModalVisible.value = false
      return true
    } catch {
      currentUser.value = null
      initialProfile.value = null
      authModalVisible.value = true
      return false
    }
  }

  async function login(username: string, password: string) {
    currentUser.value = await authApi.login({ username, password })
    authModalVisible.value = false
  }

  async function registerAndLogin(username: string, password: string, nickname?: string) {
    await authApi.register({ username, password, nickname })
    await login(username, password)
  }

  async function logout() {
    await authApi.logout()
    currentUser.value = null
    initialProfile.value = null
    clearLocalChatHistories()
    authModalVisible.value = true
  }

  return {
    currentUser,
    initialProfile,
    authModalVisible,
    isAdmin,
    checkAuthStatus,
    login,
    registerAndLogin,
    logout,
  }
}
