import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from tools import check_staged_snapshot as c


class StagedSnapshotV2Tests(unittest.TestCase):

    def _make_git_repo(self):
        td = tempfile.TemporaryDirectory()
        self.addCleanup(td.cleanup)
        root = Path(td.name)

        if not shutil.which("git"):
            self.skipTest("git not available")

        try:
            subprocess.run(["git", "init"], cwd=root, check=True, capture_output=True)
            subprocess.run(
                ["git", "-c", "user.email=test@test.com", "-c", "user.name=Test", "commit", "--allow-empty", "-m", "init"],
                cwd=root,
                check=True,
                capture_output=True,
            )
        except (subprocess.CalledProcessError, OSError) as e:
            self.skipTest(f"git init failed: {e}")

        old_root = c.REPO_ROOT

        def restore():
            c.REPO_ROOT = old_root

        self.addCleanup(restore)
        c.REPO_ROOT = root
        return root

    def _stage(self, root: Path, rel_path: str, content: str) -> None:
        p = root / rel_path
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content, encoding="utf-8")
        subprocess.run(["git", "add", rel_path], cwd=root, check=True, capture_output=True)

    def test_empty_staged(self):
        root = self._make_git_repo()
        errors = c.check_staged_snapshot()
        self.assertEqual(errors, ["No staged files"])

    def test_qualifying_requires_source(self):
        root = self._make_git_repo()
        self._stage(root, "tasks/active/T1.md", "# T1\n\n- Status: Active\n- Priority: P0\n\n## 目标\n\nX\n")
        errors = c.check_staged_snapshot(is_qualifying=True)
        self.assertTrue(any("state" in e.lower() for e in errors))

    def test_state_only_checkpoint(self):
        root = self._make_git_repo()
        self._stage(root, "ROADMAP.md", "# Roadmap\n\n## Now\n\n- T1\n")
        errors = c.check_staged_snapshot()
        self.assertTrue(any("state-only" in e.lower() for e in errors))

    def test_state_with_source_is_ok(self):
        root = self._make_git_repo()
        self._stage(root, "ROADMAP.md", "# Roadmap\n")
        self._stage(root, "tools/foo.py", "print(1)\n")
        errors = c.check_staged_snapshot()
        self.assertEqual(errors, [])

    def test_untracked_local_files_not_repo_facts(self):
        root = self._make_git_repo()
        # Create a state file plus an untracked doc-like file.
        self._stage(root, "tasks/active/T1.md", "# T1\n\n- Status: Active\n- Priority: P0\n\n## 目标\n\nX\n")
        untracked = root / "docs" / "untracked_evidence.md"
        untracked.parent.mkdir(parents=True, exist_ok=True)
        untracked.write_text("# evidence", encoding="utf-8")

        # staged_files must not include the untracked file.
        staged = c.staged_files()
        self.assertNotIn(untracked, staged)

        # The state-only checkpoint is still reported because the untracked file
        # must not count as a work product.
        errors = c.check_staged_snapshot()
        self.assertTrue(any("state-only" in e.lower() for e in errors))

    def test_commit_msg_warns_for_state_without_keyword(self):
        root = self._make_git_repo()
        self._stage(root, "ROADMAP.md", "# Roadmap\n")
        self._stage(root, "tools/foo.py", "print(1)\n")

        msg = root / "commit.msg"
        msg.write_text("add foo", encoding="utf-8")
        errors = c.check_staged_snapshot(commit_msg_path=str(msg))
        self.assertTrue(any("commit message" in e.lower() for e in errors))

    def test_commit_msg_ok_for_state_with_keyword(self):
        root = self._make_git_repo()
        self._stage(root, "ROADMAP.md", "# Roadmap\n")
        self._stage(root, "tools/foo.py", "print(1)\n")

        msg = root / "commit.msg"
        msg.write_text("docs: update roadmap", encoding="utf-8")
        errors = c.check_staged_snapshot(commit_msg_path=str(msg))
        self.assertEqual(errors, [])

    def test_staged_task_state_is_rejected(self):
        root = self._make_git_repo()
        self._stage(root, "TASK_STATE.md", "# Legacy state\n")
        errors = c.check_staged_snapshot()
        self.assertTrue(
            any("legacy control-plane file is forbidden under v2: TASK_STATE.md" in e for e in errors)
        )

    def test_staged_smart_operation_state_is_rejected(self):
        root = self._make_git_repo()
        self._stage(root, "SMART_OPERATION_STATE.md", "# Legacy state\n")
        errors = c.check_staged_snapshot()
        self.assertTrue(
            any("legacy control-plane file is forbidden under v2: SMART_OPERATION_STATE.md" in e for e in errors)
        )

    def test_staged_goal_is_rejected(self):
        root = self._make_git_repo()
        self._stage(root, "GOAL.md", "# Legacy goal\n")
        errors = c.check_staged_snapshot()
        self.assertTrue(
            any("legacy control-plane file is forbidden under v2: GOAL.md" in e for e in errors)
        )

    def test_legacy_control_file_with_source_change_still_rejected(self):
        root = self._make_git_repo()
        self._stage(root, "TASK_STATE.md", "# Legacy state\n")
        self._stage(root, "tools/foo.py", "print(1)\n")
        errors = c.check_staged_snapshot()
        self.assertTrue(
            any("legacy control-plane file is forbidden under v2: TASK_STATE.md" in e for e in errors)
        )

    def test_untracked_legacy_file_is_ignored(self):
        root = self._make_git_repo()
        legacy = root / "DEVIN_START_PROMPT.md"
        legacy.write_text("# start", encoding="utf-8")
        self._stage(root, "tools/foo.py", "print(1)\n")
        errors = c.check_staged_snapshot()
        self.assertEqual(errors, [])

    def test_v2_active_task_with_work_product_passes(self):
        root = self._make_git_repo()
        self._stage(root, "tasks/active/T1.md", "# T1\n\n- Status: Active\n")
        self._stage(root, "app/src/main/java/tv/withaibuild/customiuizer/Foo.kt", "class Foo\n")
        errors = c.check_staged_snapshot()
        self.assertEqual(errors, [])

    def test_roadmap_with_documentation_work_passes(self):
        root = self._make_git_repo()
        self._stage(root, "ROADMAP.md", "# Roadmap\n")
        self._stage(root, "docs/design.md", "# Design\n")
        errors = c.check_staged_snapshot()
        self.assertEqual(errors, [])


if __name__ == "__main__":
    unittest.main()
