package com.skillhub.service;

import com.skillhub.dto.AbilityProfileResponse;
import com.skillhub.model.Post;
import com.skillhub.repo.PostRepository;
import com.skillhub.repo.SeniorSkillRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力画像服务：把用户在社区的真实行为映射为四方向 0-100 评分。
 *
 * <p>评分口径（可解释、可审计）：
 * <pre>
 *   score = 5（基线）+ 该方向发帖数 × 10 + 收到点赞 × 2 + 收到评论 × 3
 *   封顶 100。
 * </pre>
 *
 * <p>方向映射与前端 {@code domain.ts} 一致：
 * study→学习/保研，research→科研，competition→竞赛，skills→技能/求职/实习。
 */
@Service
public class AbilityProfileService {

    private static final List<DomainDef> DOMAINS = List.of(
        new DomainDef("study", "学习", List.of("学习", "保研"),
            List.of(new Branch("知识拆解", "课程与复杂概念", 88),
                    new Branch("笔记重构", "复盘与知识连接", 81),
                    new Branch("同伴讲解", "表达与答疑", 69))),
        new DomainDef("research", "科研", List.of("科研"),
            List.of(new Branch("论文精读", "论证与证据定位", 72),
                    new Branch("问题定义", "边界与研究价值", 54),
                    new Branch("实验记录", "过程与复现", 47))),
        new DomainDef("competition", "竞赛", List.of("竞赛"),
            List.of(new Branch("赛题拆解", "约束与目标识别", 78),
                    new Branch("协作推进", "分工与节奏管理", 67),
                    new Branch("答辩表达", "叙事与临场反馈", 52))),
        new DomainDef("skills", "技能", List.of("技能", "求职", "实习"),
            List.of(new Branch("数据表达", "分析与可视化", 84),
                    new Branch("创意实践", "工具与项目交付", 76),
                    new Branch("公开表达", "展示与沟通", 63)))
    );

    private final PostRepository postRepo;
    private final SeniorSkillRepository seniorRepo;

    public AbilityProfileService(PostRepository postRepo, SeniorSkillRepository seniorRepo) {
        this.postRepo = postRepo;
        this.seniorRepo = seniorRepo;
    }

    public AbilityProfileResponse profileFor(String userId) {
        List<Post> mine = postRepo.listAfter(null, 1000, userId, null);
        List<Post> allSite = postRepo.listAfter(null, 5000, null, null);
        var seniors = seniorRepo.list(null, null);

        Map<String, AbilityProfileResponse.DomainScore> out = new LinkedHashMap<>();
        for (DomainDef def : DOMAINS) {
            // 我的方向帖子
            List<Post> mineInDomain = mine.stream()
                .filter(p -> def.matches(p.domain()))
                .toList();
            int posts = mineInDomain.size();
            int likes = mineInDomain.stream().mapToInt(p -> (int) p.likeCount()).sum();
            int comments = mineInDomain.stream().mapToInt(p -> (int) p.commentCount()).sum();

            int score = Math.min(100, 5 + posts * 10 + likes * 2 + comments * 3);
            if (posts == 0) score = Math.min(score, 20); // 无发言方向不超过 20（弱信号）

            int sitePosts = (int) allSite.stream().filter(p -> def.matches(p.domain())).count();
            int seniorCount = (int) seniors.stream()
                .filter(s -> def.matches(s.domain()) || def.matches(s.major()))
                .count();

            // 细分能力分数：以方向分为基准，按静态权重微调（无分支级行为数据时保持可解释）
            List<AbilityProfileResponse.BranchScore> branches = new ArrayList<>();
            for (Branch b : def.branches) {
                int branchScore = Math.max(0, Math.min(100, score + (b.base - 70) / 2));
                branches.add(new AbilityProfileResponse.BranchScore(b.name, b.note, branchScore));
            }

            out.put(def.id, new AbilityProfileResponse.DomainScore(
                def.id, def.name, score, posts, likes, comments, sitePosts, seniorCount, branches));
        }

        int total = (int) Math.round(out.values().stream()
            .mapToInt(AbilityProfileResponse.DomainScore::score).average().orElse(0));

        return new AbilityProfileResponse(userId, total, new ArrayList<>(out.values()));
    }

    private record DomainDef(String id, String name, List<String> labels, List<Branch> branches) {
        boolean matches(String domain) {
            if (domain == null) return false;
            return labels.stream().anyMatch(domain::contains);
        }
    }

    private record Branch(String name, String note, int base) {}
}
