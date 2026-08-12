package com.skillhub.service;

import com.skillhub.dto.ErrorCode;
import com.skillhub.model.Post;
import com.skillhub.model.User;
import com.skillhub.repo.PostRepository;
import com.skillhub.repo.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PostService {

    private static final List<String> PALETTE = List.of(
        "#fde0e6", "#dceafd", "#e5f4dc", "#f9eedc", "#ece4fa", "#fde6d4"
    );

    private final PostRepository postRepo;
    private final UserRepository userRepo;

    public PostService(PostRepository postRepo, UserRepository userRepo) {
        this.postRepo = postRepo;
        this.userRepo = userRepo;
    }

    public Post create(String userId, String title, String body, String domain) {
        if (title == null || title.isBlank() || title.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ErrorCode.POST_TITLE_TOO_LONG.code());
        }
        if (body == null || body.isBlank() || body.length() > 20000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ErrorCode.POST_BODY_TOO_LONG.code());
        }
        User author = userRepo.findById(userId).orElse(null);
        String authorName = author != null ? author.displayName() : "游客";
        String authorAvatar = author != null ? author.avatarUrl() : null;
        String cover = PALETTE.get(Math.abs(title.hashCode()) % PALETTE.size());
        Post p = new Post(
            UUID.randomUUID().toString(),
            title,
            excerpt(body),
            body,
            cover,
            userId,
            authorName,
            authorAvatar,
            domain,
            0, 0,
            Instant.now()
        );
        return postRepo.save(p);
    }

    public Post get(String id) {
        return postRepo.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorCode.POST_NOT_FOUND.code()));
    }

    public List<Post> list(String cursor, int limit, String authorId, String domain) {
        return postRepo.listAfter(cursor, Math.max(1, Math.min(limit, 50)), authorId, domain);
    }

    private String excerpt(String body) {
        if (body == null) return "";
        return body.length() > 200 ? body.substring(0, 200) + "…" : body;
    }
}