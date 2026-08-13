package com.skillhub.controller;

import com.skillhub.dto.SkillListResponse;
import com.skillhub.service.SkillCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/skills")
public class MeSkillController {
    private final SkillCatalogService catalog;

    public MeSkillController(SkillCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public SkillListResponse mine(@RequestHeader("X-User-Id") String userId) {
        return catalog.owned(userId);
    }
}
