package com.skillhub.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.config.BaseController;
import com.skillhub.config.ApiException;
import com.skillhub.dto.AbilityProfileResponse;
import com.skillhub.dto.ErrorCode;
import com.skillhub.service.AbilityProfileService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/me")
public class AbilityController extends BaseController {

    private final AbilityProfileService abilityProfileService;
    private final ObjectMapper json;

    public AbilityController(AbilityProfileService abilityProfileService, ObjectMapper json) {
        this.abilityProfileService = abilityProfileService;
        this.json = json;
    }

    /** 能力画像：四方向评分（真实社区行为统计）。 */
    @GetMapping("/ability-profile")
    public AbilityProfileResponse abilityProfile(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String fifthLayerName,
            @RequestParam(required = false) String directions) {
        return abilityProfileService.profileFor(userId, fifthLayerName, parseDirections(directions));
    }

    private List<AbilityProfileService.DirectionInput> parseDirections(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            return json.readValue(raw, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.GENERAL_VALIDATION,
                "directions 必须是合法 JSON 数组");
        }
    }
}
