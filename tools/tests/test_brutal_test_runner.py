"""Tests for tools/brutal_test_runner.py hermeticity checks."""

import io
import os
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

from tools import brutal_test_runner


class HermeticityTest(unittest.TestCase):
    def _make_repo(self, tmp: Path) -> Path:
        root = tmp / "repo"
        root.mkdir()
        self._run_git(root, ["init"])
        self._run_git(root, ["config", "user.email", "devin@local"])
        self._run_git(root, ["config", "user.name", "Devin"])
        tracked = root / "tracked.txt"
        tracked.write_text("initial\n", encoding="utf-8")
        self._run_git(root, ["add", "tracked.txt"])
        self._run_git(root, ["commit", "-m", "initial"])
        return root

    def _run_git(self, root: Path, args: list[str]) -> None:
        subprocess.run(
            ["git", *args],
            cwd=root,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=True,
        )

    def _run_hermeticity(self, root: Path, command: list[str] | None = None, baseline_dirty_ok: bool = False) -> tuple[int, str]:
        cfg = {"hermetic_commands": [command] if command else [[sys.executable, "-c", "pass"]]}
        f = io.StringIO()
        with redirect_stdout(f):
            code = brutal_test_runner.hermeticity(root, cfg, 30, baseline_dirty_ok=baseline_dirty_ok)
        return code, f.getvalue()

    def test_clean_baseline_passes(self):
        with tempfile.TemporaryDirectory() as td:
            root = self._make_repo(Path(td))
            code, output = self._run_hermeticity(root)
            self.assertEqual(0, code, output)
            self.assertIn("Hermeticity passed", output)

    def test_preexisting_tracked_modification_fails(self):
        with tempfile.TemporaryDirectory() as td:
            root = self._make_repo(Path(td))
            (root / "tracked.txt").write_text("modified\n", encoding="utf-8")
            code, output = self._run_hermeticity(root)
            self.assertEqual(1, code, output)
            self.assertIn("pre-existing tracked modifications", output)
            self.assertIn("tracked.txt", output)

    def test_preexisting_staged_modification_fails(self):
        with tempfile.TemporaryDirectory() as td:
            root = self._make_repo(Path(td))
            (root / "tracked.txt").write_text("modified\n", encoding="utf-8")
            self._run_git(root, ["add", "tracked.txt"])
            code, output = self._run_hermeticity(root)
            self.assertEqual(1, code, output)
            self.assertIn("pre-existing tracked modifications", output)
            self.assertIn("tracked.txt", output)

    def test_allowed_untracked_not_mistaken_for_tracked_drift(self):
        with tempfile.TemporaryDirectory() as td:
            root = self._make_repo(Path(td))
            build_dir = root / "build"
            build_dir.mkdir()
            (build_dir / "artifact.txt").write_text("generated\n", encoding="utf-8")
            code, output = self._run_hermeticity(root)
            self.assertEqual(0, code, output)
            self.assertNotIn("tracked modifications", output)
            self.assertIn("Hermeticity passed", output)

    def test_hermetic_command_writes_tracked_file_fails(self):
        with tempfile.TemporaryDirectory() as td:
            root = self._make_repo(Path(td))
            write_cmd = [
                sys.executable,
                "-c",
                "open('tracked.txt', 'w', encoding='utf-8').write('corrupted')",
            ]
            code, output = self._run_hermeticity(root, command=write_cmd)
            self.assertEqual(1, code, output)
            self.assertIn("tracked files changed by read-only tests", output)
            self.assertIn("tracked.txt", output)

    def test_baseline_dirty_ok_emits_warning_only(self):
        with tempfile.TemporaryDirectory() as td:
            root = self._make_repo(Path(td))
            (root / "tracked.txt").write_text("modified\n", encoding="utf-8")
            code, output = self._run_hermeticity(root, baseline_dirty_ok=True)
            self.assertEqual(0, code, output)
            self.assertIn("WARNING", output)
            self.assertIn("tracked.txt", output)


if __name__ == "__main__":
    unittest.main()
