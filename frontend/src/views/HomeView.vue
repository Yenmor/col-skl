<template>
  <div class="page-shell">
    <TopBar />

    <!-- Hero: shown before first message -->
    <section v-if="!messages.length" class="hero-panel">
      <div class="content-width">
        <h1 class="hero-title">向学长学姐提问</h1>
        <p class="hero-subtitle">Skill 来自真实经历蒸馏，自动匹配最懂你的人</p>
        <div style="display:flex;flex-wrap:wrap;gap:8px;margin-top:20px;">
          <button
            v-for="hint in hints"
            :key="hint"
            class="hero-hint-btn"
            @click="ask(hint)"
          >{{ hint }}</button>
        </div>
      </div>
    </section>

    <!-- Chat messages -->
    <div ref="scrollEl" class="chat-messages">
      <template v-for="msg in messages" :key="msg.id">
        <!-- User bubble -->
        <div v-if="msg.role === 'user'" class="chat-question">
          <span>{{ msg.content }}</span>
        </div>
        <!-- Loading placeholder -->
        <div v-else-if="msg.isStreaming" style="display:flex;align-items:center;gap:8px;padding:18px 0;">
          <span class="dot-pulse">···</span>
          <span class="tiny">正在匹配学长学姐…</span>
        </div>
        <!-- Answer grid -->
        <div v-else style="margin-bottom:32px;">
          <div class="answer-grid">
            <AnswerCard
              v-for="ans in msg.answers"
              :key="ans.seniorId"
              :answer="ans"
            />
          </div>
          <p v-if="!msg.answers?.length && msg.content" class="tiny" style="padding:12px 0;">{{ msg.content }}</p>
        </div>
      </template>
    </div>

    <ChatComposer
      @submit="ask"
      :loading="loading"
      placeholder="比如：我大二，现在准备保研还来得及吗？"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { storeToRefs } from 'pinia'
import { useChatStore } from '../stores/chatStore'
import { askSeniors } from '../services/chatService'
import TopBar from '../components/common/TopBar.vue'
import ChatComposer from '../components/chat/ChatComposer.vue'
import AnswerCard from '../components/chat/AnswerCard.vue'

const store = useChatStore()
const { messages, sessionId } = storeToRefs(store)
const loading = ref(false)
const scrollEl = ref<HTMLElement>()

const hints = [
  '大二准备保研，现在开始来得及吗？',
  '没有竞赛经历，怎么提升简历含金量？',
  '第一次联系导师，邮件怎么写？',
  '数学建模如何备赛？',
]

function scrollBottom() {
  nextTick(() => {
    if (scrollEl.value) scrollEl.value.scrollTop = scrollEl.value.scrollHeight
  })
}

async function ask(text: string) {
  if (loading.value || !text.trim()) return
  store.addUser(text)
  const pid = crypto.randomUUID()
  messages.value.push({ id: pid, role: 'assistant', content: '', isStreaming: true })
  loading.value = true
  scrollBottom()
  try {
    const res = await askSeniors(text, sessionId.value)
    sessionId.value = res.sessionId
    const idx = messages.value.findIndex(m => m.id === pid)
    if (idx !== -1) messages.value[idx] = { id: pid, role: 'assistant', content: '', answers: res.answers }
  } catch {
    const idx = messages.value.findIndex(m => m.id === pid)
    if (idx !== -1) messages.value[idx] = { id: pid, role: 'assistant', content: '暂时无法连接服务器，请稍后重试。' }
  } finally {
    loading.value = false
    scrollBottom()
  }
}
</script>
