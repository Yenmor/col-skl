# Skill Hub API v1 — Error Codes

> 唯一真理源。AI 合并冲突时以此表为准。
> 新增 code 必须先在此登记，再写代码。

---

## 命名规范（D9）

- 资源前缀 + 下划线 + 原因：`POST_NOT_FOUND` / `COMMENT_FORBIDDEN` / `LIKE_ALREADY` / `SKILL_RECALL_TIMEOUT`。
- **UPPER_SNAKE_CASE**。
- 后端 Java 端以 `ErrorCode` 公共枚举（`dto/ErrorCode.java`）存在；前端以 TS 枚举（`frontend/src/types/api-v1.ts`）镜像。
- 公共枚举位置：方案一（已锁），单一 `dto/ErrorCode.java`，不分包。

---

## AUTH_*

| code | HTTP | 含义 | 触发位置 |
|---|---|---|---|
| `AUTH_MISSING_USER_ID` | 401 | 请求缺 `X-User-Id` 或非 UUIDv4 | `BaseController.currentUserId()` |
| `AUTH_INVALID_USER_ID` | 401 | `X-User-Id` 格式非法（虽 UUID 但校验失败） | `BaseController.currentUserId()` |

---

## USER_*

| code | HTTP | 含义 | 触发位置 |
|---|---|---|---|
| `USER_NOT_FOUND` | 404 | 服务端找不到该 user（理论上不应发生） | `UserService` |
| `USER_VALIDATION_FAILED` | 400 | `displayName` 长度 / 字符非法 | `PATCH /users/me` |
| `USER_DISPLAY_NAME_TAKEN` | 400 | 暂不启用；匿名 UUID 模式无重名风险，保留占位 | — |

---

## POST_*

| code | HTTP | 含义 | 触发位置 |
|---|---|---|---|
| `POST_NOT_FOUND` | 404 | 帖子不存在 | `GET/POST /posts/:id`, `GET/POST /posts/:id/comments`, `POST/DELETE /posts/:id/like` |
| `POST_TITLE_TOO_LONG` | 400 | 标题超过 80 字 | `POST /posts` |
| `POST_BODY_TOO_LONG` | 400 | 正文超过 20000 字 | `POST /posts` |
| `POST_VALIDATION_FAILED` | 400 | `limit` 越界 / `cursor` 非法 / body 缺字段 | 帖子相关 |

---

## COMMENT_*

| code | HTTP | 含义 | 触发位置 |
|---|---|---|---|
| `COMMENT_NOT_FOUND` | 404 | 评论不存在 | `DELETE /comments/:id` |
| `COMMENT_FORBIDDEN` | 403 | 非作者本人 | `DELETE /comments/:id` |
| `COMMENT_BODY_TOO_LONG` | 400 | 评论正文超过 2000 字 | `POST /comments` |
| `COMMENT_PARENT_NOT_FOUND` | 400 | `parentId` 指向不存在的评论 | `POST /comments` |
| `COMMENT_VALIDATION_FAILED` | 400 | 评论请求体 / query 校验失败 | 评论相关 |

---

## LIKE_*

| code | HTTP | 含义 | 触发位置 |
|---|---|---|---|
| `LIKE_ALREADY` | 400 | 重复点赞（应被幂等 toggle 吸收，保留占位） | `POST /posts/:id/like` |
| `LIKE_NOT_LIKED` | 400 | 取消未点赞（应被幂等 toggle 吸收，保留占位） | `DELETE /posts/:id/like` |

---

## CHAT_*

| code | HTTP | 含义 | 触发位置 |
|---|---|---|---|
| `CHAT_EMPTY_MESSAGE` | 400 | 消息为空 / 仅空白 | `POST /chat` |
| `CHAT_VALIDATION_FAILED` | 400 | `message` 长度 / `sessionId` 非法 | `POST /chat` |
| `CHAT_SESSION_NOT_FOUND` | 404 | sessionId 不存在 | `GET /chat/sessions/:id/messages`, `POST /chat/sessions/:id/memories` |
| `CHAT_LLM_DEGRADED` | 503 | LLM 全失败且 `LLM_FALLBACK_TO_MOCK=false` | `POST /chat` |

---

## SKILL_*

| code | HTTP | 含义 | 触发位置 |
|---|---|---|---|
| `SKILL_VALIDATION_FAILED` | 400 | `query` 长度 / `topK` 越界 | `POST /skills/recall` |
| `SKILL_RECALL_TIMEOUT` | 503 | 召回超时 | `POST /skills/recall` |
| `SKILL_RECALL_DISABLED` | 503 | 召回服务被运维关闭 | `POST /skills/recall` |
| `SKILL_NO_MATCH` | 200（空 items） | 无匹配（不视为错误） | `POST /skills/recall` |

---

## SENIOR_*

| code | HTTP | 含义 | 触发位置 |
|---|---|---|---|
| `SENIOR_NOT_FOUND` | 404 | 学长 ID 不存在（七件套缺失） | `POST /seniors/:id/distill`, `GET /seniors/:id/fragments` |
| `SENIOR_DISTILL_TIMEOUT` | 503 | 蒸馏超时（>30s） | `POST /seniors/:id/distill` |
| `SENIOR_FRAGMENT_EMPTY` | 200（空 fragments） | 蒸馏无产物（不视为错误） | `POST /seniors/:id/distill` |

---

## GENERAL_*

| code | HTTP | 含义 | 触发位置 |
|---|---|---|---|
| `GENERAL_INTERNAL` | 500 | 兜底 | `GlobalExceptionHandler` |
| `GENERAL_NOT_FOUND` | 404 | 路由未匹配 | `GlobalExceptionHandler` |
| `GENERAL_METHOD_NOT_ALLOWED` | 405 | HTTP 方法不支持 | `GlobalExceptionHandler` |
| `GENERAL_VALIDATION` | 400 | 全局参数校验失败 | `GlobalExceptionHandler`（`MethodArgumentNotValidException` 等） |

---

## 待办（未启用）

> v2 候选，本次 v1 不引入：
> - `RATE_LIMIT_EXCEEDED`（D10 MVP 不做）
> - `VERSION_DEPRECATED`（30 天后下架时再启用）
> - `LLM_QUOTA_EXCEEDED`（DeepSeek 配额用尽时启用）
