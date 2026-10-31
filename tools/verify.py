#!/usr/bin/env python3
"""Local verification entry point for CustoMIUIzer A14.

This script replaces the ad-hoc gradle/ADB/signature workflow with a single,
offline-safe gate.  It never builds an APK, never touches a device, and never
runs lintVitalRelease/R8.

Examples:
    python tools/verify.py fast
    python tools/verify.py fast --changed
    python tools/verify.py fast --staged
    python tools/verify.py fast --tests PreferenceBootstrapTest
    python tools/verify.py fast --tests PreferenceBootstrapTest ModuleReceiverRegistrationTest
    python tools/verify.py full
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import eol_check

try:
    from tools.git_changes import changed_paths
except ModuleNotFoundError:
    from git_changes import changed_paths

REPO_ROOT = Path(__file__).resolve().parent.parent
REQUIRED_JAVA_MAJOR = 27

# Gradle wrapper name depends on the host.
GRADLEW = "gradlew.bat" if os.name == "nt" else "gradlew"
GRADLEW_PATH = REPO_ROOT / GRADLEW

# Build tasks that are never allowed in this verification script.
PROHIBITED_TASKS = {
    "assemble",
    "package",
    "bundle",
    "install",
    "sign",
    "publish",
    "officialRelease",
    "lintVitalRelease",
}


def fail(message: str) -> None:
    print(f"verify: {message}", file=sys.stderr)
    sys.exit(1)


def _summarize(output: str, tail_lines: int = 12) -> str:
    """Keep build diagnostics visible while abbreviating successful task output."""
    lines = output.rstrip().splitlines()
    if len(lines) <= tail_lines * 2:
        return "\n".join(lines)
    diagnostics: dict[str, tuple[str, int]] = {}
    for line in lines[4:-tail_lines]:
        if re.search(r"^\s*(?:w:|warning:|note:|Missing class|Unsupported class file)|\b(?:Warning|deprecated|obsolete)\b", line, re.IGNORECASE):
            # One representative location per file/message, including generated code.
            key = re.sub(r":\d+:\d+(?=\s)", "", line)
            first, count = diagnostics.get(key, (line, 0))
            diagnostics[key] = (first, count + 1)
    # No accepted high-risk diagnostics: surface future compiler errors and unsafe
    # bytecode/JDK behavior for review, without failing reasonable A14 deprecations.
    high_risk = re.compile(r"will become an error|Missing class|Unsupported class file|will be blocked in a future release", re.IGNORECASE)
    summary = [("[REVIEW] " if high_risk.search(line) else "") + line +
               (f" [x{count}]" if count > 1 else "") for line, count in diagnostics.values()]
    # Bound successful output even when every diagnostic is unique. Review entries
    # come first and omitted counts stay explicit; failures still print full output.
    summary.sort(key=lambda line: not line.startswith("[REVIEW]"))
    limit = 80
    omitted = summary[limit:]
    if omitted:
        reviews = sum(line.startswith("[REVIEW]") for line in omitted)
        summary = summary[:limit] + [f"... {len(omitted)} diagnostic groups omitted ({reviews} REVIEW); rerun the displayed command for complete output"]
    return "\n".join(lines[:4] + ["..."] + summary + ["..."] + lines[-tail_lines:])


def command_environment(source: dict[str, str] | None = None) -> dict[str, str]:
    """Use UTF-8 diagnostics and normalize legacy JAVA_HOME/bin for child tools."""
    env = dict(os.environ if source is None else source)
    # Piped Python diagnostics must agree with Gradle/JDK's UTF-8 output on Windows.
    env["PYTHONIOENCODING"] = "utf-8:backslashreplace"
    raw_home = env.get("JAVA_HOME", "").strip().strip('"')
    if not raw_home:
        return env

    home = Path(raw_home)
    java_name = "java.exe" if sys.platform == "win32" else "java"
    if home.name.lower() == "bin" and (home / java_name).is_file():
        env["JAVA_HOME"] = str(home.parent)
    return env


def parse_java_major(output: str) -> int | None:
    """Parse the major version from standard java -version output."""
    match = re.search(r'(?:java|openjdk) version "(?:1\.)?(\d+)', output, re.IGNORECASE)
    return int(match.group(1)) if match else None


def check_java_runtime() -> int:
    """Require the system-selected build runtime to be JDK 27."""
    env = command_environment()
    java_home = env.get("JAVA_HOME", "").strip()
    if java_home:
        java_name = "java.exe" if sys.platform == "win32" else "java"
        java = Path(java_home) / "bin" / java_name
        if not java.is_file():
            fail(f"JAVA_HOME does not point to a JDK root: {java_home}")
        executable = str(java)
    else:
        executable = shutil.which("java", path=env.get("PATH")) or ""
        if not executable:
            fail("system java was not found on PATH")

    result = subprocess.run(
        [executable, "-version"],
        cwd=REPO_ROOT,
        env=env,
        capture_output=True,
        text=True,
    )
    output = (result.stdout or "") + (result.stderr or "")
    major = parse_java_major(output)
    if result.returncode != 0 or major != REQUIRED_JAVA_MAJOR:
        fail(
            f"JDK {REQUIRED_JAVA_MAJOR} is required; "
            f"selected runtime is {major or 'unknown'} ({executable})"
        )
    print(f"verify: JDK {major} runtime passed ({executable})")
    return 0


def run(cmd: list[str]) -> int:
    """Run a command in the repo root; on success only print a summary, on failure print full output."""
    print(f"\n=== {' '.join(cmd)} ===")
    result = subprocess.run(
        cmd,
        cwd=REPO_ROOT,
        env=command_environment(),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="backslashreplace",
    )
    output = (result.stdout or "") + (result.stderr or "")
    if result.returncode != 0:
        print(output)
        return result.returncode
    print(_summarize(output))
    print(f"verify: {' '.join(cmd)} ok")
    return 0


def resolve_build_revision() -> str:
    """Return the current 8-character HEAD revision for gradle builds."""
    result = subprocess.run(
        ["git", "rev-parse", "--short=8", "HEAD"],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        check=True,
    )
    revision = result.stdout.strip()
    if not re.fullmatch(r"[0-9a-fA-F]{8}", revision):
        fail(f"could not determine 8-character build revision: {revision!r}")
    return revision


def gradle(*tasks: str) -> int:
    """Run gradle tasks, after making sure none are prohibited."""
    for task in tasks:
        if any(prohibited in task for prohibited in PROHIBITED_TASKS):
            fail(f"prohibited task requested: {task}")
    if not GRADLEW_PATH.exists():
        fail(f"gradle wrapper not found: {GRADLEW_PATH}")
    revision = resolve_build_revision()
    return run([str(GRADLEW_PATH), "--no-daemon", f"-PbuildRevision={revision}", *tasks])


def check_invariants(changed: bool = False, staged: bool = False) -> int:
    cmd = [sys.executable, str(REPO_ROOT / "tools" / "check-invariants.py")]
    if changed:
        cmd.append("--changed")
    elif staged:
        cmd.append("--staged")
    return run(cmd)


def check_eol() -> int:
    """Verify EOL / encoding policy via a single git ls-files --eol call."""
    return eol_check.check()


def check_feature_semantics() -> int:
    """Validate feature-semantics/a14.json against schema and current source."""
    cmd = [sys.executable, str(REPO_ROOT / "tools" / "audit-feature-semantics.py"), "--validate"]
    return run(cmd)


def check_observer_key_contract() -> int:
    """Ensure production PreferenceObserver onChange bodies do not depend on pref_key_ strings."""
    cmd = [sys.executable, str(REPO_ROOT / "tools" / "check_observer_key_contract.py")]
    return run(cmd)


def check_hook_body_prefmap() -> int:
    """Fail when MethodHook bodies grow new MainModule.mPrefs reads."""
    cmd = [sys.executable, str(REPO_ROOT / "tools" / "hook_body_prefmap_scan.py"), "--check"]
    return run(cmd)


def check_hotpath_alloc_budget() -> int:
    """Fail when MethodHook bodies grow new allocation-suspect patterns."""
    cmd = [sys.executable, str(REPO_ROOT / "tools" / "check_hotpath_alloc_budget.py"), "--check"]
    return run(cmd)


def check_main_source_cleanliness(changed: bool = False, staged: bool = False) -> int:
    """Ensure app/src/main contains no test-only implementation seams."""
    cmd = [sys.executable, str(REPO_ROOT / "tools" / "check_main_source_cleanliness.py")]
    if changed:
        cmd.append("--changed")
    elif staged:
        cmd.append("--staged")
    return run(cmd)


def read_build_gradle() -> str:
    build_file = REPO_ROOT / "app" / "build.gradle.kts"
    if build_file.exists():
        return build_file.read_text(encoding="utf-8")
    build_file = build_file.with_suffix("")
    if build_file.exists():
        return build_file.read_text(encoding="utf-8")
    fail("app/build.gradle.kts not found")


def read_module_prop() -> str:
    prop = (
        REPO_ROOT
        / "app"
        / "src"
        / "main"
        / "resources"
        / "META-INF"
        / "xposed"
        / "module.prop"
    )
    if not prop.exists():
        fail("module.prop not found")
    return prop.read_text(encoding="utf-8")


def require_in_text(text: str, pattern: str, what: str) -> None:
    if not re.search(pattern, text):
        fail(f"{what} not found in build configuration")


def check_static_rules() -> int:
    """Verify project-level A14 invariants that do not require gradle."""
    build = read_build_gradle()
    prop = read_module_prop()

    require_in_text(build, r'applicationId\s*=\s*"tv\.withaibuild\.customiuizer\.r14"', "applicationId")
    require_in_text(build, r'compileSdk\s*=\s*3[4-9]', "compileSdk >= 34")
    require_in_text(build, r'minSdk\s*=\s*34', "minSdk 34")
    require_in_text(build, r'targetSdk\s*=\s*34', "targetSdk 34")
    require_in_text(build, r'sourceCompatibility\s*=\s*JavaVersion\.VERSION_26', "Java source compatibility 26")
    require_in_text(build, r'targetCompatibility\s*=\s*JavaVersion\.VERSION_26', "Java target compatibility 26")
    require_in_text(build, r'jvmTarget\.set\(org\.jetbrains\.kotlin\.gradle\.dsl\.JvmTarget\.JVM_26\)', "Kotlin JVM target 26")
    require_in_text(build, r'languageVersion\s*=\s*JavaLanguageVersion\.of\(27\)', "Java toolchain 27")

    daemon_criteria = REPO_ROOT / "gradle" / "gradle-daemon-jvm.properties"
    if not daemon_criteria.is_file():
        fail("gradle daemon JVM criteria not found")
    require_in_text(daemon_criteria.read_text(encoding="utf-8"), r'(?m)^toolchainVersion=27$', "Gradle daemon JDK 27")

    require_in_text(prop, r"minApiVersion\s*=\s*102", "module.prop minApiVersion=102")
    require_in_text(prop, r"targetApiVersion\s*=\s*102", "module.prop targetApiVersion=102")
    require_in_text(prop, r"staticScope\s*=\s*true", "module.prop staticScope=true")

    # Legacy Xposed package must not appear outside the three allowed boundary files.
    allowed_legacy = {
        "app/src/main/java/tv/withaibuild/customiuizer/MainModule.java",
        "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java",
        "app/src/main/java/org/apache/commons/lang3/reflect/MemberUtilsX.java",
    }
    source_root = REPO_ROOT / "app" / "src" / "main" / "java"
    for path in list(source_root.rglob("*.kt")) + list(source_root.rglob("*.java")):
        rel = path.relative_to(REPO_ROOT).as_posix()
        text = path.read_text(encoding="utf-8")
        if "de.robv.android.xposed" in text and rel not in allowed_legacy:
            fail(f"legacy Xposed package referenced in {rel}")

    print("verify: static rules passed")
    return 0


def is_android_build_input(path: str) -> bool:
    return path.startswith(("gradle/", "app/lib/")) or ("/" not in path and path.startswith("gradlew")) or path in {
        "app/build.gradle.kts", "build.gradle.kts", "settings.gradle.kts",
        "gradle.properties",
    }


def check_source(changed: bool = False, staged: bool = False) -> int:
    for check in (
        check_java_runtime, check_static_rules, check_eol, check_observer_key_contract,
        check_hook_body_prefmap, check_hotpath_alloc_budget,
        lambda: check_main_source_cleanliness(changed=changed, staged=staged),
        lambda: check_invariants(changed=changed, staged=staged), check_feature_semantics,
    ):
        code = check()
        if code:
            return code
    return 0


def fast(tests: list[str] | None, changed: bool = False, staged: bool = False) -> int:
    code = check_source(changed, staged)
    if code:
        return code

    if tests:
        test_args = []
        for t in tests:
            test_args.extend(["--tests", t])
        return gradle("testDebugUnitTest", *test_args)

    if changed or staged:
        paths = changed_paths(REPO_ROOT, staged=staged)
        if not any(p.startswith("app/src/") or is_android_build_input(p) for p in paths):
            print("verify: no Android source or build input changes; skipping gradle")
            return 0
        if any(p.startswith("app/src/test/") or is_android_build_input(p) for p in paths):
            return gradle("testDebugUnitTest")
    return gradle("compileDebugKotlin", "compileDebugJavaWithJavac")


def full() -> int:
    code = check_source()
    if code:
        return code

    return gradle(
        "compileDebugKotlin",
        "compileDebugJavaWithJavac",
        "testDebugUnitTest",
        "lintDebug",
    )


def behavior(tests: list[str]) -> int:
    """Independent JVM gate: assertion failure=1, unavailable=2, build/tool error=3.

    No mutation name or source scanner is involved. Fresh JUnit reports prevent
    compilation failures or an old failing report from being counted as a kill.
    """
    if not tests or any(not re.fullmatch(r"[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)+", name) for name in tests):
        print("behavior: supply exact, fully qualified JVM test classes", file=sys.stderr)
        return 3
    try:
        check_java_runtime()
        folder = REPO_ROOT / "app/build/test-results/testDebugUnitTest"
        reports = [folder / f"TEST-{name}.xml" for name in dict.fromkeys(tests)]
        before = {p: p.stat().st_mtime_ns if p.exists() else None for p in reports}
        args = ["testDebugUnitTest", "--rerun"]
        for name in dict.fromkeys(tests):
            args.extend(["--tests", name])
        code = gradle(*args)
        if any(not p.is_file() or p.stat().st_mtime_ns == before[p] for p in reports):
            print("behavior: missing/stale JUnit reports; build failure is not a semantic kill", file=sys.stderr)
            return 3
        roots = [ET.parse(p).getroot() for p in reports]
        if any(int(r.get("tests", 0)) <= int(r.get("skipped", 0)) for r in roots):
            print("behavior: a selected suite executed no tests", file=sys.stderr)
            return 2
        failures = [f"{r.get('name')}.{case.get('name')}: {failure.get('message', '')[:400]}"
                    for r in roots for case in r.findall("testcase")
                    for failure in [*case.findall("failure"), *case.findall("error")]]
        if code != 0 and failures:
            print("behavior: fresh JVM failures\n" + "\n".join(failures[:12]))
            return 1
        if code != 0 or failures:
            print("behavior: inconsistent Gradle/JUnit result", file=sys.stderr)
            return 3
        print(f"behavior: {sum(int(r.get('tests', 0)) for r in roots)} tests passed")
        return 0
    except (OSError, ET.ParseError, ValueError, subprocess.SubprocessError, SystemExit) as error:
        print(f"behavior: cannot execute gate: {error}", file=sys.stderr)
        return 3


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    fast_parser = subparsers.add_parser("fast", help="static checks + compile + optional tests")
    fast_parser.add_argument(
        "--tests",
        nargs="+",
        help="run only these test classes (e.g. PreferenceBootstrapTest)",
    )
    scope = fast_parser.add_mutually_exclusive_group()
    scope.add_argument(
        "--changed",
        action="store_true",
        help="check changes relative to HEAD, including non-ignored untracked files",
    )
    scope.add_argument(
        "--staged",
        action="store_true",
        help="only check source files staged in the index",
    )

    subparsers.add_parser("full", help="static checks + invariants + compile + tests + lintDebug")
    behavior_parser = subparsers.add_parser("behavior", help="fresh JVM reports for an independent mutation gate; no APK")
    behavior_parser.add_argument("--tests", nargs="+", required=True, help="exact fully qualified JVM test classes")

    args = parser.parse_args()

    if args.command == "fast":
        return fast(args.tests, changed=args.changed, staged=args.staged)
    if args.command == "full":
        return full()
    if args.command == "behavior":
        return behavior(args.tests)
    return 2


if __name__ == "__main__":
    sys.exit(main())
