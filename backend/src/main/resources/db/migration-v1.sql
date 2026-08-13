-- =============================================================================
-- Skill Hub v1 — SQL 迁移规范（注释版，**不执行**）
-- =============================================================================
--
-- 本文件是 v1 阶段新增表 / 字段的 *规范*，由 Sprint 2 / Sprint 3 的实施人
-- （`@guan`）按需拷到 `repo/sqlite/SqliteSchema.java` 中执行。
--
-- 触发时机：
--   Sprint 2 — `users` / `post_comments` / `post_likes` / `chat_sessions` /
--              `chat_memories` 上线，与 v1 controller 同步生效。
--   Sprint 3 — `senior_distill_jobs` / `senior_fragments` 上线（与 distill
--              controller 同步）。
--
-- 不在本文件执行：
--   - `senior_skills.major` 拆 `college` / `major`（v1 不动；Sprint 3 由
--     列表层做拆分，不动 DB）。
--   - 现有 `community_posts` 表字段调整（保留 `authorName` 自由文本；
--     `author_id` 留 nullable 字段供新写入使用）。
--
-- SQLite 约束：
--   - 主键一律 TEXT（UUIDv4 字符串）。
--   - 时间戳一律 TEXT，存 `Instant.toString()`（ISO-8601 UTC）。
--   - 不使用 SQLite 触发器（D3：应用层事务维护冗余字段）。
--
-- MySQL 切方言时（D3 已锁）：与 `SqliteDialect` / `MySqlDialect` 抽象层
-- 配合，DDL 改写为 MySQL 方言，迁移脚本另行管理。
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. users
-- -----------------------------------------------------------------------------
-- MVP：匿名 cookie UUID（D1）。前端首次启动 `crypto.randomUUID()` 写入
-- `localStorage.persist.userId`；后端在缺省时落库（lazy create）。
--
CREATE TABLE IF NOT EXISTS users (
    id            TEXT PRIMARY KEY,           -- UUIDv4
    display_name  TEXT NOT NULL,              -- 1-24 字符
    avatar_url    TEXT,                       -- 可空
    role          TEXT NOT NULL DEFAULT 'GUEST',
    created_at    TEXT NOT NULL               -- Instant.toString()
);

CREATE INDEX IF NOT EXISTS idx_users_created ON users(created_at DESC);


-- -----------------------------------------------------------------------------
-- 2. posts（v1 新表，不动 `community_posts`）
-- -----------------------------------------------------------------------------
-- 决策：
--   - D1 匿名用户 → `author_id` FK → users.id。
--   - 老 `community_posts` 行保留；前端读 `posts` 时若无 `author_id`，
--     回退用 `author_name`。
--   - 30 天灰度期双表并行；Sprint 3 由 guan 写迁移动作。
--
CREATE TABLE IF NOT EXISTS posts (
    id            TEXT PRIMARY KEY,
    title         TEXT NOT NULL,              -- 1-80 字
    body          TEXT NOT NULL,              -- 1-20000 字
    excerpt       TEXT NOT NULL,              -- 后端自动截前 200 字
    cover_color   TEXT NOT NULL,              -- hex
    author_id     TEXT,                       -- FK → users.id；nullable（老数据）
    domain        TEXT,                       -- 领域
    like_count    INTEGER NOT NULL DEFAULT 0,
    comment_count INTEGER NOT NULL DEFAULT 0,
    created_at    TEXT NOT NULL,
    FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_posts_created ON posts(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_author  ON posts(author_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_domain  ON posts(domain, created_at DESC);


-- -----------------------------------------------------------------------------
-- 3. post_comments（D8：保留 parentId，UI v1 展平）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS post_comments (
    id            TEXT PRIMARY KEY,
    post_id       TEXT NOT NULL,
    author_id     TEXT,                       -- FK → users.id；nullable
    parent_id     TEXT,                       -- FK → post_comments.id；nullable
    body          TEXT NOT NULL,              -- 1-2000 字
    created_at    TEXT NOT NULL,
    FOREIGN KEY (post_id)   REFERENCES posts(id)         ON DELETE CASCADE,
    FOREIGN KEY (author_id) REFERENCES users(id)         ON DELETE SET NULL,
    FOREIGN KEY (parent_id) REFERENCES post_comments(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_comments_post    ON post_comments(post_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_comments_parent  ON post_comments(parent_id);


-- -----------------------------------------------------------------------------
-- 4. post_likes（D3：事实表 + posts.like_count 冗余）
-- -----------------------------------------------------------------------------
-- 复合主键保证幂等；触发点赞 = INSERT，触发取消 = DELETE。
-- 维护 posts.like_count 由应用层事务同步。
--
CREATE TABLE IF NOT EXISTS post_likes (
    user_id       TEXT NOT NULL,
    post_id       TEXT NOT NULL,
    created_at    TEXT NOT NULL,
    PRIMARY KEY (user_id, post_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_post_likes_post ON post_likes(post_id);


-- -----------------------------------------------------------------------------
-- 5. chat_sessions（v1 新表；现有 `chat_messages` 行映射为虚拟 session）
-- -----------------------------------------------------------------------------
-- 决策：v1 不强迁 `chat_messages`；`/api/v1/chat/sessions/:id/messages` 优先
-- 读 `chat_sessions` + `chat_messages`，未注册的 sessionId 直接 404。
--
CREATE TABLE IF NOT EXISTS chat_sessions (
    id            TEXT PRIMARY KEY,           -- UUIDv4
    user_id       TEXT,                       -- FK → users.id；nullable
    title         TEXT,                       -- 自动生成
    created_at    TEXT NOT NULL,
    updated_at    TEXT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_sessions_user_updated ON chat_sessions(user_id, updated_at DESC);


-- -----------------------------------------------------------------------------
-- 6. chat_memories（事项 8-3：会话 → 记忆）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_memories (
    id            TEXT PRIMARY KEY,
    session_id    TEXT NOT NULL,
    user_id       TEXT,                       -- FK → users.id；冗余便于瀑布
    title         TEXT,
    tags_json     TEXT NOT NULL DEFAULT '[]', -- JSON 数组
    content_json  TEXT NOT NULL,              -- 蒸馏出的对话片段（透传）
    created_at    TEXT NOT NULL,
    FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id)    REFERENCES users(id)         ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_memories_user ON chat_memories(user_id, created_at DESC);


-- -----------------------------------------------------------------------------
-- 7. senior_distill_jobs（D14：当前 v1 同步，**表保留以备 v2**）
-- -----------------------------------------------------------------------------
-- v1 同步返回，**不写入**此表。Sprint 2+ 由 guan 视情况启用。
-- 留表目的：避免 Sprint 3 重做 DDL。
--
CREATE TABLE IF NOT EXISTS senior_distill_jobs (
    id            TEXT PRIMARY KEY,
    senior_id     TEXT NOT NULL,
    status        TEXT NOT NULL,              -- QUEUED / RUNNING / DONE / FAILED
    started_at    TEXT NOT NULL,
    finished_at   TEXT,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_distill_jobs_senior ON senior_distill_jobs(senior_id, started_at DESC);


-- -----------------------------------------------------------------------------
-- 8. senior_fragments（D7：DB 落库；Python 端写入）
-- -----------------------------------------------------------------------------
-- 对应 DTO `SeniorFragmentDto`。
-- `kind` 枚举 PERSONA / WORK / MEMORY / OTHER。
-- `tags_json` 存 JSON 数组。
-- `content` 自然语言，可能含 markdown。
--
CREATE TABLE IF NOT EXISTS senior_fragments (
    id            TEXT PRIMARY KEY,
    senior_id     TEXT NOT NULL,
    kind          TEXT NOT NULL,              -- PERSONA / WORK / MEMORY / OTHER
    content       TEXT NOT NULL,
    tags_json     TEXT NOT NULL DEFAULT '[]',
    created_at    TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_fragments_senior ON senior_fragments(senior_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_fragments_kind   ON senior_fragments(senior_id, kind);


-- -----------------------------------------------------------------------------
-- 9. community_posts（existing）— 不动 schema；Sprint 3 切 v1 时由 guan 写迁移动作
-- -----------------------------------------------------------------------------
-- 保留：id / author_name / author_avatar / title / excerpt / body / cover_color /
--       like_count / comment_count / created_at
-- 不在 v1 强迁：author_id 字段新增（nullable 兼容老行）。
-- ALTER TABLE community_posts ADD COLUMN author_id TEXT;   -- nullable


-- -----------------------------------------------------------------------------
-- 10. chat_messages（existing）— 现有 schema 增加索引
-- -----------------------------------------------------------------------------
-- 现有 schema 已含 session_id；本文件加索引以支持 cursor 分页。
--
-- CREATE INDEX IF NOT EXISTS idx_chat_session_created
--     ON chat_messages(session_id, created_at DESC);


-- =============================================================================
-- End of migration-v1.sql
-- =============================================================================

-- senior_skills additive v1 metadata (executed defensively by SqliteSchema):
-- ALTER TABLE senior_skills ADD COLUMN owner_id TEXT;
-- ALTER TABLE senior_skills ADD COLUMN visibility TEXT NOT NULL DEFAULT 'PUBLIC';
-- ALTER TABLE senior_skills ADD COLUMN layer_id TEXT;
-- ALTER TABLE senior_skills ADD COLUMN summary TEXT;
-- ALTER TABLE senior_skills ADD COLUMN version TEXT NOT NULL DEFAULT 'v1';
-- ALTER TABLE senior_skills ADD COLUMN tags_json TEXT NOT NULL DEFAULT '[]';
-- ALTER TABLE senior_skills ADD COLUMN updated_at TEXT;
