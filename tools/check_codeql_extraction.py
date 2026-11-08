"""Require actual production Kotlin/Java extraction, independently of findings."""
from __future__ import annotations

import argparse
import json
import math
import subprocess
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]


def validate(report: dict, expected: set[str]) -> dict:
    if not any(path.endswith(".kt") for path in expected):
        raise ValueError("No production Kotlin source inventory")
    runs = report.get("runs", [])
    if len(runs) != 1:
        raise ValueError("Expected one Java/Kotlin SARIF run")
    run = runs[0]
    invocations = run.get("invocations", [])
    if not invocations or any(i.get("executionSuccessful") is False for i in invocations):
        raise ValueError("CodeQL execution failed or has no execution evidence")
    extracted = {
        unquote(location["physicalLocation"]["artifactLocation"]["uri"])
        for invocation in invocations
        for note in invocation.get("toolExecutionNotifications", [])
        if note.get("descriptor", {}).get("id") == "java/diagnostics/successfully-extracted-files"
        for location in note.get("locations", [])
    }
    missing = expected - extracted
    if missing:
        raise ValueError(f"Production source extraction missing {len(missing)} file(s): {sorted(missing)[:10]}")
    kotlin_lines = next((metric.get("value")
                         for metric in run.get("properties", {}).get("metricResults", [])
                         if metric.get("ruleId") == "java/summary/lines-of-code-kotlin"), None)
    if (not isinstance(kotlin_lines, (int, float)) or isinstance(kotlin_lines, bool)
            or not math.isfinite(kotlin_lines) or kotlin_lines <= 0):
        raise ValueError("Missing or empty Kotlin analysis metric")
    return {
        "production_kotlin_files": sum(path.endswith(".kt") for path in expected),
        "production_java_files": sum(path.endswith(".java") for path in expected),
        "kotlin_lines": kotlin_lines,
        "findings": len(run.get("results", [])),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("sarif", type=Path)
    args = parser.parse_args()
    try:
        paths = subprocess.check_output(
            ["git", "ls-files", "-z", "app/src/main"], cwd=ROOT,
        ).decode("utf-8").split("\0")
        expected = {path for path in paths if path.endswith((".kt", ".java"))}
        print(json.dumps(validate(json.loads(args.sarif.read_text(encoding="utf-8-sig")), expected)))
    except (OSError, ValueError, KeyError, TypeError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"CodeQL extraction check failed: {error}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
