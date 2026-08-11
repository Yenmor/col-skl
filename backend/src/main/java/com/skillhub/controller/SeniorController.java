package com.skillhub.controller;

import com.skillhub.model.SeniorSkill;
import com.skillhub.model.SeniorSkillDetail;
import com.skillhub.repo.SeniorSkillRepository;
import com.skillhub.service.SeniorIngestService;
import com.skillhub.service.SeniorReader;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/seniors")
public class SeniorController {

    private final SeniorSkillRepository repo;
    private final SeniorReader reader;
    private final SeniorIngestService ingest;

    public SeniorController(SeniorSkillRepository repo,
                            SeniorReader reader,
                            SeniorIngestService ingest) {
        this.repo = repo;
        this.reader = reader;
        this.ingest = ingest;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String domain,
                                    @RequestParam(required = false) String school) {
        List<SeniorSkill> items = repo.list(empty(domain), empty(school));
        // 同一个角度两个值（domain 集合）抽出来做 facet
        Set<String> domains = items.stream().map(SeniorSkill::domain).collect(java.util.stream.Collectors.toSet());
        Set<String> schools = items.stream().map(SeniorSkill::school).collect(java.util.stream.Collectors.toSet());
        return Map.of(
            "items", items,
            "facets", Map.of("domains", domains, "schools", schools)
        );
    }

    @GetMapping("/{id}")
    public SeniorSkillDetail detail(@PathVariable String id) {
        return reader.loadDetail(id);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestPart("file") MultipartFile file) {
        String id = ingest.upload(file);
        SeniorSkill saved = reader.ingestIfValid(reader.seniorsDir().resolve(id));
        return Map.of("id", id, "saved", saved != null);
    }

    @GetMapping("/{id}/avatar")
    public ResponseEntity<Resource> avatar(@PathVariable String id,
                                           @RequestParam(defaultValue = "avatar.svg") String file) {
        // 先看 meta.avatar，没有就回头在 seniors/<id>/ 下查找 file= 参数
        Path p = reader.avatarPath(id, file);
        if (p == null) {
            // 兜底：以 id.svg 找
            p = reader.avatarPath(id, id + ".svg");
        }
        if (p == null) return ResponseEntity.notFound().build();
        Resource res = new PathResource(p);
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.CONTENT_TYPE, "image/svg+xml");
        return new ResponseEntity<>(res, h, 200);
    }

    private static String empty(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
