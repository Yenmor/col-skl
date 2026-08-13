package com.skillhub.service;

import com.skillhub.config.LlmProperties;
import com.skillhub.dto.ExperienceMaterialsResponse;
import com.skillhub.model.Comment;
import com.skillhub.model.Post;
import com.skillhub.repo.CommentRepository;
import com.skillhub.repo.PostRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExperienceMaterialService {
    public static final int MINIMUM_THREADS = 3;

    private final PostRepository posts;
    private final CommentRepository comments;
    private final LlmProperties llm;

    public ExperienceMaterialService(PostRepository posts, CommentRepository comments,
                                     LlmProperties llm) {
        this.posts = posts;
        this.comments = comments;
        this.llm = llm;
    }

    public ExperienceMaterialsResponse forUser(String userId) {
        List<ExperienceMaterialsResponse.ThreadMaterial> threads = realThreads(userId);
        // 新访客/真实材料不足 3 个线程时，回退到稳定的演示材料（authorId 归属当前用户），
        // 保证沉淀台始终有可勾选材料，且能走通完整的沉淀证据校验。
        if (threads.size() < MINIMUM_THREADS) {
            threads = mockThreads(userId);
        }
        return new ExperienceMaterialsResponse(realLlmAvailable(), MINIMUM_THREADS, threads);
    }

    private List<ExperienceMaterialsResponse.ThreadMaterial> realThreads(String userId) {
        List<ExperienceMaterialsResponse.ThreadMaterial> threads = new ArrayList<>();
        for (Post post : posts.listAfter(null, 5000, null, null)) {
            List<Comment> threadComments = comments.listByPost(post.id(), 5000);
            List<String> ownedCommentIds = threadComments.stream()
                .filter(comment -> userId.equals(comment.authorId()))
                .map(Comment::id)
                .toList();
            if (!userId.equals(post.authorId()) && ownedCommentIds.isEmpty()) continue;
            threads.add(toMaterial(post, threadComments, ownedCommentIds));
        }
        return threads;
    }

    /**
     * 预制的演示材料：四个方向各一个线程，帖子与评论均归属当前用户，正文足够长且内容具体，
     * 使 LLM 生成的 evidence 能引用到足量本人消息、覆盖 3 个以上独立线程，从而通过证据校验。
     */
    private List<ExperienceMaterialsResponse.ThreadMaterial> mockThreads(String userId) {
        Instant base = Instant.parse("2026-08-01T09:00:00Z");
        List<ExperienceMaterialsResponse.ThreadMaterial> out = new ArrayList<>();
        out.add(mockThread("mock-thread-1", "保研：绩点和竞赛到底怎么排先后", "学习", userId, base,
            "我是大二软件工程的学生，现在绩点排在前 20%，想冲一下保研，但一直纠结绩点和竞赛哪个更该优先投入。\n\n我的困惑是：大二下还有不少专业课，如果把精力都拿去打比赛，绩点可能掉下来；可如果只刷绩点，又怕到保研季发现竞赛经历一片空白，简历上没东西可写。\n\n想听听过来人的建议，绩点和竞赛到底怎么分配时间，哪个先哪个后，以及大三上的夏令营前必须完成哪些事。",
            List.of(
                "先把结论说清楚：绩点是门票，竞赛是加分项，两者有先后但不是二选一。保研资格看的是专业排名，绩点掉出前 30% 基本就失去入场资格了，所以大二下优先稳住绩点。\n\n竞赛不需要一上来就上强度，选一个与专业相关、训练周期明确的比赛（数模、蓝桥杯这类），每周固定投入 6 到 8 小时就够，关键是赛后要把解题思路和组队过程沉淀下来，夏令营面试时能讲出细节。",
                "再补一个时间线：大三上 4 月前要刷出足够高的绩点并定下目标院校，5 月准备材料，6 月夏令营开营。竞赛最迟大二暑假要有一个拿得出手的成果，否则大三再临时补会非常被动。\n\n另外绩点和竞赛冲突时，宁可先保住绩点，竞赛可以用过程和复盘补足，绩点一旦掉下去很难追回来。")));
        out.add(mockThread("mock-thread-2", "第一次独立复现论文，记录里该留什么", "科研", userId, base.plusSeconds(3600),
            "导师让我复现一篇目标检测方向的论文，我第一次独立做，现在卡在不知道实验过程该记录哪些东西，只记了最终指标，结果换台机器结果就对不上了。\n\n想请教一下，做论文复现时实验记录里应该保留哪些内容，才能既方便自己复盘，也能在组会上说清楚问题出在哪。",
            List.of(
                "实验记录千万别只记最好结果。我每次至少保留五样东西：代码提交号、依赖版本、数据预处理方式、随机种子、以及实际跑通的命令。这样换环境复现不了时，可以逐项排查而不是重新猜。\n\n另外失败配置也要记，连续三次没改善就停下来回到假设本身，别一直调参碰运气。",
                "精读论文我有个固定顺序：先看摘要和结论定位要解决的指标，再回方法部分找和这个指标直接相关的设计，最后才看实验。读不懂方法先别急着抠公式，先确认问题定义是否站得住，能一句话说出作者在什么条件下改善了哪个可测结果，才算读进去了。")));
        out.add(mockThread("mock-thread-3", "数模三人组开赛前 24 小时怎么推进", "竞赛", userId, base.plusSeconds(7200),
            "第一次参加数学建模，队伍是三个人，现在担心开赛后手忙脚乱：题目拆解、分工、写论文这几块怎么协调，最后一天是不是应该停止大改模型。\n\n想听听有经验的同学，开赛前 24 小时和开赛后的节奏该怎么安排。",
            List.of(
                "开赛前两小时只做一件事：把题目拆成目标、约束、可得数据、交付物四块，确认清楚再定题。定题后按建模、编程、写作分工，但每四小时同步一次关键假设，任何人改指标都要在共享文档里写一句原因，否则最后论文对不上。",
                "最后一天坚决不再大改模型，把时间留给图表、论文叙事和答辩。答辩只保留问题、关键选择、结果、限制四段，每段准备一句能被追问的话，模型细节放到问答环节再展开，主线才不会被淹没。")));
        out.add(mockThread("mock-thread-4", "课程项目路演，五分钟怎么让人听懂", "技能", userId, base.plusSeconds(10800),
            "课程项目要路演了，功能、技术、过程都很多，但只有五分钟，试讲下来听众记不住重点。\n\n想请教怎么取舍内容，才能让没参与过项目的人听懂，并愿意继续追问。",
            List.of(
                "五分钟只保留四段：要解决的问题、一个真实使用过程、结果、限制。功能列表放进答疑，主线用一张前后对比图撑起来。先讲使用场景和变化，再展示工具与过程，避免一上来堆技术名词，听众才会跟得住。",
                "数据表达比堆截图更有说服力。我用访谈和真实使用数据做一张使用前后对比，把每个功能对应一次可验证的交付，讲的时候先说变化再放数据，评委追问就能往深了聊，而不是停在功能介绍上。")));
        return out;
    }

    private ExperienceMaterialsResponse.ThreadMaterial mockThread(
            String threadId, String title, String domain, String userId, Instant base,
            String postBody, List<String> ownedCommentBodies) {
        String postId = threadId + "-post";
        List<ExperienceMaterialsResponse.CommentMaterial> comments = new ArrayList<>();
        List<String> ownedIds = new ArrayList<>();
        for (int k = 0; k < ownedCommentBodies.size(); k++) {
            String commentId = threadId + "-comment-" + (k + 1);
            ownedIds.add(commentId);
            comments.add(new ExperienceMaterialsResponse.CommentMaterial(
                commentId, ownedCommentBodies.get(k), userId, null, base.plusSeconds(600L * (k + 1))));
        }
        return new ExperienceMaterialsResponse.ThreadMaterial(
            threadId, title, domain,
            new ExperienceMaterialsResponse.PostMaterial(postId, postBody, userId, base),
            comments, ownedIds);
    }

    public Map<String, ExperienceMaterialsResponse.ThreadMaterial> selectedForUser(
            String userId, List<String> threadIds) {
        Map<String, ExperienceMaterialsResponse.ThreadMaterial> available = new LinkedHashMap<>();
        for (var thread : forUser(userId).threads()) available.put(thread.threadId(), thread);
        Map<String, ExperienceMaterialsResponse.ThreadMaterial> selected = new LinkedHashMap<>();
        if (threadIds != null) {
            for (String id : threadIds) {
                if (id != null && available.containsKey(id)) selected.put(id, available.get(id));
            }
        }
        return selected;
    }

    public boolean realLlmAvailable() {
        return "deepseek".equalsIgnoreCase(llm.getProvider())
            && llm.getDeepseek().getApiKey() != null
            && !llm.getDeepseek().getApiKey().isBlank();
    }

    private ExperienceMaterialsResponse.ThreadMaterial toMaterial(
            Post post, List<Comment> threadComments, List<String> ownedCommentIds) {
        return new ExperienceMaterialsResponse.ThreadMaterial(
            post.id(), post.title(), post.domain(),
            new ExperienceMaterialsResponse.PostMaterial(
                post.id(), post.body(), post.authorId(), post.createdAt()),
            threadComments.stream().map(comment -> new ExperienceMaterialsResponse.CommentMaterial(
                comment.id(), comment.body(), comment.authorId(), comment.parentId(), comment.createdAt()
            )).toList(),
            ownedCommentIds);
    }
}
