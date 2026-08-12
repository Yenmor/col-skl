package com.skillhub.repo.sqlite;

import com.skillhub.model.User;
import com.skillhub.repo.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
public class SqliteUserRepository implements UserRepository {

    private final JdbcTemplate jdbc;

    public SqliteUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public User save(User u) {
        jdbc.update("""
            INSERT INTO users (id, display_name, avatar_url, role, created_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              display_name=excluded.display_name,
              avatar_url=excluded.avatar_url
            """,
            u.id(), u.displayName(), u.avatarUrl(), u.role(), u.createdAt().toString());
        return u;
    }

    @Override
    public Optional<User> findById(String id) {
        var rows = jdbc.query("SELECT * FROM users WHERE id=?", (rs, n) -> map(rs), id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public boolean existsById(String id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=?", Integer.class, id);
        return count != null && count > 0;
    }

    private User map(ResultSet rs) throws SQLException {
        return new User(
            rs.getString("id"),
            rs.getString("display_name"),
            rs.getString("avatar_url"),
            rs.getString("role"),
            java.time.Instant.parse(rs.getString("created_at"))
        );
    }
}