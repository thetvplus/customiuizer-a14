#!/usr/bin/env python3
"""Static gate that keeps test-only implementation seams out of app/src/main.

The production tree must not contain symbols, imports or annotations that only
exist to support JVM unit tests.  The legitimate Open With sample feature and
its assets are explicitly allow-listed.

Usage:
    python tools/check_main_source_cleanliness.py
    python tools/check_main_source_cleanliness.py --changed
    python tools/check_main_source_cleanliness.py --staged
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
MAIN_ROOT = REPO_ROOT / "app" / "src" / "main"

# Text extensions we are willing to scan.  Binary assets are skipped; the
# allow-list below covers the Open With sample assets by path.
TEXT_EXTENSIONS = {
    ".kt",
    ".java",
    ".xml",
    ".gradle",
    ".kts",
    ".md",
    ".prop",
    ".json",
    ".txt",
    ".py",
}

# Explicitly allowed paths.  These are the real user-facing Open With sample
# assets.  PrefsProvider.kt is NOT exempt: its legitimate "test/*" URI and
# asset names do not match the forbidden test-only implementation patterns and
# therefore need no whole-file exemption.
ALLOWED_PATHS = {
    "app/src/main/assets/test0.png",
    "app/src/main/assets/test1.mp3",
    "app/src/main/assets/test2.mp4",
    "app/src/main/assets/test3.txt",
    "app/src/main/assets/test4.zip",
}

# Test-only symbol names that must not appear in production source.
FORBIDDEN_SYMBOLS = re.compile(
    r"\b(?:"
    r"ForTest|ForTests|"
    r"resetForTest|resetForTests|"
    r"testOnly|"
    r"TestUnhooker"
    r")\b"
)

# Test dependencies must never be imported or referenced in production.
TEST_DEPENDENCY_PATTERN = re.compile(
    r"\b(?:"
    r"org\.junit|"
    r"kotlin\.test|"
    r"org\.mockito|"
    r"mockk"
    r")\b"
)

# AndroidX annotation that marks a declaration as exposed only for testing.
VISIBLE_FOR_TESTING_PATTERN = re.compile(r"@VisibleForTesting\b")

# Phrases in comments that describe a declaration as test-only.  These catch
# hand-rolled seams that do not use the naming patterns above.
TEST_ONLY_COMMENT_PATTERN = re.compile(
    r"(?i)(?:\bfor tests? only\b|\btests? only\b|\btest[- ]only\b)"
)


def _rel_main(path: Path) -> str:
    return path.relative_to(REPO_ROOT).as_posix()


def _git_changed_files(ref: str | None = None) -> list[Path]:
    cmd = ["git", "diff", "--name-only", "--diff-filter=ACMR"]
    if ref:
        cmd.append(ref)
    result = subprocess.run(cmd, cwd=REPO_ROOT, capture_output=True, text=True, check=True)
    paths = []
    for line in result.stdout.splitlines():
        if not line:
            continue
        path = REPO_ROOT / line
        if path.is_file() and _rel_main(path).startswith("app/src/main/"):
            paths.append(path)
    return paths


def _should_scan(path: Path) -> bool:
    rel = _rel_main(path)
    if rel in ALLOWED_PATHS:
        return False
    if path.suffix.lower() not in TEXT_EXTENSIONS:
        return False
    return True


def _find_line(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def _check_text(path: Path, text: str) -> list[str]:
    rel = _rel_main(path)
    issues: list[str] = []
    patterns = [
        ("forbidden-test-symbol", FORBIDDEN_SYMBOLS),
        ("test-dependency-reference", TEST_DEPENDENCY_PATTERN),
        ("visible-for-testing", VISIBLE_FOR_TESTING_PATTERN),
        ("test-only-comment", TEST_ONLY_COMMENT_PATTERN),
    ]
    for rule, pattern in patterns:
        for match in pattern.finditer(text):
            line = _find_line(text, match.start())
            snippet = match.group(0)
            issues.append(f"{rel}:{line}: [{rule}] {snippet}")
    return issues


def _scan_file(path: Path) -> list[str]:
    if not _should_scan(path):
        return []
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        # If a file under main is not text, skip it; binary assets are covered
        # by the allow-list and by the extension filter.
        return []
    return _check_text(path, text)


def _scan_files(files: list[Path]) -> list[str]:
    issues: list[str] = []
    for path in sorted(files):
        issues.extend(_scan_file(path))
    return issues


def _all_main_files() -> list[Path]:
    files: list[Path] = []
    if MAIN_ROOT.exists():
        for path in MAIN_ROOT.rglob("*"):
            if path.is_file():
                files.append(path)
    return files


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--staged", action="store_true", help="check only files staged in git")
    parser.add_argument(
        "--changed",
        action="store_true",
        help="check files changed relative to HEAD (staged or unstaged)",
    )
    args = parser.parse_args()

    if args.staged and args.changed:
        parser.error("--staged and --changed are mutually exclusive")

    if args.staged:
        files = _git_changed_files("--cached")
    elif args.changed:
        files = _git_changed_files("HEAD")
    else:
        files = _all_main_files()

    issues = _scan_files(files)
    if issues:
        print("main-source-cleanliness: FAILED")
        for issue in issues:
            print(f"  {issue}")
        return 1

    print(f"main-source-cleanliness: {len(files)} files, no violations")
    return 0


if __name__ == "__main__":
    sys.exit(main())
