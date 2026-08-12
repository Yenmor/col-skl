package com.skillhub.controller;

import com.skillhub.config.BaseController;
import com.skillhub.dto.ChatMemoryDto;
import com.skillhub.model.ChatMemory;
import com.skillhub.repo.ChatMemoryRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/me/memories")
public class MemoryController extends BaseController {

    private final ChatMemoryRepository memoryRepo;

    public MemoryController(ChatMemoryRepository memoryRepo) {
        this.memoryRepo = memoryRepo;
    }

    @GetMapping
    public List<ChatMemoryDto> listMine(@RequestHeader("X-User-Id") String userId,
                                        @RequestParam(defaultValue = "20") int limit) {
        return memoryRepo.listByUser(userId, Math.max(1, Math.min(limit, 50)))
            .stream().map(this::toDto).toList();
    }

    private ChatMemoryDto toDto(ChatMemory m) {
        List<String> tags;
        try {
            tags = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(m.tagsJson(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            tags = List.of();
        }
        return new ChatMemoryDto(m.id(), m.sessionId(), m.title(), tags, m.createdAt());
    }
}