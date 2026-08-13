#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""导出非演示社区数据（posts + post_comments）到 seed JSON。

用法：
    python backend/scripts/export-community.py
输出：
    backend/src/main/resources/seed/community-posts.json

规则：
- 排除 DemoDataSeeder 硬编码的演示帖/评论（posts 的 demo-/peer- 前缀，comments 的 demo-/reply-/peer- 前缀）。
- 保留全部列原值（含 like_count / comment_count，它们来自贴吧导入时的 agree 数与楼层数）。
- 该 JSON 随代码入库，后端首次启动时由 CommunitySeedService 自动导入。
"""
import json
import os
import sqlite3

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # backend/
DB = os.path.join(ROOT, "data", "skillhub.db")
OUT = os.path.join(ROOT, "src", "main", "resources", "seed", "community-posts.json")

POST_COLS = [
    "id", "title", "excerpt", "body", "cover_color",
    "author_id", "author_name", "author_avatar",
    "domain", "like_count", "comment_count", "created_at",
]
COMMENT_COLS = [
    "id", "post_id", "author_id", "author_name", "author_avatar",
    "parent_id", "body", "created_at",
]


def main():
    if not os.path.exists(DB):
        raise SystemExit(f"数据库不存在: {DB}")
    con = sqlite3.connect(f"file:{DB}?mode=ro", uri=True)
    con.row_factory = sqlite3.Row
    cur = con.cursor()
    posts = [dict(r) for r in cur.execute(
        f"SELECT {','.join(POST_COLS)} FROM posts "
        "WHERE id NOT LIKE 'demo-%' AND id NOT LIKE 'peer-%' ORDER BY created_at")]
    comments = [dict(r) for r in cur.execute(
        f"SELECT {','.join(COMMENT_COLS)} FROM post_comments "
        "WHERE id NOT LIKE 'demo-%' AND id NOT LIKE 'reply-%' AND id NOT LIKE 'peer-%' ORDER BY created_at")]
    con.close()

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"posts": posts, "comments": comments}, f, ensure_ascii=False, indent=1)
    size_mb = os.path.getsize(OUT) / 1024 / 1024
    print(f"exported posts={len(posts)} comments={len(comments)} -> {OUT}")
    print(f"size: {size_mb:.2f} MB")


if __name__ == "__main__":
    main()
