import { computed, reactive, ref, toRaw } from 'vue'
import * as chatApi from '@/api/chat'
import * as aiOpsApi from '@/api/aiOps'
import { loadLocalChatHistories, saveLocalChatHistories, MAX_HISTORY_COUNT } from '@/utils/storage'
import type { ChatHistory, ChatMessage, ChatMode, SessionInfoResponse, SessionSummary } from '@/types'

const currentMode = ref<ChatMode>('quick')
const isStreaming = ref(false)
const messages = ref<ChatMessage[]>([])
const chatHistories = ref<ChatHistory[]>(loadLocalChatHistories())
const sessionId = ref(generateSessionId())
const isCurrentChatFromHistory = ref(false)
const scrollToken = ref(0)

const hasMessages = computed(() => messages.value.length > 0)

export function useChat() {
  function addMessage(type: ChatMessage['type'], content: string, options: Partial<ChatMessage> = {}) {
    const item = reactive<ChatMessage>({
      type,
      content,
      timestamp: options.timestamp || new Date().toISOString(),
      streaming: options.streaming,
      loading: options.loading,
      aiOps: options.aiOps,
    })
    messages.value.push(item)
    if (!item.streaming && !item.loading && content) {
      persistMessageToCurrentHistory(item)
    }
    scrollToken.value++
    return item
  }

  async function loadSessionSummaries() {
    const summaries = await chatApi.getSessionSummaries()
    mergeSessionSummaries(summaries)
    persistHistories()
  }

  async function loadChatHistory(id: string) {
    if (isStreaming.value) throw new Error('请等待当前对话完成后再切换会话')
    let history = chatHistories.value.find((item) => item.id === id)
    if (!history?.detailLoaded || history.messages.length === 0) {
      history = await fetchAndCacheChatHistory(id)
    }
    if (!history) throw new Error('会话不存在或已失效')

    saveOrUpdateCurrentChat()
    sessionId.value = history.id
    messages.value = history.messages.map((item) => ({ ...item }))
    currentMode.value = 'quick'
    isCurrentChatFromHistory.value = true
    scrollToken.value++
  }

  async function deleteChatHistory(id: string) {
    chatHistories.value = chatHistories.value.filter((history) => history.id !== id)
    persistHistories()
    if (sessionId.value === id) {
      resetCurrentChat()
    }
    await chatApi.clearSession(id)
  }

  function newChat() {
    if (isStreaming.value) throw new Error('请等待当前对话完成后再新建对话')
    saveOrUpdateCurrentChat()
    resetCurrentChat()
  }

  function selectMode(mode: ChatMode) {
    if (isStreaming.value) throw new Error('请等待当前对话完成后再切换模式')
    currentMode.value = mode
  }

  async function sendMessage(content: string) {
    const question = content.trim()
    if (!question) throw new Error('请输入消息内容')
    if (isStreaming.value) throw new Error('请等待当前对话完成')

    addMessage('user', question)
    isStreaming.value = true
    try {
      if (currentMode.value === 'stream') {
        await sendStreamMessage(question)
      } else {
        await sendQuickMessage(question)
      }
      saveOrUpdateCurrentChat()
    } finally {
      isStreaming.value = false
    }
  }

  async function triggerAIOps() {
    if (isStreaming.value) throw new Error('请等待当前操作完成')
    newChat()
    const assistant = addMessage('assistant', '分析中...', { loading: true, aiOps: true })
    removePersistedMessage(assistant)
    isStreaming.value = true
    let fullResponse = ''

    try {
      await aiOpsApi.startAIOpsStream({
        onContent(content) {
          fullResponse += content
          assistant.loading = false
          assistant.content = fullResponse
          assistant.aiOps = true
          scrollToken.value++
        },
        onDone() {
          assistant.loading = false
          assistant.streaming = false
          assistant.content = fullResponse || '智能运维分析完成，但没有返回内容。'
          assistant.aiOps = true
          persistMessageToCurrentHistory(assistant)
        },
        onError(error) {
          assistant.loading = false
          assistant.content = `抱歉，智能运维分析时出现错误：${error.message}`
        },
      })
      saveOrUpdateCurrentChat()
    } finally {
      isStreaming.value = false
    }
  }

  function resetAfterLogout() {
    chatHistories.value = []
    resetCurrentChat()
  }

  return {
    currentMode,
    isStreaming,
    messages,
    chatHistories,
    hasMessages,
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
  }
}

async function sendQuickMessage(question: string) {
  const loading = addEphemeralAssistantMessage('正在思考...')
  try {
    const response = await chatApi.quickChat(sessionId.value, question)
    removeMessage(loading)
    if (response.success) {
      addStoredAssistantMessage(response.answer || '（无回复内容）')
      return
    }
    throw new Error(response.errorMessage || '请求失败')
  } catch (error) {
    removeMessage(loading)
    throw error
  }
}

async function sendStreamMessage(question: string) {
  const assistant = addEphemeralAssistantMessage('', { streaming: true })
  let fullResponse = ''
  await chatApi.streamChat(sessionId.value, question, {
    onContent(content) {
      fullResponse += content
      assistant.loading = false
      assistant.content = fullResponse
      scrollToken.value++
    },
    onDone() {
      assistant.streaming = false
      assistant.loading = false
      assistant.content = fullResponse || '（无回复内容）'
      persistMessageToCurrentHistory(assistant)
      scrollToken.value++
    },
    onError(error) {
      assistant.streaming = false
      assistant.loading = false
      assistant.content = `错误: ${error.message}`
      scrollToken.value++
    },
  })
}

function addEphemeralAssistantMessage(content: string, options: Partial<ChatMessage> = {}) {
  const item = reactive<ChatMessage>({
    type: 'assistant',
    content,
    timestamp: new Date().toISOString(),
    loading: options.loading ?? false,
    streaming: options.streaming,
    aiOps: options.aiOps,
  })
  messages.value.push(item)
  scrollToken.value++
  return item
}

function addStoredAssistantMessage(content: string) {
  const item = reactive<ChatMessage>({
    type: 'assistant',
    content,
    timestamp: new Date().toISOString(),
  })
  messages.value.push(item)
  persistMessageToCurrentHistory(item)
  scrollToken.value++
}

function removeMessage(message: ChatMessage) {
  const index = messages.value.findIndex((item) => item === message || toRaw(item) === message || item.timestamp === message.timestamp)
  if (index >= 0) messages.value.splice(index, 1)
  scrollToken.value++
}

function removePersistedMessage(message: ChatMessage) {
  const history = chatHistories.value.find((item) => item.id === sessionId.value)
  if (!history) return
  history.messages = history.messages.filter((item) => item !== message)
}

function persistMessageToCurrentHistory(message: ChatMessage) {
  const cleanMessage: ChatMessage = {
    type: message.type,
    content: message.content,
    timestamp: message.timestamp || new Date().toISOString(),
    aiOps: message.aiOps,
  }
  let history = chatHistories.value.find((item) => item.id === sessionId.value)
  if (!history) {
    history = createHistoryFromMessages(sessionId.value, [cleanMessage])
    chatHistories.value.unshift(history)
  } else {
    const duplicate = history.messages.some((item) => item.timestamp === cleanMessage.timestamp && item.content === cleanMessage.content && item.type === cleanMessage.type)
    if (!duplicate) history.messages.push(cleanMessage)
    history.title = buildChatTitle(history.messages)
    history.updatedAt = new Date().toISOString()
    history.detailLoaded = true
  }
  trimHistories()
  persistHistories()
}

function saveOrUpdateCurrentChat() {
  if (messages.value.length === 0) return
  const cleanMessages = messages.value
    .filter((item) => !item.loading && !item.streaming && item.content)
    .map((item) => ({
      type: item.type,
      content: item.content,
      timestamp: item.timestamp || new Date().toISOString(),
      aiOps: item.aiOps,
    }))
  if (cleanMessages.length === 0) return

  const existingIndex = chatHistories.value.findIndex((item) => item.id === sessionId.value)
  const history = createHistoryFromMessages(sessionId.value, cleanMessages)
  if (existingIndex >= 0) {
    chatHistories.value[existingIndex] = { ...chatHistories.value[existingIndex], ...history, detailLoaded: true }
  } else {
    chatHistories.value.unshift(history)
  }
  isCurrentChatFromHistory.value = true
  trimHistories()
  persistHistories()
}

function createHistoryFromMessages(id: string, sourceMessages: ChatMessage[]): ChatHistory {
  const now = new Date().toISOString()
  return {
    id,
    title: buildChatTitle(sourceMessages),
    messages: sourceMessages,
    createdAt: sourceMessages[0]?.timestamp || now,
    updatedAt: now,
    detailLoaded: true,
  }
}

function resetCurrentChat() {
  messages.value = []
  sessionId.value = generateSessionId()
  currentMode.value = 'quick'
  isCurrentChatFromHistory.value = false
  scrollToken.value++
}

function mergeSessionSummaries(summaries: SessionSummary[]) {
  const localById = new Map(chatHistories.value.map((history) => [history.id, history]))
  const remoteHistories = summaries
    .filter((summary) => summary?.sessionId)
    .map((summary) => {
      const cached = localById.get(summary.sessionId)
      return {
        id: summary.sessionId,
        title: summary.title || cached?.title || '新对话',
        messages: cached?.messages || [],
        createdAt: summary.createTime || cached?.createdAt || new Date().toISOString(),
        updatedAt: summary.updateTime || cached?.updatedAt || summary.createTime || new Date().toISOString(),
        detailLoaded: Boolean(cached?.detailLoaded && cached.messages.length > 0),
      } satisfies ChatHistory
    })
  const remoteIds = new Set(remoteHistories.map((history) => history.id))
  const localOnly = chatHistories.value.filter((history) => !remoteIds.has(history.id))
  chatHistories.value = [...remoteHistories, ...localOnly].sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
  trimHistories()
}

async function fetchAndCacheChatHistory(id: string) {
  const sessionInfo = await chatApi.getSessionInfo(id)
  const history = convertSessionInfoToChatHistory(sessionInfo)
  const existingIndex = chatHistories.value.findIndex((item) => item.id === history.id)
  if (existingIndex >= 0) chatHistories.value[existingIndex] = history
  else chatHistories.value.unshift(history)
  trimHistories()
  persistHistories()
  return history
}

function convertSessionInfoToChatHistory(sessionInfo: SessionInfoResponse): ChatHistory {
  const convertedMessages: ChatMessage[] = (sessionInfo.messages || [])
    .map((item) => ({
      type: item.role === 'assistant' ? 'assistant' : 'user',
      content: item.content || '',
      timestamp: item.createdAt || new Date().toISOString(),
    }))
    .filter((item) => item.content) as ChatMessage[]

  return {
    id: sessionInfo.sessionId,
    title: sessionInfo.title || buildChatTitle(convertedMessages),
    messages: convertedMessages,
    createdAt: sessionInfo.createTime || new Date().toISOString(),
    updatedAt: sessionInfo.updateTime || sessionInfo.createTime || new Date().toISOString(),
    detailLoaded: true,
  }
}

function buildChatTitle(sourceMessages: ChatMessage[]) {
  const firstUserMessage = sourceMessages.find((item) => item.type === 'user')
  if (!firstUserMessage?.content) return '新对话'
  return `${firstUserMessage.content.substring(0, 30)}${firstUserMessage.content.length > 30 ? '...' : ''}`
}

function trimHistories() {
  if (chatHistories.value.length > MAX_HISTORY_COUNT) {
    chatHistories.value = chatHistories.value.slice(0, MAX_HISTORY_COUNT)
  }
}

function persistHistories() {
  saveLocalChatHistories(chatHistories.value)
}

function generateSessionId() {
  return `session_${Math.random().toString(36).slice(2, 11)}_${Date.now()}`
}
