package com.skillhub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.config.GlobalExceptionHandler;
import com.skillhub.controller.SeniorController;
import com.skillhub.controller.SkillController;
import com.skillhub.service.SeniorIngestService;
import com.skillhub.service.SeniorReader;
import com.skillhub.service.SkillCatalogService;
import com.skillhub.service.SkillRecallService;
import com.skillhub.support.InMemorySeniorSkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class SkillImportControllerTest {
    @TempDir Path temp;

    @Test
    void multipartInvalidZipReachesBusinessValidationInsteadOfReturning415() throws Exception {
        Harness harness = harness();

        MultipartBodyBuilder body = multipart("bad.zip", "not a zip".getBytes(StandardCharsets.UTF_8));
        harness.client.post().uri("/api/v1/skills/import")
            .header("X-User-Id", "owner-1")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(body.build()))
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.error.code").isEqualTo("SKILL_IMPORT_INVALID");
    }

    @Test
    void multipartValidBundleImportsAsPublicSkill() throws Exception {
        Harness harness = harness();
        String id = "webflux-import";
        Map<String, String> entries = new LinkedHashMap<>();
        SkillIngestAndPrivacyTest.validFiles(id)
            .forEach((name, content) -> entries.put(id + "/" + name, content));

        MultipartBodyBuilder body = multipart("skill.zip", zip(entries));
        harness.client.post().uri("/api/v1/skills/import")
            .header("X-User-Id", "owner-1")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(body.build()))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.item.id").isEqualTo(id)
            .jsonPath("$.item.visibility").isEqualTo("PUBLIC")
            .jsonPath("$.item.ownerId").isEqualTo("owner-1");

        assertEquals(id, harness.repo.findById(id).orElseThrow().id());
    }

    @Test
    void legacyMultipartUploadRemainsCompatibleWithWebFlux() throws Exception {
        Harness harness = harness();
        String id = "legacy-webflux";
        Map<String, String> entries = new LinkedHashMap<>();
        SkillIngestAndPrivacyTest.validFiles(id)
            .forEach((name, content) -> entries.put(id + "/" + name, content));

        MultipartBodyBuilder body = multipart("legacy.zip", zip(entries));
        harness.client.post().uri("/api/seniors/upload")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(body.build()))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(id)
            .jsonPath("$.saved").isEqualTo(true);
    }

    private Harness harness() {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        InMemorySeniorSkillRepository repo = new InMemorySeniorSkillRepository();
        SeniorReader reader = new SeniorReader(temp.toString(), repo, json);
        SeniorIngestService ingest = new SeniorIngestService(reader, repo, json);
        SkillCatalogService catalog = new SkillCatalogService(repo, reader);
        SkillController controller = new SkillController(mock(SkillRecallService.class), catalog, ingest);
        SeniorController legacy = new SeniorController(repo, reader, ingest);
        WebTestClient client = WebTestClient.bindToController(controller, legacy)
            .controllerAdvice(new GlobalExceptionHandler())
            .build();
        return new Harness(client, repo);
    }

    private static MultipartBodyBuilder multipart(String filename, byte[] content) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource(content) {
            @Override public String getFilename() {
                return filename;
            }
        }).contentType(MediaType.parseMediaType("application/zip"));
        return body;
    }

    private static byte[] zip(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private record Harness(WebTestClient client, InMemorySeniorSkillRepository repo) {}
}
