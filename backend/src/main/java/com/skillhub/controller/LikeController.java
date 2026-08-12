package com.skillhub.controller;

import com.skillhub.config.BaseController;
import com.skillhub.dto.LikeResult;
import com.skillhub.service.LikeService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/posts/{postId}")
public class LikeController extends BaseController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @PostMapping("/like")
    public LikeResult like(@PathVariable String postId,
                           @RequestHeader("X-User-Id") String userId) {
        LikeService.LikeResult r = likeService.like(userId, postId);
        return new LikeResult(r.likeCount(), r.liked());
    }

    @DeleteMapping("/like")
    public LikeResult unlike(@PathVariable String postId,
                             @RequestHeader("X-User-Id") String userId) {
        LikeService.LikeResult r = likeService.like(userId, postId);
        return new LikeResult(r.likeCount(), r.liked());
    }
}