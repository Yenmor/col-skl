package com.skillhub.controller;

import com.skillhub.config.BaseController;
import com.skillhub.dto.DistillResult;
import com.skillhub.dto.SeniorFragmentDto;
import com.skillhub.service.SeniorDistillService;
import com.skillhub.repo.SeniorSkillRepository;
import com.skillhub.config.ApiException;
import com.skillhub.dto.ErrorCode;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/seniors")
public class SeniorControllerV1 extends BaseController {

    private final SeniorDistillService distillService;
    private final SeniorSkillRepository skills;

    public SeniorControllerV1(SeniorDistillService distillService,
                              SeniorSkillRepository skills) {
        this.distillService = distillService;
        this.skills = skills;
    }

    @PostMapping("/{id}/distill")
    public DistillResult distill(@PathVariable String id,
                                 @RequestHeader(value = "X-User-Id", required = false) String userId) {
        requirePublic(id);
        List<SeniorFragmentDto> fragments = distillService.distill(id);
        return new DistillResult(id, fragments, Instant.now());
    }

    @GetMapping("/{id}/fragments")
    public List<SeniorFragmentDto> fragments(@PathVariable String id,
                                             @RequestHeader(value = "X-User-Id", required = false) String userId,
                                             @RequestParam(defaultValue = "20") int limit) {
        requirePublic(id);
        return distillService.listBySenior(id, limit);
    }

    private void requirePublic(String id) {
        if (skills.findById(id).filter(com.skillhub.model.SeniorSkill::isPublic).isEmpty()) {
            throw new ApiException(ErrorCode.SENIOR_NOT_FOUND, "学长 Skill 不存在");
        }
    }
}
