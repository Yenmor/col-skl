package com.skillhub.controller;

import com.skillhub.dto.DistillDraftRequest;
import com.skillhub.dto.DistillDraftResponse;
import com.skillhub.dto.ExperienceMaterialsResponse;
import com.skillhub.service.ExperienceMaterialService;
import com.skillhub.service.PrivateSkillDistillService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Current-user-only distillation API. It never accepts a caller-supplied user id. */
@RestController
@RequestMapping("/api/v1/me")
public class UserDistillController {
    private final ExperienceMaterialService materials;
    private final PrivateSkillDistillService distill;

    public UserDistillController(ExperienceMaterialService materials,
                                 PrivateSkillDistillService distill) {
        this.materials = materials;
        this.distill = distill;
    }

    @GetMapping("/materials")
    public ExperienceMaterialsResponse materials(@RequestHeader("X-User-Id") String userId) {
        return materials.forUser(userId);
    }

    @PostMapping("/skills/distill")
    public Mono<DistillDraftResponse> distill(@RequestHeader("X-User-Id") String userId,
                                              @RequestBody DistillDraftRequest request) {
        return Mono.fromCallable(() -> distill.distill(userId, request))
            .subscribeOn(Schedulers.boundedElastic());
    }
}
