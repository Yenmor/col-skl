package com.skillhub.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.config.BaseController;
import com.skillhub.dto.ChatMemoryDto;
import com.skillhub.dto.ChatMessageDto;
import com.skillhub.dto.ChatRequestV1;
import com.skillhub.dto.ChatResponseV1;
import com.skillhub.dto.ChatSessionDto;
import com.skillhub.dto.CreateMemoryRequest;
import com.skillhub.dto.CreateMemoryResponse;
import com.skillhub.model.ChatMemory;
import com.skillhub.model.ChatMessageEntity;
import com.skillhub.model.ChatSession;
import com.skillhub.repo.ChatRepository;
import com.skillhub.repo.ChatSessionRepository;
import com.skillhub.repo.ChatMemoryRepository;
import com.skillhub.service.ChatOrchestrator;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatControllerV1 extends BaseController {

    private final ChatOrchestrator orchestrator;
    private final ChatRepository chatRepo;
    private final ChatSessionRepository sessionRepo;
    private final ChatMemoryRepository memoryRepo;
    private final ObjectMapper json = new ObjectMapper();

    public ChatControllerV1(ChatOrchestrator orchestrator,
                            ChatRepository chatRepo,
                            ChatSessionRepository sessionRepo,
                            ChatMemoryRepository memoryRepo) {
        this.orchestrator = orchestrator;
        this.chatRepo = chatRepo;
        this.sessionRepo = sessionRepo;
        this.memoryRepo = memoryRepo;
    }

    @PostMapping
    public ChatResponseV1 chat(@RequestHeader("X-User-Id") String userId,
                               @RequestBody ChatRequestV1 req) {
        if (req.getMessage() == null || req.getMessage().isBlank()) {
            throw badRequest(com.skillhub.dto.ErrorCode.CHAT_EMPTY_MESSAGE, "消息为空");
        }
        if (req.getSeniorId() != null && !req.getSeniorId().isBlank()) {
            orchestrator.requireAccessibleTarget(req.getSeniorId(), userId);
        }
        String sessionId = (req.getSessionId() == null || req.getSessionId().isBlank())
            ? UUID.randomUUID().toString()
            : req.getSessionId();

        sessionRepo.findById(sessionId).ifPresent(existing -> {
            if (!userId.equals(existing.userId())) {
                throw notFound(com.skillhub.dto.ErrorCode.CHAT_SESSION_NOT_FOUND, "会话不存在");
            }
        });

        sessionRepo.upsert(new ChatSession(
            sessionId, userId, truncateTitle(req.getMessage()),
            Instant.now(), Instant.now()
        ));

        chatRepo.save(new ChatMessageEntity(
            UUID.randomUUID().toString(),
            sessionId, "user",
            req.getMessage(), null,
            Instant.now()
        ));

        List<ChatOrchestrator.SeniorAnswer> answers = orchestrator.orchestrate(
            req.getMessage(), req.getExcludeSeniorId(), req.getSeniorId(), userId);

        if (!answers.isEmpty()) {
            chatRepo.save(new ChatMessageEntity(
                UUID.randomUUID().toString(),
                sessionId, "assistant",
                null, orchestrator.serialize(answers),
                Instant.now()
            ));
        }

        List<ChatResponseV1.Answer> mapped = answers.stream().map(a ->
            new ChatResponseV1.Answer(
                a.seniorId(), a.name(), a.school(), a.major(),
                a.year(), a.content(), a.domain()
            )).toList();

        return new ChatResponseV1(sessionId, mapped);
    }

    @GetMapping("/sessions")
    public List<ChatSessionDto> listSessions(@RequestHeader("X-User-Id") String userId) {
        return sessionRepo.listByUser(userId, 50).stream()
            .map(s -> new ChatSessionDto(s.id(), s.title(), s.updatedAt()))
            .toList();
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<ChatMessageDto> listMessages(@PathVariable String sessionId,
                                             @RequestHeader("X-User-Id") String userId,
                                             @RequestParam(defaultValue = "50") int limit) {
        ChatSession s = sessionRepo.findById(sessionId).orElseThrow(() ->
            notFound(com.skillhub.dto.ErrorCode.CHAT_SESSION_NOT_FOUND, "会话不存在"));
        if (!userId.equals(s.userId())) {
            throw notFound(com.skillhub.dto.ErrorCode.CHAT_SESSION_NOT_FOUND, "会话不存在");
        }
        List<ChatMessageEntity> rows = chatRepo.recentBySession(sessionId, limit);
        return rows.stream().map(this::toDto).toList();
    }

    @PostMapping("/sessions/{sessionId}/memories")
    public CreateMemoryResponse createMemory(@PathVariable String sessionId,
                                             @RequestHeader("X-User-Id") String userId,
                                             @RequestBody(required = false) CreateMemoryRequest body) {
        ChatSession s = sessionRepo.findById(sessionId).orElseThrow(() ->
            notFound(com.skillhub.dto.ErrorCode.CHAT_SESSION_NOT_FOUND, "会话不存在"));
        if (!userId.equals(s.userId())) {
            throw notFound(com.skillhub.dto.ErrorCode.CHAT_SESSION_NOT_FOUND, "会话不存在");
        }
        String title = body != null ? body.title() : s.title();
        List<String> tags = body != null && body.tags() != null ? body.tags() : List.of();
        String memoryId = UUID.randomUUID().toString();
        String contentJson = serializeSessionContent(sessionId, title, tags);
        memoryRepo.save(new ChatMemory(
            memoryId, sessionId, userId, title,
            serializeList(tags), contentJson,
            Instant.now()
        ));
        return new CreateMemoryResponse(memoryId);
    }

    private ChatMessageDto toDto(ChatMessageEntity e) {
        Object answers = null;
        if (e.answersJson() != null && !e.answersJson().isBlank()) {
            try {
                answers = json.readValue(e.answersJson(), new TypeReference<List<ChatResponseV1.Answer>>() {});
            } catch (Exception ignored) {
                answers = e.answersJson();
            }
        }
        return new ChatMessageDto(e.role(), e.content(), answers, e.createdAt());
    }

    private String truncateTitle(String msg) {
        if (msg == null) return null;
        return msg.length() > 30 ? msg.substring(0, 30) + "…" : msg;
    }

    private String serializeList(List<String> list) {
        try {
            return json.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String serializeSessionContent(String sessionId, String title, List<String> tags) {
        try {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("sessionId", sessionId);
            m.put("title", title == null ? "" : title);
            m.put("tags", tags);
            return json.writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }
}
