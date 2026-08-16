#!/usr/bin/env python3
"""Check GOAL.md and LONG_HORIZON_CONSTITUTION.md consistency."""
from __future__ import annotations

import re
import sys
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

REPO_ROOT = Path(__file__).resolve().parents[1]
GOAL = REPO_ROOT / "GOAL.md"
CONSTITUTION = REPO_ROOT / "docs" / "governance" / "LONG_HORIZON_CONSTITUTION.md"


def check() -> list[str]:
    errors: list[str] = []
    if not GOAL.exists():
        errors.append("GOAL.md missing")
        return errors
    if not CONSTITUTION.exists():
        errors.append("docs/governance/LONG_HORIZON_CONSTITUTION.md missing")

    goal = GOAL.read_text(encoding="utf-8", errors="replace")

    required = {
        "tomthenpc/customiuizer-a14": "repository lock",
        "devin/a14-rom-intelligence-audit": "branch lock",
        "EXACT_LOCK": "exact lock mode",
        "ANDROID_14_ACTIVE_STABLE_REFERENCE": "A14 product role",
        "ACTIVE_HARDENING": "lifecycle state",
        "OutOfMemoryError": "fatal error class",
        "ThreadDeath": "fatal error class",
        "VirtualMachineError": "fatal error class",
        "no secret in repository": "secret invariant",
        "不得自行删除用户功能": "feature deletion guard",
        "Android 15": "future version boundary",
        "HyperOS 2": "future version boundary",
        "LONG_HORIZON_CONSTITUTION.md": "constitution reference",
    }

    for needle, desc in required.items():
        if needle not in goal:
            errors.append(f"GOAL.md missing {desc}: {needle}")

    lifecycle_states = ["ACTIVE_HARDENING", "RELEASE_CANDIDATE", "STABLE", "LTS", "SECURITY_ONLY",
                        "EXTERNAL_VALIDATION_REQUIRED", "ARCHIVE_READY", "ARCHIVED"]
    found_states = [s for s in lifecycle_states if s in goal]
    if len(found_states) != len(lifecycle_states):
        missing = set(lifecycle_states) - set(found_states)
        errors.append(f"GOAL.md missing lifecycle states: {sorted(missing)}")

    # Check that future version expansion requires new repository and is not claimed.
    if re.search(r"(Android 15|HyperOS 2).*直接扩展", goal, re.IGNORECASE):
        errors.append("GOAL.md may claim direct Android 15/HyperOS 2 expansion")

    return errors


def main() -> int:
    errors = check()
    if errors:
        print("GOAL CONSTITUTION VIOLATIONS:")
        for err in errors:
            print(f"  - {err}")
        return 1
    print("Goal constitution checks pass.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
