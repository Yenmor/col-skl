#!/usr/bin/env python3
"""Small regression suite for the community experience distiller scripts."""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
PYTHON = sys.executable


def run(*args: str, expect: int = 0) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env["PYTHONUTF8"] = "1"
    result = subprocess.run(
        [PYTHON, *args], cwd=ROOT, text=True, capture_output=True,
        encoding="utf-8", errors="replace", env=env,
    )
    if result.returncode != expect:
        raise AssertionError(f"expected {expect}, got {result.returncode}\nstdout={result.stdout}\nstderr={result.stderr}")
    return result


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="experience-distiller-") as temp_dir:
        temp = Path(temp_dir)
        fragments = temp / "fragments.json"
        bundle = temp / "bundle"
        run("scripts/prepare_fragments.py", "assets/sample-user-activity.json", "--output", str(fragments))
        prepared = json.loads(fragments.read_text(encoding="utf-8"))
        assert prepared["corpus_summary"]["independent_threads"] == 3
        assert prepared["corpus_summary"]["target_messages"] == 5
        for fragment in prepared["fragments"]:
            assert fragment["context_quality"]["has_root_post"]
            assert fragment["target_message_ids"]

        run("scripts/write_skill_bundle.py", "--distillation", "assets/sample-distillation.json", "--fragments", str(fragments), "--output-dir", str(bundle))
        run("scripts/validate_skill_bundle.py", str(bundle), "--fragments", str(fragments))

        bad_evidence = json.loads((ROOT / "assets/sample-distillation.json").read_text(encoding="utf-8"))
        bad_evidence["work"]["workflow"][0]["evidence"] = ["frag_not_real"]
        bad_evidence_path = temp / "bad-evidence.json"
        bad_evidence_path.write_text(json.dumps(bad_evidence, ensure_ascii=False), encoding="utf-8")
        run("scripts/write_skill_bundle.py", "--distillation", str(bad_evidence_path), "--fragments", str(fragments), "--output-dir", str(temp / "bad-evidence"), expect=1)

        immature = json.loads((ROOT / "assets/sample-distillation.json").read_text(encoding="utf-8"))
        immature["mode"] = "fragments_only"
        immature["maturity"].update({"decision": "fragments_only", "coverage": 1, "total": 8})
        immature_path = temp / "immature.json"
        immature_path.write_text(json.dumps(immature, ensure_ascii=False), encoding="utf-8")
        run("scripts/write_skill_bundle.py", "--distillation", str(immature_path), "--fragments", str(fragments), "--output-dir", str(temp / "immature"), expect=1)

        unauthorized = json.loads((ROOT / "assets/sample-distillation.json").read_text(encoding="utf-8"))
        unauthorized["review"].update({"author_confirmed": True, "status": "publishable", "confirmed_at": "2026-08-12T12:00:00+08:00", "confirmed_by": "u_lin"})
        unauthorized_path = temp / "unauthorized.json"
        unauthorized_path.write_text(json.dumps(unauthorized, ensure_ascii=False), encoding="utf-8")
        run("scripts/write_skill_bundle.py", "--distillation", str(unauthorized_path), "--fragments", str(fragments), "--output-dir", str(temp / "unauthorized"), expect=1)

    print("OK: all metaskill regression tests passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
