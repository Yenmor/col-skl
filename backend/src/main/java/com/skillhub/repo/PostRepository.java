package com.skillhub.repo;

import com.skillhub.model.Post;

import java.util.List;
import java.util.Optional;

public interface PostRepository {
    Post save(Post p);
    Optional<Post> findById(String id);
    List<Post> listAfter(String cursor, int limit, String authorId, String domain);
    /** 点赞 toggle；返回新状态 (likeCount, liked)。 */
    long[] likeToggle(String userId, String postId);
}