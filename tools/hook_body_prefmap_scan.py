#!/usr/bin/env python3
"""Scan production mods for MainModule.mPrefs reads inside hook bodies.

Phase A migration gate: hook callbacks must not reach PrefMap directly.
Cold-path reads (snapshot builders, resolvers, install, pref observers) are
reported separately and excluded from the ceiling.

Usage:
    python tools/hook_body_prefmap_scan.py
    python tools/hook_body_prefmap_scan.py --json
    python tools/hook_body_prefmap_scan.py --check
    python tools/hook_body_prefmap_scan.py --write-baseline
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
DEFAULT_BASELINE = REPO_ROOT / "tools" / "HOOK_BODY_PREFMAP_BASELINE.json"

PREFS_PATTERN = re.compile(r"\bMainModule\.mPrefs\b")
LINE_COMMENT = re.compile(r"//[^\n]*")
BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.DOTALL)

HOOK_START = re.compile(
    r"(?:object\s*:\s*MethodHook\s*\(|"
    r"new\s+MethodHook\s*\(|"
    r"extends\s+MethodHook\s*\{|"
    r"XC_MethodHook\s*\()"
)

COLD_FUNC = re.compile(
    r"(?i)\b(?:fun|private\s+fun|internal\s+fun|protected\s+fun|public\s+fun|"
    r"static\s+\w+\s+\w+|private\s+static\s+\w+\s+\w+)\s+"
    r"(build\w*Snapshot|resolve\w*|rebuild\w*|refresh\w*|install\w*|handleLoad\w*|"
    r"isEnabled|ensure\w*|register\w*|onPreferenceChanged|apply\w*Snapshot|"
    r"publish\w*|setup\w*|init\w*|create\w*|currentOrBuild\w*|"
    r"buildNetSpeed|buildStatusBar|buildDetailed|buildBattery|buildClock|"
    r"buildVolume|buildNotification|buildAutoBrightness|buildBlur|"
    r"configure\w*|load\w*Snapshot|updateSnapshot|syncSnapshot)\b"
)

OBSERVER_ONCHANGE = re.compile(r"override\s+fun\s+onChange\b")

# Process heat: higher = migrate first when counts tie.
PROCESS_HEAT: dict[str, int] = {
    "SystemUIStatusBarHooks.kt": 100,
    "SystemUIControlCenterHooks.kt": 95,
    "SystemClockHooks.kt": 90,
    "SystemUIStrongToastHooks.kt": 90,
    "SystemUIBatteryHooks.kt": 85,
    "StatusBarContentGeometryHooks.kt": 85,
    "SystemUILockScreenHooks.kt": 80,
    "LauncherIconHooks.kt": 75,
    "LauncherGestureHooks.kt": 75,
    "LauncherFolderHooks.kt": 70,
    "Controls.kt": 65,
    "SystemLockScreenHooks.kt": 65,
    "LauncherLayoutHooks.kt": 60,
    "SystemAudioHooks.kt": 55,
    "SystemDisplayHooks.kt": 50,
}


@dataclass
class Hit:
    line: int
    snippet: str
    bucket: str
    enclosing: str


@dataclass
class FileReport:
    path: str
    hook_body: int
    warm_other: int
    cold_ok: int
    total: int
    heat: int
    hits: list[Hit]


def strip_comments(text: str) -> str:
    """Blank comments while keeping newlines so offsets stay aligned."""

    def blank(match: re.Match[str]) -> str:
        return re.sub(r"[^\n]", " ", match.group(0))

    return LINE_COMMENT.sub(blank, BLOCK_COMMENT.sub(blank, text))


def find_block_end(text: str, open_brace_offset: int) -> int:
    if open_brace_offset < 0 or open_brace_offset >= len(text) or text[open_brace_offset] != "{":
        return -1
    i = open_brace_offset + 1
    n = len(text)
    depth = 1
    state = "normal"
    while i < n and depth > 0:
        c = text[i]
        if state == "normal":
            if c == '"':
                state = "string"
            elif c == "'":
                state = "char"
            elif c == "/" and i + 1 < n:
                nxt = text[i + 1]
                if nxt == "/":
                    state = "line_comment"
                    i += 1
                elif nxt == "*":
                    state = "block_comment"
                    i += 1
            elif c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
        elif state == "string":
            if c == "\\" and i + 1 < n:
                i += 1
            elif c == '"':
                state = "normal"
        elif state == "char":
            if c == "\\" and i + 1 < n:
                i += 1
            elif c == "'":
                state = "normal"
        elif state == "line_comment":
            if c == "\n":
                state = "normal"
        elif state == "block_comment":
            if c == "*" and i + 1 < n and text[i + 1] == "/":
                state = "normal"
                i += 1
        i += 1
    return i - 1 if depth == 0 else -1


def hook_regions(text: str) -> list[tuple[int, int]]:
    regions: list[tuple[int, int]] = []
    for match in HOOK_START.finditer(text):
        brace = text.find("{", match.end())
        if brace < 0:
            continue
        end = find_block_end(text, brace)
        if end >= 0:
            regions.append((brace, end))
    return regions


def observer_onchange_regions(text: str) -> list[tuple[int, int]]:
    regions: list[tuple[int, int]] = []
    for match in OBSERVER_ONCHANGE.finditer(text):
        brace = text.find("{", match.end())
        if brace < 0:
            continue
        end = find_block_end(text, brace)
        if end >= 0:
            regions.append((brace, end))
    return regions


def cold_function_regions(text: str) -> list[tuple[int, int, str]]:
    regions: list[tuple[int, int, str]] = []
    for match in COLD_FUNC.finditer(text):
        name = match.group(1)
        brace = text.find("{", match.end())
        if brace < 0:
            continue
        end = find_block_end(text, brace)
        if end >= 0:
            regions.append((brace, end, name))
    return regions


def line_number(text: str, offset: int) -> int:
    return text[:offset].count("\n") + 1


def in_region(offset: int, regions: list[tuple[int, int]]) -> bool:
    return any(start <= offset <= end for start, end in regions)


def enclosing_cold(offset: int, cold_regions: list[tuple[int, int, str]]) -> str | None:
    matches = [(start, end, name) for start, end, name in cold_regions if start <= offset <= end]
    if not matches:
        return None
    _, _, name = max(matches, key=lambda item: item[0])
    return name


def enclosing_name(text: str, offset: int) -> str:
    prefix = text[:offset]
    for pattern in (
        re.compile(r"(?m)^[\t ]*(?:override\s+)?fun\s+(\w+)\s*[\(<]"),
        re.compile(r"(?m)^[\t ]*(?:public|private|protected|internal|static|\s)*fun\s+(\w+)\s*[\(<]"),
        re.compile(r"(?m)^[\t ]*(?:public|private|protected|static|\s)+[\w<>,\[\]?]+\s+(\w+)\s*\("),
    ):
        hits = list(pattern.finditer(prefix))
        if hits:
            return hits[-1].group(1)
    return "<anonymous>"


def classify_file(rel_path: str, text: str) -> FileReport:
    stripped = strip_comments(text)
    hooks = hook_regions(stripped)
    observers = observer_onchange_regions(stripped)
    cold = cold_function_regions(stripped)
    hits: list[Hit] = []

    for match in PREFS_PATTERN.finditer(stripped):
        offset = match.start()
        line = line_number(stripped, offset)
        line_start = stripped.rfind("\n", 0, offset) + 1
        line_end = stripped.find("\n", offset)
        if line_end < 0:
            line_end = len(text)
        original_line_end = text.find("\n", line_start)
        if original_line_end < 0:
            original_line_end = len(text)
        snippet = text[line_start:original_line_end].strip()

        enc = enclosing_name(stripped, offset)
        if in_region(offset, hooks):
            bucket = "hook_body"
        elif in_region(offset, observers):
            bucket = "cold_ok"
        elif (cold_name := enclosing_cold(offset, cold)) is not None:
            bucket = "cold_ok"
            enc = cold_name
        elif enc.startswith(("build", "resolve", "install", "refresh", "rebuild", "ensure", "register")):
            bucket = "cold_ok"
        elif enc in {"isEnabled", "onPreferenceChanged", "publish", "configure", "setup", "init"}:
            bucket = "cold_ok"
        else:
            bucket = "warm_other"

        hits.append(Hit(line=line, snippet=snippet, bucket=bucket, enclosing=enc))

    hook_body = sum(1 for h in hits if h.bucket == "hook_body")
    warm_other = sum(1 for h in hits if h.bucket == "warm_other")
    cold_ok = sum(1 for h in hits if h.bucket == "cold_ok")
    heat = PROCESS_HEAT.get(Path(rel_path).name, 10)

    return FileReport(
        path=rel_path,
        hook_body=hook_body,
        warm_other=warm_other,
        cold_ok=cold_ok,
        total=len(hits),
        heat=heat,
        hits=hits,
    )


def scan(mods_root: Path | None = None) -> list[FileReport]:
    root = mods_root or MODS_ROOT
    reports: list[FileReport] = []
    for path in sorted(root.rglob("*")):
        if path.suffix not in {".kt", ".java"}:
            continue
        rel = path.relative_to(root).as_posix()
        text = path.read_text(encoding="utf-8")
        if not PREFS_PATTERN.search(strip_comments(text)):
            continue
        reports.append(classify_file(rel, text))
    reports.sort(key=lambda r: (-r.hook_body, -r.heat, -r.warm_other, r.path))
    return reports


def priority(report: FileReport) -> str:
    if report.hook_body >= 20 or (report.hook_body >= 8 and report.heat >= 80):
        return "P0"
    if report.hook_body >= 5 or (report.hook_body >= 2 and report.heat >= 70):
        return "P1"
    if report.hook_body >= 1 or report.warm_other >= 3:
        return "P2"
    return "P3-cold-only"


def hook_body_by_file(reports: list[FileReport]) -> dict[str, int]:
    return {r.path: r.hook_body for r in reports if r.hook_body > 0}


def load_baseline(path: Path) -> dict[str, int]:
    if not path.exists():
        return {}
    data = json.loads(path.read_text(encoding="utf-8"))
    raw = data.get("hook_body_by_file", {})
    return {str(k): int(v) for k, v in raw.items()}


def baseline_payload(reports: list[FileReport]) -> dict[str, object]:
    by_file = hook_body_by_file(reports)
    return {
        "schema": 1,
        "hook_body_total": sum(by_file.values()),
        "hook_body_by_file": dict(sorted(by_file.items())),
    }


def ceiling_regressions(current: dict[str, int], baseline: dict[str, int]) -> list[str]:
    """Return human-readable failures when any file exceeds its frozen ceiling.

    Files absent from the baseline have ceiling 0. Decreases always pass.
    """
    errors: list[str] = []
    for path, count in sorted(current.items()):
        allowed = int(baseline.get(path, 0))
        if count > allowed:
            errors.append(f"{path}: hook_body {count} exceeds ceiling {allowed}")
    return errors


def print_report(reports: list[FileReport]) -> None:
    backlog = [r for r in reports if r.hook_body > 0 or r.warm_other > 0]
    total_hook = sum(r.hook_body for r in reports)
    total_warm = sum(r.warm_other for r in reports)
    total_cold = sum(r.cold_ok for r in reports)

    print("=== Phase A: Hook-body PrefMap scan ===")
    print("Scope: app/src/main/java/.../mods/**")
    print(f"Files with mPrefs: {len(reports)}")
    print(f"Hook-body reads (migrate): {total_hook}")
    print(f"Warm-other reads (review): {total_warm}")
    print(f"Cold-ok reads (keep): {total_cold}")
    print()

    print("--- Migration backlog (sorted: hook_body desc, heat desc) ---")
    print(f"{'Pri':<4} {'Hook':>4} {'Warm':>4} {'Cold':>4} {'Heat':>4}  File")
    print("-" * 72)
    for r in backlog:
        pri = priority(r)
        print(f"{pri:<4} {r.hook_body:>4} {r.warm_other:>4} {r.cold_ok:>4} {r.heat:>4}  {r.path}")

    print()
    print("--- Hook-body detail (top 15 files) ---")
    shown = 0
    for r in backlog:
        if r.hook_body == 0:
            continue
        print(f"\n[{priority(r)}] {r.path} ({r.hook_body} hook-body)")
        for h in [x for x in r.hits if x.bucket == "hook_body"][:12]:
            print(f"  L{h.line:>5}  {h.enclosing}()  {h.snippet[:100]}")
        if r.hook_body > 12:
            print(f"  ... +{r.hook_body - 12} more")
        shown += 1
        if shown >= 15:
            break

    cold_only = [r for r in reports if r.hook_body == 0 and r.warm_other == 0 and r.cold_ok > 0]
    if cold_only:
        print()
        print(f"--- Cold-only ({len(cold_only)} files, no migration needed) ---")
        for r in cold_only:
            print(f"  {r.cold_ok:>3} cold  {r.path}")


def check_ceiling(reports: list[FileReport], baseline_path: Path) -> int:
    current = hook_body_by_file(reports)
    total = sum(current.values())
    if not baseline_path.exists():
        print(f"hook-body PrefMap: baseline missing: {baseline_path}", file=sys.stderr)
        return 1
    baseline = load_baseline(baseline_path)
    ceiling_total = sum(baseline.values())
    errors = ceiling_regressions(current, baseline)
    print(
        f"hook-body PrefMap: {total} reads across {len(current)} files "
        f"(ceiling {ceiling_total} / {len(baseline)} files)"
    )
    if errors:
        print("hook-body PrefMap: new MethodHook PrefMap reads (ceiling is freeze-and-reduce):", file=sys.stderr)
        for error in errors:
            print(f"  {error}", file=sys.stderr)
        return 1
    print("hook-body PrefMap: ceiling held")
    return 0


def write_baseline(reports: list[FileReport], baseline_path: Path) -> None:
    payload = baseline_payload(reports)
    baseline_path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"Wrote baseline hook_body_total={payload['hook_body_total']}: {baseline_path}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", action="store_true", help="Emit machine-readable JSON")
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail if any file's hook-body PrefMap count exceeds the frozen ceiling",
    )
    parser.add_argument("--write-baseline", action="store_true", help="Rewrite the ceiling file from the current tree")
    parser.add_argument("--baseline", default=str(DEFAULT_BASELINE), help="Ceiling JSON path")
    parser.add_argument("--mods-root", help="Override mods directory (tests)")
    args = parser.parse_args(argv)

    mods_root = Path(args.mods_root) if args.mods_root else None
    reports = scan(mods_root)
    baseline_path = Path(args.baseline)

    if args.write_baseline:
        write_baseline(reports, baseline_path)
        return 0
    if args.check:
        return check_ceiling(reports, baseline_path)
    if args.json:
        payload = {
            "summary": {
                "files": len(reports),
                "hook_body": sum(r.hook_body for r in reports),
                "warm_other": sum(r.warm_other for r in reports),
                "cold_ok": sum(r.cold_ok for r in reports),
            },
            "backlog": [
                {**asdict(r), "priority": priority(r)}
                for r in reports
                if r.hook_body > 0 or r.warm_other > 0
            ],
        }
        json.dump(payload, sys.stdout, indent=2)
        sys.stdout.write("\n")
        return 0
    print_report(reports)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
