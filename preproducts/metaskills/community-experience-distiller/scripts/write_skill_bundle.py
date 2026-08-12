#!/usr/bin/env python3
"""Validate a distillation object and render a col-skl-compatible seven-file Skill bundle."""

from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


RULE_KEYS = ("scope", "required_inputs", "workflow", "decision_points", "completion_criteria", "pitfalls", "boundaries", "experience_notes")


def load_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(value, dict):
        raise ValueError(f"{path} 顶层必须是对象")
    return value


def fragment_index(payload: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {item["fragment_id"]: item for item in payload.get("fragments", []) if isinstance(item, dict) and item.get("fragment_id")}


def evidence_of(item: dict[str, Any]) -> list[str]:
    value = item.get("evidence")
    return [str(entry) for entry in value] if isinstance(value, list) else []


def collect_evidence(data: dict[str, Any]) -> set[str]:
    evidence: set[str] = set()
    work = data.get("work", {})
    for key in RULE_KEYS:
        for item in work.get(key, []) if isinstance(work, dict) else []:
            if isinstance(item, dict):
                evidence.update(evidence_of(item))
    persona = data.get("persona", {})
    for key in ("communication_principles", "expression_patterns", "uncertainty_behavior"):
        for item in persona.get(key, []) if isinstance(persona, dict) else []:
            if isinstance(item, dict):
                evidence.update(evidence_of(item))
    return evidence


def validate(data: dict[str, Any], fragments: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    skill = data.get("skill") if isinstance(data.get("skill"), dict) else {}
    maturity = data.get("maturity") if isinstance(data.get("maturity"), dict) else {}
    work = data.get("work") if isinstance(data.get("work"), dict) else {}
    review = data.get("review") if isinstance(data.get("review"), dict) else {}
    target = fragments.get("target_user") if isinstance(fragments.get("target_user"), dict) else {}
    consent = target.get("consent") if isinstance(target.get("consent"), dict) else {}
    if data.get("mode") != "full_skill" or maturity.get("decision") != "full_skill":
        errors.append("只有 full_skill 蒸馏对象可以生成七件套")
    if int(maturity.get("total", 0)) < 12:
        errors.append("成熟度总分低于 12")
    for key in ("coverage", "repeatability", "boundaries", "evidence_quality"):
        if int(maturity.get(key, 0)) < 2:
            errors.append(f"成熟度 {key} 低于 2")
    skill_id = str(skill.get("id") or "")
    if not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", skill_id):
        errors.append("skill.id 必须是小写连字符格式")
    for key in ("name", "domain", "description"):
        if not str(skill.get(key) or "").strip():
            errors.append(f"缺少 skill.{key}")
    author = skill.get("author") if isinstance(skill.get("author"), dict) else {}
    if str(author.get("user_id") or "") != str(target.get("id") or ""):
        errors.append("skill.author.user_id 与 fragments.target_user.id 不一致")
    for key in ("workflow", "decision_points", "boundaries"):
        if not isinstance(work.get(key), list) or not work[key]:
            errors.append(f"work.{key} 不能为空")
    if review.get("author_confirmed") is not True and review.get("status") != "draft":
        errors.append("作者未确认时 review.status 只能为 draft")
    if review.get("author_confirmed") is True and not review.get("confirmed_at"):
        errors.append("作者确认后必须记录 review.confirmed_at")
    if review.get("status") == "publishable":
        if review.get("author_confirmed") is not True:
            errors.append("publishable 状态要求作者确认")
        if consent.get("publication_allowed") is not True:
            errors.append("输入没有 publication_allowed=true，不得生成 publishable 产物")
    known = set(fragment_index(fragments))
    used = collect_evidence(data)
    if not used:
        errors.append("没有任何证据引用")
    unknown = sorted(used - known)
    if unknown:
        errors.append(f"引用了不存在的 fragment: {', '.join(unknown)}")
    for section_name in RULE_KEYS:
        for index, item in enumerate(work.get(section_name, [])):
            if isinstance(item, dict) and not evidence_of(item):
                errors.append(f"work.{section_name}[{index}] 缺少 evidence")
    return errors


def statement(item: dict[str, Any]) -> str:
    if "statement" in item:
        return str(item["statement"])
    if "instruction" in item:
        return str(item["instruction"])
    if "condition" in item and "action" in item:
        return f"当{item['condition']}时，{item['action']}"
    return str(item)


def cited(item: dict[str, Any]) -> str:
    refs = ", ".join(evidence_of(item))
    return f"{statement(item)} 〔证据：{refs}〕"


def bullet_section(title: str, items: list[dict[str, Any]]) -> str:
    rows = [f"## {title}", ""]
    rows.extend(f"- {cited(item)}" for item in items)
    return "\n".join(rows)


def render_work(data: dict[str, Any]) -> str:
    name = data["skill"]["name"]
    work = data["work"]
    parts = [f"# {name} — 能力方法", ""]
    sections = [
        ("任务范围", "scope"), ("所需输入", "required_inputs"),
        ("完成标准", "completion_criteria"), ("常见错误", "pitfalls"),
        ("能力边界", "boundaries"), ("经验补充", "experience_notes"),
    ]
    workflow = work.get("workflow", [])
    parts.extend(["## 执行流程", ""])
    parts.extend(f"{item.get('step', i)}. {cited(item)}" for i, item in enumerate(workflow, 1))
    parts.append("")
    parts.extend(["## 决策节点", ""])
    parts.extend(f"- **如果** {item['condition']}，**则** {item['action']}。〔证据：{', '.join(evidence_of(item))}〕" for item in work.get("decision_points", []))
    parts.append("")
    for title, key in sections:
        items = work.get(key, [])
        if items:
            parts.extend([bullet_section(title, items), ""])
    return "\n".join(parts).strip() + "\n"


def render_persona(data: dict[str, Any]) -> str:
    name = data["skill"]["name"]
    persona = data.get("persona", {})
    parts = [f"# {name} — 表达偏好", "", "> 本文件只描述该领域中有直接证据支持的沟通方式，不代表完整人格。", ""]
    for title, key in (("沟通原则", "communication_principles"), ("表达模式", "expression_patterns"), ("不确定性处理", "uncertainty_behavior")):
        items = persona.get(key, [])
        if items:
            parts.extend([bullet_section(title, items), ""])
    parts.extend(["## 禁止推断", ""])
    for item in persona.get("prohibited_inferences", ["不推断完整人格、心理状态或敏感属性"]):
        parts.append(f"- {item}")
    return "\n".join(parts).strip() + "\n"


def render_combined(data: dict[str, Any], work_md: str, persona_md: str) -> str:
    skill = data["skill"]
    triggers = " / ".join(skill.get("triggers", []))
    return f"""# {skill['name']}

你是一个经本人授权材料蒸馏、限定在“{skill['domain']}”领域的学长 Skill。你不是该作者本人的数字分身。

## 触发条件

触发词：{triggers}

## 运行契约

1. 先检查用户是否提供了“所需输入”；不足时优先追问。
2. 严格按照下方能力方法给出步骤与判断，不编造材料之外的经历。
3. 涉及时间敏感事实时说明适用时间，并建议核对官方来源。
4. 命中能力边界时停止强答，说明缺口或交给人工确认。
5. 保持下方表达偏好，但可信度来自证据和边界，而不是口吻模仿。

---

{work_md}

---

{persona_md}
""".rstrip() + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--distillation", type=Path, required=True)
    parser.add_argument("--fragments", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    try:
        data = load_json(args.distillation)
        fragments = load_json(args.fragments)
        errors = validate(data, fragments)
        if errors:
            raise ValueError("；".join(errors))
        skill = data["skill"]
        author = skill.get("author", {})
        out = args.output_dir
        out.mkdir(parents=True, exist_ok=True)
        work_md = render_work(data)
        persona_md = render_persona(data)
        combined = render_combined(data, work_md, persona_md)
        (out / "work.md").write_text(work_md, encoding="utf-8")
        (out / "persona.md").write_text(persona_md, encoding="utf-8")
        (out / "SKILL.md").write_text(combined, encoding="utf-8")
        (out / "work_skill.md").write_text(f"---\nname: {skill['id']}-work\ndescription: {skill['description']}（仅能力方法）\n---\n\n{work_md}", encoding="utf-8")
        (out / "persona_skill.md").write_text(f"---\nname: {skill['id']}-persona\ndescription: {skill['name']} 在 {skill['domain']} 领域的表达偏好\n---\n\n{persona_md}", encoding="utf-8")
        manifest = {
            "name": skill["name"], "domain": skill["domain"], "avatar": author.get("avatar", "avatar.svg"),
            "description": skill["description"], "triggers": skill.get("triggers", []), "source": "distilled",
            "version": skill.get("version", "v1"), "reviewStatus": data.get("review", {}).get("status", "draft"),
        }
        meta = {
            "schema_version": "1.0", "skill_id": skill["id"], "name": skill["name"],
            "identity": {"user_id": author.get("user_id", ""), "school": author.get("school", ""), "college": author.get("college", ""), "major": author.get("major", ""), "year_graduated": author.get("year_graduated", "")},
            "lifecycle": {"version": skill.get("version", "v1"), "status": data.get("review", {}).get("status", "draft"), "generated_at": datetime.now(timezone.utc).isoformat()},
            "review": data.get("review", {}), "maturity": data.get("maturity", {}),
        }
        used = sorted(collect_evidence(data))
        index = fragment_index(fragments)
        sources = {
            "skill_id": skill["id"], "version": skill.get("version", "v1"), "fragment_ids": used,
            "fragments": [{"fragment_id": fid, "thread_id": index[fid].get("thread_id"), "source_message_ids": index[fid].get("source_message_ids", []), "title": index[fid].get("title", "")} for fid in used],
        }
        (out / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
        (out / "meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
        (out / "sources.json").write_text(json.dumps(sources, ensure_ascii=False, indent=2), encoding="utf-8")
        avatar = author.get("avatar_source")
        if avatar and Path(avatar).is_file():
            shutil.copy2(avatar, out / manifest["avatar"])
        print(f"Wrote Skill bundle to {out}")
        return 0
    except (OSError, json.JSONDecodeError, ValueError, KeyError, TypeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
