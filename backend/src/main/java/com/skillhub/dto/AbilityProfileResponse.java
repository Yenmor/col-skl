package com.skillhub.dto;

import java.util.List;

/**
 * 能力画像响应（v1，适配能力立方体前端四方向评分）。
 *
 * @param userId    用户 id
 * @param total     综合掌握度 0-100
 * @param domains   四个方向（顺序固定：study/research/competition/skills）
 */
public record AbilityProfileResponse(
        String userId,
        int total,
        List<DomainScore> domains
) {
    /**
     * 单个方向评分。
     *
     * @param id        方向 id（study/research/competition/skills）
     * @param name      中文名（学习/科研/竞赛/技能）
     * @param score     0-100
     * @param posts     该方向该用户发布的帖子数
     * @param likes     该用户在该方向帖子收到的点赞数
     * @param comments  该用户在该方向帖子收到的评论数
     * @param sitePosts 全站该方向帖子总数（metrics 用）
     * @param seniors   该方向学长 Skill 数（metrics 用）
     * @param branches  细分能力（名称与后端无关，分数由方向分派生）
     */
    public record DomainScore(
            String id,
            String name,
            int score,
            int posts,
            int likes,
            int comments,
            int sitePosts,
            int seniors,
            List<BranchScore> branches
    ) {}

    public record BranchScore(String name, String note, int score) {}
}
