package com.skillhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * 社区示例数据（贴吧导入的 173 帖 + 7460 评论）首次启动自动迁移。
 *
 * <p>由 {@code backend/scripts/export-community.py} 导出为
 * {@code seed/community-posts.json}（classpath 资源，随代码入库）。
 *
 * <p>规则：
 * <ul>
 *   <li>开关 {@code skillhub.seed-community.enabled}（环境变量 {@code SKILLHUB_SEED_COMMUNITY}，默认 true）。</li>
 *   <li>仅在 db 中没有非演示帖子时导入（首次启动）；之后启动短路跳过。</li>
 *   <li>用原生 {@code INSERT OR IGNORE} 按 id 幂等去重，不走 repository.save，
 *       避免 {@code post_comments} 的 comment_count 自增副作用；计数直接采用 JSON 里的原值。</li>
 * </ul>
 */
@Component
public class CommunitySeedService {

    private static final String SEED_RESOURCE = "seed/community-posts.json";
    private static final int BATCH = 500;

    private final boolean enabled;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public CommunitySeedService(@Value("${skillhub.seed-community.enabled:true}") boolean enabled,
                                JdbcTemplate jdbc) {
        this.enabled = enabled;
        this.jdbc = jdbc;
    }

    public void seed() {
        if (!enabled) return;
        try {
            Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM posts WHERE id NOT LIKE 'demo-%' AND id NOT LIKE 'peer-%'",
                Integer.class);
            if (existing != null && existing > 0) return;

            ClassPathResource res = new ClassPathResource(SEED_RESOURCE);
            if (!res.exists()) return;
            Map<?, ?> root;
            try (InputStream in = res.getInputStream()) {
                root = json.readValue(in, Map.class);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> posts = (List<Map<String, Object>>) root.get("posts");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> comments = (List<Map<String, Object>>) root.get("comments");
            if (posts == null || comments == null || posts.isEmpty()) return;

            insertPosts(posts);
            insertComments(comments);
            System.out.println("[seed] imported community posts=" + posts.size()
                + " comments=" + comments.size());
        } catch (Exception e) {
            System.err.println("[seed] community data import skipped: " + e.getMessage());
        }
    }

    private void insertPosts(List<Map<String, Object>> posts) {
        for (int i = 0; i < posts.size(); i += BATCH) {
            final List<Map<String, Object>> chunk = posts.subList(i, Math.min(i + BATCH, posts.size()));
            jdbc.batchUpdate("""
                INSERT OR IGNORE INTO posts
                  (id, title, body, excerpt, cover_color, author_id, author_name, author_avatar,
                   domain, like_count, comment_count, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int n) throws SQLException {
                    Map<String, Object> p = chunk.get(n);
                    ps.setString(1, str(p.get("id")));
                    ps.setString(2, str(p.get("title")));
                    ps.setString(3, str(p.get("body")));
                    ps.setString(4, str(p.get("excerpt")));
                    ps.setString(5, str(p.get("cover_color")));
                    ps.setString(6, str(p.get("author_id")));
                    ps.setString(7, str(p.get("author_name")));
                    ps.setString(8, str(p.get("author_avatar")));
                    ps.setString(9, str(p.get("domain")));
                    ps.setLong(10, num(p.get("like_count")));
                    ps.setLong(11, num(p.get("comment_count")));
                    ps.setString(12, str(p.get("created_at")));
                }

                @Override
                public int getBatchSize() {
                    return chunk.size();
                }
            });
        }
    }

    private void insertComments(List<Map<String, Object>> comments) {
        for (int i = 0; i < comments.size(); i += BATCH) {
            final List<Map<String, Object>> chunk = comments.subList(i, Math.min(i + BATCH, comments.size()));
            jdbc.batchUpdate("""
                INSERT OR IGNORE INTO post_comments
                  (id, post_id, author_id, author_name, author_avatar, parent_id, body, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int n) throws SQLException {
                    Map<String, Object> c = chunk.get(n);
                    ps.setString(1, str(c.get("id")));
                    ps.setString(2, str(c.get("post_id")));
                    ps.setString(3, str(c.get("author_id")));
                    ps.setString(4, str(c.get("author_name")));
                    ps.setString(5, str(c.get("author_avatar")));
                    ps.setString(6, str(c.get("parent_id")));
                    ps.setString(7, str(c.get("body")));
                    ps.setString(8, str(c.get("created_at")));
                }

                @Override
                public int getBatchSize() {
                    return chunk.size();
                }
            });
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static long num(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
