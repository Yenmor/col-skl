package com.skillhub.service;

import com.skillhub.model.Comment;
import com.skillhub.model.Post;
import com.skillhub.model.User;
import com.skillhub.repo.CommentRepository;
import com.skillhub.repo.PostRepository;
import com.skillhub.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Idempotent local showcase data. Disable with SKILLHUB_DEMO_DATA=false. */
@Component
public class DemoDataSeeder {
    public static final String DEMO_USER_ID = "11111111-1111-4111-8111-111111111111";
    private static final String PEER_A = "22222222-2222-4222-8222-222222222222";
    private static final String PEER_B = "33333333-3333-4333-8333-333333333333";
    private static final String PEER_C = "44444444-4444-4444-8444-444444444444";

    private final boolean enabled;
    private final UserRepository users;
    private final PostRepository posts;
    private final CommentRepository comments;
    private final JdbcTemplate jdbc;

    public DemoDataSeeder(@Value("${skillhub.demo-data.enabled:true}") boolean enabled,
                          UserRepository users, PostRepository posts,
                          CommentRepository comments, JdbcTemplate jdbc) {
        this.enabled = enabled;
        this.users = users;
        this.posts = posts;
        this.comments = comments;
        this.jdbc = jdbc;
    }

    public void seed() {
        if (!enabled) return;
        saveUser(DEMO_USER_ID, "演示同学");
        saveUser(PEER_A, "周同学");
        saveUser(PEER_B, "林学长");
        saveUser(PEER_C, "许同学");

        seedPosts();
        seedComments();
        seedLikes();
        recountInteractions();
        seedSkillTrustShowcase();

        // This complete bundled manual Skill remains public and demonstrates the uploader-owned path.
        // It has no owner metadata in the upstream bundle, so the DB ownership survives rescans.
        jdbc.update("UPDATE senior_skills SET owner_id=?, visibility='PUBLIC' WHERE id=?",
            DEMO_USER_ID, "chen-baoyan");
    }

    private void seedSkillTrustShowcase() {
        // Explicit showcase facts. They are labelled demo in the API and never inferred from package shape.
        jdbc.update("""
            INSERT INTO skill_trust_facts
              (skill_id, source_confirmed, source_authorized, source_note,
               authority_channels_json, ai_score, ai_model, ai_note, demo, updated_at)
            VALUES (?,1,1,?,?,?,?,?,1,?)
            ON CONFLICT(skill_id) DO UPDATE SET
              source_confirmed=excluded.source_confirmed,
              source_authorized=excluded.source_authorized,
              source_note=excluded.source_note,
              authority_channels_json=excluded.authority_channels_json,
              ai_score=excluded.ai_score,
              ai_model=excluded.ai_model,
              ai_note=excluded.ai_note,
              demo=excluded.demo,
              updated_at=excluded.updated_at
            """,
            "chen-baoyan",
            "演示回访：贡献者确认时间线与方法表达，并同意在演示环境公开。",
            "[\"学校教务处公开推免通知（演示）\",\"学院培养方案（演示）\"]",
            84,
            "campus-trust-demo-v1",
            "仅检查方法与公开材料的一致性、边界和过时风险。",
            Instant.parse("2026-08-13T02:00:00Z").toString());

        seedSkillLikes("chen-baoyan", List.of(DEMO_USER_ID, PEER_A, PEER_B, PEER_C));
        for (int i = 0; i < 9; i++) {
            jdbc.update("INSERT OR IGNORE INTO skill_download_events (id, skill_id, user_id, created_at) VALUES (?,?,?,?)",
                "demo-skill-download-" + i, "chen-baoyan",
                i % 2 == 0 ? PEER_A : PEER_B, "2026-08-13T02:" + String.format("%02d", i) + ":00Z");
        }
        seedSkillComment("demo-skill-comment-1", PEER_A,
            "把月份节点和停止条件放在一起后，我更容易判断自己该补哪一项。", "2026-08-13T03:10:00Z");
        seedSkillComment("demo-skill-comment-2", PEER_B,
            "公开政策每年会变化，页面把权威渠道和 AI 分开显示很重要。", "2026-08-13T03:20:00Z");
        seedSkillComment("demo-skill-comment-3", PEER_C,
            "我下载后按自己的学校通知重新核对了一遍，时间线适合作为检查表。", "2026-08-13T03:30:00Z");
    }

    private void seedSkillLikes(String skillId, List<String> userIds) {
        for (String userId : userIds) {
            jdbc.update("INSERT OR IGNORE INTO skill_likes (user_id, skill_id, created_at) VALUES (?,?,?)",
                userId, skillId, "2026-08-13T03:00:00Z");
        }
    }

    private void seedSkillComment(String id, String userId, String body, String createdAt) {
        jdbc.update("INSERT OR IGNORE INTO skill_comments (id, skill_id, user_id, body, created_at) VALUES (?,?,?,?,?)",
            id, "chen-baoyan", userId, body, createdAt);
    }

    private void seedPosts() {
        savePost(new Post(
            "demo-study-review", "我把期末复盘改成了一张知识拆解表",
            "先拆概念，再连接错题和课堂笔记，最后给同伴讲解。",
            "这学期我不再按章节抄笔记，而是把每个复杂概念拆成定义、例题、易错点和关联知识。每周把错题重新归到这张表，再找同学做一次同伴讲解。三轮下来，复盘时间更短，也更容易发现自己只是记住了结论、没有理解推导。",
            "#eafbef", DEMO_USER_ID, "演示同学", null, "学习", 6, 0,
            Instant.parse("2026-08-08T09:20:00Z")));
        savePost(new Post(
            "demo-study-notes", "把散落笔记重构成一张复习地图",
            "不再重新抄一遍，只补关系、断点和下一步。",
            "这次我只处理旧笔记：每页先写一个问题，把重复内容合并，再用箭头标出前后关系。每周复盘只补断点，不重新抄一遍。两周后再看，能直接定位卡住的位置，也知道下一次该补哪一块。",
            "#eafbef", DEMO_USER_ID, "演示同学", null, "学习", 4, 0,
            Instant.parse("2026-08-08T13:40:00Z")));
        savePost(new Post(
            "demo-research-experiment", "第一次独立复现实验，我保留了哪些记录",
            "论文精读、问题定义、实验记录和停止条件。",
            "第一次做论文复现时，我先用一句话写清问题定义，再把论文精读中的数据、指标和基线逐项核对。实验记录不只记最好结果，也保留环境、随机种子、失败配置和每次改动。连续三次没有改善时停止调参，回到假设本身检查，而不是继续碰运气。",
            "#fff8da", DEMO_USER_ID, "演示同学", null, "科研", 4, 0,
            Instant.parse("2026-08-09T11:10:00Z")));
        savePost(new Post(
            "demo-competition-retro", "数模三人组前 24 小时怎么推进",
            "赛题拆解后明确分工，提前准备答辩表达。",
            "开赛前两小时只做赛题拆解：目标、约束、可得数据和交付物。确定题目后按建模、编程、写作分工，但每四小时同步一次关键假设。协作推进中任何人改指标都要在共享记录里说明。最后一天不再大改模型，把时间留给图表、论文叙事和答辩表达。",
            "#fff0eb", DEMO_USER_ID, "演示同学", null, "竞赛", 5, 0,
            Instant.parse("2026-08-10T07:45:00Z")));
        savePost(new Post(
            "demo-competition-pitch", "答辩表达只留一条主线后，评委终于听懂了",
            "用一次追问测试叙事，把模型细节留到问答。",
            "第一次模拟答辩时，我们五分钟塞了十二张图。第二次只保留问题、关键选择、结果和限制四段，每段都准备一句可以被追问的话。队友扮演评委连续打断两轮后，我们删掉了三个解释不清的结论，现场表达反而更完整。",
            "#fff0eb", DEMO_USER_ID, "演示同学", null, "竞赛", 5, 0,
            Instant.parse("2026-08-10T14:30:00Z")));
        savePost(new Post(
            "demo-skill-project", "校园项目展示前，我怎样整理数据和故事",
            "用数据表达支撑公开表达，把创意实践落到交付。",
            "做校园工具项目时，我先明确要解决的真实问题，再用访谈和使用数据做分析与可视化。创意实践不能只剩界面截图，每个功能都要对应一次可验证的项目交付。公开表达时先讲使用场景和变化，再展示工具、过程与限制，避免堆技术名词。",
            "#e8f9ff", DEMO_USER_ID, "演示同学", null, "技能", 7, 0,
            Instant.parse("2026-08-11T13:30:00Z")));
        savePost(new Post(
            "demo-club-workshop", "社团工作坊从报名到复盘的完整记录",
            "把自定义主题中的经验沉淀和同伴反馈留下来。",
            "我把社团活动作为自己的第五层主题。先按新生真实问题梳理主题脉络，再记录报名、到场和提问情况。活动结束后收集同伴反馈，把有效流程沉淀成下一次可复用的清单；没有得到验证的判断只保留为待确认问题。",
            "#f4edff", DEMO_USER_ID, "演示同学", null, "自定义", 3, 0,
            Instant.parse("2026-08-12T08:00:00Z")));
        savePost(new Post(
            "peer-research-question", "读论文时总抓不到作者真正解决的问题",
            "我能看懂方法，但不知道怎样判断问题定义是否站得住。",
            "我能看懂方法和公式，但读完后还是说不清作者真正解决了什么。大家精读论文时会先看哪里，又怎样判断问题定义有没有研究价值？",
            "#fff8da", PEER_A, "周同学", null, "科研", 2, 0,
            Instant.parse("2026-08-07T15:00:00Z")));
        savePost(new Post(
            "peer-skill-question", "第一次做项目路演，五分钟应该留下什么",
            "功能很多，但讲完之后听众记不住。",
            "课程项目准备路演，功能、技术和过程都很多，五分钟根本讲不完。怎样取舍内容，才能让没有参与项目的人听懂并愿意继续问？",
            "#e8f9ff", PEER_B, "林学长", null, "技能", 3, 0,
            Instant.parse("2026-08-06T10:25:00Z")));
        savePost(new Post(
            "peer-study-explain", "给同学讲题时，总是讲着讲着自己也乱了",
            "答案会做，但一开口就找不到清楚的顺序。",
            "最近和室友互相讲题。我自己做时觉得已经会了，真正开口却常常跳步骤，对方一追问就要从头再想。怎样安排一次短讲，才能判断自己到底哪里没说清？",
            "#eafbef", PEER_C, "许同学", null, "学习", 2, 0,
            Instant.parse("2026-08-08T16:10:00Z")));
        savePost(new Post(
            "peer-research-lab", "复现实验换一台电脑就跑不出同样结果",
            "代码没改，环境和随机结果却对不上。",
            "同一份代码在实验室电脑能运行，换到自己的电脑后指标差了一截。我之前只记了最终参数，没有完整保留中间过程。大家会记录哪些内容，才能判断是环境、数据还是随机性的问题？",
            "#fff8da", PEER_C, "许同学", null, "科研", 2, 0,
            Instant.parse("2026-08-09T16:20:00Z")));
        savePost(new Post(
            "peer-club-feedback", "社团分享会结束后，怎样收集真正有用的反馈",
            "大家都说不错，但下次仍不知道该改什么。",
            "活动结束后的问卷几乎都是‘很好’和‘有收获’，很难指导下一场。除了满意度之外，怎样问才能知道同学在哪一步跟不上、哪些内容真的会带走使用？",
            "#f4edff", PEER_A, "周同学", null, "自定义", 2, 0,
            Instant.parse("2026-08-12T11:30:00Z")));
    }

    private void seedComments() {
        saveComment(new Comment("reply-study-1", "demo-study-review", PEER_A, "周同学", null, null,
            "这张表里把易错点和关联知识放在一起很有用，我准备拿数据结构试一次。", Instant.parse("2026-08-08T10:00:00Z")));
        saveComment(new Comment("reply-study-2", "demo-study-review", PEER_B, "林学长", null, null,
            "同伴讲解还能暴露术语会背、因果说不清的问题。", Instant.parse("2026-08-08T10:15:00Z")));
        saveComment(new Comment("reply-study-notes-1", "demo-study-notes", PEER_C, "许同学", null, null,
            "只补断点这个规则很实用，不会又陷入重新誊抄。", Instant.parse("2026-08-08T14:05:00Z")));
        saveComment(new Comment("reply-research-1", "demo-research-experiment", PEER_A, "周同学", null, null,
            "保留失败配置这点很关键，不然第二周很容易重复走旧路。", Instant.parse("2026-08-09T12:00:00Z")));
        saveComment(new Comment("reply-competition-1", "demo-competition-retro", PEER_B, "林学长", null, null,
            "四小时同步一次假设，比最后才拼论文稳很多。", Instant.parse("2026-08-10T09:10:00Z")));
        saveComment(new Comment("reply-competition-pitch-1", "demo-competition-pitch", PEER_A, "周同学", null, null,
            "先删掉解释不清的结论，比继续加图更能稳住主线。", Instant.parse("2026-08-10T15:00:00Z")));
        saveComment(new Comment("reply-competition-pitch-2", "demo-competition-pitch", PEER_C, "许同学", null, null,
            "让队友连续打断的模拟方式很接近真实答辩，我会试试。", Instant.parse("2026-08-10T15:18:00Z")));
        saveComment(new Comment("reply-skill-1", "demo-skill-project", PEER_A, "周同学", null, null,
            "先讲使用场景以后，技术选择确实更容易被理解。", Instant.parse("2026-08-11T14:20:00Z")));
        saveComment(new Comment("reply-club-1", "demo-club-workshop", PEER_B, "林学长", null, null,
            "报名和到场差异也值得记录，能反推通知方式是否有效。", Instant.parse("2026-08-12T09:00:00Z")));

        saveComment(new Comment("demo-comment-research", "peer-research-question", DEMO_USER_ID, "演示同学", null, null,
            "我会先写一句‘作者在什么条件下，要改善哪个可测结果’，再回到摘要和实验核对。写不出来时先别急着看方法，说明问题定义还没读清。", Instant.parse("2026-08-07T15:30:00Z")));
        saveComment(new Comment("peer-reply-research", "peer-research-question", PEER_A, "周同学", null, "demo-comment-research",
            "这个句式很清楚，我重新读摘要后终于能把问题说出来了。", Instant.parse("2026-08-07T16:00:00Z")));
        saveComment(new Comment("demo-comment-skill", "peer-skill-question", DEMO_USER_ID, "演示同学", null, null,
            "五分钟只保留问题、一个真实使用过程、结果和限制。功能列表放到答疑，主线用一张前后对比图支撑公开表达。", Instant.parse("2026-08-06T11:00:00Z")));
        saveComment(new Comment("peer-reply-skill", "peer-skill-question", PEER_B, "林学长", null, "demo-comment-skill",
            "按这四段重排后顺多了，功能细节放答疑也没有丢。", Instant.parse("2026-08-06T11:40:00Z")));
        saveComment(new Comment("demo-comment-study-explain", "peer-study-explain", DEMO_USER_ID, "演示同学", null, null,
            "我会把同伴讲解限制在三分钟：先说这题要判断什么，再讲一个关键转折，最后让对方复述。对方复述不出来的位置，就是下一轮要补的地方。", Instant.parse("2026-08-08T16:35:00Z")));
        saveComment(new Comment("peer-reply-study-explain", "peer-study-explain", PEER_C, "许同学", null, "demo-comment-study-explain",
            "三分钟和最后复述都很具体，今晚就拿一道题试。", Instant.parse("2026-08-08T17:00:00Z")));
        saveComment(new Comment("demo-comment-research-lab-1", "peer-research-lab", DEMO_USER_ID, "演示同学", null, null,
            "我会先补一份实验记录：代码提交号、依赖版本、数据摘要、随机种子和实际命令各占一行。先固定环境重跑，再一次只改一个变量，避免把几个原因混在一起。", Instant.parse("2026-08-09T16:45:00Z")));
        saveComment(new Comment("peer-reply-research-lab", "peer-research-lab", PEER_C, "许同学", null, "demo-comment-research-lab-1",
            "查到是数据预处理版本不一致，按一项一项固定确实很快定位了。", Instant.parse("2026-08-09T17:20:00Z")));
        saveComment(new Comment("demo-comment-research-lab-2", "peer-research-lab", DEMO_USER_ID, "演示同学", null, "peer-reply-research-lab",
            "那就把这次差异也写进复现记录，并补上停止条件；下次遇到指标漂移，可以直接按同一顺序排查。", Instant.parse("2026-08-09T17:35:00Z")));
        saveComment(new Comment("demo-comment-club-feedback", "peer-club-feedback", DEMO_USER_ID, "演示同学", null, null,
            "我会把同伴反馈改成三个行为问题：哪一步停住、活动后会实际使用什么、如果删掉一段会删哪段。再找两位参与者做五分钟追问，比单看满意度更有信息。", Instant.parse("2026-08-12T12:00:00Z")));
        saveComment(new Comment("peer-reply-club-feedback", "peer-club-feedback", PEER_A, "周同学", null, "demo-comment-club-feedback",
            "行为问题比‘觉得怎么样’容易回答多了，也能直接决定下次删改。", Instant.parse("2026-08-12T12:25:00Z")));
    }

    private void seedLikes() {
        like("demo-study-review", PEER_A, "2026-08-08T10:02:00Z");
        like("demo-study-review", PEER_B, "2026-08-08T10:03:00Z");
        like("demo-study-review", PEER_C, "2026-08-08T10:04:00Z");
        like("demo-study-notes", PEER_A, "2026-08-08T14:07:00Z");
        like("demo-study-notes", PEER_B, "2026-08-08T14:08:00Z");
        like("demo-research-experiment", PEER_A, "2026-08-09T12:02:00Z");
        like("demo-research-experiment", PEER_B, "2026-08-09T12:03:00Z");
        like("demo-competition-retro", PEER_A, "2026-08-10T09:12:00Z");
        like("demo-competition-retro", PEER_B, "2026-08-10T09:13:00Z");
        like("demo-competition-pitch", PEER_A, "2026-08-10T15:02:00Z");
        like("demo-competition-pitch", PEER_B, "2026-08-10T15:03:00Z");
        like("demo-competition-pitch", PEER_C, "2026-08-10T15:20:00Z");
        like("demo-skill-project", PEER_A, "2026-08-11T14:22:00Z");
        like("demo-skill-project", PEER_B, "2026-08-11T14:23:00Z");
        like("demo-club-workshop", PEER_B, "2026-08-12T09:02:00Z");
        like("peer-research-question", DEMO_USER_ID, "2026-08-07T15:35:00Z");
        like("peer-skill-question", DEMO_USER_ID, "2026-08-06T11:05:00Z");
        like("peer-study-explain", DEMO_USER_ID, "2026-08-08T16:37:00Z");
        like("peer-research-lab", DEMO_USER_ID, "2026-08-09T16:47:00Z");
        like("peer-club-feedback", DEMO_USER_ID, "2026-08-12T12:02:00Z");
    }

    private void like(String postId, String userId, String createdAt) {
        jdbc.update("INSERT OR IGNORE INTO post_likes (user_id, post_id, created_at) VALUES (?, ?, ?)",
            userId, postId, createdAt);
        jdbc.update("UPDATE posts SET like_count=(SELECT COUNT(*) FROM post_likes WHERE post_id=?) WHERE id=?",
            postId, postId);
    }

    private void recountInteractions() {
        jdbc.update("""
            UPDATE posts
            SET like_count=(SELECT COUNT(*) FROM post_likes WHERE post_id=posts.id),
                comment_count=(SELECT COUNT(*) FROM post_comments WHERE post_id=posts.id)
            WHERE id LIKE 'demo-%' OR id LIKE 'peer-%'
            """);
    }

    private void saveUser(String id, String name) {
        // 无条件 upsert：默认登录身份(演示同学)可能先被 getOrCreate 以“游客#xxxx”
        // 懒创建过，这里必须把展示名覆盖回演示名（UserRepository.save 是 ON CONFLICT DO UPDATE）。
        users.save(new User(id, name, null, "GUEST", Instant.parse("2026-08-01T00:00:00Z")));
    }

    private void savePost(Post post) {
        if (posts.findById(post.id()).isEmpty()) posts.save(post);
    }

    private void saveComment(Comment comment) {
        if (comments.findById(comment.id()).isEmpty()) comments.save(comment);
    }
}
