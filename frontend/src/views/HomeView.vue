<template>
  <section class="home">
    <header class="hero">
      <h1>学长 Skill 共创场</h1>
      <p class="subtitle">把零散经验蒸馏为可被引用的学长.Skill。提问 / 蒸馏 / 复盘，都在这里。</p>
      <div class="hints">
        <button v-for="h in HINTS" :key="h" class="hint" @click="ask(h)">{{ h }}</button>
      </div>
    </header>

    <div class="composer-wrap">
      <ChatComposer @submit="ask" :loading="store.loading" />
    </div>

    <div v-if="store.error" class="error-banner">{{ store.error }}</div>

    <div v-if="store.messages.length" class="conversation">
      <div
        v-for="(m, i) in store.messages"
        :key="i"
        class="msg"
        :class="{ user: m.role === 'user', assistant: m.role === 'assistant' }"
      >
        <div v-if="m.role === 'user'" class="bubble user-bubble">
          <div class="bubble-author">你</div>
          <div class="bubble-text">{{ m.content }}</div>
        </div>
        <div v-else-if="(m.answers ?? []).length > 0" class="answer-bubble">
          <div class="answer-head">
            <div class="avatar" :style="{ background: avatarColor((m.answers ?? [])[0].seniorId) }">
              {{ ((m.answers ?? [])[0].name || '?').charAt(0) }}
            </div>
            <div>
              <div class="answer-author">{{ (m.answers ?? [])[0].name }}</div>
              <div class="answer-school">{{ (m.answers ?? [])[0].school }} · {{ (m.answers ?? [])[0].major }}</div>
            </div>
            <router-link :to="`/seniors/${(m.answers ?? [])[0].seniorId}`" class="view-skill">查看 Skill</router-link>
          </div>
          <div class="answer-content">{{ (m.answers ?? [])[0].content }}</div>
        </div>
      </div>
    </div>

    <div v-else class="empty">
      <div class="empty-icon">💬</div>
      <p>试试上面的提示按钮，或在下方输入你的问题</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useChatStore } from '../stores/chatStore';
import ChatComposer from '../components/chat/ChatComposer.vue';

const store = useChatStore();

const HINTS = [
  '保研流程怎么准备？',
  '计算机选课避坑',
  'ACM 怎么入门？',
  '如何找科研',
];

async function ask(text: string) {
  await store.send(text);
}

function avatarColor(seed: string): string {
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) | 0;
  const palette = ['#fde0e6', '#dceafd', '#e5f4dc', '#f9eedc', '#ece4fa', '#fde6d4', '#d4f0fa'];
  return palette[Math.abs(h) % palette.length];
}

onMounted(async () => {
  await store.loadHistory();
});
</script>

<style scoped>
.home { padding: 0 0 120px; max-width: 720px; margin: 0 auto; }

/* 沉浸式 hero */
.hero { padding: 60px 24px 40px; text-align: center; background: linear-gradient(180deg, #fce4ec 0%, transparent 100%); }
.hero h1 { font-size: 32px; font-weight: 800; color: var(--ink); margin: 0 0 8px; letter-spacing: -0.5px; }
.subtitle { color: var(--ink-2); margin: 0 0 24px; font-size: 15px; line-height: 1.6; }
.hints { display: flex; gap: 8px; flex-wrap: wrap; justify-content: center; }
.hint { padding: 8px 16px; border-radius: 999px; border: 1px solid var(--border); background: var(--paper); cursor: pointer; color: var(--ink); font-size: 14px; transition: all 0.15s; box-shadow: 0 2px 8px rgba(0,0,0,0.04); }
.hint:hover { background: var(--pink); color: white; border-color: var(--pink); }

.composer-wrap { padding: 0 24px 16px; }
.error-banner { margin: 0 24px 12px; padding: 10px 14px; background: #fee; border: 1px solid #fcc; border-radius: 8px; color: #a00; font-size: 14px; }

/* 对话流 */
.conversation { padding: 0 24px; display: flex; flex-direction: column; gap: 20px; }
.msg { animation: fadeIn 0.2s ease; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }

.bubble { padding: 12px 18px; border-radius: 18px; max-width: 80%; }
.bubble-author { font-size: 12px; opacity: 0.7; margin-bottom: 2px; }
.bubble-text { font-size: 15px; line-height: 1.6; }
.user-bubble { background: var(--pink); color: white; margin-left: auto; border-bottom-right-radius: 4px; }
.user-bubble .bubble-author { color: rgba(255,255,255,0.85); }

/* 单学长回答气泡 */
.answer-bubble { background: var(--surface); border-radius: 18px; padding: 16px 20px; box-shadow: 0 2px 12px rgba(0,0,0,0.04); border-bottom-left-radius: 4px; }
.answer-head { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; padding-bottom: 12px; border-bottom: 1px solid var(--border); }
.avatar { display: flex; align-items: center; justify-content: center; width: 40px; height: 40px; border-radius: 50%; color: white; font-weight: 700; font-size: 16px; flex: 0 0 auto; }
.answer-author { font-weight: 600; color: var(--ink); font-size: 15px; }
.answer-school { font-size: 12px; color: var(--ink-2); margin-top: 2px; }
.view-skill { margin-left: auto; padding: 4px 12px; border-radius: 999px; background: var(--pink); color: white; text-decoration: none; font-size: 12px; }
.answer-content { color: var(--ink); font-size: 15px; line-height: 1.8; white-space: pre-wrap; }

/* 空态 */
.empty { padding: 60px 24px; text-align: center; color: var(--ink-2); }
.empty-icon { font-size: 48px; margin-bottom: 12px; }

@media (max-width: 720px) {
  .hero { padding: 40px 16px 30px; }
  .hero h1 { font-size: 26px; }
  .conversation, .composer-wrap, .error-banner { padding-left: 16px; padding-right: 16px; }
}
</style>
