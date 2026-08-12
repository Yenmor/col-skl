# 最终形态架构（TODO 对位）

> 对应 `Desktop/TODO.md` 的最终形态。三端：Vue（浏览器）、Java（Spring Boot 3）、Python（FastAPI，AI 服务）。
> Java 通过 HTTP 调 Python。Python 内部不展开。

## 总图

```mermaid
flowchart LR
    subgraph Vue["Vue 端 — 浏览器"]
        VUI["页面 / 组件 / 状态<br/>对话 · 社区 · Skill 仓库 · 我的"]
    end

    subgraph Java["Java 端 — Spring Boot 3 + WebFlux"]
        JAPI["REST / SSE 接口层"]
        JCore["业务编排<br/>账号 · 帖 · 评论 · 点赞 · 对话"]
        JPersist["持久化 (JdbcTemplate)"]
    end

    subgraph Python["Python 端 — FastAPI 独立服务"]
        PAI["AI 能力（黑盒）"]
    end

    subgraph Store["存储"]
        JDB[("SQLite / MySQL<br/>账号 · 帖 · 评论 · 聊天 · 预制数据")]
        PVec[("向量库<br/>帖片段 · 记忆 · 标签")]
        PSkill[("Skill 文件仓库<br/>seniors/&lt;id&gt;/* 七件套")]
    end

    VUI -- "HTTP / SSE（JSON · 分页 · 文件上传）" --> JAPI
    JAPI --> JCore
    JCore --> JPersist
    JPersist --- JDB

    JCore -- "HTTP：<br/>PostBundle JSON · UserPostBundle JSON ·<br/>question + 候选 Skill · 预制数据落库" --> PAI
    PAI -- "HTTP：<br/>向量片段 + 标签 · 回答 · 预制 Skill 落盘" --> JCore

    PAI --- PVec
    PAI --- PSkill
    JCore -. "读 SKILL.md / 写 zip 上传" .-> PSkill
```

## TODO 对位

| # | TODO | 端 |
|---|---|---|
| 1 | 完整社区（账号 / 发帖 / 评论） | Vue + Java |
| 2 | 帖子分析 Agent → 向量库 | Python |
| 3 | 帖 / 用户帖 JSON 打包 | Java |
| 4 | RAG 召回 | Python |
| 5 | metaskill（生成 Skill 的 Skill） | Python（未展开） |
| 6 | 人格蒸馏流程 | Python（未展开） |
| 7 | 协作接口 / 数据结构 | Java + Python |
| 8 | 前端默认对话：选人 + RAG + 加载 | Vue + Java + Python |
| 9 | 方块视觉效果 | Vue |
| 10 | 分类器（片段打标签） | Python |
| 11 | 预制数据入库 | Java |
| 12 | 用 metaskill 蒸馏预制人 Skill | Python（未展开） |
