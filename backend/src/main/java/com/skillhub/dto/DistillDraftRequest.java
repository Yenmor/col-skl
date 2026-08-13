package com.skillhub.dto;

import java.util.List;

public record DistillDraftRequest(
        String topic,
        String goal,
        List<String> threadIds,
        String layerId,
        List<String> tags
) {}
