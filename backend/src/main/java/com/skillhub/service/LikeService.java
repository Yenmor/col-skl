package com.skillhub.service;

import com.skillhub.dto.ErrorCode;
import com.skillhub.model.Post;
import com.skillhub.repo.PostRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LikeService {

    private final PostRepository postRepo;

    public LikeService(PostRepository postRepo) {
        this.postRepo = postRepo;
    }

    public LikeResult like(String userId, String postId) {
        // 验证 post 存在
        postRepo.findById(postId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorCode.POST_NOT_FOUND.code()));
        long[] res = postRepo.likeToggle(userId, postId);
        return new LikeResult((int) res[0], res[1] == 1);
    }

    public record LikeResult(int likeCount, boolean liked) {}
}