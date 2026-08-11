import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ChatMessage, SeniorAnswer } from '../types'

export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>([])
  const sessionId = ref<string>()

  function addUser(content: string) {
    messages.value.push({ id: crypto.randomUUID(), role: 'user', content })
  }

  function addAssistant(answers: SeniorAnswer[]) {
    messages.value.push({ id: crypto.randomUUID(), role: 'assistant', content: '', answers })
  }

  function reset() {
    messages.value = []
    sessionId.value = undefined
  }

  return { messages, sessionId, addUser, addAssistant, reset }
})
