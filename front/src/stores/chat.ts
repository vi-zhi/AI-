import { defineStore } from 'pinia'
import { ref } from 'vue'
import { sendText, sendVoice, createSession, sendMessage } from '@/api/draw'
import type { DrawResponse, SessionResponse } from '@/api/draw'

export interface ChatMessage {
  role: 'user' | 'assistant'
  text: string
  timestamp: number
}

export const useChatStore = defineStore('chat', () => {
  // 消息列表
  const messages = ref<ChatMessage[]>([])

  // 当前 draw.io XML
  const currentXml = ref<string>('')

  // 会话信息
  const session = ref<SessionResponse | null>(null)

  // 加载状态
  const loading = ref(false)
  const recording = ref(false)

  // 是否使用会话模式
  const useSession = ref(false)

  /** 发送文本消息 */
  async function sendUserMessage(text: string) {
    loading.value = true
    try {
      const res = await sendText(text)
      if (res.data.code === '0000') {
        const replies = res.data.data.replies
        messages.value.push({
          role: 'user',
          text: res.data.data.inputText || text,
          timestamp: Date.now(),
        })
        messages.value.push({
          role: 'assistant',
          text: replies[0] || '',
          timestamp: Date.now(),
        })
        if (replies[1]) {
          currentXml.value = replies[1]
        }
      }
    } finally {
      loading.value = false
    }
  }

  /** 发送语音消息 */
  async function sendVoiceMessage(file: File) {
    loading.value = true
    try {
      const res = await sendVoice(file)
      if (res.data.code === '0000') {
        const replies = res.data.data.replies
        messages.value.push({
          role: 'user',
          text: replies[0] || '语音消息',
          timestamp: Date.now(),
        })
        messages.value.push({
          role: 'assistant',
          text: replies[0] || '',
          timestamp: Date.now(),
        })
        if (replies[1]) {
          currentXml.value = replies[1]
        }
      }
    } finally {
      loading.value = false
    }
  }

  /** 发送聊天消息(会话模式) */
  async function sendChatMessage(message: string) {
    loading.value = true
    try {
      const res = await sendMessage(
        session.value!.sessionId,
        message,
        session.value?.agentId,
        session.value?.userId,
      )
      if (res.data.code === '0000') {
        const replies = res.data.data.replies
        messages.value.push({
          role: 'user',
          text: message,
          timestamp: Date.now(),
        })
        messages.value.push({
          role: 'assistant',
          text: replies[0] || '',
          timestamp: Date.now(),
        })
        if (replies[1]) {
          currentXml.value = replies[1]
        }
      }
    } finally {
      loading.value = false
    }
  }

  /** 创建新会话 */
  async function initSession() {
    const res = await createSession()
    if (res.data.code === '0000') {
      session.value = res.data.data
      useSession.value = true
    }
  }

  /** 重置 */
  function reset() {
    messages.value = []
    currentXml.value = ''
    session.value = null
    useSession.value = false
  }

  return {
    messages,
    currentXml,
    session,
    loading,
    recording,
    useSession,
    sendUserMessage,
    sendVoiceMessage,
    sendChatMessage,
    initSession,
    reset,
  }
})
