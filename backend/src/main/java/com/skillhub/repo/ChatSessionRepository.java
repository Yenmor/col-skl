package com.skillhub.repo;

import com.skillhub.model.ChatSession;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository {
    ChatSession upsert(ChatSession s);
    Optional<ChatSession> findById(String id);
    List<ChatSession> listByUser(String userId, int limit);
    boolean existsById(String id);
}