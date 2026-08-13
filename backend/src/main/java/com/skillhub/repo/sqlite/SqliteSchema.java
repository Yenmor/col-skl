package com.skillhub.repo.sqlite;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时建表 — 仅维护 DDL。换 mysql 时换此处的 SQL。
 *
 * <p>本类包含 v0（旧 3 表）+ v1（8 张新表）全部 DDL。
 * 全部 {@code CREATE TABLE IF NOT EXISTS}，可重复启动。
 *
 * <p>v1 表见 {@code docs/api-v1.md} 与 {@code db/migration-v1.sql}。
 */
@Component
public class SqliteSchema {
    private final JdbcTemplate jdbc;

    public SqliteSchema(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        // ----- v0 表（保留兼容） -----
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS senior_skills (
              id TEXT PRIMARY KEY,
              name TEXT NOT NULL,
              school TEXT,
              major TEXT,
              year TEXT,
              domain TEXT,
              avatar_filename TEXT,
              source TEXT,
              created_at TEXT
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS community_posts (
              id TEXT PRIMARY KEY,
              author_id TEXT,
              author_name TEXT,
              author_avatar TEXT,
              title TEXT,
              excerpt TEXT,
              body TEXT,
              cover_color TEXT,
              like_count INTEGER DEFAULT 0,
              comment_count INTEGER DEFAULT 0,
              domain TEXT,
              created_at TEXT
            )
        """);
        // v1 兼容：老列 ALTER 不会重复执行；用 try/catch 兜底
        ensureColumn("community_posts", "author_id", "TEXT");
        ensureColumn("community_posts", "domain", "TEXT");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS chat_messages (
              id TEXT PRIMARY KEY,
              session_id TEXT,
              role TEXT,
              content TEXT,
              answers_json TEXT,
              created_at TEXT
            )
        """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_chat_session_created ON chat_messages(session_id, created_at DESC)");

        // Skill v1 metadata. Existing rows stay public and keep their original timestamps.
        ensureColumn("senior_skills", "owner_id", "TEXT");
        ensureColumn("senior_skills", "visibility", "TEXT NOT NULL DEFAULT 'PUBLIC'");
        ensureColumn("senior_skills", "layer_id", "TEXT");
        ensureColumn("senior_skills", "summary", "TEXT");
        ensureColumn("senior_skills", "version", "TEXT NOT NULL DEFAULT 'v1'");
        ensureColumn("senior_skills", "tags_json", "TEXT NOT NULL DEFAULT '[]'");
        ensureColumn("senior_skills", "updated_at", "TEXT");
        jdbc.update("UPDATE senior_skills SET visibility='PUBLIC' WHERE visibility IS NULL OR TRIM(visibility)='' ");
        jdbc.update("UPDATE senior_skills SET version='v1' WHERE version IS NULL OR TRIM(version)='' ");
        jdbc.update("UPDATE senior_skills SET tags_json='[]' WHERE tags_json IS NULL OR TRIM(tags_json)='' ");
        jdbc.update("UPDATE senior_skills SET updated_at=created_at WHERE updated_at IS NULL OR TRIM(updated_at)='' ");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_skills_visibility_updated ON senior_skills(visibility, updated_at DESC)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_skills_owner_updated ON senior_skills(owner_id, updated_at DESC)");

        // Explainable Skill trust evidence. Keep source confirmation, platform checks,
        // and community adoption as separate facts so popularity cannot impersonate verification.
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS skill_trust_facts (
              skill_id TEXT PRIMARY KEY,
              source_confirmed INTEGER NOT NULL DEFAULT 0,
              source_authorized INTEGER NOT NULL DEFAULT 0,
              source_note TEXT,
              authority_channels_json TEXT NOT NULL DEFAULT '[]',
              ai_score INTEGER,
              ai_model TEXT,
              ai_note TEXT,
              demo INTEGER NOT NULL DEFAULT 0,
              updated_at TEXT NOT NULL
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS skill_likes (
              user_id TEXT NOT NULL,
              skill_id TEXT NOT NULL,
              created_at TEXT NOT NULL,
              PRIMARY KEY (user_id, skill_id)
            )
        """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_skill_likes_skill ON skill_likes(skill_id)");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS skill_download_events (
              id TEXT PRIMARY KEY,
              skill_id TEXT NOT NULL,
              user_id TEXT,
              created_at TEXT NOT NULL
            )
        """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_skill_downloads_skill ON skill_download_events(skill_id, created_at DESC)");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS skill_comments (
              id TEXT PRIMARY KEY,
              skill_id TEXT NOT NULL,
              user_id TEXT,
              body TEXT NOT NULL,
              created_at TEXT NOT NULL
            )
        """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_skill_comments_skill ON skill_comments(skill_id, created_at DESC)");

        // ----- v1 表 -----

        // users（D1 匿名 cookie UUID）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS users (
              id TEXT PRIMARY KEY,
              display_name TEXT NOT NULL,
              avatar_url TEXT,
              role TEXT NOT NULL DEFAULT 'GUEST',
              created_at TEXT NOT NULL
            )
        """);

        // posts（v1 主帖表）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS posts (
              id TEXT PRIMARY KEY,
              title TEXT NOT NULL,
              body TEXT NOT NULL,
              excerpt TEXT NOT NULL,
              cover_color TEXT NOT NULL,
              author_id TEXT,
              author_name TEXT,
              author_avatar TEXT,
              domain TEXT,
              like_count INTEGER NOT NULL DEFAULT 0,
              comment_count INTEGER NOT NULL DEFAULT 0,
              created_at TEXT NOT NULL
            )
        """);
        ensureColumn("posts", "author_name", "TEXT");
        ensureColumn("posts", "author_avatar", "TEXT");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_posts_created ON posts(created_at DESC)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_posts_author  ON posts(author_id, created_at DESC)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_posts_domain  ON posts(domain, created_at DESC)");

        // post_comments（D8 保留 parentId）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS post_comments (
              id TEXT PRIMARY KEY,
              post_id TEXT NOT NULL,
              author_id TEXT,
              author_name TEXT,
              author_avatar TEXT,
              parent_id TEXT,
              body TEXT NOT NULL,
              created_at TEXT NOT NULL
            )
        """);
        ensureColumn("post_comments", "author_name", "TEXT");
        ensureColumn("post_comments", "author_avatar", "TEXT");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_comments_post   ON post_comments(post_id, created_at DESC)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_comments_parent ON post_comments(parent_id)");

        // post_likes（D3 事实表）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS post_likes (
              user_id TEXT NOT NULL,
              post_id TEXT NOT NULL,
              created_at TEXT NOT NULL,
              PRIMARY KEY (user_id, post_id)
            )
        """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_post_likes_post ON post_likes(post_id)");

        // chat_sessions
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS chat_sessions (
              id TEXT PRIMARY KEY,
              user_id TEXT,
              title TEXT,
              created_at TEXT NOT NULL,
              updated_at TEXT NOT NULL
            )
        """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_sessions_user_updated ON chat_sessions(user_id, updated_at DESC)");

        // chat_memories
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS chat_memories (
              id TEXT PRIMARY KEY,
              session_id TEXT NOT NULL,
              user_id TEXT,
              title TEXT,
              tags_json TEXT NOT NULL DEFAULT '[]',
              content_json TEXT NOT NULL,
              created_at TEXT NOT NULL
            )
        """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_memories_user ON chat_memories(user_id, created_at DESC)");

        // senior_distill_jobs（v2 占位）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS senior_distill_jobs (
              id TEXT PRIMARY KEY,
              senior_id TEXT NOT NULL,
              status TEXT NOT NULL,
              started_at TEXT NOT NULL,
              finished_at TEXT,
              error_message TEXT
            )
        """);

        // senior_fragments（D7 蒸馏片段）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS senior_fragments (
              id TEXT PRIMARY KEY,
              senior_id TEXT NOT NULL,
              kind TEXT NOT NULL,
              content TEXT NOT NULL,
              tags_json TEXT NOT NULL DEFAULT '[]',
              created_at TEXT NOT NULL
            )
        """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_fragments_senior ON senior_fragments(senior_id, created_at DESC)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_fragments_kind   ON senior_fragments(senior_id, kind)");
    }

    /**
     * 给已有表加列；重复添加抛错时静默忽略（IF NOT EXISTS 在 SQLite 12 才支持，旧版本用 catch 兜底）。
     */
    private void ensureColumn(String table, String column, String typeDef) {
        try {
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + typeDef);
        } catch (Exception ignored) {
            // 列已存在；忽略
        }
    }
}
