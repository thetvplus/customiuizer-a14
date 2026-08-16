#!/usr/bin/env python3
"""Mutation, hermeticity and determinism tests with truthful result semantics.

The runner never mutates the real worktree.  Every mutation is applied to a
detached temporary git worktree.  Results are classified as:

    MUTATION_NOT_APPLIED
    MUTATION_APPLIED
    INDEPENDENT_GATE_KILLED
    SELF_DETECTION_ONLY
    SURVIVED
    CANNOT_VERIFY
    GATE_ERROR
    CLEANUP_ERROR

A mutation is only an "independent semantic kill" when an independent kill gate
returns INDEPENDENT_GATE_KILLED.  Self-detection and cannot-verify results are
reported separately and never mixed into the kill rate.
"""
from __future__ import annotations

import argparse
import base64
import contextlib
import hashlib
import importlib.util
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from typing import Callable, Sequence

# Allow running as `python tools/brutal_test_runner.py` by putting the repo root
# on sys.path so `tools` is importable.
_HERE = Path(__file__).resolve().parent
if _HERE.name == "tools" and _HERE.parent not in map(Path, sys.path):
    sys.path.insert(0, str(_HERE.parent))

from tools import brutal_gate_protocol as protocol
from tools import brutal_test_policy as policy
from tools.brutal_gate_protocol import (
    CANNOT_VERIFY,
    CLEANUP_ERROR,
    GATE_ERROR,
    INDEPENDENT_GATE_KILLED,
    MUTATION_APPLIED,
    MUTATION_NOT_APPLIED,
    SELF_DETECTION_ONLY,
    SURVIVED,
)


def run(argv: Sequence[str], cwd: Path, timeout: int, env: dict[str, str] | None = None) -> tuple[int, str]:
    """Execute a command without a shell and return (exit_code, output)."""
    return protocol.run_command(argv, cwd, timeout, env)


def run_quiet(argv: Sequence[str], cwd: Path, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(
        list(argv),
        cwd=cwd,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=check,
    )


def repo_root() -> Path:
    result = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"],
        text=True,
        capture_output=True,
        check=True,
    )
    return Path(result.stdout.strip()).resolve()


def tracked_hashes(root: Path) -> dict[str, str]:
    result = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=root,
        stdout=subprocess.PIPE,
        check=True,
    )
    hashes: dict[str, str] = {}
    for raw in result.stdout.split(b"\0"):
        if not raw:
            continue
        rel = raw.decode("utf-8", errors="surrogateescape")
        path = root / rel
        if path.is_file():
            hashes[rel] = hashlib.sha256(path.read_bytes()).hexdigest()
        else:
            hashes[rel] = "<missing>"
    return hashes


def git_status_porcelain(root: Path) -> list[tuple[str, str]]:
    """Return parsed `git status --porcelain=v1 --untracked-files=all` entries."""
    result = subprocess.run(
        ["git", "status", "--porcelain=v1", "--untracked-files=all"],
        cwd=root,
        stdout=subprocess.PIPE,
        text=True,
        check=True,
    )
    entries: list[tuple[str, str]] = []
    for line in result.stdout.splitlines():
        if not line:
            continue
        status = line[:2]
        path = line[3:]
        entries.append((status, path))
    return entries


def active_worktrees(root: Path) -> list[Path]:
    result = subprocess.run(
        ["git", "worktree", "list", "--porcelain"],
        cwd=root,
        stdout=subprocess.PIPE,
        text=True,
        check=True,
    )
    worktrees: list[Path] = []
    for line in result.stdout.splitlines():
        if line.startswith("worktree "):
            worktrees.append(Path(line[len("worktree "):]).resolve())
    return worktrees


@contextlib.contextmanager
def detached_worktree(root: Path):
    temp_parent = Path(tempfile.mkdtemp(prefix="brutal-worktree-"))
    target = temp_parent / "repo"
    worktree_proc = subprocess.run(
        ["git", "worktree", "add", "--detach", "--force", str(target), "HEAD"],
        cwd=root,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    if worktree_proc.returncode != 0:
        raise RuntimeError(worktree_proc.stdout)
    try:
        yield target
    except BaseException as original_exc:
        # Preserve the original exception when we later raise CleanupError.
        original = original_exc
    else:
        original = None
    finally:
        cleanup_failures: list[str] = []

        # 1. Remove the git worktree.
        start = time.monotonic()
        removed = False
        while True:
            remove_proc = subprocess.run(
                ["git", "worktree", "remove", "--force", str(target)],
                cwd=root,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
            )
            if remove_proc.returncode == 0:
                removed = True
                break
            if time.monotonic() - start > 10:
                cleanup_failures.append(
                    f"git worktree remove failed: {remove_proc.stdout}"
                )
                break
            time.sleep(0.1)

        # 2. Remove the temp parent directory, failing closed.
        try:
            shutil.rmtree(temp_parent, ignore_errors=False)
        except Exception as exc:
            cleanup_failures.append(f"shutil.rmtree failed: {exc!r}")

        # 3. Verify no stray worktree entry remains.
        for active in active_worktrees(root):
            if active == target:
                cleanup_failures.append("worktree still listed after removal")
                break

        # 4. If anything went wrong, raise CleanupError.  Preserve the original
        # exception as __cause__ so callers can still see the gate result.
        if cleanup_failures:
            exc = CleanupError("; ".join(cleanup_failures))
            if original is not None:
                raise exc from original
            raise exc


# ---------------------------------------------------------------------------
# Built-in mutators for control-state, CI, catalog and hazard tests
# ---------------------------------------------------------------------------

def replace_first(path: Path, pattern: str, replacement: str, flags: int = 0) -> None:
    text = path.read_text(encoding="utf-8")
    changed, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f"mutation pattern not found in {path}: {pattern}")
    path.write_text(changed, encoding="utf-8")


def replace_all(path: Path, pattern: str, replacement: str, flags: int = 0) -> int:
    text = path.read_text(encoding="utf-8")
    changed, count = re.subn(pattern, replacement, text, flags=flags)
    if count == 0:
        raise RuntimeError(f"mutation pattern not found in {path}: {pattern}")
    path.write_text(changed, encoding="utf-8")
    return count


def append_after_line(path: Path, prefix: str, lines: list[str]) -> None:
    text = path.read_text(encoding="utf-8")
    out = []
    done = False
    for line in text.splitlines():
        out.append(line)
        if not done and line.startswith(prefix):
            out.extend(lines)
            done = True
    if not done:
        raise RuntimeError(f"line prefix not found: {prefix}")
    path.write_text("\n".join(out) + "\n", encoding="utf-8")


def mutation_source_file(root: Path, body: str) -> None:
    path = root / "app/src/main/java/brutal_mutation/InjectedHazard.kt"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(f"package brutal_mutation\n\n{body}\n", encoding="utf-8")


def workflow_path(root: Path, cfg: dict, full: bool = False) -> Path:
    paths = cfg["full_workflows"] if full else cfg["fast_workflows"]
    if not paths:
        raise RuntimeError("workflow path not configured")
    return root / paths[0]


def mutate_duplicate_smart(root: Path, cfg: dict) -> None:
    path = root / cfg["smart_state"]
    text = path.read_text(encoding="utf-8")
    m = re.search(r"(?m)^CurrentObjective:.*$", text)
    if not m:
        raise RuntimeError("CurrentObjective missing")
    path.write_text(text[:m.end()] + "\n" + m.group(0) + text[m.end():], encoding="utf-8")


def mutate_fake_ci_pass(root: Path, cfg: dict) -> None:
    path = root / cfg["smart_state"]
    replace_first(path, r"(?m)^LastCIState:.*$", "LastCIState: PASS")
    text = path.read_text(encoding="utf-8")
    if "LastCIRun:" not in text:
        append_after_line(
            path,
            "LastCIState:",
            [
                "LastCIRun: https://github.com/example/example/actions/runs/1",
                "LastCIJob: https://github.com/example/example/actions/runs/1/job/1",
                "LastCICommit: 0000000000000000000000000000000000000000",
            ],
        )


def mutate_symbolic_tree(root: Path, cfg: dict) -> None:
    replace_first(root / cfg["smart_state"], r"(?m)^LastVerifiedTree:.*$", "LastVerifiedTree: HEAD^{tree}")


def mutate_ambiguous_resume(root: Path, cfg: dict) -> None:
    replace_first(root / cfg["smart_state"], r"(?m)^ResumeTask:(.*)$", r"ResumeTask:\1 or skip to P99")


def mutate_task_drift(root: Path, cfg: dict) -> None:
    replace_first(root / cfg["task_state"], r"State:\s*`COMPLETE`", "State: `TODO`")


def mutate_progress_tamper(root: Path, cfg: dict) -> None:
    path = root / cfg["progress_json"]
    data = json.loads(path.read_text(encoding="utf-8"))
    data["__BRUTAL_MUTATION__"] = {"projectProgress": 999.0}
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def mutate_remove_fetch_depth(root: Path, cfg: dict) -> None:
    path = workflow_path(root, cfg)
    text = path.read_text(encoding="utf-8")
    changed, count = re.subn(r"(?m)^\s*fetch-depth\s*:\s*0\s*\n", "", text, count=1)
    if count != 1:
        raise RuntimeError("fetch-depth: 0 not found")
    path.write_text(changed, encoding="utf-8")


def mutate_wrong_branch(root: Path, cfg: dict) -> None:
    replace_first(workflow_path(root, cfg), re.escape(cfg["expected_branch"]), "main")


def mutate_signing_leak(root: Path, cfg: dict) -> None:
    path = workflow_path(root, cfg)
    with path.open("a", encoding="utf-8") as f:
        f.write(
            "\n# BRUTAL MUTATION\n"
            "      - name: forbidden signing\n"
            "        run: ./gradlew -PofficialRelease=true :app:assembleDevelop\n"
        )


def mutate_hardcoded_api37(root: Path, cfg: dict) -> None:
    path = workflow_path(root, cfg)
    with path.open("a", encoding="utf-8") as f:
        f.write(
            "\n# BRUTAL MUTATION\n"
            "      - name: brittle sdk\n"
            '        run: sdkmanager "platforms;android-37" "build-tools;37.0.0"\n'
        )


def mutate_windows_path(root: Path, cfg: dict) -> None:
    path = root / "tools" / "__brutal_windows_path_mutation.py"
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = base64.b64decode(
        "ZnJvbSBwYXRobGliIGltcG9ydCBQYXRoCnggPSBQYXRoKCJDOlxcdGVtcFxc"
        "cmVwbyIpCnkgPSAiYS9iIi5yZXBsYWNlKCIvIiwgIlxcIikK"
    ).decode("utf-8")
    path.write_text(payload, encoding="utf-8")


def mutate_duplicate_feature_id(root: Path, cfg: dict) -> None:
    path = root / cfg["catalog_file"]
    text = path.read_text(encoding="utf-8")
    ids = list(re.finditer(r'\bid\s*=\s*([A-Za-z0-9_]+)', text))
    unique = []
    for m in ids:
        if m.group(1) not in [u.group(1) for u in unique]:
            unique.append(m)
        if len(unique) == 2:
            break
    if len(unique) < 2:
        raise RuntimeError("two FeatureSpec ids not found")
    second = unique[1]
    changed = text[:second.start(1)] + unique[0].group(1) + text[second.end(1):]
    path.write_text(changed, encoding="utf-8")


def mutate_preference_default(root: Path, cfg: dict) -> None:
    path = root / cfg["catalog_file"]
    text = path.read_text(encoding="utf-8")
    m = re.search(r"getBoolean\s*\(([^,\n]+),\s*(false|true)\s*\)", text)
    if not m:
        raise RuntimeError("literal getBoolean default not found")
    flipped = "true" if m.group(2) == "false" else "false"
    changed = text[:m.start(2)] + flipped + text[m.end(2):]
    path.write_text(changed, encoding="utf-8")


def mutate_process_scope(root: Path, cfg: dict) -> None:
    path = root / cfg["catalog_file"]
    text = path.read_text(encoding="utf-8")
    m = re.search(r"\btarget\s*=\s*FeatureTarget\.([A-Z0-9_]+)", text)
    if not m:
        raise RuntimeError("FeatureTarget target not found")
    replacements = ["SYSTEM_UI", "LAUNCHER", "SYSTEM_PACKAGE", "SYSTEM_SERVER"]
    target = next((x for x in replacements if x != m.group(1)), "SYSTEM_UI")
    changed = text[:m.start(1)] + target + text[m.end(1):]
    path.write_text(changed, encoding="utf-8")


def mutate_install_phase(root: Path, cfg: dict) -> None:
    path = root / cfg["catalog_file"]
    text = path.read_text(encoding="utf-8")
    m = re.search(r"\bphase\s*=\s*InstallPhase\.([A-Z0-9_]+)", text)
    if not m:
        raise RuntimeError("InstallPhase phase not found")
    replacements = ["PACKAGE_READY", "APPLICATION_ATTACHED", "SYSTEM_SERVER_STARTING", "MODULE_LOADED"]
    target = next((x for x in replacements if x != m.group(1)), "PACKAGE_READY")
    changed = text[:m.start(1)] + target + text[m.end(1):]
    path.write_text(changed, encoding="utf-8")


def mutate_remove_dispatch(root: Path, cfg: dict) -> None:
    roots = [root / p for p in cfg.get("installer_roots", [])]
    target = None
    for path in roots:
        if path.exists() and any(path.iterdir()):
            target = next(path.rglob("*.kt"), None) or next(path.rglob("*.java"), None)
            if target:
                break
    if target is None:
        raise RuntimeError("no installer source file found")
    replace_first(target, r"FeatureInstallRegistry\.installAll\b", "FeatureInstallRegistry.installAllSkipped")


def mutate_fatal_swallow(root: Path, cfg: dict) -> None:
    mutation_source_file(
        root,
        'object InjectedHazard { fun run() { try { error("x") } catch (t: Throwable) { } } }',
    )


def mutate_static_context(root: Path, cfg: dict) -> None:
    mutation_source_file(
        root,
        "object InjectedHazard { @JvmField var context: android.content.Context? = null }",
    )


def mutate_eager_thread(root: Path, cfg: dict) -> None:
    mutation_source_file(
        root,
        'object InjectedHazard { val worker = android.os.HandlerThread("bad").apply { start() } }',
    )


# ---------------------------------------------------------------------------
# Mutator registry
# ---------------------------------------------------------------------------

def maybe_extra_mutators() -> dict[str, Callable[[Path, dict], None]]:
    try:
        spec = importlib.util.spec_from_file_location(
            "brutal_a14_mutators",
            Path(__file__).resolve().parent / "brutal_a14_mutators.py",
        )
        if spec is None or spec.loader is None:
            return {}
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return getattr(module, "MUTATORS", {})
    except Exception:
        return {}


MUTATORS: dict[str, Callable[[Path, dict], None]] = {
    "duplicate_smart_key": mutate_duplicate_smart,
    "fake_ci_pass": mutate_fake_ci_pass,
    "symbolic_verified_tree": mutate_symbolic_tree,
    "ambiguous_resume": mutate_ambiguous_resume,
    "task_state_drift": mutate_task_drift,
    "progress_json_tamper": mutate_progress_tamper,
    "remove_fetch_depth": mutate_remove_fetch_depth,
    "wrong_ci_branch": mutate_wrong_branch,
    "signing_leak": mutate_signing_leak,
    "hardcoded_api37": mutate_hardcoded_api37,
    "windows_path": mutate_windows_path,
    "duplicate_feature_id": mutate_duplicate_feature_id,
    "preference_default_flip": mutate_preference_default,
    "process_scope_flip": mutate_process_scope,
    "install_phase_flip": mutate_install_phase,
    "remove_installer_dispatch": mutate_remove_dispatch,
    "fatal_swallow": mutate_fatal_swallow,
    "static_context_leak": mutate_static_context,
    "eager_handler_thread": mutate_eager_thread,
}
MUTATORS.update(maybe_extra_mutators())


# ---------------------------------------------------------------------------
# Configuration validation
# ---------------------------------------------------------------------------

class ConfigError(Exception):
    pass


class CleanupError(Exception):
    """Raised when a detached worktree could not be removed after use."""
    pass


def _validate_command_list(value: list[str], context: str) -> None:
    if not isinstance(value, list) or not value:
        raise ConfigError(f"{context} must be a non-empty list")
    for token in value:
        if not isinstance(token, str) or not token:
            raise ConfigError(f"{context} must contain only non-empty strings")


def validate_config(cfg: dict) -> None:
    # Validate the brutal test config against the fixed policy.
    # All policy failures are collected and reported together so the command is
    # fail-closed: it cannot be bypassed by only fixing the first error.
    if not isinstance(cfg, dict):
        raise ConfigError("config must be a JSON object")

    errors: list[str] = []

    # 1. Schema and top-level invariants.
    if cfg.get("schema_version") != policy.EXPECTED_SCHEMA_VERSION:
        errors.append(f"schema_version must be {policy.EXPECTED_SCHEMA_VERSION}")

    for key in ("mutations", "hermetic_commands", "determinism_command", "determinism_outputs", "expected_branch"):
        if key not in cfg:
            errors.append(f"config missing required key: {key}")

    for key in ("coverage_target", "minimum_independent_kills", "required_independent_mutations", "coverage_ledger"):
        if key not in cfg:
            errors.append(f"config missing policy key: {key}")

    # 2. Policy targets.
    if cfg.get("coverage_target") != policy.REQUIRED_COVERAGE_TARGET:
        errors.append(f"coverage_target must be {policy.REQUIRED_COVERAGE_TARGET}")

    if cfg.get("minimum_independent_kills") != policy.REQUIRED_INDEPENDENT_KILLS:
        errors.append(f"minimum_independent_kills must be {policy.REQUIRED_INDEPENDENT_KILLS}")

    required = cfg.get("required_independent_mutations", [])
    if not isinstance(required, list) or set(required) != policy.REQUIRED_INDEPENDENT_MUTATIONS:
        missing = sorted(policy.REQUIRED_INDEPENDENT_MUTATIONS - set(required))
        extra = sorted(set(required) - policy.REQUIRED_INDEPENDENT_MUTATIONS)
        if missing:
            errors.append(f"required_independent_mutations missing: {missing}")
        if extra:
            errors.append(f"required_independent_mutations contains unknown: {extra}")

    # 3. Command lists.
    for cmd in cfg.get("hermetic_commands", []):
        try:
            _validate_command_list(cmd, "hermetic_commands entry")
        except ConfigError as exc:
            errors.append(str(exc))

    try:
        _validate_command_list(cfg.get("determinism_command", []), "determinism_command")
    except ConfigError as exc:
        errors.append(str(exc))

    determinism_outputs = cfg.get("determinism_outputs", [])
    if not isinstance(determinism_outputs, list) or not determinism_outputs:
        errors.append("determinism_outputs must be a non-empty list")
    elif len(determinism_outputs) != len(set(determinism_outputs)):
        errors.append("determinism_outputs must be unique")

    apply_checks = cfg.get("apply_checks", {})
    if not isinstance(apply_checks, dict):
        errors.append("apply_checks must be a dict")
    else:
        for name, cmd in apply_checks.items():
            try:
                _validate_command_list(cmd, f"apply_check {name!r}")
            except ConfigError as exc:
                errors.append(str(exc))

    kill_gates = cfg.get("kill_gates", {})
    if not isinstance(kill_gates, dict):
        errors.append("kill_gates must be a dict")
    else:
        for forbidden in policy.FORBIDDEN_KILL_GATES:
            if forbidden in kill_gates:
                errors.append(f"forbidden kill gate registered: {forbidden}")
        for name, cmd in kill_gates.items():
            try:
                _validate_command_list(cmd, f"kill_gate {name!r}")
            except ConfigError as exc:
                errors.append(str(exc))

    # 4. Mutations.
    mutations = cfg.get("mutations", [])
    if not isinstance(mutations, list) or not mutations:
        errors.append("mutations must be a non-empty list")

    mutation_names: set[str] = set()
    seen_ids: set[str] = set()
    for m in mutations:
        if not isinstance(m, dict):
            errors.append("every mutation must be an object")
            continue

        name = m.get("name")
        if not name or not isinstance(name, str):
            errors.append("every mutation must have a non-empty name")
            continue
        if name in seen_ids:
            errors.append(f"duplicate mutation id: {name!r}")
        seen_ids.add(name)
        mutation_names.add(name)

        mutator = m.get("mutator")
        if not mutator or not isinstance(mutator, str):
            errors.append(f"mutation {name!r} missing mutator")
        elif mutator not in MUTATORS:
            errors.append(f"mutation {name!r} references unknown mutator {mutator!r}")

        apply_check = m.get("apply_check")
        if apply_check is not None:
            if not isinstance(apply_check, str) or apply_check not in apply_checks:
                errors.append(f"mutation {name!r} references unknown apply_check {apply_check!r}")

        kill_gate = m.get("kill_gate")
        if kill_gate is not None:
            if not isinstance(kill_gate, str):
                errors.append(f"mutation {name!r} has non-string kill_gate")
            elif kill_gate in policy.FORBIDDEN_KILL_GATES:
                errors.append(f"mutation {name!r} uses forbidden kill_gate {kill_gate!r}")
            elif kill_gate not in kill_gates:
                errors.append(f"mutation {name!r} references unknown kill_gate {kill_gate!r}")

        coverage_status = m.get("coverage_status")
        if coverage_status not in policy.VALID_COVERAGE_STATUSES:
            errors.append(f"mutation {name!r} has invalid coverage_status {coverage_status!r}")

        required_independent = m.get("required_independent")
        if not isinstance(required_independent, bool):
            errors.append(f"mutation {name!r} missing required_independent boolean")
            continue

        if required_independent:
            if name not in policy.REQUIRED_INDEPENDENT_MUTATIONS:
                errors.append(f"mutation {name!r} is required_independent but not in policy set")
            if coverage_status != "ACTIVE_INDEPENDENT":
                errors.append(f"mutation {name!r} is required_independent but coverage_status is {coverage_status!r}")
            if not kill_gate:
                errors.append(f"mutation {name!r} is required_independent but has no kill_gate")
        else:
            if name in policy.REQUIRED_INDEPENDENT_MUTATIONS:
                errors.append(f"mutation {name!r} is in policy set but required_independent is false")

    for name in policy.REQUIRED_INDEPENDENT_MUTATIONS:
        if name not in mutation_names:
            errors.append(f"required independent mutation missing from mutations: {name!r}")

    # 5. Coverage ledger.
    ledger = cfg.get("coverage_ledger", [])
    if not isinstance(ledger, list):
        errors.append("coverage_ledger must be a list")
    else:
        if len(ledger) != policy.REQUIRED_COVERAGE_TARGET:
            errors.append(f"coverage_ledger has {len(ledger)} entries, expected {policy.REQUIRED_COVERAGE_TARGET}")

        ledger_by_id: dict[str, dict] = {}
        ledger_seen: set[str] = set()
        counts: dict[str, int] = {}
        for entry in ledger:
            if not isinstance(entry, dict):
                errors.append("coverage_ledger entries must be objects")
                continue
            lid = entry.get("id")
            if not lid or not isinstance(lid, str):
                errors.append("coverage_ledger entry missing non-empty id")
                continue
            if lid in ledger_seen:
                errors.append(f"duplicate ledger id: {lid!r}")
            ledger_seen.add(lid)
            ledger_by_id[lid] = entry

            status = entry.get("status")
            if status not in policy.VALID_COVERAGE_STATUSES:
                errors.append(f"coverage_ledger entry {lid!r} has invalid status {status!r}")
            else:
                counts[status] = counts.get(status, 0) + 1

            if lid not in mutation_names and lid not in policy.KNOWN_LEGACY_LEDGER_IDS:
                if not entry.get("reason") or not entry.get("requiredFollowUp"):
                    errors.append(f"coverage_ledger entry {lid!r} is not a mutation and has no explanation")

        active_count = counts.get("ACTIVE_INDEPENDENT", 0)
        if active_count != policy.REQUIRED_INDEPENDENT_KILLS:
            errors.append(f"coverage_ledger has {active_count} ACTIVE_INDEPENDENT, expected {policy.REQUIRED_INDEPENDENT_KILLS}")

        if sum(counts.get(s, 0) for s in policy.VALID_COVERAGE_STATUSES) != policy.REQUIRED_COVERAGE_TARGET:
            errors.append("coverage_ledger status counts do not sum to target")

        for name in policy.REQUIRED_INDEPENDENT_MUTATIONS:
            entry = ledger_by_id.get(name)
            if entry is None:
                errors.append(f"coverage_ledger missing required independent entry: {name!r}")
            elif entry.get("status") != "ACTIVE_INDEPENDENT":
                errors.append(f"coverage_ledger entry {name!r} must be ACTIVE_INDEPENDENT")

        for name in mutation_names:
            if name not in ledger_by_id:
                errors.append(f"mutation {name!r} is not in coverage_ledger")

        for lid in policy.KNOWN_LEGACY_LEDGER_IDS:
            entry = ledger_by_id.get(lid)
            if entry is None:
                errors.append(f"coverage_ledger missing legacy entry: {lid!r}")

    if errors:
        raise ConfigError("\n".join(errors))



def _is_allowed_untracked(rel: str, allowed_untracked: set[str]) -> bool:
    for prefix in allowed_untracked:
        if rel.startswith(prefix) or rel.startswith("." + prefix.rstrip("/")):
            return True
    return False


def collect_status_failures(
    root: Path,
    allowed_untracked: set[str],
    baseline_untracked: set[str] | None = None,
    ignore_tracked: bool = False,
) -> tuple[bool, list[str]]:
    """Check git status for tracked changes and new untracked files."""
    failures: list[str] = []
    for status, rel in git_status_porcelain(root):
        if status == "??":
            if _is_allowed_untracked(rel, allowed_untracked):
                continue
            if baseline_untracked is not None and rel in baseline_untracked:
                continue
            failures.append(f"untracked: {rel}")
        elif ignore_tracked:
            continue
        elif status.startswith("D"):
            failures.append(f"deleted: {rel}")
        elif status:
            failures.append(f"modified ({status.strip()}): {rel}")
    return not failures, failures


def allowed_untracked_patterns() -> set[str]:
    return {
        ".gradle/",
        "build/",
        "app/build/",
        "__pycache__/",
    }


# ---------------------------------------------------------------------------
# Hermeticity and determinism
# ---------------------------------------------------------------------------

def hermeticity(
    root: Path,
    cfg: dict,
    timeout: int,
    *,
    baseline_dirty_ok: bool = False,
) -> int:
    """Run configured hermetic commands and verify the tracked tree is unchanged.

    By default, any pre-existing tracked modification or staged change causes a
    fail-closed error. Unit tests may pass ``baseline_dirty_ok=True`` to exercise
    the warning path, but normal CLI and CI must never enable it.
    """
    allowed = allowed_untracked_patterns()
    baseline_status = git_status_porcelain(root)
    baseline_untracked = {rel for status, rel in baseline_status if status == "??" and not _is_allowed_untracked(rel, allowed)}

    _, pre_failures = collect_status_failures(root, allowed, baseline_untracked=baseline_untracked, ignore_tracked=True)
    if pre_failures:
        print("Hermeticity FAILED: existing untracked files in protected paths")
        for f in pre_failures[:30]:
            print(f"  {f}")
        if len(pre_failures) > 30:
            print(f"  ... and {len(pre_failures) - 30} more")
        return 1

    tracked_dirty = [(status, rel) for status, rel in baseline_status if status and status != "??"]
    if tracked_dirty:
        dirty_paths = [f"  {rel} ({status.strip()})" for status, rel in tracked_dirty]
        if baseline_dirty_ok:
            print("Hermeticity WARNING: pre-existing tracked modifications detected; these will not be blamed on the hermetic commands.")
            for line in dirty_paths:
                print(line)
        else:
            print("Hermeticity FAILED: pre-existing tracked modifications detected before hermetic commands")
            for line in dirty_paths:
                print(line)
            return 1

    before = tracked_hashes(root)
    for command in cfg["hermetic_commands"]:
        code, _ = run(command, root, timeout)
        if code:
            print(f"Hermeticity FAILED: command failed: {' '.join(command)!r}")
            return 1

    after = tracked_hashes(root)
    dirty = sorted(k for k in set(before) | set(after) if before.get(k) != after.get(k))
    if dirty:
        print("Hermeticity FAILED: tracked files changed by read-only tests")
        for d in dirty[:30]:
            print(f"  {d}")
        return 1

    _, post_failures = collect_status_failures(root, allowed, baseline_untracked=baseline_untracked, ignore_tracked=True)
    if post_failures:
        print("Hermeticity FAILED: new untracked files after tests")
        for f in post_failures[:30]:
            print(f"  {f}")
        return 1

    worktrees = active_worktrees(root)
    stale = [w for w in worktrees if "brutal-worktree-" in w.as_posix()]
    if stale:
        print("Hermeticity FAILED: stale brutal worktrees remain")
        for w in stale[:10]:
            print(f"  {w}")
        return 1

    print(f"Hermeticity passed: {len(cfg['hermetic_commands'])} command(s), working tree clean")
    return 0


VOLATILE_KEYS = {
    "generatedAt",
    "sourceCommit",
    "sourceTree",
    "verifiedTree",
    "verifiedMode",
    "ciState",
    "ciRun",
    "ciJob",
    "ciCommit",
    "auditTime",
    "timestamp",
    "builtAt",
    "builtBy",
}


def _normalize_json(text: str) -> str:
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return text
    if isinstance(data, dict):
        for key in list(data.keys()):
            if key in VOLATILE_KEYS:
                del data[key]
        return json.dumps(data, indent=2, sort_keys=True)
    return text


def _pascal_key(key: str) -> str:
    if not key:
        return key
    return key[0].upper() + key[1:]


def _normalize_text(text: str) -> str:
    for key in VOLATILE_KEYS:
        for form in {key, _pascal_key(key)}:
            text = re.sub(
                rf"(?m)^\s*(?:{re.escape(form)})\s*[:=]\s*.*$\n?",
                "",
                text,
            )
    text = re.sub(r"\n\n+", "\n", text)
    return text


def normalize_determinism_output(path: Path, text: str) -> str:
    """Remove known volatile fields while keeping the structural payload."""
    if path.suffix == ".json":
        return _normalize_json(text)
    return _normalize_text(text)


def determinism(root: Path, cfg: dict, timeout: int) -> int:
    command = cfg["determinism_command"]
    outputs = [Path(p) for p in cfg["determinism_outputs"]]
    snapshots: list[dict[str, str]] = []
    expected_output_paths = {p.as_posix() for p in outputs}

    with detached_worktree(root) as work:
        for env in (
            {"PYTHONHASHSEED": "1", "TZ": "UTC", "LC_ALL": "C"},
            {"PYTHONHASHSEED": "777", "TZ": "Asia/Tokyo", "LC_ALL": "C"},
        ):
            before = tracked_hashes(work)
            code, _ = run(command, work, timeout, env)
            if code:
                print(f"Determinism FAILED: command failed: {' '.join(command)!r}")
                return 1

            for rel in outputs:
                path = work / rel
                if not path.exists():
                    print(f"Determinism FAILED: output missing: {rel}")
                    return 1

            after_files = {p for p in work.rglob("*") if p.is_file() and not _is_build_artifact(p, work) and p.name != ".git"}
            after_rel = {p.relative_to(work).as_posix() for p in after_files}
            new_files = after_rel - set(before.keys())
            unexpected_new = new_files - expected_output_paths
            if unexpected_new:
                print(f"Determinism FAILED: unexpected new files: {sorted(unexpected_new)[:20]}")
                return 1

            after = tracked_hashes(work)
            changed = [rel for rel in set(before) if before[rel] != after.get(rel, "<missing>")]
            unexpected_changed = [rel for rel in changed if rel not in expected_output_paths]
            if unexpected_changed:
                print(f"Determinism FAILED: modified tracked files outside outputs: {unexpected_changed[:20]}")
                return 1

            current: dict[str, str] = {}
            for rel in outputs:
                path = work / rel
                text = path.read_text(encoding="utf-8")
                text = normalize_determinism_output(path, text)
                current[rel.as_posix()] = hashlib.sha256(text.encode()).hexdigest()
            snapshots.append(current)

    if snapshots[0] != snapshots[1]:
        print("Determinism FAILED: output differs between runs")
        print(json.dumps({"run1": snapshots[0], "run2": snapshots[1]}, indent=2))
        return 1

    print(f"Determinism passed for {len(outputs)} output(s)")
    return 0


def _is_build_artifact(path: Path, root: Path) -> bool:
    rel = path.relative_to(root).as_posix()
    for prefix in (".git/", ".gradle/", "build/", "app/build/", "__pycache__/"):
        if rel.startswith(prefix):
            return True
    return False


# ---------------------------------------------------------------------------
# Mutation testing with truthful semantics
# ---------------------------------------------------------------------------

def _select_mutations(cfg: dict, selected: set[str] | None) -> list[dict]:
    cases = []
    for case in cfg["mutations"]:
        if selected and case["name"] not in selected:
            continue
        cases.append(case)
    return cases


def _run_apply_check(
    work: Path,
    cfg: dict,
    case: dict,
    mutator_id: str,
) -> str:
    apply_name = case.get("apply_check")
    if not apply_name:
        # No dedicated apply check: we will fall back to tracked file hashes.
        return MUTATION_APPLIED

    apply_template = cfg["apply_checks"][apply_name]
    argv = protocol.render_command(apply_template, mutator=mutator_id)
    try:
        code, _ = run(argv, work, cfg.get("apply_check_timeout", 120))
    except subprocess.TimeoutExpired:
        return GATE_ERROR
    except Exception as exc:
        print(f"  apply check raised {exc!r}", file=sys.stderr)
        return GATE_ERROR

    if code == 0:
        return MUTATION_APPLIED
    if code == 4:
        return MUTATION_NOT_APPLIED
    if code == 2:
        return CANNOT_VERIFY
    return GATE_ERROR


def _run_kill_gate(
    work: Path,
    cfg: dict,
    case: dict,
    mutator_id: str,
) -> str:
    kill_name = case.get("kill_gate")
    if not kill_name:
        # No independent gate: a successful apply check only proves
        # self-detection, not a real product kill.
        return SELF_DETECTION_ONLY

    kill_template = cfg["kill_gates"][kill_name]
    argv = protocol.render_command(kill_template, name=case["name"], mutator=mutator_id)
    try:
        code, _ = run(argv, work, cfg.get("kill_gate_timeout", 300))
    except subprocess.TimeoutExpired:
        return GATE_ERROR
    except Exception as exc:
        print(f"  kill gate raised {exc!r}", file=sys.stderr)
        return GATE_ERROR
    return protocol.classify_kill(code)


def _mutator_modified_files(work: Path, allowed: set[str]) -> bool:
    """Return True if the worktree has any new or modified files after mutation."""
    for status, rel in git_status_porcelain(work):
        if not status:
            continue
        if status == "??" and _is_allowed_untracked(rel, allowed):
            continue
        return True
    return False


def mutation_test(
    root: Path,
    cfg: dict,
    timeout: int,
    selected: set[str] | None,
    ignore_minimum: bool = False,
) -> int:
    cases = _select_mutations(cfg, selected)
    if not cases:
        print("Mutation test FAILED: no mutations selected")
        return 1

    results: list[tuple[dict, str, str]] = []

    for case in cases:
        name = case["name"]
        mutator_id = case["mutator"]
        mutator = MUTATORS.get(mutator_id)
        if mutator is None:
            results.append((case, GATE_ERROR, f"unknown mutator {mutator_id!r}"))
            continue

        status = GATE_ERROR
        detail = ""
        try:
            with detached_worktree(root) as work:
                allowed = allowed_untracked_patterns()
                mutator(work, cfg)
                status = _run_apply_check(work, cfg, case, mutator_id)

                # When there is no dedicated apply check, the mutator must have
                # changed at least one tracked or untracked file, otherwise it
                # was not applied.  This catches mutators that inject untracked
                # hazards or CI-only files.
                if case.get("apply_check") is None and not _mutator_modified_files(work, allowed):
                    status = MUTATION_NOT_APPLIED
                    detail = "mutator did not modify any tracked or untracked file"

                # If the apply check confirms the mutation but there is no
                # independent kill gate, this is self-detection only.
                if status == MUTATION_APPLIED:
                    status = _run_kill_gate(work, cfg, case, mutator_id)

        except subprocess.TimeoutExpired as exc:
            status = GATE_ERROR
            detail = f"mutator timed out after {exc.timeout}s"
        except CleanupError as exc:
            cause = exc.__cause__ or exc.__context__
            if cause:
                detail = f"{detail}; original={cause!r}; cleanup failed: {exc}"
            else:
                detail = f"{detail}; cleanup failed: {exc}"
            status = CLEANUP_ERROR
        except Exception as exc:
            status = GATE_ERROR
            detail = repr(exc)

        results.append((case, status, detail))

    # Summary table
    print(f"\n{'Status':22} {'Required':9} {'Name'}")
    print("-" * 80)
    for case, status, detail in results:
        required = "yes" if case.get("required_independent") else "no"
        print(f"{status:22} {required:9} {case['name']}")
        if detail:
            for line in detail.splitlines():
                print(f"    {line}")

    counts: dict[str, int] = {}
    for _, status, _ in results:
        counts[status] = counts.get(status, 0) + 1

    configured = len(cases)
    applied = counts.get(MUTATION_APPLIED, 0) + counts.get(INDEPENDENT_GATE_KILLED, 0) + counts.get(SELF_DETECTION_ONLY, 0) + counts.get(SURVIVED, 0)
    independent_kills = counts.get(INDEPENDENT_GATE_KILLED, 0)
    self_detection = counts.get(SELF_DETECTION_ONLY, 0)
    survived = counts.get(SURVIVED, 0)
    cannot_verify = counts.get(CANNOT_VERIFY, 0)
    gate_errors = counts.get(GATE_ERROR, 0)
    not_applied = counts.get(MUTATION_NOT_APPLIED, 0)
    cleanup_errors = counts.get(CLEANUP_ERROR, 0)

    required_cases = [c for c in cases if c.get("required_independent")]
    required_names = {c["name"] for c in required_cases}
    required_killed = [c["name"] for c, s, _ in results if c["name"] in required_names and s == INDEPENDENT_GATE_KILLED]
    required_survived = [c["name"] for c, s, _ in results if c["name"] in required_names and s == SURVIVED]

    print("\nBrutal mutation summary")
    print(f"  Configured mutations:            {configured}")
    print(f"  Applied mutations:               {applied}")
    print(f"  Independent semantic kills:      {independent_kills}")
    print(f"  Self-detection only:             {self_detection}")
    print(f"  Survived:                        {survived}")
    print(f"  Cannot verify:                   {cannot_verify}")
    print(f"  Gate errors:                     {gate_errors}")
    print(f"  Not applied:                     {not_applied}")
    print(f"  Cleanup errors:                  {cleanup_errors}")
    print(f"  Independent semantic kill rate:  {independent_kills}/{len(required_cases) if required_cases else configured}")

    failed = False

    if not ignore_minimum and cfg.get("minimum_independent_kills") is not None:
        minimum = cfg["minimum_independent_kills"]
        if independent_kills < minimum:
            print(f"\nFAILED: independent kill count {independent_kills} below minimum {minimum}")
            failed = True

    if required_survived:
        print(f"\nFAILED: required independent mutation(s) survived: {required_survived}")
        failed = True

    if not_applied:
        print(f"\nFAILED: {not_applied} mutation(s) did not apply")
        failed = True

    if cannot_verify:
        print(f"\nFAILED: {cannot_verify} mutation(s) could not be verified")
        failed = True

    if gate_errors:
        print(f"\nFAILED: {gate_errors} gate/mutator error(s)")
        failed = True

    if cleanup_errors:
        print(f"\nFAILED: {cleanup_errors} cleanup error(s)")
        failed = True

    if not failed:
        if required_cases and independent_kills == len(required_cases):
            print("\nAll required independent mutations killed.")
        else:
            print("\nRun completed without required independent failures.")
        return 0

    print("\nSURVIVED_MUTATION means the independent gate did not detect an injected defect.")
    return 1


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main(argv=None) -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--config", required=True)
    p.add_argument("--timeout", type=int, default=900)
    sub = p.add_subparsers(dest="command", required=True)
    sub.add_parser("validate")
    sub.add_parser("hermeticity")
    sub.add_parser("determinism")
    m = sub.add_parser("mutate")
    m.add_argument("--case", action="append")
    m.add_argument("--ignore-minimum", action="store_true", help="do not enforce minimum_independent_kills when running a subset")
    sub.add_parser("all")
    args = p.parse_args(argv)

    root = repo_root()
    cfg = json.loads((root / args.config).read_text(encoding="utf-8"))

    if args.command == "validate":
        try:
            validate_config(cfg)
        except ConfigError as exc:
            print("Config validation FAILED:", file=sys.stderr)
            print(str(exc), file=sys.stderr)
            return 2
        print("Config validation passed")
        print(f"  coverage target: {cfg['coverage_target']}")
        print(f"  required independent kills: {cfg['minimum_independent_kills']}")
        print(f"  executable mutations: {len(cfg['mutations'])}")
        print(f"  coverage ledger: {len(cfg.get('coverage_ledger', []))}")
        return 0

    try:
        validate_config(cfg)
    except ConfigError as exc:
        print(f"Config error: {exc}", file=sys.stderr)
        return 2

    if args.command == "hermeticity":
        return hermeticity(root, cfg, args.timeout)
    if args.command == "determinism":
        return determinism(root, cfg, args.timeout)
    if args.command == "mutate":
        selected = set(args.case or [])
        return mutation_test(root, cfg, args.timeout, selected or None, ignore_minimum=args.ignore_minimum)

    code = hermeticity(root, cfg, args.timeout)
    if code:
        return code
    code = determinism(root, cfg, args.timeout)
    if code:
        return code
    return mutation_test(root, cfg, args.timeout, None)


if __name__ == "__main__":
    raise SystemExit(main())
