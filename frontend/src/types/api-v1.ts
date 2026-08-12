/**
 * Skill Hub API v1 — TypeScript 类型镜像。
 *
 * 这是 v1 契约的单一真理源（前端侧）。后端字段以
 * `docs/api-v1.md` 为准；本文件**只**镜像，不解释。
 *
 * AI 合并冲突规则（已锁）：
 *   docs/api-v1.md  >  docs/contract-changelog.md  >  本文件  >  已合并 PR  >  飞线 PR
 *
 * 错误码枚举与后端 `dto/ErrorCode.java` 一一对应。
 */

// -----------------------------------------------------------------------------
// 通用
// -----------------------------------------------------------------------------

/** ISO-8601 UTC 时间戳字符串。 */
export type Iso8601 = string;

/** UUIDv4 字符串。 */
export type Uuid = string;

/** 分页响应。 */
export type Page<T> = {
  items: T[];
  nextCursor: string | null;
};

/** 统一错误信封。 */
export type ErrorEnvelope = {
  error: {
    code: ErrorCode;
    message: string;
    details?: unknown;
    traceId: string;
  };
};

// -----------------------------------------------------------------------------
// 错误码（与后端 dto/ErrorCode.java 对齐）
// -----------------------------------------------------------------------------

export const ErrorCode = {
  // AUTH
  AUTH_MISSING_USER_ID: 'AUTH_MISSING_USER_ID',
  AUTH_INVALID_USER_ID: 'AUTH_INVALID_USER_ID',

  // USER
  USER_NOT_FOUND: 'USER_NOT_FOUND',
  USER_VALIDATION_FAILED: 'USER_VALIDATION_FAILED',
  USER_DISPLAY_NAME_TAKEN: 'USER_DISPLAY_NAME_TAKEN',

  // POST
  POST_NOT_FOUND: 'POST_NOT_FOUND',
  POST_TITLE_TOO_LONG: 'POST_TITLE_TOO_LONG',
  POST_BODY_TOO_LONG: 'POST_BODY_TOO_LONG',
  POST_VALIDATION_FAILED: 'POST_VALIDATION_FAILED',

  // COMMENT
  COMMENT_NOT_FOUND: 'COMMENT_NOT_FOUND',
  COMMENT_FORBIDDEN: 'COMMENT_FORBIDDEN',
  COMMENT_BODY_TOO_LONG: 'COMMENT_BODY_TOO_LONG',
  COMMENT_PARENT_NOT_FOUND: 'COMMENT_PARENT_NOT_FOUND',
  COMMENT_VALIDATION_FAILED: 'COMMENT_VALIDATION_FAILED',

  // LIKE
  LIKE_ALREADY: 'LIKE_ALREADY',
  LIKE_NOT_LIKED: 'LIKE_NOT_LIKED',

  // CHAT
  CHAT_EMPTY_MESSAGE: 'CHAT_EMPTY_MESSAGE',
  CHAT_VALIDATION_FAILED: 'CHAT_VALIDATION_FAILED',
  CHAT_SESSION_NOT_FOUND: 'CHAT_SESSION_NOT_FOUND',
  CHAT_LLM_DEGRADED: 'CHAT_LLM_DEGRADED',

  // SKILL
  SKILL_VALIDATION_FAILED: 'SKILL_VALIDATION_FAILED',
  SKILL_RECALL_TIMEOUT: 'SKILL_RECALL_TIMEOUT',
  SKILL_RECALL_DISABLED: 'SKILL_RECALL_DISABLED',

  // SENIOR
  SENIOR_NOT_FOUND: 'SENIOR_NOT_FOUND',
  SENIOR_DISTILL_TIMEOUT: 'SENIOR_DISTILL_TIMEOUT',

  // GENERAL
  GENERAL_INTERNAL: 'GENERAL_INTERNAL',
  GENERAL_NOT_FOUND: 'GENERAL_NOT_FOUND',
  GENERAL_METHOD_NOT_ALLOWED: 'GENERAL_METHOD_NOT_ALLOWED',
  GENERAL_VALIDATION: 'GENERAL_VALIDATION',
} as const;

export type ErrorCode = (typeof ErrorCode)[keyof typeof ErrorCode];

/** 业务异常类（前端捕获用）。 */
export class ApiError extends Error {
  readonly code: ErrorCode;
  readonly status: number;
  readonly details?: unknown;
  readonly traceId: string;

  constructor(
    code: ErrorCode,
    message: string,
    status: number,
    traceId: string,
    details?: unknown,
  ) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.details = details;
    this.traceId = traceId;
  }
}

// -----------------------------------------------------------------------------
// users
// -----------------------------------------------------------------------------

export type UserRole = 'GUEST' | string;

export type UserDto = {
  id: Uuid;
  displayName: string;
  avatarUrl: string | null;
  role: UserRole;
  createdAt: Iso8601;
};

export type PatchUserRequest = {
  displayName?: string;
  avatarUrl?: string | null;
};

// -----------------------------------------------------------------------------
// posts
// -----------------------------------------------------------------------------

export type PostSummary = {
  id: Uuid;
  title: string;
  excerpt: string;
  coverColor: string;
  authorId: Uuid;
  authorName: string;
  authorAvatar: string | null;
  domain: string | null;
  likeCount: number;
  commentCount: number;
  createdAt: Iso8601;
};

export type PostDetail = {
  summary: PostSummary;
  body: string;
};

export type CreatePostRequest = {
  title: string;
  body: string;
  domain?: string;
};

export type ListPostsQuery = {
  cursor?: string;
  limit?: number;
  authorId?: Uuid;
  domain?: string;
};

// -----------------------------------------------------------------------------
// comments
// -----------------------------------------------------------------------------

export type CommentDto = {
  id: Uuid;
  postId: Uuid;
  authorId: Uuid;
  authorName: string;
  authorAvatar: string | null;
  parentId: Uuid | null;
  body: string;
  createdAt: Iso8601;
};

export type CreateCommentRequest = {
  body: string;
  parentId?: Uuid | null;
};

export type ListCommentsQuery = {
  cursor?: string;
  limit?: number;
};

// -----------------------------------------------------------------------------
// likes
// -----------------------------------------------------------------------------

export type LikeResult = {
  likeCount: number;
  liked: boolean;
};

// -----------------------------------------------------------------------------
// chat
// -----------------------------------------------------------------------------

/**
 * 请求体（v1）— 不可序列化为 record；保留 class（后端 mutable Java class 强制约束）。
 * 前端用 interface 即可。
 */
export type ChatRequestV1 = {
  message: string;
  sessionId?: Uuid;
};

export type ChatAnswer = {
  seniorId: Uuid;
  name: string;
  school: string;
  major: string;
  year: string;
  content: string;
};

export type ChatResponseV1 = {
  sessionId: Uuid;
  answers: ChatAnswer[];
};

export type ChatSessionDto = {
  sessionId: Uuid;
  title: string | null;
  updatedAt: Iso8601;
};

export type ChatMessageDto = {
  role: 'user' | 'assistant';
  content: string;
  /** assistant 行的 ChatAnswer[] JSON 序列化产物（透传）。 */
  answers?: unknown;
  createdAt: Iso8601;
};

export type ListChatMessagesQuery = {
  cursor?: string;
  limit?: number;
};

// -----------------------------------------------------------------------------
// memories
// -----------------------------------------------------------------------------

export type CreateMemoryRequest = {
  title?: string;
  tags?: string[];
};

export type CreateMemoryResponse = {
  memoryId: Uuid;
};

export type ChatMemoryDto = {
  memoryId: Uuid;
  sessionId: Uuid;
  title: string | null;
  tags: string[];
  createdAt: Iso8601;
};

export type ListMemoriesQuery = {
  cursor?: string;
  limit?: number;
};

// -----------------------------------------------------------------------------
// skills（公开召回）
// -----------------------------------------------------------------------------

export type SkillRecallItem = {
  seniorId: Uuid;
  /** 0.0~1.0 */
  score: number;
  /** 自然语言片段；可能含 markdown。 */
  text: string;
  tags: string[];
};

export type RecallRequest = {
  query: string;
  topK?: number;
  domain?: string;
  school?: string;
};

export type RecallResponse = {
  items: SkillRecallItem[];
};

// -----------------------------------------------------------------------------
// seniors（蒸馏 / 片段）
// -----------------------------------------------------------------------------

export const SeniorFragmentKind = {
  PERSONA: 'PERSONA',
  WORK: 'WORK',
  MEMORY: 'MEMORY',
  OTHER: 'OTHER',
} as const;

export type SeniorFragmentKind =
  (typeof SeniorFragmentKind)[keyof typeof SeniorFragmentKind];

export type SeniorFragmentDto = {
  id: Uuid;
  seniorId: Uuid;
  kind: SeniorFragmentKind;
  content: string;
  tags: string[];
  createdAt: Iso8601;
};

export type DistillResult = {
  seniorId: Uuid;
  fragments: SeniorFragmentDto[];
  updatedAt: Iso8601;
};

export type ListSeniorFragmentsQuery = {
  cursor?: string;
  limit?: number;
};

// -----------------------------------------------------------------------------
// 横切：headers
// -----------------------------------------------------------------------------

/** 请求头常量。 */
export const Headers = {
  USER_ID: 'X-User-Id',
  TRACE_ID: 'X-Trace-Id',
} as const;
