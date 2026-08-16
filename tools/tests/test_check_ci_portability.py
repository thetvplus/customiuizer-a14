#!/usr/bin/env python3
"""Tests for tools/check_ci_portability.py."""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
REPO_CHECKER = REPO_ROOT / "tools" / "check_ci_portability.py"


class CIPortabilityCheckerTest(unittest.TestCase):
    """End-to-end tests for the CI portability checker."""

    def run_checker(
        self,
        cwd: Path,
        checker: Path = REPO_CHECKER,
    ) -> subprocess.CompletedProcess:
        env = os.environ.copy()
        env["PYTHONPATH"] = str(cwd / "tools")
        return subprocess.run(
            [sys.executable, str(checker)],
            cwd=cwd,
            env=env,
            capture_output=True,
            text=True,
        )

    def make_fake_repo(self, tmp: str) -> Path:
        """Create a minimal fake repo tree with the checker copied in."""
        root = Path(tmp)
        (root / "tools").mkdir()
        (root / "tools" / "tests").mkdir()
        (root / ".github" / "workflows").mkdir(parents=True)
        shutil.copy(REPO_CHECKER, root / "tools" / "check_ci_portability.py")
        (root / "tools" / "tests" / "test_check_ci_portability.py").touch()
        return root

    def test_self_passes(self):
        result = self.run_checker(REPO_ROOT, REPO_CHECKER)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("CI portability checks pass.", result.stdout)

    def test_hardcoded_windows_drive_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_fake_repo(tmp)
            (root / "tools" / "bad.py").write_text(
                'path = "C:/Users/tv/project"\n', encoding="utf-8"
            )
            result = self.run_checker(root, root / "tools" / "check_ci_portability.py")
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            self.assertIn("hardcoded drive letter", result.stdout)

    def test_direct_gradlew_bat_subprocess_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_fake_repo(tmp)
            (root / "tools" / "bad.py").write_text(
                'import subprocess\nsubprocess.run(["gradlew.bat"])\n',
                encoding="utf-8",
            )
            result = self.run_checker(root, root / "tools" / "check_ci_portability.py")
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            self.assertIn("gradlew.bat only", result.stdout)

    def test_path_replace_separator_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_fake_repo(tmp)
            (root / "tools" / "bad.py").write_text(
                'rel = "foo/bar"\nwindows = rel.replace("/", "\\\\")\n',
                encoding="utf-8",
            )
            result = self.run_checker(root, root / "tools" / "check_ci_portability.py")
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            self.assertIn("path separator replace", result.stdout)

    def test_canonical_cross_platform_wrapper_selection_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_fake_repo(tmp)
            (root / "tools" / "compile.py").write_text(
                'import sys\n'
                'GRADLEW = "gradlew.bat" if sys.platform == "win32" else "gradlew"\n',
                encoding="utf-8",
            )
            result = self.run_checker(root, root / "tools" / "check_ci_portability.py")
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_verify_py_cross_platform_selection_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_fake_repo(tmp)
            (root / "tools" / "verify.py").write_text(
                'import os\n'
                'GRADLEW = "gradlew.bat" if os.name == "nt" else "gradlew"\n',
                encoding="utf-8",
            )
            result = self.run_checker(root, root / "tools" / "check_ci_portability.py")
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_build_debug_apk_cross_platform_selection_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_fake_repo(tmp)
            (root / "tools" / "build_debug_apk.py").write_text(
                'import os\n'
                'REPO_ROOT = "/fake"\n'
                'GRADLEW = "gradlew.bat" if os.name == "nt" else "gradlew"\n'
                'GRADLEW_PATH = REPO_ROOT / GRADLEW\n',
                encoding="utf-8",
            )
            result = self.run_checker(root, root / "tools" / "check_ci_portability.py")
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_incomplete_windows_only_branch_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_fake_repo(tmp)
            (root / "tools" / "bad.py").write_text(
                'import os\nif os.name == "nt":\n    GRADLEW = "gradlew.bat"\n',
                encoding="utf-8",
            )
            result = self.run_checker(root, root / "tools" / "check_ci_portability.py")
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            self.assertIn("os.name branching", result.stdout)

    def test_workflow_without_fetch_depth_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_fake_repo(tmp)
            (root / ".github" / "workflows" / "fast.yml").write_text(
                textwrap.dedent(
                    """
                    on:
                      push:
                        branches:
                          - devin/a14-rom-intelligence-audit
                    jobs:
                      fast:
                        steps:
                          - uses: actions/checkout@v4
                    """
                ).strip()
                + "\n",
                encoding="utf-8",
            )
            result = self.run_checker(root, root / "tools" / "check_ci_portability.py")
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            self.assertIn("fetch-depth: 0", result.stdout)

    def test_workflow_with_official_release_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_fake_repo(tmp)
            (root / ".github" / "workflows" / "fast.yml").write_text(
                textwrap.dedent(
                    """
                    on:
                      push:
                        branches:
                          - devin/a14-rom-intelligence-audit
                    jobs:
                      fast:
                        steps:
                          - uses: actions/checkout@v4
                            with:
                              fetch-depth: 0
                          - run: ./gradlew :app:assembleRelease -PofficialRelease=true
                    """
                ).strip()
                + "\n",
                encoding="utf-8",
            )
            result = self.run_checker(root, root / "tools" / "check_ci_portability.py")
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            self.assertIn("officialRelease=true", result.stdout)

    def test_workflow_wildcard_branch_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_fake_repo(tmp)
            (root / ".github" / "workflows" / "fast.yml").write_text(
                textwrap.dedent(
                    """
                    on:
                      push:
                        branches:
                          - '*'
                    jobs:
                      fast:
                        steps:
                          - uses: actions/checkout@v4
                            with:
                              fetch-depth: 0
                    """
                ).strip()
                + "\n",
                encoding="utf-8",
            )
            result = self.run_checker(root, root / "tools" / "check_ci_portability.py")
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            self.assertIn("push branches must be exactly", result.stdout)


if __name__ == "__main__":
    unittest.main()
