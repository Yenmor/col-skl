package com.skillhub.repo;

import com.skillhub.model.User;

import java.util.Optional;

public interface UserRepository {
    User save(User u);
    Optional<User> findById(String id);
    boolean existsById(String id);
}