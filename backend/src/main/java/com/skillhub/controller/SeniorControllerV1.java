package com.skillhub.controller;

import com.skillhub.config.BaseController;
import com.skillhub.dto.DistillResult;
import com.skillhub.dto.SeniorFragmentDto;
import com.skillhub.service.SeniorDistillService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/seniors")
public class SeniorControllerV1 extends BaseController {

    private final SeniorDistillService distillService;

    public SeniorControllerV1(SeniorDistillService distillService) {
        this.distillService = distillService;
    }

    @PostMapping("/{id}/distill")
    public DistillResult distill(@PathVariable String id,
                                 @RequestHeader(value = "X-User-Id", required = false) String userId) {
        List<SeniorFragmentDto> fragments = distillService.distill(id);
        return new DistillResult(id, fragments, Instant.now());
    }

    @GetMapping("/{id}/fragments")
    public List<SeniorFragmentDto> fragments(@PathVariable String id,
                                             @RequestHeader(value = "X-User-Id", required = false) String userId,
                                             @RequestParam(defaultValue = "20") int limit) {
        return distillService.listBySenior(id, limit);
    }
}