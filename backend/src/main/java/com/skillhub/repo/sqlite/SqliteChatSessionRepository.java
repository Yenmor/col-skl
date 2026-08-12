package com.skillhub.repo.sqlite;

import com.skillhub.model.ChatSession;
import com.skillhub.repo.ChatSessionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class SqliteChatSessionRepository implements ChatSessionRepository {

    private final JdbcTemplate jdbc;

    public SqliteChatSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ChatSession upsert(ChatSession s) {
        jdbc.update("""
            INSERT INTO chat_sessions (id, user_id, title, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              title=excluded.title, updated_at=excluded.updated_at
            """,
            s.id(), s.userId(), s.title(),
            s.createdAt().toString(), s.updatedAt().toString());
        return s;
    }

    @Override
    public Optional<ChatSession> findById(String id) {
        var rows = jdbc.query("SELECT * FROM chat_sessions WHERE id=?", (rs, n) -> map(rs), id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<ChatSession> listByUser(String userId, int limit) {
        return jdbc.query("""
            SELECT * FROM chat_sessions
            WHERE user_id=?
            ORDER BY updated_at DESC LIMIT ?
            """, (rs, n) -> map(rs), userId, limit);
    }

    @Override
    public boolean existsById(String id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM chat_sessions WHERE id=?", Integer.class, id);
        return count != null && count > 0;
    }

    private ChatSession map(ResultSet rs) throws SQLException {
        return new ChatSession(
            rs.getString("id"),
            rs.getString("user_id"),
            rs.getString("title"),
            java.time.Instant.parse(rs.getString("created_at")),
            java.time.Instant.parse(rs.getString("updated_at"))
        );
    }
}