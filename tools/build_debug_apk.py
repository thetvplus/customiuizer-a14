#!/usr/bin/env python3
"""Explicit diagnostic Debug APK build for CustoMIUIzer A14.

This script builds a diagnostic Debug APK only when explicitly authorized.
It is not a delivery build, not the develop variant, and not a release
candidate. It is intended for short-term engineering diagnostics only.

It:
1. Verifies the tracked worktree is clean.
2. Resolves the current engineering HEAD revision.
3. Builds the APK with an explicit buildRevision and disabled configuration cache.
4. Verifies the APK contains a matching build-provenance.properties.
5. Computes the APK SHA-256 and emits a machine-readable report.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path

if __package__:
    from . import build_revision, check_release_metadata, verify_apk_provenance
else:
    import build_revision
    import check_release_metadata
    import verify_apk_provenance

REPO_ROOT = Path(__file__).resolve().parent.parent
# Canonical cross-platform wrapper selection; this is not a Windows-only branch.
GRADLEW = "gradlew.bat" if os.name == "nt" else "gradlew"
GRADLEW_PATH = REPO_ROOT / GRADLEW
APK_OUTPUT_DIR = REPO_ROOT / "app" / "build" / "outputs" / "apk" / "debug"


def fail(message: str, code: int = 1) -> None:
    print(f"build_debug_apk: {message}", file=sys.stderr)
    sys.exit(code)


def run(cmd: list[str], *, cwd: Path = REPO_ROOT, check: bool = True) -> subprocess.CompletedProcess[str]:
    print(f"=== {' '.join(cmd)} ===")
    result = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)
    if check and result.returncode != 0:
        print(result.stdout or "")
        print(result.stderr or "", file=sys.stderr)
        fail(f"command failed: {' '.join(cmd)}")
    return result


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest().upper()


def resolve_debug_apk() -> Path:
    """Read the current build's AGP output, rejecting stale or ambiguous APKs."""
    metadata_path = APK_OUTPUT_DIR / "output-metadata.json"
    try:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        fail(f"cannot read Debug output metadata: {error}")
    code, name = check_release_metadata.parse_gradle_version()
    elements = metadata.get("elements", [])
    if metadata.get("variantName") != "debug" or len(elements) != 1:
        fail("Debug output metadata must contain one APK for the debug variant")
    element = elements[0]
    if element.get("versionCode") != code or element.get("versionName") != f"{name}-debug":
        fail("Debug output metadata does not match the current Gradle version")
    filename = element.get("outputFile", "")
    if not isinstance(filename, str) or not filename:
        fail("Debug output metadata has no APK filename")
    apk = (APK_OUTPUT_DIR / filename).resolve()
    if apk.parent != APK_OUTPUT_DIR.resolve() or apk.suffix.lower() != ".apk" or not apk.is_file():
        fail("Debug output metadata does not reference an APK in the Debug output directory")
    return apk


def build_debug_apk() -> dict[str, object]:
    if not GRADLEW_PATH.exists():
        fail(f"gradle wrapper not found: {GRADLEW_PATH}")

    try:
        build_revision.check_tracked_worktree_clean(REPO_ROOT)
    except subprocess.CalledProcessError as e:
        fail(f"tracked worktree is dirty (uncommitted or staged changes): {e}")
    except RuntimeError as e:
        fail(str(e))

    full_sha = build_revision.git_head_sha(REPO_ROOT, full=True)
    short_sha = build_revision.validate_revision(build_revision.git_head_sha(REPO_ROOT, full=False))

    run(
        [
            str(GRADLEW_PATH),
            "--no-daemon",
            "--no-configuration-cache",
            ":app:assembleDebug",
            f"-PbuildRevision={short_sha}",
            "-PrequireBuildRevision=true",
        ],
        cwd=REPO_ROOT,
    )

    apk_output = resolve_debug_apk()
    provenance = verify_apk_provenance.read_apk_provenance(apk_output)
    if provenance.get("revision") != short_sha:
        fail(
            f"APK provenance revision mismatch: "
            f"expected {short_sha}, found {provenance.get('revision')}"
        )
    if provenance.get("buildType") != "debug":
        fail(f"APK provenance buildType is not debug: {provenance.get('buildType')}")

    apk_sha = sha256_file(apk_output)

    return {
        "engineeringFullSha": full_sha,
        "engineeringShortSha": short_sha,
        "buildRevision": short_sha,
        "apkPath": str(apk_output),
        "apkSha256": apk_sha,
        "trackedWorktreeClean": True,
        "signature": "Debug",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output",
        "-o",
        type=Path,
        default=None,
        help="optional path to write the JSON report",
    )
    args = parser.parse_args()

    report = build_debug_apk()
    print(json.dumps(report, indent=2))

    if args.output:
        args.output.write_text(json.dumps(report, indent=2), encoding="utf-8")

    return 0


if __name__ == "__main__":
    sys.exit(main())
