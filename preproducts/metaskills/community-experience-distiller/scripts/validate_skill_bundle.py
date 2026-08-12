#!/usr/bin/env python3
"""Validate a generated campus-experience Skill bundle and its source links."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


REQUIRED = ("SKILL.md", "manifest.json", "meta.json", "work.md", "persona.md", "work_skill.md", "persona_skill.md", "sources.json")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    parser.add_argument("--fragments", type=Path, required=True)
    args = parser.parse_args()
    errors: list[str] = []
    for name in REQUIRED:
        if not (args.bundle / name).is_file():
            errors.append(f"缺少 {name}")
    if errors:
        print("\n".join(f"error: {item}" for item in errors), file=sys.stderr)
        return 1
    try:
        manifest = json.loads((args.bundle / "manifest.json").read_text(encoding="utf-8"))
        meta = json.loads((args.bundle / "meta.json").read_text(encoding="utf-8"))
        sources = json.loads((args.bundle / "sources.json").read_text(encoding="utf-8"))
        fragments = json.loads(args.fragments.read_text(encoding="utf-8-sig"))
        consent = fragments.get("target_user", {}).get("consent", {})
        known = {item["fragment_id"] for item in fragments.get("fragments", [])}
        unknown = set(sources.get("fragment_ids", [])) - known
        if unknown:
            errors.append(f"sources 引用了未知 fragment: {sorted(unknown)}")
        if manifest.get("version") != meta.get("lifecycle", {}).get("version"):
            errors.append("manifest 与 meta 版本不一致")
        if manifest.get("reviewStatus") != meta.get("review", {}).get("status"):
            errors.append("manifest 与 meta 审核状态不一致")
        if meta.get("review", {}).get("author_confirmed") is not True and manifest.get("reviewStatus") != "draft":
            errors.append("作者未确认的产物必须保持 draft")
        if manifest.get("reviewStatus") == "publishable" and consent.get("publication_allowed") is not True:
            errors.append("输入未授权公开发布，产物不得为 publishable")
        skill_text = (args.bundle / "SKILL.md").read_text(encoding="utf-8")
        for heading in ("## 触发条件", "## 运行契约", "## 执行流程", "## 决策节点", "## 能力边界"):
            if heading not in skill_text:
                errors.append(f"SKILL.md 缺少 {heading}")
        evidence_ids = set(re.findall(r"frag_[a-f0-9]{10}", skill_text))
        if not evidence_ids:
            errors.append("SKILL.md 没有可追溯证据 ID")
        if evidence_ids - known:
            errors.append(f"SKILL.md 引用了未知证据: {sorted(evidence_ids - known)}")
        if not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", str(meta.get("skill_id", ""))):
            errors.append("meta.skill_id 格式非法")
    except (OSError, json.JSONDecodeError, KeyError, TypeError) as exc:
        errors.append(str(exc))
    if errors:
        print("\n".join(f"error: {item}" for item in errors), file=sys.stderr)
        return 1
    print(f"OK: {args.bundle}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
