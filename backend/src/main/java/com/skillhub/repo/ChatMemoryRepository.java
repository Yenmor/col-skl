package com.skillhub.repo;

import com.skillhub.model.ChatMemory;

import java.util.List;

public interface ChatMemoryRepository {
    ChatMemory save(ChatMemory m);
    List<ChatMemory> listByUser(String userId, int limit);
}