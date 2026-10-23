#!/usr/bin/env python3
"""Promote reviewed bilingual Unreleased entries into one aligned release commit.

This command only edits source metadata; it never commits, pushes, tags or
publishes. Release notes are subsequently rendered from these exact sections.
"""
from __future__ import annotations

import argparse
from datetime import date
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def version_tuple(version: str) -> tuple[int, ...]:
    require(re.fullmatch(r"r14\.\d+\.\d+", version) is not None, "version must be r14.x.y")
    return tuple(map(int, version[1:].split(".")))


def plan(root: Path, version: str, code: int, release_date: date, *,
         supersede: str | None = None, base_version: str | None = None) -> dict[Path, str]:
    gradle = root / "app/build.gradle.kts"
    source = gradle.read_text(encoding="utf-8")
    names = re.findall(r'^val lastVersionName = "(r14\.\d+\.\d+)"$', source, re.M)
    codes = re.findall(r"^val lastVersion = (\d+)$", source, re.M)
    require(len(names) == len(codes) == 1, "Gradle version declarations must be unique")
    previous, previous_code = names[0], int(codes[0])
    require((supersede is None) == (base_version is None), "supersede and base-version must be selected together")
    if supersede is not None:
        require(supersede == previous, "Only the current release may be superseded")
        require(version_tuple(base_version) < version_tuple(previous), "base-version must precede the superseded release")
    require(version_tuple(version) > version_tuple(previous), "versionName must increase")
    require(previous_code < code <= 2_100_000_000, "versionCode must increase within the Android limit")
    changes = {gradle: source.replace(f'val lastVersionName = "{previous}"', f'val lastVersionName = "{version}"')
               .replace(f"val lastVersion = {previous_code}\n", f"val lastVersion = {code}\n")}
    for filename in ("README.md", "README_EN.md"):
        path = root / filename
        text = path.read_text(encoding="utf-8")
        pattern = re.escape(f"`{previous}`") + r"([^\n]*?\bversionCode )" + str(previous_code) + r"\b"
        text, count = re.subn(pattern, lambda match: f"`{version}`{match[1]}{code}", text)
        require(count == 1, f"{filename} current release declaration must be unique")
        changes[path] = text
    baseline = base_version or previous
    for filename, heading, intro in (("CHANGELOG.md", "Unreleased", f"Changes since {baseline}:"),
                                     ("CHANGELOG_CN.md", "未发布", f"自 {baseline} 起的变更：")):
        path = root / filename
        text = path.read_text(encoding="utf-8")
        matches = list(re.finditer(rf"^## {heading}\s*$", text, re.M))
        require(len(matches) == 1, f"{filename} needs one {heading} section")
        match = matches[0]
        next_heading = re.search(r"^## ", text[match.end():], re.M)
        end = match.end() + next_heading.start() if next_heading else len(text)
        body = text[match.end():end].strip()
        require(re.search(r"^[-*] \S", body, re.M) is not None, f"{filename} {heading} needs reviewed change entries")
        require("versionCode" not in body, f"{filename} {heading} must not contain a versionCode declaration")
        require(not re.search(rf"^## {re.escape(version)}\b", text, re.M), f"{filename} already contains {version}")
        history = text[end:]
        if supersede is not None:
            releases = list(re.finditer(r"^## (r14\.\d+\.\d+)\b[^\n]*", history, re.M))
            require(len(releases) >= 2 and releases[0][1] == supersede and releases[1][1] == base_version,
                    f"{filename} base-version must be the immediately preceding retained release")
            require(sum(item[1] == supersede for item in releases) == 1,
                    f"{filename} superseded release must be unique")
            user_heading = "用户变化" if filename.endswith("_CN.md") else "User changes"
            engineering_heading = "工程说明" if filename.endswith("_CN.md") else "Engineering notes"
            require(f"### {user_heading}\n" in body and f"### {engineering_heading}\n" in body,
                    f"{filename} consolidation needs reviewed cumulative user changes and engineering notes")
            history = history[releases[1].start():]
        section = (f"## {heading}\n\n## {version} — {release_date.isoformat()}\n\n"
                   f"versionCode {code}{'。' if filename.endswith('_CN.md') else '.'} {intro}\n\n{body}\n\n")
        changes[path] = text[:match.start()] + section + history
    return changes


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--code", type=int, required=True)
    parser.add_argument("--date", type=date.fromisoformat, default=date.today())
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--supersede", help="Explicitly replace the current Changelog section with reviewed cumulative entries")
    parser.add_argument("--base-version", help="Previous retained release used by the cumulative entries")
    args = parser.parse_args()
    try:
        changes = plan(ROOT, args.version, args.code, args.date, supersede=args.supersede, base_version=args.base_version)
        # Validate every input before writing any of the five files.
        for path, text in changes.items():
            if not args.dry_run:
                path.write_text(text, encoding="utf-8", newline="\n")
            print(("Would update " if args.dry_run else "Updated ") + path.relative_to(ROOT).as_posix())
    except (OSError, ValueError) as error:
        print(f"prepare_release: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
