"""Unit tests for the main source cleanliness gate."""
from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(REPO_ROOT / "tools"))
import check_main_source_cleanliness as gate


class MainSourceCleanlinessTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmpdir = Path(tempfile.mkdtemp())
        self.main_root = self.tmpdir / "app" / "src" / "main"
        self.main_root.mkdir(parents=True)
        self._orig_repo_root = gate.REPO_ROOT
        self._orig_main_root = gate.MAIN_ROOT
        gate.REPO_ROOT = self.tmpdir
        gate.MAIN_ROOT = self.main_root

    def tearDown(self) -> None:
        gate.REPO_ROOT = self._orig_repo_root
        gate.MAIN_ROOT = self._orig_main_root

    def write_main(self, rel_path: str, text: str) -> Path:
        path = self.main_root / rel_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return path

    def test_allowed_open_with_assets_are_ignored(self) -> None:
        for name in ("test0.png", "test1.mp3", "test2.mp4", "test3.txt", "test4.zip"):
            path = self.write_main(f"assets/{name}", "binary-content")
            self.assertEqual(gate._scan_file(path), [], f"{name} must be allow-listed")

    def test_prefs_provider_legitimate_test_uri_passes(self) -> None:
        # PrefsProvider is no longer allow-listed wholesale; only its real Open With
        # URI and asset names are safe because they do not match the forbidden
        # test-only implementation patterns.
        path = self.write_main(
            "java/tv/withaibuild/customiuizer/PrefsProvider.kt",
            'val route = "test/*"\n',
        )
        self.assertEqual(gate._scan_file(path), [])

    def test_prefs_provider_reset_for_test_is_flagged(self) -> None:
        path = self.write_main(
            "java/tv/withaibuild/customiuizer/PrefsProvider.kt",
            "internal fun resetForTest() {}\n",
        )
        issues = gate._scan_file(path)
        self.assertEqual(len(issues), 1)
        self.assertIn("forbidden-test-symbol", issues[0])
        self.assertIn("resetForTest", issues[0])

    def test_prefs_provider_junit_import_is_flagged(self) -> None:
        path = self.write_main(
            "java/tv/withaibuild/customiuizer/PrefsProvider.kt",
            "import org.junit.Test\n",
        )
        issues = gate._scan_file(path)
        self.assertEqual(len(issues), 1)
        self.assertIn("test-dependency-reference", issues[0])

    def test_fortest_method_name_is_flagged(self) -> None:
        path = self.write_main(
            "java/tv/foo/Foo.kt",
            "internal fun resetForTest() {\n}\n",
        )
        issues = gate._scan_file(path)
        self.assertEqual(len(issues), 1)
        self.assertIn("forbidden-test-symbol", issues[0])
        self.assertIn("resetForTest", issues[0])

    def test_testonly_comment_is_flagged(self) -> None:
        path = self.write_main(
            "java/tv/foo/Foo.kt",
            "/** This is for tests only. */\ninternal fun foo() {}\n",
        )
        issues = gate._scan_file(path)
        self.assertEqual(len(issues), 1)
        self.assertIn("test-only-comment", issues[0])

    def test_visible_for_testing_is_flagged(self) -> None:
        path = self.write_main(
            "java/tv/foo/Foo.kt",
            "import androidx.annotation.VisibleForTesting\n"
            "@VisibleForTesting\n"
            "fun foo() {}\n",
        )
        issues = gate._scan_file(path)
        self.assertEqual(len(issues), 1)
        self.assertIn("visible-for-testing", issues[0])

    def test_junit_import_is_flagged(self) -> None:
        path = self.write_main(
            "java/tv/foo/Foo.kt",
            "import org.junit.Test\n",
        )
        issues = gate._scan_file(path)
        self.assertEqual(len(issues), 1)
        self.assertIn("test-dependency-reference", issues[0])

    def test_legitimate_test_word_is_not_flagged(self) -> None:
        # A preference key or URI containing the substring "test" is fine.
        path = self.write_main(
            "java/tv/foo/Foo.kt",
            'val key = "pref_key_system_cleanopenwith_test"\n',
        )
        self.assertEqual(gate._scan_file(path), [])

    def test_real_production_tree_has_no_issues(self) -> None:
        gate.REPO_ROOT = self._orig_repo_root
        gate.MAIN_ROOT = self._orig_main_root
        try:
            issues = gate._scan_files(gate._all_main_files())
            self.assertEqual(issues, [])
        finally:
            gate.REPO_ROOT = self.tmpdir
            gate.MAIN_ROOT = self.main_root


if __name__ == "__main__":
    unittest.main()
