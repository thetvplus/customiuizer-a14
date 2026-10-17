"""Tests for the EOL policy parser."""

import unittest
from pathlib import Path

import tools.eol_check as eol_check


class EolPolicyParserTest(unittest.TestCase):
    def test_index_lf_allowed(self):
        self.assertTrue(eol_check._is_index_eol_allowed("lf", ["text", "eol=lf"]))

    def test_index_mixed_not_allowed(self):
        self.assertFalse(eol_check._is_index_eol_allowed("mixed", ["text"]))

    def test_index_crlf_not_allowed(self):
        self.assertFalse(eol_check._is_index_eol_allowed("crlf", ["text"]))

    def test_binary_ignored(self):
        self.assertTrue(eol_check._is_index_eol_allowed("-text", ["-text"]))
        self.assertTrue(eol_check._is_index_eol_allowed("none", []))

    def test_worktree_lf_allowed(self):
        self.assertTrue(eol_check._is_eol_allowed("foo.kt", "lf", "lf", ["text", "eol=lf"]))

    def test_worktree_crlf_allowed_for_bat(self):
        self.assertTrue(eol_check._is_eol_allowed("foo.bat", "lf", "crlf", ["text", "eol=crlf"]))

    def test_worktree_crlf_not_allowed_for_kt(self):
        self.assertFalse(eol_check._is_eol_allowed("foo.kt", "lf", "crlf", ["text", "eol=lf"]))

    def test_worktree_mixed_not_allowed(self):
        self.assertFalse(eol_check._is_eol_allowed("foo.kt", "lf", "mixed", ["text"]))

    def test_binary_worktree_ignored(self):
        self.assertTrue(eol_check._is_eol_allowed("foo.jar", "-text", "-text", ["-text"]))

    def test_repo_root_is_path(self):
        self.assertTrue(Path(eol_check.REPO_ROOT).is_dir())


if __name__ == "__main__":
    unittest.main()
