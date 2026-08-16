#!/usr/bin/env python3
"""Freeze and compare the direct hook call surface.

This is not a semantic hook verifier. It is a high-sensitivity tripwire:
added, removed or textually changed direct hook calls must be explained by
updating the baseline in the same reviewed checkpoint.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path


PATTERN = re.compile(
    r"\b(?:findAndHookMethod|findAndHookConstructor|hookAllMethods|"
    r"hookAllConstructors|hookMethod|hookBefore|hookAfter)\s*\("
)


def normalize(line: str) -> str:
    line = re.sub(r"//.*$", "", line)
    return re.sub(r"\s+", " ", line.strip())


def collect(root: Path) -> list[dict]:
    rows: list[dict] = []
    for path in sorted([*root.rglob("*.kt"), *root.rglob("*.java")]):
        text = path.read_text(encoding="utf-8")
        lines = text.splitlines()
        for idx, line in enumerate(lines, 1):
            if not PATTERN.search(line):
                continue
            if re.search(r"\b(?:fun|void|def)\s+\w*(?:findAndHook|hookAll|hookMethod)", line):
                continue
            snippet = normalize(line)
            if not snippet:
                continue
            rel = path.as_posix()
            fp = hashlib.sha256(f"{rel}\0{snippet}".encode()).hexdigest()[:24]
            rows.append({"fingerprint": fp, "path": rel, "line": idx, "snippet": snippet})
    return rows


def main(argv=None) -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--source-root", default="app/src/main/java")
    p.add_argument("--baseline", default="docs/audit/HOOK_SURFACE_BASELINE.json")
    p.add_argument("--write-baseline", action="store_true")
    args = p.parse_args(argv)

    rows = collect(Path(args.source_root))
    baseline_path = Path(args.baseline)
    current = {r["fingerprint"]: r for r in rows}

    if args.write_baseline:
        baseline_path.parent.mkdir(parents=True, exist_ok=True)
        baseline_path.write_text(
            json.dumps({"schema": 1, "count": len(rows), "hooks": rows}, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"Wrote hook surface baseline: {len(rows)} calls")
        return 0

    if not baseline_path.exists():
        print(f"Missing hook surface baseline: {baseline_path}")
        return 1
    old_rows = json.loads(baseline_path.read_text(encoding="utf-8")).get("hooks", [])
    old = {r["fingerprint"]: r for r in old_rows}
    added = [current[k] for k in sorted(current.keys() - old.keys())]
    removed = [old[k] for k in sorted(old.keys() - current.keys())]
    if added or removed:
        print(f"Hook surface drift: +{len(added)} / -{len(removed)}")
        for row in added[:100]:
            print(f"  ADD {row['path']}:{row['line']}: {row['snippet']}")
        for row in removed[:100]:
            print(f"  DEL {row['path']}:{row['line']}: {row['snippet']}")
        return 1
    print(f"Hook surface unchanged: {len(rows)} direct call(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
