package com.skillhub.controller;

import com.skillhub.config.BaseController;
import com.skillhub.model.Comment;
import com.skillhub.model.Post;
import com.skillhub.repo.CommentRepository;
import com.skillhub.repo.PostRepository;
import com.skillhub.repo.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户活动导出端点（TODO 事项 3 + 6）。
 *
 * <p>把某个用户的全部发言（帖子、评论、回复）以及相关上下文打包成一个 JSON 对象，
 * 供 metaskill / 蒸馏流程直接消费，不需要关心数据来源。
 *
 * <p>响应结构（{@code docs/api-v1.md §8} 的输入契约）：
 * <pre>
 * {
 *   "userId": "...",
 *   "displayName": "...",
 *   "posts": [
 *     {
 *       "id": "...", "title": "...", "body": "...", "domain": "...",
 *       "createdAt": "...",
 *       "comments": [ { "id": "...", "authorId": "...", "body": "...", "createdAt": "..." } ],
 *       "relatedPosts": [ { "id": "...", "title": "...", "body": "..." } ]
 *     }
 *   ]
 * }
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/activity")
public class UserActivityController extends BaseController {

    private final PostRepository postRepo;
    private final CommentRepository commentRepo;
    private final UserRepository userRepo;

    public UserActivityController(PostRepository postRepo,
                                  CommentRepository commentRepo,
                                  UserRepository userRepo) {
        this.postRepo = postRepo;
        this.commentRepo = commentRepo;
        this.userRepo = userRepo;
    }

    @GetMapping
    public Map<String, Object> activity(@PathVariable String userId) {
        // 1. 该用户的全部帖子
        List<Post> posts = postRepo.listAfter(null, 500, userId, null);

        List<Map<String, Object>> postItems = new ArrayList<>();
        for (Post p : posts) {
            // 2. 每帖的评论（含该用户自己的评论，以及他人对帖子的评论作为上下文）
            List<Comment> comments = commentRepo.listByPost(p.id(), 200);
            List<Map<String, Object>> commentItems = new ArrayList<>();
            for (Comment c : comments) {
                Map<String, Object> cm = new HashMap<>();
                cm.put("id", c.id());
                cm.put("authorId", c.authorId());
                cm.put("authorName", c.authorName());
                cm.put("parentId", c.parentId());
                cm.put("body", c.body());
                cm.put("createdAt", c.createdAt().toString());
                cm.put("isTargetUser", userId.equals(c.authorId()));
                commentItems.add(cm);
            }

            Map<String, Object> pm = new HashMap<>();
            pm.put("id", p.id());
            pm.put("title", p.title());
            pm.put("body", p.body());
            pm.put("domain", p.domain());
            pm.put("createdAt", p.createdAt().toString());
            pm.put("comments", commentItems);
            postItems.add(pm);
        }

        Map<String, Object> out = new HashMap<>();
        out.put("userId", userId);
        out.put("displayName", userRepo.findById(userId).map(u -> u.displayName()).orElse(userId));
        out.put("posts", postItems);
        out.put("postCount", postItems.size());
        out.put("commentCount", postItems.stream()
            .mapToLong(pm -> ((List<?>) pm.get("comments")).size()).sum());
        return out;
    }
}
