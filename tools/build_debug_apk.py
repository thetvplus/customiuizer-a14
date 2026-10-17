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
import tempfile
import zipfile
from pathlib import Path

import build_revision
import verify_apk_provenance

REPO_ROOT = Path(__file__).resolve().parent.parent
# Canonical cross-platform wrapper selection; this is not a Windows-only branch.
GRADLEW = "gradlew.bat" if os.name == "nt" else "gradlew"
GRADLEW_PATH = REPO_ROOT / GRADLEW
APK_OUTPUT = (
    REPO_ROOT
    / "app"
    / "build"
    / "outputs"
    / "apk"
    / "debug"
    / "CustoMIUIzer-A14-r14.16.1-debug.apk"
)


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

    if not APK_OUTPUT.is_file():
        fail(f"APK not found at expected path: {APK_OUTPUT}")

    provenance = verify_apk_provenance.read_apk_provenance(APK_OUTPUT)
    if provenance.get("revision") != short_sha:
        fail(
            f"APK provenance revision mismatch: "
            f"expected {short_sha}, found {provenance.get('revision')}"
        )
    if provenance.get("buildType") != "debug":
        fail(f"APK provenance buildType is not debug: {provenance.get('buildType')}")

    apk_sha = sha256_file(APK_OUTPUT)

    return {
        "engineeringFullSha": full_sha,
        "engineeringShortSha": short_sha,
        "buildRevision": short_sha,
        "apkPath": str(APK_OUTPUT),
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
