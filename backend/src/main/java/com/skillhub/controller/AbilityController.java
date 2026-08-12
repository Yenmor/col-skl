package com.skillhub.controller;

import com.skillhub.config.BaseController;
import com.skillhub.dto.AbilityProfileResponse;
import com.skillhub.service.AbilityProfileService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
public class AbilityController extends BaseController {

    private final AbilityProfileService abilityProfileService;

    public AbilityController(AbilityProfileService abilityProfileService) {
        this.abilityProfileService = abilityProfileService;
    }

    /** 能力画像：四方向评分（真实社区行为统计）。 */
    @GetMapping("/ability-profile")
    public AbilityProfileResponse abilityProfile(@RequestHeader("X-User-Id") String userId) {
        return abilityProfileService.profileFor(userId);
    }
}
