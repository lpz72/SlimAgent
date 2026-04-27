import { http, unwrapApiResponse } from './client'
import type { UserProfile } from '@/types'

export async function getProfile() {
  return unwrapApiResponse<UserProfile>(await http.get('/profile'))
}

export async function saveProfile(profile: UserProfile) {
  return unwrapApiResponse<UserProfile>(await http.post('/profile', profile))
}
