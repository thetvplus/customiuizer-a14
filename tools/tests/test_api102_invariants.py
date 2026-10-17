"""Tests for native API 102 lifecycle invariants."""

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


class Api102LifecycleTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mod = _load_check_invariants()

    def _fake_path(self, rel: str) -> Path:
        return SOURCE_ROOT / rel

    def test_native_setId_is_allowed_in_installers(self):
        findings = self.mod.check_api102_lifecycle(
            self._fake_path("tv/withaibuild/customiuizer/mods/Other.kt"),
            'fun install() = builder.setId("x")',
        )
        self.assertEqual([], findings)

    def test_replaceHook_forbidden(self):
        text = """
fun swap(handle: XposedInterface.HookHandle) = handle.replaceHook(hooker)
"""
        findings = self.mod.check_api102_lifecycle(self._fake_path("tv/withaibuild/customiuizer/mods/Other.kt"), text)
        self.assertEqual(1, len(findings))
        self.assertIn("replaceHook", findings[0].detail)

    def test_hotReload_params_forbidden(self):
        text = """
override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam) {}
override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {}
"""
        findings = self.mod.check_api102_lifecycle(self._fake_path("tv/withaibuild/customiuizer/mods/Other.kt"), text)
        self.assertEqual(2, len(findings))

    def test_getApiVersion_only_allowed_in_entry(self):
        text = """
fun callback() = getApiVersion()
"""
        findings = self.mod.check_api102_lifecycle(self._fake_path("tv/withaibuild/customiuizer/mods/Other.kt"), text)
        self.assertEqual(1, len(findings))

    def test_getApiVersion_allowed_in_mainModule(self):
        text = """
int api = getApiVersion();
"""
        findings = self.mod.check_api102_lifecycle(
            self._fake_path("tv/withaibuild/customiuizer/MainModule.java"), text
        )
        self.assertEqual(0, len(findings))

    def test_native_installation_has_no_api101_fallback_or_r8_barrier(self):
        root = SOURCE_ROOT / 'tv/withaibuild/customiuizer/mods/utils'
        self.assertFalse((root / 'XposedApiCapabilities.kt').exists())
        self.assertFalse((root / 'Api102HookBridge.kt').exists())
        native = (root / 'HookInstallation.kt').read_text(encoding='utf-8')
        self.assertIn('configured.setId(id)', native)
        setter = native.index('configured.setId(id)')
        intercept = native.index('identified.intercept(hook)')
        self.assertLess(setter, intercept)
        self.assertNotIn('catch', native[setter:intercept])
        rules = (REPO_ROOT / 'app/proguard-rules.pro').read_text(encoding='utf-8')
        self.assertNotIn('Api102HookBridge', rules)
        self.assertNotIn('HookInstallation', rules)

    def test_systemui_hook_id_does_not_enable_hot_reload(self):
        coordinator = (SOURCE_ROOT / 'tv/withaibuild/customiuizer/mods/utils/SystemUiBootstrapCoordinator.kt').read_text(encoding='utf-8')
        self.assertIn('ModuleHelper.findAndHookMethodWithId(', coordinator)
        self.assertIn('HookInstallation.STABLE_ID_SYSTEMUI_INIT', coordinator)
        metadata = (REPO_ROOT / 'app/src/main/resources/META-INF/xposed/module.prop').read_text(encoding='utf-8')
        self.assertIn('minApiVersion=102', metadata)
        self.assertNotIn('autoHotReload=true', metadata)


if __name__ == "__main__":
    unittest.main()
