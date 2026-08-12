package com.skillhub.repo.sqlite;

import com.skillhub.model.ChatMemory;
import com.skillhub.repo.ChatMemoryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class SqliteChatMemoryRepository implements ChatMemoryRepository {

    private final JdbcTemplate jdbc;

    public SqliteChatMemoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ChatMemory save(ChatMemory m) {
        jdbc.update("""
            INSERT INTO chat_memories (id, session_id, user_id, title, tags_json, content_json, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            m.id(), m.sessionId(), m.userId(), m.title(),
            m.tagsJson(), m.contentJson(), m.createdAt().toString());
        return m;
    }

    @Override
    public List<ChatMemory> listByUser(String userId, int limit) {
        return jdbc.query("""
            SELECT * FROM chat_memories
            WHERE user_id=?
            ORDER BY created_at DESC LIMIT ?
            """, (rs, n) -> map(rs), userId, limit);
    }

    private ChatMemory map(ResultSet rs) throws SQLException {
        return new ChatMemory(
            rs.getString("id"),
            rs.getString("session_id"),
            rs.getString("user_id"),
            rs.getString("title"),
            rs.getString("tags_json"),
            rs.getString("content_json"),
            java.time.Instant.parse(rs.getString("created_at"))
        );
    }
}