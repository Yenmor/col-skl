package com.skillhub.repo.sqlite;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper json;

    public SqliteSeniorSkillRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public SeniorSkill save(SeniorSkill s) {
        jdbc.update("""
            INSERT INTO senior_skills
              (id,name,school,major,year,domain,avatar_filename,source,created_at,
               owner_id,visibility,layer_id,summary,version,tags_json,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
              name=excluded.name, school=excluded.school, major=excluded.major,
              year=excluded.year, domain=excluded.domain, avatar_filename=excluded.avatar_filename,
              source=excluded.source, owner_id=excluded.owner_id, visibility=excluded.visibility,
              layer_id=excluded.layer_id, summary=excluded.summary, version=excluded.version,
              tags_json=excluded.tags_json, updated_at=excluded.updated_at
            """,
            s.id(), s.name(), s.school(), s.major(), s.year(), s.domain(),
            s.avatarFilename(), s.source(),
            s.createdAt().toString(), s.ownerId(), s.visibility(), s.layerId(), s.summary(),
            s.version(), writeTags(s.tags()), s.updatedAt().toString());
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
    public Optional<SeniorSkill> findAccessibleById(String id, String userId) {
        var rows = jdbc.query("""
            SELECT * FROM senior_skills
            WHERE id=? AND (visibility='PUBLIC' OR owner_id=?)
            """, (rs, n) -> map(rs), id, userId);
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
    public List<SeniorSkill> listPublic(String domain, String school, String query) {
        StringBuilder sql = new StringBuilder("SELECT * FROM senior_skills WHERE visibility='PUBLIC'");
        var args = new java.util.ArrayList<Object>();
        if (domain != null && !domain.isBlank()) {
            sql.append(" AND domain = ?");
            args.add(domain);
        }
        if (school != null && !school.isBlank()) {
            sql.append(" AND school = ?");
            args.add(school);
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (LOWER(name) LIKE ? OR LOWER(summary) LIKE ? OR LOWER(tags_json) LIKE ?)");
            String needle = "%" + query.trim().toLowerCase(java.util.Locale.ROOT) + "%";
            args.add(needle);
            args.add(needle);
            args.add(needle);
        }
        sql.append(" ORDER BY updated_at DESC, created_at DESC");
        return jdbc.query(sql.toString(), (rs, n) -> map(rs), args.toArray());
    }

    @Override
    public List<SeniorSkill> listOwned(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) return List.of();
        return jdbc.query("""
            SELECT * FROM senior_skills WHERE owner_id=?
            ORDER BY updated_at DESC, created_at DESC
            """, (rs, n) -> map(rs), ownerId);
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
            parseInstant(rs.getString("created_at")),
            rs.getString("owner_id"),
            rs.getString("visibility"),
            rs.getString("layer_id"),
            rs.getString("summary"),
            rs.getString("version"),
            readTags(rs.getString("tags_json")),
            parseInstant(rs.getString("updated_at"))
        );
    }

    private String writeTags(List<String> tags) {
        try {
            return json.writeValueAsString(tags == null ? List.of() : tags);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private List<String> readTags(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private java.time.Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return java.time.Instant.EPOCH;
        try {
            return java.time.Instant.parse(value);
        } catch (Exception ignored) {
            return java.time.Instant.EPOCH;
        }
    }
}
