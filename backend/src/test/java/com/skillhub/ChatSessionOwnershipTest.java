package com.skillhub;

import com.skillhub.config.ApiException;
import com.skillhub.controller.ChatControllerV1;
import com.skillhub.dto.ChatRequestV1;
import com.skillhub.dto.ErrorCode;
import com.skillhub.model.ChatMemory;
import com.skillhub.model.ChatMessageEntity;
import com.skillhub.model.ChatSession;
import com.skillhub.repo.ChatMemoryRepository;
import com.skillhub.repo.ChatRepository;
import com.skillhub.repo.ChatSessionRepository;
import com.skillhub.service.ChatOrchestrator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Controller ownership guard runs before orchestration and message persistence. */
class ChatSessionOwnershipTest {
    @Test
    void foreignExistingSessionCannotBeUpsertedOrPolluted() {
        MemorySessions sessions = new MemorySessions();
        Instant created = Instant.parse("2026-08-13T00:00:00Z");
        sessions.upsert(new ChatSession("session-1", "owner", "original", created, created));
        MemoryChat messages = new MemoryChat();
        ChatOrchestrator unused = null;
        ChatControllerV1 controller = new ChatControllerV1(
            unused, messages, sessions, new MemoryMemories());
        ChatRequestV1 request = new ChatRequestV1("foreign message", "session-1");

        ApiException error = assertThrows(ApiException.class,
            () -> controller.chat("attacker", request));

        assertEquals(ErrorCode.CHAT_SESSION_NOT_FOUND, error.errorCode());
        assertEquals("owner", sessions.findById("session-1").orElseThrow().userId());
        assertEquals("original", sessions.findById("session-1").orElseThrow().title());
        assertTrue(messages.values.isEmpty(), "guard must run before saving the user message");
    }

    @Test
    void forbiddenTargetCreatesNoSessionAndWritesNoMessages() {
        assertRejectedTargetLeavesNoTrace(ErrorCode.SKILL_FORBIDDEN);
    }

    @Test
    void missingTargetCreatesNoSessionAndWritesNoMessages() {
        assertRejectedTargetLeavesNoTrace(ErrorCode.SKILL_NOT_FOUND);
    }

    private void assertRejectedTargetLeavesNoTrace(ErrorCode code) {
        MemorySessions sessions = new MemorySessions();
        MemoryChat messages = new MemoryChat();
        ChatOrchestrator orchestrator = mock(ChatOrchestrator.class);
        doThrow(new ApiException(code, "target rejected"))
            .when(orchestrator).requireAccessibleTarget("private-target", "user-1");
        ChatControllerV1 controller = new ChatControllerV1(
            orchestrator, messages, sessions, new MemoryMemories());
        ChatRequestV1 request = new ChatRequestV1("请回答", null);
        request.setSeniorId("private-target");

        ApiException error = assertThrows(ApiException.class,
            () -> controller.chat("user-1", request));

        assertEquals(code, error.errorCode());
        assertTrue(sessions.values.isEmpty(), "target guard must run before session creation");
        assertTrue(messages.values.isEmpty(), "target guard must run before message persistence");
        verify(orchestrator).requireAccessibleTarget("private-target", "user-1");
        verifyNoMoreInteractions(orchestrator);
    }

    private static final class MemoryChat implements ChatRepository {
        final List<ChatMessageEntity> values = new ArrayList<>();
        @Override public void save(ChatMessageEntity message) { values.add(message); }
        @Override public List<ChatMessageEntity> recentBySession(String sessionId, int limit) {
            return values.stream().filter(message -> message.sessionId().equals(sessionId)).limit(limit).toList();
        }
    }

    private static final class MemorySessions implements ChatSessionRepository {
        final Map<String, ChatSession> values = new LinkedHashMap<>();
        @Override public ChatSession upsert(ChatSession session) { values.put(session.id(), session); return session; }
        @Override public Optional<ChatSession> findById(String id) { return Optional.ofNullable(values.get(id)); }
        @Override public List<ChatSession> listByUser(String userId, int limit) {
            return values.values().stream().filter(session -> userId.equals(session.userId())).limit(limit).toList();
        }
        @Override public boolean existsById(String id) { return values.containsKey(id); }
    }

    private static final class MemoryMemories implements ChatMemoryRepository {
        @Override public ChatMemory save(ChatMemory memory) { return memory; }
        @Override public List<ChatMemory> listByUser(String userId, int limit) { return List.of(); }
    }
}
