package com.skillhub.repo.sqlite;

import com.skillhub.model.CommunityPost;
import com.skillhub.repo.CommunityRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class SqliteCommunityRepository implements CommunityRepository {

    private final JdbcTemplate jdbc;

    public SqliteCommunityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CommunityPost save(CommunityPost p) {
        jdbc.update("""
            INSERT INTO community_posts
              (id,author_name,author_avatar,title,excerpt,body,cover_color,like_count,comment_count,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
              title=excluded.title, excerpt=excluded.excerpt, body=excluded.body
            """,
            p.id(), p.authorName(), p.authorAvatar(), p.title(), p.excerpt(),
            p.body(), p.coverColor(), p.likeCount(), p.commentCount(),
            p.createdAt().toString());
        return p;
    }

    @Override
    public Optional<CommunityPost> findById(String id) {
        var rows = jdbc.query("SELECT * FROM community_posts WHERE id=?",
            (rs, n) -> map(rs), id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<CommunityPost> recent(int limit) {
        return jdbc.query("""
            SELECT * FROM community_posts
            ORDER BY created_at DESC LIMIT ?
            """, (rs, n) -> map(rs), limit);
    }

    private CommunityPost map(ResultSet rs) throws SQLException {
        return new CommunityPost(
            rs.getString("id"),
            rs.getString("author_name"),
            rs.getString("author_avatar"),
            rs.getString("title"),
            rs.getString("excerpt"),
            rs.getString("body"),
            rs.getString("cover_color"),
            rs.getLong("like_count"),
            rs.getLong("comment_count"),
            java.time.Instant.parse(rs.getString("created_at"))
        );
    }
}
