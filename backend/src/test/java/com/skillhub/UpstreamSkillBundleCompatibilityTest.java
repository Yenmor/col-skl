package com.skillhub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.model.SeniorSkill;
import com.skillhub.service.SeniorIngestService;
import com.skillhub.service.SeniorReader;
import com.skillhub.support.InMemorySeniorSkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpstreamSkillBundleCompatibilityTest {
    @TempDir Path temp;

    @Test
    void everyBundledUpstreamSkillPassesThePublicImportContract() throws Exception {
        Path sourceRoot = bundledSkillsRoot();
        List<Path> bundles;
        try (var paths = Files.list(sourceRoot)) {
            bundles = paths.filter(Files::isDirectory)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        }
        assertFalse(bundles.isEmpty(), "仓库至少应包含一个内置 Skill");

        var json = new ObjectMapper().findAndRegisterModules();
        var repo = new InMemorySeniorSkillRepository();
        var reader = new SeniorReader(temp.toString(), repo, json);
        var ingest = new SeniorIngestService(reader, repo, json);

        for (Path bundle : bundles) {
            String id = bundle.getFileName().toString();
            for (String required : SeniorReader.REQUIRED_FILES) {
                Path file = bundle.resolve(required);
                assertTrue(Files.isRegularFile(file) && Files.size(file) > 0,
                    id + " 缺少非空七件套文件 " + required);
            }

            var meta = json.readTree(bundle.resolve("meta.json").toFile());
            assertEquals(id, meta.path("skill_id").asText(), id + " 的 meta.skill_id 应与目录一致");
            var manifest = json.readTree(bundle.resolve("manifest.json").toFile());
            for (String key : List.of("id", "skill_id")) {
                String declaredId = manifest.path(key).asText("");
                assertTrue(declaredId.isBlank() || id.equals(declaredId),
                    id + " 的 manifest." + key + " 与目录不一致");
            }

            String skillMd = Files.readString(bundle.resolve("SKILL.md"));
            for (String heading : List.of(
                "## 触发条件", "## 运行契约", "## 执行流程", "## 决策节点", "## 能力边界")) {
                assertTrue(skillMd.contains(heading), id + " 缺少结构 " + heading);
            }

            SeniorSkill imported = ingest.uploadPublic(
                id + ".zip", new ByteArrayInputStream(zipBundle(bundle)), null);
            assertEquals(id, imported.id());
            assertEquals(SeniorSkill.PUBLIC, imported.visibility());
            assertFalse(imported.summary().isBlank());
            assertFalse(reader.loadV1Detail(id, null).skillMd().isBlank());
        }

        assertEquals(bundles.size(), repo.listPublic(null, null, null).size());
        assertEquals(bundles.size(), reader.listCandidates().size());
    }

    private static Path bundledSkillsRoot() {
        Path workingDir = Path.of("").toAbsolutePath().normalize();
        Path fromBackend = workingDir.resolve("data/seniors");
        if (Files.isDirectory(fromBackend)) return fromBackend;
        Path fromRepository = workingDir.resolve("backend/data/seniors");
        assertTrue(Files.isDirectory(fromRepository), "找不到 backend/data/seniors");
        return fromRepository;
    }

    private static byte[] zipBundle(Path bundle) throws Exception {
        String id = bundle.getFileName().toString();
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output); var paths = Files.walk(bundle)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                String relative = bundle.relativize(file).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(id + "/" + relative));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
