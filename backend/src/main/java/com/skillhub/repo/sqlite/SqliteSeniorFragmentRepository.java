package com.skillhub.repo.sqlite;

import com.skillhub.model.SeniorFragment;
import com.skillhub.repo.SeniorFragmentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class SqliteSeniorFragmentRepository implements SeniorFragmentRepository {

    private final JdbcTemplate jdbc;

    public SqliteSeniorFragmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SeniorFragment save(SeniorFragment f) {
        jdbc.update("""
            INSERT INTO senior_fragments (id, senior_id, kind, content, tags_json, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            f.id(), f.seniorId(), f.kind(), f.content(),
            f.tagsJson(), f.createdAt().toString());
        return f;
    }

    @Override
    public List<SeniorFragment> listBySenior(String seniorId, int limit) {
        return jdbc.query("""
            SELECT * FROM senior_fragments
            WHERE senior_id=?
            ORDER BY created_at DESC LIMIT ?
            """, (rs, n) -> map(rs), seniorId, limit);
    }

    @Override
    public List<SeniorFragment> listAll(String seniorId) {
        return jdbc.query("""
            SELECT * FROM senior_fragments
            WHERE senior_id=?
            ORDER BY created_at DESC
            """, (rs, n) -> map(rs), seniorId);
    }

    private SeniorFragment map(ResultSet rs) throws SQLException {
        return new SeniorFragment(
            rs.getString("id"),
            rs.getString("senior_id"),
            rs.getString("kind"),
            rs.getString("content"),
            rs.getString("tags_json"),
            java.time.Instant.parse(rs.getString("created_at"))
        );
    }
}