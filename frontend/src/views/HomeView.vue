<template>
  <div class="page-shell home-page" :class="[`home-${homeMode}`, `community-${communityPhase}`]">
    <button
      v-if="homeMode === 'chat' && !messages.length"
      class="home-reveal-surface"
      type="button"
      aria-label="隐藏对话框并展开能力立方体"
      @click="showCube"
    />

    <template v-if="homeMode === 'chat'">
      <section
        class="chat-session"
        :class="{ active: messages.length }"
        :style="{ '--domain-color': activeDomain.color, '--domain-ink': activeDomain.ink }"
        aria-label="SkillsLab 对话卡片"
      >
        <header v-if="messages.length" class="session-head">
          <div class="session-identity">
            <span>{{ activeDomain.glyph }}</span>
            <div>
              <small>{{ activeDomain.code }}</small>
              <strong v-if="chat.activePanel.length">专家会审 · {{ chat.activePanel.length }} 位</strong>
              <strong v-else>{{ activeDomain.name }}方向 · 学长协作回答</strong>
              <small v-if="chat.activePanel.length" class="session-panel-names">{{ panelNames }}</small>
            </div>
          </div>
          <div class="session-actions">
            <button v-if="chat.activePanel.length" type="button" @click="chat.switchPanel">
              <RefreshCw :size="13" /><span class="action-label">换一批</span>
            </button>
            <RouterLink v-if="chat.activePanel.length" :to="`/seniors/${chat.activePanel[0].seniorId}`">
              <BookOpen :size="13" /><span class="action-label">查看 Skill</span>
            </RouterLink>
            <button type="button" @click="resetConversation">
              <RotateCcw :size="13" /><span class="action-label">新任务</span>
            </button>
          </div>
        </header>

        <div v-if="messages.length" ref="scrollEl" class="session-messages">
          <div
            v-for="msg in messages"
            :key="msg.id"
            :class="msg.role === 'user' ? 'session-question' : 'session-answer'"
          >
            <template v-if="msg.role === 'user'">
              <span>{{ msg.content }}</span>
            </template>
            <template v-else-if="msg.answers && msg.answers.length">
              <div v-for="a in msg.answers" :key="a.seniorId" class="panel-bubble">
                <div class="panel-avatar">
                  <img v-if="avatarOf(a)" :src="avatarOf(a)" :alt="a.name" @error="onAvatarError(a)" />
                  <span v-else>{{ a.name.slice(0, 1) }}</span>
                </div>
                <div class="panel-body">
                  <div class="panel-head">
                    <strong>{{ a.name }}</strong>
                    <small class="panel-domain" :style="{ background: domainOf(a).tint, color: domainOf(a).ink }">{{ a.domain }}</small>
                  </div>
                  <p>{{ a.content }}</p>
                </div>
              </div>
            </template>
            <template v-else>
              <i class="session-avatar">{{ activeDomain.glyph }}</i>
              <div class="session-bubble">
                <p>{{ msg.content }}</p>
                <i v-if="msg.isStreaming" class="chat-caret" />
              </div>
            </template>
          </div>
        </div>

        <ChatComposer
          compact
          :loading="loading"
          :draft="questionDraft"
          :context="activeDomain.name"
          :context-color="activeDomain.color"
          :placeholder="messages.length ? '继续追问，或描述一个新的真实任务' : placeholder"
          @submit="ask"
          @select-domain="abilitySpace.select"
        />
      </section>
    </template>

    <template v-else>
      <nav class="home-layer-switcher" aria-label="能力地层">
        <button
          v-for="(domain, index) in skillDomains"
          :key="domain.id"
          type="button"
          :class="{ active: activeId === domain.id }"
          :style="{ '--domain-color': domain.color, '--domain-ink': domain.ink }"
          @click="abilitySpace.select(domain.id)"
        ><small>{{ String(index + 1).padStart(2, '0') }}</small><span>{{ domain.glyph }}</span><strong>{{ domain.name }}</strong></button>
      </nav>

      <div class="home-cube-controls" aria-label="立方体视角控制">
        <button type="button" title="向左旋转" aria-label="向左旋转" @click="abilitySpace.commandCube('left')"><ArrowLeft :size="16" /></button>
        <button type="button" title="暂停或继续自动运动" aria-label="暂停或继续自动运动" @click="abilitySpace.commandCube('motion')"><Pause :size="15" /></button>
        <button type="button" title="重置视角" aria-label="重置视角" @click="abilitySpace.commandCube('reset')"><RotateCcw :size="15" /></button>
        <button type="button" title="向右旋转" aria-label="向右旋转" @click="abilitySpace.commandCube('right')"><ArrowRight :size="16" /></button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ArrowLeft, ArrowRight, BookOpen, Pause, RefreshCw, RotateCcw } from '@lucide/vue'
import { useChatStore, type ExpertAnswer } from '../stores/chatStore'
import { useAbilitySpaceStore } from '../stores/abilitySpace'
import { domainById, domainForLabel, inferDomain, skillDomains } from '../domain'
import { avatarUrl } from '../services/seniorService'
import ChatComposer from '../components/chat/ChatComposer.vue'

const chat = useChatStore()
const abilitySpace = useAbilitySpaceStore()
const { messages } = storeToRefs(chat)
const { activeId, homeMode, communityPhase } = storeToRefs(abilitySpace)
const loading = computed(() => chat.loading)
const scrollEl = ref<HTMLElement>()
const questionDraft = ref('')
const targetSeniorId = ref<string>()
const avatarFailed = ref<Set<string>>(new Set())
const activeDomain = computed(() => domainById(activeId.value))
const CUBE_IDLE_TIMEOUT = 5000
const activityEvents = ['pointerdown', 'pointermove', 'wheel', 'keydown', 'touchstart'] as const
let cubeIdleTimer: number | undefined

const placeholder = computed(() => `今天想推进什么？描述你的${activeDomain.value.name}任务...`)

const panelNames = computed(() => {
  const names = chat.activePanel.slice(0, 2).map(a => a.name)
  return chat.activePanel.length > 2 ? `${names.join('、')} 等` : names.join('、')
})

function domainOf(a: ExpertAnswer) {
  return domainForLabel(a.domain)
}

function avatarOf(a: ExpertAnswer) {
  if (avatarFailed.value.has(a.seniorId)) return ''
  return avatarUrl({
    id: a.seniorId,
    name: a.name,
    school: a.school,
    major: a.major,
    year: a.year,
    domain: a.domain,
    avatarFilename: '',
    source: '',
    createdAt: '',
  })
}

function onAvatarError(a: ExpertAnswer) {
  avatarFailed.value = new Set(avatarFailed.value).add(a.seniorId)
}

function showCube() {
  abilitySpace.showCube()
}

function clearCubeIdleTimer() {
  if (cubeIdleTimer === undefined) return
  window.clearTimeout(cubeIdleTimer)
  cubeIdleTimer = undefined
}

function scheduleCubeIdleReturn() {
  clearCubeIdleTimer()
  if (homeMode.value !== 'cube' || communityPhase.value !== 'idle' || messages.value.length) return
  cubeIdleTimer = window.setTimeout(() => {
    cubeIdleTimer = undefined
    if (homeMode.value === 'cube' && communityPhase.value === 'idle' && !messages.value.length) {
      abilitySpace.showChat()
    }
  }, CUBE_IDLE_TIMEOUT)
}

function noteCubeActivity() {
  if (homeMode.value === 'cube') scheduleCubeIdleReturn()
}

watch([homeMode, communityPhase], scheduleCubeIdleReturn, { flush: 'post' })
watch(messages, () => scrollBottom(), { deep: true })

onMounted(() => {
  questionDraft.value = sessionStorage.getItem('skillslab:question-draft') ?? ''
  targetSeniorId.value = sessionStorage.getItem('skillslab:senior-id') ?? undefined
  sessionStorage.removeItem('skillslab:question-draft')
  if (communityPhase.value === 'idle' && !messages.value.length) abilitySpace.showChat()
  activityEvents.forEach(event => window.addEventListener(event, noteCubeActivity, { passive: true }))
  scheduleCubeIdleReturn()
  chat.loadHistory().then(scrollBottom)
})

onBeforeUnmount(() => {
  clearCubeIdleTimer()
  activityEvents.forEach(event => window.removeEventListener(event, noteCubeActivity))
})

function scrollBottom() {
  nextTick(() => {
    if (!scrollEl.value) return
    const streaming = messages.value.some(m => m.isStreaming)
    scrollEl.value.scrollTo({ top: scrollEl.value.scrollHeight, behavior: streaming ? 'auto' : 'smooth' })
  })
}

function resetConversation() {
  chat.reset()
  targetSeniorId.value = undefined
  sessionStorage.removeItem('skillslab:senior-id')
  abilitySpace.showChat()
}

async function ask(text: string) {
  if (loading.value || !text.trim()) return
  const inferred = inferDomain(text)
  abilitySpace.select(inferred.id)
  const seniorId = targetSeniorId.value
  targetSeniorId.value = undefined
  sessionStorage.removeItem('skillslab:senior-id')
  await chat.send(text, seniorId)
}
</script>
