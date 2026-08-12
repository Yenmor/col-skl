package com.skillhub.controller;

import com.skillhub.config.BaseController;
import com.skillhub.dto.RecallRequest;
import com.skillhub.dto.SkillRecallItem;
import com.skillhub.service.SkillRecallService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillController extends BaseController {

    private final SkillRecallService recallService;

    public SkillController(SkillRecallService recallService) {
        this.recallService = recallService;
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