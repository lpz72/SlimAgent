export type ChatMode = 'quick' | 'stream'
export type MessageType = 'user' | 'assistant'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface AuthUser {
  id?: number
  username: string
  nickname?: string
  role?: string
}

export interface UserProfile {
  gender?: string | null
  age?: number | null
  heightCm?: number | string | null
  weightKg?: number | string | null
  targetWeightKg?: number | string | null
  activityLevel?: string | null
  dietPreference?: string | null
  healthNotes?: string | null
}

export interface AuthMeResponse {
  user: AuthUser
  profile?: UserProfile | null
}

export interface ChatMessage {
  type: MessageType
  content: string
  timestamp?: string
  streaming?: boolean
  loading?: boolean
  aiOps?: boolean
}

export interface ChatHistory {
  id: string
  title: string
  messages: ChatMessage[]
  createdAt: string
  updatedAt: string
  detailLoaded?: boolean
}

export interface ChatResponse {
  success: boolean
  answer?: string
  errorMessage?: string
}

export interface SessionSummary {
  sessionId: string
  title?: string
  createTime?: string
  updateTime?: string
}

export interface BackendSessionMessage {
  role: string
  content?: string
  createdAt?: string
}

export interface SessionInfoResponse {
  sessionId: string
  title?: string
  createTime?: string
  updateTime?: string
  messages?: BackendSessionMessage[]
}

export interface SseMessage {
  type: 'content' | 'done' | 'error' | string
  data?: string | null
}

export interface FileUploadResponse {
  fileName?: string
  message?: string
  [key: string]: unknown
}
