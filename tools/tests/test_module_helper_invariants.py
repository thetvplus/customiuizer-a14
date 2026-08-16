"""Tests for the ModuleHelper fatal-boundary invariants."""

import importlib.util
import sys
import types
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
CHECK_INVARIANTS = REPO_ROOT / "tools" / "check-invariants.py"
TARGET_PATH = (
    REPO_ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "tv"
    / "withaibuild"
    / "customiuizer"
    / "mods"
    / "utils"
    / "ModuleHelper.kt"
)


def _load_check_invariants() -> types.ModuleType:
    spec = importlib.util.spec_from_file_location("check_invariants_module_helper", CHECK_INVARIANTS)
    module = importlib.util.module_from_spec(spec)
    sys.modules["check_invariants_module_helper"] = module
    if spec.loader is not None:
        spec.loader.exec_module(module)
    return module


class ModuleHelperFatalBoundaryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mod = _load_check_invariants()

    def _findings(self, text: str) -> list:
        return self.mod.check_module_helper_fatal_boundaries(TARGET_PATH, text)

    def _details(self, findings) -> list:
        return [f.detail for f in findings]

    def test_oom_preceding_catch_does_not_protect_generic_catch(self):
        text = """
fun action() {
    try {
        doIt()
    } catch (oom: OutOfMemoryError) {
        throw oom
    } catch (t: Throwable) {
        null
    }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("t'" in d and "throw t" in d for d in details),
            f"details: {details}",
        )

    def test_generic_catch_with_unwrap_passes(self):
        text = """
fun action() {
    try {
        doIt()
    } catch (oom: OutOfMemoryError) {
        throw oom
    } catch (t: Throwable) {
        FatalErrors.unwrapAndRethrowIfFatal(t)
        null
    }
}
"""
        self.assertEqual([], self._findings(text))

    def test_generic_catch_with_unconditional_throw_passes(self):
        text = """
fun action() {
    try {
        doIt()
    } catch (t: Throwable) {
        throw t
    }
}
"""
        self.assertEqual([], self._findings(text))

    def test_unwrap_for_wrong_variable_fails(self):
        text = """
fun action() {
    try {
        doIt()
    } catch (t: Throwable) {
        FatalErrors.unwrapAndRethrowIfFatal(other)
    }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("t'" in d for d in details),
            f"details: {details}",
        )

    def test_unwrap_located_in_another_catch_fails(self):
        text = """
fun action() {
    try {
        doIt()
    } catch (t: Throwable) {
        null
    } catch (other: Throwable) {
        FatalErrors.unwrapAndRethrowIfFatal(t)
    }
}
"""
        details = self._details(self._findings(text))
        self.assertGreaterEqual(len(details), 1, f"details: {details}")
        self.assertTrue(
            any("t'" in d for d in details),
            f"details: {details}",
        )

    def test_wrapped_fatal_assignment_behavior_passes(self):
        text = """
fun action() {
    try {
        doIt()
    } catch (t: Throwable) {
        val toReport = FatalErrors.unwrapAndRethrowIfFatal(t)
        log(toReport)
    }
}
"""
        self.assertEqual([], self._findings(text))


if __name__ == "__main__":
    unittest.main()
