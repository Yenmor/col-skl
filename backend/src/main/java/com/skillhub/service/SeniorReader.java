package com.skillhub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.dto.SkillDetailResponse;
import com.skillhub.dto.SkillSourcesSummary;
import com.skillhub.dto.SkillSummary;
import com.skillhub.dto.SkillTrust;
import com.skillhub.model.SeniorSkill;
import com.skillhub.model.SeniorSkillDetail;
import com.skillhub.repo.SeniorSkillRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * 启动时扫描 seniors-dir/，把符合七件套约定的目录落库到 senior_skills 表。
 * 详情页时按需读 SKILL.md / work.md / persona.md / manifest.json / meta.json。
 */
@Service
public class SeniorReader {

    public static final Set<String> REQUIRED_FILES = Set.of(
        "SKILL.md", "manifest.json", "meta.json",
        "work.md", "persona.md", "work_skill.md", "persona_skill.md"
    );

    private final Path seniorsDir;
    private final SeniorSkillRepository repo;
    private final ObjectMapper json;

    public SeniorReader(@Value("${skillhub.seniors-dir}") String seniorsPath,
                         SeniorSkillRepository repo,
                         ObjectMapper json) {
        this.seniorsDir = Paths.get(seniorsPath);
        this.repo = repo;
        this.json = json;
    }

    public void scanOnBoot() {
        try {
            Files.createDirectories(seniorsDir);
            Set<String> dirIds = new HashSet<>();
            try (Stream<Path> stream = Files.list(seniorsDir)) {
                stream.filter(Files::isDirectory).forEach(dir -> {
                    dirIds.add(dir.getFileName().toString());
                    ingestIfValid(dir);
                });
            }
            // 清理 DB 中目录已不存在的孤儿记录
            for (String dbId : repo.allIds()) {
                if (!dirIds.contains(dbId)) {
                    repo.deleteById(dbId);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("初始化扫描 seniors 目录失败", e);
        }
    }

    public SeniorSkill ingestIfValid(Path dir) {
        return ingestIfValid(dir, null, null);
    }

    public SeniorSkill ingestIfValid(Path dir, String ownerId, String visibility) {
        String id = dir.getFileName().toString();
        if (!REQUIRED_FILES.stream().allMatch(f -> Files.exists(dir.resolve(f)))) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> manifest = json.readValue(
                dir.resolve("manifest.json").toFile(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = json.readValue(
                dir.resolve("meta.json").toFile(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> identity = (Map<String, Object>) meta.getOrDefault("identity", Map.of());

            String avatar = String.valueOf(manifest.getOrDefault("avatar", ""));

            // college 为空时不拼 " · " 前缀，避免出现 " · 软件工程"
            String college = String.valueOf(identity.getOrDefault("college", ""));
            String major = String.valueOf(identity.getOrDefault("major", ""));
            String majorDisplay = college == null || college.isBlank() || "null".equals(college)
                ? major
                : college + " · " + major;

            // source 优先取 manifest.source（distilled/manual），缺省 manual
            String source = String.valueOf(manifest.getOrDefault("source", "manual"));
            if (source == null || source.isBlank() || "null".equals(source)) source = "manual";

            SeniorSkill existing = repo.findById(id).orElse(null);
            String resolvedOwner = ownerId != null ? ownerId
                : value(manifest, "ownerId", value(meta, "owner_id",
                    value(identity, "user_id", existing == null ? null : existing.ownerId())));
            String bundleVisibility = value(manifest, "visibility",
                value(meta, "visibility", null));
            String resolvedVisibility = visibility != null ? visibility
                : bundleVisibility != null ? bundleVisibility
                : existing == null ? SeniorSkill.PUBLIC : existing.visibility();
            String summary = value(manifest, "description", existing == null ? "" : existing.summary());
            String version = value(manifest, "version", existing == null ? "v1" : existing.version());
            String layerId = value(manifest, "layerId", value(manifest, "layer_id",
                existing == null ? domainToLayer(value(manifest, "domain", "")) : existing.layerId()));
            List<String> tags = stringList(manifest.get("triggers"));
            Instant now = Instant.now();
            SeniorSkill s = new SeniorSkill(
                id,
                String.valueOf(manifest.getOrDefault("name", id)),
                String.valueOf(identity.getOrDefault("school", "")),
                majorDisplay,
                String.valueOf(identity.getOrDefault("year_graduated", "")),
                String.valueOf(manifest.getOrDefault("domain", "")),
                avatar,
                source,
                existing == null ? now : existing.createdAt(),
                resolvedOwner,
                resolvedVisibility,
                layerId,
                summary,
                version,
                tags,
                now
            );
            return repo.save(s);
        } catch (IOException e) {
            System.err.println("ingest 失败 " + dir + ": " + e.getMessage());
            return null;
        }
    }

    public SeniorSkillDetail loadDetail(String id) {
        repo.findById(id).orElseThrow(() ->
            new NoSuchElementException("未找到学长.Skill: " + id));
        Path dir = seniorsDir.resolve(id);
        return new SeniorSkillDetail(
            repo.findById(id).get(),
            readText(dir, "SKILL.md"),
            readText(dir, "work.md"),
            readText(dir, "persona.md"),
            readText(dir, "manifest.json"),
            readText(dir, "meta.json")
        );
    }

    public SkillDetailResponse loadV1Detail(String id, String userId) {
        SeniorSkill skill = repo.findAccessibleById(id, userId).orElseThrow(() ->
            new NoSuchElementException("未找到 Skill: " + id));
        Path dir = checkedSkillDir(id);
        SkillSummary summary = SkillSummary.from(skill, trustFor(id));
        return SkillDetailResponse.from(
            summary,
            readText(dir, "SKILL.md"),
            readText(dir, "work.md"),
            readText(dir, "persona.md"),
            readJson(dir, "manifest.json"),
            readJson(dir, "meta.json"),
            sourcesSummary(dir));
    }

    public SkillTrust trustFor(String id) {
        Path dir = checkedSkillDir(id);
        String skill = readText(dir, "SKILL.md");
        JsonNode manifest = readJson(dir, "manifest.json");
        JsonNode meta = readJson(dir, "meta.json");
        SkillSourcesSummary sources = sourcesSummary(dir);

        int packageScore = (int) Math.round(REQUIRED_FILES.stream()
            .filter(name -> Files.isRegularFile(dir.resolve(name)))
            .count() * 100.0 / REQUIRED_FILES.size());
        int sourceScore = 0;
        if (sources.available()) {
            int base = "PLATFORM_VERIFIED".equals(sources.verification()) ? 35 : 15;
            int ceiling = "PLATFORM_VERIFIED".equals(sources.verification()) ? 100 : 60;
            sourceScore = Math.min(ceiling,
                base + sources.mappingCount() * 8 + Math.min(30, sources.threadCount() * 10));
        }
        int methodParts = countPresent(skill, List.of("## 运行契约", "## 执行流程", "## 决策节点"));
        int methodScore = Math.min(100, methodParts * 25 + Math.min(25, countNumberedSteps(skill) * 5));
        int boundaryHits = countContains(skill, List.of("## 能力边界", "不知道", "不适用", "核对", "不确定", "风险"));
        int boundaryScore = Math.min(100, boundaryHits * 18);

        int campusSignals = 0;
        String corpus = skill + "\n" + manifest.toString() + "\n" + meta.toString();
        for (String signal : List.of("学校", "学院", "专业", "课程", "年级", "校园", "大学", "保研", "竞赛", "科研")) {
            if (corpus.contains(signal)) campusSignals++;
        }
        if (!meta.path("identity").path("school").asText("").isBlank()) campusSignals += 2;
        if (!meta.path("identity").path("major").asText("").isBlank()) campusSignals += 2;
        int campusScore = Math.min(100, campusSignals * 9);
        int overall = (campusScore + sourceScore + methodScore + boundaryScore + packageScore) / 5;
        return new SkillTrust(campusScore, sourceScore, methodScore, boundaryScore, packageScore, overall);
    }

    public SkillSourcesSummary sourcesSummary(String id) {
        return sourcesSummary(checkedSkillDir(id));
    }

    private SkillSourcesSummary sourcesSummary(Path dir) {
        Path path = dir.resolve("sources.json");
        if (!Files.isRegularFile(path)) {
            return new SkillSourcesSummary(false, 0, 0, List.of(), "sources.json 缺失", "MISSING");
        }
        JsonNode root = readJson(dir, "sources.json");
        if (root.isMissingNode() || root.isNull()) {
            return new SkillSourcesSummary(false, 0, 0, List.of(), "sources.json 无法解析", "MISSING");
        }
        LinkedHashSet<String> evidence = new LinkedHashSet<>();
        LinkedHashSet<String> threads = new LinkedHashSet<>();
        LinkedHashSet<String> mappings = new LinkedHashSet<>();
        root.path("fragment_ids").forEach(node -> {
            if (node.isTextual() && !node.asText().isBlank()) evidence.add(node.asText());
        });
        root.path("fragments").forEach(node -> {
            String fragmentId = node.path("fragment_id").asText("");
            String threadId = node.path("thread_id").asText("");
            if (!fragmentId.isBlank()) evidence.add(fragmentId);
            if (!threadId.isBlank()) threads.add(threadId);
            if (!fragmentId.isBlank() && !threadId.isBlank()) mappings.add(fragmentId + "\u0000" + threadId);
        });
        if (mappings.isEmpty() || evidence.isEmpty() || threads.isEmpty()) {
            return new SkillSourcesSummary(false, 0, 0, List.of(), "sources.json 没有有效来源映射", "MISSING");
        }
        String verification = "PLATFORM_VERIFIED".equals(root.path("verification").asText())
            ? "PLATFORM_VERIFIED" : "PACKAGE_DECLARED";
        return new SkillSourcesSummary(true, mappings.size(), threads.size(),
            evidence.stream().limit(100).toList(), null, verification);
    }

    /** 列出所有「(id, SKILL.md 摘录的 first 280 chars)」，供 LLM 选人。 */
    public List<SeniorCandidate> listCandidates() {
        List<SeniorSkill> all = repo.listPublic(null, null, null);
        List<SeniorCandidate> out = new ArrayList<>();
        for (SeniorSkill s : all) {
            Path dir = seniorsDir.resolve(s.id());
            String head = "";
            try {
                String md = readText(dir, "SKILL.md");
                head = md.length() > 280 ? md.substring(0, 280) + "…" : md;
            } catch (Exception ignored) { }
            out.add(new SeniorCandidate(s.id(), s.name(), s.school(), s.major(),
                String.valueOf(s.year()), s.domain(), head));
        }
        return out;
    }

    public String loadSkillMd(String id) {
        return readText(checkedSkillDir(id), "SKILL.md");
    }

    public Path avatarPath(String id, String filename) {
        if (filename == null || filename.isBlank()) return null;
        Path p = checkedSkillDir(id).resolve(filename).normalize();
        if (!p.startsWith(checkedSkillDir(id))) return null;
        return Files.exists(p) ? p : null;
    }

    public Path seniorsDir() { return seniorsDir; }

    private String readText(Path dir, String name) {
        try {
            return Files.readString(dir.resolve(name));
        } catch (IOException e) {
            return "";
        }
    }

    public Path checkedSkillDir(String id) {
        if (id == null || !id.matches("[a-z0-9][a-z0-9-]{1,63}")) {
            throw new IllegalArgumentException("Skill ID 不合法");
        }
        Path base = seniorsDir.toAbsolutePath().normalize();
        Path dir = base.resolve(id).normalize();
        if (!dir.startsWith(base)) throw new IllegalArgumentException("Skill 路径不合法");
        return dir;
    }

    private JsonNode readJson(Path dir, String name) {
        try {
            return json.readTree(dir.resolve(name).toFile());
        } catch (Exception ignored) {
            return json.missingNode();
        }
    }

    private static int countPresent(String text, List<String> markers) {
        return (int) markers.stream().filter(text::contains).count();
    }

    private static int countContains(String text, List<String> markers) {
        return (int) markers.stream().filter(text::contains).count();
    }

    private static int countNumberedSteps(String text) {
        int count = 0;
        for (String line : text.split("\\R")) {
            if (line.matches("\\s*\\d+[.、].+")) count++;
        }
        return count;
    }

    private static String value(Map<String, Object> map, String key, String fallback) {
        Object raw = map.get(key);
        if (raw == null) return fallback;
        String value = raw.toString().trim();
        return value.isBlank() || "null".equalsIgnoreCase(value) ? fallback : value;
    }

    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> values)) return List.of();
        return values.stream().filter(Objects::nonNull).map(Object::toString)
            .map(String::trim).filter(s -> !s.isBlank()).distinct().toList();
    }

    private static String domainToLayer(String domain) {
        if (domain == null) return null;
        if (domain.contains("科研")) return "research";
        if (domain.contains("竞赛")) return "competition";
        if (domain.contains("技能") || domain.contains("求职") || domain.contains("实习")) return "skills";
        return "study";
    }

    /** 给 LLM 选人用的最小投影 */
    public record SeniorCandidate(
        String id, String name, String school, String major,
        String year, String domain, String skillHead
    ) {}
}
