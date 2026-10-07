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


def key_pattern(key: str) -> str:
    return rf"(?:{re.escape(key)}|'{re.escape(key)}'|\"{re.escape(key)}\")"


def yaml_mapping_lines(text: str) -> list[tuple[int, int, str]]:
    """Read structural lines, excluding comments and block scalar contents."""
    lines = []
    offset = 0
    scalar_indent = None
    for line in text.splitlines(keepends=True):
        stripped = line.lstrip(" \t")
        indent = len(line) - len(stripped)
        if stripped.strip() and not stripped.startswith("#"):
            if scalar_indent is not None and indent <= scalar_indent:
                scalar_indent = None
            if scalar_indent is None:
                lines.append((offset, indent, stripped.rstrip("\r\n")))
                if re.search(r":[ \t]*[|>][1-9+-]*[ \t]*(?:#.*)?$", stripped):
                    scalar_indent = indent + (2 if stripped.startswith("- ") else 0)
        offset += len(line)
    return lines


def workflow_steps(text: str) -> list[tuple[int, str]]:
    """Read every mapping in each steps sequence, regardless of first key."""
    lines = yaml_mapping_lines(text)
    blocks = []
    for index, (_, parent_indent, line) in enumerate(lines):
        if re.match(r"(?:- )?<<[ \t]*:", line) or re.match(r"(?:- )?\"[^\"]*\\[^\"]*\"[ \t]*:", line):
            raise ValueError("workflow mapping keys must not use merges or escaped names")
        if re.match(rf"{key_pattern('jobs')}[ \t]*:", line) and not re.fullmatch(
            rf"{key_pattern('jobs')}[ \t]*:[ \t]*(?:#.*)?", line
        ):
            raise ValueError("jobs must use a block mapping")
        if not re.match(rf"{key_pattern('steps')}[ \t]*:", line):
            continue
        if not re.fullmatch(rf"{key_pattern('steps')}[ \t]*:[ \t]*(?:#.*)?", line):
            raise ValueError("steps must use a block sequence")
        start = None
        step_indent = None
        end = len(text)
        for offset, indent, child in lines[index + 1:]:
            if indent < parent_indent or (step_indent is not None and indent <= step_indent
                                          and not child.startswith("- ")):
                end = offset
                break
            if step_indent is None:
                step_indent = indent
            if indent == step_indent:
                if not re.match(r"- (?:[\w-]+|'[\w-]+'|\"[\w-]+\")[ \t]*:", child):
                    raise ValueError("each step must use a block mapping")
                if start is not None:
                    blocks.append((start, text[start:offset]))
                start = offset
        if start is not None:
            blocks.append((start, text[start:end]))
    return blocks


def step_action(step: str) -> re.Match[str] | None:
    indent = len(step) - len(step.lstrip(" \t"))
    return re.search(rf"(?m)^(?:[ \t]{{{indent}}}- |[ \t]{{{indent + 2}}}){key_pattern('uses')}[ \t]*:[ \t]*(\S+)", step)


def workflow_job_actions(text: str) -> list[tuple[int, str]]:
    """External reusable workflows are called by jobs.<id>.uses, outside steps."""
    actions = []
    parent_indent = job_indent = property_indent = None
    for offset, indent, line in yaml_mapping_lines(text):
        if re.fullmatch(rf"{key_pattern('jobs')}[ \t]*:[ \t]*(?:#.*)?", line):
            parent_indent, job_indent, property_indent = indent, None, None
            continue
        if parent_indent is None:
            continue
        if indent <= parent_indent:
            parent_indent = None
            continue
        if job_indent is None:
            job_indent = indent
        if indent == job_indent:
            if not re.fullmatch(r"(?:[\w-]+|'[\w-]+'|\"[\w-]+\")[ \t]*:[ \t]*(?:#.*)?", line):
                raise ValueError("each job must use a block mapping")
            property_indent = None
            continue
        if property_indent is None:
            property_indent = indent
        if indent == property_indent:
            match = re.match(rf"{key_pattern('uses')}[ \t]*:[ \t]*(\S+)", line)
            if match is not None:
                actions.append((offset, match.group(1)))
    return actions


def mapping_body(text: str, key: str, required_indentation: int | None = None) -> str | None:
    """Read one block mapping up to the next sibling, never across triggers."""
    matches = re.finditer(rf"(?m)^([ \t]*)(- )?{key_pattern(key)}[ \t]*:[ \t]*(?:#.*)?$", text)
    start = next((match for match in matches if required_indentation is None
                  or len(match.group(1)) + (2 if match.group(2) else 0) == required_indentation), None)
    if start is None:
        return None
    indentation = len(start.group(1)) + (2 if start.group(2) else 0)
    lines = []
    for line in text[start.end():].splitlines(keepends=True):
        stripped = line.lstrip()
        if stripped.strip() and not stripped.startswith("#"):
            if len(line) - len(stripped) <= indentation:
                break
        lines.append(line)
    return "".join(lines)


def action_input_is_true(step: str, key: str) -> bool:
    """Require one literal true input in the action's direct with mapping."""
    step_indentation = len(step) - len(step.lstrip(" \t"))
    body = mapping_body(step, "with", step_indentation + 2)
    if body is None:
        return False
    indents = [len(line) - len(line.lstrip(" \t")) for line in body.splitlines()
               if line.strip() and not line.lstrip(" \t").startswith("#")]
    if not indents:
        return False
    values = re.findall(rf"(?m)^[ \t]{{{min(indents)}}}{key_pattern(key)}[ \t]*:[ \t]*([^\n]*)", body)
    return len(values) == 1 and re.fullmatch(r"true[ \t]*(?:#.*)?", values[0]) is not None


def scan_workflow(path: Path, expected_branch: str, default_branch: str) -> list[str]:
    text = path.read_text(encoding="utf-8")
    errors: list[str] = []
    rel = path.as_posix()

    def add(rule: str, message: str, offset: int = 0) -> None:
        errors.append(f"{rel}:{line_of(text, offset)}: {rule}: {message}")

    if "actions/checkout@" in text and not re.search(r"fetch-depth\s*:\s*0\b", text):
        add("CI_FULL_HISTORY", "checkout must use fetch-depth: 0")
    if "actions/checkout@" in text:
        if re.search(r"persist-credentials\s*:\s*true\b", text):
            add(
                "CI_CHECKOUT_CREDENTIALS",
                "checkout must set persist-credentials: false; later steps do not need authenticated git",
            )
        elif not re.search(r"persist-credentials\s*:\s*false\b", text):
            add(
                "CI_CHECKOUT_CREDENTIALS",
                "checkout must set persist-credentials: false; later steps do not need authenticated git",
            )
    if re.search(r"chmod\s+\+x\s+\./gradlew\b", text):
        add(
            "CI_GRADLEW_MODE",
            "do not chmod ./gradlew in CI; git tracks the wrapper as 100755",
        )
    try:
        steps = workflow_steps(text)
        actions = workflow_job_actions(text)
    except ValueError as error:
        add("CI_STEP_FORMAT", str(error))
        steps = []
        actions = []

    for offset, step in steps:
        match = step_action(step)
        if match is not None:
            actions.append((offset + match.start(), match.group(1)))
    for offset, spec in actions:
        if spec.startswith("./") or spec.startswith(".\\"):
            continue
        ref = spec.rsplit("@", 1)[-1] if "@" in spec else ""
        if not re.fullmatch(r"[0-9a-f]{40}", ref):
            add(
                "CI_ACTION_PIN",
                f"action must be pinned to a 40-char commit SHA, not {spec}",
                offset,
            )

    for offset, step in steps:
        action = step_action(step)
        is_setup_java = action is not None and action.group(1).lower().startswith("actions/setup-java@")
        if is_setup_java and not action_input_is_true(step, "verify-signature"):
            add("CI_JDK_SIGNATURE", "setup-java must explicitly require signature verification", offset)
        if is_setup_java and not action_input_is_true(step, "force-download"):
            add("CI_JDK_DOWNLOAD", "setup-java must download the JDK so its signature is actually verified", offset)
        if "name: develop-apk-and-mapping" in step and not re.search(
            r"(?m)^\s+if-no-files-found:\s*error\s*$", step
        ):
            add("CI_REQUIRED_ARTIFACT", "missing develop artifacts must fail the upload", offset)

    if path.name == "a14-full-ci.yml":
        builds = list(re.finditer(r"(?m)^\s*\./gradlew[^\n]*\bclean\b[^\n]*:app:assembleDevelop[^\n]*$", text))
        if len(builds) != 2:
            add("CI_REPRO_BUILDS", "Full CI requires two separate clean develop builds")
        for build in builds:
            arguments = build.group().split()
            isolated_options = {
                "--no-daemon", "--no-build-cache", "--no-configuration-cache",
                "-Pkotlin.compiler.execution.strategy=in-process", "-Pkotlin.incremental=false",
            }
            if not isolated_options.issubset(arguments):
                add("CI_REPRO_CACHE", "both reproducibility builds must bypass compiled caches and shared processes", build.start())
        if not re.search(r"(?m)^\s*cmp[^\n]*first-mapping[.]txt[^\n]*mapping[.]txt", text):
            add("CI_REPRO_MAPPING", "compare R8 mappings as well as APK contents")

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
    if hardcoded_37:
        add(
            "CI_API37_RESOLUTION",
            "unversioned platforms;android-37 is not a stable pin; use tools/ci_install_android_sdk.sh",
            hardcoded_37.start(),
        )
    if re.search(r"android-37\*|android-CinnamonBun", text) or (
        re.search(r"sdkmanager\s+--list", text) and re.search(r"sort\s+-V", text)
    ):
        add(
            "CI_SDK_NONDETERMINISTIC",
            "do not glob or sort API 37 packages; pin exact stable packages",
        )
    if "android-actions/setup-android" in text and "tools/ci_install_android_sdk.sh" not in text:
        add(
            "CI_SDK_PIN",
            "setup-android workflows must install compile SDK via tools/ci_install_android_sdk.sh",
        )

    if "actions/checkout@" in text:
        if not re.search(r"permissions\s*:\s*\n(?:[ \t]+[^\n]+\n)*?[ \t]+contents\s*:\s*read\b", text):
            add("CI_PERMISSIONS", "set permissions.contents: read")

    push_body = mapping_body(text, "push")
    if push_body is not None:
        branch_text = mapping_body(push_body, "branches") or ""
        branches = re.findall(r"^\s*-\s*['\"]?([A-Za-z0-9_./-]+)['\"]?\s*$", branch_text, re.M)
        if expected_branch not in branches:
            add("CI_EXACT_BRANCH", f"push must include exact branch {expected_branch!r}")
        unexpected = [b for b in branches if b != expected_branch]
        if unexpected:
            add("CI_EXACT_BRANCH", f"unexpected push branches: {unexpected}")

    has_schedule = bool(re.search(r"(?m)^\s*schedule\s*:", text))
    has_dispatch = bool(re.search(r"(?m)^\s*workflow_dispatch\s*:", text))
    has_push = push_body is not None
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


def scan_android_sdk_script(repo_root: Path) -> list[str]:
    path = repo_root / "tools" / "ci_install_android_sdk.sh"
    rel = "tools/ci_install_android_sdk.sh"
    if not path.is_file():
        return [f"{rel}:1: CI_SDK_PIN: missing Android SDK install script"]
    text = path.read_text(encoding="utf-8")
    errors: list[str] = []
    if re.search(r'PLATFORM_PACKAGE="[^"]*(?:beta|preview|rc[0-9])[^"]*"', text, re.I) or re.search(
        r'BUILD_TOOLS_PACKAGE="[^"]*(?:beta|preview|rc[0-9])[^"]*"',
        text,
        re.I,
    ):
        errors.append(f"{rel}:1: CI_SDK_STABLE: pinned SDK packages must not include beta/rc/preview")
    if not re.search(r'PLATFORM_PACKAGE="platforms;android-37\.\d+"', text):
        errors.append(f"{rel}:1: CI_SDK_PIN: PLATFORM_PACKAGE must be a stable platforms;android-37.N pin")
    if not re.search(r'BUILD_TOOLS_PACKAGE="build-tools;\d+\.\d+\.\d+"', text):
        errors.append(f"{rel}:1: CI_SDK_PIN: BUILD_TOOLS_PACKAGE must be an exact stable build-tools pin")
    if re.search(r"\bfind\b", text) or re.search(r"sort\s+-V", text) or "head -n 1" in text:
        errors.append(f"{rel}:1: CI_SDK_NONDETERMINISTIC: verify the pinned package directories, do not glob")
    if "android.jar" not in text or "/aapt" not in text:
        errors.append(f"{rel}:1: CI_SDK_PIN: must verify android.jar and aapt for the pinned packages")
    return errors


def scan_compile_sdk_contract(repo_root: Path) -> list[str]:
    gradle_path = repo_root / "app" / "build.gradle.kts"
    script_path = repo_root / "tools" / "ci_install_android_sdk.sh"
    errors: list[str] = []
    if not gradle_path.is_file():
        return ["app/build.gradle.kts:1: CI_SDK_COMPILE_MAJOR: missing app/build.gradle.kts"]
    if not script_path.is_file():
        return ["tools/ci_install_android_sdk.sh:1: CI_SDK_COMPILE_MAJOR: missing Android SDK install script"]

    gradle = gradle_path.read_text(encoding="utf-8")
    script = script_path.read_text(encoding="utf-8")
    compile = re.search(r"(?m)^\s*compileSdk\s*=\s*(\d+)\s*$", gradle)
    platform = re.search(r'(?m)^\s*PLATFORM_PACKAGE="platforms;android-(\d+)(?:\.(\d+))?"\s*$', script)
    if compile is None:
        errors.append("app/build.gradle.kts:1: CI_SDK_COMPILE_MAJOR: compileSdk major is missing")
        return errors
    if platform is None:
        errors.append(
            "tools/ci_install_android_sdk.sh:1: CI_SDK_COMPILE_MAJOR: PLATFORM_PACKAGE must be platforms;android-N or android-N.M"
        )
        return errors
    if compile.group(1) != platform.group(1):
        errors.append(
            "app/build.gradle.kts:1: CI_SDK_COMPILE_MAJOR: "
            f"compileSdk {compile.group(1)} does not match pinned platform major {platform.group(1)}"
        )
    elif (platform.group(2) or "0") != "0":
        errors.append(
            "app/build.gradle.kts:1: CI_SDK_COMPILE_MINOR: "
            f"integer compileSdk {compile.group(1)} selects android-{compile.group(1)}.0, "
            f"but CI installs android-{platform.group(1)}.{platform.group(2)}"
        )
    selected_tools = re.search(r'(?m)^\s*buildToolsVersion\s*=\s*"(\d+\.\d+\.\d+)"\s*$', gradle)
    installed_tools = re.search(r'(?m)^\s*BUILD_TOOLS_PACKAGE="build-tools;(\d+\.\d+\.\d+)"\s*$', script)
    if selected_tools is None or installed_tools is None:
        errors.append("app/build.gradle.kts:1: CI_SDK_BUILD_TOOLS: both Gradle and CI must explicitly pin SDK build tools")
    elif selected_tools.group(1) != installed_tools.group(1):
        errors.append(
            "app/build.gradle.kts:1: CI_SDK_BUILD_TOOLS: "
            f"Gradle selects {selected_tools.group(1)} but CI installs {installed_tools.group(1)}"
        )
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
    repo_root = Path(args.repo_root).resolve()
    errors.extend(scan_repo_scripts(repo_root))
    errors.extend(scan_android_sdk_script(repo_root))
    errors.extend(scan_compile_sdk_contract(repo_root))

    if errors:
        print("CI contract violations:")
        for error in errors:
            print(f"  {error}")
        return 1
    print(f"CI contract scan passed: {len(paths)} workflow(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
