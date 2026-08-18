#!/usr/bin/env python3
"""Check that README, CHANGELOG and Gradle version metadata stay aligned."""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
GRADLE = REPO_ROOT / "app" / "build.gradle.kts"


def parse_gradle_version() -> tuple[int, str]:
    text = GRADLE.read_text(encoding="utf-8")
    code = re.search(r"^val lastVersion\s*=\s*(\d+)\s*$", text, re.M)
    name = re.search(r'^val lastVersionName\s*=\s*"([^"]+)"\s*$', text, re.M)
    if not code or not name:
        raise SystemExit("app/build.gradle.kts is missing lastVersion / lastVersionName")
    return int(code.group(1)), name.group(1)


def check(require_tag: bool = False) -> list[str]:
    errors: list[str] = []
    version_code, version_name = parse_gradle_version()
    if not re.fullmatch(r"r14\.\d+\.\d+", version_name):
        errors.append(f"versionName is not an r14.x.y value: {version_name}")

    files = {
        "README.md": REPO_ROOT / "README.md",
        "README_EN.md": REPO_ROOT / "README_EN.md",
        "CHANGELOG.md": REPO_ROOT / "CHANGELOG.md",
        "CHANGELOG_CN.md": REPO_ROOT / "CHANGELOG_CN.md",
    }
    for label, path in files.items():
        if not path.is_file():
            errors.append(f"{label} missing")
            continue
        text = path.read_text(encoding="utf-8")
        if version_name not in text:
            errors.append(f"{label} does not mention {version_name}")

    for changelog in ("CHANGELOG.md", "CHANGELOG_CN.md"):
        text = files[changelog].read_text(encoding="utf-8") if files[changelog].is_file() else ""
        if not re.search(rf"^## {re.escape(version_name)}\b", text, re.M):
            errors.append(f"{changelog} missing heading for {version_name}")
        if str(version_code) not in text:
            errors.append(f"{changelog} does not mention versionCode {version_code}")

    if require_tag:
        result = subprocess.run(
            ["git", "describe", "--tags", "--exact-match", "HEAD"],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
        )
        tag = result.stdout.strip()
        if result.returncode != 0:
            errors.append("HEAD is not an exact tag; --require-tag needs a tagged commit")
        elif tag not in {version_name, f"{version_code}-{version_name}"}:
            errors.append(f"tag {tag!r} does not match {version_name} or {version_code}-{version_name}")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--require-tag", action="store_true")
    args = parser.parse_args()
    errors = check(require_tag=args.require_tag)
    if errors:
        print("RELEASE METADATA VIOLATIONS:")
        for err in errors:
            print(f"  - {err}")
        return 1
    print("Release metadata checks pass.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
