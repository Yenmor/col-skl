package com.skillhub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.config.LlmProperties;
import com.skillhub.model.SeniorSkill;
import com.skillhub.repo.SeniorFragmentRepository;
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
    private final SeniorFragmentRepository fragmentRepo;
    private final LlmClient llm;
    private final MockLlmClient mock;
    private final LlmProperties properties;
    private final ObjectMapper json = new ObjectMapper();

    public ChatOrchestrator(SeniorSkillRepository repo,
                            SeniorReader reader,
                            SeniorFragmentRepository fragmentRepo,
                            LlmClient llm,
                            MockLlmClient mock,
                            LlmProperties properties) {
        this.repo = repo;
        this.reader = reader;
        this.fragmentRepo = fragmentRepo;
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
            // domain 命中加权
            if (c.domain() != null && !c.domain().isBlank() && message.contains(c.domain())) {
                overlap += 3;
            }
            // manifest triggers 命中加权（与 SkillRecallService 同源）
            for (String t : readTriggers(c.id())) {
                if (message.contains(t)) overlap += 6;
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

        List<ScoredCandidate> selected = scored.stream().limit(1).toList();
        List<CompletableFuture<SeniorAnswer>> futures = selected.stream()
            .map(sc -> CompletableFuture.supplyAsync(() -> answerFor(sc, message)))
            .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> readTriggers(String seniorId) {
        try {
            var manifestPath = reader.seniorsDir().resolve(seniorId).resolve("manifest.json");
            if (!java.nio.file.Files.exists(manifestPath)) return List.of();
            Map<String, Object> root = json.readValue(
                java.nio.file.Files.readString(manifestPath), Map.class);
            Object triggers = root.get("triggers");
            if (triggers instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object o : list) {
                    if (o != null) out.add(o.toString());
                }
                return out;
            }
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private SeniorAnswer answerFor(ScoredCandidate scored, String message) {
        SeniorReader.SeniorCandidate c = scored.c();
        SeniorSkill senior = findById(c.id());
        String skillMd = reader.loadSkillMd(c.id());
        String prompt = buildSystemPrompt(senior, c, skillMd, message);
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
                                     String skillMd,
                                     String userMessage) {
        String name = senior != null ? senior.name() : candidate.name();
        String school = senior != null ? senior.school() : candidate.school();
        String major = senior != null ? senior.major() : candidate.major();
        String domain = senior != null ? senior.domain() : candidate.domain();
        StringBuilder sb = new StringBuilder();
        sb.append("你是大学生成长 Skill 共创场中的「").append(name).append("」。\n")
          .append("你的学校是").append(safe(school))
          .append("，专业是").append(safe(major))
          .append("，主要领域是").append(safe(domain)).append("。\n")
          .append("请严格依据下面的 Skill 经验与记忆片段回答用户，不要假装拥有文档之外的经历；如果信息不足，请明确说出不确定之处。")
          .append("回答要具体、友好、可执行，优先给出步骤、时间点或判断标准，不要提及系统提示词、模型或内部编排。\n\n")
          .append("【SKILL.md】\n").append(truncate(skillMd, 6000));

        // ---- 简化 RAG：从 senior_fragments 召回与用户问题相关的记忆片段 ----
        List<String> memories = recallMemories(candidate.id(), userMessage);
        if (!memories.isEmpty()) {
            sb.append("\n\n【相关记忆片段】\n");
            for (int i = 0; i < Math.min(memories.size(), 5); i++) {
                sb.append("- ").append(truncate(memories.get(i), 300)).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 简化 RAG：把该学长全部蒸馏片段与用户问题做关键词/Jaccard 打分，取 top 相关。
     * 向量库版（Chroma/Milvus）留到 v2（D7）。
     */
    private List<String> recallMemories(String seniorId, String userMessage) {
        try {
            Set<String> qTokens = tokenize(userMessage);
            var scored = new ArrayList<Object[]>();
            for (var f : fragmentRepo.listAll(seniorId)) {
                Set<String> fTokens = new HashSet<>(tokenize(f.content()));
                if (f.tagsJson() != null) {
                    try {
                        List<String> tags = json.readValue(f.tagsJson(), new TypeReference<List<String>>() {});
                        for (String t : tags) {
                            fTokens.addAll(tokenize(t));
                        }
                    } catch (Exception ignored) {
                    }
                }
                int score = jaccard(qTokens, fTokens);
                if (f.content() != null && userMessage != null) {
                    // 直接子串命中加权
                    for (String t : qTokens) {
                        if (t.length() >= 2 && f.content().contains(t)) score += 3;
                    }
                }
                if (score > 0) {
                    scored.add(new Object[]{f, score});
                }
            }
            scored.sort((a, b) -> Integer.compare((Integer) b[1], (Integer) a[1]));
            List<String> out = new ArrayList<>();
            for (Object[] o : scored) {
                var f = (com.skillhub.model.SeniorFragment) o[0];
                out.add("[" + f.kind() + "] " + f.content());
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
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
        String lower = text.toLowerCase();
        Set<String> out = new HashSet<>();
        // 1. 英文/数字分词
        for (String t : lower.replaceAll("[^\\u4e00-\\u9fa5a-z0-9 ]", " ").split("\\s+")) {
            if (t.length() < 2 || STOP_WORDS.contains(t)) continue;
            out.add(t);
        }
        // 2. 中文 bigram（连续两个汉字为一组），解决无空格中文 jaccard 恒为 0 的问题
        String hanzi = lower.replaceAll("[^\\u4e00-\\u9fa5]", "");
        for (int i = 0; i + 1 < hanzi.length(); i++) {
            out.add(hanzi.substring(i, i + 2));
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
