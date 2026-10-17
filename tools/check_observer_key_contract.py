#!/usr/bin/env python3
"""Static gate for preference observer onChange key matching.

Production [PreferenceObserver] implementations must match against canonical
short keys only. This script flags any observer whose onChange body still
contains raw `pref_key_` string literals or helper calls for prefix handling.

Supports:
- Kotlin named classes and anonymous objects implementing `PreferenceObserver`
  or `ModuleHelper.PreferenceObserver`.
- Kotlin `onChange` with either a block body (`{ ... }`) or an expression body
  that ends in a trailing lambda (`= ModuleHelper.guarded { ... }` etc.).
- Future Java implementations are rejected explicitly; once a Java
  implementation exists the project can either extend this script or remove the
  explicit rejection after review.

The interface declarations in `PreferenceObserverRegistry.kt` and
`ModuleHelper.kt` are intentionally ignored.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from source_hazard_scan import find_block_end

REPO_ROOT = Path(__file__).resolve().parent.parent
SOURCE_ROOT = REPO_ROOT / "app" / "src" / "main" / "java"

# Matches the start of a Kotlin preference-observer implementation.
# It captures the open brace that begins the class/object body.
#
# Examples that must match:
#   object : ModuleHelper.PreferenceObserver {
#   private val observer = object : ModuleHelper.PreferenceObserver {
#   class Foo : PreferenceObserver {
#   class Foo(val x: Int) : Some, ModuleHelper.PreferenceObserver {
#
# The negative lookbehind rejects lines that declare an interface, e.g.
#   interface PreferenceObserver : PreferenceObserverRegistry.PreferenceObserver {
KOTLIN_OBSERVER_PATTERN = re.compile(
    r"(?<![\w.])(?:object\s*:\s*|class\s+\w+(?:\s*\([^)]*\))?\s*:\s*(?:[\w.\s,]+,\s*)?)(?:ModuleHelper\.)?PreferenceObserver\b"
    r"(?:\s*<[^>]+>\s*)?(?:\s*,\s*[\w.]+)*\s*\{\s*",
    re.S,
)

# Matches a Java anonymous implementation or named class implementing the
# interface. These are reported as explicit failures because the project
# currently has zero Java PreferenceObserver implementations.
JAVA_OBSERVER_PATTERN = re.compile(
    r"new\s+(?:ModuleHelper\.)?PreferenceObserver\s*\(\s*\)\s*\{"
    r"|class\s+\w+\s+(?:[\w\s,]+\s+)?implements\s+(?:[\w.\s,]+,\s*)?(?:ModuleHelper\.)?PreferenceObserver\b",
    re.S,
)

# Matches a Kotlin onChange method declaration, either block-bodied or
# expression-bodied.  The group is the parameter name (unused for scanning).
KOTLIN_ONCHANGE_PATTERN = re.compile(
    r"override\s+fun\s+onChange\s*\(\s*(\w+)\s*:\s*String\?\s*\)\s*(?:=|\{)",
    re.S,
)

FORBIDDEN_PATTERNS = [
    re.compile(r'"pref_key_[^"]*"'),
    re.compile(r'\bpref_key_[a-zA-Z0-9_]+\b'),
    re.compile(r'\.contains\s*\(\s*"pref_key_'),
    re.compile(r'\.startsWith\s*\(\s*"pref_key_'),
    re.compile(r'\.endsWith\s*\(\s*"pref_key_'),
]

EXCLUDED_PATHS = {
    # Non-observer production code may still mention storage keys legitimately.
    "app/src/main/java/tv/withaibuild/customiuizer/utils/PreferenceKeys.kt",
    "app/src/main/java/tv/withaibuild/customiuizer/utils/PrefMap.kt",
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/PreferenceBootstrap.kt",
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/PreferenceObserverRegistry.kt",
}


def find_kotlin_observer_bodies(text: str) -> list[tuple[int, int]]:
    """Return start/end offsets for each Kotlin PreferenceObserver body in [text]."""
    bodies: list[tuple[int, int]] = []
    for match in KOTLIN_OBSERVER_PATTERN.finditer(text):
        open_offset = text.find("{", match.start(), match.end())
        if open_offset < 0:
            continue
        # Reject matches that are actually part of an `interface` declaration
        # line (e.g. `interface PreferenceObserver : PreferenceObserver { ... }`).
        # The regex covers the interface name as the first `PreferenceObserver`;
        # interface declarations are not implementations and must be skipped.
        line_start = text.rfind("\n", 0, match.start()) + 1
        before = text[line_start:match.start()]
        stripped = before.strip()
        if stripped == "" or stripped.startswith("//"):
            # The keyword is on a previous line; inspect further up.
            prev_line_start = text.rfind("\n", 0, line_start - 1) + 1
            before = text[prev_line_start:line_start - 1]
            stripped = before.strip()
        if re.search(r"\binterface\b\s+(?:\w+\s*)?:?\s*$", before):
            continue
        close_offset = find_block_end(text, open_offset)
        if close_offset > 0:
            bodies.append((open_offset, close_offset))
    return bodies


def _walk(text: str, start: int, stop_on_brace: bool = False) -> int | None:
    """Scan forward, skipping strings and comments, optionally stopping at '{'.

    Returns the offset of the first '{' when [stop_on_brace] is True, otherwise
    returns the length of the text.  The scan also returns early when the brace
    or end-of-input is reached while inside a string or comment.
    """
    i = start
    in_string: str | None = None
    in_line_comment = False
    in_block_comment = False
    while i < len(text):
        ch = text[i]
        if in_string:
            if ch == in_string and text[i - 1] != "\\":
                in_string = None
            i += 1
            continue
        if in_line_comment:
            if ch == "\n":
                in_line_comment = False
            i += 1
            continue
        if in_block_comment:
            if ch == "*" and i + 1 < len(text) and text[i + 1] == "/":
                in_block_comment = False
                i += 2
                continue
            i += 1
            continue
        if ch in ('"', "'", "`"):
            in_string = ch
            i += 1
            continue
        if ch == "/" and i + 1 < len(text):
            if text[i + 1] == "/":
                in_line_comment = True
                i += 2
                continue
            if text[i + 1] == "*":
                in_block_comment = True
                i += 2
                continue
        if stop_on_brace and ch == "{":
            return i
        i += 1
    return len(text) if not stop_on_brace else -1


def _is_member_declaration_brace(text: str, brace_offset: int) -> bool:
    """Return True if the brace at [brace_offset] starts a class/object member."""
    line_start = text.rfind("\n", 0, brace_offset) + 1
    line = text[line_start:brace_offset].strip()
    return bool(
        re.match(
            r"(?:fun|class|object|companion|init|val|var|override)\b|}\s*$",
            line,
        )
    )


def _find_simple_expression_end(text: str, start: int) -> int:
    """Scan for the end of a simple expression body (next member or class end)."""
    i = start
    in_string: str | None = None
    in_line_comment = False
    in_block_comment = False
    while i < len(text):
        ch = text[i]
        if in_string:
            if ch == in_string and text[i - 1] != "\\":
                in_string = None
            i += 1
            continue
        if in_line_comment:
            if ch == "\n":
                in_line_comment = False
            i += 1
            continue
        if in_block_comment:
            if ch == "*" and i + 1 < len(text) and text[i + 1] == "/":
                in_block_comment = False
                i += 2
                continue
            i += 1
            continue
        if ch in ('"', "'", "`"):
            in_string = ch
            i += 1
            continue
        if ch == "/" and i + 1 < len(text):
            if text[i + 1] == "/":
                in_line_comment = True
                i += 2
                continue
            if text[i + 1] == "*":
                in_block_comment = True
                i += 2
                continue
        if ch == "\n":
            line_start = i + 1
            while line_start < len(text) and text[line_start] in " \t\r":
                line_start += 1
            if line_start < len(text):
                rest = text[line_start:]
                if re.match(
                    r"(?:fun|val|var|companion|init|override|class|object)\b|}\s*(?:\n|$)",
                    rest,
                ):
                    return i
        i += 1
    return len(text)


def extract_onchange_body(text: str, match: re.Match) -> tuple[int, int] | None:
    """Return (start, end) offsets of an onChange body, or None if unparsable."""
    after = match.end()
    # Block body: the opening brace is right after the signature.
    if text[after - 1] == "{":
        close = find_block_end(text, after - 1)
        if close > 0:
            return after - 1, close
        return None

    # Expression body: skip '=' and whitespace, then locate the body.
    eq_pos = match.start() + match.group(0).index("=")
    expr_start = eq_pos + 1
    while expr_start < len(text) and text[expr_start] in " \t\r\n":
        expr_start += 1
    if expr_start >= len(text):
        return None

    # If the expression starts with a brace it is a lambda expression body.
    if text[expr_start] == "{":
        close = find_block_end(text, expr_start)
        if close > 0:
            return expr_start, close
        return None

    # Look for the first '{'.  If it is part of a trailing lambda we use it as
    # the body; if it belongs to a subsequent class/object member we stop at
    # the simple expression boundary instead.
    first_brace = _walk(text, expr_start, stop_on_brace=True)
    if first_brace >= 0 and not _is_member_declaration_brace(text, first_brace):
        close = find_block_end(text, first_brace)
        if close > 0:
            return first_brace, close

    end = _find_simple_expression_end(text, expr_start)
    return expr_start, end


def check_kotlin_file(path: Path, rel: str, text: str) -> list[str]:
    issues: list[str] = []
    for body_start, body_end in find_kotlin_observer_bodies(text):
        body = text[body_start:body_end]
        for match in KOTLIN_ONCHANGE_PATTERN.finditer(body):
            onchange = extract_onchange_body(body, match)
            if onchange is None:
                continue
            onchange_start, onchange_end = onchange
            onchange_body = body[onchange_start:onchange_end]
            for pattern in FORBIDDEN_PATTERNS:
                if pattern.search(onchange_body):
                    line = text[: body_start + onchange_start].count("\n") + 1
                    issues.append(
                        f"{rel}:{line}: observer onChange contains pref_key_ pattern: {pattern.pattern}"
                    )
                    break
    return issues


def check_java_file(path: Path, rel: str, text: str) -> list[str]:
    issues: list[str] = []
    if JAVA_OBSERVER_PATTERN.search(text):
        issues.append(
            f"{rel}: Java PreferenceObserver implementation detected; "
            "extend the contract gate or remove this after explicit review"
        )
    return issues


def check_file(path: Path, repo_root: Path | None = None) -> list[str]:
    repo_root = repo_root or REPO_ROOT
    rel = path.relative_to(repo_root).as_posix()
    if rel in EXCLUDED_PATHS:
        return []

    text = path.read_text(encoding="utf-8")
    if path.suffix == ".kt":
        return check_kotlin_file(path, rel, text)
    if path.suffix == ".java":
        return check_java_file(path, rel, text)
    return []


def scan_source(source_root: Path | None = None) -> list[str]:
    source_root = source_root or SOURCE_ROOT
    issues: list[str] = []
    for pattern in ("**/*.kt", "**/*.java"):
        for path in sorted(source_root.rglob(pattern)):
            issues.extend(check_file(path))
    return issues


def main() -> int:
    issues = scan_source()
    if issues:
        print("observer-key-contract: FAILED")
        for issue in issues:
            print(f"  {issue}")
        return 1

    print("observer-key-contract: passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
