#!/usr/bin/env python3
"""Route a complete task diff to existing gates; never installs or signs an APK.

plan is read-only. host executes only the host_commands it prints. Device,
artifact and performance requirements remain explicit pending work for the agent.
"""
from __future__ import annotations

import argparse
import ast
import json
import re
from pathlib import Path
import subprocess
import sys

try:
    from tools.ci_scope import select_scope
    from tools.git_changes import changed_paths
    from tools.device_verify import SCENARIOS
except ModuleNotFoundError:
    from ci_scope import select_scope
    from git_changes import changed_paths
    from device_verify import SCENARIOS

ROOT = Path(__file__).resolve().parent.parent
TEST_ROOT = "app/src/test/java/"
SOURCE = "app/src/main/"
# Bounded feature ownership, not a second Feature catalog. Test names are resolved
# from the current checkout; an empty selection is an error rather than a pass.
FEATURES = {
    "deck": ("mods/recents/", ("**/mods/recents/*Test.*",), "deck-open-return"),
    "island": ("mods/island/", ("**/mods/island/*Test.*",), "island-takeover"),
    "dock": ("mods/Dock", ("**/mods/Dock*Test.*",), "dock-overview-return"),
    "gesture": ("mods/utils/gesture/", ("**/mods/utils/gesture/*Test.*",), "gesture-cancel"),
}


def task_paths(root: Path, base: str | None) -> list[str]:
    if base:
        # Comparing to the working tree includes committed + staged + unstaged
        # changes. Validate the revision before passing it to git diff.
        revision = subprocess.check_output(
            ["git", "rev-parse", "--verify", f"{base}^{{commit}}"], cwd=root, text=True).strip()
        return sorted(changed_paths(root, base=revision))
    return sorted(changed_paths(root))


def tool_tests(root: Path, paths: list[str]) -> list[str] | None:
    """Transient Python import closure; shared gates and unknown inputs stay broad."""
    if any(p.startswith((".github/", "rom-contracts/", "docs/rom-intelligence/", "tools/brutal_", "tools/dexsql/"))
           or p.startswith("tools/") and ("/" not in p[6:] and any(token in Path(p).stem for token in
               ("verify_apk", "release", "ci_", "catalog", "generate", "extract", "check_", "scan")))
           or p in ("tools/verify.py", "tools/git_changes.py") for p in paths):
        return None
    changed = {p[:-3].replace("/", ".") for p in paths if p.startswith("tools/") and p.endswith(".py")}
    if any(p.startswith("tools/device/") for p in paths):
        changed.add("tools.device_verify")
    if not changed:
        return None  # Catalog/non-Python inputs use the broad gate without an import graph.
    files = [*root.glob("tools/*.py"), *root.glob("tools/tests/test_*.py")]
    literal_targets = {p.name: p.relative_to(root).with_suffix("").as_posix().replace("/", ".") for p in files}
    graph = {}
    for path in files:
        module = path.relative_to(root).with_suffix("").as_posix().replace("/", ".")
        try:
            tree = ast.parse(path.read_text(encoding="utf-8"))
        except (OSError, SyntaxError):
            return None
        deps = set()
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                deps.update(n.name for n in node.names)
            elif isinstance(node, ast.ImportFrom):
                prefix = node.module or ""
                if node.level: prefix = module.rsplit(".", node.level)[0] + ("." + prefix if prefix else "")
                deps.add(prefix)
                deps.update(prefix + "." + n.name for n in node.names)
            elif isinstance(node, ast.Constant) and isinstance(node.value, str):
                target = literal_targets.get(node.value.replace("\\", "/").rsplit("/", 1)[-1])
                if target: deps.add(target)  # subprocess / importlib source consumers
        graph[module] = {d if d.startswith("tools.") else "tools." + d for d in deps}
    if any(c not in graph for c in changed):
        return None
    affected = set(changed)
    while True:
        next_set = affected | {name for name, deps in graph.items() if deps & affected}
        if next_set == affected: break
        affected = next_set
    tests = sorted(name for name in affected if name.startswith("tools.tests.test_"))
    # This checker intentionally consumes the whole tooling tree, without imports.
    tests.append("tools.tests.test_check_ci_portability")
    return sorted(set(tests)) if len(tests) > 1 else None


def leaf_tests(root: Path, path: str, patterns: tuple[str, ...], sources: dict | None = None) -> list[str] | None:
    """Narrow only isolated value/math types; lifecycle/reflection stays owner-wide.

    Reuses the existing lexical source reader. Cross-owner consumers and source
    tests using reflection strings keep the bounded owner fallback explicit.
    """
    try:
        from tools.agent_context import facts, scrub_comments
    except ModuleNotFoundError:
        from agent_context import facts, scrub_comments
    source = root / path
    if not source.is_file(): return None
    text = source.read_text(encoding="utf-8")
    info = facts(path, text)
    if info["hooks"] or info["loader"] or info["lifecycle"] or re.search(
            r"Xposed|reflect|SharedPreferences|StateFlow|Coroutine|synchronized|Handler|Listener|\bView\b", text):
        return None
    # Limit precision to the reviewed value-only seams. Expanding this set needs
    # the same consumer/behavior review; arbitrary feature files stay broad.
    if source.name not in {"IslandSizing.kt", "IslandCapsuleGeometry.kt", "IosDeckGeometry.kt"}:
        return None
    names = {s["name"] for s in info["symbols"] if s["kind"] in {"class", "object", "interface"} and not s["owner"]}
    if not names: return None
    pattern = re.compile(r"\b(?:" + "|".join(map(re.escape, names)) + r")\b")
    # Broaden through in-owner consumers only. A caller crossing the owner is a
    # reason to preserve broad validation, not to pretend a lexical graph is ABI.
    owners = set(names)
    for other, other_text in (sources or source_texts(root)).items():
        if other == source: continue
        if pattern.search(other_text):
            if other.parent != source.parent: return None
            caller = facts(other.relative_to(root).as_posix(), other_text)
            owners.update(s["name"] for s in caller["symbols"] if not s["owner"] and s["kind"] != "fun")
    selected, remaining = [], {p: scrub_comments(p.read_text(encoding="utf-8"))
                               for p in (root / TEST_ROOT).rglob("*") if p.suffix in {".kt", ".java"}}
    while True:
        pattern = re.compile(r"\b(?:" + "|".join(map(re.escape, owners)) + r")\b")
        matched = [p for p, text in remaining.items() if pattern.search(text)]
        if not matched: break
        for test in matched:
            text = remaining.pop(test)
            if test.stem.endswith("Test"):
                selected.append(test.relative_to(root / TEST_ROOT).with_suffix("").as_posix().replace("/", "."))
            info = facts(test.as_posix(), text)
            owners.update(s["name"] for s in info["symbols"] if not s["owner"] and s["kind"] != "fun")
        if len(owners) > 128: return None
    return sorted(selected) or None


def source_texts(root: Path) -> dict:
    try:
        from tools.agent_context import scrub_comments
    except ModuleNotFoundError:
        from agent_context import scrub_comments
    return {p: scrub_comments(p.read_text(encoding="utf-8")) for p in (root / "app/src/main/java").rglob("*")
            if p.suffix in {".kt", ".java"}}


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
    settings = any(p.startswith("app/settings/") for p in paths)
    tests = set()
    unknown = []
    sources = None
    resolved_tests = {}
    def behavior_tests(patterns):
        # One plan observes one checkout; reuse the same owner lookup across
        # changed files/cross-owner callers, with no cache surviving this call.
        if patterns not in resolved_tests:
            resolved_tests[patterns] = test_classes(root, patterns)
        return resolved_tests[patterns]
    for path in production:
        feature = next((name for name, (fragment, _, _) in FEATURES.items() if fragment in path), None)
        if feature:
            if sources is None: sources = source_texts(root)
            _, patterns, scenario = FEATURES[feature]
            narrow = leaf_tests(root, path, patterns, sources)
            tests.update(narrow or behavior_tests(patterns))
            scenarios.add(scenario)
            reasons.append(f"{path}: {feature} owner and behavior regressions")
            if narrow: reasons.append(f"{path}: reviewed value seam + direct consumer behavior tests")
            # Follow exported types into other feature owners. Installer/catalog
            # references only establish installation; shared business consumers
            # outside a known owner preserve the existing Full fallback.
            current = sources.get(root / path, "")
            names = set(re.findall(r"\b(?:class|object|interface)\s+([A-Z]\w+)", current))
            if names:
                references = re.compile(r"\b(?:" + "|".join(map(re.escape, names)) + r")\b")
                for caller, text in sources.items():
                    if caller == root / path or not references.search(text): continue
                    relative = caller.relative_to(root).as_posix()
                    other = next((key for key, (fragment, _, _) in FEATURES.items() if fragment in relative), None)
                    if other and other != feature:
                        tests.update(behavior_tests(FEATURES[other][1]))
                        scenarios.add(FEATURES[other][2])
                        reasons.append(f"{path}: cross-owner consumer {relative}")
                    elif not other and "/mods/utils/feature/" not in relative:
                        unknown.append(relative)
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
    # Settings inputs require actual generation + behavior, not unrelated full
    # Debug lint. Unknown shared runtime/build inputs retain the existing Full.
    if unknown or build:
        commands.append(["python", "tools/verify.py", "full"])
        reasons.append("Shared/unmapped runtime, removed test or build boundary: use existing Full gate")
    elif production or tests or settings:
        if settings:
            tests.update(behavior_tests(("**/*Settings*Test.*", "**/*Preference*Test.*")))
        commands.append(["python", "tools/verify.py", "fast", "--tests", *sorted(tests)])
    if select_scope("local", "", paths)["tools"]:
        selected_tools = tool_tests(root, paths)
        commands.append(["python", "-m", "unittest", *selected_tools, "-b"] if selected_tools else
                        ["python", "-m", "unittest", "discover", "-s", "tools/tests", "-p", "test_*.py", "-b"])
        commands.append(["python", "tools/brutal_test_runner.py", "--config", "tools/brutal_test_config.json", "validate"])
        reasons.append("Verification/tool consumers: current tool regressions and mutation policy")
    if any(p.endswith(".md") for p in paths):
        commands.append(["python", "tools/check_docs.py"])
    if settings:
        commands.append(["python", "tools/generate_feature_map.py", "--check"])
    if production or build or settings:
        pending += ["ROM/API feasibility: reuse verified ABI/owner evidence; changed private signatures need targeted DexSQL definitions + callers + decode",
                    "Runtime: verify installed candidate identity, LSPosed scope/process activation and observable feature effect; Host success does not prove a hook ran",
                    "Performance: same device/ROM/settings/scenario, separate automation from sampling; baseline and candidate Perfetto windows remain required for claims"]
    if build or any(p.endswith((".xml", ".pro")) or "/res/" in p for p in production):
        pending.append("Artifact boundary: :app:assembleDevelop :app:lintVitalDevelop and affected R8/API output checks")
    if any("brutal_" in p or p == "tools/verify.py" for p in paths):
        pending.append("Mutation changes: commit inputs, then run selected cases from clean HEAD with the existing runner and inspect actual JUnit failures")
    if not paths:
        reasons.append("No working-tree changes; use --base for a committed task diff. This is not verification success.")
    available = [{"name": name, "status": "SUPPORTED" if SCENARIOS.get(name, {}).get("implemented", name in SCENARIOS) else "NOT_IMPLEMENTED",
                  "proof": SCENARIOS.get(name, {}).get("proof", "UNVERIFIED"),
                  "requires": SCENARIOS.get(name, {}).get("requires", "target ROM preflight")} for name in sorted(scenarios)]
    return {"paths": paths, "reasons": sorted(set(reasons)), "host_commands": commands,
            "device_scenarios": [s["name"] for s in available if s["status"] == "SUPPORTED"],
            "scenario_requirements": available, "pending": pending,
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
