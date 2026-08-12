package com.skillhub.dto;

import java.util.List;

/**
 * v1 创建记忆请求。
 */
public record CreateMemoryRequest(
        String title,
        List<String> tags
) {}