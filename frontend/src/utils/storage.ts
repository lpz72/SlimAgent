import type { ChatHistory } from '@/types'

const STORAGE_KEY = 'chatHistories'
const MAX_HISTORY_COUNT = 50
const MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000

export function loadLocalChatHistories(): ChatHistory[] {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    const histories = stored ? (JSON.parse(stored) as ChatHistory[]) : []
    const minTime = Date.now() - MAX_AGE_MS
    return histories.filter((history) => {
      const updatedAt = new Date(history.updatedAt || history.createdAt || 0).getTime()
      return updatedAt >= minTime
    })
  } catch {
    return []
  }
}

export function saveLocalChatHistories(histories: ChatHistory[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(histories.slice(0, MAX_HISTORY_COUNT)))
}

export function clearLocalChatHistories() {
  localStorage.removeItem(STORAGE_KEY)
}

export { MAX_HISTORY_COUNT }
