# Skill Hub v1 — 架构决策记录（ADR）

> Sprint 0 锁定的 12 个决策。
> 任何与此处冲突的 PR/代码，**均以本文档为准**。

---

## D1 — 鉴权方式：匿名 cookie UUID

- **状态**：已锁定
- **上下文**：MVP 不做真实登录；前端需要稳定的 userId 用于点赞 / 评论 / 记忆。
- **选项**：
  - A. 完全匿名（cookie / localStorage UUID） ← **采纳**
  - B. 强制登录（OAuth / SMS）
  - C. 双轨（匿名可发帖但限流）
- **结论**：A。前端首次启动由 axios 拦截器 `crypto.randomUUID()` 写入 `localStorage.persist.userId`，请求时注入 `X-User-Id`。
- **影响接口**：`/users/me`, 点赞, 评论, 记忆
- **风险**：`localStorage` 跨设备不同步；MVP 不做云端用户表。

---

## D2 — id 策略：UUIDv4

- **状态**：已锁定
- **上下文**：跨模块合并时，int 自增会冲突。
- **选项**：
  - UUIDv4 ← **采纳**
  - ULID
  - Snowflake
  - 自增 int
- **结论**：UUIDv4，与现状 `UUID.randomUUID()` 完全一致。
- **影响接口**：全部资源。

---

## D3 — 点赞计数：应用层事务 + 冗余字段

- **状态**：已锁定
- **上下文**：计数性能 vs 数据一致性。
- **选项**：
  - 应用层事务 + `post_likes` 事实表 + `posts.like_count` 冗余 ← **采纳**
  - SQLite / MySQL 触发器
  - 不冗余（每次实时 `COUNT(*)`）
- **结论**：方案 A。切 MySQL 时一致，不依赖方言特性。
- **影响接口**：`/posts/:id/like` 计数维护。

---

## D4 — ABI 兼容：provisional → stable

- **状态**：已锁定
- **上下文**：现有 `/api/*` 已有前端依赖。
- **选项**：
  - 不分版本，强推 v1（破坏性） ← 否
  - 现有 `/api/*` 标 provisional，新增走 `/api/v1/*` ← **采纳**
- **结论**：方案 B。`/api/*` 全部加 `Deprecation: true` + `Sunset: <30 天后>`，30 天内下架。
- **影响接口**：全部。

---

## D5 — chat sessionId：服务端生成

- **状态**：已锁定
- **上下文**：客户端生成 vs 服务端生成。
- **选项**：
  - 客户端生成
  - 服务端生成，首响应返回 ← **采纳**
- **结论**：方案 B。客户端首请求不传 `sessionId`，服务端补 UUID；后续客户端回带。
- **影响接口**：`/api/v1/chat`。

---

## D6 — Skill 召回：公开

- **状态**：已锁定
- **上下文**：召回是 chat 内部依赖，但前端"换一批学长"按钮需要直接调。
- **选项**：
  - 仅 chat 内部 RPC
  - 也开 `/api/v1/skills/recall` ← **采纳**
- **结论**：方案 B。`/api/v1/chat` 内部直接调同进程的 `SkillRecallService`（**不 HTTP 自调**），公开 endpoint 给前端复用。
- **影响接口**：`/api/v1/skills/recall`。

---

## D7 — 蒸馏片段存储：DB

- **状态**：已锁定
- **上下文**：MVP 是否上向量库。
- **选项**：
  - DB（`senior_fragments` 表） ← **采纳**
  - 文件系统 JSON
  - 向量库（Chroma / Milvus / Qdrant）
- **结论**：方案 A。Sprint 0 写 `senior_fragments` 表 schema 注释（`db/migration-v1.sql`），**不执行**。向量库 v2。
- **影响接口**：`/api/v1/seniors/:id/distill`, `/api/v1/seniors/:id/fragments`。

---

## D8 — 评论树：保留 `parentId`，UI 展平一层

- **状态**：已锁定
- **上下文**：楼中楼复杂度。
- **选项**：
  - 一层（无 `parentId`）
  - 多层 `parentId`，UI 展平一层 ← **采纳**
- **结论**：方案 B。DB 保留 `parentId` 字段，UI v1 只渲染一层回复。楼中楼 UI v2。
- **影响接口**：`/api/v1/posts/:postId/comments`。

---

## D9 — 错误 code 命名：资源前缀

- **状态**：已锁定
- **上下文**：错误码可读性 vs 简短。
- **选项**：
  - 全局平铺（`NOT_FOUND`）
  - 按资源前缀（`POST_NOT_FOUND`）← **采纳**
- **结论**：方案 B。Java 端单一 `dto/ErrorCode` 公共枚举（方案一，已锁）。
- **影响接口**：所有 4xx/5xx。

---

## D10 — 限流 / 防刷：MVP 不做

- **状态**：已锁定
- **上下文**：黑客松节奏。
- **选项**：
  - MVP 不做，代码留 TODO ← **采纳**
  - 必做
- **结论**：方案 A。
- **影响接口**：全局。

---

## D11 — deprecated 灰度：30 天

- **状态**：已锁定
- **上下文**：现有 `/api/*` 路径如何退出。
- **选项**：
  - 0 / 7 / 30 / 90 天
- **结论**：**30 天**。黑客松节奏匹配 30 天灰度期。`Deprecation: true` + `Sunset: <RFC-7231-date>`。
- **影响接口**：`/api/chat`, `/api/seniors`, `/api/community` 全部。

---

## D12 — chat 与 memories 关系：独立资源

- **状态**：已锁定
- **上下文**：会话记忆是 chat 子资源，但需要"我的记忆"复用。
- **选项**：
  - 独立资源（`/api/v1/chat/sessions/:id/memories`）← **采纳**
  - 同一资源嵌套
- **结论**：方案 A。便于将来 `/api/v1/users/me/memories` 复用。
- **影响接口**：`/api/v1/chat/sessions/:id/memories`, `/api/v1/users/me/memories`。

---

## 额外决策（你授权我全权拍板）

### D13 — Java 调用 Python 服务的形态

- **状态**：已锁定（Sprint 0）
- **结论**：**形态 A — Java 内部先 mock，后期切形态 B**（跨 HTTP WebClient）。
- **理由**：黑客松节奏下 Python 服务不一定先起；Sprint 0 仅定契约，形态 A 不阻塞任何人；Sprint 3 联调时再决定是否切形态 B。

### D14 — `distill` 同步 vs 异步

- **状态**：已锁定（你授权我全权）
- **结论**：**同步**。
- **理由**：黑客松节奏下客户端无耐心轮询；LLM 30s timeout 内可控；失败时 `fragments=[]` 不破坏响应；异步队列留给 v2 的多人并发蒸馏。

### D15 — `localStorage.persist.userId` 缺失处理

- **状态**：已锁定（你已确认）
- **结论**：**自动生成 UUID 并写入**。与 D1 匿名 cookie UUID 一致。

### D16 — 自然语言产物接口的契约

- **状态**：已锁定（你已确认）
- **结论**：**最小外壳**。`skills/recall` 外壳 `seniorId / score / text / tags`；`distill` 外壳 `kind / content / tags`。复杂结构由 Python 端负责，Java 端透传 `JsonNode`。

### D17 — `fragments.kind` 枚举

- **状态**：已锁定
- **结论**：`PERSONA / WORK / MEMORY / OTHER`（4 个枚举值）。

### D18 — AI 合并冲突优先级

- **状态**：已锁定（你已确认）
- **结论**：`docs/api-v1.md` > `docs/contract-changelog.md` > 已合并 PR > 飞线 PR。

---

## 变更记录

- 2026-08-12：Sprint 0 起草 12 + 6 个决策（you + AI 共同拍板）。
- 后续变更追加在 `docs/contract-changelog.md`。
