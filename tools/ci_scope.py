#!/usr/bin/env python3
"""Select additional CI work from event data and the complete Git diff."""
from __future__ import annotations

import json
import os
import subprocess
from pathlib import Path


def select_scope(event_name: str, ref: str, event: dict, paths: list[str]) -> dict[str, bool]:
    periodic = event_name in {"workflow_dispatch", "schedule"} or ref.startswith("refs/tags/r14.")
    tools_changed = any(path.startswith((
        "tools/", ".github/",
        "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/",
        "docs/rom-intelligence/A14_PROCESS_",
    )) for path in paths)
    build_changed = any(
        path.startswith(("gradle/", "app/lib/", "gradlew")) or path in {
            "app/build.gradle.kts", "app/proguard-rules.pro", "build.gradle.kts",
            "settings.gradle.kts", "gradle.properties", "local.properties",
        } for path in paths
    )
    message = (event.get("head_commit") or {}).get("message", "")
    return {"tools": periodic or tools_changed,
            "full": periodic or tools_changed or build_changed or "[full-ci]" in message}


def changed_paths(event_name: str, event: dict, cwd: Path | None = None) -> list[str]:
    if event_name == "pull_request":
        pr = event["pull_request"]
        comparison = f"{pr['base']['sha']}...{pr['head']['sha']}"
    elif event_name == "push":
        before, after = event["before"], event["after"]
        if before == "0" * 40:
            command = ["git", "ls-tree", "-r", "--name-only", "-z", after]
            return subprocess.check_output(command, cwd=cwd).decode("utf-8").split("\0")[:-1]
        comparison = f"{before}..{after}"
    else:
        return []
    output = subprocess.check_output(["git", "diff", "--name-only", "-z", comparison], cwd=cwd)
    return output.decode("utf-8").split("\0")[:-1]


def main() -> int:
    event_name = os.environ["GITHUB_EVENT_NAME"]
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    scope = select_scope(event_name, os.environ["GITHUB_REF"], event, changed_paths(event_name, event))
    with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as output:
        for key, value in scope.items():
            output.write(f"{key}={str(value).lower()}\n")
    print(json.dumps(scope, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
