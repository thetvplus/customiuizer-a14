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
CURRENT_CHANGELOGS = (("CHANGELOG.md", "简体中文"), ("CHANGELOG_EN.md", "English"))
LEGACY_CHANGELOGS = (("CHANGELOG_CN.md", "简体中文"), ("CHANGELOG.md", "English"))


def source_changelogs(paths: set[str]) -> tuple[tuple[str, str], ...]:
    """Select an unambiguous layout at the candidate's exact source commit."""
    present = paths & {"CHANGELOG.md", "CHANGELOG_EN.md", "CHANGELOG_CN.md"}
    for layout in (CURRENT_CHANGELOGS, LEGACY_CHANGELOGS):
        if present == {filename for filename, _ in layout}:
            return layout
    raise ValueError("Source changelog layout is missing or ambiguous")


def parse_gradle_version() -> tuple[int, str]:
    text = GRADLE.read_text(encoding="utf-8")
    code = re.search(r"^val lastVersion\s*=\s*(\d+)\s*$", text, re.M)
    name = re.search(r'^val lastVersionName\s*=\s*"([^"]+)"\s*$', text, re.M)
    if not code or not name:
        raise SystemExit("app/build.gradle.kts is missing lastVersion / lastVersionName")
    return int(code.group(1)), name.group(1)


def changelog_sections(text: str) -> list[tuple[str, str]]:
    """Return release headings and their own bodies, excluding later sections."""
    headings = list(re.finditer(r"^##\s+([^\n]+)$", text, re.M))
    sections: list[tuple[str, str]] = []
    for index, heading in enumerate(headings):
        version = re.match(r"(r14\.\d+\.\d+)\b", heading.group(1))
        if version:
            end = headings[index + 1].start() if index + 1 < len(headings) else len(text)
            sections.append((version.group(1), text[heading.end():end]))
    return sections


def check(require_tag: bool = False) -> list[str]:
    errors: list[str] = []
    version_code, version_name = parse_gradle_version()
    if not re.fullmatch(r"r14\.\d+\.\d+", version_name):
        errors.append(f"versionName is not an r14.x.y value: {version_name}")

    files = {
        "README.md": REPO_ROOT / "README.md",
        "README_EN.md": REPO_ROOT / "README_EN.md",
        **{filename: REPO_ROOT / filename for filename, _ in CURRENT_CHANGELOGS},
    }
    if (REPO_ROOT / "CHANGELOG_CN.md").exists():
        errors.append("Current source must use CHANGELOG.md and CHANGELOG_EN.md")
    for label, path in files.items():
        if not path.is_file():
            errors.append(f"{label} missing")
            continue
        text = path.read_text(encoding="utf-8")
        if version_name not in text:
            errors.append(f"{label} does not mention {version_name}")

    for changelog, _ in CURRENT_CHANGELOGS:
        text = files[changelog].read_text(encoding="utf-8") if files[changelog].is_file() else ""
        sections = changelog_sections(text)
        if not sections or sections[0][0] != version_name:
            errors.append(f"{changelog} first release heading must be {version_name}")
        current = [body for name, body in sections if name == version_name]
        if len(current) != 1:
            errors.append(f"{changelog} must contain exactly one heading for {version_name}")
        elif re.findall(r"\bversionCode(?:\s*[:=]\s*|\s+)(\d+)\b", current[0]) != [str(version_code)]:
            errors.append(f"{changelog} {version_name} must declare versionCode {version_code} exactly once")

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
