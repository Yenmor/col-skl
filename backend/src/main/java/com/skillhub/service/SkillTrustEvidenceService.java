package com.skillhub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.dto.SkillSourcesSummary;
import com.skillhub.dto.SkillTrustEvidence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SkillTrustEvidenceService {
    private final JdbcTemplate jdbc;
    private final SeniorReader reader;
    private final ObjectMapper json;

    public SkillTrustEvidenceService(JdbcTemplate jdbc, SeniorReader reader, ObjectMapper json) {
        this.jdbc = jdbc;
        this.reader = reader;
        this.json = json;
    }

    public SkillTrustEvidence evidenceFor(String skillId) {
        TrustFacts facts = factsFor(skillId);
        SkillSourcesSummary sources = reader.sourcesSummary(skillId);
        Counts counts = countsFor(skillId);

        int sourceScore = (facts.sourceConfirmed ? 50 : 0)
            + (facts.sourceAuthorized ? 30 : 0)
            + (sources.available() ? 20 : 0);
        String sourceStatus = facts.sourceConfirmed && facts.sourceAuthorized
            ? "CONFIRMED" : facts.sourceConfirmed || facts.sourceAuthorized ? "PARTIAL" : "MISSING";
        String sourceLabel = facts.sourceConfirmed && facts.sourceAuthorized
            ? "本人已核实并同意公开"
            : facts.sourceConfirmed ? "本人已核实，公开授权待补"
            : facts.sourceAuthorized ? "已同意公开，内容待本人核实"
            : "尚未取得本人核实与同意";
        String sourceDetail = appendNote(
            sources.available()
                ? sourceLabel + "；另有 " + sources.threadCount() + " 个线程、" + sources.mappingCount() + " 条来源映射。"
                : sourceLabel + "；包内没有可用的来源映射。",
            facts.sourceNote);

        int authorityScore = authorityScore(facts.authorities.size());
        int platformScore = platformScore(authorityScore, facts.aiScore);
        String platformStatus = !facts.authorities.isEmpty()
            ? "CONFIRMED" : facts.aiScore != null ? "PARTIAL" : "MISSING";
        String platformLabel = !facts.authorities.isEmpty()
            ? facts.authorities.size() + " 条权威渠道已核对"
            : facts.aiScore != null ? "仅有平台 AI 辅助评分" : "尚无平台核验记录";
        String authorityText = facts.authorities.isEmpty() ? null : String.join("、", facts.authorities);
        String platformDetail = platformDetail(facts, authorityText);

        int communityScore = communityScore(counts.likes, counts.downloads, counts.comments);
        long interactions = counts.likes + counts.downloads + counts.comments;
        String communityStatus = interactions > 0 ? "CONFIRMED" : "NOT_TRACKED";
        String communityLabel = interactions > 0 ? "社区采用数据已记录" : "尚无社区采用数据";
        String communityDetail = interactions > 0
            ? "热度只反映采用与讨论，不等同方法正确或来源真实。"
            : "点赞、下载和评论会在真实发生后累计，不用空值补分。";

        int overall = overallScore(sourceScore, platformScore, communityScore,
            facts.sourceConfirmed, facts.sourceAuthorized, facts.authorities.size());
        String level = overall >= 80 ? "高信任"
            : overall >= 60 ? "证据较充分"
            : overall >= 40 ? "待补核验" : "证据不足";
        String summary = summaryFor(facts, sources, counts);

        return new SkillTrustEvidence(
            overall, level, summary,
            new SkillTrustEvidence.Item(sourceStatus, sourceLabel, sourceDetail, sourceScore,
                facts.sourceConfirmed, facts.sourceAuthorized, null, null, null, null,
                null, null, null),
            new SkillTrustEvidence.Item(platformStatus, platformLabel, platformDetail, platformScore,
                null, null, facts.aiScore != null, facts.aiScore, authorityText,
                facts.authorities.size(), null, null, null),
            new SkillTrustEvidence.Item(communityStatus, communityLabel, communityDetail, communityScore,
                null, null, null, null, null, null,
                counts.likes, counts.downloads, counts.comments));
    }

    /** A public upload is explicit permission to publish, but is not treated as author confirmation. */
    public void recordPublicUpload(String skillId) {
        jdbc.update("""
            INSERT INTO skill_trust_facts
              (skill_id, source_confirmed, source_authorized, source_note,
               authority_channels_json, demo, updated_at)
            VALUES (?,0,1,?,'[]',0,?)
            ON CONFLICT(skill_id) DO UPDATE SET
              source_authorized=1,
              source_note=CASE WHEN source_note IS NULL OR TRIM(source_note)=''
                THEN excluded.source_note ELSE source_note END,
              updated_at=excluded.updated_at
            """, skillId, "上传者主动公开该 Skill；尚未完成内容回访核实。", Instant.now().toString());
    }

    public void recordDownload(String skillId, String userId) {
        jdbc.update("INSERT INTO skill_download_events (id, skill_id, user_id, created_at) VALUES (?,?,?,?)",
            UUID.randomUUID().toString(), skillId, clean(userId), Instant.now().toString());
    }

    static int authorityScore(int count) {
        if (count <= 0) return 0;
        if (count == 1) return 70;
        if (count == 2) return 85;
        return 100;
    }

    static int platformScore(int authorityScore, Integer aiScore) {
        int ai = aiScore == null ? 0 : clamp(aiScore);
        if (authorityScore == 0) return aiScore == null ? 0 : (int) Math.round(ai * 0.35);
        if (aiScore == null) return authorityScore;
        return (int) Math.round(authorityScore * 0.65 + ai * 0.35);
    }

    static int communityScore(long likes, long downloads, long comments) {
        double likeScore = logScore(likes, 50);
        double downloadScore = logScore(downloads, 200);
        double commentScore = logScore(comments, 30);
        return clamp((int) Math.round(likeScore * 0.30 + downloadScore * 0.35 + commentScore * 0.35));
    }

    static int overallScore(int source, int platform, int community,
                            boolean confirmed, boolean authorized, int authorityCount) {
        int raw = clamp((int) Math.round(source * 0.40 + platform * 0.40 + community * 0.20));
        // Popularity and AI cannot lift an unconfirmed source into a high-trust state.
        if (!confirmed || !authorized) return Math.min(raw, 49);
        // Without an independent authoritative channel, the maximum remains "evidence fairly complete".
        if (authorityCount <= 0) return Math.min(raw, 69);
        return raw;
    }

    private TrustFacts factsFor(String skillId) {
        List<TrustFacts> rows = jdbc.query("""
            SELECT source_confirmed, source_authorized, source_note,
                   authority_channels_json, ai_score, ai_model, ai_note, demo
            FROM skill_trust_facts WHERE skill_id=?
            """, (rs, row) -> new TrustFacts(
                rs.getInt("source_confirmed") != 0,
                rs.getInt("source_authorized") != 0,
                rs.getString("source_note"),
                readAuthorities(rs.getString("authority_channels_json")),
                (Integer) rs.getObject("ai_score"),
                rs.getString("ai_model"),
                rs.getString("ai_note"),
                rs.getInt("demo") != 0), skillId);
        return rows.isEmpty() ? TrustFacts.empty() : rows.get(0);
    }

    private Counts countsFor(String skillId) {
        long likes = count("skill_likes", skillId);
        long downloads = count("skill_download_events", skillId);
        long comments = count("skill_comments", skillId);
        return new Counts(likes, downloads, comments);
    }

    private long count(String table, String skillId) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE skill_id=?", Long.class, skillId);
        return value == null ? 0 : value;
    }

    private List<String> readAuthorities(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            return json.readValue(raw, new TypeReference<List<String>>() {}).stream()
                .map(String::trim).filter(s -> !s.isBlank()).distinct().toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String platformDetail(TrustFacts facts, String authorityText) {
        StringBuilder text = new StringBuilder();
        if (authorityText != null) text.append("已记录：").append(authorityText).append("。 ");
        if (facts.aiScore != null) {
            text.append("平台 AI 辅助评分 ").append(clamp(facts.aiScore)).append(" 分");
            if (facts.aiModel != null && !facts.aiModel.isBlank()) text.append("（").append(facts.aiModel).append("）");
            text.append("；AI 只检查一致性与风险，不是权威认证。 ");
        }
        if (facts.aiNote != null && !facts.aiNote.isBlank()) text.append(facts.aiNote.trim()).append(" ");
        if (facts.demo) text.append("以上为演示数据。 ");
        if (text.isEmpty()) text.append("没有权威渠道记录，也没有可展示的 AI 辅助评分。 ");
        return text.toString().trim();
    }

    private static String summaryFor(TrustFacts facts, SkillSourcesSummary sources, Counts counts) {
        if (!facts.sourceConfirmed || !facts.sourceAuthorized) {
            return "本人核实或公开同意尚未补齐；AI 与社区热度不会绕过这道门槛。";
        }
        if (facts.authorities.isEmpty()) {
            return "本人确认已完成，但还缺独立权威渠道核对，综合信任上限被限制。";
        }
        if (!sources.available()) {
            return "本人确认和权威渠道已有记录；包内来源映射仍需补充。";
        }
        if (counts.likes + counts.downloads + counts.comments == 0) {
            return "来源与平台核验较完整，尚待真实使用与讨论数据积累。";
        }
        return "本人确认、权威渠道、平台 AI 辅助检查与社区采用数据均可分别查看。";
    }

    private static String appendNote(String base, String note) {
        return note == null || note.isBlank() ? base : base + " " + note.trim();
    }

    private static double logScore(long value, long reference) {
        if (value <= 0) return 0;
        return Math.min(100, Math.log1p(value) / Math.log1p(reference) * 100);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Counts(long likes, long downloads, long comments) {}

    private record TrustFacts(boolean sourceConfirmed, boolean sourceAuthorized, String sourceNote,
                              List<String> authorities, Integer aiScore, String aiModel,
                              String aiNote, boolean demo) {
        static TrustFacts empty() {
            return new TrustFacts(false, false, null, List.of(), null, null, null, false);
        }
    }
}
