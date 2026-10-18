"""Changed worktree paths shared by local verification gates."""

from __future__ import annotations

import subprocess
from pathlib import Path


def changed_paths(repo: Path, *, staged: bool = False) -> list[str]:
    """List index changes, or all changes from HEAD including untracked files."""
    commands = [["diff", "--no-renames", "--name-only", "-z", "--diff-filter=ACMRD",
                 "--cached" if staged else "HEAD", "--"]]
    if not staged:
        commands.append(["ls-files", "--others", "--exclude-standard", "-z"])

    paths = []
    for command in commands:
        output = subprocess.check_output(["git", *command], cwd=repo)
        paths.extend(output.decode("utf-8").split("\0")[:-1])
    return list(dict.fromkeys(paths))
