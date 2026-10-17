#!/usr/bin/env python3
"""Scan MethodHook bodies for allocation-suspect patterns on the hot path.

This gate prevents introduction of new per-invocation allocations, reflection,
or I/O inside hook callbacks. Existing violations are tracked in a baseline
file that can only decrease over time (ceiling model).

Usage:
    python tools/check_hotpath_alloc_budget.py
    python tools/check_hotpath_alloc_budget.py --check
    python tools/check_hotpath_alloc_budget.py --write-baseline
    python tools/check_hotpath_alloc_budget.py --json
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import asdict, dataclass
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
MODS_ROOT = REPO_ROOT / "app" / "src" / "main" / "java" / "tv" / "withaibuild" / "customiuizer" / "mods"
DEFAULT_BASELINE = REPO_ROOT / "tools" / "HOTPATH_ALLOC_BASELINE.json"

ALLOC_PATTERNS = re.compile(
    r"\b(?:"
    r"HashMap\s*\(|LinkedHashMap\s*\(|HashSet\s*\(|LinkedHashSet\s*\("
    r"|ArrayList\s*\(|mutableListOf\s*\(|mutableMapOf\s*\(|mutableSetOf\s*\("
    r"|StringBuilder\s*\(|StringBuffer\s*\("
    r"|Properties\s*\(|Regex\s*\("
    r"|File\s*\(|FileInputStream\s*\(|FileOutputStream\s*\(|RandomAccessFile\s*\("
    r"|BufferedReader\s*\(|InputStreamReader\s*\("
    r"|getDeclaredMethod\s*\(|getDeclaredField\s*\(|getMethod\s*\(|getField\s*\("
    r"|Class\.forName\s*\("
    r"|Parcel\.obtain\s*\("
    r"|contentResolver\s*\.\s*query\s*\("
    r")"
)

LINE_COMMENT = re.compile(r"//[^\n]*")
BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.DOTALL)

HOOK_START = re.compile(
    r"(?:object\s*:\s*MethodHook\s*\(|"
    r"new\s+MethodHook\s*\(|"
    r"extends\s+MethodHook\s*\{|"
    r"XC_MethodHook\s*\()"
)


@dataclass
class Hit:
    line: int
    snippet: str
    pattern: str


@dataclass
class FileReport:
    path: str
    hook_body_hits: list[Hit]

    @property
    def count(self) -> int:
        return len(self.hook_body_hits)


def strip_comments(source: str) -> str:
    source = BLOCK_COMMENT.sub(lambda m: "\n" * m.group().count("\n"), source)
    source = LINE_COMMENT.sub("", source)
    return source


def find_hook_body_end(source: str, start: int) -> int:
    depth = 0
    i = start
    while i < len(source):
        ch = source[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return len(source)


def scan_file(path: Path) -> FileReport:
    raw = path.read_text(encoding="utf-8")
    cleaned = strip_comments(raw)
    hits: list[Hit] = []

    for hook_match in HOOK_START.finditer(cleaned):
        brace = cleaned.find("{", hook_match.end())
        if brace == -1:
            continue
        body_end = find_hook_body_end(cleaned, brace)
        body = cleaned[brace:body_end]
        body_start_line = cleaned[:brace].count("\n") + 1

        for alloc_match in ALLOC_PATTERNS.finditer(body):
            line_in_body = body[:alloc_match.start()].count("\n")
            abs_line = body_start_line + line_in_body
            snippet = body[alloc_match.start():alloc_match.start() + 40].split("\n")[0]
            hits.append(Hit(line=abs_line, snippet=snippet.strip(), pattern=alloc_match.group().strip()))

    rel = path.relative_to(REPO_ROOT).as_posix()
    return FileReport(path=rel, hook_body_hits=hits)


def scan_all() -> list[FileReport]:
    reports = []
    for kt_file in sorted(MODS_ROOT.rglob("*.kt")):
        report = scan_file(kt_file)
        if report.count > 0:
            reports.append(report)
    for java_file in sorted(MODS_ROOT.rglob("*.java")):
        report = scan_file(java_file)
        if report.count > 0:
            reports.append(report)
    return reports


def load_baseline(path: Path) -> dict:
    if not path.exists():
        return {"hook_body_alloc_total": 999, "files": {}}
    return json.loads(path.read_text(encoding="utf-8"))


def write_baseline(reports: list[FileReport], path: Path) -> None:
    total = sum(r.count for r in reports)
    files = {r.path: r.count for r in reports}
    data = {"hook_body_alloc_total": total, "files": files}
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote baseline hook_body_alloc_total={total}: {path}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail if above ceiling")
    parser.add_argument("--write-baseline", action="store_true", help="freeze current count")
    parser.add_argument("--json", action="store_true", help="output JSON report")
    parser.add_argument("--baseline", type=Path, default=DEFAULT_BASELINE)
    args = parser.parse_args()

    reports = scan_all()
    total = sum(r.count for r in reports)
    file_count = len(reports)

    if args.json:
        print(json.dumps([asdict(r) for r in reports], indent=2))
        return 0

    if args.write_baseline:
        write_baseline(reports, args.baseline)
        return 0

    baseline = load_baseline(args.baseline)
    ceiling = baseline.get("hook_body_alloc_total", 999)

    print(f"hotpath-alloc: {total} hits across {file_count} files (ceiling {ceiling})")

    if total > 0:
        print("hotpath-alloc: allocation-suspect patterns in MethodHook bodies:")
        for r in reports:
            for h in r.hook_body_hits:
                print(f"  {r.path}:{h.line}: {h.snippet}")

    if args.check:
        if total > ceiling:
            print(f"hotpath-alloc: FAILED — {total} exceeds ceiling {ceiling}")
            return 1
        print("hotpath-alloc: ceiling held")

    return 0


if __name__ == "__main__":
    sys.exit(main())
