package com.skillhub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillhub.config.ApiException;
import com.skillhub.config.LlmProperties;
import com.skillhub.dto.DistillDraftRequest;
import com.skillhub.dto.DistillDraftResponse;
import com.skillhub.dto.ErrorCode;
import com.skillhub.dto.ExperienceMaterialsResponse;
import com.skillhub.model.SeniorSkill;
import com.skillhub.model.User;
import com.skillhub.repo.SeniorSkillRepository;
import com.skillhub.service.llm.LlmClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Real-LLM-only implementation of the community-experience-distiller protocol. */
@Service
public class PrivateSkillDistillService {
    private static final List<String> WORK_SECTIONS = List.of(
        "scope", "required_inputs", "workflow", "decision_points",
        "completion_criteria", "pitfalls", "boundaries", "experience_notes");
    private static final List<String> PERSONA_SECTIONS = List.of(
        "communication_principles", "expression_patterns", "uncertainty_behavior", "chat_style");
    private static final int MIN_OWNER_MESSAGE_CHARS = 8;
    private static final int MAX_ATTEMPTS = 4;

    private final ExperienceMaterialService materials;
    private final SeniorReader reader;
    private final SeniorSkillRepository repo;
    private final SkillCatalogService catalog;
    private final UserService users;
    private final LlmClient llm;
    private final LlmProperties properties;
    private final ObjectMapper json;

    public PrivateSkillDistillService(ExperienceMaterialService materials,
                                      SeniorReader reader,
                                      SeniorSkillRepository repo,
                                      SkillCatalogService catalog,
                                      UserService users,
                                      LlmClient llm,
                                      LlmProperties properties,
                                      ObjectMapper json) {
        this.materials = materials;
        this.reader = reader;
        this.repo = repo;
        this.catalog = catalog;
        this.users = users;
        this.llm = llm;
        this.properties = properties;
        this.json = json;
    }

    public DistillDraftResponse distill(String userId, DistillDraftRequest request) {
        validateRequest(request);
        Map<String, ExperienceMaterialsResponse.ThreadMaterial> selected =
            materials.selectedForUser(userId, request.threadIds());
        int requested = request.threadIds() == null ? 0
            : (int) request.threadIds().stream().filter(java.util.Objects::nonNull).distinct().count();
        if (requested < ExperienceMaterialService.MINIMUM_THREADS
            || selected.size() < ExperienceMaterialService.MINIMUM_THREADS) {
            throw new ApiException(ErrorCode.DISTILL_INSUFFICIENT_EVIDENCE,
                "至少选择三个本人参与的独立讨论线程",
                Map.of(
                    "minimumThreads", ExperienceMaterialService.MINIMUM_THREADS,
                    "selectedThreads", selected.size(),
                    "missingThreads", Math.max(0, ExperienceMaterialService.MINIMUM_THREADS - selected.size())));
        }
        if (!materials.realLlmAvailable()) {
            throw new ApiException(ErrorCode.DISTILL_LLM_UNAVAILABLE,
                "未配置真实 LLM，当前只能浏览材料，不能生成草稿",
                Map.of("provider", properties.getProvider(), "llmAvailable", false));
        }

        User user = users.getOrCreate(userId);
        ObjectNode fragments = buildFragments(user, selected);

        // 单次 LLM 生成存在随机性（偶发引用编造证据、覆盖线程不足）。
        // 用演示材料时重试最多 MAX_ATTEMPTS 次，显著提高"一次点击即成功"的稳定性。
        ObjectNode distillation = null;
        Validation validation = null;
        String skillId = null;
        List<String> lastErrors = List.of();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS && distillation == null; attempt++) {
            String response;
            try {
                response = llm.complete(loadProtocol(), buildUserPrompt(request, fragments))
                    .block(Duration.ofSeconds(Math.max(10, properties.getTimeoutSeconds())));
            } catch (RuntimeException ex) {
                lastErrors = List.of("真实模型生成失败: " + safeMessage(ex));
                continue;
            }
            if (response == null || response.isBlank()) {
                lastErrors = List.of("真实模型返回空结果");
                continue;
            }
            ObjectNode parsed;
            try {
                parsed = parseDistillation(response);
            } catch (RuntimeException ex) {
                lastErrors = List.of("模型未返回合法 JSON: " + safeMessage(ex));
                continue;
            }
            String candidateId = "skill-" + UUID.randomUUID().toString().substring(0, 12);
            normalizeOwnedDraft(parsed, candidateId, user, request);
            Validation checked = validateDistillation(parsed, fragments);
            lastErrors = checked.errors();
            if (checked.errors().isEmpty()) {
                distillation = parsed;
                validation = checked;
                skillId = candidateId;
            }
        }

        if (distillation == null || validation == null) {
            if (isEvidenceInsufficientForErrors(lastErrors)) {
                throw new ApiException(ErrorCode.DISTILL_INSUFFICIENT_EVIDENCE,
                    "所选材料还不足以生成完整 Skill",
                    Map.of(
                        "minimumThreads", ExperienceMaterialService.MINIMUM_THREADS,
                        "selectedThreads", selected.size(),
                        "violations", lastErrors,
                        "missingEvidence", missingEvidenceList(lastErrors)));
            }
            throw new ApiException(ErrorCode.DISTILL_GENERATION_FAILED,
                "模型产物未通过 metaskill 证据校验", Map.of("violations", lastErrors));
        }

        Path base = reader.seniorsDir().toAbsolutePath().normalize();
        Path staging = base.resolve(".draft-" + UUID.randomUUID()).normalize();
        Path generated = staging.resolve(skillId).normalize();
        Path destination = reader.checkedSkillDir(skillId);
        boolean moved = false;
        boolean indexed = false;
        try {
            Files.createDirectories(generated);
            writeBundle(generated, distillation, fragments, validation.usedEvidence(),
                validation.usedOwnerMessages());
            validateWrittenBundle(generated, skillId, validation.usedEvidence());
            move(generated, destination);
            moved = true;
            SeniorSkill saved = reader.ingestIfValid(destination, userId, SeniorSkill.PRIVATE);
            if (saved == null) throw new IOException("生成目录无法建立 Skill 索引");
            indexed = true;
            return new DistillDraftResponse(catalog.detail(skillId, userId));
        } catch (Exception ex) {
            if (indexed) repo.deleteById(skillId);
            if (moved) SeniorIngestService.deleteTree(destination);
            if (ex instanceof ApiException api) throw api;
            throw new ApiException(ErrorCode.DISTILL_GENERATION_FAILED,
                "草稿写入失败", Map.of("reason", safeMessage(ex)));
        } finally {
            SeniorIngestService.deleteTree(staging);
        }
    }

    private void validateRequest(DistillDraftRequest request) {
        if (request == null || request.topic() == null || request.topic().isBlank()
            || request.goal() == null || request.goal().isBlank()) {
            throw new ApiException(ErrorCode.GENERAL_VALIDATION, "topic 和 goal 不能为空");
        }
        if (request.topic().length() > 60 || request.goal().length() > 500) {
            throw new ApiException(ErrorCode.GENERAL_VALIDATION, "topic 或 goal 过长");
        }
    }

    private ObjectNode buildFragments(User user,
                                      Map<String, ExperienceMaterialsResponse.ThreadMaterial> selected) {
        ObjectNode root = json.createObjectNode();
        root.put("export_version", "1.0");
        root.put("exported_at", Instant.now().toString());
        ObjectNode target = root.putObject("target_user");
        target.put("id", user.id());
        target.put("display_name", user.displayName());
        target.putObject("profile");
        target.putObject("consent")
            .put("distillation_allowed", true)
            .put("publication_allowed", false);
        ArrayNode fragments = root.putArray("fragments");
        for (var thread : selected.values()) {
            ObjectNode fragment = fragments.addObject();
            fragment.put("fragment_id", fragmentId(user.id(), thread.threadId()));
            fragment.put("thread_id", thread.threadId());
            fragment.put("title", thread.title());
            fragment.put("domain", thread.domain());
            ArrayNode sourceIds = fragment.putArray("source_message_ids");
            sourceIds.add(thread.post().id());
            thread.comments().forEach(comment -> sourceIds.add(comment.id()));
            ObjectNode post = fragment.putObject("post");
            post.put("id", thread.post().id());
            post.put("body", thread.post().body());
            post.put("author_id", thread.post().authorId());
            post.put("created_at", String.valueOf(thread.post().createdAt()));
            ArrayNode comments = fragment.putArray("comments");
            for (var comment : thread.comments()) {
                ObjectNode item = comments.addObject();
                item.put("id", comment.id());
                item.put("body", comment.body());
                item.put("author_id", comment.authorId());
                if (comment.parentId() != null) item.put("parent_id", comment.parentId());
                item.put("created_at", String.valueOf(comment.createdAt()));
                item.put("is_target_user", user.id().equals(comment.authorId()));
            }
            fragment.put("target_authored_post", user.id().equals(thread.post().authorId()));
            fragment.set("target_comment_ids", json.valueToTree(thread.ownedCommentIds()));
            ArrayNode targetMessageIds = fragment.putArray("target_message_ids");
            if (user.id().equals(thread.post().authorId())) targetMessageIds.add(thread.post().id());
            thread.ownedCommentIds().forEach(targetMessageIds::add);
        }
        return root;
    }

    private String buildUserPrompt(DistillDraftRequest request, ObjectNode fragments) {
        return """
            请根据给定 topic、goal 和 fragments，只输出一个符合 distillation-schema 的 JSON 对象。
            禁止 Markdown 代码围栏。禁止补写材料中不存在的事实。
            每条 work/persona 规则的 evidence 必须是非空对象数组；每项格式为
            {"fragment_id":"frag_xxx","message_ids":["msg_xxx"]}。
            fragment_id 只能引用输入中的片段；message_ids 必须引用同一片段 target_message_ids
            中由目标用户本人写下、正文至少 8 个有效字符且直接支持该规则的消息。
            full_skill 必须满足成熟度总分 >=12、每项 >=2、至少两个 decision_points、至少一个 boundary，
            并让核心规则覆盖至少三个独立 thread；不满足时请输出 mode=fragments_only。
            注意：当前输入材料已经包含多个独立线程以及足够的本人长发言，足以支撑 full_skill；
            除非材料在对应方向确实缺失，否则必须输出 mode=full_skill，不要轻易降级为 fragments_only。

            topic: %s
            goal: %s
            preferred_layer_id: %s
            preferred_tags: %s
            fragments:
            %s
            """.formatted(request.topic(), request.goal(), safe(request.layerId()),
                request.tags() == null ? "[]" : request.tags(), fragments.toPrettyString());
    }

    private String loadProtocol() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> roots = List.of(
            cwd.resolve("../preproducts/metaskills/community-experience-distiller").normalize(),
            cwd.resolve("preproducts/metaskills/community-experience-distiller").normalize());
        for (Path root : roots) {
            Path skill = root.resolve("SKILL.md");
            Path protocol = root.resolve("references/distillation-protocol.md");
            Path schema = root.resolve("references/distillation-schema.md");
            if (Files.isRegularFile(skill) && Files.isRegularFile(protocol) && Files.isRegularFile(schema)) {
                try {
                    return Files.readString(skill) + "\n\n" + Files.readString(protocol)
                        + "\n\n" + Files.readString(schema);
                } catch (IOException ignored) {
                }
            }
        }
        throw new ApiException(ErrorCode.DISTILL_GENERATION_FAILED,
            "community-experience-distiller 协议文件不可用");
    }

    private ObjectNode parseDistillation(String raw) {
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            int newline = cleaned.indexOf('\n');
            int end = cleaned.lastIndexOf("```");
            if (newline >= 0 && end > newline) cleaned = cleaned.substring(newline + 1, end).trim();
        }
        try {
            JsonNode node = json.readTree(cleaned);
            if (!(node instanceof ObjectNode object)) throw new IOException("顶层不是 JSON 对象");
            return object;
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.DISTILL_GENERATION_FAILED,
                "模型未返回合法的结构化 JSON", Map.of("reason", safeMessage(ex)));
        }
    }

    private void normalizeOwnedDraft(ObjectNode root, String skillId, User user,
                                     DistillDraftRequest request) {
        ObjectNode skill = object(root, "skill");
        skill.put("id", skillId);
        if (skill.path("name").asText("").isBlank()) {
            skill.put("name", user.displayName() + " · " + truncate(request.topic(), 12));
        }
        if (skill.path("domain").asText("").isBlank()) skill.put("domain", controlledDomain(request.topic()));
        if (skill.path("description").asText("").isBlank()) skill.put("description", truncate(request.goal(), 60));
        if (!skill.path("triggers").isArray() || skill.path("triggers").isEmpty()) {
            skill.set("triggers", json.valueToTree(request.tags() == null || request.tags().isEmpty()
                ? List.of(truncate(request.topic(), 8)) : request.tags()));
        }
        skill.put("version", "v0.1-draft");
        ObjectNode author = object(skill, "author");
        author.put("user_id", user.id());
        author.put("display_name", user.displayName());
        author.put("school", author.path("school").asText(""));
        author.put("college", author.path("college").asText(""));
        author.put("major", author.path("major").asText(""));
        author.put("year_graduated", author.path("year_graduated").asText(""));
        ObjectNode review = object(root, "review");
        review.put("author_confirmed", false);
        review.put("status", "draft");
        review.putNull("confirmed_at");
        review.putNull("confirmed_by");
        root.put("layer_id", safe(request.layerId()));
    }

    private Validation validateDistillation(ObjectNode root, ObjectNode fragments) {
        List<String> errors = new ArrayList<>();
        if (!"full_skill".equals(root.path("mode").asText())) errors.add("mode 必须为 full_skill");
        JsonNode maturity = root.path("maturity");
        if (!"full_skill".equals(maturity.path("decision").asText())) errors.add("maturity.decision 必须为 full_skill");
        if (maturity.path("total").asInt(0) < 12) errors.add("成熟度总分低于 12");
        for (String key : List.of("coverage", "repeatability", "boundaries", "evidence_quality")) {
            if (maturity.path(key).asInt(0) < 2) errors.add("成熟度 " + key + " 低于 2");
        }
        JsonNode work = root.path("work");
        if (!nonEmptyArray(work.path("workflow"))) errors.add("work.workflow 不能为空");
        if (!nonEmptyArray(work.path("boundaries"))) errors.add("work.boundaries 不能为空");
        if (!work.path("decision_points").isArray() || work.path("decision_points").size() < 2) {
            errors.add("work.decision_points 至少两项");
        }

        Map<String, String> evidenceThreads = new HashMap<>();
        Map<String, Map<String, String>> targetMessagesByEvidence = new HashMap<>();
        fragments.path("fragments").forEach(fragment ->
        {
            String fragmentId = fragment.path("fragment_id").asText();
            evidenceThreads.put(fragmentId, fragment.path("thread_id").asText());
            LinkedHashMap<String, String> targetMessages = new LinkedHashMap<>();
            if (fragment.path("target_authored_post").asBoolean(false)) {
                targetMessages.put(fragment.path("post").path("id").asText(),
                    fragment.path("post").path("body").asText());
            }
            fragment.path("comments").forEach(comment -> {
                if (comment.path("is_target_user").asBoolean(false)) {
                    targetMessages.put(comment.path("id").asText(), comment.path("body").asText());
                }
            });
            targetMessagesByEvidence.put(fragmentId, targetMessages);
        });
        Set<String> used = new LinkedHashSet<>();
        Map<String, Set<String>> usedOwnerMessages = new LinkedHashMap<>();
        validateEvidenceSections(work, WORK_SECTIONS, evidenceThreads.keySet(), targetMessagesByEvidence,
            used, usedOwnerMessages, errors, "work");
        JsonNode persona = root.path("persona");
        validateEvidenceSections(persona, PERSONA_SECTIONS, evidenceThreads.keySet(), targetMessagesByEvidence,
            used, usedOwnerMessages, errors, "persona");
        long usedThreads = used.stream().map(evidenceThreads::get).filter(java.util.Objects::nonNull).distinct().count();
        if (usedThreads < ExperienceMaterialService.MINIMUM_THREADS) {
            errors.add("核心规则未引用至少三个独立 thread");
        }
        return new Validation(errors, used, usedOwnerMessages);
    }

    private boolean isEvidenceInsufficient(ObjectNode root, List<String> errors) {
        if ("fragments_only".equals(root.path("mode").asText())
            || "fragments_only".equals(root.path("maturity").path("decision").asText())) {
            return true;
        }
        return errors.stream().allMatch(error -> error.contains("成熟度")
            || error.contains("workflow") || error.contains("boundaries")
            || error.contains("decision_points") || error.contains("独立 thread")
            || error.contains("本人证据正文过短")
            || error.contains("mode 必须") || error.contains("maturity.decision"));
    }

    private List<String> missingEvidence(ObjectNode root, List<String> errors) {
        LinkedHashSet<String> missing = new LinkedHashSet<>(errors);
        JsonNode maturity = root.path("maturity");
        if (maturity.path("coverage").asInt(0) < 2) missing.add("需要更多独立场景覆盖");
        if (maturity.path("repeatability").asInt(0) < 2) missing.add("需要可复现步骤和完成标准");
        if (maturity.path("boundaries").asInt(0) < 2) missing.add("需要决策节点、失败或边界证据");
        if (maturity.path("evidence_quality").asInt(0) < 2) missing.add("需要更多本人直接发言支持核心结论");
        return missing.stream().toList();
    }

    /** 重试耗尽后仅依据错误列表判断是否属于"证据不足"（无产物对象可参考时使用）。 */
    private boolean isEvidenceInsufficientForErrors(List<String> errors) {
        if (errors.isEmpty()) return false;
        return errors.stream().allMatch(error -> error.contains("成熟度")
            || error.contains("workflow") || error.contains("boundaries")
            || error.contains("decision_points") || error.contains("独立 thread")
            || error.contains("本人证据正文过短")
            || error.contains("mode 必须") || error.contains("maturity.decision"));
    }

    private List<String> missingEvidenceList(List<String> errors) {
        return errors.isEmpty() ? List.of("需要更多本人直接发言支持核心结论") : List.copyOf(errors);
    }

    private void validateEvidenceSections(JsonNode parent, List<String> sections,
                                            Set<String> known,
                                            Map<String, Map<String, String>> ownerMessagesByEvidence,
                                            Set<String> used,
                                            Map<String, Set<String>> usedOwnerMessages,
                                            List<String> errors, String prefix) {
        for (String section : sections) {
            JsonNode items = parent.path(section);
            if (!items.isArray()) continue;
            for (int i = 0; i < items.size(); i++) {
                JsonNode evidence = items.get(i).path("evidence");
                if (!evidence.isArray() || evidence.isEmpty()) {
                    errors.add(prefix + "." + section + "[" + i + "] 缺少 evidence");
                    continue;
                }
                for (JsonNode ref : evidence) {
                    if (!ref.isObject()) {
                        errors.add(prefix + "." + section + "[" + i
                            + "] evidence 必须使用 {fragment_id,message_ids} 对象");
                        continue;
                    }
                    String fragmentId = ref.path("fragment_id").asText("");
                    if (!known.contains(fragmentId)) {
                        errors.add("引用了未知证据: " + fragmentId);
                        continue;
                    }
                    used.add(fragmentId);
                    JsonNode messageIds = ref.path("message_ids");
                    if (!messageIds.isArray() || messageIds.isEmpty()) {
                        errors.add(prefix + "." + section + "[" + i
                            + "] evidence.message_ids 不能为空: " + fragmentId);
                        continue;
                    }
                    Map<String, String> ownerMessages = ownerMessagesByEvidence
                        .getOrDefault(fragmentId, Map.of());
                    for (JsonNode messageIdNode : messageIds) {
                        String messageId = messageIdNode.asText("");
                        if (!ownerMessages.containsKey(messageId)) {
                            errors.add(prefix + "." + section + "[" + i
                                + "] message_id 不属于该片段的本人消息: " + messageId);
                            continue;
                        }
                        String body = ownerMessages.get(messageId);
                        if (meaningfulLength(body) < MIN_OWNER_MESSAGE_CHARS) {
                            errors.add(prefix + "." + section + "[" + i
                                + "] 本人证据正文过短: " + messageId);
                            continue;
                        }
                        usedOwnerMessages.computeIfAbsent(fragmentId,
                            ignored -> new LinkedHashSet<>()).add(messageId);
                    }
                }
            }
        }
    }

    private void writeBundle(Path dir, ObjectNode root, ObjectNode fragments, Set<String> used,
                             Map<String, Set<String>> usedOwnerMessages)
            throws IOException {
        JsonNode skill = root.path("skill");
        JsonNode author = skill.path("author");
        String name = skill.path("name").asText();
        String domain = skill.path("domain").asText();
        String description = skill.path("description").asText();
        String version = skill.path("version").asText("v0.1-draft");
        String workMd = renderWork(name, root.path("work"));
        String personaMd = renderPersona(name, root.path("persona"));
        String triggers = joinText(skill.path("triggers"), " / ");
        String combined = "# " + name + "\n\n"
            + "这是一个限定在“" + domain + "”领域、由本人社区证据生成的私有 Skill 草稿。\n\n"
            + "## 触发条件\n\n触发词：" + triggers + "\n\n"
            + "## 运行契约\n\n"
            + "1. 只依据下方方法和证据回答，不编造材料之外的经历。\n"
            + "2. 信息不足时先追问关键输入。\n"
            + "3. 涉及时间敏感事实时提醒核对官方来源。\n"
            + "4. 命中能力边界时明确停止强答。\n\n---\n\n"
            + workMd + "\n---\n\n" + personaMd;
        Files.writeString(dir.resolve("SKILL.md"), combined);
        Files.writeString(dir.resolve("work.md"), workMd);
        Files.writeString(dir.resolve("persona.md"), personaMd);
        Files.writeString(dir.resolve("work_skill.md"), "---\nname: " + skill.path("id").asText()
            + "-work\ndescription: " + description + "（能力方法）\n---\n\n" + workMd);
        Files.writeString(dir.resolve("persona_skill.md"), "---\nname: " + skill.path("id").asText()
            + "-persona\ndescription: " + name + " 的领域表达偏好\n---\n\n" + personaMd);

        ObjectNode manifest = json.createObjectNode();
        manifest.put("id", skill.path("id").asText());
        manifest.put("name", name);
        manifest.put("domain", domain);
        manifest.put("avatar", author.path("avatar").asText(""));
        manifest.put("description", description);
        manifest.set("triggers", skill.path("triggers").deepCopy());
        manifest.put("source", "distilled");
        manifest.put("version", version);
        manifest.put("reviewStatus", "draft");
        manifest.put("layerId", root.path("layer_id").asText(""));
        manifest.put("ownerId", author.path("user_id").asText());
        manifest.put("visibility", SeniorSkill.PRIVATE);
        writeJson(dir.resolve("manifest.json"), manifest);

        ObjectNode meta = json.createObjectNode();
        meta.put("schema_version", "1.0");
        meta.put("skill_id", skill.path("id").asText());
        meta.put("name", name);
        meta.put("owner_id", author.path("user_id").asText());
        meta.put("visibility", SeniorSkill.PRIVATE);
        ObjectNode identity = meta.putObject("identity");
        for (String key : List.of("user_id", "display_name", "school", "college", "major", "year_graduated")) {
            identity.put(key, author.path(key).asText(""));
        }
        meta.putObject("lifecycle").put("version", version).put("status", "draft")
            .put("generated_at", Instant.now().toString());
        meta.set("review", root.path("review").deepCopy());
        meta.set("maturity", root.path("maturity").deepCopy());
        writeJson(dir.resolve("meta.json"), meta);

        ObjectNode sources = json.createObjectNode();
        sources.put("skill_id", skill.path("id").asText());
        sources.put("version", version);
        sources.put("verification", "PLATFORM_VERIFIED");
        sources.set("fragment_ids", json.valueToTree(used));
        ArrayNode mappings = sources.putArray("fragments");
        Map<String, JsonNode> index = new LinkedHashMap<>();
        fragments.path("fragments").forEach(fragment -> index.put(fragment.path("fragment_id").asText(), fragment));
        for (String id : used) {
            JsonNode fragment = index.get(id);
            ObjectNode mapping = mappings.addObject();
            mapping.put("fragment_id", id);
            mapping.put("thread_id", fragment.path("thread_id").asText());
            mapping.put("title", fragment.path("title").asText());
            Set<String> actualOwnerMessages = usedOwnerMessages.getOrDefault(id, Set.of());
            mapping.set("source_message_ids", json.valueToTree(actualOwnerMessages));
            mapping.set("target_message_ids", json.valueToTree(actualOwnerMessages));
        }
        writeJson(dir.resolve("sources.json"), sources);
    }

    private String renderWork(String name, JsonNode work) {
        StringBuilder out = new StringBuilder("# ").append(name).append(" - 能力方法\n\n");
        renderNumbered(out, "执行流程", work.path("workflow"));
        renderDecisions(out, work.path("decision_points"));
        Map<String, String> names = Map.of(
            "scope", "任务范围", "required_inputs", "所需输入", "completion_criteria", "完成标准",
            "pitfalls", "常见错误", "boundaries", "能力边界", "experience_notes", "经验补充");
        for (String section : List.of("scope", "required_inputs", "completion_criteria", "pitfalls", "boundaries", "experience_notes")) {
            renderBullets(out, names.get(section), work.path(section));
        }
        return out.toString();
    }

    private String renderPersona(String name, JsonNode persona) {
        StringBuilder out = new StringBuilder("# ").append(name).append(" - 表达偏好\n\n")
            .append("> 只描述该领域中有直接证据支持的沟通方式，不代表完整人格。\n\n");
        Map<String, String> names = Map.of(
            "chat_style", "聊天风格", "communication_principles", "沟通原则",
            "expression_patterns", "表达模式", "uncertainty_behavior", "不确定性处理");
        for (String section : PERSONA_SECTIONS) renderBullets(out, names.get(section), persona.path(section));
        out.append("## 禁止推断\n\n- 不推断完整人格、心理状态或敏感属性。\n");
        return out.toString();
    }

    private void renderNumbered(StringBuilder out, String title, JsonNode items) {
        out.append("## ").append(title).append("\n\n");
        for (int i = 0; items.isArray() && i < items.size(); i++) {
            JsonNode item = items.get(i);
            out.append(i + 1).append(". ").append(statement(item)).append(citation(item)).append("\n");
        }
        out.append("\n");
    }

    private void renderDecisions(StringBuilder out, JsonNode items) {
        out.append("## 决策节点\n\n");
        for (JsonNode item : items) {
            out.append("- 当").append(item.path("condition").asText()).append("时，")
                .append(item.path("action").asText()).append(citation(item)).append("\n");
        }
        out.append("\n");
    }

    private void renderBullets(StringBuilder out, String title, JsonNode items) {
        if (!items.isArray() || items.isEmpty()) return;
        out.append("## ").append(title).append("\n\n");
        for (JsonNode item : items) out.append("- ").append(statement(item)).append(citation(item)).append("\n");
        out.append("\n");
    }

    private String statement(JsonNode item) {
        if (item.hasNonNull("statement")) return item.path("statement").asText();
        if (item.hasNonNull("instruction")) return item.path("instruction").asText();
        if (item.hasNonNull("condition")) return "当" + item.path("condition").asText()
            + "时，" + item.path("action").asText();
        return item.toString();
    }

    private String citation(JsonNode item) {
        List<String> citations = new ArrayList<>();
        JsonNode evidence = item.path("evidence");
        if (evidence.isArray()) {
            evidence.forEach(ref -> {
                if (ref.isObject()) {
                    String fragmentId = ref.path("fragment_id").asText("");
                    if (!fragmentId.isBlank()) citations.add(fragmentId);
                } else if (ref.isTextual()) {
                    citations.add(ref.asText());
                }
            });
        }
        return " 〔证据：" + String.join(", ", citations) + "〕";
    }

    private void validateWrittenBundle(Path dir, String id, Set<String> known) throws IOException {
        for (String file : SeniorReader.REQUIRED_FILES) {
            if (!Files.isRegularFile(dir.resolve(file)) || Files.size(dir.resolve(file)) == 0) {
                throw new IOException("缺少生成文件: " + file);
            }
        }
        if (!Files.isRegularFile(dir.resolve("sources.json"))) throw new IOException("缺少 sources.json");
        JsonNode meta = json.readTree(dir.resolve("meta.json").toFile());
        JsonNode sources = json.readTree(dir.resolve("sources.json").toFile());
        if (!id.equals(meta.path("skill_id").asText())) throw new IOException("meta ID 不一致");
        if (!"draft".equals(meta.path("review").path("status").asText())
            || meta.path("review").path("author_confirmed").asBoolean(true)) {
            throw new IOException("自动生成项必须是未确认草稿");
        }
        for (JsonNode evidence : sources.path("fragment_ids")) {
            if (!known.contains(evidence.asText())) throw new IOException("sources 含未知证据");
        }
        for (JsonNode mapping : sources.path("fragments")) {
            if (!mapping.path("target_message_ids").isArray()
                || mapping.path("target_message_ids").isEmpty()) {
                throw new IOException("sources 核心映射缺少本人 authored message");
            }
        }
    }

    private void move(Path source, Path destination) throws IOException {
        if (Files.exists(destination) || repo.existsById(destination.getFileName().toString())) {
            throw new IOException("生成的 Skill ID 冲突");
        }
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, destination);
        }
    }

    private void writeJson(Path path, JsonNode node) throws IOException {
        json.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), node);
    }

    private static ObjectNode object(ObjectNode parent, String field) {
        JsonNode existing = parent.get(field);
        if (existing instanceof ObjectNode object) return object;
        return parent.putObject(field);
    }

    private static ObjectNode object(ObjectNode parent, String nestedParent, String field) {
        return object(object(parent, nestedParent), field);
    }

    private static boolean nonEmptyArray(JsonNode node) {
        return node.isArray() && !node.isEmpty();
    }

    private static String fragmentId(String userId, String threadId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((userId + ":" + threadId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 5; i++) hex.append(String.format("%02x", digest[i]));
            return "frag_" + hex;
        } catch (Exception ex) {
            return "frag_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        }
    }

    private static String joinText(JsonNode array, String separator) {
        if (!array.isArray()) return "";
        List<String> values = new ArrayList<>();
        array.forEach(item -> values.add(item.asText()));
        return String.join(separator, values);
    }

    private static List<String> stringValues(JsonNode array) {
        if (!array.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        array.forEach(item -> {
            if (item.isTextual()) values.add(item.asText());
            else if (!item.isNull()) values.add(item.toString());
        });
        return values;
    }

    private static String controlledDomain(String value) {
        if (value != null) {
            for (String domain : List.of("学习", "保研", "科研", "竞赛", "技能", "求职", "实习", "选课")) {
                if (value.contains(domain)) return domain;
            }
        }
        return "技能";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int max) {
        String safe = safe(value).trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    private static int meaningfulLength(String value) {
        if (value == null) return 0;
        return value.replaceAll("[\\s\\p{P}\\p{S}]", "").length();
    }

    private static String safeMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : truncate(message, 300);
    }

    private record Validation(List<String> errors, Set<String> usedEvidence,
                              Map<String, Set<String>> usedOwnerMessages) {}
}
