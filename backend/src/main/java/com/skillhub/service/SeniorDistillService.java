package com.skillhub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.dto.ErrorCode;
import com.skillhub.dto.SeniorFragmentDto;
import com.skillhub.model.SeniorFragment;
import com.skillhub.repo.SeniorFragmentRepository;
import com.skillhub.service.SeniorReader.SeniorCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 蒸馏服务（v1 §8）。
 *
 * <p>本服务基于 metaskill 蒸馏产物
 * （{@code preproducts/metaskills/community-experience-distiller}）落地的
 * 七件套结构，从 {@code data/seniors/<id>/} 读：
 * <ul>
 *   <li>{@code SKILL.md} —— 拆解成 {@code PERSONA} 片段</li>
 *   <li>{@code work.md} / {@code work_skill.md} —— 拆解成 {@code WORK} 片段</li>
 *   <li>{@code persona.md} / {@code persona_skill.md} —— 拆解成 {@code PERSONA} 片段</li>
 *   <li>{@code sources.json} —— 每个 {@code fragment_id} 解析为 {@code MEMORY} 片段</li>
 * </ul>
 *
 * <p>蒸馏结果以 {@code SeniorFragmentDto} 形式返回，并写入 {@code senior_fragments} 表。
 *
 * <p>设计依据：
 * <ul>
 *   <li>TODO 事项 2（"自动分析帖子内容存入向量库"）—— 此处用 SQLite 表代替向量库（D7）</li>
 *   <li>TODO 事项 5（metaskill）—— 蒸馏流程由 {@code preproducts/metaskills} 提供</li>
 *   <li>D14 决策：同步返回</li>
 *   <li>D17 决策：{@code kind} 枚举 {@code PERSONA / WORK / MEMORY / OTHER}</li>
 * </ul>
 */
@Service
public class SeniorDistillService {

    private static final Logger LOG = LoggerFactory.getLogger(SeniorDistillService.class);

    private final SeniorReader reader;
    private final SeniorFragmentRepository repo;
    private final ObjectMapper json = new ObjectMapper();

    public SeniorDistillService(SeniorReader reader, SeniorFragmentRepository repo) {
        this.reader = reader;
        this.repo = repo;
    }

    /**
     * 对指定学长执行蒸馏。
     *
     * <p>同步返回（{@code docs/api-v1.md §8.1}）。失败时返回空列表，不抛错。
     */
    public List<SeniorFragmentDto> distill(String seniorId) {
        // 验证 senior 存在
        boolean exists = reader.listCandidates().stream()
            .anyMatch(c -> c.id().equals(seniorId));
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorCode.SENIOR_NOT_FOUND.code());
        }

        List<SeniorFragmentDto> out = new ArrayList<>();
        Path dir = reader.seniorsDir().resolve(seniorId);

        // 1. SKILL.md → WORK 片段
        out.addAll(parseMdAsFragments(dir, "SKILL.md", SeniorFragmentDto.SeniorFragmentKind.WORK, seniorId));

        // 2. work.md / work_skill.md → WORK 片段
        out.addAll(parseMdAsFragments(dir, "work.md", SeniorFragmentDto.SeniorFragmentKind.WORK, seniorId));
        out.addAll(parseMdAsFragments(dir, "work_skill.md", SeniorFragmentDto.SeniorFragmentKind.WORK, seniorId));

        // 3. persona.md / persona_skill.md → PERSONA 片段
        out.addAll(parseMdAsFragments(dir, "persona.md", SeniorFragmentDto.SeniorFragmentKind.PERSONA, seniorId));
        out.addAll(parseMdAsFragments(dir, "persona_skill.md", SeniorFragmentDto.SeniorFragmentKind.PERSONA, seniorId));

        // 4. sources.json → MEMORY 片段
        out.addAll(parseSourcesAsMemories(dir, seniorId));

        // 5. 写入表
        for (SeniorFragmentDto dto : out) {
            repo.save(new SeniorFragment(
                dto.id(),
                dto.seniorId(),
                dto.kind().name(),
                dto.content(),
                jsonTags(dto.tags()),
                dto.createdAt()
            ));
        }

        LOG.info("distilled senior={} fragments={}", seniorId, out.size());
        return out;
    }

    /**
     * 把 md 文件按段落拆成多个片段（每段一条）。
     */
    private List<SeniorFragmentDto> parseMdAsFragments(Path dir, String file, SeniorFragmentDto.SeniorFragmentKind kind, String seniorId) {
        Path p = dir.resolve(file);
        if (!Files.exists(p)) return List.of();
        try {
            String text = Files.readString(p);
            // 按空行 / 标题切段
            String[] paragraphs = text.split("\\n\\s*\\n");
            List<SeniorFragmentDto> out = new ArrayList<>();
            for (String para : paragraphs) {
                String trimmed = para.trim();
                if (trimmed.isEmpty() || trimmed.length() < 5) continue;
                if (trimmed.startsWith("---") || trimmed.startsWith("```")) continue;
                // 跳过大段标题
                if (trimmed.startsWith("#") && !trimmed.contains("\n")) continue;
                String[] tags = extractTags(trimmed);
                out.add(new SeniorFragmentDto(
                    UUID.randomUUID().toString(),
                    seniorId,
                    kind,
                    trimmed.length() > 500 ? trimmed.substring(0, 500) + "…" : trimmed,
                    List.of(tags),
                    java.time.Instant.now()
                ));
            }
            return out;
        } catch (IOException e) {
            LOG.warn("failed to read {} for senior {}: {}", file, seniorId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 从 sources.json 解析每个 fragment 为 MEMORY 片段。
     */
    private List<SeniorFragmentDto> parseSourcesAsMemories(Path dir, String seniorId) {
        Path p = dir.resolve("sources.json");
        if (!Files.exists(p)) return List.of();
        try {
            String text = Files.readString(p);
            Map<String, Object> root = json.readValue(text, new TypeReference<>() {});
            Object fragmentsObj = root.get("fragments");
            if (!(fragmentsObj instanceof List<?> fragments)) return List.of();

            List<SeniorFragmentDto> out = new ArrayList<>();
            for (Object o : fragments) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Object fid = m.get("fragment_id");
                Object title = m.get("title");
                Object thread = m.get("thread_id");
                Object sources = m.get("source_message_ids");
                String content = "线程 " + thread + " — " + title
                    + (sources != null ? "（来源 " + sources + "）" : "");
                out.add(new SeniorFragmentDto(
                    UUID.randomUUID().toString(),
                    seniorId,
                    SeniorFragmentDto.SeniorFragmentKind.MEMORY,
                    content,
                    List.of("回忆", "片段"),
                    java.time.Instant.now()
                ));
            }
            return out;
        } catch (IOException e) {
            LOG.warn("failed to read sources.json for senior {}: {}", seniorId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 粗略从段落首句抓标签关键词。
     */
    private String[] extractTags(String text) {
        // 简单启发式：抓 〔证据：...〕引用
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("〔证据：([^,，;；]+)〕").matcher(text);
        List<String> tags = new ArrayList<>();
        while (m.find()) {
            tags.add(m.group(1).trim());
        }
        if (tags.isEmpty()) tags.add("经验");
        return tags.toArray(new String[0]);
    }

    private String jsonTags(List<String> tags) {
        try {
            return json.writeValueAsString(tags);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * 列出指定学长的历史蒸馏片段。
     */
    public List<SeniorFragmentDto> listBySenior(String seniorId, int limit) {
        // 验证 senior 存在
        boolean exists = reader.listCandidates().stream()
            .anyMatch(c -> c.id().equals(seniorId));
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorCode.SENIOR_NOT_FOUND.code());
        }
        return repo.listBySenior(seniorId, Math.max(1, Math.min(limit, 50)))
            .stream()
            .map(f -> new SeniorFragmentDto(
                f.id(),
                f.seniorId(),
                SeniorFragmentDto.SeniorFragmentKind.valueOf(f.kind()),
                f.content(),
                parseTags(f.tagsJson()),
                f.createdAt()
            ))
            .toList();
    }

    private List<String> parseTags(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return this.json.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}