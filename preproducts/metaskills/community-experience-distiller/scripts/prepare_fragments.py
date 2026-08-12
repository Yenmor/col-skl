#!/usr/bin/env python3
"""Normalize community activity JSON and rebuild evidence-bearing conversation fragments."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


POST_KEYS = ("posts", "related_posts", "items")
CHILD_KEYS = ("comments", "replies", "children")
BODY_KEYS = ("body", "content", "text")
ID_KEYS = ("id", "post_id", "comment_id", "message_id")
TIME_KEYS = ("created_at", "timestamp", "time")
PARENT_KEYS = ("reply_to_id", "parent_id")
SENSITIVE_PATTERNS = {
    "phone": re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)"),
    "email": re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"),
    "student_id": re.compile(r"(?<!\d)\d{10,18}(?!\d)"),
}


def first(obj: dict[str, Any], keys: tuple[str, ...], default: Any = None) -> Any:
    for key in keys:
        if key in obj and obj[key] is not None:
            return obj[key]
    return default


def author_info(obj: dict[str, Any]) -> tuple[str, str]:
    author = obj.get("author")
    if isinstance(author, dict):
        author_id = first(author, ("id", "user_id", "author_id"), "")
        display = first(author, ("display_name", "name", "nickname"), "")
    else:
        author_id = first(obj, ("author_id", "user_id"), "")
        display = first(obj, ("author_name", "display_name", "nickname"), "")
    return str(author_id or ""), str(display or "")


def stable_id(kind: str, post_id: str, parent_id: str, text: str) -> str:
    raw = f"{kind}|{post_id}|{parent_id}|{text}".encode("utf-8")
    return f"generated_{hashlib.sha1(raw).hexdigest()[:12]}"


def normalize_tags(value: Any) -> list[str]:
    if isinstance(value, str):
        return [item.strip() for item in re.split(r"[,，/|]", value) if item.strip()]
    if isinstance(value, list):
        return [str(item).strip() for item in value if str(item).strip()]
    return []


def extract_posts(payload: dict[str, Any]) -> list[dict[str, Any]]:
    for key in POST_KEYS:
        value = payload.get(key)
        if isinstance(value, list):
            return [item for item in value if isinstance(item, dict)]
    return []


def extract_children(obj: dict[str, Any]) -> list[dict[str, Any]]:
    children: list[dict[str, Any]] = []
    seen_objects: set[int] = set()
    for key in CHILD_KEYS:
        value = obj.get(key)
        if not isinstance(value, list):
            continue
        for item in value:
            if isinstance(item, dict) and id(item) not in seen_objects:
                seen_objects.add(id(item))
                children.append(item)
    return children


def sensitivity_labels(text: str) -> list[str]:
    return [name for name, pattern in SENSITIVE_PATTERNS.items() if pattern.search(text)]


def normalize_payload(payload: dict[str, Any]) -> tuple[dict[str, Any], dict[str, dict[str, Any]], list[str]]:
    warnings: list[str] = []
    target = payload.get("target_user") or payload.get("user") or {}
    if not isinstance(target, dict):
        raise ValueError("target_user 必须是对象")
    target_id = str(first(target, ("id", "user_id"), "") or "")
    if not target_id:
        raise ValueError("缺少 target_user.id，无法可靠识别目标用户")

    consent = target.get("consent") if isinstance(target.get("consent"), dict) else {}
    if consent.get("distillation_allowed") is False:
        raise ValueError("目标用户明确禁止蒸馏，停止处理")
    if consent.get("distillation_allowed") is not True:
        warnings.append("未发现明确的 distillation_allowed=true；产物只能用于内部草稿审核")
    if consent.get("publication_allowed") is not True:
        warnings.append("未获得公开发布授权；生成 Skill 时必须保持 draft")

    posts = extract_posts(payload)
    if not posts:
        raise ValueError("没有找到 posts/related_posts/items 数组")

    nodes: dict[str, dict[str, Any]] = {}

    def add_node(raw: dict[str, Any], kind: str, post_id: str, inferred_parent: str | None) -> str:
        text = str(first(raw, BODY_KEYS, "") or "").strip()
        node_id = str(first(raw, ID_KEYS, "") or "").strip()
        parent_id = str(first(raw, PARENT_KEYS, inferred_parent or "") or "").strip() or None
        if not node_id:
            node_id = stable_id(kind, post_id, parent_id or "", text)
            warnings.append(f"内容缺少 ID，已生成临时 ID: {node_id}")
        author_id, author_name = author_info(raw)
        if not author_id:
            warnings.append(f"{node_id} 缺少 author.id")
        if not text:
            warnings.append(f"{node_id} 正文为空")
        node = {
            "message_id": node_id,
            "post_id": post_id,
            "parent_id": parent_id,
            "kind": kind,
            "author_id": author_id,
            "author_name": author_name,
            "created_at": str(first(raw, TIME_KEYS, "") or ""),
            "text": text,
            "deleted": bool(raw.get("deleted", False)),
            "sensitivity": sensitivity_labels(text),
        }
        if node_id in nodes and nodes[node_id] != node:
            raise ValueError(f"同一 ID 对应不同内容: {node_id}")
        nodes[node_id] = node
        for child in extract_children(raw):
            add_node(child, "reply", post_id, node_id)
        return node_id

    normalized_posts = []
    for index, post in enumerate(posts, 1):
        title = str(post.get("title") or "").strip()
        body = str(first(post, BODY_KEYS, "") or "").strip()
        post_id = str(first(post, ID_KEYS, "") or "").strip()
        if not post_id:
            post_id = stable_id("post", str(index), "", title + body)
            warnings.append(f"帖子缺少 ID，已生成临时 ID: {post_id}")
        root_id = add_node(post, "post", post_id, None)
        nodes[root_id]["title"] = title
        nodes[root_id]["tags"] = normalize_tags(post.get("tags"))
        normalized_posts.append({"post_id": post_id, "root_id": root_id, "title": title, "tags": nodes[root_id]["tags"]})

    return {
        "id": target_id,
        "display_name": str(first(target, ("display_name", "name", "nickname"), target_id)),
        "profile": target.get("profile") if isinstance(target.get("profile"), dict) else {},
        "consent": consent,
    }, nodes, warnings


def path_to_root(node_id: str, nodes: dict[str, dict[str, Any]]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    current = node_id
    while current and current not in seen and current in nodes:
        seen.add(current)
        result.append(current)
        current = nodes[current].get("parent_id") or ""
    result.reverse()
    return result


def rebuild_fragments(target: dict[str, Any], nodes: dict[str, dict[str, Any]], warnings: list[str]) -> list[dict[str, Any]]:
    children: dict[str, list[str]] = defaultdict(list)
    post_nodes: dict[str, list[str]] = defaultdict(list)
    for node_id, node in nodes.items():
        post_nodes[node["post_id"]].append(node_id)
        if node.get("parent_id"):
            children[node["parent_id"]].append(node_id)

    target_by_post: dict[str, list[str]] = defaultdict(list)
    for node_id, node in nodes.items():
        if node["author_id"] == target["id"] and node["text"] and not node["deleted"]:
            target_by_post[node["post_id"]].append(node_id)

    if not target_by_post:
        raise ValueError("没有找到 target_user 的有效发言")

    fragments: list[dict[str, Any]] = []
    for post_id, target_ids in sorted(target_by_post.items()):
        selected: set[str] = set()
        for target_id in target_ids:
            selected.update(path_to_root(target_id, nodes))
            selected.update(children.get(target_id, []))
            parent_id = nodes[target_id].get("parent_id")
            if parent_id:
                siblings = children.get(parent_id, [])
                if target_id in siblings:
                    pos = siblings.index(target_id)
                    selected.update(siblings[max(0, pos - 1): pos + 2])

        roots = [nid for nid in post_nodes[post_id] if nodes[nid]["kind"] == "post"]
        selected.update(roots)

        def sort_key(nid: str) -> tuple[str, int, str]:
            node = nodes[nid]
            created = node.get("created_at") or "9999"
            return created, 0 if node["kind"] == "post" else 1, nid

        transcript = []
        for node_id in sorted(selected, key=sort_key):
            node = nodes[node_id]
            if node["deleted"] or not node["text"]:
                continue
            if node["author_id"] == target["id"]:
                role = "target"
            elif node["kind"] == "post":
                role = "root_context"
            elif node.get("parent_id") in target_ids:
                role = "response_to_target"
            else:
                role = "context"
            transcript.append({**node, "role": role})

        root = nodes[roots[0]] if roots else {}
        digest = hashlib.sha1(post_id.encode("utf-8")).hexdigest()[:10]
        fragments.append({
            "fragment_id": f"frag_{digest}",
            "thread_id": post_id,
            "title": root.get("title", ""),
            "tags": root.get("tags", []),
            "target_message_ids": sorted(target_ids),
            "source_message_ids": [item["message_id"] for item in transcript],
            "transcript": transcript,
            "context_quality": {
                "has_root_post": bool(roots),
                "has_timestamp": all(bool(nodes[nid].get("created_at")) for nid in target_ids),
                "has_response": any(item["role"] == "response_to_target" for item in transcript),
                "sensitive_labels": sorted({label for item in transcript for label in item["sensitivity"]}),
            },
        })
    return fragments


def topic_hints(fragments: list[dict[str, Any]]) -> list[dict[str, Any]]:
    tag_to_fragments: dict[str, set[str]] = defaultdict(set)
    for fragment in fragments:
        for tag in fragment.get("tags", []):
            normalized = re.sub(r"\s+", "", str(tag).lower())
            if normalized:
                tag_to_fragments[normalized].add(fragment["fragment_id"])
    return [
        {"hint": tag, "fragment_ids": sorted(ids), "independent_threads": len(ids)}
        for tag, ids in sorted(tag_to_fragments.items(), key=lambda item: (-len(item[1]), item[0]))
    ]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    try:
        payload = json.loads(args.input.read_text(encoding="utf-8-sig"))
        if not isinstance(payload, dict):
            raise ValueError("顶层 JSON 必须是对象")
        target, nodes, warnings = normalize_payload(payload)
        fragments = rebuild_fragments(target, nodes, warnings)
        target_count = sum(len(item["target_message_ids"]) for item in fragments)
        sensitive = Counter(label for item in fragments for label in item["context_quality"]["sensitive_labels"])
        result = {
            "schema_version": "1.0",
            "generated_at": datetime.now(timezone.utc).isoformat(),
            "target_user": target,
            "corpus_summary": {
                "total_messages": len(nodes),
                "target_messages": target_count,
                "independent_threads": len(fragments),
                "fragment_count": len(fragments),
                "sensitive_findings": dict(sensitive),
                "supply_hint": "full_skill_candidate" if len(fragments) >= 3 else "fragments_only_candidate",
            },
            "topic_hints": topic_hints(fragments),
            "warnings": sorted(set(warnings)),
            "fragments": fragments,
        }
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"Wrote {len(fragments)} fragments with {target_count} target messages to {args.output}")
        if result["warnings"]:
            print(f"Warnings: {len(result['warnings'])}")
        return 0
    except (OSError, json.JSONDecodeError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
