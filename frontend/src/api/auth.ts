import { http, unwrapApiResponse } from './client'
import type { AuthMeResponse, AuthUser } from '@/types'

export interface AuthPayload {
  username: string
  password: string
  nickname?: string
}

export async function getCurrentUser() {
  return unwrapApiResponse<AuthMeResponse>(await http.get('/auth/me'))
}

export async function register(payload: AuthPayload) {
  return unwrapApiResponse<AuthUser>(await http.post('/auth/register', payload))
}

export async function login(payload: Pick<AuthPayload, 'username' | 'password'>) {
  return unwrapApiResponse<AuthUser>(await http.post('/auth/login', payload))
}

export async function logout() {
  return unwrapApiResponse<string>(await http.post('/auth/logout'))
}
