package com.skillhub.dto;

import java.util.List;

public record ChatResponse(
    String sessionId,
    List<Answer> answers
) {
    public record Answer(
        String seniorId, String name, String school, String major,
        String year, String content
    ) {}
}
