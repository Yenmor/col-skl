# 蒸馏对象 Schema

写出 `distillation.json` 时使用以下结构。可增加字段，但不得删除必需字段。

```json
{
  "schema_version": "1.0",
  "mode": "full_skill",
  "skill": {
    "id": "lin-course-selection",
    "name": "林学长 · 计算机选课避坑",
    "domain": "选课",
    "description": "帮助软件工程学生根据目标与负担选择专业课程",
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
    "prohibited_inferences": ["不推断完整人格、心理状态或敏感属性"]
  },
  "open_questions": [],
  "review": {"author_confirmed": false, "status": "draft", "confirmed_at": null, "confirmed_by": null}
}
```

约束：`mode` 与 `maturity.decision` 一致；`skill.id` 仅用小写字母、数字和连字符；所有规则都带非空 `evidence`；证据只引用现存 `fragment_id`；关键流程与判断推荐跨 fragment 支撑；未由作者确认时状态只能为 `draft`；`fragments_only` 不运行七件套写入器。
