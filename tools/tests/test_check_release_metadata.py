import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools import check_release_metadata as c


class ReleaseMetadataTests(unittest.TestCase):
    def test_current_tree_passes(self):
        errors = c.check(require_tag=False)
        self.assertEqual(errors, [], errors)

    def test_gradle_version_is_r14(self):
        code, name = c.parse_gradle_version()
        self.assertGreaterEqual(code, 199)
        self.assertTrue(name.startswith("r14."))

    def check_fixture(self, changelog: str) -> list[str]:
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            for name in ("README.md", "README_EN.md"):
                (root / name).write_text("Current release: r14.22.7", encoding="utf-8")
            for name in ("CHANGELOG.md", "CHANGELOG_CN.md"):
                (root / name).write_text(changelog, encoding="utf-8")
            with patch.object(c, "REPO_ROOT", root), patch.object(c, "parse_gradle_version", return_value=(230, "r14.22.7")):
                return c.check()

    def test_current_section_and_older_versions_pass(self):
        self.assertEqual([], self.check_fixture(
            "## r14.22.7 — 2026-10-09\nversionCode 230.\nChanges.\n"
            "## r14.21.9\nversionCode 218.\n"))

    def test_stale_first_release_cannot_pass_by_mentioning_current_version_later(self):
        errors = self.check_fixture(
            "## r14.22.6\nversionCode 229.\n"
            "## r14.22.7\nversionCode 230.\n")
        self.assertTrue(any("first release heading" in error for error in errors))

    def test_old_section_code_does_not_validate_current_release(self):
        errors = self.check_fixture(
            "## r14.22.7\nversionCode 229.\n"
            "## r14.21.9\nversionCode 230.\n")
        self.assertTrue(any("must declare versionCode" in error for error in errors))

    def test_incidental_number_is_not_a_version_code_declaration(self):
        errors = self.check_fixture("## r14.22.7\n230 tests pass.\n")
        self.assertTrue(any("must declare versionCode" in error for error in errors))

    def test_duplicate_current_headings_fail(self):
        errors = self.check_fixture("## r14.22.7\nversionCode 230.\n" * 2)
        self.assertTrue(any("exactly one heading" in error for error in errors))

    def test_multiple_version_code_declarations_fail(self):
        errors = self.check_fixture("## r14.22.7\nversionCode 230; versionCode 229.\n")
        self.assertTrue(any("exactly once" in error for error in errors))

    def test_unrelated_heading_ends_current_release_section(self):
        errors = self.check_fixture("## r14.22.7\nChanges.\n## Build notes\nversionCode 230.\n")
        self.assertTrue(any("must declare versionCode" in error for error in errors))
