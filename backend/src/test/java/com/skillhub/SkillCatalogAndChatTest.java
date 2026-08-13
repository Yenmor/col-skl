package com.skillhub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.config.ApiException;
import com.skillhub.config.LlmProperties;
import com.skillhub.dto.ErrorCode;
import com.skillhub.model.SeniorSkill;
import com.skillhub.repo.SeniorFragmentRepository;
import com.skillhub.service.ChatOrchestrator;
import com.skillhub.service.SeniorReader;
import com.skillhub.service.SkillCatalogService;
import com.skillhub.service.llm.LlmClient;
import com.skillhub.service.llm.MockLlmClient;
import com.skillhub.support.InMemorySeniorSkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class SkillCatalogAndChatTest {
    @TempDir Path temp;

    @Test
    void bundleDownloadContainsRealFilesAndHonorsPrivateOwnership() throws Exception {
        var repo = new InMemorySeniorSkillRepository();
        var reader = new SeniorReader(temp.toString(), repo, new ObjectMapper());
        Path dir = writeBundle("private-bundle", "owner-1", SeniorSkill.PRIVATE);
        reader.ingestIfValid(dir, "owner-1", SeniorSkill.PRIVATE);
        var catalog = new SkillCatalogService(repo, reader);

        byte[] archive = catalog.bundle("private-bundle", "owner-1");
        Set<String> entries = new java.util.LinkedHashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.add(entry.getName());
        }

        assertTrue(entries.contains("private-bundle/SKILL.md"));
        assertTrue(entries.contains("private-bundle/manifest.json"));
        assertTrue(entries.contains("private-bundle/sources.json"));
        ApiException forbidden = assertThrows(ApiException.class,
            () -> catalog.bundle("private-bundle", "other"));
        assertEquals(ErrorCode.SKILL_FORBIDDEN, forbidden.errorCode());
    }

    @Test
    void fixedTargetChatUsesOnlyRequestedAccessibleSkill() throws Exception {
        var repo = new InMemorySeniorSkillRepository();
        var reader = new SeniorReader(temp.toString(), repo, new ObjectMapper());
        reader.ingestIfValid(writeBundle("public-one", null, SeniorSkill.PUBLIC), null, SeniorSkill.PUBLIC);
        reader.ingestIfValid(writeBundle("private-one", "owner-1", SeniorSkill.PRIVATE), "owner-1", SeniorSkill.PRIVATE);
        CapturingLlm llm = new CapturingLlm();
        LlmProperties properties = new LlmProperties();
        properties.setProvider("deepseek");
        var orchestrator = new ChatOrchestrator(
            repo, reader, new EmptyFragments(), llm, new MockLlmClient(), properties);

        var answers = orchestrator.orchestrate("请回答", null, "private-one", "owner-1");

        assertEquals(1, answers.size());
        assertEquals("private-one", answers.get(0).seniorId());
        assertTrue(llm.systemPrompt.contains("private-one method"));
        assertFalse(llm.systemPrompt.contains("public-one method"));
        ApiException forbidden = assertThrows(ApiException.class,
            () -> orchestrator.orchestrate("请回答", null, "private-one", "other"));
        assertEquals(ErrorCode.SKILL_FORBIDDEN, forbidden.errorCode());
    }

    private Path writeBundle(String id, String ownerId, String visibility) throws Exception {
        Path dir = temp.resolve(id);
        Files.createDirectories(dir);
        var files = SkillIngestAndPrivacyTest.validFiles(id);
        files.put("manifest.json", "{\"id\":\"" + id + "\",\"name\":\"" + id
            + "\",\"domain\":\"技能\",\"description\":\"摘要\",\"triggers\":[\"项目\"],"
            + "\"version\":\"v1\",\"ownerId\":\"" + (ownerId == null ? "" : ownerId)
            + "\",\"visibility\":\"" + visibility + "\"}");
        files.put("meta.json", "{\"skill_id\":\"" + id + "\",\"owner_id\":\""
            + (ownerId == null ? "" : ownerId) + "\",\"visibility\":\"" + visibility
            + "\",\"identity\":{\"user_id\":\"" + (ownerId == null ? "" : ownerId) + "\"}}");
        files.put("SKILL.md", "# " + id + " method\n\n## 触发条件\n项目\n\n## 运行契约\n证据\n\n## 执行流程\n1. 做\n\n## 决策节点\n- 判断\n\n## 能力边界\n- 不知道\n");
        for (var entry : files.entrySet()) Files.writeString(dir.resolve(entry.getKey()), entry.getValue());
        return dir;
    }

    private static final class CapturingLlm implements LlmClient {
        String systemPrompt = "";
        @Override public Mono<String> complete(String systemPrompt, String userMessage) {
            this.systemPrompt = systemPrompt;
            return Mono.just("fixed answer");
        }
    }

    private static final class EmptyFragments implements SeniorFragmentRepository {
        @Override public com.skillhub.model.SeniorFragment save(com.skillhub.model.SeniorFragment fragment) { return fragment; }
        @Override public List<com.skillhub.model.SeniorFragment> listBySenior(String seniorId, int limit) { return List.of(); }
        @Override public List<com.skillhub.model.SeniorFragment> listAll(String seniorId) { return List.of(); }
    }
}
