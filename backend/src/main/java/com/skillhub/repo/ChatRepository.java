package com.skillhub.repo;

import com.skillhub.model.ChatMessageEntity;

import java.util.List;

public interface ChatRepository {
    void save(ChatMessageEntity m);
    List<ChatMessageEntity> recentBySession(String sessionId, int limit);
}
