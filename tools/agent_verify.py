#!/usr/bin/env python3
"""Route a complete task diff to existing gates; never installs or signs an APK.

plan is read-only. host executes only the host_commands it prints. Device,
artifact and performance requirements remain explicit pending work for the agent.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess
import sys

try:
    from tools.git_changes import changed_paths
except ModuleNotFoundError:
    from git_changes import changed_paths

ROOT = Path(__file__).resolve().parent.parent
TEST_ROOT = "app/src/test/java/"
SOURCE = "app/src/main/"
# Bounded feature ownership, not a second Feature catalog. Test names are resolved
# from the current checkout; an empty selection is an error rather than a pass.
FEATURES = {
    "deck": ("mods/recents/", ("**/mods/recents/*Test.*",), "deck-open-return"),
    "island": ("mods/island/", ("**/mods/island/*Test.*",), "island-notice"),
    "dock": ("mods/Dock", ("**/mods/Dock*Test.*",), "dock-overview-return"),
    "gesture": ("mods/utils/gesture/", ("**/mods/utils/gesture/*Test.*",), "gesture-cancel"),
}


def task_paths(root: Path, base: str | None) -> list[str]:
    paths = changed_paths(root)
    if base:
        # Comparing to the working tree includes committed + staged + unstaged
        # changes. Validate the revision before passing it to git diff.
        revision = subprocess.check_output(
            ["git", "rev-parse", "--verify", f"{base}^{{commit}}"], cwd=root, text=True).strip()
        output = subprocess.check_output(
            ["git", "diff", "--no-renames", "--name-only", "-z", revision, "--"], cwd=root)
        paths.extend(output.decode("utf-8").split("\0")[:-1])
    return sorted(set(paths))


def test_classes(root: Path, patterns: tuple[str, ...]) -> list[str]:
    found = set()
    for pattern in patterns:
        matches = list((root / TEST_ROOT).glob(pattern))
        if not matches:
            raise ValueError(f"No current behavior tests for {pattern}")
        for path in matches:
            found.add(path.relative_to(root / TEST_ROOT).with_suffix("").as_posix().replace("/", "."))
    return sorted(found)


def plan(root: Path, paths: list[str]) -> dict:
    commands, pending, scenarios, reasons = [], [], set(), []
    production = [p for p in paths if p.startswith(SOURCE)]
    tests = set()
    unknown = []
    for path in production:
        feature = next((name for name, (fragment, _, _) in FEATURES.items() if fragment in path), None)
        if feature:
            _, patterns, scenario = FEATURES[feature]
            tests.update(test_classes(root, patterns))
            scenarios.add(scenario)
            reasons.append(f"{path}: {feature} owner and behavior regressions")
        else:
            unknown.append(path)
    build = any(p.startswith(("gradle/", "gradlew", "app/lib/")) or p in {
        "app/build.gradle.kts", "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
        "app/proguard-rules.pro"} for p in paths)
    for path in paths:
        if path.startswith(TEST_ROOT) and path.endswith(("Test.kt", "Test.java")):
            if (root / path).is_file():
                tests.add(path[len(TEST_ROOT):].rsplit(".", 1)[0].replace("/", "."))
            else:
                unknown.append(path)  # deleted coverage cannot silently disappear
        elif path.startswith("app/src/test/"):
            unknown.append(path)  # shared fixtures/support may affect any suite
    if unknown or build:
        commands.append(["python", "tools/verify.py", "full"])
        reasons.append("Shared/unmapped runtime, removed test or build boundary: use existing Full gate")
    elif production or tests:
        commands.append(["python", "tools/verify.py", "fast", "--tests", *sorted(tests)])
    if any(p.startswith(("tools/", ".github/", "rom-contracts/")) for p in paths):
        commands.append(["python", "-m", "unittest", "discover", "-s", "tools/tests", "-p", "test_*.py", "-b"])
        commands.append(["python", "tools/brutal_test_runner.py", "--config", "tools/brutal_test_config.json", "validate"])
        reasons.append("Verification/tool consumers: current tool regressions and mutation policy")
    if any(p.endswith(".md") for p in paths):
        commands.append(["python", "tools/check_docs.py"])
    if production or build:
        pending += ["ROM/API feasibility: reuse verified ABI/owner evidence; changed private signatures need targeted DexSQL definitions + callers + decode",
                    "Runtime: verify installed candidate identity, LSPosed scope/process activation and observable feature effect; Host success does not prove a hook ran",
                    "Performance: same device/ROM/settings/scenario, separate automation from sampling; baseline and candidate Perfetto windows remain required for claims"]
    if build or any(p.endswith((".xml", ".pro")) or "/res/" in p for p in production):
        pending.append("Artifact boundary: :app:assembleDevelop :app:lintVitalDevelop and affected R8/API output checks")
    if any("brutal_" in p or p == "tools/verify.py" for p in paths):
        pending.append("Mutation changes: commit inputs, then run selected cases from clean HEAD with the existing runner and inspect actual JUnit failures")
    if not paths:
        reasons.append("No working-tree changes; use --base for a committed task diff. This is not verification success.")
    return {"paths": paths, "reasons": sorted(set(reasons)), "host_commands": commands,
            "device_scenarios": sorted(scenarios), "pending": pending,
            "evidence_reuse": "Reuse successful logs/reports only for equal inputs, environment and scope per docs/TESTING.md; this plan is not a receipt or a pass."}


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("plan", "host"))
    parser.add_argument("--base", help="exact task base commit, including already committed work")
    args = parser.parse_args(argv)
    try:
        selected = plan(ROOT, task_paths(ROOT, args.base))
        print(json.dumps(selected, ensure_ascii=False, indent=2), flush=True)
        if args.command == "host":
            if not selected["host_commands"]:
                return 2
            for command in selected["host_commands"]:
                result = subprocess.run([sys.executable, *command[1:]], cwd=ROOT)
                if result.returncode:
                    return result.returncode
        return 0
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        print(f"agent_verify: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
