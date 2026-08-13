import { defineStore } from 'pinia';
import { ref } from 'vue';
import { chatApi } from '../services/api-v1';
import { getOrCreateUserId, newUuid } from '../services/api-v1';
import type { ChatAnswer, ChatResponseV1 } from '../types/api-v1';

const SESSION_KEY = 'persist.sessionId';

/** 打字机流式已隐藏暂未启用（QQ 式整条发送）；streamInto 保留不删，日后恢复改此开关即可 */
const STREAMING_ENABLED = false;

export type ExpertAnswer = {
  seniorId: string;
  name: string;
  school: string;
  major: string;
  year: string;
  domain: string;
  content: string;
};

export type ChatMessage = {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  isStreaming?: boolean;
  answers?: ExpertAnswer[];
};

export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>([]);
  const sessionId = ref<string>(loadSession());
  const loading = ref(false);
  const error = ref<string | null>(null);
  /** 本批评委（专家会审），对话头部与"换一批"使用 */
  const activePanel = ref<ExpertAnswer[]>([]);

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
    messages.value.push({ id: newUuid(), role: 'user', content });
  }

  function addAssistantPlaceholder() {
    const id = newUuid();
    messages.value.push({
      id,
      role: 'assistant',
      content: '专家会审中，请稍候…',
      isStreaming: false,
    });
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

  function toExpertAnswers(answers: ChatAnswer[]): ExpertAnswer[] {
    return answers.map(a => ({
      seniorId: a.seniorId ?? '',
      name: a.name ?? '',
      school: a.school ?? '',
      major: a.major ?? '',
      year: a.year ?? '',
      domain: a.domain ?? '',
      content: a.content ?? '',
    }));
  }

  /** 每条专家消息之间的冒出间隔（ms）：像群聊里群友逐个发言 */
  const PANEL_REVEAL_DELAY = 650;

  const sleep = (ms: number) => new Promise<void>(resolve => setTimeout(resolve, ms));

  /** 专家会审气泡逐个冒出：移除占位消息，每间隔 PANEL_REVEAL_DELAY push 一条专家消息 */
  async function applyAnswers(pendingId: string, answers: ChatAnswer[]) {
    const experts = toExpertAnswers(answers);
    const index = messages.value.findIndex(m => m.id === pendingId);
    if (index >= 0) messages.value.splice(index, 1);
    for (let i = 0; i < experts.length; i++) {
      if (i > 0) await sleep(PANEL_REVEAL_DELAY);
      const expert = experts[i];
      messages.value.push({
        id: newUuid(),
        role: 'assistant',
        content: expert.content,
        answers: [expert],
      });
    }
    activePanel.value = experts;
  }

  /** 发送消息：可锁定指定 Skill；未指定时自动召回多位专家，整条 QQ 式呈现 */
  async function send(text: string, seniorId?: string) {
    if (loading.value || !text.trim()) return;
    loading.value = true;
    error.value = null;
    let pendingId = '';

    try {
      addUser(text);
      pendingId = addAssistantPlaceholder();
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
        if (msg) { msg.content = '暂时没有匹配的专家，换个问法试试。'; msg.isStreaming = false; }
        return;
      }
      if (STREAMING_ENABLED) {
        streamInto(pendingId, answers.map(a => a.content).join('\n'));
      }
      await applyAnswers(pendingId, answers);
    } catch (e) {
      const msg = pendingId ? messages.value.find(m => m.id === pendingId) : undefined;
      if (msg) { msg.content = '暂时无法连接服务器，请稍后重试。'; msg.isStreaming = false; }
      error.value = (e as Error).message;
    } finally {
      loading.value = false;
    }
  }

  /** 换一批：排除本批评委重新回答最后一个问题 */
  async function switchPanel() {
    const lastUser = [...messages.value].reverse().find(m => m.role === 'user');
    if (!lastUser || loading.value) return;

    loading.value = true;
    let pendingId = '';
    try {
      pendingId = addAssistantPlaceholder();
      const resp: ChatResponseV1 = await chatApi.send({
        message: lastUser.content,
        sessionId: sessionId.value || undefined,
        excludeSeniorId: activePanel.value.map(a => a.seniorId).join(','),
      });
      sessionId.value = resp.sessionId;
      saveSession(resp.sessionId);
      const answers = resp.answers ?? [];
      if (answers.length === 0) {
        const msg = messages.value.find(m => m.id === pendingId);
        if (msg) { msg.content = '暂时没有匹配的专家，换个问法试试。'; msg.isStreaming = false; }
        return;
      }
      await applyAnswers(pendingId, answers);
    } catch (e) {
      const msg = pendingId ? messages.value.find(m => m.id === pendingId) : undefined;
      if (msg) { msg.content = '切换失败，请重试。'; msg.isStreaming = false; }
      error.value = (e as Error).message;
    } finally {
      loading.value = false;
    }
  }

  async function loadHistory() {
    if (!sessionId.value) return;
    try {
      const rows = await chatApi.listMessages(sessionId.value, { limit: 50 });
      const restored: ChatMessage[] = [];
      let panel: ExpertAnswer[] = [];
      for (const row of rows.reverse()) {
        if (row.role === 'user') {
          restored.push({ id: newUuid(), role: 'user', content: row.content ?? '' });
          continue;
        }
        if (Array.isArray(row.answers) && row.answers.length > 0) {
          const experts = (row.answers as ChatAnswer[]).map(a => ({
            seniorId: a.seniorId ?? '',
            name: a.name ?? '',
            school: a.school ?? '',
            major: a.major ?? '',
            year: a.year ?? '',
            domain: a.domain ?? '',
            content: a.content ?? '',
          }));
          for (const expert of experts) {
            restored.push({ id: newUuid(), role: 'assistant', content: expert.content, answers: [expert] });
          }
          panel = experts;
          continue;
        }
        restored.push({ id: newUuid(), role: 'assistant', content: row.content ?? '' });
      }
      messages.value = restored;
      activePanel.value = panel;
    } catch {
      // 历史拉取失败不阻塞
    }
  }

  function reset() {
    messages.value = [];
    sessionId.value = '';
    activePanel.value = [];
    if (typeof localStorage !== 'undefined') localStorage.removeItem(SESSION_KEY);
  }

  return {
    messages, sessionId, loading, error, activePanel,
    send, switchPanel, loadHistory, reset, getOrCreateUser, addUser,
  };
});
