package com.skillhub.model;

import java.time.Instant;

public record CommunityPost(
        String id,
        String authorName,
        String authorAvatar,
        String title,
        String excerpt,        // 前 200 字摘要
        String body,           // 全文
        String coverColor,     // 瀑布封面配色
        long likeCount,
        long commentCount,
        Instant createdAt
) {
}
