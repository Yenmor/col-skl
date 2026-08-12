import { defineStore } from 'pinia';
import { ref } from 'vue';
import { chatApi } from '../services/api-v1';
import { getOrCreateUserId } from '../services/api-v1';
import type { ChatMessageDto, ChatResponseV1 } from '../types/api-v1';

const SESSION_KEY = 'persist.sessionId';

export type LocalMessage = {
  role: 'user' | 'assistant';
  content: string;
  answers?: ChatResponseV1['answers'];
  createdAt: string;
};

export const useChatStore = defineStore('chat', () => {
  const messages = ref<LocalMessage[]>([]);
  const sessionId = ref<string>(loadSession());
  const loading = ref(false);
  const error = ref<string | null>(null);

  function loadSession(): string {
    if (typeof localStorage === 'undefined') return '';
    return localStorage.getItem(SESSION_KEY) ?? '';
  }

  function saveSession(id: string) {
    if (typeof localStorage !== 'undefined') localStorage.setItem(SESSION_KEY, id);
  }

  function getOrCreateUser() {
    return getOrCreateUserId();
  }

  async function send(text: string) {
    if (!text.trim()) return;
    loading.value = true;
    error.value = null;
    const userMsg: LocalMessage = {
      role: 'user',
      content: text,
      createdAt: new Date().toISOString(),
    };
    messages.value.push(userMsg);

    try {
      const resp = await chatApi.send({ message: text, sessionId: sessionId.value || undefined });
      sessionId.value = resp.sessionId;
      saveSession(resp.sessionId);
      const aiMsg: LocalMessage = {
        role: 'assistant',
        content: resp.answers.map(a => `[${a.name}] ${a.content}`).join('\n\n'),
        answers: resp.answers,
        createdAt: new Date().toISOString(),
      };
      messages.value.push(aiMsg);
    } catch (e) {
      error.value = (e as Error).message;
    } finally {
      loading.value = false;
    }
  }

  async function loadHistory() {
    if (!sessionId.value) return;
    try {
      const rows = await chatApi.listMessages(sessionId.value, { limit: 50 });
      // rows 倒序（createdAt DESC），转正序
      messages.value = rows.reverse().map((r: ChatMessageDto) => ({
        role: r.role as 'user' | 'assistant',
        content: r.content ?? (r.answers ? JSON.stringify(r.answers) : ''),
        answers: r.answers as ChatResponseV1['answers'] | undefined,
        createdAt: r.createdAt,
      }));
    } catch (e) {
      error.value = (e as Error).message;
    }
  }

  function reset() {
    messages.value = [];
    sessionId.value = '';
    if (typeof localStorage !== 'undefined') localStorage.removeItem(SESSION_KEY);
  }

  return { messages, sessionId, loading, error, send, loadHistory, reset, getOrCreateUser };
});
