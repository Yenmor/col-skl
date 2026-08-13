/**
 * Skill Hub API v1 — 前端 service（实装版）。
 *
 * 设计依据：{@code docs/api-v1.md}
 *   - axios 实例复用 vite.config.ts 现有 /api 代理（→ :8080）
 *   - authHeader() 读 localStorage.persist.userId；缺失则 crypto.randomUUID() 生成并写入
 *   - request<T> 统一拦截 4xx/5xx，抛 ApiError
 *   - chatService / skills/recall / distill 不带 fallback
 *   - seniorService / communityService 保留 fallback（CLAUDE.md 强制）
 */

import axios, { type AxiosInstance, type AxiosResponse } from 'axios';
import { ApiError, ErrorCode, Headers, type ErrorEnvelope, type Uuid } from '../types/api-v1';
import type {
  ChatMemoryDto,
  ChatMessageDto,
  ChatRequestV1,
  ChatResponseV1,
  ChatSessionDto,
  CommentDto,
  CreateCommentRequest,
  CreateMemoryRequest,
  CreateMemoryResponse,
  CreatePostRequest,
  DistillResult,
  LikeResult,
  ListChatMessagesQuery,
  ListCommentsQuery,
  ListMemoriesQuery,
  ListPostsQuery,
  ListSeniorFragmentsQuery,
  Page,
  PatchUserRequest,
  PostDetail,
  PostSummary,
  RecallRequest,
  SeniorFragmentDto,
  SkillRecallItem,
  UserDto,
} from '../types/api-v1';

// -----------------------------------------------------------------------------
// 基础
// -----------------------------------------------------------------------------

const http: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json; charset=utf-8' },
});

const USER_ID_KEY = 'persist.userId';

/**
 * 生成匿名用户 ID。crypto.randomUUID 仅在安全上下文（HTTPS/localhost）可用，
 * 生产 HTTP 环境回退到时间戳 + 随机数，输出合法 8-4-4-4-12 UUID 格式。
 */
export function newUuid(): Uuid {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  const hex = (n: number, width: number) => n.toString(16).padStart(width, '0');
  const rand = (width: number) => Math.floor(Math.random() * Math.pow(16, width));
  const t = Math.floor(Date.now() / 1000);
  const variant = hex(8 + Math.floor(Math.random() * 4), 1);
  return `${hex(t % 0x100000000, 8)}-${hex(rand(4), 4)}-4${hex(rand(3), 3)}-${variant}${hex(rand(3), 3)}-${hex(rand(6), 6)}${hex(rand(6), 6)}`;
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function getOrCreateUserId(): Uuid {
  if (typeof localStorage === 'undefined') return newUuid();
  const cached = localStorage.getItem(USER_ID_KEY);
  // 历史缺陷版本可能写入过畸形 ID，检测到非法格式时重置为新的随机身份。
  if (cached && UUID_PATTERN.test(cached)) return cached;
  const fresh = newUuid();
  localStorage.setItem(USER_ID_KEY, fresh);
  return fresh;
}

function authHeader(): Record<string, string> {
  return { [Headers.USER_ID]: getOrCreateUserId() };
}

export async function request<T>(method: string, url: string, body?: unknown, extraHeaders?: Record<string, string>): Promise<T> {
  try {
    const res: AxiosResponse<T> = await http.request<T>({
      method,
      url,
      data: body,
      headers: { ...authHeader(), ...(extraHeaders ?? {}) },
    });
    return res.data;
  } catch (err) {
    if (axios.isAxiosError(err)) {
      const env = err.response?.data as ErrorEnvelope | undefined;
      const status = err.response?.status ?? 0;
      const traceId = (err.response?.headers?.[Headers.TRACE_ID.toLowerCase()] as string) ?? '';
      if (env && env.error) {
        throw new ApiError(
          (env.error.code as ErrorCode) ?? ErrorCode.GENERAL_INTERNAL,
          env.error.message ?? err.message,
          status,
          traceId,
          env.error.details,
        );
      }
      throw new ApiError(ErrorCode.GENERAL_INTERNAL, err.message, status, traceId);
    }
    throw err;
  }
}

// -----------------------------------------------------------------------------
// users
// -----------------------------------------------------------------------------

export const usersApi = {
  getMe: () => request<UserDto>('GET', '/users/me'),
  patchMe: (body: PatchUserRequest) => request<UserDto>('PATCH', '/users/me', body),
};

// -----------------------------------------------------------------------------
// posts
// -----------------------------------------------------------------------------

function toQuery(params: Record<string, unknown>): string {
  const parts: string[] = [];
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue;
    parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
  }
  return parts.length ? `?${parts.join('&')}` : '';
}

export const postsApi = {
  list: (q: ListPostsQuery = {}) => {
    const qs = toQuery(q as Record<string, unknown>);
    return request<Page<PostSummary>>('GET', `/posts${qs}`);
  },
  create: (body: CreatePostRequest) => request<PostSummary>('POST', '/posts', body),
  get: (id: Uuid) => request<PostDetail>('GET', `/posts/${id}`),
};

// -----------------------------------------------------------------------------
// comments
// -----------------------------------------------------------------------------

export const commentsApi = {
  list: (postId: Uuid, q: ListCommentsQuery = {}) => {
    const qs = toQuery(q as Record<string, unknown>);
    return request<CommentDto[]>('GET', `/posts/${postId}/comments${qs}`);
  },
  create: (postId: Uuid, body: CreateCommentRequest) =>
    request<CommentDto>('POST', `/posts/${postId}/comments`, body),
  remove: (commentId: Uuid) => request<void>('DELETE', `/comments/${commentId}`),
};

// -----------------------------------------------------------------------------
// likes
// -----------------------------------------------------------------------------

export const likesApi = {
  like: (postId: Uuid) => request<LikeResult>('POST', `/posts/${postId}/like`),
  unlike: (postId: Uuid) => request<LikeResult>('DELETE', `/posts/${postId}/like`),
};

// -----------------------------------------------------------------------------
// chat
// -----------------------------------------------------------------------------

export const chatApi = {
  send: (body: ChatRequestV1) => request<ChatResponseV1>('POST', '/chat', body),
  listSessions: () => request<ChatSessionDto[]>('GET', '/chat/sessions'),
  listMessages: (sessionId: Uuid, q: ListChatMessagesQuery = {}) => {
    const qs = toQuery(q as Record<string, unknown>);
    return request<ChatMessageDto[]>('GET', `/chat/sessions/${sessionId}/messages${qs}`);
  },
};

// -----------------------------------------------------------------------------
// memories
// -----------------------------------------------------------------------------

export const memoriesApi = {
  create: (sessionId: Uuid, body: CreateMemoryRequest = {}) =>
    request<CreateMemoryResponse>('POST', `/chat/sessions/${sessionId}/memories`, body),
  listMine: (q: ListMemoriesQuery = {}) => {
    const qs = toQuery(q as Record<string, unknown>);
    return request<ChatMemoryDto[]>('GET', `/users/me/memories${qs}`);
  },
};

// -----------------------------------------------------------------------------
// skills
// -----------------------------------------------------------------------------

export const skillsApi = {
  recall: (body: RecallRequest) => request<SkillRecallItem[]>('POST', '/skills/recall', body),
};

// -----------------------------------------------------------------------------
// seniors
// -----------------------------------------------------------------------------

export const seniorsApi = {
  distill: (seniorId: Uuid) => request<DistillResult>('POST', `/seniors/${seniorId}/distill`),
  listFragments: (seniorId: Uuid, q: ListSeniorFragmentsQuery = {}) => {
    const qs = toQuery(q as Record<string, unknown>);
    return request<SeniorFragmentDto[]>('GET', `/seniors/${seniorId}/fragments${qs}`);
  },
};

// -----------------------------------------------------------------------------
// 聚合
// -----------------------------------------------------------------------------

export const apiV1 = {
  users: usersApi,
  posts: postsApi,
  comments: commentsApi,
  likes: likesApi,
  chat: chatApi,
  memories: memoriesApi,
  skills: skillsApi,
  seniors: seniorsApi,
};

export default apiV1;
