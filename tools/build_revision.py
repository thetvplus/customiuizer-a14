#!/usr/bin/env python3
"""Build-revision helpers for CustoMIUIzer A14.

This module exposes reusable, unit-testable helpers used by the build and
provenance verification scripts. It intentionally keeps Android/Gradle I/O in
the calling scripts so that tests can run without the full build toolchain.
"""

from __future__ import annotations

import re
import subprocess
from pathlib import Path
from typing import Iterable

REVISION_RE = re.compile(r"^[0-9a-fA-F]{8}$")

# Untracked paths that are allowed in the repo during a delivery build.
ALLOWED_UNTRACKED: set[str] = {
    "DEVIN_LOCAL_A14_SKILLS_V2/",
}


def git_head_sha(repo: Path, full: bool = False) -> str:
    """Return the current HEAD SHA (8-char by default, 40-char if full=True)."""
    cmd = ["git", "rev-parse", "HEAD"] if full else ["git", "rev-parse", "--short=8", "HEAD"]
    result = subprocess.run(
        cmd,
        cwd=repo,
        capture_output=True,
        text=True,
        check=True,
    )
    return result.stdout.strip()


def validate_revision(rev: str | None) -> str:
    """Return a normalized 8-char lowercase revision or raise ValueError."""
    if rev is None:
        raise ValueError("build revision is required")
    rev = rev.strip()
    if not REVISION_RE.fullmatch(rev):
        raise ValueError(f"build revision must be an 8-character hex SHA, got: {rev!r}")
    return rev.lower()


def normalize_resolved_type(value: int | None) -> int | None:
    """Return the value if it is a non-negative resolved type, otherwise None.

    Mirrors the Kotlin `normalizeResolvedType` used in the production resolver:
    negative values are treated as missing/failed and must not be mistaken for a
    valid type mask or index.
    """
    if value is None:
        return None
    return value if value >= 0 else None


def _is_allowed_untracked_path(path: str) -> bool:
    """Check whether an untracked path is explicitly allowed."""
    for allowed in ALLOWED_UNTRACKED:
        if path == allowed.rstrip("/"):
            return True
        if allowed.endswith("/") and (path.startswith(allowed) or path.startswith(allowed.rstrip("/") + "/")):
            return True
    return False


def is_allowed_untracked(line: str) -> bool:
    """Parse a `git status --porcelain` line and decide if it is allowed.

    Allowed entries are exactly the two excluded paths or anything under the
    allowed directory prefix.
    """
    if not line.startswith("?? "):
        return False
    path = line[3:].strip()
    return _is_allowed_untracked_path(path)


def check_tracked_worktree_clean(repo: Path) -> bool:
    """Verify that tracked files are clean and only allowed untracked files exist."""
    subprocess.run(
        ["git", "diff", "--quiet"],
        cwd=repo,
        check=True,
    )
    subprocess.run(
        ["git", "diff", "--cached", "--quiet"],
        cwd=repo,
        check=True,
    )
    result = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=repo,
        capture_output=True,
        text=True,
        check=True,
    )
    for line in result.stdout.splitlines():
        if not is_allowed_untracked(line):
            raise RuntimeError(f"tracked or disallowed untracked worktree change: {line}")
    return True


def iter_disallowed_untracked(repo: Path) -> Iterable[str]:
    """Yield disallowed status lines, if any."""
    result = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=repo,
        capture_output=True,
        text=True,
        check=True,
    )
    for line in result.stdout.splitlines():
        if not is_allowed_untracked(line):
            yield line
