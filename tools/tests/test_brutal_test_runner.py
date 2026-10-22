"""Tests for tools/brutal_test_runner.py hermeticity checks."""

import io
import os
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from contextlib import nullcontext
from pathlib import Path
from unittest.mock import patch

from tools import brutal_test_runner


class BehaviorMutationTest(unittest.TestCase):
    def run_cases(self, statuses):
        cases = [dict(name=name, mutator=name, apply_check=None, kill_gate="behavior",
                      required_independent=False, baseline_required=True) for name in ("first", "second")]
        cfg = {"mutations": cases, "kill_gates": {"behavior": ["python", "tools/verify.py", "behavior", "--tests", "example.RealTest"]}}
        with tempfile.TemporaryDirectory() as td, redirect_stdout(io.StringIO()):
            root = Path(td)
            with patch.object(brutal_test_runner, "detached_worktree", side_effect=lambda _: nullcontext(root)), \
                    patch.dict(brutal_test_runner.MUTATORS, first=lambda *_: None, second=lambda *_: None), \
                    patch.object(brutal_test_runner, "_mutator_modified_files", return_value=True), \
                    patch.object(brutal_test_runner, "_run_kill_gate", side_effect=statuses) as gate:
                code = brutal_test_runner.mutation_test(root, cfg, 30, None, ignore_minimum=True)
                return code, gate.call_count

    def test_one_clean_baseline_is_shared_by_the_same_behavior_command(self):
        self.assertEqual((0, 3), self.run_cases(["SURVIVED", "INDEPENDENT_GATE_KILLED", "INDEPENDENT_GATE_KILLED"]))

    def test_failed_baseline_is_not_a_kill_or_repeated_for_each_case(self):
        self.assertEqual((1, 1), self.run_cases(["GATE_ERROR"]))

    def test_optional_behavior_survivor_still_fails_the_selected_run(self):
        self.assertEqual((1, 3), self.run_cases(["SURVIVED", "SURVIVED", "INDEPENDENT_GATE_KILLED"]))


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

    def test_temporary_worktree_copies_only_sdk_and_propagates_body_failure(self):
        with tempfile.TemporaryDirectory() as td:
            root = self._make_repo(Path(td))
            sdk = (Path(td) / "android-sdk").as_posix()
            (root / "local.properties").write_text(f"sdk.dir={sdk}\nother.private.property=do-not-copy\n")
            failure = RuntimeError("mutator failed")
            with self.assertRaises(RuntimeError) as caught:
                with brutal_test_runner.detached_worktree(root) as work:
                    self.assertEqual(f"sdk.dir={sdk}\n", (work / "local.properties").read_text())
                    (work / "tracked.txt").write_text("changed")
                    raise failure
            self.assertIs(failure, caught.exception)
            self.assertFalse(work.parent.exists())
            self.assertEqual([root.resolve()], brutal_test_runner.active_worktrees(root))

    def test_determinism_preserves_prepared_sdk_and_rejects_modified_or_new_inputs(self):
        for suffix, expected in (
            ("", 0),
            ("; Path('local.properties').write_text('sdk.dir=/changed\\n')", 1),
            ("; Path('unexpected.txt').write_text('new')", 1),
            ("; Path('local.properties').unlink()", 1),
        ):
            with self.subTest(suffix=suffix), tempfile.TemporaryDirectory() as td:
                root = self._make_repo(Path(td))
                original = "sdk.dir=/prepared-sdk\n"
                (root / "local.properties").write_text(original, encoding="utf-8")
                command = [sys.executable, "-c",
                           "from pathlib import Path; Path('result.json').write_text('{}')" + suffix]
                cfg = {"determinism_command": command, "determinism_outputs": ["result.json"]}
                output = io.StringIO()
                with redirect_stdout(output):
                    code = brutal_test_runner.determinism(root, cfg, 30)
                self.assertEqual(expected, code, output.getvalue())
                self.assertEqual(original, (root / "local.properties").read_text(encoding="utf-8"))
                self.assertEqual([root.resolve()], brutal_test_runner.active_worktrees(root))

    def test_partial_git_removal_finishes_only_when_directory_and_entry_are_gone(self):
        real_run = subprocess.run
        real_rmtree = brutal_test_runner.shutil.rmtree
        attempts = 0
        def locked_then_removed(*args, **kwargs):
            nonlocal attempts
            attempts += 1
            if attempts == 1:
                raise PermissionError("build file is still locked")
            return real_rmtree(*args, **kwargs)
        def partial_remove(command, *args, **kwargs):
            result = real_run(command, *args, **kwargs)
            if command[:4] == ["git", "worktree", "remove", "--force"]:
                self.assertEqual(0, result.returncode)
                remaining = Path(command[-1])
                remaining.mkdir()
                (remaining / "locked-build-file").write_text("released")
                return subprocess.CompletedProcess(command, 1, "directory removal failed after unregister")
            return result
        with tempfile.TemporaryDirectory() as td:
            root = self._make_repo(Path(td))
            with patch.object(brutal_test_runner.subprocess, "run", side_effect=partial_remove), \
                    patch.object(brutal_test_runner.shutil, "rmtree", side_effect=locked_then_removed):
                with brutal_test_runner.detached_worktree(root) as work:
                    (work / "tracked.txt").write_text("changed")
            self.assertFalse(work.parent.exists())
            self.assertEqual(2, attempts)
            self.assertEqual([root.resolve()], brutal_test_runner.active_worktrees(root))

    def test_registered_worktree_after_cleanup_still_fails_closed(self):
        real_run = subprocess.run
        def refuse_remove(command, *args, **kwargs):
            if command[:4] == ["git", "worktree", "remove", "--force"]:
                return subprocess.CompletedProcess(command, 1, "worktree locked")
            return real_run(command, *args, **kwargs)
        with tempfile.TemporaryDirectory() as td:
            root = self._make_repo(Path(td))
            with patch.object(brutal_test_runner.subprocess, "run", side_effect=refuse_remove), \
                    patch.object(brutal_test_runner.time, "monotonic", side_effect=[0, 11]):
                with self.assertRaisesRegex(brutal_test_runner.CleanupError, "still listed"):
                    with brutal_test_runner.detached_worktree(root):
                        pass

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

    def test_failing_command_preserves_diagnostics(self):
        with tempfile.TemporaryDirectory() as td:
            root = self._make_repo(Path(td))
            command = [sys.executable, "-c", "print('specific regression detail'); raise SystemExit(7)"]
            code, output = self._run_hermeticity(root, command=command)
            self.assertEqual(1, code, output)
            self.assertIn("specific regression detail", output)
            self.assertIn("exit=7", output)
            self.assertIn("Hermeticity FAILED: command failed", output)

    def test_successful_command_reports_test_result_once(self):
        with tempfile.TemporaryDirectory() as td:
            root = self._make_repo(Path(td))
            command = [sys.executable, "-c", "print('actual test result')"]
            code, output = self._run_hermeticity(root, command=command)
            self.assertEqual(0, code, output)
            self.assertEqual(1, output.splitlines().count("actual test result"))
            self.assertIn("exit=0", output)


if __name__ == "__main__":
    unittest.main()
