# 项目接入建议

## 最小服务链路

```text
CommunityRepository 导出某用户全部发言及相关帖子 JSON
→ prepare_fragments.py
→ LLM 按 SKILL.md 生成 distillation.json
→ write_skill_bundle.py
→ validate_skill_bundle.py
→ 作者审核
→ 上传到 backend/data/seniors/<skill-id>/
→ SeniorReader.ingestIfValid()
```

## 推荐的异步任务状态

| 状态 | 含义 |
| --- | --- |
| `collecting` | 汇总目标用户发言与相关线程 |
| `fragmenting` | 重建上下文片段 |
| `insufficient` | 未达到完整 Skill 门槛，只保存碎片经验 |
| `distilling` | 生成证据化蒸馏对象 |
| `awaiting_author_review` | 等待本人确认事实、边界和身份 |
| `validating` | 验证七件套与证据引用 |
| `published` | 已获授权并进入仓库 |
| `rejected` | 作者拒绝或安全审查未通过 |

## 后端数据建议

除现有 `senior_skills` 外，建议后续增加：

- `experience_fragments`：`fragment_id`、`user_id`、`thread_id`、正文摘要、来源消息、主题、时效和授权；
- `distillation_jobs`：任务状态、输入快照、模型版本、成熟度、错误与审核人；
- `skill_versions`：Skill ID、版本、来源 fragment、审核状态和发布时间；
- `skill_run_feedback`：运行绑定的 Skill 版本、采纳状态、完成状态和失败原因。

## LLM 调用边界

脚本不调用模型。服务层应把以下内容交给 LLM：

1. `SKILL.md` 的蒸馏流程；
2. `references/distillation-protocol.md`；
3. `references/distillation-schema.md`；
4. 当前 `fragments.json`；
5. 期望只输出 `distillation.json` 的结构化约束。

写入和验证必须继续由脚本执行，避免模型直接写仓库文件或绕过证据检查。

## 演示建议

现场展示一位用户的三条独立讨论：先显示原帖和回复如何重建成三个 fragment，再显示成熟度评分和 Skill 草稿，最后点开 `sources.json` 回溯原始线程。另准备一个只有一条评论的用户，展示系统主动降级为碎片经验而不生成 Skill。
