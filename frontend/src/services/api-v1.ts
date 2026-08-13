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
 * 默认登录身份：后端 DemoDataSeeder 预置的「演示同学」。
 * 新访客首次访问即以此身份进入，能力画像/沉淀材料开箱即显示；
 * localStorage 已有身份的老访客保留原值。
 */
const DEFAULT_USER_ID: Uuid = '11111111-1111-4111-8111-111111111111';

export function getOrCreateUserId(): Uuid {
  if (typeof localStorage === 'undefined') return DEFAULT_USER_ID;
  const cached = localStorage.getItem(USER_ID_KEY);
  if (cached) return cached;
  localStorage.setItem(USER_ID_KEY, DEFAULT_USER_ID);
  return DEFAULT_USER_ID;
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
