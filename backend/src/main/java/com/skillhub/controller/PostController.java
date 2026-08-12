package com.skillhub.controller;

import com.skillhub.config.BaseController;
import com.skillhub.dto.CreatePostRequest;
import com.skillhub.dto.Page;
import com.skillhub.dto.PostDetail;
import com.skillhub.dto.PostSummary;
import com.skillhub.model.Post;
import com.skillhub.service.PostService;
import com.skillhub.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController extends BaseController {

    private final PostService postService;
    private final UserService userService;

    public PostController(PostService postService, UserService userService) {
        this.postService = postService;
        this.userService = userService;
    }

    @GetMapping
    public Page<PostSummary> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String authorId,
            @RequestParam(required = false) String domain
    ) {
        // 列表接口：userId 可选（用于后续个性化排序）
        List<Post> all = postService.list(cursor, limit, authorId, domain);
        return page(all.stream().map(this::toSummary).toList(), limit);
    }

    @PostMapping
    public PostSummary create(@RequestHeader("X-User-Id") String userId,
                              @RequestBody CreatePostRequest body) {
        userService.getOrCreate(userId);
        Post p = postService.create(userId, body.title(), body.body(), body.domain());
        return toSummary(p);
    }

    @GetMapping("/{id}")
    public PostDetail get(@PathVariable String id) {
        Post p = postService.get(id);
        return new PostDetail(toSummary(p), p.body());
    }

    private PostSummary toSummary(Post p) {
        return new PostSummary(
            p.id(), p.title(), p.excerpt(), p.coverColor(),
            p.authorId() == null ? "" : p.authorId(),
            p.authorName() == null ? "游客" : p.authorName(),
            p.authorAvatar(),
            p.domain(),
            (int) p.likeCount(),
            (int) p.commentCount(),
            p.createdAt()
        );
    }
}