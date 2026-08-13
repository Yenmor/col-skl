package com.skillhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.config.ApiException;
import com.skillhub.config.LlmProperties;
import com.skillhub.dto.DistillDraftRequest;
import com.skillhub.dto.ErrorCode;
import com.skillhub.model.Comment;
import com.skillhub.model.Post;
import com.skillhub.model.User;
import com.skillhub.repo.CommentRepository;
import com.skillhub.repo.PostRepository;
import com.skillhub.repo.UserRepository;
import com.skillhub.service.ExperienceMaterialService;
import com.skillhub.service.PrivateSkillDistillService;
import com.skillhub.service.SeniorReader;
import com.skillhub.service.SkillCatalogService;
import com.skillhub.service.UserService;
import com.skillhub.service.llm.LlmClient;
import com.skillhub.support.InMemorySeniorSkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PrivateSkillDistillServiceTest {
    private static final String USER = "user-1";
    @TempDir Path temp;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void requiresThreeAccessibleIndependentThreadsBeforeCallingModel() {
        Harness harness = harness(2, realProperties(), new FixedLlm("{}"));

        ApiException error = assertThrows(ApiException.class, () -> harness.service.distill(
            USER, request(List.of("thread-1", "thread-2"))));

        assertEquals(ErrorCode.DISTILL_INSUFFICIENT_EVIDENCE, error.errorCode());
        assertTrue(harness.repo.allIds().isEmpty());
        assertDirectoryEmpty();
    }

    @Test
    void mockOrUnconfiguredProviderCannotGenerateDraft() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("mock");
        Harness harness = harness(3, properties, new FixedLlm(validDistillation()));

        ApiException error = assertThrows(ApiException.class, () -> harness.service.distill(
            USER, request(List.of("thread-1", "thread-2", "thread-3"))));

        assertEquals(ErrorCode.DISTILL_LLM_UNAVAILABLE, error.errorCode());
        assertTrue(harness.repo.allIds().isEmpty());
        assertDirectoryEmpty();
    }

    @Test
    void fragmentsOnlyReturnsEvidenceGapWithoutWritingAnything() {
        String fragmentsOnly = """
            {"mode":"fragments_only","maturity":{"decision":"fragments_only","coverage":1,
             "repeatability":1,"boundaries":0,"evidence_quality":2,"total":4},
             "open_questions":["还缺失败案例"],"skill":{},"work":{},"persona":{},"review":{}}
            """;
        Harness harness = harness(3, realProperties(), new FixedLlm(fragmentsOnly));

        ApiException error = assertThrows(ApiException.class, () -> harness.service.distill(
            USER, request(List.of("thread-1", "thread-2", "thread-3"))));

        assertEquals(ErrorCode.DISTILL_INSUFFICIENT_EVIDENCE, error.errorCode());
        assertTrue(harness.repo.allIds().isEmpty());
        assertDirectoryEmpty();
    }

    @Test
    void invalidEvidenceFailsAtomically() {
        Harness harness = harness(3, realProperties(), new FixedLlm(
            validDistillation().replace(fragmentId("thread-3"), "frag_deadbeef00")));

        ApiException error = assertThrows(ApiException.class, () -> harness.service.distill(
            USER, request(List.of("thread-1", "thread-2", "thread-3"))));

        assertEquals(ErrorCode.DISTILL_GENERATION_FAILED, error.errorCode());
        assertTrue(harness.repo.allIds().isEmpty());
        assertDirectoryEmpty();
    }

    @Test
    void legacyFragmentOnlyEvidenceIsRejectedAtomically() {
        String legacy = validDistillation().replaceFirst(
            "\\{\\\"fragment_id\\\":\\\"[^\\\"]+\\\",\\\"message_ids\\\":\\[\\\"thread-1\\\"\\]}",
            "\\\"" + fragmentId("thread-1") + "\\\"");
        Harness harness = harness(3, realProperties(), new FixedLlm(legacy));

        ApiException error = assertThrows(ApiException.class, () -> harness.service.distill(
            USER, request(List.of("thread-1", "thread-2", "thread-3"))));

        assertEquals(ErrorCode.DISTILL_GENERATION_FAILED, error.errorCode());
        assertTrue(harness.repo.allIds().isEmpty());
        assertDirectoryEmpty();
    }

    @Test
    void shortOwnerRepliesCannotTurnOtherAuthorsPostsIntoSkillEvidence() {
        List<Post> posts = java.util.stream.IntStream.rangeClosed(1, 3)
            .mapToObj(index -> post(index, "other-user", "他人的完整项目复盘 " + index))
            .toList();
        List<Comment> comments = java.util.stream.IntStream.rangeClosed(1, 3)
            .mapToObj(index -> new Comment(
                "comment-" + index, "thread-" + index, USER, "我", null, null,
                "谢谢", Instant.parse("2026-08-1" + index + "T01:00:00Z")))
            .toList();
        String output = validDistillation()
            .replace("thread-1", "comment-1")
            .replace("thread-2", "comment-2")
            .replace("thread-3", "comment-3");
        Harness harness = harness(posts, comments, realProperties(), new FixedLlm(output));

        ApiException error = assertThrows(ApiException.class, () -> harness.service.distill(
            USER, request(List.of("thread-1", "thread-2", "thread-3"))));

        assertEquals(ErrorCode.DISTILL_INSUFFICIENT_EVIDENCE, error.errorCode());
        assertTrue(harness.repo.allIds().isEmpty());
        assertDirectoryEmpty();
    }

    @Test
    void validModelOutputCreatesPrivateTraceableDraftAndSurvivesRescan() throws Exception {
        Harness harness = harness(3, realProperties(), new FixedLlm(validDistillation()));

        var response = harness.service.distill(
            USER, request(List.of("thread-1", "thread-2", "thread-3")));

        assertEquals("PRIVATE", response.item().visibility());
        assertEquals(USER, response.item().ownerId());
        assertFalse(response.item().sources().evidenceIds().isEmpty());
        assertTrue(harness.repo.listPublic(null, null, null).isEmpty());
        Path dir = temp.resolve(response.item().id());
        JsonNode sources = json.readTree(dir.resolve("sources.json").toFile());
        assertEquals(3, sources.path("fragments").size());
        sources.path("fragments").forEach(mapping ->
        {
            assertFalse(mapping.path("target_message_ids").isEmpty(),
                "every core source must identify a message authored by the owner");
            assertEquals(mapping.path("target_message_ids"), mapping.path("source_message_ids"),
                "sources must record only owner messages actually cited by generated rules");
        });

        harness.repo.clear();
        harness.reader.scanOnBoot();
        assertEquals("PRIVATE", harness.repo.findById(response.item().id()).orElseThrow().visibility());
        assertTrue(harness.repo.findAccessibleById(response.item().id(), USER).isPresent());
        assertTrue(harness.repo.findAccessibleById(response.item().id(), "other").isEmpty());
    }

    private Harness harness(int threadCount, LlmProperties properties, LlmClient llm) {
        List<Post> posts = java.util.stream.IntStream.rangeClosed(1, threadCount)
            .mapToObj(index -> post(index, USER, "本人做过的项目步骤 " + index))
            .toList();
        List<Comment> comments = java.util.stream.IntStream.rangeClosed(1, threadCount)
            .mapToObj(index -> new Comment(
                "other-comment-" + index, "thread-" + index, "other-user", "其他人", null, null,
                "这是其他人的补充，不能写入本人的 Skill 来源。", Instant.parse("2026-08-1" + index + "T01:00:00Z")))
            .toList();
        return harness(posts, comments, properties, llm);
    }

    private Harness harness(List<Post> posts, List<Comment> comments,
                            LlmProperties properties, LlmClient llm) {
        MemoryPosts postRepo = new MemoryPosts(posts);
        MemoryComments commentRepo = new MemoryComments(comments);
        ExperienceMaterialService materials = new ExperienceMaterialService(postRepo, commentRepo, properties);
        InMemorySeniorSkillRepository skillRepo = new InMemorySeniorSkillRepository();
        SeniorReader reader = new SeniorReader(temp.toString(), skillRepo, json);
        SkillCatalogService catalog = new SkillCatalogService(skillRepo, reader);
        UserService users = new UserService(new MemoryUsers());
        PrivateSkillDistillService service = new PrivateSkillDistillService(
            materials, reader, skillRepo, catalog, users, llm, properties, json);
        return new Harness(service, skillRepo, reader);
    }

    private Post post(int index, String authorId, String body) {
        return new Post(
            "thread-" + index, "项目复盘 " + index, body,
            body, "#fff", authorId, "作者", null, "技能",
            0, 0, Instant.parse("2026-08-1" + index + "T00:00:00Z"));
    }

    private DistillDraftRequest request(List<String> threadIds) {
        return new DistillDraftRequest("项目复盘", "整理真实项目方法", threadIds, "skills", List.of("项目", "复盘"));
    }

    private LlmProperties realProperties() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("deepseek");
        properties.getDeepseek().setApiKey("test-key");
        properties.setTimeoutSeconds(10);
        return properties;
    }

    private String validDistillation() {
        String f1 = fragmentId("thread-1");
        String f2 = fragmentId("thread-2");
        String f3 = fragmentId("thread-3");
        return """
            {
              "mode":"full_skill",
              "skill":{"name":"项目学长 · 项目复盘","domain":"技能","description":"把做过的项目按证据复盘。","triggers":["项目","复盘"],"author":{}},
              "maturity":{"coverage":3,"repeatability":3,"boundaries":3,"evidence_quality":3,"total":12,"decision":"full_skill"},
              "work":{
                "scope":[{"statement":"复盘本人做过的项目","evidence":[{"fragment_id":"%s","message_ids":["thread-1"]}]}],
                "required_inputs":[{"statement":"项目目标和结果","evidence":[{"fragment_id":"%s","message_ids":["thread-1"]}]}],
                "workflow":[{"step":1,"instruction":"先列目标，再核对结果","evidence":[{"fragment_id":"%s","message_ids":["thread-1"]},{"fragment_id":"%s","message_ids":["thread-2"]},{"fragment_id":"%s","message_ids":["thread-3"]}]}],
                "decision_points":[
                  {"condition":"结果偏离目标","action":"回查关键决策","evidence":[{"fragment_id":"%s","message_ids":["thread-1"]},{"fragment_id":"%s","message_ids":["thread-2"]}]},
                  {"condition":"材料不足","action":"明确停止推断","evidence":[{"fragment_id":"%s","message_ids":["thread-2"]},{"fragment_id":"%s","message_ids":["thread-3"]}]}
                ],
                "completion_criteria":[{"statement":"每个结论都有来源","evidence":[{"fragment_id":"%s","message_ids":["thread-3"]}]}],
                "pitfalls":[{"statement":"不把他人发言写成本人经验","evidence":[{"fragment_id":"%s","message_ids":["thread-1"]}]}],
                "boundaries":[{"statement":"没有证据时明确不知道","evidence":[{"fragment_id":"%s","message_ids":["thread-3"]}]}],
                "experience_notes":[]
              },
              "persona":{
                "communication_principles":[{"statement":"先问目标","evidence":[{"fragment_id":"%s","message_ids":["thread-1"]}]}],
                "expression_patterns":[],
                "uncertainty_behavior":[{"statement":"不知道就说明","evidence":[{"fragment_id":"%s","message_ids":["thread-2"]}]}],
                "chat_style":[{"statement":"短句回答","evidence":[{"fragment_id":"%s","message_ids":["thread-2"]},{"fragment_id":"%s","message_ids":["thread-3"]}]}],
                "prohibited_inferences":["不推断敏感属性"]
              },
              "open_questions":[],
              "review":{"author_confirmed":false,"status":"draft"}
            }
            """.formatted(f1, f1, f1, f2, f3, f1, f2, f2, f3, f3, f1, f3, f1, f2, f2, f3);
    }

    private String fragmentId(String threadId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((USER + ":" + threadId).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 5; i++) hex.append(String.format("%02x", digest[i]));
            return "frag_" + hex;
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private void assertDirectoryEmpty() {
        try (var paths = Files.list(temp)) {
            assertTrue(paths.findAny().isEmpty());
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private record Harness(PrivateSkillDistillService service,
                           InMemorySeniorSkillRepository repo,
                           SeniorReader reader) {}

    private record FixedLlm(String response) implements LlmClient {
        @Override public Mono<String> complete(String systemPrompt, String userMessage) {
            return Mono.just(response);
        }
    }

    private record MemoryPosts(List<Post> values) implements PostRepository {
        @Override public Post save(Post post) { throw new UnsupportedOperationException(); }
        @Override public Optional<Post> findById(String id) { return values.stream().filter(p -> p.id().equals(id)).findFirst(); }
        @Override public List<Post> listAfter(String cursor, int limit, String authorId, String domain) {
            return values.stream().filter(post -> authorId == null || authorId.equals(post.authorId())).limit(limit).toList();
        }
        @Override public List<Post> search(String query, int limit) { return List.of(); }
        @Override public long[] likeToggle(String userId, String postId) { throw new UnsupportedOperationException(); }
    }
    private record MemoryComments(List<Comment> values) implements CommentRepository {
        @Override public Comment save(Comment comment) { throw new UnsupportedOperationException(); }
        @Override public Optional<Comment> findById(String id) { return values.stream().filter(c -> c.id().equals(id)).findFirst(); }
        @Override public List<Comment> listByPost(String postId, int limit) { return values.stream().filter(c -> c.postId().equals(postId)).limit(limit).toList(); }
        @Override public boolean deleteById(String id) { return false; }
    }
    private static final class MemoryUsers implements UserRepository {
        private final Map<String, User> values = new LinkedHashMap<>();
        @Override public User save(User user) { values.put(user.id(), user); return user; }
        @Override public Optional<User> findById(String id) { return Optional.ofNullable(values.get(id)); }
        @Override public boolean existsById(String id) { return values.containsKey(id); }
    }
}
