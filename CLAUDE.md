# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

抖音黑客松参赛项目「大学生成长 Skill 共创场」。三大模块：**对话**（AI 按 Skill 多学长并发回答）、**社区**（帖子瀑布流）、**Skill 仓库**（学长.Skill 上传/检索）。设计语言小红书风格。

## 运行

后端 (Spring Boot 3, JDK 21, Maven)：
```bash
cd backend
export JAVA_HOME=C:/Users/17551/.jdks/ms-21.0.9
mvn spring-boot:run
```

前端 (Vue 3 + Vite + TypeScript + Tailwind)：
```bash
cd frontend
npm install
npm run dev   # http://localhost:5173
npm run build # 生产构建（vue-tsc 类型检查 + vite build）
```

Maven 走 aliyun mirror，配置在 `C:\Users\17551\.m2\settings.xml`，无需翻墙。

## 架构概览

```
大学生成长Skill共创场/
  frontend/src/
    views/         HomeView, CommunityView, SeniorsView, SeniorDetailView, MeView
    components/    common/TopBar  chat/AnswerCard,ChatComposer  senior/SeniorCard
    stores/        chatStore (messages + sessionId)  seniorStore (items + domain filter)
    services/      chatService, seniorService, communityService  — 每个都有 mock fallback
    types/index.ts — 所有共享接口类型
    style.css      — 全部 CSS（@layer components 自定义类 + 两套媒体查询）
  backend/src/main/java/com/skillhub/
    config/  InitRunner (启动顺序守卫), WebConfig (CORS)
    model/   SeniorSkill, SeniorSkillDetail, CommunityPost, ChatMessageEntity
    dto/     ChatRequest (class, 非 record!), ChatResponse
    repo/    接口层 + sqlite/ 实现（SqliteSchema 建表, SqliteSeniorSkillRepository 等）
    service/ SeniorReader (扫目录/读文件), ChatOrchestrator (排序选人+mock生成), SeniorIngestService (zip上传)
    controller/ ChatController, SeniorController, CommunityController
  backend/data/
    skillhub.db          — SQLite 数据库（运行时生成）
    seniors/<id>/        — 学长.Skill 七件套目录
```

## 关键设计点

**启动顺序**：`InitRunner.@PostConstruct` 保证 `SqliteSchema.init()` 先于 `SeniorReader.scanOnBoot()` 执行，避免扫描时表尚未建立。

**ChatRequest 必须用 class**：WebFlux 的 `@RequestBody` 反序列化需要 setter，Java record 没有 setter 会产生 400。已转为带 getter/setter 的普通 class。

**学长.Skill 七件套约束**：`SeniorReader` 检查目录内必须同时存在 `SKILL.md, manifest.json, meta.json, work.md, persona.md, work_skill.md, persona_skill.md` 才入库。`manifest.json` 提供 `name/domain/avatar`，`meta.json.identity` 提供 `school/college/major/year_graduated`。

**选人逻辑（当前 mock）**：`ChatOrchestrator.orchestrate()` 对每个候选人的 SKILL.md 前 280 字与用户消息做 Jaccard 相似度 + domain 命中加分，取 Top 3。替换为真实 LLM 时只需改 `synthesizeAnswer()` 方法。

**前端 fallback**：`seniorService` 和 `communityService` 在请求后端失败时都返回本地 mock 数据，开发时不依赖后端也可运行。

**CSS token**：`src/style.css` `:root` 定义 `--paper, --surface, --ink, --ink-soft, --ink-faint, --line, --pink, --pink-soft, --blue, --green`，`tailwind.config.js` 的 `colors` 映射到这些变量（不要用旧的 `--background/--primary` 等）。

**移动端 composer 定位**：底部 tab bar `height: 68px`，`.composer { bottom: 68px }` 在 `@media (max-width: 720px)` 里覆写，避免被遮挡。桌面端导航是右下角 pill，不影响。

## API 契约

```
POST /api/chat
  body: { message: string, sessionId?: string }
  resp: { sessionId, answers: [{ seniorId, name, school, major, year, content }] }

GET  /api/seniors?domain=&school=     → { items: SeniorSkill[] }
GET  /api/seniors/:id                 → SeniorSkillDetail
POST /api/seniors/upload              → multipart zip
GET  /api/seniors/:id/avatar          → image/svg+xml

GET  /api/community/posts             → { items: CommunityPost[] }
POST /api/community/posts             → CommunityPost
```

## 数据库

SQLite，路径 `backend/data/skillhub.db`（运行时自动创建）。三张表：`senior_skills, community_posts, chat_messages`。Repository 接口抽象，切 MySQL 只需换实现 + datasource 配置，不动 service 层。

## 当前状态

框架已跑通，`npm run build` 零错误。对话回答是 mock（从 SKILL.md 截取片段）。社区帖、学长列表有 fallback mock 数据。接入真实 LLM 的扩展点在 `ChatOrchestrator.synthesizeAnswer()`。
