package com.skillhub.service;

import com.skillhub.dto.ErrorCode;
import com.skillhub.model.Comment;
import com.skillhub.model.Post;
import com.skillhub.model.User;
import com.skillhub.repo.CommentRepository;
import com.skillhub.repo.PostRepository;
import com.skillhub.repo.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CommentService {

    private final CommentRepository commentRepo;
    private final PostRepository postRepo;
    private final UserRepository userRepo;

    public CommentService(CommentRepository commentRepo, PostRepository postRepo, UserRepository userRepo) {
        this.commentRepo = commentRepo;
        this.postRepo = postRepo;
        this.userRepo = userRepo;
    }

    public Comment create(String userId, String postId, String body, String parentId) {
        if (body == null || body.isBlank() || body.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ErrorCode.COMMENT_BODY_TOO_LONG.code());
        }
        Post post = postRepo.findById(postId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorCode.POST_NOT_FOUND.code()));
        if (parentId != null && !parentId.isBlank()) {
            commentRepo.findById(parentId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, ErrorCode.COMMENT_PARENT_NOT_FOUND.code()));
        }
        User author = userRepo.findById(userId).orElse(null);
        String authorName = author != null ? author.displayName() : "游客";
        String authorAvatar = author != null ? author.avatarUrl() : null;
        Comment c = new Comment(
            UUID.randomUUID().toString(),
            postId,
            userId,
            authorName,
            authorAvatar,
            parentId,
            body,
            Instant.now()
        );
        return commentRepo.save(c);
    }

    public List<Comment> listByPost(String postId, int limit) {
        // 验证 post 存在
        postRepo.findById(postId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorCode.POST_NOT_FOUND.code()));
        return commentRepo.listByPost(postId, Math.max(1, Math.min(limit, 50)));
    }

    public void delete(String userId, String commentId) {
        Comment c = commentRepo.findById(commentId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorCode.COMMENT_NOT_FOUND.code()));
        if (!userId.equals(c.authorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ErrorCode.COMMENT_FORBIDDEN.code());
        }
        commentRepo.deleteById(commentId);
    }
}