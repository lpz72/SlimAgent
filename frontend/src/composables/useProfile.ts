import { reactive, ref } from 'vue'
import * as profileApi from '@/api/profile'
import type { UserProfile } from '@/types'

const profile = reactive<UserProfile>({})
const profilePanelVisible = ref(false)

function assignProfile(data?: UserProfile | null) {
  profile.gender = data?.gender || ''
  profile.age = data?.age ?? null
  profile.heightCm = data?.heightCm ?? null
  profile.weightKg = data?.weightKg ?? null
  profile.targetWeightKg = data?.targetWeightKg ?? null
  profile.activityLevel = data?.activityLevel || ''
  profile.dietPreference = data?.dietPreference || ''
  profile.healthNotes = data?.healthNotes || ''
}

export function useProfile() {
  async function openProfilePanel() {
    profilePanelVisible.value = true
    await loadProfile()
  }

  function closeProfilePanel() {
    profilePanelVisible.value = false
  }

  async function loadProfile() {
    const data = await profileApi.getProfile()
    assignProfile(data)
  }

  async function saveProfile() {
    const saved = await profileApi.saveProfile({
      gender: profile.gender || null,
      age: profile.age ? Number(profile.age) : null,
      heightCm: profile.heightCm ? Number(profile.heightCm) : null,
      weightKg: profile.weightKg ? Number(profile.weightKg) : null,
      targetWeightKg: profile.targetWeightKg ? Number(profile.targetWeightKg) : null,
      activityLevel: profile.activityLevel || null,
      dietPreference: profile.dietPreference || null,
      healthNotes: profile.healthNotes || null,
    })
    assignProfile(saved)
  }

  return {
    profile,
    profilePanelVisible,
    assignProfile,
    openProfilePanel,
    closeProfilePanel,
    loadProfile,
    saveProfile,
  }
}
