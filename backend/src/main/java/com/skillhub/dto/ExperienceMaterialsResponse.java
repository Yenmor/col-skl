package com.skillhub.dto;

import java.time.Instant;
import java.util.List;

public record ExperienceMaterialsResponse(
        boolean llmAvailable,
        int minimumThreads,
        List<ThreadMaterial> threads
) {
    public record ThreadMaterial(
            String threadId,
            String title,
            String domain,
            PostMaterial post,
            List<CommentMaterial> comments,
            List<String> ownedCommentIds
    ) {}

    public record PostMaterial(
            String id,
            String body,
            String authorId,
            Instant createdAt
    ) {}

    public record CommentMaterial(
            String id,
            String body,
            String authorId,
            String parentId,
            Instant createdAt
    ) {}
}
