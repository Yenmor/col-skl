package com.skillhub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.config.ApiException;
import com.skillhub.dto.ErrorCode;
import com.skillhub.model.SeniorSkill;
import com.skillhub.repo.SeniorSkillRepository;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Validates a complete Skill bundle before atomically publishing it. */
@Service
public class SeniorIngestService {

    private static final long MAX_UNCOMPRESSED_BYTES = 50L * 1024 * 1024;
    private static final int MAX_UPLOAD_BYTES = 50 * 1024 * 1024;
    private static final int MAX_ENTRIES = 100;

    private final SeniorReader reader;
    private final SeniorSkillRepository repo;
    private final ObjectMapper json;
    private final SkillTrustEvidenceService trustEvidence;

    @Autowired
    public SeniorIngestService(SeniorReader reader, SeniorSkillRepository repo, ObjectMapper json,
                               SkillTrustEvidenceService trustEvidence) {
        this.reader = reader;
        this.repo = repo;
        this.json = json;
        this.trustEvidence = trustEvidence;
    }

    /** Source-compatible constructor for import tests without a database. */
    public SeniorIngestService(SeniorReader reader, SeniorSkillRepository repo, ObjectMapper json) {
        this(reader, repo, json, null);
    }

    /** Legacy compatibility: a user-initiated upload is public even without an owner header. */
    public String upload(MultipartFile zip) {
        return uploadPublic(zip, null).id();
    }

    public SeniorSkill uploadPublic(MultipartFile zip, String ownerId) {
        if (zip == null || zip.isEmpty()) {
            throw invalid("zip 文件为空");
        }
        try {
            return uploadPublic(zip.getOriginalFilename(), zip.getInputStream(), ownerId);
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw invalid("无法读取 Skill 包: " + ex.getMessage());
        }
    }

    public Mono<SeniorSkill> uploadPublic(FilePart file, String ownerId) {
        if (file == null) return Mono.error(invalid("zip 文件为空"));
        return DataBufferUtils.join(file.content(), MAX_UPLOAD_BYTES)
            .map(buffer -> {
                try {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return bytes;
                } finally {
                    DataBufferUtils.release(buffer);
                }
            })
            .switchIfEmpty(Mono.error(invalid("zip 文件为空")))
            .publishOn(Schedulers.boundedElastic())
            .map(bytes -> uploadPublic(file.filename(), new ByteArrayInputStream(bytes), ownerId))
            .onErrorMap(DataBufferLimitException.class, ex -> invalid("zip 文件超过 50MB"));
    }

    /** WebFlux-compatible upload boundary used by the v1 FilePart controller. */
    public SeniorSkill uploadPublic(String filename, InputStream input, String ownerId) {
        if (input == null) {
            throw invalid("zip 文件为空");
        }
        if (filename != null && !filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw invalid("只接受 .zip Skill 包");
        }

        Path base = reader.seniorsDir().toAbsolutePath().normalize();
        Path staging = base.resolve(".incoming-" + UUID.randomUUID()).normalize();
        String id = null;
        Path finalDir = null;
        boolean moved = false;
        try (InputStream upload = input) {
            Files.createDirectories(staging);
            id = extractAndValidate(upload, staging);
            Path stagedBundle = staging.resolve(id).normalize();
            validateBundle(stagedBundle, id);
            persistNormalizedAccessMetadata(stagedBundle, ownerId, SeniorSkill.PUBLIC);
            finalDir = reader.checkedSkillDir(id);
            if (Files.exists(finalDir) || repo.existsById(id)) {
                throw new ApiException(ErrorCode.SKILL_CONFLICT, "Skill ID 已存在: " + id);
            }
            moveDirectory(stagedBundle, finalDir);
            moved = true;
            SeniorSkill saved = reader.ingestIfValid(finalDir, ownerId, SeniorSkill.PUBLIC);
            if (saved == null) {
                throw invalid("Skill 包未通过七件套索引校验");
            }
            if (trustEvidence != null) trustEvidence.recordPublicUpload(saved.id());
            return saved;
        } catch (ApiException ex) {
            if (moved && id != null) repo.deleteById(id);
            throw ex;
        } catch (IOException ex) {
            if (moved && id != null) repo.deleteById(id);
            throw invalid("无法读取 Skill 包: " + ex.getMessage());
        } catch (RuntimeException ex) {
            if (moved && id != null) repo.deleteById(id);
            throw ex;
        } finally {
            deleteTree(staging);
            if (moved && id != null && repo.findById(id).isEmpty() && finalDir != null) {
                deleteTree(finalDir);
            }
        }
    }

    private String extractAndValidate(InputStream input, Path staging) throws IOException {
        String rootId = null;
        int entries = 0;
        long totalBytes = 0;
        Set<String> seen = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw invalid("zip 文件条目过多");
                String raw = entry.getName().replace('\\', '/');
                if (raw.isBlank() || raw.startsWith("/") || raw.matches("^[A-Za-z]:.*")) {
                    throw invalid("zip 包含非法绝对路径");
                }
                String[] segments = raw.split("/");
                if (segments.length == 0 || segments[0].isBlank()
                    || java.util.Arrays.stream(segments).anyMatch(".."::equals)) {
                    throw invalid("zip 包含目录穿越路径");
                }
                String candidateId = segments[0];
                if (!candidateId.matches("[a-z0-9][a-z0-9-]{1,63}")) {
                    throw invalid("Skill 顶层目录 ID 不合法");
                }
                if (rootId == null) rootId = candidateId;
                if (!rootId.equals(candidateId)) throw invalid("zip 只能包含一个 Skill 顶层目录");

                Path destination = staging.resolve(raw).normalize();
                Path expectedRoot = staging.resolve(rootId).normalize();
                if (!destination.startsWith(expectedRoot)) throw invalid("zip 包含目录穿越路径");
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                    continue;
                }
                if (segments.length < 2 || segments[segments.length - 1].isBlank()) {
                    throw invalid("zip 文件必须位于 Skill 顶层目录内");
                }
                String relative = expectedRoot.relativize(destination).toString();
                if (!seen.add(relative.toLowerCase(Locale.ROOT))) throw invalid("zip 包含重复文件: " + relative);
                Files.createDirectories(destination.getParent());
                try (var output = Files.newOutputStream(destination)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = zis.read(buffer)) >= 0) {
                        if (read == 0) continue;
                        totalBytes += read;
                        if (totalBytes > MAX_UNCOMPRESSED_BYTES) throw invalid("zip 解压后超过 50MB");
                        output.write(buffer, 0, read);
                    }
                }
            }
        }
        if (rootId == null) throw invalid("zip 内容为空");
        return rootId;
    }

    private void validateBundle(Path dir, String id) throws IOException {
        for (String required : SeniorReader.REQUIRED_FILES) {
            Path file = dir.resolve(required);
            if (!Files.isRegularFile(file) || Files.size(file) == 0) {
                throw invalid("缺少必需文件: " + required);
            }
        }
        JsonNode manifest = parseObject(dir.resolve("manifest.json"), "manifest.json");
        JsonNode meta = parseObject(dir.resolve("meta.json"), "meta.json");
        if (Files.exists(dir.resolve("sources.json"))) {
            validateSources(parseObject(dir.resolve("sources.json"), "sources.json"), id);
        }
        String metaId = meta.path("skill_id").asText("");
        if (metaId.isBlank() || !id.equals(metaId)) {
            throw invalid("meta.json.skill_id 必须与顶层目录 ID 一致");
        }
        for (String key : new String[]{"id", "skill_id"}) {
            String manifestId = manifest.path(key).asText("");
            if (!manifestId.isBlank() && !id.equals(manifestId)) {
                throw invalid("manifest.json 的 ID 与顶层目录不一致");
            }
        }
        if (manifest.path("name").asText("").isBlank() || manifest.path("domain").asText("").isBlank()) {
            throw invalid("manifest.json 缺少 name 或 domain");
        }
        String skillMd = Files.readString(dir.resolve("SKILL.md"));
        for (String heading : new String[]{"## 触发条件", "## 执行流程", "## 决策节点", "## 能力边界"}) {
            if (!skillMd.contains(heading)) throw invalid("SKILL.md 缺少结构: " + heading);
        }
    }

    private void validateSources(JsonNode sources, String id) {
        if (!id.equals(sources.path("skill_id").asText(""))) {
            throw invalid("sources.json.skill_id 必须与顶层目录 ID 一致");
        }
        JsonNode declaredNode = sources.path("fragment_ids");
        JsonNode mappingsNode = sources.path("fragments");
        if (!declaredNode.isArray() || declaredNode.isEmpty()
            || !mappingsNode.isArray() || mappingsNode.isEmpty()) {
            throw invalid("sources.json 必须包含非空 fragment_ids 和 fragments");
        }

        Set<String> declared = stringIds(declaredNode, "sources.json.fragment_ids");
        Set<String> mapped = new LinkedHashSet<>();
        Set<String> threads = new HashSet<>();
        Set<String> allMessages = new HashSet<>();
        for (int index = 0; index < mappingsNode.size(); index++) {
            JsonNode mapping = mappingsNode.get(index);
            if (!mapping.isObject()) {
                throw invalid("sources.json.fragments[" + index + "] 必须是对象");
            }
            String fragmentId = requiredId(mapping.path("fragment_id"),
                "sources.json.fragments[" + index + "].fragment_id");
            String threadId = requiredId(mapping.path("thread_id"),
                "sources.json.fragments[" + index + "].thread_id");
            if (!mapped.add(fragmentId)) throw invalid("sources.json 包含重复 fragment_id: " + fragmentId);
            if (!threads.add(threadId)) throw invalid("sources.json 包含重复 thread_id: " + threadId);

            Set<String> sourceMessages = stringIds(
                mapping.path("source_message_ids"),
                "sources.json.fragments[" + index + "].source_message_ids");
            for (String messageId : sourceMessages) {
                if (!allMessages.add(messageId)) {
                    throw invalid("sources.json 包含跨映射重复 message_id: " + messageId);
                }
            }

            JsonNode targetNode = mapping.get("target_message_ids");
            if (targetNode != null) {
                Set<String> targets = stringIds(targetNode,
                    "sources.json.fragments[" + index + "].target_message_ids");
                if (!sourceMessages.containsAll(targets)) {
                    throw invalid("sources.json target_message_ids 必须属于同一映射的 source_message_ids");
                }
            }
        }
        if (!declared.equals(mapped)) {
            throw invalid("sources.json.fragment_ids 与 fragments 映射不一致");
        }
    }

    private Set<String> stringIds(JsonNode node, String label) {
        if (!node.isArray() || node.isEmpty()) throw invalid(label + " 必须是非空数组");
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String value = requiredId(item, label);
            if (!values.add(value)) throw invalid(label + " 包含重复 ID: " + value);
        }
        return values;
    }

    private String requiredId(JsonNode node, String label) {
        if (!node.isTextual()) throw invalid(label + " 必须是字符串");
        String value = node.asText().trim();
        if (value.isEmpty() || value.length() > 200) throw invalid(label + " 不能为空且不得超过 200 字符");
        return value;
    }

    private void persistNormalizedAccessMetadata(Path dir, String ownerId, String visibility)
            throws IOException {
        JsonNode manifestNode = parseObject(dir.resolve("manifest.json"), "manifest.json");
        JsonNode metaNode = parseObject(dir.resolve("meta.json"), "meta.json");
        if (!(manifestNode instanceof com.fasterxml.jackson.databind.node.ObjectNode manifest)
            || !(metaNode instanceof com.fasterxml.jackson.databind.node.ObjectNode meta)) {
            throw invalid("Skill 元数据必须是 JSON 对象");
        }
        if (ownerId == null || ownerId.isBlank()) manifest.remove("ownerId");
        else manifest.put("ownerId", ownerId);
        manifest.put("visibility", visibility);
        if (ownerId == null || ownerId.isBlank()) meta.remove("owner_id");
        else meta.put("owner_id", ownerId);
        meta.put("visibility", visibility);
        json.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("manifest.json").toFile(), manifest);
        json.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("meta.json").toFile(), meta);
    }

    private JsonNode parseObject(Path path, String label) {
        try {
            JsonNode parsed = json.readTree(path.toFile());
            if (parsed == null || !parsed.isObject()) throw invalid(label + " 必须是 JSON 对象");
            return parsed;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid(label + " JSON 无法解析");
        }
    }

    private void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target);
        }
    }

    private ApiException invalid(String message) {
        return new ApiException(ErrorCode.SKILL_IMPORT_INVALID, message);
    }

    static void deleteTree(Path target) {
        if (target == null || !Files.exists(target)) return;
        try (var paths = Files.walk(target)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
