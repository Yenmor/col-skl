package com.skillhub.repo.sqlite;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时建表 — 仅维护 DDL。换 mysql 时换此处的 SQL。
 */
@Component
public class SqliteSchema {
    private final JdbcTemplate jdbc;

    public SqliteSchema(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
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
              author_name TEXT,
              author_avatar TEXT,
              title TEXT,
              excerpt TEXT,
              body TEXT,
              cover_color TEXT,
              like_count INTEGER DEFAULT 0,
              comment_count INTEGER DEFAULT 0,
              created_at TEXT
            )
        """);
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
    }
}
