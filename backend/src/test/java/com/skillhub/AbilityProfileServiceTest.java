package com.skillhub;

import com.skillhub.model.Comment;
import com.skillhub.model.Post;
import com.skillhub.model.SeniorSkill;
import com.skillhub.repo.CommentRepository;
import com.skillhub.repo.PostRepository;
import com.skillhub.service.AbilityProfileService;
import com.skillhub.service.SkillRecallService;
import com.skillhub.service.SeniorReader;
import com.skillhub.support.InMemorySeniorSkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AbilityProfileServiceTest {
    @TempDir Path temp;
    @Test
    void scoresSixRealEvidenceTypesAndCustomFifthLayerWithoutReplyDuplication() {
        String user = "owner";
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        MemoryPosts posts = new MemoryPosts(List.of(
            post("p1", "机器人项目复盘", "做了机器人导航项目", user, "机器人", 4, 3, now),
            post("p2", "别人提问", "机器人传感器怎么调", "other", "机器人", 0, 2, now),
            post("p3", "科研论文", "论文精读", user, "科研", 1, 0, now)));
        MemoryComments comments = new MemoryComments(List.of(
            comment("c1", "p1", "other", null, "请补充"),
            comment("c2", "p2", user, null, "机器人传感器先标定"),
            comment("c3", "p2", "other", "c2", "这样有效，谢谢")));
        var skills = new InMemorySeniorSkillRepository();
        skills.save(skill("draft", user, SeniorSkill.PRIVATE, "机器人", now));
        skills.save(skill("public", user, SeniorSkill.PUBLIC, "机器人", now));
        SkillRecallService recall = new SkillRecallService(
            new SeniorReader(temp.toString(), skills, new ObjectMapper()), skills);
        var service = new AbilityProfileService(posts, comments, skills, recall);
        List<AbilityProfileService.DirectionInput> directions = List.of(
            new AbilityProfileService.DirectionInput("custom", "机器人", List.of(
                new AbilityProfileService.BranchInput("传感器标定", "传感器 标定"),
                new AbilityProfileService.BranchInput("机械加工", "机械 加工"))));

        var profile = service.profileFor(user, "机器人", directions);
        var custom = profile.domains().stream().filter(domain -> domain.id().equals("custom")).findFirst().orElseThrow();

        assertEquals(5, profile.domains().size());
        assertEquals("能力证据成熟度", profile.label());
        assertEquals(1, custom.evidence().posts());
        assertEquals(1, custom.evidence().comments());
        assertEquals(4, custom.evidence().receivedLikes());
        assertEquals(2, custom.evidence().receivedReplies(), "post reply and parent reply must be unique by id");
        assertEquals(1, custom.evidence().privateDrafts());
        assertEquals(1, custom.evidence().publicSkills());
        assertEquals(38, custom.score());
        var sensor = custom.branches().stream().filter(branch -> branch.name().equals("传感器标定")).findFirst().orElseThrow();
        var mechanics = custom.branches().stream().filter(branch -> branch.name().equals("机械加工")).findFirst().orElseThrow();
        assertTrue(sensor.evidence().comments() > 0);
        assertEquals(0, mechanics.evidence().total(), "branch must not inherit broad layer matches");
    }

    private static Post post(String id, String title, String body, String author, String domain,
                             long likes, long replies, Instant at) {
        return new Post(id, title, body, body, "#fff", author, author, null, domain, likes, replies, at);
    }
    private static Comment comment(String id, String post, String author, String parent, String body) {
        return new Comment(id, post, author, author, null, parent, body, Instant.parse("2026-08-13T00:00:00Z"));
    }
    private static SeniorSkill skill(String id, String owner, String visibility, String summary, Instant at) {
        return new SeniorSkill(id, id, "", "", "", "机器人", "", "distilled", at,
            owner, visibility, "custom", summary, "v1", List.of("机器人"), at);
    }

    private record MemoryPosts(List<Post> values) implements PostRepository {
        @Override public Post save(Post post) { throw new UnsupportedOperationException(); }
        @Override public Optional<Post> findById(String id) { return values.stream().filter(p -> p.id().equals(id)).findFirst(); }
        @Override public List<Post> listAfter(String cursor, int limit, String authorId, String domain) {
            return values.stream().filter(p -> authorId == null || authorId.equals(p.authorId())).limit(limit).toList();
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
}
