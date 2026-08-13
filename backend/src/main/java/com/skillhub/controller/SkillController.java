package com.skillhub.controller;

import com.skillhub.config.BaseController;
import com.skillhub.dto.SkillDetailResponse;
import com.skillhub.dto.SkillListResponse;
import com.skillhub.dto.SkillSummary;
import com.skillhub.dto.RecallRequest;
import com.skillhub.dto.SkillRecallItem;
import com.skillhub.service.SeniorIngestService;
import com.skillhub.service.SkillCatalogService;
import com.skillhub.service.SkillRecallService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillController extends BaseController {

    private final SkillRecallService recallService;
    private final SkillCatalogService catalog;
    private final SeniorIngestService ingest;

    public SkillController(SkillRecallService recallService,
                           SkillCatalogService catalog,
                           SeniorIngestService ingest) {
        this.recallService = recallService;
        this.catalog = catalog;
        this.ingest = ingest;
    }

    @GetMapping
    public SkillListResponse list(@RequestParam(required = false) String domain,
                                  @RequestParam(required = false) String school,
                                  @RequestParam(required = false, name = "q") String query) {
        return catalog.publicList(domain, school, query);
    }

    @GetMapping("/{id}")
    public SkillDetailResponse detail(@PathVariable String id,
                                      @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return catalog.detail(id, userId);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, SkillSummary>> upload(
            @RequestHeader("X-User-Id") String userId,
            @RequestPart("file") FilePart file) {
        return ingest.uploadPublic(file, userId)
            .map(skill -> Map.of("item", catalog.summary(skill)));
    }

    @GetMapping("/{id}/bundle")
    public ResponseEntity<byte[]> bundle(@PathVariable String id,
                                         @RequestHeader(value = "X-User-Id", required = false) String userId) {
        byte[] archive = catalog.bundle(id, userId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(id + ".zip").build());
        headers.setContentLength(archive.length);
        return ResponseEntity.ok().headers(headers).body(archive);
    }

    @PostMapping("/recall")
    public List<SkillRecallItem> recall(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                        @RequestBody RecallRequest body) {
        if (body.query() == null || body.query().isBlank()) {
            throw badRequest(com.skillhub.dto.ErrorCode.SKILL_VALIDATION_FAILED, "query 不能为空");
        }
        int topK = body.topK() != null ? body.topK() : 3;
        return recallService.recall(body.query(), topK, body.domain(), body.school());
    }
}
