package com.skillhub.repo.sqlite;

import com.skillhub.model.Comment;
import com.skillhub.repo.CommentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class SqliteCommentRepository implements CommentRepository {

    private final JdbcTemplate jdbc;

    public SqliteCommentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Comment save(Comment c) {
        jdbc.update("""
            INSERT INTO post_comments (id, post_id, author_id, author_name, author_avatar, parent_id, body, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            c.id(), c.postId(), c.authorId(), c.authorName(), c.authorAvatar(), c.parentId(), c.body(),
            c.createdAt().toString());
        jdbc.update("UPDATE posts SET comment_count=comment_count+1 WHERE id=?", c.postId());
        return c;
    }

    @Override
    public Optional<Comment> findById(String id) {
        var rows = jdbc.query("SELECT * FROM post_comments WHERE id=?", (rs, n) -> map(rs), id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Comment> listByPost(String postId, int limit) {
        return jdbc.query("""
            SELECT * FROM post_comments
            WHERE post_id=?
            ORDER BY created_at ASC LIMIT ?
            """, (rs, n) -> map(rs), postId, limit);
    }

    @Override
    @Transactional
    public boolean deleteById(String id) {
        // 先查 post_id 用于扣 comment_count
        Optional<Comment> c = findById(id);
        if (c.isEmpty()) return false;
        int n = jdbc.update("DELETE FROM post_comments WHERE id=?", id);
        if (n > 0) {
            jdbc.update("UPDATE posts SET comment_count=MAX(comment_count-1,0) WHERE id=?",
                c.get().postId());
        }
        return n > 0;
    }

    private Comment map(ResultSet rs) throws SQLException {
        return new Comment(
            rs.getString("id"),
            rs.getString("post_id"),
            rs.getString("author_id"),
            rs.getString("author_name"),
            rs.getString("author_avatar"),
            rs.getString("parent_id"),
            rs.getString("body"),
            java.time.Instant.parse(rs.getString("created_at"))
        );
    }
}