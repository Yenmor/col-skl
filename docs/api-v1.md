# Skill Hub API v1 契约

> 单一真理源（Single Source of Truth）。
> 所有 v1 路由的请求/响应、错误体、分页、稳定性以此文档为准。
> AI 合并冲突优先级：`docs/api-v1.md` > `docs/contract-changelog.md` > 已合并 PR > 飞线 PR。

---

## 0. 基础约定

### 0.1 通用头部

| Header | 方向 | 说明 |
|---|---|---|
| `X-User-Id` | 请求 | 匿名 UUIDv4。缺失时，前端 axios 拦截器自动生成并写入 `localStorage.persist.userId` |
| `X-Trace-Id` | 请求 / 响应 | 全链路追踪 ID。前端无需生成；后端在缺省时填入 UUIDv4 |
| `Deprecation: true` | 响应 | 仅出现在 `/api/*`（provisional）路径上 |
| `Sunset: <RFC-7231-date>` | 响应 | 灰度截止日，仅 provisional 路径有 |
| `Content-Type: application/json; charset=utf-8` | 响应 | 全部 JSON |

### 0.2 时间与 ID

- **时间**：ISO-8601 UTC（`2026-08-12T14:30:00Z`），后端 `Instant.toString()`，前端 `new Date()`。
- **id**：UUIDv4 字符串（与现状一致）。
- **不要**自增 int。

### 0.3 分页（cursor）

- 所有列表接口走 **cursor 分页**，不使用 `offset`。
- `cursor` 是 base64(`{"id":"<lastItemId>"}`)，客户端**不解析**，只回传。
- 首屏请求可不带 `cursor`。
- 响应：
  ```json
  { "items": [...], "nextCursor": "eyJpZCI6ImRlZiJ9" | null }
  ```
- 单资源 `GET /:id` 不分页。

### 0.4 错误信封（所有 4xx / 5xx）

```json
{
  "error": {
    "code": "POST_NOT_FOUND",
    "message": "帖子不存在",
    "details": { "postId": "abc" },
    "traceId": "0a1b2c..."
  }
}
```

详见 `docs/error-codes.md`。

### 0.5 稳定性标注

- **provisional**（现有 `/api/chat, /api/seniors, /api/community`）：字段随时可改，**不保证 ABI**。
- **stable**（`/api/v1/*`）：字段**只增不删**，删除走 v2。
- 30 天灰度：`Deprecation: true` + `Sunset: <date>`，到日下架。

### 0.6 自然语言产物接口的契约

`/api/v1/skills/recall` 与 `/api/v1/seniors/:id/distill` 的产物基本是自然语言片段。
- DTO 只定义**最小外壳**：`seniorId / score / text / tags`（recall），`kind / content / tags`（distill）。
- 其余字段（嵌套对话、引用链、概率）由 Python 端产出，**Java 端透传** `JsonNode` 给前端。
- 前端**只解析外壳字段**，其它字段直接展示原文。

---

## 1. users

### 1.1 `GET /api/v1/users/me`

- **稳定性**：stable
- **请求**：无 body；需要 `X-User-Id`
- **响应 200**：
  ```json
  {
    "id": "uuid",
    "displayName": "游客#a3f9",
    "avatarUrl": null,
    "role": "GUEST",
    "createdAt": "2026-08-12T14:30:00Z"
  }
  ```
- **错误**：
  - `401 AUTH_MISSING_USER_ID` —— `X-User-Id` 缺失或非 UUIDv4
  - `404 USER_NOT_FOUND` —— 服务端找不到该 user（理论上不应发生）

### 1.2 `PATCH /api/v1/users/me`

- **稳定性**：stable
- **请求 body**：
  ```json
  { "displayName": "string(1-24)", "avatarUrl": "string? | null" }
  ```
- **响应 200**：与 `GET /me` 同。
- **错误**：
  - `400 USER_VALIDATION_FAILED` —— `displayName` 长度 / 字符非法
  - `400 USER_DISPLAY_NAME_TAKEN` —— 暂不启用（匿名 UUID 模式无重名风险），保留占位
  - `401 AUTH_MISSING_USER_ID`

---

## 2. posts

### 2.1 `GET /api/v1/posts`

- **稳定性**：stable
- **Query**：
  - `cursor` string? — 上一页 `nextCursor`
  - `limit` int? — 默认 20，最大 50
  - `authorId` string? — 按作者过滤
  - `domain` string? — 领域过滤（如 `竞赛 / 保研 / 科研 / 求职 / 实习`）
- **响应 200**：
  ```json
  {
    "items": [
      {
        "id": "uuid",
        "title": "string",
        "excerpt": "string(<=200)",
        "coverColor": "#hex",
        "authorId": "uuid",
        "authorName": "string",
        "authorAvatar": "string?",
        "domain": "string?",
        "likeCount": 0,
        "commentCount": 0,
        "createdAt": "2026-08-12T14:30:00Z"
      }
    ],
    "nextCursor": "string | null"
  }
  ```
- **错误**：
  - `400 POST_VALIDATION_FAILED` —— `limit` 越界 / `cursor` 非法

### 2.2 `POST /api/v1/posts`

- **稳定性**：stable
- **请求 body**：
  ```json
  { "title": "string(1-80)", "body": "string(1-20000)", "domain": "string?" }
  ```
- **响应 201**：`PostSummary`（同 2.1 的 `items[]` 元素）
- **错误**：
  - `400 POST_TITLE_TOO_LONG` / `POST_BODY_TOO_LONG` / `POST_VALIDATION_FAILED`
  - `401 AUTH_MISSING_USER_ID`

### 2.3 `GET /api/v1/posts/:id`

- **稳定性**：stable
- **响应 200**：`PostDetail`（在 `PostSummary` 基础上 + `body` 全文）
- **错误**：
  - `404 POST_NOT_FOUND`

---

## 3. comments

### 3.1 `GET /api/v1/posts/:postId/comments`

- **稳定性**：stable
- **Query**：
  - `cursor` string?
  - `limit` int? — 默认 20，最大 50
- **响应 200**：
  ```json
  {
    "items": [
      {
        "id": "uuid",
        "postId": "uuid",
        "authorId": "uuid",
        "authorName": "string",
        "authorAvatar": "string?",
        "parentId": "uuid | null",
        "body": "string",
        "createdAt": "2026-08-12T14:30:00Z"
      }
    ],
    "nextCursor": "string | null"
  }
  ```
- **错误**：
  - `404 POST_NOT_FOUND` —— `:postId` 不存在
  - `400 COMMENT_VALIDATION_FAILED`

### 3.2 `POST /api/v1/posts/:postId/comments`

- **稳定性**：stable
- **请求 body**：
  ```json
  { "body": "string(1-2000)", "parentId": "uuid | null" }
  ```
- **响应 201**：`CommentDto`（同 3.1 `items[]` 元素）
- **错误**：
  - `404 POST_NOT_FOUND`
  - `400 COMMENT_BODY_TOO_LONG` / `COMMENT_VALIDATION_FAILED` / `COMMENT_PARENT_NOT_FOUND`
  - `401 AUTH_MISSING_USER_ID`

### 3.3 `DELETE /api/v1/comments/:id`

- **稳定性**：stable
- **响应 204**：空 body
- **错误**：
  - `404 COMMENT_NOT_FOUND`
  - `403 COMMENT_FORBIDDEN` —— 非作者本人
  - `401 AUTH_MISSING_USER_ID`

> v1 评论 UI 展平一层；`parentId` 字段保留，**不渲染楼中楼**。

---

## 4. likes

### 4.1 `POST /api/v1/posts/:postId/like`

- **稳定性**：stable
- **幂等 toggle**。已点赞则返回当前状态。
- **响应 200**：
  ```json
  { "likeCount": 42, "liked": true }
  ```
- **错误**：
  - `404 POST_NOT_FOUND`
  - `401 AUTH_MISSING_USER_ID`

### 4.2 `DELETE /api/v1/posts/:postId/like`

- **稳定性**：stable
- **响应 200**：
  ```json
  { "likeCount": 41, "liked": false }
  ```
- **错误**：同 4.1。

> 计数策略（D3）：`post_likes(user_id, post_id)` 事实表 + `posts.like_count` 冗余，**应用层事务**维护。切 MySQL 时一致。

---

## 5. chat

### 5.1 `POST /api/v1/chat`

- **稳定性**：stable
- **请求 body**（`ChatRequestV1` — mutable class，受 `CLAUDE.md` 强制约束）：
  ```json
  { "message": "string(1-2000)", "sessionId": "uuid?" }
  ```
- **响应 200**：
  ```json
  {
    "sessionId": "uuid",
    "answers": [
      {
        "seniorId": "uuid",
        "name": "string",
        "school": "string",
        "major": "string",
        "year": "string",
        "content": "string(自然语言)"
      }
    ]
  }
  ```
- **错误**：
  - `400 CHAT_EMPTY_MESSAGE` / `CHAT_VALIDATION_FAILED`
  - `401 AUTH_MISSING_USER_ID`
  - `503 CHAT_LLM_DEGRADED` —— LLM 全失败且 fallback 关闭

> `sessionId` 服务端生成（D5），首响应返回，后续客户端回带。
> 内部直接调同进程的 `SkillRecallService`（D6），不 HTTP 自调。

### 5.2 `GET /api/v1/chat/sessions`

- **稳定性**：stable
- **响应 200**：
  ```json
  {
    "items": [
      { "sessionId": "uuid", "title": "string?", "updatedAt": "2026-08-12T14:30:00Z" }
    ],
    "nextCursor": "string | null"
  }
  ```
- **错误**：
  - `401 AUTH_MISSING_USER_ID`

### 5.3 `GET /api/v1/chat/sessions/:sessionId/messages`

- **稳定性**：stable
- **Query**：`cursor` string?、`limit` int?（默认 50，最大 100）
- **响应 200**：
  ```json
  {
    "items": [
      {
        "role": "user | assistant",
        "content": "string",
        "answers": "any?（assistant 行的 JSON 序列化）",
        "createdAt": "2026-08-12T14:30:00Z"
      }
    ],
    "nextCursor": "string | null"
  }
  ```
- **错误**：
  - `404 CHAT_SESSION_NOT_FOUND`
  - `401 AUTH_MISSING_USER_ID`

> 暴露现有 `SqliteChatRepository.recentBySession`（仓里死接口）。

---

## 6. memories

### 6.1 `POST /api/v1/chat/sessions/:sessionId/memories`

- **稳定性**：stable
- **请求 body**：
  ```json
  { "title": "string?", "tags": ["string"]? }
  ```
- **响应 201**：
  ```json
  { "memoryId": "uuid" }
  ```
- **错误**：
  - `404 CHAT_SESSION_NOT_FOUND`
  - `401 AUTH_MISSING_USER_ID`

### 6.2 `GET /api/v1/users/me/memories`

- **稳定性**：stable
- **Query**：`cursor` string?、`limit` int?（默认 20，最大 50）
- **响应 200**：
  ```json
  {
    "items": [
      {
        "memoryId": "uuid",
        "sessionId": "uuid",
        "title": "string?",
        "tags": ["string"],
        "createdAt": "2026-08-12T14:30:00Z"
      }
    ],
    "nextCursor": "string | null"
  }
  ```
- **错误**：
  - `401 AUTH_MISSING_USER_ID`

---

## 7. skills（公开召回服务）

### 7.1 `POST /api/v1/skills/recall`

- **稳定性**：stable
- **请求 body**：
  ```json
  {
    "query": "string(1-500)",
    "topK": 3,
    "domain": "string?",
    "school": "string?"
  }
  ```
- **响应 200**：
  ```json
  {
    "items": [
      {
        "seniorId": "uuid",
        "score": 0.87,
        "text": "string(自然语言片段)",
        "tags": ["string"]
      }
    ]
  }
  ```
- **错误**：
  - `400 SKILL_VALIDATION_FAILED`
  - `503 SKILL_RECALL_TIMEOUT` / `SKILL_RECALL_DISABLED`
  - `401 AUTH_MISSING_USER_ID`

> Python 端负责打分、片段切片、tags；Java 端透传外壳。
> DTO 字段含义：
> - `score`：0.0~1.0 浮点
> - `text`：自然语言片段，**可能包含 markdown**
> - `tags`：分类器（事项 10）产出的标签

---

## 8. seniors（蒸馏与片段）

### 8.1 `POST /api/v1/seniors/:id/distill`

- **稳定性**：stable
- **请求**：无 body
- **响应 200**（**同步**，Sprint 0 决策）：
  ```json
  {
    "seniorId": "uuid",
    "fragments": [
      {
        "kind": "PERSONA | WORK | MEMORY | OTHER",
        "content": "string(自然语言)",
        "tags": ["string"]
      }
    ],
    "updatedAt": "2026-08-12T14:30:00Z"
  }
  ```
- **错误**：
  - `404 SENIOR_NOT_FOUND`
  - `503 SENIOR_DISTILL_TIMEOUT`
  - `401 AUTH_MISSING_USER_ID`

> 蒸馏失败时 `fragments` 为 `[]`，前端空态展示，不破坏响应。
> Sprint 0 选**同步**（黑工客松节奏 + 30s LLM timeout 可控）。
> 异步队列留给 v2 的多人并发蒸馏。
> `seniorId` 写回 `senior_fragments` 表。

### 8.2 `GET /api/v1/seniors/:id/fragments`

- **稳定性**：stable
- **Query**：`cursor` string?、`limit` int?（默认 20，最大 50）
- **响应 200**：
  ```json
  {
    "items": [
      {
        "id": "uuid",
        "seniorId": "uuid",
        "kind": "PERSONA | WORK | MEMORY | OTHER",
        "content": "string",
        "tags": ["string"],
        "createdAt": "2026-08-12T14:30:00Z"
      }
    ],
    "nextCursor": "string | null"
  }
  ```
- **错误**：
  - `404 SENIOR_NOT_FOUND`
  - `401 AUTH_MISSING_USER_ID`

> 现有 `/api/seniors*`（`/api/seniors`、`/api/seniors/:id`、`/api/seniors/upload`、`/api/seniors/:id/avatar`）保持原状，仅标 `Deprecation: true`，30 天内下架。

---

## 9. 横切

### 9.1 错误信封（重复见 §0.4）

### 9.2 `X-User-Id` 注入

- 前端 axios 拦截器：
  1. 请求前读 `localStorage.persist.userId`；缺失则 `crypto.randomUUID()` 生成并写入。
  2. 注入请求头 `X-User-Id: <uuid>`。
- 后端兜底：
  1. 读 `X-User-Id`，缺失或非 UUIDv4 → `401 AUTH_MISSING_USER_ID`。
  2. 否则按 user 上下文处理。

### 9.3 `X-Trace-Id` 注入

- 前端无需生成。
- 后端在缺省时填入 UUIDv4，写入 MDC、响应头、ErrorEnvelope。

### 9.4 CORS

- 维持现状 `WebConfig`：允许 `http://localhost:* / http://127.0.0.1:*`。
- 允许 `GET / POST / OPTIONS`。
- v1 上线后**不新增** `PUT / DELETE / PATCH` 的 CORS 头（评论删除用 `DELETE`，届时扩展）。

### 9.5 前端 fallback 策略

- `seniorService` / `communityService` / `likes` 保留离线 fallback（CLAUDE.md / AGENTS.md 强制）。
- `chatService` / `skills/recall` / `distill` **不带 fallback**（远程不可用就报 `CHAT_LLM_DEGRADED` / `SKILL_RECALL_TIMEOUT`）。

### 9.6 LLM 与 fallback

- 默认 `LLM_PROVIDER=mock`。
- 启用真实：`LLM_PROVIDER=deepseek` + `DEEPSEEK_*`。
- `LLM_FALLBACK_TO_MOCK` 控制失败 fallback。

---

## 10. 现有 `/api/*` → `/api/v1/*` 迁移表

| 现有 | v1 等价 | 备注 |
|---|---|---|
| `POST /api/chat` | `POST /api/v1/chat` | 字段相同 |
| `GET /api/seniors` | `GET /api/v1/seniors?domain=&school=` | Sprint 3 由 guan 写迁移；`senior_skills.major` 拆 `college`/`major` |
| `GET /api/seniors/:id` | 保留 + 加 `/api/v1/seniors/:id` | 7 件套详情保留 |
| `POST /api/seniors/upload` | 保留 + Sprint 3 写 v1 | |
| `GET /api/seniors/:id/avatar` | 保留 | |
| `GET /api/community/posts` | `GET /api/v1/posts` | 字段从 `authorName/authorAvatar` 自由文本改 `authorId` + 关联 |
| `POST /api/community/posts` | `POST /api/v1/posts` | 同上 |

> `/api/*` 全部加 `Deprecation: true` + `Sunset: <30 天后>` 响应头。
> 30 天到期下架；前端 service 的 fallback 切换开关在 Sprint 3 切。

---

## 11. AI 合并冲突优先级（已锁）

```text
docs/api-v1.md  >  docs/contract-changelog.md  >  已合并 PR  >  飞线 PR
```

具体规则：

1. 字段名以 `docs/api-v1.md` 为准；PR 字段若不一致 → 改 PR。
2. 错误码以 `docs/error-codes.md` 为准；新加 code 必须先在表里登记。
3. 时间戳一律 ISO-8601 UTC。
4. id 一律 UUIDv4。
5. `ChatRequest` / `ChatRequestV1` 保持 mutable class（CLAUDE.md 强制）。
6. LLM 默认 mock，DeepSeek 启用的 fallback 链不变。
7. 前端 `seniorService` / `communityService` 保留 fallback；`chatService` 不带 fallback。
8. CSS token `--paper / --surface / --ink / --pink` 等不要改。
