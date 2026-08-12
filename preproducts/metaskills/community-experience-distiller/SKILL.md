---
name: community-experience-distiller
description: 将某位用户在社区中的全部发帖、评论、回复及相关帖子 JSON 重建为有语义且可追溯的对话片段，评估特定领域经验是否达到 Skill 蒸馏门槛，并生成与大学生成长 Skill 共创场兼容的七件套学长 Skill 草稿。用于社区经验供给、用户发言蒸馏、学长 Skill 创建、碎片经验入库、来源追溯、增量更新和本人确认前审计；不要用于无授权的人格复制、心理诊断或仅凭少量发言生成完整 Skill。
---

# 社区经验 Skill 蒸馏器

把社区发言视为证据，而不是直接当作 Prompt。先重建对话语境，再分别蒸馏任务能力与表达偏好，最后生成可审核、可追溯、可降级的 Skill 草稿。

## 核心原则

1. **领域化，不复制整个人。** 每次只蒸馏一个明确任务领域，例如“计算机专业选课避坑”，不生成所谓完整人格或数字分身。
2. **证据先于结论。** 每条方法、判断、边界和表达规则必须引用 `fragment_id` 与原始 `message_id`；无来源的结论不得进入 Skill。
3. **上下文先于单句。** 不孤立解释评论。必须包含根帖子、回复祖先、直接回应和必要邻近消息，再分析目标用户的发言。
4. **成熟能力与碎片经验双层供给。** 通过成熟度门槛才生成完整 Skill；未通过时输出经验片段与缺口，不强行补全。
5. **能力与表达偏好双轨蒸馏。** `work` 描述如何完成任务；`persona` 只描述在该领域内可观察的沟通与不确定性处理方式。
6. **本人确认后发布。** 自动产物必须标记为 `draft`。只有输入明确记录本人授权且本人审阅结果后，才允许标记为 `reviewed` 或 `publishable`。
7. **不推断敏感属性。** 不从发言猜测心理健康、政治立场、宗教、性取向、疾病、家庭状况或其他敏感身份。

## 输入与输出

输入必须是包含目标用户资料及其所有相关帖子、评论和回复的 JSON。开始前读取 [input-schema.md](references/input-schema.md)。字段不完全一致时允许做显式映射，但不得丢失原始 ID、作者、时间、回复关系和正文。

完整 Skill 的中间蒸馏对象必须符合 [distillation-schema.md](references/distillation-schema.md)。成熟度判断、证据规则与冲突处理详见 [distillation-protocol.md](references/distillation-protocol.md)。

把本 metaskill 接入当前 Spring Boot 项目时，读取 [project-integration.md](references/project-integration.md)。

输出分两种：

- `full_skill`：生成后端兼容的七件套 `SKILL.md`、`manifest.json`、`meta.json`、`work.md`、`persona.md`、`work_skill.md`、`persona_skill.md`，并额外保存 `sources.json` 供追溯。
- `fragments_only`：不生成完整 Skill，只交付 `fragments.json`、成熟度报告和还缺哪些材料。

## 工作流

### 1. 检查授权与数据契约

确认 JSON 中至少具有：目标用户稳定 ID、内容稳定 ID、作者 ID、正文、所属帖子或回复关系、时间戳和授权状态。若缺少授权，只能生成内部审阅草稿。若目标用户 ID 不明确，停止并要求上游修正数据。

### 2. 重建有语义的对话片段

运行：

```bash
python scripts/prepare_fragments.py input.json --output fragments.json
```

脚本会统一帖子、评论和嵌套回复结构；找出目标用户的全部发言；为每条发言补入根帖子、回复祖先、直接回应及有限邻近上下文；对相同帖子或语义接近的发言保守聚合；保留来源 ID、作者、时间和角色；输出覆盖情况、隐私提示和初步供给模式建议。

先查看脚本的 warnings。不得在回复链断裂、目标发言丢失或 ID 冲突时继续蒸馏。

### 3. 划分候选领域

通读 `fragments.json`，按“用户帮助别人完成的真实任务”划分领域，而不是按抽象兴趣划分。

好的领域包括“软件工程学生如何选择大二专业课”“数学建模三人队如何分工与备赛”“NUS 交换申请的材料与课程置换”。“大学生活”“很会学习”“热心学长”不是合格领域。

若语料支持多个稳定领域，为每个领域分别评估并生成独立 Skill，不生成全能型 Skill。

### 4. 生成证据台账

对每个候选领域逐项记录：重复出现的方法或步骤；会改变路径的条件判断；失败、反例或风险；可观察的完成标准；表达与追问习惯；冲突、过时信息和待本人确认内容。

每项至少引用一个 `fragment_id`；关键方法和决策点优先要求两个不同帖子或任务情境的证据。区分目标用户原话、他人描述、社区事实和模型归纳。

### 5. 执行成熟度门槛

按 [distillation-protocol.md](references/distillation-protocol.md) 从覆盖度、可复现性、条件与边界、证据质量四个维度评分。

仅在以下条件全部成立时选择 `full_skill`：

- 总分至少 12/16；
- 任一维度不低于 2；
- 至少 3 个独立讨论线程；
- 至少 2 个带条件的决策节点；
- 至少 1 条失败、边界或明确“不知道”；
- 核心方法不是从单条孤立发言推断；
- 输出任务和适用用户能够被明确描述。

否则选择 `fragments_only`，说明缺少哪些证据。不得为了产出完整 Skill 而补写常识。

### 6. 双轨蒸馏

#### Work 轨道

提炼任务范围、输入要求、流程、决策节点、完成标准、常见错误、适用与不适用条件、需要人工确认之处。把易变化的事实与长期方法分开；时间敏感信息必须保留日期。

#### Persona 轨道

只提炼有多条直接发言支持的表达偏好，例如先追问背景、喜欢给时间表、如何表达不确定性。不得将语气模仿包装成专业能力，不得写心理动机，不得放大偶发情绪。

### 7. 生成蒸馏对象

按 [distillation-schema.md](references/distillation-schema.md) 写出 `distillation.json`。所有 `evidence` 数组必须引用 `fragments.json` 中存在的 ID。默认设置：

```json
{"review": {"author_confirmed": false, "status": "draft"}}
```

将可能过时、互相冲突或只出现一次的内容放入 `open_questions`，不要进入强规则。

### 8. 写出七件套 Skill

仅在成熟度为 `full_skill` 时运行：

```bash
python scripts/write_skill_bundle.py --distillation distillation.json --fragments fragments.json --output-dir output/<skill-id>
```

写入器会拒绝无效证据引用、缺少边界或错误成熟度，并生成 `sources.json`。不要手工绕过验证器。

### 9. 验证产物

运行：

```bash
python scripts/validate_skill_bundle.py output/<skill-id> --fragments fragments.json
```

验证必须通过：七件套齐全、JSON 可解析、ID 合法、`manifest` 与 `meta` 一致、Skill 含触发/流程/决策/边界、来源引用存在、草稿状态正确。

### 10. 交付本人审核

向原作者展示这个 Skill 解决什么、不解决什么；使用了哪些发言；提炼出的步骤、判断与表达偏好；冲突和不确定项；建议删除或脱敏的内容。

本人确认后才能把 `review.author_confirmed` 改为 `true`，记录确认时间与确认人，并重新生成与验证产物。本人纠正应作为新证据或 correction 记录进入下一版本，不静默覆盖旧结论。

## 增量更新

收到新增发言时，重新生成片段并比较证据台账：新内容确认旧规则时增加来源；补充旧规则时扩充条件或例外；冲突时保留双方来源、时间与场景并要求本人裁决；事实过时时更新时效字段并创建新版本；不足以改变 Skill 时只进入碎片经验池。

每次运行和反馈必须绑定明确的 Skill 版本。

## 失败条件

以下情况停止生成完整 Skill：目标用户无法唯一识别；大量发言缺失上下文；领域过宽且无法拆分；核心结论主要来自他人转述；只有表达风格而无可执行方法；只有知识点而无条件或边界；存在敏感信息或授权范围不明；语料冲突无法按时间或场景解释。

此时输出 `fragments_only`、问题清单和补充材料建议。
