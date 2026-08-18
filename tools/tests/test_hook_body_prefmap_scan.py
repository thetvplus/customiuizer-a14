"""Unit tests for the hook-body PrefMap scanner and freeze-and-reduce ceiling."""
from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(REPO_ROOT / "tools"))
import hook_body_prefmap_scan as scan


HOOK_BODY_SOURCE = """
package x
import tv.withaibuild.customiuizer.MainModule
object Sample {
    fun install() {
        ModuleHelper.findAndHookMethod("pkg.Cls", cl, "tick", object : MethodHook() {
            override fun after(param: MethodHookParam) {
                val n = MainModule.mPrefs.getInt("system_netspeedinterval", 4)
            }
        })
    }
}
"""

SNAPSHOT_SOURCE = """
package x
import tv.withaibuild.customiuizer.MainModule
object Sample {
    fun buildNetSpeedTextStyleSnapshot(prefs: PrefMap): Snapshot {
        return Snapshot(MainModule.mPrefs.getInt("system_netspeedinterval", 4))
    }
    fun install() {
        ModuleHelper.findAndHookMethod("pkg.Cls", cl, "tick", object : MethodHook() {
            override fun after(param: MethodHookParam) {
                apply(currentSnapshot)
            }
        })
    }
}
"""

KDOC_SOURCE = """
package x
/**
 * Does not touch [MainModule.mPrefs] on the hot path.
 */
object Sample {
    fun install() {
        ModuleHelper.findAndHookMethod("pkg.Cls", cl, "tick", object : MethodHook() {
            override fun after(param: MethodHookParam) {
                apply(currentSnapshot)
            }
        })
    }
}
"""


class ClassifyTest(unittest.TestCase):
    def test_method_hook_after_is_hook_body(self) -> None:
        report = scan.classify_file("Hot.kt", HOOK_BODY_SOURCE)
        self.assertEqual(1, report.hook_body)
        self.assertEqual("hook_body", report.hits[0].bucket)

    def test_snapshot_builder_is_cold(self) -> None:
        report = scan.classify_file("Cold.kt", SNAPSHOT_SOURCE)
        self.assertEqual(0, report.hook_body)
        self.assertEqual(1, report.cold_ok)

    def test_kdoc_mention_is_ignored(self) -> None:
        report = scan.classify_file("Docs.kt", KDOC_SOURCE)
        self.assertEqual(0, report.total)


class CeilingTest(unittest.TestCase):
    def test_increase_is_regression(self) -> None:
        errors = scan.ceiling_regressions({"Hot.kt": 2}, {"Hot.kt": 1})
        self.assertEqual(["Hot.kt: hook_body 2 exceeds ceiling 1"], errors)

    def test_decrease_passes(self) -> None:
        self.assertEqual([], scan.ceiling_regressions({"Hot.kt": 1}, {"Hot.kt": 2}))

    def test_new_file_has_zero_ceiling(self) -> None:
        errors = scan.ceiling_regressions({"New.kt": 1}, {})
        self.assertEqual(["New.kt: hook_body 1 exceeds ceiling 0"], errors)

    def test_migrated_file_may_leave_baseline(self) -> None:
        self.assertEqual([], scan.ceiling_regressions({}, {"Old.kt": 5}))


class CheckCommandTest(unittest.TestCase):
    def write_tree(self, root: Path, rel: str, text: str) -> None:
        path = root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def test_check_fails_when_hook_body_grows(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            mods = Path(tmp) / "mods"
            baseline = Path(tmp) / "baseline.json"
            self.write_tree(mods, "Hot.kt", HOOK_BODY_SOURCE)
            baseline.write_text(
                json.dumps({"schema": 1, "hook_body_total": 0, "hook_body_by_file": {}}),
                encoding="utf-8",
            )
            code = scan.main(["--check", "--mods-root", str(mods), "--baseline", str(baseline)])
            self.assertEqual(1, code)

    def test_check_passes_at_frozen_ceiling(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            mods = Path(tmp) / "mods"
            baseline = Path(tmp) / "baseline.json"
            self.write_tree(mods, "Hot.kt", HOOK_BODY_SOURCE)
            reports = scan.scan(mods)
            scan.write_baseline(reports, baseline)
            code = scan.main(["--check", "--mods-root", str(mods), "--baseline", str(baseline)])
            self.assertEqual(0, code)

    def test_current_repo_ceiling_holds(self) -> None:
        code = scan.main(["--check"])
        self.assertEqual(0, code, "repo baseline must match or exceed the current tree")


if __name__ == "__main__":
    unittest.main()
