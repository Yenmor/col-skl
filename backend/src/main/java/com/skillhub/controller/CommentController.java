package com.skillhub.controller;

import com.skillhub.config.BaseController;
import com.skillhub.dto.CommentDto;
import com.skillhub.dto.CreateCommentRequest;
import com.skillhub.model.Comment;
import com.skillhub.service.CommentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CommentController extends BaseController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/posts/{postId}/comments")
    public List<CommentDto> list(@PathVariable String postId,
                                 @RequestParam(defaultValue = "20") int limit) {
        return commentService.listByPost(postId, limit)
            .stream().map(this::toDto).toList();
    }

    @PostMapping("/posts/{postId}/comments")
    public CommentDto create(@PathVariable String postId,
                             @RequestHeader("X-User-Id") String userId,
                             @RequestBody CreateCommentRequest body) {
        Comment c = commentService.create(userId, postId, body.body(), body.parentId());
        return toDto(c);
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id,
                                       @RequestHeader("X-User-Id") String userId) {
        commentService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    private CommentDto toDto(Comment c) {
        return new CommentDto(
            c.id(), c.postId(),
            c.authorId() == null ? "" : c.authorId(),
            c.authorName() == null ? "游客" : c.authorName(),
            c.authorAvatar(),
            c.parentId(),
            c.body(),
            c.createdAt()
        );
    }
}