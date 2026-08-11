package com.skillhub.repo.sqlite;

import com.skillhub.model.ChatMessageEntity;
import com.skillhub.repo.ChatRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class SqliteChatRepository implements ChatRepository {

    private final JdbcTemplate jdbc;

    public SqliteChatRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(ChatMessageEntity m) {
        jdbc.update("""
            INSERT INTO chat_messages (id,session_id,role,content,answers_json,created_at)
            VALUES (?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
              content=excluded.content, answers_json=excluded.answers_json
            """,
            m.id(), m.sessionId(), m.role(), m.content(), m.answersJson(),
            m.createdAt().toString());
    }

    @Override
    public List<ChatMessageEntity> recentBySession(String sessionId, int limit) {
        return jdbc.query("""
            SELECT * FROM chat_messages
            WHERE session_id=?
            ORDER BY created_at DESC LIMIT ?
            """,
            (rs, n) -> new ChatMessageEntity(
                rs.getString("id"),
                rs.getString("session_id"),
                rs.getString("role"),
                rs.getString("content"),
                rs.getString("answers_json"),
                java.time.Instant.parse(rs.getString("created_at"))
            ), sessionId, limit);
    }
}
