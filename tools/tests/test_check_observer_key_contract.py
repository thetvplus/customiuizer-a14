"""Unit tests for the observer key-contract static gate."""
from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

# Ensure the tools directory is on the path.
REPO_ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(REPO_ROOT / "tools"))
import check_observer_key_contract as gate


class ObserverKeyContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmpdir = Path(tempfile.mkdtemp())
        self.src_root = self.tmpdir / "app" / "src" / "main" / "java"
        self.src_root.mkdir(parents=True)
        # Patch module globals so check_file/scan_source work on the temp tree.
        self._orig_repo_root = gate.REPO_ROOT
        self._orig_source_root = gate.SOURCE_ROOT
        gate.REPO_ROOT = self.tmpdir
        gate.SOURCE_ROOT = self.src_root

    def tearDown(self) -> None:
        gate.REPO_ROOT = self._orig_repo_root
        gate.SOURCE_ROOT = self._orig_source_root

    def write_kt(self, rel_path: str, text: str) -> Path:
        path = self.src_root / rel_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return path

    def test_block_body_prefixed_key_fails(self) -> None:
        path = self.write_kt(
            "tv/foo/BlockObserver.kt",
            """
package tv.foo
object : ModuleHelper.PreferenceObserver {
    override fun onChange(key: String?) {
        if (key == "pref_key_system_charginginfo") return
    }
}
""",
        )
        issues = gate.check_file(path)
        self.assertEqual(len(issues), 1)
        self.assertIn("pref_key_", issues[0])

    def test_block_body_short_key_passes(self) -> None:
        path = self.write_kt(
            "tv/foo/BlockObserver.kt",
            """
package tv.foo
object : ModuleHelper.PreferenceObserver {
    override fun onChange(key: String?) {
        if (key == "system_charginginfo") return
    }
}
""",
        )
        issues = gate.check_file(path)
        self.assertEqual(issues, [])

    def test_expression_body_prefixed_key_fails(self) -> None:
        path = self.write_kt(
            "tv/foo/ExprObserver.kt",
            """
package tv.foo
object : ModuleHelper.PreferenceObserver {
    override fun onChange(key: String?) = ModuleHelper.guarded {
        if (key == "pref_key_system_charginginfo") return
    }
}
""",
        )
        issues = gate.check_file(path)
        self.assertEqual(len(issues), 1)
        self.assertIn("pref_key_", issues[0])

    def test_expression_body_short_key_passes(self) -> None:
        path = self.write_kt(
            "tv/foo/ExprObserver.kt",
            """
package tv.foo
object : ModuleHelper.PreferenceObserver {
    override fun onChange(key: String?) = ModuleHelper.guarded {
        if (key == "system_charginginfo") return
    }
}
""",
        )
        issues = gate.check_file(path)
        self.assertEqual(issues, [])

    def test_multiline_expression_body_checked(self) -> None:
        path = self.write_kt(
            "tv/foo/MultiLineObserver.kt",
            """
package tv.foo
object : ModuleHelper.PreferenceObserver {
    override fun onChange(name: String?) =
        someWrapper(name) {
            if (name == "pref_key_system_charginginfo") return
        }
}
""",
        )
        issues = gate.check_file(path)
        self.assertEqual(len(issues), 1)
        self.assertIn("pref_key_", issues[0])

    def test_simple_expression_body_with_prefixed_key_fails(self) -> None:
        path = self.write_kt(
            "tv/foo/SimpleExprObserver.kt",
            """
package tv.foo
object : PreferenceObserver {
    override fun onChange(key: String?) =
        key == "pref_key_system_example"

    fun anotherMethod() {
        println("later brace")
    }
}
""",
        )
        issues = gate.check_file(path)
        self.assertEqual(len(issues), 1)
        self.assertIn("pref_key_", issues[0])

    def test_simple_expression_body_with_short_key_passes(self) -> None:
        path = self.write_kt(
            "tv/foo/SimpleExprShortObserver.kt",
            """
package tv.foo
object : PreferenceObserver {
    override fun onChange(key: String?) =
        key == "system_example"

    fun anotherMethod() {
        println("later brace")
    }
}
""",
        )
        issues = gate.check_file(path)
        self.assertEqual(issues, [])

    def test_simple_expression_body_single_line_prefixed_key_fails(self) -> None:
        path = self.write_kt(
            "tv/foo/SingleLineExprObserver.kt",
            """
package tv.foo
object : PreferenceObserver {
    override fun onChange(key: String?) = key == "pref_key_system_example"

    fun anotherMethod() {
        println("later brace")
    }
}
""",
        )
        issues = gate.check_file(path)
        self.assertEqual(len(issues), 1)
        self.assertIn("pref_key_", issues[0])

    def test_simple_expression_body_single_line_short_key_passes(self) -> None:
        path = self.write_kt(
            "tv/foo/SingleLineExprShortObserver.kt",
            """
package tv.foo
object : PreferenceObserver {
    override fun onChange(key: String?) = key == "system_example"

    fun anotherMethod() {
        println("later brace")
    }
}
""",
        )
        issues = gate.check_file(path)
        self.assertEqual(issues, [])

    def test_non_observer_storage_key_does_not_flag(self) -> None:
        path = self.write_kt(
            "tv/foo/RegularClass.kt",
            """
package tv.foo
class RegularClass {
    fun doSomething() {
        val rawKey = "pref_key_system_charginginfo"
        println(rawKey)
    }
}
""",
        )
        issues = gate.check_file(path)
        self.assertEqual(issues, [])

    def test_excluded_canonicalization_files_do_not_flag(self) -> None:
        # Create a fake PreferenceKeys.kt in the excluded path with a pref_key_
        # string outside any observer; the gate must ignore the file.
        rel = "tv/withaibuild/customiuizer/utils/PreferenceKeys.kt"
        path = self.write_kt(
            rel,
            """
package tv.withaibuild.customiuizer.utils

const val RAW_KEY = "pref_key_system_charginginfo"

class RegularClass {
    override fun onChange(key: String?) {
        if (key == "pref_key_system_charginginfo") return
    }
}
""",
        )
        issues = gate.check_file(path)
        self.assertEqual(issues, [])

    def test_real_production_directory_scan_has_no_issues(self) -> None:
        # Use the actual source root without any temp files. Restore the module
        # globals for this scan so relative paths resolve against the real repo.
        gate.REPO_ROOT = self._orig_repo_root
        gate.SOURCE_ROOT = self._orig_source_root
        try:
            issues = gate.scan_source()
            self.assertEqual(issues, [])
        finally:
            gate.REPO_ROOT = self.tmpdir
            gate.SOURCE_ROOT = self.src_root


if __name__ == "__main__":
    unittest.main()
