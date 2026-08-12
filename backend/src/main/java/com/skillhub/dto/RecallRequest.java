package com.skillhub.dto;

import java.util.List;

/**
 * v1 召回请求。
 */
public record RecallRequest(
        String query,
        Integer topK,
        String domain,
        String school
) {}