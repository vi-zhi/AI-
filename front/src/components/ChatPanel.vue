<template>
  <div class="chat-panel">
    <div class="chat-header">
      <span class="chat-title">AI 助手</span>
      <button class="btn-reset" @click="$emit('reset')" title="新对话">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
          <path d="M3 3v5h5"/>
        </svg>
      </button>
    </div>

    <div class="chat-messages" ref="messagesRef">
      <div v-if="messages.length === 0" class="empty-state">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#ccc" stroke-width="1.5">
          <rect x="3" y="3" width="18" height="18" rx="2"/>
          <path d="M8 7h8M8 11h6M8 15h9"/>
        </svg>
        <p>开始对话，让 AI 为你绘制流程图</p>
        <p class="hint">支持文本和语音两种方式</p>
      </div>

      <div v-for="(msg, i) in messages" :key="i" class="message" :class="msg.role">
        <div class="message-bubble">
          <div class="message-role">{{ msg.role === 'user' ? '你' : 'AI' }}</div>
          <div class="message-text">{{ msg.text }}</div>
        </div>
      </div>

      <div v-if="store.loading" class="message assistant">
        <div class="message-bubble loading">
          <span class="dot"></span>
          <span class="dot"></span>
          <span class="dot"></span>
        </div>
      </div>
    </div>

    <div class="chat-input-area">
      <div v-if="!voiceMode" class="text-input-row">
        <textarea
            v-model="inputText"
            placeholder="输入你的需求，按 Enter 发送..."
            rows="1"
            @keydown.enter.exact.prevent="handleSend"
            @input="autoResize"
            ref="textareaRef"
        ></textarea>
        <button class="btn-send" :disabled="!inputText.trim() || store.loading" @click="handleSend">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
            <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
          </svg>
        </button>
      </div>

      <div v-else class="voice-input-row">
        <button
            class="btn-record"
            :class="{ recording: store.recording }"
            @mousedown="startRecording"
            @mouseup="stopRecording"
            @mouseleave="cancelRecording"
            @touchstart.prevent="startRecording"
            @touchend.prevent="stopRecording"
        >
          <svg v-if="!store.recording" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
            <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
            <line x1="12" y1="19" x2="12" y2="23"/>
            <line x1="8" y1="23" x2="16" y2="23"/>
          </svg>
          <svg v-else width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
            <rect x="6" y="6" width="12" height="12" rx="2"/>
          </svg>
        </button>
        <span class="voice-hint">{{ store.recording ? '正在录音...' : '按住说话' }}</span>
        <button class="btn-switch" @click="voiceMode = false">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M11 4H7a3 3 0 0 0-3 3v2a3 3 0 0 0 3 3h4"/>
            <polyline points="9 14 12 17 15 14"/>
            <line x1="12" y1="17" x2="12" y2="22"/>
          </svg>
        </button>
      </div>

      <div class="input-toggle">
        <button
            class="btn-toggle"
            :class="{ active: !voiceMode }"
            @click="voiceMode = false"
        >
          键盘输入
        </button>
        <button
            class="btn-toggle"
            :class="{ active: voiceMode }"
            @click="voiceMode = true"
        >
          语音输入
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import { useChatStore } from '@/stores/chat'

const store = useChatStore()
const { messages } = store
const messagesRef = ref<HTMLElement>()
const textareaRef = ref<HTMLTextAreaElement>()
const inputText = ref('')
const voiceMode = ref(false)

let audioContext: AudioContext | null = null
let mediaStream: MediaStream | null = null
let mediaStreamSource: MediaStreamAudioSourceNode | null = null
let scriptProcessor: ScriptProcessorNode | null = null
let audioData: Float32Array[] = []

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || store.loading) return
  await store.sendUserMessage(text)
  inputText.value = ''
}

function floatTo16BitPCM(output: DataView, offset: number, input: Float32Array) {
  for (let i = 0; i < input.length; i++, offset += 2) {
    const s = Math.max(-1, Math.min(1, input[i]))
    output.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7FFF, true)
  }
}

function writeString(view: DataView, offset: number, string: string) {
  for (let i = 0; i < string.length; i++) {
    view.setUint8(offset + i, string.charCodeAt(i))
  }
}

function encodeWAV(samples: Float32Array, sampleRate: number) {
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)
  
  writeString(view, 0, 'RIFF')
  view.setUint32(4, 36 + samples.length * 2, true)
  writeString(view, 8, 'WAVE')
  writeString(view, 12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  writeString(view, 36, 'data')
  view.setUint32(40, samples.length * 2, true)
  floatTo16BitPCM(view, 44, samples)
  
  return new Blob([buffer], { type: 'audio/wav' })
}

function mergeBuffers(buffers: Float32Array[]) {
  let totalLength = 0
  for (let i = 0; i < buffers.length; i++) {
    totalLength += buffers[i].length
  }
  const result = new Float32Array(totalLength)
  let offset = 0
  for (let i = 0; i < buffers.length; i++) {
    result.set(buffers[i], offset)
    offset += buffers[i].length
  }
  return result
}

async function startRecording() {
  try {
    audioData = []
    mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true })
    audioContext = new (window.AudioContext || (window as any).webkitAudioContext)({ sampleRate: 16000 })
    mediaStreamSource = audioContext.createMediaStreamSource(mediaStream)
    scriptProcessor = audioContext.createScriptProcessor(4096, 1, 1)
    
    scriptProcessor.onaudioprocess = (e) => {
      audioData.push(new Float32Array(e.inputBuffer.getChannelData(0)))
    }
    
    mediaStreamSource.connect(scriptProcessor)
    scriptProcessor.connect(audioContext.destination)
    
    store.recording = true
  } catch {
    alert('无法访问麦克风，请检查浏览器权限')
  }
}

function stopRecording() {
  if (!store.recording || !scriptProcessor || !audioContext || !mediaStream) return
  
  store.recording = false
  
  mediaStreamSource?.disconnect()
  scriptProcessor?.disconnect()
  mediaStream?.getTracks().forEach(t => t.stop())
  
  const merged = mergeBuffers(audioData)
  const wavBlob = encodeWAV(merged, 16000)
  const file = new File([wavBlob], 'voice.wav', { type: 'audio/wav' })
  
  store.sendVoiceMessage(file)
  
  audioContext.close()
}

function cancelRecording() {
  if (!store.recording) return
  
  store.recording = false
  
  mediaStreamSource?.disconnect()
  scriptProcessor?.disconnect()
  mediaStream?.getTracks().forEach(t => t.stop())
  audioContext?.close()
  audioData = []
}

function autoResize(e: Event) {
  const el = e.target as HTMLTextAreaElement
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}

watch(
    () => store.messages.length,
    async () => {
      await nextTick()
      if (messagesRef.value) {
        messagesRef.value.scrollTop = messagesRef.value.scrollHeight
      }
    },
)

defineEmits<{ reset: [] }>()
</script>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(102, 126, 234, 0.1);
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.05) 0%, rgba(118, 75, 162, 0.05) 100%);
}

.chat-title {
  font-size: 16px;
  font-weight: 700;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.btn-reset {
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(102, 126, 234, 0.2);
  border-radius: 10px;
  padding: 6px 12px;
  cursor: pointer;
  color: #667eea;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 4px;
}

.btn-reset:hover {
  color: #fff;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-color: transparent;
  transform: translateY(-1px);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.chat-messages::-webkit-scrollbar {
  width: 6px;
}

.chat-messages::-webkit-scrollbar-track {
  background: transparent;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: linear-gradient(180deg, #667eea 0%, #764ba2 100%);
  border-radius: 3px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #888;
  gap: 12px;
}

.empty-state p {
  font-size: 15px;
  font-weight: 500;
}

.hint {
  font-size: 13px;
  opacity: 0.7;
}

.message {
  margin-bottom: 16px;
  animation: slideIn 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message-bubble {
  max-width: 85%;
  padding: 12px 16px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.6;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.message-bubble:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12);
}

.message.user .message-bubble {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  margin-left: auto;
  border-bottom-right-radius: 6px;
}

.message.assistant .message-bubble {
  background: rgba(255, 255, 255, 0.9);
  color: #333;
  margin-right: auto;
  border-bottom-left-radius: 6px;
  border: 1px solid rgba(102, 126, 234, 0.1);
}

.message-role {
  font-size: 11px;
  opacity: 0.7;
  margin-bottom: 4px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.message-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.message.user .message-text {
  color: #fff;
}

.loading {
  display: flex;
  gap: 6px;
  padding: 16px 24px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  animation: bounce 1.4s infinite ease-in-out;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.4);
}

.dot:nth-child(1) { animation-delay: -0.32s; }
.dot:nth-child(2) { animation-delay: -0.16s; }
.dot:nth-child(3) { animation-delay: 0s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1.1); opacity: 1; }
}

.chat-input-area {
  border-top: 1px solid rgba(102, 126, 234, 0.1);
  padding: 16px 20px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.5) 0%, rgba(255, 255, 255, 0.8) 100%);
}

.text-input-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.text-input-row textarea {
  flex: 1;
  resize: none;
  border: 2px solid rgba(102, 126, 234, 0.15);
  border-radius: 14px;
  padding: 12px 16px;
  font-size: 14px;
  font-family: inherit;
  outline: none;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  max-height: 120px;
  line-height: 1.5;
  background: rgba(255, 255, 255, 0.8);
}

.text-input-row textarea:focus {
  border-color: #667eea;
  box-shadow: 0 0 0 4px rgba(102, 126, 234, 0.1);
  background: #fff;
}

.text-input-row textarea::placeholder {
  color: #aaa;
}

.btn-send {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border: none;
  border-radius: 14px;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  flex-shrink: 0;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
}

.btn-send:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.btn-send:active:not(:disabled) {
  transform: translateY(0);
}

.btn-send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.voice-input-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn-record {
  background: #1677ff;
  color: #fff;
  border: none;
  border-radius: 50%;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-record:hover {
  background: #4096ff;
}

.btn-record.recording {
  background: #ff4d4f;
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(255,77,79,0.4); }
  50% { box-shadow: 0 0 0 10px rgba(255,77,79,0); }
}

.voice-hint {
  font-size: 13px;
  color: #666;
}

.btn-switch {
  background: none;
  border: 1px solid #eee;
  border-radius: 6px;
  padding: 6px;
  cursor: pointer;
  color: #666;
  transition: all 0.2s;
}

.btn-switch:hover {
  color: #1677ff;
  border-color: #1677ff;
}

.input-toggle {
  display: flex;
  gap: 4px;
  margin-top: 8px;
}

.btn-toggle {
  flex: 1;
  padding: 6px 0;
  border: 1px solid #eee;
  border-radius: 6px;
  background: #fff;
  font-size: 12px;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-toggle.active {
  background: #1677ff;
  color: #fff;
  border-color: #1677ff;
}

.btn-toggle:hover:not(.active) {
  border-color: #1677ff;
  color: #1677ff;
}

.chat-messages::-webkit-scrollbar {
  width: 4px;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: #ddd;
  border-radius: 2px;
}
</style>