package com.skillhub.controller;

import com.skillhub.model.CommunityPost;
import com.skillhub.repo.CommunityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/community")
public class CommunityController {

    private final CommunityRepository repo;
    private static final List<String> PALETTE = List.of(
        "#fde0e6", "#dceafd", "#e5f4dc", "#f9eedc", "#ece4fa", "#fde6d4"
    );

    public CommunityController(CommunityRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/posts")
    public ResponseEntity<Map<String, Object>> posts(@RequestParam(defaultValue = "20") int limit) {
        List<CommunityPost> items = repo.recent(limit);
        return ResponseEntity.ok()
            .header("Deprecation", "true")
            .header("Sunset", DateTimeFormatter.ISO_DATE.format(LocalDate.now().plusDays(30)))
            .header("Link", "</api/v1/posts>; rel=\"successor-version\"")
            .body(Map.of("items", items, "count", items.size()));
    }

    @PostMapping("/posts")
    public ResponseEntity<CommunityPost> create(@RequestBody CreatePostRequest body) {
        String cover = PALETTE.get(Math.abs(body.title().hashCode()) % PALETTE.size());
        CommunityPost p = new CommunityPost(
            UUID.randomUUID().toString(),
            body.authorName() == null ? "某同学" : body.authorName(),
            body.authorAvatar() == null ? "" : body.authorAvatar(),
            body.title(),
            excerpt(body.body()),
            body.body(),
            cover,
            0, 0,
            Instant.now()
        );
        return ResponseEntity.ok()
            .header("Deprecation", "true")
            .header("Sunset", DateTimeFormatter.ISO_DATE.format(LocalDate.now().plusDays(30)))
            .body(repo.save(p));
    }

    private String excerpt(String body) {
        if (body == null) return "";
        return body.length() > 200 ? body.substring(0, 200) + "…" : body;
    }

    public record CreatePostRequest(
        String title,
        String body,
        String authorName,
        String authorAvatar
    ) {}
}
