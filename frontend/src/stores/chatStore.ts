import { defineStore } from 'pinia';
import { ref } from 'vue';
import { chatApi } from '../services/api-v1';
import { getOrCreateUserId } from '../services/api-v1';
import type { ChatResponseV1 } from '../types/api-v1';

const SESSION_KEY = 'persist.sessionId';

export type ChatMessage = {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  isStreaming?: boolean;
};

export type ActiveSenior = {
  seniorId: string;
  name: string;
  school: string;
  major: string;
  year: string;
  content: string;
};

export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>([]);
  const sessionId = ref<string>(loadSession());
  const loading = ref(false);
  const error = ref<string | null>(null);
  /** 当前对话的学长（沉浸式对话的头部标识） */
  const activeSenior = ref<ActiveSenior | null>(null);
  /** 排除的学长（"换一位"用），本地维护 */
  const excludedSeniorIds = ref<string[]>([]);

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

  function addUser(content: string) {
    messages.value.push({ id: crypto.randomUUID(), role: 'user', content });
  }

  function addAssistantPlaceholder() {
    const id = crypto.randomUUID();
    messages.value.push({ id, role: 'assistant', content: '', isStreaming: true });
    return id;
  }

  /** 打字机流式：把完整回答逐块写入 assistant 消息 */
  function streamInto(id: string, fullText: string) {
    const msg = messages.value.find(m => m.id === id);
    if (!msg) return;
    let i = 0;
    const step = Math.max(4, Math.round(fullText.length / 40));
    const tick = () => {
      i += step;
      if (i >= fullText.length) {
        msg.content = fullText;
        msg.isStreaming = false;
        return;
      }
      msg.content = fullText.slice(0, i);
      setTimeout(tick, 24);
    };
    tick();
  }

  /** 发送消息：可锁定指定 Skill；未指定时自动召回 top-1，回答以流式呈现 */
  async function send(text: string, seniorId?: string) {
    if (loading.value || !text.trim()) return;
    loading.value = true;
    error.value = null;
    addUser(text);
    const pendingId = addAssistantPlaceholder();

    try {
      const resp: ChatResponseV1 = await chatApi.send({
        message: text,
        sessionId: sessionId.value || undefined,
        seniorId: seniorId || undefined,
      });
      sessionId.value = resp.sessionId;
      saveSession(resp.sessionId);

      const answers = resp.answers ?? [];
      if (answers.length === 0) {
        const msg = messages.value.find(m => m.id === pendingId);
        if (msg) { msg.content = '暂时没有匹配的学长，换个问法试试。'; msg.isStreaming = false; }
        return;
      }
      const ans = answers[0];
      activeSenior.value = {
        seniorId: ans.seniorId,
        name: ans.name,
        school: ans.school,
        major: ans.major,
        year: ans.year,
        content: ans.content,
      };
      excludedSeniorIds.value = [];
      streamInto(pendingId, ans.content);
    } catch (e) {
      const msg = messages.value.find(m => m.id === pendingId);
      if (msg) { msg.content = '暂时无法连接服务器，请稍后重试。'; msg.isStreaming = false; }
      error.value = (e as Error).message;
    } finally {
      loading.value = false;
    }
  }

  /** 换一位：排除当前学长重新回答最后一个问题 */
  async function switchSenior() {
    const lastUser = [...messages.value].reverse().find(m => m.role === 'user');
    if (!lastUser || loading.value) return;
    const current = activeSenior.value;

    loading.value = true;
    const pendingId = addAssistantPlaceholder();
    try {
      const resp: ChatResponseV1 = await chatApi.send({
        message: lastUser.content,
        sessionId: sessionId.value || undefined,
        excludeSeniorId: current?.seniorId,
      });
      sessionId.value = resp.sessionId;
      saveSession(resp.sessionId);
      const answers = resp.answers ?? [];
      if (answers.length === 0) {
        const msg = messages.value.find(m => m.id === pendingId);
        if (msg) { msg.content = '没有其他匹配的学长了。'; msg.isStreaming = false; }
        return;
      }
      const ans = answers[0];
      activeSenior.value = {
        seniorId: ans.seniorId, name: ans.name, school: ans.school,
        major: ans.major, year: ans.year, content: ans.content,
      };
      streamInto(pendingId, ans.content);
    } catch (e) {
      const msg = messages.value.find(m => m.id === pendingId);
      if (msg) { msg.content = '切换失败，请重试。'; msg.isStreaming = false; }
    } finally {
      loading.value = false;
    }
  }

  async function loadHistory() {
    if (!sessionId.value) return;
    try {
      const rows = await chatApi.listMessages(sessionId.value, { limit: 50 });
      messages.value = rows.reverse().map(r => {
        let content = r.content ?? '';
        if (!content && r.role === 'assistant' && Array.isArray(r.answers)) {
          const answers = r.answers as Array<{
            seniorId?: string; name?: string; school?: string;
            major?: string; year?: string; content?: string;
          }>;
          if (answers.length > 0) {
            const a = answers[0];
            content = a.content ?? '';
            activeSenior.value = {
              seniorId: a.seniorId ?? '',
              name: a.name ?? '',
              school: a.school ?? '',
              major: a.major ?? '',
              year: a.year ?? '',
              content,
            };
          }
        }
        return {
          id: crypto.randomUUID(),
          role: (r.role as 'user' | 'assistant'),
          content,
        };
      });
    } catch {
      // 历史拉取失败不阻塞
    }
  }

  function reset() {
    messages.value = [];
    sessionId.value = '';
    activeSenior.value = null;
    excludedSeniorIds.value = [];
    if (typeof localStorage !== 'undefined') localStorage.removeItem(SESSION_KEY);
  }

  return {
    messages, sessionId, loading, error, activeSenior,
    send, switchSenior, loadHistory, reset, getOrCreateUser, addUser,
  };
});
