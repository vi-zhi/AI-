import axios from 'axios'

const http = axios.create({
  baseURL: '/api/v1/draw',
  timeout: 60000,
})

export interface ApiResponse<T = any> {
  code: string
  info: string
  data: T
}

export interface DrawResponse {
  replies: string[]
  inputText?: string
  type?: string
  sessionId?: string
}

export interface SessionResponse {
  sessionId: string
  agentId: string
  userId: string
}

/** 发送文本消息 */
export function sendText(text: string, agentId = '100001', userId = 'default') {
  const params = new URLSearchParams()
  params.append('text', text)
  params.append('agentId', agentId)
  params.append('userId', userId)
  return http.post<ApiResponse<DrawResponse>>('/text', params, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  })
}

/** 上传语音文件 */
export function sendVoice(file: File, agentId = '100001', userId = 'default') {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('agentId', agentId)
  formData.append('userId', userId)
  return http.post<ApiResponse<DrawResponse>>('/voice', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 创建聊天会话 */
export function createSession(agentId = '100001', userId = 'default') {
  return http.post<ApiResponse<SessionResponse>>('/chat/session', { agentId, userId })
}

/** 发送聊天消息(带会话) */
export function sendMessage(sessionId: string, message: string, agentId = '100001', userId = 'default') {
  return http.post<ApiResponse<DrawResponse>>('/chat/message', { agentId, userId, sessionId, message })
}
