package com.skillhub.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.config.BaseController;
import com.skillhub.dto.DistillResult;
import com.skillhub.dto.SeniorFragmentDto;
import com.skillhub.model.SeniorFragment;
import com.skillhub.repo.SeniorFragmentRepository;
import com.skillhub.service.SeniorDistillService;
import com.skillhub.service.SeniorReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * 用户蒸馏端点（TODO 事项 2 + 5）。
 *
 * <p>接收用户 ID + 该用户的全部相关帖子 / 评论 / 回复 JSON，按 metaskill
 * （{@code preproducts/metaskills/community-experience-distiller}）的简化版流程
 * 生成七件套 + 蒸馏片段。
 *
 * <p>步骤：
 * <ol>
 *   <li>解析 userId + posts / comments（输入契约见 {@code docs/api-v1.md §8}）</li>
 *   <li>从 senior_skills 候选中找匹配学长的 SKILL.md 模板</li>
 *   <li>直接生成七件套（manifest / meta / SKILL / work / persona / work_skill / persona_skill）到 {@code data/seniors/<newId>/}</li>
 *   <li>同步蒸馏到 {@code senior_fragments} 表</li>
 *   <li>返回 DistillResult</li>
 * </ol>
 *
 * <p>本端点<span>不</span>做"成熟度评估"——MCP 9 跑 4 维评分在 metaskill 脚本里，
 * Java 端走"已通过"假设（要求调用方先验过 metaskill 评分）。
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/distill")
public class UserDistillController extends BaseController {

    private static final Logger LOG = LoggerFactory.getLogger(UserDistillController.class);

    private final SeniorDistillService distillService;
    private final SeniorReader reader;
    private final SeniorFragmentRepository fragmentRepo;
    private final ObjectMapper json = new ObjectMapper();

    public UserDistillController(SeniorDistillService distillService,
                                 SeniorReader reader,
                                 SeniorFragmentRepository fragmentRepo) {
        this.distillService = distillService;
        this.reader = reader;
        this.fragmentRepo = fragmentRepo;
    }

    @PostMapping
    public DistillResult distill(@PathVariable String userId,
                                 @RequestBody DistillRequest body) {
        if (body == null || body.posts() == null || body.posts().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "posts 不能为空");
        }
        // 生成新 skill id = userId 派生
        String newId = "skill-" + userId.toLowerCase().replaceAll("[^a-z0-9-]", "-");

        // 简化：直接从 posts 拆对话片段
        List<SeniorFragmentDto> fragments = new ArrayList<>();
        int idx = 0;
        for (PostDto p : body.posts()) {
            if (p.body() == null || p.body().isBlank()) continue;
            String content = "讨论：" + (p.title() == null ? "" : p.title()) + "\n" + truncate(p.body(), 400);
            String fragmentId = "frag_" + UUID.randomUUID().toString().substring(0, 10);
            fragments.add(new SeniorFragmentDto(
                fragmentId,
                newId,
                SeniorFragmentDto.SeniorFragmentKind.WORK,
                content,
                List.of(body.domain() == null ? "经验" : body.domain()),
                Instant.now()
            ));
            idx++;
        }
        // 写七件套到 data/seniors/<newId>/
        writeSkillBundle(newId, userId, body, fragments);

        // 写 senior_fragments 表
        for (SeniorFragmentDto f : fragments) {
            fragmentRepo.save(new SeniorFragment(
                f.id(), f.seniorId(), f.kind().name(), f.content(),
                jsonTags(f.tags()), f.createdAt()
            ));
        }

        LOG.info("distilled userId={} → newSkill={} fragments={}", userId, newId, fragments.size());
        return new DistillResult(newId, fragments, Instant.now());
    }

    private void writeSkillBundle(String newId, String userId, DistillRequest body, List<SeniorFragmentDto> fragments) {
        try {
            Path dir = reader.seniorsDir().resolve(newId);
            Files.createDirectories(dir);
            String displayName = body.displayName() == null || body.displayName().isBlank() ? "学长" : body.displayName().trim();
            String domain = normalizeDomain(body.domain());
            String name = displayName + " · " + domain;
            // manifest.json
            String manifest = "{\n" +
                "  \"name\": \"" + escape(name) + "\",\n" +
                "  \"domain\": \"" + escape(domain) + "\",\n" +
                "  \"avatar\": \"avatar.svg\",\n" +
                "  \"description\": \"" + escape("从社区发言整理的" + domain + "经验，待本人确认。") + "\",\n" +
                "  \"triggers\": " + json.writeValueAsString(body.triggers() == null || body.triggers().isEmpty() ? List.of(domain) : body.triggers()) + ",\n" +
                "  \"source\": \"distilled\",\n" +
                "  \"version\": \"v1\",\n" +
                "  \"reviewStatus\": \"draft\"\n" +
                "}\n";
            Files.writeString(dir.resolve("manifest.json"), manifest);

            // meta.json
            String meta = "{\n" +
                "  \"schema_version\": \"1.0\",\n" +
                "  \"skill_id\": \"" + newId + "\",\n" +
                "  \"name\": \"" + escape(name) + "\",\n" +
                "  \"identity\": {\n" +
                "    \"user_id\": \"" + userId + "\",\n" +
                "    \"display_name\": \"" + escape(displayName) + "\",\n" +
                "    \"school\": \"" + escape(body.school() == null ? "未填写" : body.school()) + "\",\n" +
                "    \"college\": \"" + escape(body.college() == null ? "未填写" : body.college()) + "\",\n" +
                "    \"major\": \"" + escape(body.major() == null ? "未填写" : body.major()) + "\",\n" +
                "    \"year_graduated\": \"" + escape(body.year() == null ? "" : body.year()) + "\"\n" +
                "  },\n" +
                "  \"lifecycle\": {\n" +
                "    \"version\": \"v1\",\n" +
                "    \"status\": \"draft\",\n" +
                "    \"generated_at\": \"" + Instant.now() + "\"\n" +
                "  },\n" +
                "  \"review\": {\n" +
                "    \"author_confirmed\": false,\n" +
                "    \"status\": \"draft\"\n" +
                "  }\n" +
                "}\n";
            Files.writeString(dir.resolve("meta.json"), meta);

            // SKILL.md
            StringBuilder skill = new StringBuilder();
            skill.append("# ").append(name).append("\n\n");
            skill.append("从社区发言整理的").append(domain).append("经验，待本人确认。\n\n");
            skill.append("## 触发条件\n\n");
            for (String t : body.triggers() == null || body.triggers().isEmpty() ? List.of(domain) : body.triggers()) {
                skill.append("- ").append(t).append("\n");
            }
            skill.append("\n## 步骤\n\n");
            skill.append("1. 识别用户的目标与限制。\n");
            skill.append("2. 依据下列蒸馏出的经验片段给出可执行建议。\n");
            skill.append("3. 涉及时间敏感事实时优先建议核对官方来源。\n\n");
            skill.append("## 证据片段\n\n");
            for (SeniorFragmentDto f : fragments) {
                skill.append("### ").append(f.id()).append("\n\n");
                skill.append(f.content()).append("\n\n");
            }
            Files.writeString(dir.resolve("SKILL.md"), skill.toString());

            // work.md / persona.md 简化
            Files.writeString(dir.resolve("work.md"), "# Work\n\n" + body.posts().get(0).body());
            Files.writeString(dir.resolve("persona.md"), "# Persona\n\n用户在社区中表现出的表达风格与判断习惯。");
            Files.writeString(dir.resolve("work_skill.md"), "---\nname: work\ndescription: 从社区发言整理的工作能力 Skill。\n---\n\n# Work Skill\n");
            Files.writeString(dir.resolve("persona_skill.md"), "---\nname: persona\ndescription: 从社区发言整理的表达偏好 Skill。\n---\n\n# Persona Skill\n");

            // sources.json
            StringBuilder sources = new StringBuilder();
            sources.append("{\n  \"skill_id\": \"").append(newId).append("\",\n");
            sources.append("  \"version\": \"v1\",\n");
            sources.append("  \"fragment_ids\": [");
            for (int i = 0; i < fragments.size(); i++) {
                if (i > 0) sources.append(", ");
                sources.append("\"").append(fragments.get(i).id()).append("\"");
            }
            sources.append("],\n  \"fragments\": [\n");
            for (int i = 0; i < fragments.size(); i++) {
                if (i > 0) sources.append(",\n");
                SeniorFragmentDto f = fragments.get(i);
                sources.append("    {\"fragment_id\": \"").append(f.id()).append("\", ");
                sources.append("\"thread_id\": \"").append(body.posts().get(Math.min(i, body.posts().size() - 1)).id() == null ? "" : body.posts().get(Math.min(i, body.posts().size() - 1)).id()).append("\", ");
                sources.append("\"title\": \"社区发言 ").append(i + 1).append("\"}");
            }
            sources.append("\n  ]\n}\n");
            Files.writeString(dir.resolve("sources.json"), sources.toString());

        } catch (IOException e) {
            LOG.error("failed to write skill bundle for {}", newId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "写七件套失败：" + e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private static final List<String> CONTROLLED_DOMAINS = List.of(
        "学习", "科研", "竞赛", "技能", "保研", "选课", "求职", "实习"
    );

    /** domain 收敛到受控词表：命中则用词表值，否则归入「学习」。 */
    private String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) return "学习";
        String d = domain.trim();
        for (String known : CONTROLLED_DOMAINS) {
            if (d.equals(known)) return known;
        }
        for (String known : CONTROLLED_DOMAINS) {
            if (d.contains(known)) return known;
        }
        return "学习";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String jsonTags(List<String> tags) {
        try {
            return json.writeValueAsString(tags);
        } catch (Exception e) {
            return "[]";
        }
    }

    public record DistillRequest(
            String displayName,
            String school,
            String college,
            String major,
            String year,
            String domain,
            List<String> triggers,
            List<PostDto> posts
    ) {}

    public record PostDto(
            String id,
            String title,
            String body,
            String domain
    ) {}
}
