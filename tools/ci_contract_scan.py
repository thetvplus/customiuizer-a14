#!/usr/bin/env python3
"""Check actionable CI security and build contracts using parsed YAML.

PyYAML BaseLoader resolves YAML syntax and aliases as data, without constructing
Python objects or treating the GitHub key 'on' as a YAML 1.1 boolean.
"""
from __future__ import annotations

import argparse
import posixpath
import re
import sys
from pathlib import Path

import yaml


class WorkflowLoader(yaml.BaseLoader):
    def construct_mapping(self, node, deep=False):
        mapping = {}
        for key_node, value_node in node.value:
            key = self.construct_object(key_node, deep=deep)
            if not isinstance(key, str) or key == "<<" or key in mapping:
                raise ValueError("workflow contains an invalid, merged or duplicate mapping key")
            mapping[key] = self.construct_object(value_node, deep=deep)
        return mapping


def load_workflow(text: str) -> dict:
    workflow = yaml.load(text, Loader=WorkflowLoader)
    if not isinstance(workflow, dict) or not isinstance(workflow.get("jobs"), dict):
        raise ValueError("workflow must contain a jobs mapping")
    for job in workflow["jobs"].values():
        if not isinstance(job, dict):
            raise ValueError("job must be a mapping")
        steps = job.get("steps", [])
        if not isinstance(steps, list) or any(not isinstance(step, dict) for step in steps):
            raise ValueError("steps must contain mappings")
    return workflow


def line_of(text: str, offset: int) -> int:
    return text[:offset].count("\n") + 1


def action_input(step: dict, key: str) -> str | None:
    inputs = step.get("with", {})
    if not isinstance(inputs, dict) or any(not name.isascii() for name in inputs):
        return None
    values = [value for name, value in inputs.items() if name.lower() == key]
    return values[0].strip() if len(values) == 1 and isinstance(values[0], str) else None


def action_input_is_true(step: dict, key: str) -> bool:
    return action_input(step, key) in ("true", "True", "TRUE")


def is_root_action(spec: str, repository: str) -> bool:
    parts = spec.rsplit("@", 1)[0].split("/")
    return (len(parts) >= 2 and "/".join(part.lower() for part in parts[:2]) == repository
            and posixpath.normpath("/".join(parts[2:]) or ".") == ".")


def scan_workflow(path: Path, expected_branch: str, default_branch: str) -> list[str]:
    text = path.read_text(encoding="utf-8")
    errors = []

    def add(rule: str, message: str, offset: int = 0) -> None:
        errors.append(f"{path.as_posix()}:{line_of(text, offset)}: {rule}: {message}")

    try:
        workflow = load_workflow(text)
    except (yaml.YAMLError, ValueError) as error:
        add("CI_STEP_FORMAT", str(error))
        return errors

    for job in workflow["jobs"].values():
        steps = job.get("steps", [])
        for action in [job, *steps]:
            spec = action.get("uses")
            if spec is None:
                continue
            if not isinstance(spec, str):
                add("CI_ACTION_PIN", "action reference must be a string")
                continue
            spec = spec.strip()
            if not spec.startswith(("./", ".\\")) and not re.fullmatch(r"[0-9a-f]{40}", spec.rsplit("@", 1)[-1]):
                add("CI_ACTION_PIN", f"action must be pinned to a full commit SHA: {spec}")
            if is_root_action(spec, "actions/setup-java"):
                if not action_input_is_true(action, "verify-signature"):
                    add("CI_JDK_SIGNATURE", "setup-java must verify the downloaded JDK signature")
                if not action_input_is_true(action, "force-download"):
                    add("CI_JDK_DOWNLOAD", "setup-java must download the JDK to verify its signature")
            if is_root_action(spec, "actions/checkout"):
                if action_input(action, "fetch-depth") != "0":
                    add("CI_FULL_HISTORY", "checkout must use fetch-depth: 0")
                if action_input(action, "persist-credentials") not in ("false", "False", "FALSE"):
                    add("CI_CHECKOUT_CREDENTIALS", "checkout must set persist-credentials: false")
                permissions = job.get("permissions", workflow.get("permissions", {}))
                if not isinstance(permissions, dict) or permissions.get("contents") != "read":
                    add("CI_PERMISSIONS", "set permissions.contents: read")
            if isinstance(action.get("with"), dict) and action["with"].get("name") == "develop-apk-and-mapping":
                if action["with"].get("if-no-files-found") != "error":
                    add("CI_REQUIRED_ARTIFACT", "missing develop artifacts must fail the upload")

        if job is workflow["jobs"].get("full") and path.name == "a14-ci.yml":
            commands = "\n".join(step.get("run", "") for step in steps)
            builds = re.findall(r"(?m)^\s*\./gradlew[^\n]*\bclean\b[^\n]*:app:assembleDevelop[^\n]*$", commands)
            if len(builds) != 2:
                add("CI_REPRO_BUILDS", "Full CI requires two separate clean develop builds")
            options = {"--no-daemon", "--no-build-cache", "--no-configuration-cache",
                       "-Pkotlin.compiler.execution.strategy=in-process", "-Pkotlin.incremental=false"}
            if any(not options.issubset(build.split()) for build in builds):
                add("CI_REPRO_CACHE", "reproducibility builds must bypass compiled caches and shared processes")
            if not re.search(r"(?m)^\s*cmp[^\n]*first-mapping[.]txt[^\n]*mapping[.]txt", commands):
                add("CI_REPRO_MAPPING", "compare R8 mappings as well as APK contents")

    # Portability and forbidden signing/publication are text checks, independent
    # of YAML formatting. SDK installation is checked against Gradle below.
    rules = [
        ("CI_GRADLEW_MODE", r"chmod\s+\+x\s+\./gradlew\b", "git tracks the executable wrapper"),
        ("CI_LINUX_SHELL", r"\bpwsh\b|\bpowershell\b", "Ubuntu workflow invokes PowerShell"),
        ("CI_LINUX_GRADLE", r"\bgradlew[.]bat\b", "Ubuntu workflow invokes the Windows wrapper"),
        ("CI_SIGNING", r"(?:-P)?officialRelease\s*=\s*true\b|customiuizerA1[34]KeystoreProperties|CUSTOMIUIZER_A1[34]_KEYSTORE_PROPERTIES|secrets[.][A-Z0-9_]*(?:KEYSTORE|SIGN|PASSWORD|KEY)", "official signing is forbidden in CI"),
        ("CI_SDK_LICENSE", r"yes\s*\|\s*sdkmanager\s+--licenses", "setup action already accepts licenses"),
        ("CI_SDK_MASKED_FAILURE", r"sdkmanager[^\n]*(?:\|\|\s*true|;\s*true)", "sdkmanager failure must not be masked"),
        ("CI_SDK_LEGACY_TOOLS", r"packages\s*:\s*[\"']?tools[\"']?\s*$", "do not install legacy SDK tools"),
        ("CI_API37_RESOLUTION", r"sdkmanager[^\n]*[\"']platforms;android-37[\"']", "use the pinned SDK install script"),
        ("CI_SDK_NONDETERMINISTIC", r"android-37\*|android-CinnamonBun|sort\s+-V", "do not glob or sort SDK packages"),
        ("CI_RELEASE", r"\b(?:gh\s+release|softprops/action-gh-release|create-release)\b", "CI must not create a public release"),
    ]
    for rule, pattern, message in rules:
        match = re.search(pattern, text, re.I | re.M)
        if match:
            add(rule, message, match.start())
    if "android-actions/setup-android" in text and "tools/ci_install_android_sdk.sh" not in text:
        add("CI_SDK_PIN", "install the pinned Android SDK packages")

    events = workflow.get("on", {})
    if isinstance(events, dict):
        push = events.get("push")
        if "push" in events and (not isinstance(push, dict) or push.get("branches", []) != [expected_branch]):
            add("CI_EXACT_BRANCH", f"push must target only {expected_branch!r}")
        if "full" in path.name.lower() and expected_branch != default_branch and "push" not in events:
            if "schedule" in events or "workflow_dispatch" in events:
                add("CI_INERT_NONDEFAULT", "non-default full workflow needs a push trigger")
    elif events == "push" or isinstance(events, list) and "push" in events:
        add("CI_EXACT_BRANCH", f"push must target only {expected_branch!r}")
    if any(not str(job.get("timeout-minutes", "")).isdigit() for job in workflow["jobs"].values()
           if "steps" in job):
        add("CI_TIMEOUT", "each executable job must have timeout-minutes")
    if "concurrency" not in workflow:
        add("CI_CONCURRENCY", "workflow must define concurrency")
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
