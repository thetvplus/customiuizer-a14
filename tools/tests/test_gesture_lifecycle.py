"""Gesture Hook wiring and owner-release source gates."""

import importlib.util
import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent


def source(rel: str) -> str:
    path = REPO_ROOT / rel
    if not path.is_file():
        raise FileNotFoundError(f"Missing source: {path}")
    return path.read_text(encoding="utf-8", errors="replace")


def find_hook_call(text: str, class_name: str, method_name: str) -> bool:
    """Return True if the file hooks the given class+method with findAndHookMethod."""
    pattern = re.compile(
        r"findAndHookMethod\s*\(\s*"
        + re.escape(f'"{class_name}"')
        + r"\s*,(?:[^\"]|\"[^\"]*\")*?"
        + re.escape(f'"{method_name}"'),
        re.DOTALL,
    )
    return bool(pattern.search(text))


class GestureLifecycleInventoryTest(unittest.TestCase):
    """Static source invariants for gesture owner lifecycle."""

    def test_phone_status_bar_view_attach_has_detach(self) -> None:
        """Status-bar gesture hooks attach and detach PhoneStatusBarView."""
        text = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt")
        self.assertTrue(
            find_hook_call(text, "com.android.systemui.statusbar.phone.PhoneStatusBarView", "onAttachedToWindow"),
            "PhoneStatusBarView onAttachedToWindow hook missing",
        )
        self.assertTrue(
            find_hook_call(text, "com.android.systemui.statusbar.phone.PhoneStatusBarView", "onDetachedFromWindow"),
            "PhoneStatusBarView onDetachedFromWindow hook missing",
        )

    def test_status_bar_machine_prepare_has_corresponding_clear(self) -> None:
        """statusBarMachine.prepare(ownerId, view) is paired with statusBarMachine.clear(ownerId)."""
        text = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt")
        owner_expr = r"System\.identityHashCode\s*\(\s*thisObject\s*\)"

        prepare = re.search(
            r"statusBarMachine\.prepare\s*\(\s*" + owner_expr + r"\s*,\s*thisObject\s*\)",
            text,
        )
        clear = re.search(
            r"statusBarMachine\.clear\s*\(\s*" + owner_expr + r"\s*\)",
            text,
        )

        self.assertIsNotNone(prepare, "statusBarMachine.prepare(System.identityHashCode(thisObject), thisObject) missing")
        self.assertIsNotNone(clear, "statusBarMachine.clear(System.identityHashCode(thisObject)) missing")


SOURCE_ROOT = REPO_ROOT / "app/src/main/java"

CONTROL_CENTER_PATH = SOURCE_ROOT / "tv/withaibuild/customiuizer/mods/utils/ControlCenterPluginRuntime.kt"

STATUS_BAR_PATH = SOURCE_ROOT / "tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt"

HOOK_INSTALL = (
    'ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.windowview.ControlCenterWindowViewImpl", '
    'classLoader, "destroy", destroyHook)'
)

VALID_CALLBACK = """
val destroyHook = object : MethodHook() {
    override fun before(param: BeforeHookCallback) {
        if (!lease.active) return
        val thisObject = param.getThisObject() as? View ?: return
        ModuleHelper.guarded {
            controlCenterMachine.clear(System.identityHashCode(thisObject))
        }
    }
}
"""

class GestureOwnerCleanupInvariantsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location("gesture_cleanup_invariants", REPO_ROOT / "tools/check-invariants.py")
        cls.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.mod)

    def check_control_center(self, text):
        return self.mod.check_gesture_detach_cleanup(CONTROL_CENTER_PATH, text)

    def assert_rejected(self, text):
        findings = self.check_control_center(text)
        self.assertEqual(1, len(findings))
        self.assertEqual("gesture-detach-cleanup", findings[0].rule)

    def test_current_production_source_has_owner_release(self):
        self.assertEqual([], self.check_control_center(CONTROL_CENTER_PATH.read_text(encoding="utf-8")))

    def test_prepared_batch_must_register_the_actual_owner_cleanup(self):
        text = CONTROL_CENTER_PATH.read_text(encoding="utf-8")
        mutations = (
            ('destroy to hooks.destroy', 'destroy to hooks.onAttachedToWindow'),
            ('destroy = destroyHook', 'destroy = onAttachedHook'),
            ('method("destroy", Void.TYPE)', 'method("onDetachedFromWindow", Void.TYPE)'),
            ('PreparedHookBatch().install(entries)', 'PreparedHookBatch().install(emptyList())'),
            ('controlCenterMachine.clear(System.identityHashCode(thisObject))', 'controlCenterMachine.clear()'),
            ('controlCenterMachine.clear(System.identityHashCode(thisObject))', 'controlCenterMachine.clear(System.identityHashCode(otherView))'),
        )
        for before, after in mutations:
            with self.subTest(before=before):
                self.assertEqual(1, text.count(before))
                self.assert_rejected(text.replace(before, after))

    def test_registered_destroy_clears_its_callback_owner(self):
        self.assertEqual([], self.check_control_center(VALID_CALLBACK + HOOK_INSTALL))

    def test_destroy_without_clear_is_rejected(self):
        self.assert_rejected(VALID_CALLBACK.replace("controlCenterMachine.clear(System.identityHashCode(thisObject))", "") + HOOK_INSTALL)

    def test_clear_of_another_owner_is_rejected(self):
        self.assert_rejected(VALID_CALLBACK.replace("identityHashCode(thisObject)", "identityHashCode(otherView)") + HOOK_INSTALL)

    def test_whole_machine_clear_is_rejected(self):
        self.assert_rejected(VALID_CALLBACK.replace("clear(System.identityHashCode(thisObject))", "clear()") + HOOK_INSTALL)

    def test_clear_in_an_unregistered_callback_is_rejected(self):
        self.assert_rejected(VALID_CALLBACK + HOOK_INSTALL.replace("destroyHook)", "anotherHook)"))

    def test_clear_outside_destroy_callback_is_rejected(self):
        without_clear = VALID_CALLBACK.replace("controlCenterMachine.clear(System.identityHashCode(thisObject))", "")
        self.assert_rejected(without_clear + HOOK_INSTALL + "\ncontrolCenterMachine.clear(System.identityHashCode(thisObject))")

    def test_obsolete_inherited_detach_target_is_rejected(self):
        self.assert_rejected(VALID_CALLBACK + HOOK_INSTALL.replace('"destroy"', '"onDetachedFromWindow"'))

    def test_additional_obsolete_detach_target_is_rejected(self):
        self.assert_rejected(VALID_CALLBACK + HOOK_INSTALL + "\n" + HOOK_INSTALL.replace('"destroy"', '"onDetachedFromWindow"'))

    def test_commented_clear_cannot_satisfy_release(self):
        self.assert_rejected(VALID_CALLBACK.replace("controlCenterMachine.clear", "// controlCenterMachine.clear") + HOOK_INSTALL)

    def test_status_bar_still_requires_detach(self):
        valid = '"onDetachedFromWindow" statusBarMachine.clear(System.identityHashCode(thisObject))'
        self.assertEqual([], self.mod.check_gesture_detach_cleanup(STATUS_BAR_PATH, valid))
        findings = self.mod.check_gesture_detach_cleanup(STATUS_BAR_PATH, valid.replace('"onDetachedFromWindow"', '"destroy"'))
        self.assertEqual(1, len(findings))


if __name__ == "__main__":
    unittest.main()
