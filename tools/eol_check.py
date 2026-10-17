#!/usr/bin/env python3
"""Lightweight EOL / encoding policy gate.

Expectations:
- Tracked text blobs in the index are LF (i/lf or i/none for empty files).
- Worktree text files are LF except for *.bat / *.cmd, which may be CRLF.
- Binary files are ignored.
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent


def fail(message: str) -> None:
    print(f"eol-check: {message}", file=sys.stderr)
    sys.exit(1)


def _is_eol_allowed(path: str, i_eol: str, w_eol: str, attrs: list[str]) -> bool:
    """Return True if the worktree EOL is acceptable for the given path/attrs."""
    if w_eol in ("-text", "none"):
        return True
    if "-text" in attrs:
        return True
    if w_eol == "lf":
        return True
    if w_eol == "crlf" and (path.endswith(".bat") or path.endswith(".cmd")):
        return True
    return False


def _is_index_eol_allowed(i_eol: str, attrs: list[str]) -> bool:
    """Return True if the index EOL is acceptable."""
    if i_eol in ("-text", "none"):
        return True
    if "-text" in attrs:
        return True
    return i_eol == "lf"


def check() -> int:
    result = subprocess.run(
        ["git", "ls-files", "--eol"],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        check=True,
    )

    violations: list[str] = []
    for line in result.stdout.splitlines():
        if not line.strip():
            continue

        # git ls-files --eol outputs:
        # <i/eol> <w/eol> <attr tokens...>\t<path>
        parts = line.split("\t")
        if len(parts) != 2:
            continue

        info, path = parts[0], parts[1].strip()
        tokens = info.split()
        if len(tokens) < 2:
            continue

        # git ls-files --eol prefixes the EOL type with "i/" or "w/".
        i_eol = tokens[0].split("/", 1)[1]
        w_eol = tokens[1].split("/", 1)[1]
        raw_attrs = tokens[2:] if len(tokens) > 2 else []
        attrs = [a.removeprefix("attr/") for a in raw_attrs if a and a != "attr/"]

        if not _is_index_eol_allowed(i_eol, attrs):
            violations.append(f"index {i_eol}: {path}")

        if not _is_eol_allowed(path, i_eol, w_eol, attrs):
            violations.append(f"worktree {w_eol}: {path}")

    if violations:
        for v in violations:
            print(f"eol-check: violation: {v}", file=sys.stderr)
        fail(f"EOL policy violations: {len(violations)}")

    print("eol-check: passed")
    return 0


if __name__ == "__main__":
    sys.exit(check())
