package com.skillhub.service;

import com.skillhub.dto.ErrorCode;
import com.skillhub.model.User;
import com.skillhub.repo.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository repo;

    public UserService(UserRepository repo) {
        this.repo = repo;
    }

    /**
     * 懒创建匿名用户（D1）。
     */
    public User getOrCreate(String userId) {
        return repo.findById(userId).orElseGet(() -> {
            User u = new User(
                userId,
                "游客#" + userId.substring(0, 4),
                null,
                "GUEST",
                Instant.now()
            );
            return repo.save(u);
        });
    }

    public User patch(String userId, String displayName, String avatarUrl) {
        if (displayName != null) {
            if (displayName.isBlank() || displayName.length() > 24) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ErrorCode.USER_VALIDATION_FAILED.code());
            }
        }
        User current = getOrCreate(userId);
        User updated = new User(
            current.id(),
            displayName != null ? displayName : current.displayName(),
            avatarUrl != null ? avatarUrl : current.avatarUrl(),
            current.role(),
            current.createdAt()
        );
        return repo.save(updated);
    }
}