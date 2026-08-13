package com.skillhub.service;

import com.skillhub.dto.AbilityProfileResponse;
import com.skillhub.dto.SkillRecallItem;
import com.skillhub.model.Comment;
import com.skillhub.model.Post;
import com.skillhub.model.SeniorSkill;
import com.skillhub.repo.CommentRepository;
import com.skillhub.repo.PostRepository;
import com.skillhub.repo.SeniorSkillRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AbilityProfileService {
    private static final List<DirectionInput> DEFAULTS = List.of(
        new DirectionInput("study", "学习", List.of(
            new BranchInput("知识拆解", "课程与复杂概念"),
            new BranchInput("笔记重构", "复盘与知识连接"),
            new BranchInput("同伴讲解", "表达与答疑"))),
        new DirectionInput("research", "科研", List.of(
            new BranchInput("论文精读", "论证与证据定位"),
            new BranchInput("问题定义", "边界与研究价值"),
            new BranchInput("实验记录", "过程与复现"))),
        new DirectionInput("competition", "竞赛", List.of(
            new BranchInput("赛题拆解", "约束与目标识别"),
            new BranchInput("协作推进", "分工与节奏管理"),
            new BranchInput("答辩表达", "叙事与临场反馈"))),
        new DirectionInput("skills", "技能", List.of(
            new BranchInput("数据表达", "分析与可视化"),
            new BranchInput("创意实践", "工具与项目交付"),
            new BranchInput("公开表达", "展示与沟通")))
    );

    private final PostRepository postRepo;
    private final CommentRepository commentRepo;
    private final SeniorSkillRepository seniorRepo;
    private final SkillRecallService recall;

    public AbilityProfileService(PostRepository postRepo,
                                 CommentRepository commentRepo,
                                 SeniorSkillRepository seniorRepo,
                                 SkillRecallService recall) {
        this.postRepo = postRepo;
        this.commentRepo = commentRepo;
        this.seniorRepo = seniorRepo;
        this.recall = recall;
    }

    public AbilityProfileResponse profileFor(String userId) {
        return profileFor(userId, "我的主题", List.of());
    }

    public AbilityProfileResponse profileFor(String userId, String fifthLayerName,
                                             List<DirectionInput> requested) {
        List<DirectionInput> directions = mergeDirections(fifthLayerName, requested);
        List<Post> allPosts = postRepo.listAfter(null, 5000, null, null);
        Map<String, List<Comment>> commentsByPost = new LinkedHashMap<>();
        for (Post post : allPosts) commentsByPost.put(post.id(), commentRepo.listByPost(post.id(), 5000));
        List<SeniorSkill> owned = seniorRepo.listOwned(userId);
        List<SeniorSkill> publicSkills = seniorRepo.listPublic(null, null, null);

        List<AbilityProfileResponse.DomainScore> domains = new ArrayList<>();
        for (DirectionInput direction : directions) {
            Evidence evidence = evidenceFor(userId, directionKeywords(direction),
                allPosts, commentsByPost, owned);
            List<AbilityProfileResponse.BranchScore> branches = new ArrayList<>();
            for (BranchInput branch : direction.branches()) {
                LinkedHashSet<String> keywords = new LinkedHashSet<>();
                keywords.add(branch.name());
                keywords.addAll(tokens(branch.name()));
                keywords.addAll(tokens(branch.note()));
                Evidence branchEvidence = evidenceFor(userId, keywords, allPosts, commentsByPost, owned);
                branches.add(new AbilityProfileResponse.BranchScore(
                    branch.name(), branch.note(), branchEvidence.score(), branchEvidence.counts()));
            }
            int sitePosts = (int) allPosts.stream().filter(post -> matches(postText(post), directionKeywords(direction))).count();
            int seniors = (int) publicSkills.stream().filter(skill -> matches(skillText(skill), directionKeywords(direction))).count();
            domains.add(new AbilityProfileResponse.DomainScore(
                direction.id(), direction.name(), evidence.score(), evidence.counts().posts(),
                evidence.counts().receivedLikes(),
                evidence.counts().comments() + evidence.counts().receivedReplies(),
                sitePosts, seniors, branches, evidence.counts()));
        }

        AbilityProfileResponse.LowestDirection lowest = findLowest(domains);
        String query = lowest.branchName() == null || lowest.branchName().isBlank()
            ? lowest.domainName() : lowest.domainName() + " " + lowest.branchName();
        List<SkillRecallItem> recommendations = recall.recall(query, 3, null, null);
        int total = (int) Math.round(domains.stream()
            .mapToInt(AbilityProfileResponse.DomainScore::score).average().orElse(0));
        return new AbilityProfileResponse(
            userId, total, "能力证据成熟度", domains, lowest, recommendations);
    }

    private Evidence evidenceFor(String userId, Set<String> keywords, List<Post> allPosts,
                                 Map<String, List<Comment>> commentsByPost,
                                 List<SeniorSkill> ownedSkills) {
        int posts = 0;
        int ownComments = 0;
        int receivedLikes = 0;
        Set<String> receivedReplyIds = new LinkedHashSet<>();
        Set<String> ownMatchingCommentIds = new LinkedHashSet<>();

        for (Post post : allPosts) {
            boolean matchingPost = matches(postText(post), keywords);
            List<Comment> thread = commentsByPost.getOrDefault(post.id(), List.of());
            if (userId.equals(post.authorId()) && matchingPost) {
                posts++;
                receivedLikes += Math.toIntExact(Math.min(Integer.MAX_VALUE, post.likeCount()));
                thread.stream().filter(comment -> !userId.equals(comment.authorId()))
                    .map(Comment::id).forEach(receivedReplyIds::add);
            }
            for (Comment comment : thread) {
                boolean matchingComment = matches(comment.body() + " " + postText(post), keywords);
                if (userId.equals(comment.authorId()) && matchingComment) {
                    ownComments++;
                    ownMatchingCommentIds.add(comment.id());
                }
            }
        }
        for (List<Comment> thread : commentsByPost.values()) {
            for (Comment comment : thread) {
                if (!userId.equals(comment.authorId()) && comment.parentId() != null
                    && ownMatchingCommentIds.contains(comment.parentId())) {
                    receivedReplyIds.add(comment.id());
                }
            }
        }

        int privateDrafts = 0;
        int publicOwned = 0;
        for (SeniorSkill skill : ownedSkills) {
            if (!matches(skillText(skill), keywords)) continue;
            if (skill.isPublic()) publicOwned++;
            else privateDrafts++;
        }
        AbilityProfileResponse.EvidenceCounts counts = new AbilityProfileResponse.EvidenceCounts(
            posts, ownComments, receivedLikes, receivedReplyIds.size(), privateDrafts, publicOwned,
            posts + ownComments + receivedLikes + receivedReplyIds.size() + privateDrafts + publicOwned);
        return new Evidence(score(counts), counts);
    }

    private int score(AbilityProfileResponse.EvidenceCounts counts) {
        int postScore = Math.min(30, counts.posts() * 10);
        int commentScore = Math.min(20, counts.comments() * 4);
        int likeScore = Math.min(20, counts.receivedLikes() * 2);
        int replyScore = Math.min(15, counts.receivedReplies() * 3);
        int draftScore = Math.min(5, counts.privateDrafts() * 5);
        int publicSkillScore = Math.min(10, counts.publicSkills() * 5);
        return postScore + commentScore + likeScore + replyScore + draftScore + publicSkillScore;
    }

    private AbilityProfileResponse.LowestDirection findLowest(
            List<AbilityProfileResponse.DomainScore> domains) {
        AbilityProfileResponse.DomainScore lowestDomain = domains.stream()
            .min(java.util.Comparator.comparingInt(AbilityProfileResponse.DomainScore::score))
            .orElseThrow();
        AbilityProfileResponse.BranchScore lowestBranch = domains.stream()
            .flatMap(domain -> domain.branches().stream())
            .min(java.util.Comparator.comparingInt(AbilityProfileResponse.BranchScore::score))
            .orElse(null);
        if (lowestBranch != null && lowestBranch.score() <= lowestDomain.score()) {
            AbilityProfileResponse.DomainScore owner = domains.stream()
                .filter(domain -> domain.branches().contains(lowestBranch)).findFirst().orElse(lowestDomain);
            return new AbilityProfileResponse.LowestDirection(
                owner.id(), owner.name(), lowestBranch.name(), lowestBranch.score(),
                lowestBranch.evidence().total());
        }
        return new AbilityProfileResponse.LowestDirection(
            lowestDomain.id(), lowestDomain.name(), null, lowestDomain.score(),
            lowestDomain.evidence().total());
    }

    private List<DirectionInput> mergeDirections(String fifthLayerName, List<DirectionInput> requested) {
        Map<String, DirectionInput> byId = new LinkedHashMap<>();
        DEFAULTS.forEach(direction -> byId.put(direction.id(), direction));
        if (requested != null) {
            for (DirectionInput direction : requested) {
                if (direction == null || direction.id() == null || direction.id().isBlank()) continue;
                DirectionInput existing = byId.get(direction.id());
                String name = clean(direction.name(), existing == null ? direction.id() : existing.name());
                List<BranchInput> branches = direction.branches() == null || direction.branches().isEmpty()
                    ? existing == null ? List.of() : existing.branches()
                    : sanitizeBranches(direction.branches());
                byId.put(direction.id(), new DirectionInput(direction.id(), name, branches));
            }
        }
        String customName = clean(fifthLayerName,
            byId.containsKey("custom") ? byId.get("custom").name() : "我的主题");
        DirectionInput custom = byId.get("custom");
        List<BranchInput> customBranches = custom == null ? List.of() : custom.branches();
        byId.put("custom", new DirectionInput("custom", customName, customBranches));
        List<DirectionInput> ordered = new ArrayList<>();
        for (String id : List.of("study", "research", "competition", "skills", "custom")) {
            if (byId.containsKey(id)) ordered.add(byId.get(id));
        }
        return ordered;
    }

    private List<BranchInput> sanitizeBranches(List<BranchInput> branches) {
        return branches.stream().filter(java.util.Objects::nonNull)
            .filter(branch -> branch.name() != null && !branch.name().isBlank())
            .map(branch -> new BranchInput(branch.name().trim(), clean(branch.note(), "相关社区证据")))
            .distinct().limit(20).toList();
    }

    private LinkedHashSet<String> directionKeywords(DirectionInput direction) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        keywords.add(direction.name());
        switch (direction.id()) {
            case "study" -> keywords.addAll(List.of("学习", "保研", "选课", "课程", "绩点"));
            case "research" -> keywords.addAll(List.of("科研", "论文", "实验", "课题"));
            case "competition" -> keywords.addAll(List.of("竞赛", "比赛", "答辩", "赛题"));
            case "skills" -> keywords.addAll(List.of("技能", "求职", "实习", "项目", "作品"));
            default -> { }
        }
        keywords.removeIf(value -> value == null || value.isBlank());
        return keywords;
    }

    private Set<String> tokens(String text) {
        if (text == null || text.isBlank()) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String token : text.split("[\\s、，,。/与和及]+")) {
            if (token.length() >= 2) {
                result.add(token);
                if (token.matches(".*[\\u4e00-\\u9fa5].*") && token.length() > 2) {
                    for (int i = 0; i + 1 < token.length(); i++) result.add(token.substring(i, i + 2));
                }
            }
        }
        return result;
    }

    private boolean matches(String text, Set<String> keywords) {
        if (text == null || keywords.isEmpty()) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        return keywords.stream().filter(java.util.Objects::nonNull)
            .map(String::trim).filter(keyword -> keyword.length() >= 2)
            .map(keyword -> keyword.toLowerCase(Locale.ROOT)).anyMatch(lower::contains);
    }

    private String postText(Post post) {
        return safe(post.title()) + " " + safe(post.body()) + " " + safe(post.domain());
    }

    private String skillText(SeniorSkill skill) {
        return safe(skill.name()) + " " + safe(skill.domain()) + " "
            + safe(skill.layerId()) + " " + safe(skill.summary()) + " "
            + String.join(" ", skill.tags());
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record DirectionInput(String id, String name, List<BranchInput> branches) {}
    public record BranchInput(String name, String note) {}
    private record Evidence(int score, AbilityProfileResponse.EvidenceCounts counts) {}
}
