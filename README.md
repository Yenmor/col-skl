# CSg · 大学生成长 Skill 共创场

抖音黑客松参赛项目。社区 + Skill 仓库 + AI 对话三大模块，对话时由 LLM 从「学长.Skill」仓库里挑选若干位学长独立作答。

## 当前阶段

仅搭建主框架，所有回答/学长数据均为 mock。社区 + Skill 仓库 + 对话三大模块均可独立运行，跨模块接口已对齐。

## 三大模块

| 模块 | 入口 | 说明 |
| --- | --- | --- |
| 对话 | `/` | 顶部 tab，默认页。提问 → 后端挑 Top 3 学长.Skill → 并发得到 3 段独立回答 → 红书风三列卡片展示 |
| 社区 | `/community` | 公开帖瀑布流，发帖 / 互动入口预留，详情页 M3 接 |
| Skill 仓库 | `/seniors` | 学长.Skill 瀑布，按领域横切 + 按学校过滤；详情页打开 SKILL.md / 风格摘要 / 来源 |
| 我的 | `/me` | 框架页 |

## 学长.Skill 的形态

沿用 [colleague-skill](https://github.com/titanwings/colleague-skill) 蒸馏产物形态（7 件套）：

```
seniors/<id>/
  SKILL.md            # 能力 + 行为双轨道（prompt 注入内容）
  manifest.json       # 展示元数据（领域 / 标签 / 头像）
  meta.json           # 身份元数据（学校 / 专业 / 年级 / 出处）
  work.md             # 经验 / 能力原文
  persona.md          # 语气 / 行为原文
  work_skill.md       # 能力子 Skill
  persona_skill.md    # 行为子 Skill
```

仓库默认放在 `backend/data/seniors/`。上传：用户拖拽一个含七件套的 zip 到前端 → 后端解压 → 元数据入库 → 列表中可见。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 前端 | Vue 3 + Vite + TypeScript + Tailwind CSS + Pinia + Vue Router |
| 后端 | Spring Boot 3 + WebFlux + Maven |
| 持久 | SQLite（开发） / MySQL（生产） via Repository 抽象 |
| LLM | 默认 mock；预制接口可换 Anthropic Claude / OpenAI |

## 跑

后端：
```bash
cd backend
export JAVA_HOME=C:/Users/17551/.jdks/ms-21.0.9
mvn spring-boot:run
```

前端：
```bash
cd frontend
npm install
npm run dev
```
