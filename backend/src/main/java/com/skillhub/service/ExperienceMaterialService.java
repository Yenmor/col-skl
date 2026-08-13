package com.skillhub.service;

import com.skillhub.config.LlmProperties;
import com.skillhub.dto.ExperienceMaterialsResponse;
import com.skillhub.model.Comment;
import com.skillhub.model.Post;
import com.skillhub.repo.CommentRepository;
import com.skillhub.repo.PostRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExperienceMaterialService {
    public static final int MINIMUM_THREADS = 3;

    private final PostRepository posts;
    private final CommentRepository comments;
    private final LlmProperties llm;

    public ExperienceMaterialService(PostRepository posts, CommentRepository comments,
                                     LlmProperties llm) {
        this.posts = posts;
        this.comments = comments;
        this.llm = llm;
    }

    public ExperienceMaterialsResponse forUser(String userId) {
        List<ExperienceMaterialsResponse.ThreadMaterial> threads = new ArrayList<>();
        for (Post post : posts.listAfter(null, 5000, null, null)) {
            List<Comment> threadComments = comments.listByPost(post.id(), 5000);
            List<String> ownedCommentIds = threadComments.stream()
                .filter(comment -> userId.equals(comment.authorId()))
                .map(Comment::id)
                .toList();
            if (!userId.equals(post.authorId()) && ownedCommentIds.isEmpty()) continue;
            threads.add(toMaterial(post, threadComments, ownedCommentIds));
        }
        return new ExperienceMaterialsResponse(realLlmAvailable(), MINIMUM_THREADS, threads);
    }

    public Map<String, ExperienceMaterialsResponse.ThreadMaterial> selectedForUser(
            String userId, List<String> threadIds) {
        Map<String, ExperienceMaterialsResponse.ThreadMaterial> available = new LinkedHashMap<>();
        for (var thread : forUser(userId).threads()) available.put(thread.threadId(), thread);
        Map<String, ExperienceMaterialsResponse.ThreadMaterial> selected = new LinkedHashMap<>();
        if (threadIds != null) {
            for (String id : threadIds) {
                if (id != null && available.containsKey(id)) selected.put(id, available.get(id));
            }
        }
        return selected;
    }

    public boolean realLlmAvailable() {
        return "deepseek".equalsIgnoreCase(llm.getProvider())
            && llm.getDeepseek().getApiKey() != null
            && !llm.getDeepseek().getApiKey().isBlank();
    }

    private ExperienceMaterialsResponse.ThreadMaterial toMaterial(
            Post post, List<Comment> threadComments, List<String> ownedCommentIds) {
        return new ExperienceMaterialsResponse.ThreadMaterial(
            post.id(), post.title(), post.domain(),
            new ExperienceMaterialsResponse.PostMaterial(
                post.id(), post.body(), post.authorId(), post.createdAt()),
            threadComments.stream().map(comment -> new ExperienceMaterialsResponse.CommentMaterial(
                comment.id(), comment.body(), comment.authorId(), comment.parentId(), comment.createdAt()
            )).toList(),
            ownedCommentIds);
    }
}
