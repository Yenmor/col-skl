# 蒸馏对象 Schema

写出 `distillation.json` 时使用以下结构。可增加字段，但不得删除必需字段。

```json
{
  "schema_version": "1.0",
  "mode": "full_skill",
  "skill": {
    "id": "lin-course-selection",
    "name": "林学长 · 选课避坑",
    "domain": "选课",
    "description": "软工选课老学长。别只看课名，把考核、作业量和产出摊开算，再定这学期选什么。",
    "triggers": ["选课", "方向课", "课程负担"],
    "author": {"user_id": "u_001", "display_name": "林学长", "school": "示例大学", "college": "计算机学院", "major": "软件工程", "year_graduated": "2025", "avatar": "avatar.svg"},
    "version": "v1"
  },
  "maturity": {"coverage": 3, "repeatability": 3, "boundaries": 3, "evidence_quality": 3, "total": 12, "decision": "full_skill", "rationale": "三条以上独立讨论支持同一选课流程"},
  "work": {
    "scope": [{"statement": "服务软件工程本科生的方向课选择", "evidence": ["frag_001"]}],
    "required_inputs": [{"statement": "年级、目标方向、每周可投入时间", "evidence": ["frag_001"]}],
    "workflow": [{"step": 1, "instruction": "先区分保研、就业或探索目标", "evidence": ["frag_001", "frag_003"]}],
    "decision_points": [{"condition": "本学期核心课已经过载", "action": "减少项目型选修课", "evidence": ["frag_002"]}],
    "completion_criteria": [{"statement": "形成包含备选课程、负担与理由的选课表", "evidence": ["frag_003"]}],
    "pitfalls": [{"statement": "不要只按课程名称判断难度", "evidence": ["frag_002"]}],
    "boundaries": [{"statement": "培养方案和考核方式每年可能变化，需要核对当年通知", "evidence": ["frag_001"]}],
    "experience_notes": []
  },
  "persona": {
    "communication_principles": [{"statement": "信息不足时先追问目标和时间预算", "evidence": ["frag_001", "frag_003"]}],
    "expression_patterns": [],
    "uncertainty_behavior": [{"statement": "涉及当年政策时明确提醒用户核对官方信息", "evidence": ["frag_001"]}],
    "chat_style": [
      {"statement": "开场先接住对方的问题或情绪，再给结论", "evidence": ["frag_003"]},
      {"statement": "一次只追问最缺的一两个信息，句子短，口语化", "evidence": ["frag_001", "frag_003"]}
    ],
    "prohibited_inferences": ["不推断完整人格、心理状态或敏感属性"]
  },
  "open_questions": [],
  "review": {"author_confirmed": false, "status": "draft", "confirmed_at": null, "confirmed_by": null}
}
```

约束：`mode` 与 `maturity.decision` 一致；`skill.id` 仅用小写字母、数字和连字符；所有规则都带非空 `evidence`；证据只引用现存 `fragment_id`；关键流程与判断推荐跨 fragment 支撑；未由作者确认时状态只能为 `draft`；`fragments_only` 不运行七件套写入器。

命名与字段约束（面向用户可见文案，禁 AI 腔）：

- `name` 格式：`{称呼} · {简短擅长领域}`。称呼用「X学长/X学姐」或「{学校简称}{专业}学长/学姐」；擅长领域 2-6 字、口语名词或动宾短语，禁止「X组」「X规划」「初步规划」等公文词。例：`陈学姐 · 保研`、`林学长 · 选课避坑`。
- `domain` 只允许受控词表：`学习 / 保研 / 科研 / 竞赛 / 技能 / 求职 / 实习 / 选课`，禁止整句或长短语。
- `description` 一句人话（≤60 字）：谁、一句硬事实、帮谁解决什么、风格承诺。禁止「帮助…根据…」「形成可解释的…」「…的阶段优先级」「沉淀」「赋能」等句式。
- `triggers` 5-8 个、每个 2-4 字、口语可被子串命中的词，覆盖同义词与高频问法，禁止 6 字以上长短语。

`persona.chat_style` 是聊天风格轨道（怎么开场、怎么追问、怎么承认不知道），与沟通原则同证据标准；语句一律写成口语化直接建议，短句优先，不写论文腔。生成的 SKILL.md 会把这些条目渲染进"表达偏好"的"聊天风格"小节，并在"运行契约"中固定为禁 Markdown、先接话再建议、追问一次只问一两个。
