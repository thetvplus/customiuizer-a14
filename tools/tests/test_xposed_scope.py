"""Tests for Xposed module metadata and scope configuration."""

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SCOPE_LIST = REPO_ROOT / "app" / "src" / "main" / "resources" / "META-INF" / "xposed" / "scope.list"


class XposedScopeTest(unittest.TestCase):
    def _load_scope(self) -> set[str]:
        self.assertTrue(SCOPE_LIST.is_file(), f"scope.list not found: {SCOPE_LIST}")
        return {line.strip() for line in SCOPE_LIST.read_text(encoding="utf-8").splitlines() if line.strip() and not line.startswith("#")}

    def test_scope_covers_system_server_and_required_hosts(self) -> None:
        # Entry-point wiring is checked by CurrentArchitectureTest. Scope admission
        # must remain mandatory even when a broken entry point is absent.
        scope = self._load_scope()
        required = {"system", "android", "com.android.systemui", "com.miui.home"}
        self.assertFalse(required - scope, f"Missing required scopes: {required - scope}")
