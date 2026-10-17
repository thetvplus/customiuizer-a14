#!/usr/bin/env python3
"""Scan A14 source and classify production hook ownership.

Default mode (--check) scans the current source tree, produces an in-memory
structured classification, and validates it without depending on any committed
Markdown inventory.

Use --output <path> to additionally write a diagnostic Markdown copy of the
scan result to an explicit path (for example a TemporaryDirectory from tests).
This Markdown is a diagnostic artifact, not a long-term control-plane file.
"""

from __future__ import annotations

import argparse
import os
import re
from collections import defaultdict
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SOURCE_ROOT = Path(
    os.environ.get("A14_HOOK_SOURCE_ROOT", REPO_ROOT / "app" / "src" / "main" / "java")
)

HOOK_CALL_RE = re.compile(
    r"(XposedHelpers|XposedBridge)\.(findAndHookMethod|findAndHookConstructor|hookAllMethods|hookAllConstructors|hookMethod|newInstance|set\w+)"
    r"|findAndHookMethod\s*\("
    r"|hookAllMethods\s*\("
    r"|hookAllConstructors\s*\("
)

LEGACY_RE = re.compile(r"de\.robv\.android\.xposed")

ALLOWED_CATEGORIES = frozenset(
    {
        "API_BRIDGE",
        "INSTALLER_INFRASTRUCTURE",
        "RESOURCE_INFRASTRUCTURE",
        "REGISTRY_FEATURE",
        "LEGACY_EXCEPTION",
        "DEAD_CANDIDATE",
    }
)


def classify(path: Path, line: int, text: str) -> str:
    rel = path.relative_to(SOURCE_ROOT).as_posix()
    if LEGACY_RE.search(text):
        return "LEGACY_EXCEPTION"
    if "ResourceHooks" in rel or "resource" in path.name.lower():
        return "RESOURCE_INFRASTRUCTURE"
    if "XposedHelpers" in rel or "HookerClassHelper" in rel:
        return "API_BRIDGE"
    if "Installer" in rel or "MainModule" in rel:
        return "INSTALLER_INFRASTRUCTURE"
    if "/feature/" in rel or "Features" in rel or "Hooks" in rel:
        return "REGISTRY_FEATURE"
    # Most top-level mods/*.kt files are FeatureDefinition implementations.
    if rel.startswith("tv/withaibuild/customiuizer/mods/") and not rel.startswith(
        "tv/withaibuild/customiuizer/mods/utils/"
    ):
        return "REGISTRY_FEATURE"
    # Business-feature helper classes in utils/ that still contain feature-specific hook calls.
    if (
        "DeviceInfoMonitor" in rel
        or "LockScreenAlbumArtController" in rel
        or "ControlCenterPluginRuntime" in rel
        or "StatusBarTextFit" in rel
        or "StatusBarContentGeometry" in rel
    ):
        return "REGISTRY_FEATURE"
    if "mods/utils/ModuleHelper" in rel:
        return "INSTALLER_INFRASTRUCTURE"
    if "mods/utils/ReflectionCache" in rel:
        return "INSTALLER_INFRASTRUCTURE"
    if "PreferenceObserverRegistry" in rel:
        return "INSTALLER_INFRASTRUCTURE"
    if "SystemUiBootstrapCoordinator" in rel:
        return "INSTALLER_INFRASTRUCTURE"
    return "UNKNOWN"


def nearest_function(lines: list[str], line_idx: int) -> str | None:
    for i in range(line_idx, -1, -1):
        stripped = lines[i].strip()
        m = re.match(r"(?:\s)*(?:fun|override fun|open fun)\s+(\w+)", stripped)
        if m:
            return m.group(1)
        m = re.match(
            r"(?:\s)*(?:public|private|protected|static|\s)+(?:\w+\s+)+(\w+)\s*\(",
            stripped,
        )
        if m and m.group(1) not in ("if", "while", "for", "switch", "catch"):
            return m.group(1)
    return None


def _scan_source() -> tuple[int, list[dict[str, object]]]:
    records: list[dict[str, object]] = []
    total = 0
    sources = sorted(
        SOURCE_ROOT.rglob("*.kt"), key=lambda p: p.as_posix().lower()
    ) + sorted(SOURCE_ROOT.rglob("*.java"), key=lambda p: p.as_posix().lower())

    for path in sources:
        text = path.read_text(encoding="utf-8")
        lines = text.splitlines()
        for i, line in enumerate(lines, start=1):
            if HOOK_CALL_RE.search(line):
                total += 1
                category = classify(path, i, text)
                function = nearest_function(lines, i - 1) or "?"
                rel = path.relative_to(SOURCE_ROOT).as_posix()
                records.append(
                    {
                        "file": rel,
                        "line": i,
                        "function": function,
                        "category": category,
                        "snippet": line.strip(),
                    }
                )

    return total, records


def _validate(total: int, records: list[dict[str, object]]) -> list[str]:
    errors: list[str] = []
    if total <= 0:
        errors.append("No hook call sites found")
    if len(records) != total:
        errors.append(
            f"Record count mismatch: total={total}, records={len(records)}"
        )

    required_fields = ("file", "line", "function", "category")
    for record in records:
        for field in required_fields:
            value = record.get(field)
            if value is None or (isinstance(value, str) and value == ""):
                errors.append(f"Record missing {field}: {record}")

        category = record.get("category")
        if category not in ALLOWED_CATEGORIES:
            errors.append(
                f"Disallowed category {category!r} at "
                f"{record.get('file')}:{record.get('line')}"
            )

    return errors


def _generate_markdown(total: int, records: list[dict[str, object]]) -> str:
    groups: dict[str, list[dict[str, object]]] = defaultdict(list)
    for record in records:
        groups[str(record["category"])].append(record)

    md = ["# A14 Hook Ownership Inventory\n\n"]
    md.append(f"Total hook call sites scanned: {total}\n\n")
    md.append("| Category | Count |\n|---|---|\n")
    for cat in sorted(groups, key=lambda c: (-len(groups[c]), c)):
        md.append(f"| {cat} | {len(groups[cat])} |\n")
    md.append("\n")

    for cat in sorted(groups, key=lambda c: (-len(groups[c]), c)):
        md.append(f"## {cat}\n\n")
        md.append("| File | Line | Function | Snippet |\n|---|---|---|---|\n")
        for record in sorted(groups[cat], key=lambda r: (r["file"], r["line"])):
            snippet = (
                str(record.get("snippet", ""))
                .replace("|", "\\|")
                .replace("\n", " ")
                .replace("\r", " ")[:120]
            )
            md.append(
                f"| `{record['file']}` | {record['line']} | "
                f"`{record['function']}` | `{snippet}` |\n"
            )
        md.append("\n")

    return "".join(md)


def _write_inventory(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")


def _normalize_text(text: str) -> str:
    return text.replace("\r\n", "\n").replace("\r", "\n")


def check(output_path: Path | None = None) -> int:
    """Scan source, validate classification, and optionally write a diagnostic Markdown."""
    total, records = _scan_source()
    errors = _validate(total, records)

    if errors:
        print("Hook ownership scan failed:")
        for error in errors:
            print(f"  - {error}")
        return 1

    if output_path is not None:
        _write_inventory(output_path, _generate_markdown(total, records))
        print(f"Wrote {output_path}")

    print("Hook ownership scan passes")
    return 0


def main() -> int:
    p = argparse.ArgumentParser(
        description="Scan A14 hook call sites and verify ownership classification."
    )
    p.add_argument(
        "--check",
        action="store_true",
        default=True,
        help="scan source and verify classification (default)",
    )
    p.add_argument(
        "--output",
        type=Path,
        default=None,
        help="write a diagnostic Markdown copy to PATH",
    )
    args = p.parse_args()
    return check(args.output)


if __name__ == "__main__":
    raise SystemExit(main())
