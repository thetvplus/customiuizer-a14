"""Tests for the API 102 isolation invariants in tools/check-invariants.py."""

import importlib.util
import sys
import types
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
CHECK_INVARIANTS = REPO_ROOT / "tools" / "check-invariants.py"
SOURCE_ROOT = REPO_ROOT / "app" / "src" / "main" / "java"


def _load_check_invariants() -> types.ModuleType:
    spec = importlib.util.spec_from_file_location("check_invariants", CHECK_INVARIANTS)
    module = importlib.util.module_from_spec(spec)
    sys.modules["check_invariants"] = module
    if spec.loader is not None:
        spec.loader.exec_module(module)
    return module


class Api102IsolationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mod = _load_check_invariants()

    def _fake_path(self, rel: str) -> Path:
        return SOURCE_ROOT / rel

    def test_setId_only_allowed_in_bridge(self):
        text = """
package tv.withaibuild.customiuizer.mods.utils

fun other() {
    builder.setId("x")
}
"""
        findings = self.mod.check_api102_isolation(self._fake_path("tv/withaibuild/customiuizer/mods/Other.kt"), text)
        self.assertEqual(1, len(findings))
        self.assertIn("Api102HookBridge", findings[0].detail)

    def test_setId_allowed_in_bridge(self):
        text = """
package tv.withaibuild.customiuizer.mods.utils

fun bridge(builder: XposedInterface.HookBuilder) = builder.setId("x")
"""
        findings = self.mod.check_api102_isolation(
            self._fake_path("tv/withaibuild/customiuizer/mods/utils/Api102HookBridge.kt"), text
        )
        self.assertEqual(0, len(findings))

    def test_replaceHook_forbidden(self):
        text = """
fun swap(handle: XposedInterface.HookHandle) = handle.replaceHook(hooker)
"""
        findings = self.mod.check_api102_isolation(self._fake_path("tv/withaibuild/customiuizer/mods/Other.kt"), text)
        self.assertEqual(1, len(findings))
        self.assertIn("replaceHook", findings[0].detail)

    def test_hotReload_params_forbidden(self):
        text = """
override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam) {}
override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {}
"""
        findings = self.mod.check_api102_isolation(self._fake_path("tv/withaibuild/customiuizer/mods/Other.kt"), text)
        self.assertEqual(2, len(findings))

    def test_getApiVersion_only_allowed_in_entry(self):
        text = """
fun callback() = getApiVersion()
"""
        findings = self.mod.check_api102_isolation(self._fake_path("tv/withaibuild/customiuizer/mods/Other.kt"), text)
        self.assertEqual(1, len(findings))

    def test_getApiVersion_allowed_in_mainModule(self):
        text = """
XposedApiCapabilities.initialize(getApiVersion())
"""
        findings = self.mod.check_api102_isolation(
            self._fake_path("tv/withaibuild/customiuizer/MainModule.java"), text
        )
        self.assertEqual(0, len(findings))


if __name__ == "__main__":
    unittest.main()
