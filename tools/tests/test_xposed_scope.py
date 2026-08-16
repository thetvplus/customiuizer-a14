"""Tests for Xposed module metadata and scope configuration."""

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SCOPE_LIST = REPO_ROOT / "app" / "src" / "main" / "resources" / "META-INF" / "xposed" / "scope.list"
MAIN_MODULE = REPO_ROOT / "app" / "src" / "main" / "java" / "tv" / "withaibuild" / "customiuizer" / "MainModule.java"


class XposedScopeTest(unittest.TestCase):
    def _load_scope(self) -> set[str]:
        self.assertTrue(SCOPE_LIST.is_file(), f"scope.list not found: {SCOPE_LIST}")
        return {line.strip() for line in SCOPE_LIST.read_text(encoding="utf-8").splitlines() if line.strip() and not line.startswith("#")}

    def _load_main_module(self) -> str:
        self.assertTrue(MAIN_MODULE.is_file(), f"MainModule.java not found: {MAIN_MODULE}")
        return MAIN_MODULE.read_text(encoding="utf-8")

    def test_scope_list_contains_system(self) -> None:
        scope = self._load_scope()
        self.assertIn("system", scope, "scope.list must include 'system' for system_server")

    def test_scope_list_contains_android(self) -> None:
        scope = self._load_scope()
        self.assertIn("android", scope, "scope.list must include 'android' for android system ui processes")

    def test_scope_list_contains_systemui_and_launcher(self) -> None:
        scope = self._load_scope()
        self.assertIn("com.android.systemui", scope)
        self.assertIn("com.miui.home", scope)

    def test_system_server_requires_system_scope(self) -> None:
        main = self._load_main_module()
        if "onSystemServerStarting" not in main:
            self.skipTest("MainModule does not implement onSystemServerStarting")
        scope = self._load_scope()
        self.assertIn("system", scope, "MainModule.onSystemServerStarting requires 'system' in scope.list")
