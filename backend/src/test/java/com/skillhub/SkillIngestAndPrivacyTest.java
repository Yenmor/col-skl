package com.skillhub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.config.ApiException;
import com.skillhub.dto.ErrorCode;
import com.skillhub.model.SeniorSkill;
import com.skillhub.service.SeniorIngestService;
import com.skillhub.service.SeniorReader;
import com.skillhub.support.InMemorySeniorSkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SkillIngestAndPrivacyTest {
    @TempDir Path temp;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void validUploadIsPublicOwnedAndComplete() throws Exception {
        var repo = new InMemorySeniorSkillRepository();
        var reader = new SeniorReader(temp.toString(), repo, json);
        var ingest = new SeniorIngestService(reader, repo, json);

        SeniorSkill skill = ingest.uploadPublic(zip("real-skill", validFiles("real-skill")), "owner-1");

        assertEquals(SeniorSkill.PUBLIC, skill.visibility());
        assertEquals("owner-1", skill.ownerId());
        assertTrue(Files.isRegularFile(temp.resolve("real-skill/SKILL.md")));
        assertEquals(1, repo.listPublic(null, null, null).size());

        repo.clear();
        reader.scanOnBoot();
        SeniorSkill restored = repo.findById("real-skill").orElseThrow();
        assertEquals(SeniorSkill.PUBLIC, restored.visibility());
        assertEquals("owner-1", restored.ownerId(), "caller ownership must survive DB rebuild");
    }

    @Test
    void rejectsZipSlipAndLeavesNoFilesOrRows() throws Exception {
        var repo = new InMemorySeniorSkillRepository();
        var reader = new SeniorReader(temp.toString(), repo, json);
        var ingest = new SeniorIngestService(reader, repo, json);
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("bad-skill/../../escaped.txt", "bad");

        ApiException error = assertThrows(ApiException.class,
            () -> ingest.uploadPublic(zipEntries(entries), "owner-1"));

        assertEquals(ErrorCode.SKILL_IMPORT_INVALID, error.errorCode());
        assertFalse(Files.exists(temp.resolve("escaped.txt")));
        assertTrue(repo.allIds().isEmpty());
        try (var files = Files.list(temp)) {
            assertTrue(files.findAny().isEmpty());
        }
    }

    @Test
    void rejectsMissingFilesBadJsonAndIdMismatchAtomically() throws Exception {
        var repo = new InMemorySeniorSkillRepository();
        var reader = new SeniorReader(temp.toString(), repo, json);
        var ingest = new SeniorIngestService(reader, repo, json);

        Map<String, String> missing = validFiles("missing-skill");
        missing.remove("persona_skill.md");
        assertEquals(ErrorCode.SKILL_IMPORT_INVALID,
            assertThrows(ApiException.class, () -> ingest.uploadPublic(zip("missing-skill", missing), "u")).errorCode());

        Map<String, String> invalidJson = validFiles("bad-json");
        invalidJson.put("manifest.json", "{");
        assertEquals(ErrorCode.SKILL_IMPORT_INVALID,
            assertThrows(ApiException.class, () -> ingest.uploadPublic(zip("bad-json", invalidJson), "u")).errorCode());

        Map<String, String> mismatch = validFiles("mismatch");
        mismatch.put("meta.json", "{\"skill_id\":\"other\",\"identity\":{}}");
        assertEquals(ErrorCode.SKILL_IMPORT_INVALID,
            assertThrows(ApiException.class, () -> ingest.uploadPublic(zip("mismatch", mismatch), "u")).errorCode());

        assertTrue(repo.allIds().isEmpty());
        try (var files = Files.list(temp)) { assertTrue(files.findAny().isEmpty()); }
    }

    @Test
    void rejectsInvalidSourceMappingsAtomically() throws Exception {
        var repo = new InMemorySeniorSkillRepository();
        var reader = new SeniorReader(temp.toString(), repo, json);
        var ingest = new SeniorIngestService(reader, repo, json);

        Map<String, String> wrongSkill = validFiles("wrong-source-id");
        wrongSkill.put("sources.json", validSources("other-skill"));
        assertEquals(ErrorCode.SKILL_IMPORT_INVALID, assertThrows(ApiException.class,
            () -> ingest.uploadPublic(zip("wrong-source-id", wrongSkill), "u")).errorCode());

        Map<String, String> duplicateThread = validFiles("duplicate-thread");
        duplicateThread.put("sources.json", """
            {"skill_id":"duplicate-thread","fragment_ids":["frag_one","frag_two"],"fragments":[
              {"fragment_id":"frag_one","thread_id":"thread-1","source_message_ids":["message-1"]},
              {"fragment_id":"frag_two","thread_id":"thread-1","source_message_ids":["message-2"]}
            ]}
            """);
        assertEquals(ErrorCode.SKILL_IMPORT_INVALID, assertThrows(ApiException.class,
            () -> ingest.uploadPublic(zip("duplicate-thread", duplicateThread), "u")).errorCode());

        Map<String, String> foreignTarget = validFiles("foreign-target");
        foreignTarget.put("sources.json", """
            {"skill_id":"foreign-target","fragment_ids":["frag_one"],"fragments":[
              {"fragment_id":"frag_one","thread_id":"thread-1","source_message_ids":["message-1"],
               "target_message_ids":["message-from-another-thread"]}
            ]}
            """);
        assertEquals(ErrorCode.SKILL_IMPORT_INVALID, assertThrows(ApiException.class,
            () -> ingest.uploadPublic(zip("foreign-target", foreignTarget), "u")).errorCode());

        assertTrue(repo.allIds().isEmpty());
        try (var files = Files.list(temp)) { assertTrue(files.findAny().isEmpty()); }
    }

    @Test
    void rescanRestoresPrivateVisibilityAndOwnerFromBundle() throws Exception {
        var repo = new InMemorySeniorSkillRepository();
        var reader = new SeniorReader(temp.toString(), repo, json);
        Path dir = temp.resolve("private-draft");
        Files.createDirectories(dir);
        Map<String, String> files = validFiles("private-draft");
        files.put("manifest.json", """
            {"id":"private-draft","name":"我的草稿","domain":"技能","description":"私有",
             "triggers":["项目"],"source":"distilled","version":"v0.1","ownerId":"owner-1","visibility":"PRIVATE"}
            """);
        files.put("meta.json", """
            {"skill_id":"private-draft","owner_id":"owner-1","visibility":"PRIVATE",
             "identity":{"user_id":"owner-1"},"lifecycle":{"version":"v0.1"}}
            """);
        for (var entry : files.entrySet()) Files.writeString(dir.resolve(entry.getKey()), entry.getValue());

        reader.scanOnBoot();
        repo.clear();
        reader.scanOnBoot();

        SeniorSkill restored = repo.findById("private-draft").orElseThrow();
        assertEquals(SeniorSkill.PRIVATE, restored.visibility());
        assertEquals("owner-1", restored.ownerId());
        assertTrue(repo.listPublic(null, null, null).isEmpty());
        assertTrue(repo.findAccessibleById("private-draft", "owner-1").isPresent());
        assertTrue(repo.findAccessibleById("private-draft", "other").isEmpty());
    }

    @Test
    void emptySourcesAreExplicitlyUnavailableWithZeroTraceability() throws Exception {
        var repo = new InMemorySeniorSkillRepository();
        var reader = new SeniorReader(temp.toString(), repo, json);
        Path dir = temp.resolve("empty-sources");
        Files.createDirectories(dir);
        Map<String, String> files = validFiles("empty-sources");
        files.put("sources.json", "{}");
        for (var entry : files.entrySet()) Files.writeString(dir.resolve(entry.getKey()), entry.getValue());
        reader.ingestIfValid(dir);

        assertFalse(reader.sourcesSummary("empty-sources").available());
        assertEquals(0, reader.sourcesSummary("empty-sources").mappingCount());
        assertEquals(0, reader.trustFor("empty-sources").sourceTraceability());
    }

    @Test
    void packageDeclaredDuplicateMappingsDoNotInflateTraceability() throws Exception {
        var repo = new InMemorySeniorSkillRepository();
        var reader = new SeniorReader(temp.toString(), repo, json);
        Path dir = temp.resolve("duplicate-sources");
        Files.createDirectories(dir);
        Map<String, String> files = validFiles("duplicate-sources");
        files.put("sources.json", """
            {"fragment_ids":["frag_0123456789"],"fragments":[
              {"fragment_id":"frag_0123456789","thread_id":"thread-1"},
              {"fragment_id":"frag_0123456789","thread_id":"thread-1"},
              {"fragment_id":"frag_0123456789","thread_id":"thread-1"}
            ]}
            """);
        for (var entry : files.entrySet()) Files.writeString(dir.resolve(entry.getKey()), entry.getValue());
        reader.ingestIfValid(dir);

        assertEquals("PACKAGE_DECLARED", reader.sourcesSummary("duplicate-sources").verification());
        assertEquals(1, reader.sourcesSummary("duplicate-sources").mappingCount());
        assertEquals(1, reader.sourcesSummary("duplicate-sources").threadCount());
        assertTrue(reader.trustFor("duplicate-sources").sourceTraceability() <= 60);
    }

    private MockMultipartFile zip(String id, Map<String, String> files) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        files.forEach((name, content) -> entries.put(id + "/" + name, content));
        return zipEntries(entries);
    }

    private MockMultipartFile zipEntries(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", "skill.zip", "application/zip", output.toByteArray());
    }

    static Map<String, String> validFiles(String id) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("manifest.json", "{\"id\":\"" + id + "\",\"name\":\"真实 Skill\",\"domain\":\"技能\",\"description\":\"真实摘要\",\"triggers\":[\"项目\"],\"version\":\"v1\"}");
        files.put("meta.json", "{\"skill_id\":\"" + id + "\",\"identity\":{\"school\":\"大学\",\"major\":\"软件\"}}");
        files.put("SKILL.md", "# Skill\n\n## 触发条件\n项目\n\n## 运行契约\n只用证据\n\n## 执行流程\n1. 做事\n\n## 决策节点\n- 判断\n\n## 能力边界\n- 不知道时核对\n");
        files.put("work.md", "# Work\n真实方法");
        files.put("persona.md", "# Persona\n表达方式");
        files.put("work_skill.md", "# Work Skill\n真实方法");
        files.put("persona_skill.md", "# Persona Skill\n表达方式");
        files.put("sources.json", validSources(id));
        return files;
    }

    private static String validSources(String id) {
        return "{\"skill_id\":\"" + id
            + "\",\"fragment_ids\":[\"frag_0123456789\"],\"fragments\":[{\"fragment_id\":\"frag_0123456789\""
            + ",\"thread_id\":\"thread-1\",\"source_message_ids\":[\"message-1\"]}]}";
    }
}
