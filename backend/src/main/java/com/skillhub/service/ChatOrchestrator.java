package com.skillhub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.model.SeniorSkill;
import com.skillhub.repo.SeniorSkillRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 对话编排：拿用户消息 → 用所有学长.Skill 的 SKILL.md 摘要 → 静态按
 * 关键词相似度挑 Top 3 → 用每位的 SKILL.md 当 prompt 上下文分别生成 mock 回答。
 *
 * 当前 LLM 用本地的「mock 内容生成器」代替，等接入真实 LLM 时只需替换
 * {@link #synthesizeAnswer(SeniorSkill, String, String)}。
 */
@Service
public class ChatOrchestrator {

    private static final List<String> STOP_WORDS = List.of(
        "我","你","他","她","的","是","了","吗","啊","呢","要","想","学",
        "问","下","个","怎么","如何","一下","一点","什么","哪些","请","帮","能"
    );

    private final SeniorSkillRepository repo;
    private final SeniorReader reader;
    private final ObjectMapper json = new ObjectMapper();

    public ChatOrchestrator(SeniorSkillRepository repo, SeniorReader reader) {
        this.repo = repo;
        this.reader = reader;
    }

    public List<SeniorAnswer> orchestrate(String message) {
        List<SeniorReader.SeniorCandidate> candidates = reader.listCandidates();
        // 评分：候选人 head 与 message 的 jaccard + 域名对齐奖励
        var scored = new ArrayList<ScoredCandidate>();
        Set<String> msgTokens = tokenize(message);
        for (var c : candidates) {
            int overlap = jaccard(msgTokens, tokenize(c.skillHead()));
            if (c.domain() != null && !c.domain().isBlank()
                && message.contains(c.domain())) overlap += 2;
            scored.add(new ScoredCandidate(c, overlap));
        }
        // 1) domain 命中优先，2) jaccard 高优先
        scored.sort((a, b) -> {
            String aDom = Optional.ofNullable(a.c().domain()).orElse("");
            String bDom = Optional.ofNullable(b.c().domain()).orElse("");
            boolean aHit = !aDom.isBlank() && message.contains(aDom);
            boolean bHit = !bDom.isBlank() && message.contains(bDom);
            if (aHit != bHit) return aHit ? -1 : 1;
            return Integer.compare(b.score(), a.score());
        });

        return scored.stream().limit(3).map(sc -> {
            String skillMd = reader.loadSkillMd(sc.c().id());
            String content = synthesizeAnswer(findById(sc.c().id()), sc.c(), message, skillMd);
            return new SeniorAnswer(
                sc.c().id(), sc.c().name(), sc.c().school(), sc.c().major(),
                sc.c().year(), content
            );
        }).collect(Collectors.toList());
    }

    public String serialize(List<SeniorAnswer> answers) {
        try {
            return json.writeValueAsString(answers);
        } catch (Exception e) {
            return "[]";
        }
    }

    public List<SeniorAnswer> deserialize(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank()) return List.of();
        try {
            return json.readValue(jsonStr, new TypeReference<List<SeniorAnswer>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private SeniorSkill findById(String id) {
        return repo.findById(id).orElse(null);
    }

    /** 当前是 mock。接入 LLM 时整段被 LLM 调用替换。 */
    private String synthesizeAnswer(SeniorSkill senior, SeniorReader.SeniorCandidate c,
                                   String userMsg, String skillMd) {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(senior != null ? senior.name() : c.name()).append(" · ")
          .append(Optional.ofNullable(c.school()).orElse("")).append("】\n\n");
        sb.append("收到你的问题：「").append(userMsg).append("」\n\n");
        // 简单 mock：从 SKILL.md 摘录前两段作回答
        String[] sections = skillMd.split("## ");
        sb.append("以我那一届为例，先给个整体方向：\n\n");
        int chunks = 0;
        for (String section : sections) {
            if (chunks >= 2) break;
            String trimmed = section.trim();
            if (trimmed.isEmpty()) continue;
            int newline = trimmed.indexOf('\n');
            String title = newline > 0 ? trimmed.substring(0, newline).trim() : trimmed;
            String body = newline > 0 ? trimmed.substring(newline + 1).trim() : "";
            if (!title.isEmpty() && !body.isEmpty()) {
                sb.append("**").append(title).append("**\n\n");
                sb.append(body.length() > 220 ? body.substring(0, 220) + "…" : body);
                sb.append("\n\n");
                chunks++;
            }
        }
        sb.append("— 详细的时间表/数字可点开我的主页查看完整 SKILL 文档。");
        return sb.toString().trim();
    }

    private Set<String> tokenize(String text) {
        if (text == null) return Set.of();
        String[] toks = text.toLowerCase()
            .replaceAll("[^\\u4e00-\\u9fa5a-z0-9 ]", " ")
            .split("\\s+");
        Set<String> out = new HashSet<>();
        for (String t : toks) {
            if (t.length() < 2) continue;
            if (STOP_WORDS.contains(t)) continue;
            out.add(t);
        }
        return out;
    }

    private int jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> union = new HashSet<>(a); union.addAll(b);
        Set<String> inter = new HashSet<>(a); inter.retainAll(b);
        return union.isEmpty() ? 0 : (inter.size() * 100 / union.size());
    }

    private record ScoredCandidate(SeniorReader.SeniorCandidate c, int score) {}

    public record SeniorAnswer(
        String seniorId, String name, String school, String major,
        String year, String content
    ) {}
}
