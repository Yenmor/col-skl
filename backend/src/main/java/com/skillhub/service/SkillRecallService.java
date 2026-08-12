package com.skillhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.dto.SkillRecallItem;
import com.skillhub.service.SeniorReader.SeniorCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 技能召回服务（v1 §7）。
 *
 * <p>从 {@code data/seniors/<id>/manifest.json} 读取 triggers / domain / name，
 * 用 Jaccard + 关键词命中打分（D6 公开 API）。
 *
 * <p>设计依据：
 * <ul>
 *   <li>D6 决策：召回服务公开</li>
 *   <li>D7 决策：MVP 阶段不引入向量库；关键词 + tag 命中已足够</li>
 *   <li>D16 决策：返回最小外壳（{@code seniorId / score / text / tags}）</li>
 * </ul>
 */
@Service
public class SkillRecallService {

    private static final Logger LOG = LoggerFactory.getLogger(SkillRecallService.class);

    private final SeniorReader reader;
    private final ObjectMapper json = new ObjectMapper();

    public SkillRecallService(SeniorReader reader) {
        this.reader = reader;
    }

    public List<SkillRecallItem> recall(String query, int topK, String domain, String school) {
        Set<String> qTokens = tokenize(query);
        List<ScoredCandidate> scored = new ArrayList<>();

        for (SeniorCandidate c : reader.listCandidates()) {
            // domain 过滤
            if (domain != null && !domain.isBlank()
                && (c.domain() == null || !c.domain().equals(domain))) {
                continue;
            }
            // 读 manifest.json 拿 triggers
            List<String> triggers = readTriggers(c.id());
            Set<String> cTokens = new HashSet<>(triggers);
            cTokens.addAll(tokenize(c.name()));
            cTokens.addAll(tokenize(c.domain() == null ? "" : c.domain()));
            cTokens.addAll(tokenize(c.skillHead() == null ? "" : c.skillHead()));

            int score = jaccard(qTokens, cTokens);
            // 关键词命中加分
            for (String t : triggers) {
                if (query.contains(t)) score += 5;
            }
            // 领域命中加分
            if (c.domain() != null && query.contains(c.domain())) score += 3;

            String text = "[" + c.name() + "] " + c.school() + " · " + c.major()
                + " · " + (c.domain() == null ? "" : c.domain());
            scored.add(new ScoredCandidate(c.id(), score, text, triggers));
        }

        scored.sort((a, b) -> Integer.compare(b.score, a.score));
        int limit = Math.max(1, Math.min(topK, 20));
        return scored.stream()
            .limit(limit)
            .map(s -> new SkillRecallItem(
                s.seniorId,
                Math.min(1.0, s.score / 20.0),
                s.text,
                s.tags
            ))
            .toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> readTriggers(String seniorId) {
        Path p = reader.seniorsDir().resolve(seniorId).resolve("manifest.json");
        if (!Files.exists(p)) return List.of();
        try {
            String text = Files.readString(p);
            Map<String, Object> root = json.readValue(text, Map.class);
            Object triggers = root.get("triggers");
            if (triggers instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object o : list) {
                    if (o != null) out.add(o.toString());
                }
                return out;
            }
            return List.of();
        } catch (IOException e) {
            LOG.warn("failed to read manifest for {}: {}", seniorId, e.getMessage());
            return List.of();
        }
    }

    private Set<String> tokenize(String text) {
        if (text == null) return Set.of();
        String[] toks = text.toLowerCase()
            .replaceAll("[^\\u4e00-\\u9fa5a-z0-9 ]", " ")
            .split("\\s+");
        Set<String> out = new HashSet<>();
        for (String t : toks) {
            if (t.length() < 2) continue;
            out.add(t);
        }
        return out;
    }

    private int jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        return union.isEmpty() ? 0 : (inter.size() * 100 / union.size());
    }

    private record ScoredCandidate(String seniorId, int score, String text, List<String> tags) {}
}