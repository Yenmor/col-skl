package com.skillhub.repo.sqlite;

import com.skillhub.model.Post;
import com.skillhub.repo.PostRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class SqlitePostRepository implements PostRepository {

    private final JdbcTemplate jdbc;

    public SqlitePostRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Post save(Post p) {
        jdbc.update("""
            INSERT INTO posts
              (id, title, body, excerpt, cover_color, author_id, author_name, author_avatar, domain, like_count, comment_count, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              title=excluded.title, body=excluded.body, excerpt=excluded.excerpt, domain=excluded.domain
            """,
            p.id(), p.title(), p.body(), p.excerpt(), p.coverColor(),
            p.authorId(), p.authorName(), p.authorAvatar(), p.domain(),
            p.likeCount(), p.commentCount(),
            p.createdAt().toString());
        return p;
    }

    @Override
    public Optional<Post> findById(String id) {
        var rows = jdbc.query("SELECT * FROM posts WHERE id=?", (rs, n) -> map(rs), id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Post> listAfter(String cursor, int limit, String authorId, String domain) {
        // domain 支持逗号分隔多值（四方向映射到多中文标签）
        StringBuilder sql = new StringBuilder("SELECT * FROM posts WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (authorId != null && !authorId.isBlank()) {
            sql.append(" AND author_id=?");
            args.add(authorId);
        }
        if (domain != null && !domain.isBlank()) {
            String[] parts = domain.split(",");
            sql.append(" AND domain IN (");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sql.append(",");
                sql.append("?");
                args.add(parts[i].trim());
            }
            sql.append(")");
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), (rs, n) -> map(rs), args.toArray());
    }

    /**
     * 幂等 toggle：在事务里 INSERT/DELETE post_likes，同步更新 posts.like_count。
     */
    @Override
    @Transactional
    public long[] likeToggle(String userId, String postId) {
        // 查 post 是否存在
        Integer postCount = jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE id=?", Integer.class, postId);
        if (postCount == null || postCount == 0) {
            return new long[]{0, 0};
        }
        // 是否已点赞
        Integer likeCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM post_likes WHERE user_id=? AND post_id=?",
            Integer.class, userId, postId);
        boolean liked;
        if (likeCount != null && likeCount > 0) {
            jdbc.update("DELETE FROM post_likes WHERE user_id=? AND post_id=?", userId, postId);
            jdbc.update("UPDATE posts SET like_count=like_count-1 WHERE id=? AND like_count>0", postId);
            liked = false;
        } else {
            jdbc.update("INSERT INTO post_likes (user_id, post_id, created_at) VALUES (?, ?, ?)",
                userId, postId, java.time.Instant.now().toString());
            jdbc.update("UPDATE posts SET like_count=like_count+1 WHERE id=?", postId);
            liked = true;
        }
        Long newCount = jdbc.queryForObject("SELECT like_count FROM posts WHERE id=?", Long.class, postId);
        return new long[]{newCount == null ? 0 : newCount, liked ? 1 : 0};
    }

    private Post map(ResultSet rs) throws SQLException {
        return new Post(
            rs.getString("id"),
            rs.getString("title"),
            rs.getString("excerpt"),
            rs.getString("body"),
            rs.getString("cover_color"),
            rs.getString("author_id"),
            rs.getString("author_name"),
            rs.getString("author_avatar"),
            rs.getString("domain"),
            rs.getLong("like_count"),
            rs.getLong("comment_count"),
            java.time.Instant.parse(rs.getString("created_at"))
        );
    }
}