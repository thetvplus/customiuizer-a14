#!/usr/bin/env python3
"""Exit-code protocol and shared helpers for the A14 brutal test suite.

This module defines the contract between the runner, the apply-check scanner,
and every independent kill gate.  It also provides a safe way to render and
execute configured command templates without shell=True.
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
from enum import IntEnum
from pathlib import Path
from typing import Sequence


class ApplyExitCode(IntEnum):
    """Exit codes returned by apply-check commands (e.g. a14_contract scan)."""

    MUTATION_APPLIED = 0
    CANNOT_VERIFY = 2
    SCANNER_ERROR = 3
    MUTATION_NOT_APPLIED = 4


class KillExitCode(IntEnum):
    """Exit codes returned by independent kill-gate commands."""

    SURVIVED = 0
    INDEPENDENT_GATE_KILLED = 1
    CANNOT_VERIFY = 2
    GATE_ERROR = 3


# Human-readable status names used in runner output and reports.
MUTATION_NOT_APPLIED = "MUTATION_NOT_APPLIED"
MUTATION_APPLIED = "MUTATION_APPLIED"
INDEPENDENT_GATE_KILLED = "INDEPENDENT_GATE_KILLED"
SELF_DETECTION_ONLY = "SELF_DETECTION_ONLY"
SURVIVED = "SURVIVED"
CANNOT_VERIFY = "CANNOT_VERIFY"
GATE_ERROR = "GATE_ERROR"
CLEANUP_ERROR = "CLEANUP_ERROR"


# Validators for values that may be interpolated into a command template.
_ID_RE = re.compile(r"^[A-Za-z0-9_][A-Za-z0-9_.-]*$")


def validate_id(value: str, context: str = "identifier") -> str:
    """Return the value if it is a safe command identifier, otherwise raise."""
    if not value:
        raise ValueError(f"empty {context}")
    if value == "." or value == "..":
        raise ValueError(f"invalid {context} (path nav): {value!r}")
    if "/" in value or "\\" in value:
        raise ValueError(f"invalid {context} (path separator): {value!r}")
    if value.startswith("."):
        raise ValueError(f"invalid {context} (hidden/dot-prefixed): {value!r}")
    if any(ord(c) < 32 for c in value):
        raise ValueError(f"invalid {context} (control character): {value!r}")
    if not _ID_RE.fullmatch(value):
        raise ValueError(f"invalid {context}: {value!r}")
    return value


def validate_command_template(template: Sequence[str]) -> list[str]:
    """Ensure a configured command template is a non-empty argv list."""
    if not template:
        raise ValueError("command template is empty")
    if not all(isinstance(token, str) and token for token in template):
        raise ValueError("command template must contain only non-empty strings")
    return list(template)


def render_command(
    template: Sequence[str],
    name: str | None = None,
    mutator: str | None = None,
) -> list[str]:
    """Return a concrete argv list for a command template.

    Only {name} and {mutator} placeholders are supported.  Only values whose
    placeholder actually appears are validated and substituted.
    """
    rendered: list[str] = []
    for token in template:
        if "{" not in token:
            rendered.append(token)
            continue

        for placeholder, value, context in (
            ("{name}", name, "mutation name"),
            ("{mutator}", mutator, "mutator id"),
        ):
            if placeholder in token:
                if value is None:
                    raise ValueError(f"missing {context} for placeholder {placeholder!r}")
                token = token.replace(placeholder, validate_id(value, context))

        if "{" in token:
            raise ValueError(f"unresolved or unsupported placeholder in command template: {token!r}")
        rendered.append(token)
    return rendered


def run_command(
    argv: Sequence[str],
    cwd: Path,
    timeout: int,
    env: dict[str, str] | None = None,
) -> tuple[int, str]:
    """Execute a command without a shell and return (exit_code, combined_output)."""
    merged = os.environ.copy()
    if env:
        merged.update(env)
    proc = subprocess.run(
        list(argv),
        cwd=cwd,
        shell=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=timeout,
        env=merged,
    )
    return proc.returncode, (proc.stdout or "")


def classify_apply(exit_code: int) -> str:
    """Classify an apply-check exit code as a status constant."""
    if exit_code == ApplyExitCode.MUTATION_APPLIED:
        return MUTATION_APPLIED
    if exit_code == ApplyExitCode.CANNOT_VERIFY:
        return CANNOT_VERIFY
    if exit_code == ApplyExitCode.SCANNER_ERROR:
        return GATE_ERROR
    if exit_code == ApplyExitCode.MUTATION_NOT_APPLIED:
        return MUTATION_NOT_APPLIED
    # Any other non-zero code from an apply check is treated as an error.
    return GATE_ERROR


def classify_kill(exit_code: int) -> str:
    """Classify an independent kill-gate exit code as a status constant."""
    if exit_code == KillExitCode.SURVIVED:
        return SURVIVED
    if exit_code == KillExitCode.INDEPENDENT_GATE_KILLED:
        return INDEPENDENT_GATE_KILLED
    if exit_code == KillExitCode.CANNOT_VERIFY:
        return CANNOT_VERIFY
    if exit_code >= KillExitCode.GATE_ERROR:
        return GATE_ERROR
    # Negative or unexpected codes are also gate errors.
    return GATE_ERROR


# Exit codes returned by the main runner program.
class RunnerExitCode(IntEnum):
    OK = 0
    FAIL = 1
    CONFIG_ERROR = 2
