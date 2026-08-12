package com.skillhub.repo;

import com.skillhub.model.Comment;

import java.util.List;
import java.util.Optional;

public interface CommentRepository {
    Comment save(Comment c);
    Optional<Comment> findById(String id);
    List<Comment> listByPost(String postId, int limit);
    boolean deleteById(String id);
}