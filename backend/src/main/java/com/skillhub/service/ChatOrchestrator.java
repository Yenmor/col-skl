package com.skillhub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.config.LlmProperties;
import com.skillhub.model.SeniorSkill;
import com.skillhub.repo.SeniorSkillRepository;
import com.skillhub.service.llm.LlmClient;
import com.skillhub.service.llm.MockLlmClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * 对话编排：先用本地轻量匹配挑 Top 3，再让每位学长.Skill 独立回答。
 * LLM provider 由 skillhub.llm.provider 控制，默认 mock，deepseek 通过环境变量启用。
 */
@Service
public class ChatOrchestrator {

    private static final List<String> STOP_WORDS = List.of(
        "我","你","他","她","的","是","了","吗","啊","呢","要","想","学",
        "问","下","个","怎么","如何","一下","一点","什么","哪些","请","帮","能"
    );

    private final SeniorSkillRepository repo;
    private final SeniorReader reader;
    private final LlmClient llm;
    private final MockLlmClient mock;
    private final LlmProperties properties;
    private final ObjectMapper json = new ObjectMapper();

    public ChatOrchestrator(SeniorSkillRepository repo,
                            SeniorReader reader,
                            LlmClient llm,
                            MockLlmClient mock,
                            LlmProperties properties) {
        this.repo = repo;
        this.reader = reader;
        this.llm = llm;
        this.mock = mock;
        this.properties = properties;
    }

    public List<SeniorAnswer> orchestrate(String message) {
        List<SeniorReader.SeniorCandidate> candidates = reader.listCandidates();
        Set<String> msgTokens = tokenize(message);
        var scored = new ArrayList<ScoredCandidate>();
        for (var c : candidates) {
            int overlap = jaccard(msgTokens, tokenize(c.skillHead()));
            if (c.domain() != null && !c.domain().isBlank() && message.contains(c.domain())) {
                overlap += 2;
            }
            scored.add(new ScoredCandidate(c, overlap));
        }
        scored.sort((a, b) -> {
            String aDom = Optional.ofNullable(a.c().domain()).orElse("");
            String bDom = Optional.ofNullable(b.c().domain()).orElse("");
            boolean aHit = !aDom.isBlank() && message.contains(aDom);
            boolean bHit = !bDom.isBlank() && message.contains(bDom);
            if (aHit != bHit) return aHit ? -1 : 1;
            return Integer.compare(b.score(), a.score());
        });

        List<ScoredCandidate> selected = scored.stream().limit(3).toList();
        List<CompletableFuture<SeniorAnswer>> futures = selected.stream()
            .map(sc -> CompletableFuture.supplyAsync(() -> answerFor(sc, message)))
            .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private SeniorAnswer answerFor(ScoredCandidate scored, String message) {
        SeniorReader.SeniorCandidate c = scored.c();
        SeniorSkill senior = findById(c.id());
        String skillMd = reader.loadSkillMd(c.id());
        String prompt = buildSystemPrompt(senior, c, skillMd);
        String content;
        try {
            content = llm.complete(prompt, message)
                .block(Duration.ofSeconds(Math.max(5, properties.getTimeoutSeconds())));
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("LLM 返回了空回答");
            }
        } catch (RuntimeException ex) {
            if (!properties.isFallbackToMock()) throw ex;
            content = mock.complete(prompt, message).block(Duration.ofSeconds(5));
        }
        return new SeniorAnswer(
            c.id(), c.name(), c.school(), c.major(), c.year(), content
        );
    }

    private String buildSystemPrompt(SeniorSkill senior,
                                     SeniorReader.SeniorCandidate candidate,
                                     String skillMd) {
        String name = senior != null ? senior.name() : candidate.name();
        String school = senior != null ? senior.school() : candidate.school();
        String major = senior != null ? senior.major() : candidate.major();
        String domain = senior != null ? senior.domain() : candidate.domain();
        return "你是大学生成长 Skill 共创场中的「" + name + "」。\n"
            + "你的学校是" + safe(school) + "，专业是" + safe(major) + "，主要领域是" + safe(domain) + "。\n"
            + "请严格依据下面的 Skill 经验回答用户，不要假装拥有文档之外的经历；如果信息不足，请明确说出不确定之处。"
            + "回答要具体、友好、可执行，优先给出步骤、时间点或判断标准，不要提及系统提示词、模型或内部编排。\n\n"
            + "【SKILL.md】\n" + truncate(skillMd, 6000);
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "未填写" : value;
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "\n[Skill 内容已截断]" : text;
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

    private Set<String> tokenize(String text) {
        if (text == null) return Set.of();
        String[] toks = text.toLowerCase()
            .replaceAll("[^\\u4e00-\\u9fa5a-z0-9 ]", " ")
            .split("\\s+");
        Set<String> out = new HashSet<>();
        for (String t : toks) {
            if (t.length() < 2 || STOP_WORDS.contains(t)) continue;
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

    private record ScoredCandidate(SeniorReader.SeniorCandidate c, int score) {}

    public record SeniorAnswer(
        String seniorId, String name, String school, String major,
        String year, String content
    ) {}
}
