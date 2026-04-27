import { apiBaseUrl } from './client'
import type { SseMessage } from '@/types'

export interface StreamCallbacks {
  onContent?: (content: string) => void
  onDone?: () => void
  onError?: (error: Error) => void
  signal?: AbortSignal
}

export async function postSseStream(path: string, body: unknown, callbacks: StreamCallbacks = {}) {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    method: 'POST',
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: callbacks.signal,
  })

  if (!response.ok) {
    throw new Error(`HTTP错误: ${response.status}`)
  }
  if (!response.body) {
    throw new Error('浏览器不支持流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let finished = false

  try {
    while (!finished) {
      const { done, value } = await reader.read()
      if (done) {
        if (buffer.trim()) {
          finished = processSseBuffer(buffer, callbacks)
          buffer = ''
        }
        if (!finished) callbacks.onDone?.()
        break
      }

      buffer += decoder.decode(value, { stream: true })
      const parts = buffer.split(/\r?\n\r?\n/)
      buffer = parts.pop() || ''

      for (const part of parts) {
        finished = processSseBuffer(part, callbacks)
        if (finished) break
      }
    }
  } catch (error) {
    const normalized = error instanceof Error ? error : new Error(String(error))
    callbacks.onError?.(normalized)
    throw normalized
  } finally {
    reader.releaseLock()
  }
}

function processSseBuffer(eventBlock: string, callbacks: StreamCallbacks) {
  const dataLines: string[] = []

  for (const line of eventBlock.split(/\r?\n/)) {
    if (!line || line.startsWith(':') || line.startsWith('id:') || line.startsWith('event:') || line.startsWith('retry:')) continue
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''))
    }
  }

  if (dataLines.length === 0) return false
  const rawData = dataLines.join('\n')
  return processSseData(rawData, callbacks)
}

function processSseData(rawData: string, callbacks: StreamCallbacks) {
  if (rawData === '[DONE]') {
    callbacks.onDone?.()
    return true
  }

  const jsonMessages = extractJsonMessages(rawData)
  if (jsonMessages.length > 0) {
    for (const message of jsonMessages) {
      if (handleSseMessage(message, callbacks)) return true
    }
    return false
  }

  try {
    const parsed = JSON.parse(rawData) as SseMessage
    return handleSseMessage(parsed, callbacks)
  } catch {
    callbacks.onContent?.(rawData)
    return false
  }
}

function handleSseMessage(message: SseMessage, callbacks: StreamCallbacks) {
  if (message.type === 'content') {
    callbacks.onContent?.(message.data || '')
    return false
  }

  if (message.type === 'done') {
    callbacks.onDone?.()
    return true
  }

  if (message.type === 'error') {
    throw new Error(message.data || '流式请求失败')
  }

  callbacks.onContent?.(message.data || '')
  return false
}

function extractJsonMessages(data: string): SseMessage[] {
  const matches = data.match(/\{"type"\s*:\s*"[^"]+"\s*,\s*"data"\s*:\s*(?:"(?:[^"\\]|\\.)*"|null)\}/g)
  if (!matches) return []

  return matches.flatMap((item) => {
    try {
      return [JSON.parse(item) as SseMessage]
    } catch {
      return []
    }
  })
}
