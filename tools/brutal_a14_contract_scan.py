#!/usr/bin/env python3
"""Apply-check scanner for the A14 source mutation suite.

This tool verifies whether a mutator actually changed the source tree.  It
extracts the patterns the mutator targets and checks whether those patterns
appear with a different count in the current worktree than they did in the
original committed source.  It is intentionally not a kill gate: a positive
result only means the mutation was applied, not that an independent product
test caught the defect.

Usage:
    python tools/brutal_a14_contract_scan.py --mutation <mutator_name>
    python tools/brutal_a14_contract_scan.py --self-test

Exit protocol:
    0  MUTATION_APPLIED
    2  CANNOT_VERIFY
    3  SCANNER_ERROR
    4  MUTATION_NOT_APPLIED
"""
from __future__ import annotations

import argparse
import ast
import importlib.util
import inspect
import re
import subprocess
import sys
from pathlib import Path


def repo_root() -> Path:
    result = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"],
        text=True,
        encoding="utf-8",
        capture_output=True,
        check=True,
    )
    return Path(result.stdout.strip()).resolve()


def git_show(root: Path, rel: str, ref: str = "HEAD") -> str:
    """Return the contents of *rel* at the given ref."""
    result = subprocess.run(
        ["git", "show", f"{ref}:{rel}"],
        cwd=root,
        text=True,
        encoding="utf-8",
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        return ""
    return result.stdout


def resolve_rel(node: ast.AST) -> str | None:
    """Resolve the `rel` argument from a mutator call."""
    if isinstance(node, ast.Constant) and isinstance(node.value, str):
        return node.value
    if isinstance(node, ast.Call):
        func = node.func
        if isinstance(func, ast.Name) and func.id in ("_kt_file", "_java_file"):
            if len(node.args) == 1 and isinstance(node.args[0], ast.Constant):
                rel = node.args[0].value
                if isinstance(rel, str):
                    return f"app/src/main/java/{rel}"
    return None


def resolve_pattern(node: ast.AST) -> str | None:
    if isinstance(node, ast.Constant) and isinstance(node.value, str):
        return node.value
    return None


def resolve_flags(node: ast.AST) -> int:
    if isinstance(node, ast.Constant) and isinstance(node.value, int):
        return node.value
    if isinstance(node, ast.Attribute) and isinstance(node.value, ast.Name) and node.value.id == "re":
        if node.attr == "DOTALL":
            return re.DOTALL
        if node.attr == "MULTILINE":
            return re.MULTILINE
        if node.attr == "IGNORECASE":
            return re.IGNORECASE
    if isinstance(node, ast.BinOp) and isinstance(node.op, ast.BitOr):
        return resolve_flags(node.left) | resolve_flags(node.right)
    return 0


def extract_patterns(source: str) -> list[tuple[str, str, int]]:
    """Extract (rel, pattern, flags) from a mutator function body."""
    tree = ast.parse(source)
    patterns: list[tuple[str, str, int]] = []

    for node in ast.walk(tree):
        if isinstance(node, ast.Call):
            func = node.func
            if isinstance(func, ast.Name) and func.id in ("_replace_first", "_replace_all"):
                if len(node.args) >= 3:
                    rel = resolve_rel(node.args[1])
                    pattern = resolve_pattern(node.args[2])
                    flags = 0
                    for kw in node.keywords:
                        if kw.arg == "flags":
                            flags = resolve_flags(kw.value)
                            break
                    if rel and pattern:
                        patterns.append((rel, pattern, flags))
            if isinstance(func, ast.Name) and func.id == "_inject_hazard":
                # _inject_hazard(root, body) or _inject_hazard(body) are both
                # supported.  The body is the source text that will be written
                # to the fixed hazard file path.
                if len(node.args) >= 2:
                    body = resolve_pattern(node.args[1])
                elif len(node.args) == 1:
                    body = resolve_pattern(node.args[0])
                else:
                    body = None
                if body:
                    patterns.append(("app/src/main/java/brutal_mutation/InjectedHazard.kt", re.escape(body), 0))
    return patterns


def count_matches(text: str | None, pattern: str, flags: int) -> int:
    if text is None:
        return 0
    try:
        return len(re.findall(pattern, text, flags))
    except re.error as exc:
        print(f"Invalid regex {pattern!r}: {exc}", file=sys.stderr)
        return -1


def scan_mutation(root: Path, mutators: dict, name: str) -> int:
    func = mutators.get(name)
    if func is None:
        print(f"Unknown mutation: {name}", file=sys.stderr)
        return 3

    try:
        source = inspect.getsource(func)
    except (OSError, TypeError) as exc:
        print(f"Cannot read source for {name}: {exc}", file=sys.stderr)
        return 3

    patterns = extract_patterns(source)
    if not patterns:
        # Swallow-style mutators that only introduce an empty catch cannot be
        # mechanically verified by pattern counts.  Report this clearly as
        # "cannot verify" rather than "killed" or "not applied".
        if name.startswith("catch_") or name.startswith("swallow_linkage") or name.startswith("swallow_verify"):
            return 2
        print(f"No patterns extracted for {name}; cannot verify", file=sys.stderr)
        return 2

    applied = False
    for rel, pattern, flags in patterns:
        work_path = root / rel
        work_text = work_path.read_text(encoding="utf-8") if work_path.exists() else ""
        baseline_text = git_show(root, rel)

        baseline_count = count_matches(baseline_text, pattern, flags)
        work_count = count_matches(work_text, pattern, flags)

        if baseline_count == -1 or work_count == -1:
            return 3

        if baseline_count != work_count:
            print(f"APPLIED: {name} in {rel} (baseline={baseline_count}, current={work_count})")
            applied = True

    return 0 if applied else 4


def _synthetic_inject_hazard_test(root: Path, mutators: dict, name: str) -> bool:
    """Run an _inject_hazard mutator in a synthetic repo and verify apply-check."""
    import os
    import shutil
    import tempfile

    tmp = Path(tempfile.mkdtemp(prefix="brutal-contract-selftest-"))
    try:
        (tmp / "app/src/main/java").mkdir(parents=True)
        (tmp / "app/src/main/java/Foo.java").write_text(
            "public class Foo {\n    public void foo() {}\n}\n", encoding="utf-8"
        )
        subprocess.run(["git", "init"], cwd=tmp, check=True, capture_output=True)
        subprocess.run(["git", "config", "user.email", "devin@local"], cwd=tmp, check=True, capture_output=True)
        subprocess.run(["git", "config", "user.name", "Devin"], cwd=tmp, check=True, capture_output=True)
        subprocess.run(["git", "add", "."], cwd=tmp, check=True, capture_output=True)
        subprocess.run(["git", "commit", "-m", "baseline"], cwd=tmp, check=True, capture_output=True)

        not_applied = scan_mutation(tmp, mutators, name)
        if not_applied != 4:
            print(f"Self-test: {name} expected MUTATION_NOT_APPLIED before apply, got {not_applied}")
            return False

        env = os.environ.copy()
        env["PYTHONPATH"] = str(root)
        run_result = subprocess.run(
            [sys.executable, "-c",
             f"import tools.brutal_a14_mutators as m; m.MUTATORS['{name}'](__import__('pathlib').Path({str(tmp)!r}), {{}})"],
            cwd=tmp,
            env=env,
            capture_output=True,
            text=True,
        )
        if run_result.returncode != 0:
            print(f"Self-test: failed to apply {name}: {run_result.stderr}", file=sys.stderr)
            return False

        applied = scan_mutation(tmp, mutators, name)
        if applied != 0:
            print(f"Self-test: {name} expected MUTATION_APPLIED after apply, got {applied}")
            return False
        return True
    except Exception as exc:
        print(f"Self-test: synthetic hazard test for {name} raised {exc!r}")
        return False
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def self_test(root: Path, mutators: dict) -> int:
    """Verify mutator patterns and synthetic injected-hazard behavior."""
    failed = False
    for name in sorted(mutators):
        func = mutators[name]
        try:
            source = inspect.getsource(func)
        except (OSError, TypeError):
            continue
        patterns = extract_patterns(source)
        if not patterns:
            print(f"No patterns for {name}")
            failed = True
            continue
        has_injected_hazard = False
        for rel, pattern, flags in patterns:
            baseline_text = git_show(root, rel)
            count = count_matches(baseline_text, pattern, flags)
            if count == -1:
                failed = True
                continue

            # _inject_hazard writes to a generated file that does not exist in
            # the committed baseline, so a baseline count of 0 is expected.  For
            # normal replace/remove mutators, the target pattern must already
            # exist in the baseline.
            if rel == "app/src/main/java/brutal_mutation/InjectedHazard.kt":
                has_injected_hazard = True
                try:
                    re.compile(pattern)
                except re.error as exc:
                    print(f"Invalid regex in {name}: {pattern!r}: {exc}")
                    failed = True
                continue

            if count == 0:
                print(f"Pattern does not match baseline: {name} in {rel}")
                print(f"  pattern: {pattern!r}")
                failed = True

        if has_injected_hazard:
            if not _synthetic_inject_hazard_test(root, mutators, name):
                failed = True

    return 1 if failed else 0


def load_mutators(root: Path) -> dict:
    spec = importlib.util.spec_from_file_location("brutal_a14_mutators", root / "tools" / "brutal_a14_mutators.py")
    if spec is None or spec.loader is None:
        raise RuntimeError("Cannot load brutal_a14_mutators.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return getattr(module, "MUTATORS", {})


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--mutation", help="mutator name to verify")
    p.add_argument("--self-test", action="store_true", help="verify mutator patterns match the baseline and injected hazards are detectable")
    args = p.parse_args()

    root = repo_root()
    try:
        mutators = load_mutators(root)
    except Exception as exc:
        print(f"Failed to load mutators: {exc}", file=sys.stderr)
        return 3

    if args.self_test:
        return self_test(root, mutators)
    if not args.mutation:
        print("No mutation specified", file=sys.stderr)
        return 3
    return scan_mutation(root, mutators, args.mutation)


if __name__ == "__main__":
    raise SystemExit(main())
