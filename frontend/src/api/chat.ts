import { http, unwrapApiResponse } from './client'
import { postSseStream } from './stream'
import type { StreamCallbacks } from './stream'
import type { ChatResponse, SessionInfoResponse, SessionSummary } from '@/types'

export async function quickChat(id: string, question: string) {
  return unwrapApiResponse<ChatResponse>(await http.post('/chat', { id, question }))
}

export function streamChat(id: string, question: string, callbacks: StreamCallbacks) {
  return postSseStream('/chat_stream', { id, question }, callbacks)
}

export async function getSessionSummaries() {
  return unwrapApiResponse<SessionSummary[]>(await http.get('/chat/sessions'))
}

export async function getSessionInfo(sessionId: string) {
  return unwrapApiResponse<SessionInfoResponse>(await http.get(`/chat/session/${encodeURIComponent(sessionId)}`))
}

export async function clearSession(id: string) {
  return unwrapApiResponse<string>(await http.post('/chat/clear', { id }))
}
