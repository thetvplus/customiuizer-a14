"""Hot-path structure contract for charging-info hook decisions."""
from __future__ import annotations

import re
import sys
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
LOCKSCREEN_HOOKS = (
    REPO_ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "tv"
    / "withaibuild"
    / "customiuizer"
    / "mods"
    / "SystemLockScreenHooks.kt"
)


def _find_block_end(text: str, open_offset: int) -> int:
    """Mirror of source_hazard_scan.find_block_end using simple brace counting."""
    depth = 0
    in_string: str | None = None
    in_line_comment = False
    in_block_comment = False
    i = open_offset
    while i < len(text):
        ch = text[i]
        if in_line_comment:
            if ch == "\n":
                in_line_comment = False
            i += 1
            continue
        if in_block_comment:
            if ch == "*" and i + 1 < len(text) and text[i + 1] == "/":
                in_block_comment = False
                i += 2
                continue
            i += 1
            continue
        if in_string:
            if ch == in_string and text[i - 1] != "\\":
                in_string = None
            i += 1
            continue
        # Start of line comment
        if ch == "/" and i + 1 < len(text) and text[i + 1] == "/":
            in_line_comment = True
            i += 2
            continue
        # Start of block comment
        if ch == "/" and i + 1 < len(text) and text[i + 1] == "*":
            in_block_comment = True
            i += 2
            continue
        if ch in ('"', "'", '`'):
            in_string = ch
            i += 1
            continue
        if ch == '{':
            depth += 1
        elif ch == '}':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def _extract_method_body(text: str, method_name: str) -> str:
    pattern = re.compile(rf"(?:^|\n)\s*(?:@\w+\s+)*(?:\w+\s+)?fun\s+{re.escape(method_name)}\s*\(", re.S)
    match = pattern.search(text)
    if not match:
        raise AssertionError(f"{method_name} not found in SystemLockScreenHooks.kt")
    open_brace = text.find("{", match.start())
    if open_brace < 0:
        raise AssertionError(f"{method_name} has no body")
    close_brace = _find_block_end(text, open_brace)
    if close_brace < 0:
        raise AssertionError(f"could not find end of {method_name} body")
    return text[open_brace : close_brace + 1]


class ChargingInfoOutcomeContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.kt_text = LOCKSCREEN_HOOKS.read_text(encoding="utf-8")

    def test_computeChargingInfoReplacement_returns_nullable_string(self) -> None:
        # The signature spans multiple lines; find the declaration and the
        # return type separately.
        match = re.search(
            r"internal\s+fun\s+computeChargingInfoReplacement\s*\(.*?:\s*String\?",
            self.kt_text,
            re.S,
        )
        self.assertIsNotNone(
            match,
            "computeChargingInfoReplacement must return a nullable String, not a Pair or outcome wrapper",
        )

    def _hot_path_forbidden(self, body: str) -> list[str]:
        issues: list[str] = []
        if re.search(r"Pair\s*<", body):
            issues.append("found Pair< in hot path")
        if re.search(r"\bTriple\b", body):
            issues.append("found Triple in hot path")
        if re.search(r"\bdata\s+class\b", body):
            issues.append("found data class in hot path")
        if re.search(r"val\s+\(\s*\w+\s*,", body):
            issues.append("found destructuring result container in hot path")
        # The `to` infix operator creates a Pair; allow it inside strings/comments
        # but flag the operator form ` ... to ... `.
        for match in re.finditer(r'"(?:\\.|[^"\\])*"', body):
            pass
        # Strip string literals to avoid false positives.
        body_no_strings = re.sub(r'"(?:\\.|[^"\\])*"', '""', body)
        body_no_strings = re.sub(r"'(?:\\.|[^'\\])*'", "''", body_no_strings)
        body_no_strings = re.sub(r"`(?:\\.|[^`\\])*`", "``", body_no_strings)
        if re.search(r"\S\s+to\s+\S", body_no_strings):
            issues.append("found `to` infix operator in hot path")
        return issues

    def test_computeChargingInfoReplacement_no_outcome_container(self) -> None:
        body = _extract_method_body(self.kt_text, "computeChargingInfoReplacement")
        issues = self._hot_path_forbidden(body)
        self.assertEqual(issues, [])

    def test_chargingInfoHook_core_no_outcome_container(self) -> None:
        body = _extract_method_body(self.kt_text, "ChargingInfoHook")
        issues = self._hot_path_forbidden(body)
        self.assertEqual(issues, [])


if __name__ == "__main__":
    unittest.main()
