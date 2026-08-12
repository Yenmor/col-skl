package com.skillhub.controller;

import com.skillhub.dto.ChatRequest;
import com.skillhub.dto.ChatResponse;
import com.skillhub.model.ChatMessageEntity;
import com.skillhub.repo.ChatRepository;
import com.skillhub.service.ChatOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatOrchestrator orchestrator;
    private final ChatRepository chatRepo;

    public ChatController(ChatOrchestrator orchestrator, ChatRepository chatRepo) {
        this.orchestrator = orchestrator;
        this.chatRepo = chatRepo;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest req) {
        String sessionId = (req.getSessionId() == null || req.getSessionId().isBlank())
            ? UUID.randomUUID().toString() : req.getSessionId();

        // 持久化 user 消息
        chatRepo.save(new ChatMessageEntity(
            UUID.randomUUID().toString(),
            sessionId, "user",
            req.getMessage(), null,
            Instant.now()
        ));

        List<ChatOrchestrator.SeniorAnswer> answers = orchestrator.orchestrate(req.getMessage());

        // 持久化 assistant 消息
        chatRepo.save(new ChatMessageEntity(
            UUID.randomUUID().toString(),
            sessionId, "assistant",
            null, orchestrator.serialize(answers),
            Instant.now()
        ));

        List<ChatResponse.Answer> mapped = answers.stream().map(a ->
            new ChatResponse.Answer(
                a.seniorId(), a.name(), a.school(), a.major(),
                a.year(), a.content()
            )).toList();

        ChatResponse body = new ChatResponse(sessionId, mapped);
        return ResponseEntity.ok()
            .header("Deprecation", "true")
            .header("Sunset", DateTimeFormatter.ISO_DATE.format(LocalDate.now().plusDays(30)))
            .header("Link", "</api/v1/chat>; rel=\"successor-version\"")
            .body(body);
    }
}
