package com.skillhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final Set<String> REQUIRED_FILES = Set.of(
        "SKILL.md", "manifest.json", "meta.json",
        "work.md", "persona.md", "work_skill.md", "persona_skill.md"
    );

    private final Path seniorsDir;
    private final SeniorSkillRepository repo;
    private final ObjectMapper json = new ObjectMapper();

    public SeniorReader(@Value("${skillhub.seniors-dir}") String seniorsPath,
                         SeniorSkillRepository repo) {
        this.seniorsDir = Paths.get(seniorsPath);
        this.repo = repo;
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

            SeniorSkill s = new SeniorSkill(
                id,
                String.valueOf(manifest.getOrDefault("name", id)),
                String.valueOf(identity.getOrDefault("school", "")),
                majorDisplay,
                String.valueOf(identity.getOrDefault("year_graduated", "")),
                String.valueOf(manifest.getOrDefault("domain", "")),
                avatar,
                source,
                Instant.now()
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

    /** 列出所有「(id, SKILL.md 摘录的 first 280 chars)」，供 LLM 选人。 */
    public List<SeniorCandidate> listCandidates() {
        List<SeniorSkill> all = repo.list(null, null);
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
        return readText(seniorsDir.resolve(id), "SKILL.md");
    }

    public Path avatarPath(String id, String filename) {
        if (filename == null || filename.isBlank()) return null;
        Path p = seniorsDir.resolve(id).resolve(filename);
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

    /** 给 LLM 选人用的最小投影 */
    public record SeniorCandidate(
        String id, String name, String school, String major,
        String year, String domain, String skillHead
    ) {}
}
