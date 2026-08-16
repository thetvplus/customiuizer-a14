#!/usr/bin/env python3
"""Aggressive text-level CI contract checker for GitHub Actions workflows.

Standard-library only. It intentionally rejects configurations that are
technically valid but unsafe, inert on a non-default branch, non-hermetic,
signing-aware, or dependent on a package name that is not resolved at runtime.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


def line_of(text: str, offset: int) -> int:
    return text[:offset].count("\n") + 1


def scan_workflow(path: Path, expected_branch: str, default_branch: str) -> list[str]:
    text = path.read_text(encoding="utf-8")
    errors: list[str] = []
    rel = path.as_posix()

    def add(rule: str, message: str, offset: int = 0) -> None:
        errors.append(f"{rel}:{line_of(text, offset)}: {rule}: {message}")

    if "actions/checkout@" in text and not re.search(r"fetch-depth\s*:\s*0\b", text):
        add("CI_FULL_HISTORY", "checkout must use fetch-depth: 0")

    if re.search(r"\bpw[s]h\b|\bpowe[r]shell\b", text, re.I):
        add("CI_LINUX_SHELL", "Ubuntu workflow invokes PowerShell")
    if re.search(r"\bgradlew[.]bat\b", text, re.I):
        add("CI_LINUX_GRADLE", "Ubuntu workflow invokes gradlew[.]bat")
    if re.search(r"(?:-P)?officialRelease\s*=\s*true\b", text):
        add("CI_SIGNING", "officialRelease=true is forbidden in CI")
    if re.search(
        r"customiuizerA1[34]KeystoreProperties|CUSTOMIUIZER_A1[34]_KEYSTORE_PROPERTIES|"
        r"secrets\.[A-Z0-9_]*(?:KEYSTORE|SIGN|PASSWORD|KEY)",
        text,
        re.I,
    ):
        add("CI_SIGNING", "workflow references signing property or secret")

    if re.search(r"yes\s*\|\s*sdkmanager\s+--licenses", text):
        add("CI_SDK_LICENSE", "setup action already accepts licenses; duplicate yes pipe is brittle")
    if re.search(r"sdkmanager[^\n]*(?:\|\|\s*true|;\s*true)", text):
        add("CI_SDK_MASKED_FAILURE", "sdkmanager failure must not be masked")
    if re.search(r'packages\s*:\s*(?:["\']?)tools(?:["\']?)\s*$', text, re.M):
        add("CI_SDK_LEGACY_TOOLS", "legacy tools package pulls obsolete emulator/tooling")

    hardcoded_37 = re.search(r'sdkmanager[^\n]*["\']platforms;android-37["\']', text)
    has_resolver = (
        "android-CinnamonBun" in text
        and re.search(r"sdkmanager\s+--list", text)
        and re.search(r"PLATFORM_PACKAGE|SDK_PLATFORM_PACKAGE", text)
    )
    if hardcoded_37 and not has_resolver:
        add(
            "CI_API37_RESOLUTION",
            "API 37 must be discovered with android-37/CinnamonBun fallback",
            hardcoded_37.start(),
        )

    if "actions/checkout@" in text:
        if not re.search(r"permissions\s*:\s*\n(?:[ \t]+[^\n]+\n)*?[ \t]+contents\s*:\s*read\b", text):
            add("CI_PERMISSIONS", "set permissions.contents: read")

    push_block = re.search(
        r"(?ms)^\s*push\s*:\s*\n(?P<body>(?:^[ \t]+.*\n?)*)",
        text,
    )
    if push_block:
        body = push_block.group("body")
        branches = re.findall(r"^\s*-\s*([A-Za-z0-9_./-]+)\s*$", body, re.M)
        if expected_branch not in branches:
            add("CI_EXACT_BRANCH", f"push must include exact branch {expected_branch!r}")
        unexpected = [b for b in branches if b != expected_branch]
        if unexpected:
            add("CI_EXACT_BRANCH", f"unexpected push branches: {unexpected}")

    has_schedule = bool(re.search(r"(?m)^\s*schedule\s*:", text))
    has_dispatch = bool(re.search(r"(?m)^\s*workflow_dispatch\s*:", text))
    has_push = bool(push_block)
    if path.name.lower().find("full") >= 0 and expected_branch != default_branch:
        if (has_schedule or has_dispatch) and not has_push:
            add(
                "CI_INERT_NONDEFAULT",
                "schedule/workflow_dispatch workflow only on a non-default branch is not an executable current-branch gate",
            )

    if has_schedule:
        # Capture either a folded block or a single-line `if:` expression.
        if_match = re.search(
            r"(?m)^(?P<indent>\s*)if\s*:\s*(?P<fold>[>\-]+)?\s*\n(?P<body>(?:^(?P=indent)\s+.*\n?)+)",
            text,
        )
        if if_match is None:
            single_line = re.search(r"(?m)^\s*if\s*:\s*(.+)$", text)
            if single_line is None:
                add("CI_SCHEDULE_CONDITION", "schedule trigger must have an explicit job-level `if` condition")
            else:
                if_body = single_line.group(1)
                if "github.event_name" not in if_body or "'schedule'" not in if_body:
                    add(
                        "CI_SCHEDULE_CONDITION",
                        "schedule trigger must be explicitly handled in the job `if` (github.event_name == 'schedule')",
                    )
        else:
            if_body = if_match.group("body")
            if "github.event_name" not in if_body or "'schedule'" not in if_body:
                add(
                    "CI_SCHEDULE_CONDITION",
                    "schedule trigger must be explicitly handled in the job `if` (github.event_name == 'schedule')",
                )

    if not re.search(r"timeout-minutes\s*:\s*\d+", text):
        add("CI_TIMEOUT", "job must have timeout-minutes")
    if not re.search(r"concurrency\s*:", text):
        add("CI_CONCURRENCY", "workflow must define concurrency")

    if re.search(r"\b(?:gh\s+release|softprops/action-gh-release|create-release)\b", text, re.I):
        add("CI_RELEASE", "workflow must not create a public release")

    return errors


def scan_repo_scripts(repo_root: Path) -> list[str]:
    errors: list[str] = []
    path_replace = re.compile(r'\.replace\s*\(\s*["\']/["\']\s*,\s*["\']\\\\?["\']\s*\)')
    drive = re.compile(r'(?<![A-Za-z0-9_])[A-Za-z]:[\\/]')
    for base_name in ("tools", "scripts"):
        base = repo_root / base_name
        if not base.exists():
            continue
        for path in sorted([*base.rglob("*.py"), *base.rglob("*.sh")]):
            if path.name in {
                "ci_contract_scan.py",
                "test_brutal_tools.py",
                "check_ci_portability.py",
                "test_check_ci_portability.py",
            }:
                continue
            content = path.read_text(encoding="utf-8", errors="replace")
            rel = path.relative_to(repo_root).as_posix()
            for rule, pattern in (("CI_WINDOWS_PATH_REPLACE", path_replace), ("CI_HARDCODED_DRIVE", drive)):
                for match in pattern.finditer(content):
                    errors.append(
                        f"{rel}:{line_of(content, match.start())}: {rule}: Windows-only path construction"
                    )
    return errors


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--root", default=".github/workflows")
    p.add_argument("--repo-root", default=".")
    p.add_argument("--expected-branch", required=True)
    p.add_argument("--default-branch", default="main")
    args = p.parse_args(argv)

    root = Path(args.root)
    paths = sorted([*root.glob("*.yml"), *root.glob("*.yaml")]) if root.exists() else []
    if not paths:
        print(f"CI contract scan: no workflows found under {root}", file=sys.stderr)
        return 1

    errors: list[str] = []
    for path in paths:
        errors.extend(scan_workflow(path, args.expected_branch, args.default_branch))
    errors.extend(scan_repo_scripts(Path(args.repo_root).resolve()))

    if errors:
        print("CI contract violations:")
        for error in errors:
            print(f"  {error}")
        return 1
    print(f"CI contract scan passed: {len(paths)} workflow(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
