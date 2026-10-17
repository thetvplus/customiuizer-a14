#!/usr/bin/env python3
"""Portability gate for CI-usable scripts, tools and GitHub workflows.

Scans ``tools/``, ``tools/tests/``, ``scripts/`` and ``.github/workflows/`` for
Windows-only paths, shell assumptions, shallow checkout, signing leaks and
other defects that caused A13 CI failures.

Usage:
    python tools/check_ci_portability.py

Exit code 0 means the repository is safe for the first Ubuntu CI run.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

TOOLS_DIR = REPO_ROOT / "tools"
TOOLS_TESTS_DIR = REPO_ROOT / "tools" / "tests"
SCRIPTS_DIR = REPO_ROOT / "scripts"
WORKFLOWS_DIR = REPO_ROOT / ".github" / "workflows"

# The checker itself and its test file contain the pattern literals used for
# detection; they must not be treated as the defects they define.
ALLOWED_FILES = {
    TOOLS_DIR / "check_ci_portability.py",
    TOOLS_TESTS_DIR / "test_check_ci_portability.py",
}

# Canonical cross-platform gradlew wrapper selection.  These spans are masked
# before the Windows-only rules run so the literal "gradlew.bat" and the
# platform check inside a proper wrapper selection do not trip the detector.
_GRADLEW_TERNARY_PATTERN = re.compile(
    r"""
    ^\s*\w+\s*=\s*(?:["']gradlew\.bat["'])\s+if\s+.*?\b(?:os\.name|sys\.platform|shutil\.which)\b.*?\s+else\s+(?:["']gradlew["'])\s*$
    |
    ^\s*\w+\s*=\s*(?:["']gradlew["'])\s+if\s+.*?\b(?:os\.name|sys\.platform|shutil\.which)\b.*?\s+else\s+(?:["']gradlew\.bat["'])\s*$
    """,
    re.VERBOSE | re.M,
)
_GRADLEW_IFELSE_PATTERN = re.compile(
    r"""
    if\s*[^:\n]*\b(?:os\.name|sys\.platform|shutil\.which)\b[^:\n]*:\s*\n
    \s+\w+\s*=\s*(?:["']gradlew\.bat["'])\s*\n
    \s*else:\s*\n
    \s+\w+\s*=\s*(?:["']gradlew["'])\s*$
    |
    if\s*[^:\n]*\b(?:os\.name|sys\.platform|shutil\.which)\b[^:\n]*:\s*\n
    \s+\w+\s*=\s*(?:["']gradlew["'])\s*\n
    \s*else:\s*\n
    \s+\w+\s*=\s*(?:["']gradlew\.bat["'])\s*$
    """,
    re.VERBOSE | re.M,
)

# Patterns that appear in markdown escaping and other legitimate contexts;
# the portability checker only flags the path-separator variant of replace().
PATH_REPLACE_PATTERNS = [
    re.compile(r'\.replace\s*\(\s*["\']\/["\']\s*,\s*["\']\\["\']\s*\)'),
    re.compile(r'\.replace\s*\(\s*["\']/["\']\s*,\s*["\']\\\\["\']\s*\)'),
]

# A Windows drive path is a single letter (not part of an identifier or regex
# class) followed by a colon and a path separator.
HARD_DRIVE_PATTERN = re.compile(r'(?<![A-Za-z0-9_$\\\\])[A-Za-z]:[\\/]')
POWERSHELL_PATTERN = re.compile(r'\b(powershell|pwsh)\b')
GRADLEW_BAT_PATTERN = re.compile(r'\bgradlew\.bat\b')
OS_NAME_PATTERN = re.compile(r'\bos\.name\b')


def _canonical_gradlew_spans(text: str) -> list[tuple[int, int]]:
    """Return text spans that are canonical cross-platform gradlew selections."""
    spans: list[tuple[int, int]] = []
    for pattern in (_GRADLEW_TERNARY_PATTERN, _GRADLEW_IFELSE_PATTERN):
        for match in pattern.finditer(text):
            spans.append((match.start(), match.end()))
    return spans


def _mask_spans(text: str, spans: list[tuple[int, int]]) -> str:
    """Replace *spans* with spaces, preserving newlines, so offsets stay valid."""
    chars = list(text)
    for start, end in sorted(spans):
        for i in range(start, min(end, len(chars))):
            if chars[i] != "\n":
                chars[i] = " "
    return "".join(chars)


def iter_text_files(root: Path) -> list[Path]:
    """Return text files under *root*, excluding __pycache__."""
    if not root.is_dir():
        return []
    files = []
    for path in root.rglob("*"):
        if path.is_dir() and path.name == "__pycache__":
            continue
        if path.is_file() and path.suffix in {".py", ".yml", ".yaml", ".sh", ".ps1", ".md", ".json"}:
            files.append(path)
    return files


def check_text_file(
    path: Path,
    text: str,
    rules: list[tuple[str, re.Pattern]],
    *,
    original: str | None = None,
) -> list[str]:
    """Apply *rules* to *text*, returning human-readable violations.

    *original* is used for snippet extraction when *text* has been masked.
    """
    violations = []
    source = original if original is not None else text
    for name, pattern in rules:
        for match in pattern.finditer(text):
            line = text[: match.start()].count("\n") + 1
            snippet = source[match.start() : match.end()].replace("\n", " ")[:80]
            violations.append(f"  {path.relative_to(REPO_ROOT)}:{line}: {name}: {snippet!r}")
    return violations


def check_python_file(path: Path) -> list[str]:
    """Cross-platform Python specific checks."""
    if path in ALLOWED_FILES:
        return []

    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return []

    masked = _mask_spans(text, _canonical_gradlew_spans(text))

    rules: list[tuple[str, re.Pattern]] = [
        ("path separator replace", PATH_REPLACE_PATTERNS[0]),
        ("path separator replace", PATH_REPLACE_PATTERNS[1]),
        ("hardcoded drive letter", HARD_DRIVE_PATTERN),
        ("Powershell invocation", POWERSHELL_PATTERN),
        ("gradlew.bat only", GRADLEW_BAT_PATTERN),
        ("os.name branching", OS_NAME_PATTERN),
    ]

    return check_text_file(path, masked, rules, original=text)


def check_workflow_file(path: Path) -> list[str]:
    """Extra CI-specific checks for GitHub workflow YAML."""
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return []
    violations = []
    rel = path.relative_to(REPO_ROOT)

    # Require full-history checkout.
    if "actions/checkout" in text and "fetch-depth: 0" not in text:
        violations.append(f"  {rel}: checkout must use fetch-depth: 0")

    # Exact branch lock.
    branch_section = re.search(
        r'on:\s*\n(?:\s+\w+:\s*\n)*\s+push:\s*\n\s+branches:\s*\n((?:\s+-\s+.*\n)+)',
        text,
    )
    if branch_section:
        branches = re.findall(r'-\s+([\w\-/\.]+)', branch_section.group(1))
        if branches != ["main"]:
            violations.append(
                f"  {rel}: push branches must be exactly [main], got {branches}"
            )

    if "devin/a14-rom-intelligence-audit" in text:
        violations.append(f"  {rel}: stale branch devin/a14-rom-intelligence-audit is forbidden")

    # Forbidden commands / properties.
    forbidden = {
        "scripts/verify.ps1": re.compile(r'\bscripts/verify\.ps1\b'),
        "gradlew.bat": re.compile(r'\bgradlew\.bat\b'),
        "officialRelease=true": re.compile(r'(?:-P)?officialRelease\s*=\s*true\b'),
        "keystore property": re.compile(r'customiuizerA14KeystoreProperties|CUSTOMIUIZER_A14_KEYSTORE_PROPERTIES'),
        "keystore secret": re.compile(r'\b(KEYSTORE_|SIGNING_|KEY_PASSWORD|STORE_PASSWORD|secrets\.[A-Z_]*(?:SIGN|KEY|STORE))\b'),
        "r14 signing path": re.compile(r'r14[\\/]buildkey|\$?C:\\\\Users\\\\tv\\\\Documents\\\\buildkey'),
    }
    for name, pattern in forbidden.items():
        for match in pattern.finditer(text):
            line = text[: match.start()].count("\n") + 1
            violations.append(f"  {rel}:{line}: forbidden {name}")

    return violations


def main() -> int:
    """Run all portability checks and print a summary."""
    all_violations: list[str] = []

    for path in iter_text_files(TOOLS_DIR):
        all_violations.extend(check_python_file(path))

    # Scripts: PowerShell scripts are expected to be Windows-only; only their
    # presence in a workflow is forbidden, which is checked by check_workflow_file.
    for path in iter_text_files(SCRIPTS_DIR):
        if path.suffix == ".ps1":
            continue
        all_violations.extend(check_python_file(path))

    # Workflows: CI-specific checks.
    if WORKFLOWS_DIR.is_dir():
        for path in WORKFLOWS_DIR.iterdir():
            if path.is_file() and path.suffix in {".yml", ".yaml"}:
                all_violations.extend(check_workflow_file(path))

    if all_violations:
        print("CI portability violations:")
        for v in all_violations:
            print(v)
        return 1

    print("CI portability checks pass.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
