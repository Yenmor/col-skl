package com.skillhub.repo.sqlite;

import com.skillhub.model.SeniorSkill;
import com.skillhub.repo.SeniorSkillRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class SqliteSeniorSkillRepository implements SeniorSkillRepository {

    private final JdbcTemplate jdbc;

    public SqliteSeniorSkillRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SeniorSkill save(SeniorSkill s) {
        jdbc.update("""
            INSERT INTO senior_skills (id,name,school,major,year,domain,avatar_filename,source,created_at)
            VALUES (?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
              name=excluded.name, school=excluded.school, major=excluded.major,
              year=excluded.year, domain=excluded.domain, avatar_filename=excluded.avatar_filename,
              source=excluded.source
            """,
            s.id(), s.name(), s.school(), s.major(), s.year(), s.domain(),
            s.avatarFilename(), s.source(),
            s.createdAt().toString());
        return s;
    }

    @Override
    public Optional<SeniorSkill> findById(String id) {
        var rows = jdbc.query(
            "SELECT * FROM senior_skills WHERE id = ?",
            (rs, n) -> map(rs), id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public boolean existsById(String id) {
        Integer c = jdbc.queryForObject(
            "SELECT COUNT(*) FROM senior_skills WHERE id=?", Integer.class, id);
        return c != null && c > 0;
    }

    @Override
    public List<SeniorSkill> list(String domain, String school) {
        StringBuilder sql = new StringBuilder("SELECT * FROM senior_skills WHERE 1=1");
        var args = new Object[][]{ new Object[]{} };
        var argList = new java.util.ArrayList<Object>();
        if (domain != null && !domain.isBlank()) {
            sql.append(" AND domain = ?");
            argList.add(domain);
        }
        if (school != null && !school.isBlank()) {
            sql.append(" AND school = ?");
            argList.add(school);
        }
        sql.append(" ORDER BY created_at DESC");
        return jdbc.query(sql.toString(), (rs, n) -> map(rs), argList.toArray());
    }

    @Override
    public List<String> allIds() {
        return jdbc.query("SELECT id FROM senior_skills", (rs, n) -> rs.getString("id"));
    }

    @Override
    public void deleteById(String id) {
        jdbc.update("DELETE FROM senior_skills WHERE id=?", id);
    }

    private SeniorSkill map(ResultSet rs) throws SQLException {
        return new SeniorSkill(
            rs.getString("id"),
            rs.getString("name"),
            rs.getString("school"),
            rs.getString("major"),
            rs.getString("year"),
            rs.getString("domain"),
            rs.getString("avatar_filename"),
            rs.getString("source"),
            java.time.Instant.parse(rs.getString("created_at"))
        );
    }
}
