#!/usr/bin/env python3
"""Static hazard scanner for injected Android runtime code.

The scanner is intentionally strict. Existing reviewed findings can be frozen
in a JSON baseline. CI fails only on new fingerprints unless --strict-all is
used. Add `BRUTAL_ALLOW:<RULE>` on the same line for a narrow reviewed waiver.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from dataclasses import dataclass, asdict
from pathlib import Path


@dataclass
class Finding:
    rule: str
    path: str
    line: int
    snippet: str
    disposition: str = "pending"

    @property
    def fingerprint(self) -> str:
        normalized = re.sub(r"\s+", " ", self.snippet.strip())
        raw = f"{self.rule}\0{self.path}\0{self.line}\0{normalized}".encode()
        return hashlib.sha256(raw).hexdigest()[:20]


RULES: list[tuple[str, re.Pattern[str], str]] = [
    (
        "EMPTY_CATCH",
        re.compile(r"catch\s*\([^)]*\)\s*\{\s*(?://[^\n]*)?\s*\}", re.S),
        "empty catch hides runtime failures",
    ),
    (
        "GLOBAL_SCOPE",
        re.compile(r"\bGlobalScope\s*\."),
        "GlobalScope has no injectable lifecycle owner",
    ),
    (
        "THREAD_SLEEP",
        re.compile(r"\bThread\.sleep\s*\("),
        "blocking sleep in injected production source",
    ),
    (
        "RUN_BLOCKING",
        re.compile(r"\brunBlocking\s*\{"),
        "runBlocking can block SystemUI/system_server",
    ),
    (
        "PRINT_STACK_TRACE",
        re.compile(r"\.printStackTrace\s*\("),
        "printStackTrace is uncontrolled production diagnostics",
    ),
    (
        "SYSTEM_OUT",
        re.compile(r"\bSystem\.(?:out|err)\."),
        "System.out/System.err in injected production source",
    ),
    (
        "NATIVE_LOAD",
        re.compile(r"\bSystem\.load(?:Library)?\s*\("),
        "native loading requires explicit benchmark and ABI review",
    ),
    (
        "STATIC_STRONG_ANDROID_OWNER",
        re.compile(
            r"(?m)^\s*(?:public|private|protected|internal)?\s*(?:static\s+|@JvmField\s+)?"
            r"(?:var|val|[A-Za-z0-9_<>?.]+\s+)"
            r"[A-Za-z0-9_]*\s*(?::\s*)?"
            r"(?:Context|Activity|View|Fragment|Window|Drawable)\??\s*(?:=|;)"
        ),
        "potential strong Android owner; require scoped owner or WeakReference",
    ),
    (
        "EAGER_HANDLER_THREAD",
        re.compile(r"HandlerThread\s*\([^)]*\)[\s\S]{0,160}?\.start\s*\("),
        "eager HandlerThread start; worker must be lazy and bounded",
    ),
    (
        "UNBOUNDED_GLOBAL_COLLECTION",
        re.compile(
            r"(?m)^\s*(?:static\s+|@JvmField\s+)?(?:val|var|final\s+\w+\s+)"
            r"\w+\s*(?::[^=\n]+)?=\s*(?:mutableListOf|mutableMapOf|mutableSetOf|"
            r"ArrayList|HashMap|HashSet|ConcurrentHashMap)\s*[<(]"
        ),
        "global mutable collection needs hard bound and lifecycle cleanup",
    ),
]


SCOPES: dict[str, list[str]] = {
    "production": ["app/src/main/java"],
    "test": ["app/src/test/java"],
    "tools": ["tools"],
    "all": ["app/src/main/java", "app/src/test/java", "tools"],
}


def line_number(text: str, offset: int) -> int:
    return text[:offset].count("\n") + 1


def find_block_end(text: str, open_brace_offset: int) -> int:
    """Return the index of the matching `}` for `{` at open_brace_offset.

    Skips string literals, character literals, line/block comments and nested
    braces so that a catch/finally/try body is captured correctly.
    """
    if text[open_brace_offset] != "{":
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
                i += 1
                continue
            if c == "'":
                state = "char"
                i += 1
                continue
            if c == "/" and i + 1 < n:
                nxt = text[i + 1]
                if nxt == "/":
                    state = "line_comment"
                    i += 2
                    continue
                if nxt == "*":
                    state = "block_comment"
                    i += 2
                    continue
            if c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    return i
        elif state == "string":
            if c == "\\" and i + 1 < n:
                i += 2
                continue
            if c == '"':
                state = "normal"
        elif state == "char":
            if c == "\\" and i + 1 < n:
                i += 2
                continue
            if c == "'":
                state = "normal"
        elif state == "line_comment":
            if c == "\n":
                state = "normal"
        elif state == "block_comment":
            if c == "*" and i + 1 < n and text[i + 1] == "/":
                state = "normal"
                i += 1
        i += 1
    return -1


def _advance_past_string_or_comment(text: str, start: int) -> int:
    """Return the first offset >= start that is not inside a string or comment.

    Assumes the caller is positioned just before the opening delimiter.
    """
    i = start
    n = len(text)
    if i >= n:
        return n
    state = "normal"
    while i < n:
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
            i += 1
        elif state == "string":
            if c == "\\" and i + 1 < n:
                i += 1
            elif c == '"':
                state = "normal"
                return i + 1
            i += 1
        elif state == "char":
            if c == "\\" and i + 1 < n:
                i += 1
            elif c == "'":
                state = "normal"
                return i + 1
            i += 1
        elif state == "line_comment":
            if c == "\n":
                state = "normal"
                return i + 1
            i += 1
        elif state == "block_comment":
            if c == "*" and i + 1 < n and text[i + 1] == "/":
                state = "normal"
                i += 1
                return i + 1
            i += 1
    return n


def _find_block_body_end(text: str, start: int) -> int:
    """For a `fun` or `init` token at [start], find the matching `}` of its body.

    Returns -1 for expression bodies, abstract/no-body declarations, or invalid syntax.
    """
    n = len(text)
    i = _advance_past_string_or_comment(text, start)
    state = "normal"
    paren_depth = 0
    seen_equals = False
    while i < n:
        c = text[i]
        if state == "normal":
            if c == '"':
                state = "string"
                i += 1
                continue
            if c == "'":
                state = "char"
                i += 1
                continue
            if c == "/" and i + 1 < n:
                nxt = text[i + 1]
                if nxt == "/":
                    state = "line_comment"
                    i += 2
                    continue
                if nxt == "*":
                    state = "block_comment"
                    i += 2
                    continue
            if c == "(":
                paren_depth += 1
            elif c == ")":
                if paren_depth > 0:
                    paren_depth -= 1
            elif c == "=" and paren_depth == 0:
                seen_equals = True
            elif c == "{" and paren_depth == 0:
                if seen_equals:
                    return -1
                return find_block_end(text, i)
            elif c == ";" or c == "\n":
                if seen_equals:
                    # Expression body without a block.
                    return -1
            i += 1
        elif state == "string":
            if c == "\\" and i + 1 < n:
                i += 2
                continue
            if c == '"':
                state = "normal"
            i += 1
        elif state == "char":
            if c == "\\" and i + 1 < n:
                i += 2
                continue
            if c == "'":
                state = "normal"
            i += 1
        elif state == "line_comment":
            if c == "\n":
                state = "normal"
            i += 1
        elif state == "block_comment":
            if c == "*" and i + 1 < n and text[i + 1] == "/":
                state = "normal"
                i += 1
            i += 1
    return -1


def kotlin_function_and_init_ranges(text: str) -> list[tuple[int, int]]:
    """Return (start, end) offsets of Kotlin `fun` and `init` block bodies."""
    ranges: list[tuple[int, int]] = []
    token_pattern = re.compile(r"\b(?:fun|init)\b")
    i = 0
    n = len(text)
    while True:
        m = token_pattern.search(text, i)
        if not m:
            break
        i = m.end()
        # Skip if the token is inside a string or comment.
        probe = _advance_past_string_or_comment(text, m.start())
        if probe <= m.start():
            continue
        end = _find_block_body_end(text, m.start())
        if end > 0:
            # The body starts at the `{` we just found; store the open offset for containment checks.
            body_open = text.rfind("{", m.start(), end)
            if body_open >= 0:
                ranges.append((body_open, end))
    return ranges


def check_catch_throwable_fatal(path: Path, repo_root: Path, text: str) -> list[Finding]:
    """Catch(Throwable) requires visible fatal propagation in the local block."""
    rel = path.relative_to(repo_root).as_posix()
    findings: list[Finding] = []
    catch_pattern = re.compile(
        r"catch\s*\(\s*(?:\w+\s*:\s*)?(?:\w+\s+)?Throwable(?:\s+\w+)?\s*\)\s*\{",
        re.S,
    )
    for match in catch_pattern.finditer(text):
        open_offset = match.end() - 1
        if open_offset < 0 or text[open_offset] != "{":
            continue
        close_offset = find_block_end(text, open_offset)
        if close_offset < 0:
            continue
        block = text[match.end() : close_offset]
        if not re.search(
            r"\bthrow\b|rethrow|fatal|OutOfMemoryError|ThreadDeath|VirtualMachineError",
            block,
            re.I,
        ):
            line = line_number(text, match.start())
            source_line = text.splitlines()[line - 1]
            if "BRUTAL_ALLOW:CATCH_THROWABLE_NO_FATAL" not in source_line:
                snippet = re.sub(r"\s+", " ", match.group(0))[:100]
                findings.append(
                    Finding(
                        "CATCH_THROWABLE_NO_FATAL",
                        rel,
                        line,
                        (snippet + re.sub(r"\s+", " ", block)[:220]).rstrip(),
                    )
                )
    return findings


def _inside_any(offset: int, ranges: list[tuple[int, int]]) -> bool:
    for start, end in ranges:
        if start < offset < end:
            return True
    return False


def scan_file(path: Path, repo_root: Path) -> list[Finding]:
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return []
    rel = path.relative_to(repo_root).as_posix()
    findings: list[Finding] = []
    fun_ranges = kotlin_function_and_init_ranges(text) if path.suffix == ".kt" else []
    for rule, pattern, _ in RULES:
        for match in pattern.finditer(text):
            if rule == "STATIC_STRONG_ANDROID_OWNER" and fun_ranges and _inside_any(match.start(), fun_ranges):
                continue
            line = line_number(text, match.start())
            source_line = text.splitlines()[line - 1] if text.splitlines() else ""
            if f"BRUTAL_ALLOW:{rule}" in source_line:
                continue
            snippet = re.sub(r"\s+", " ", match.group(0))[:220].rstrip()
            findings.append(Finding(rule, rel, line, snippet))

    findings.extend(check_catch_throwable_fatal(path, repo_root, text))
    return findings


def collect(root: Path, paths: list[str], baseline: dict[str, str] | None = None) -> list[Finding]:
    findings: list[Finding] = []
    seen: set[str] = set()
    for raw in paths:
        path = root / raw
        candidates = [path] if path.is_file() else [
            *path.rglob("*.kt"),
            *path.rglob("*.java"),
            *path.rglob("*.py"),
        ] if path.exists() else []
        for candidate in sorted(set(candidates)):
            for f in scan_file(candidate, root):
                if f.fingerprint in seen:
                    continue
                seen.add(f.fingerprint)
                if baseline is not None:
                    f.disposition = baseline.get(f.fingerprint, "pending")
                findings.append(f)
    return findings


def load_baseline(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    data = json.loads(path.read_text(encoding="utf-8"))
    by_fingerprint: dict[str, str] = {}
    for entry in data.get("findings", []):
        fp = entry.get("fingerprint")
        if fp:
            by_fingerprint[fp] = entry.get("disposition", "pending")
    return by_fingerprint


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--repo-root", default=".")
    p.add_argument(
        "--path",
        action="append",
        default=[],
        help="file or directory relative to repo; repeatable",
    )
    p.add_argument(
        "--scope",
        choices=list(SCOPES.keys()),
        default="production",
        help="predefined scan scope; ignored if --path is given",
    )
    p.add_argument("--baseline", default="tools/SOURCE_HAZARD_BASELINE.json")
    p.add_argument("--write-baseline", action="store_true")
    p.add_argument("--strict-all", action="store_true")
    p.add_argument("--json-output")
    args = p.parse_args(argv)

    root = Path(args.repo_root).resolve()
    paths = args.path or SCOPES[args.scope]
    baseline_path = root / args.baseline
    baseline = load_baseline(baseline_path) if baseline_path.exists() else {}
    findings = collect(root, paths, baseline=baseline)

    if args.write_baseline:
        baseline_path.parent.mkdir(parents=True, exist_ok=True)
        accepted = {f.fingerprint for f in findings}
        # Preserve dispositions from an existing baseline, default new findings to accepted.
        if baseline_path.exists():
            old = load_baseline(baseline_path)
        else:
            old = {}
        for f in findings:
            if f.fingerprint in old:
                f.disposition = old[f.fingerprint]
            else:
                f.disposition = "accepted"
        payload = {
            "schema": 3,
            "scope": paths,
            "fingerprints": sorted({f.fingerprint for f in findings}),
            "findings": [dict(asdict(f), fingerprint=f.fingerprint) for f in findings],
        }
        baseline_path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8", newline="\n")
        print(f"Wrote baseline with {len(findings)} finding(s): {baseline_path}")
        return 0

    if args.strict_all:
        new_findings = [f for f in findings if f.disposition == "pending"]
    else:
        new_findings = [f for f in findings if f.fingerprint not in baseline]
    payload = {
        "total": len(findings),
        "baseline": len(baseline),
        "new": len(new_findings),
        "findings": [dict(asdict(f), fingerprint=f.fingerprint) for f in new_findings],
    }
    if args.json_output:
        out = Path(args.json_output)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")

    if new_findings:
        print(f"New source hazards: {len(new_findings)}")
        for f in new_findings:
            print(f"  {f.path}:{f.line}: {f.rule}: {f.snippet}")
        return 1
    print(f"Source hazard scan passed: {len(findings)} reviewed finding(s), 0 new")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
