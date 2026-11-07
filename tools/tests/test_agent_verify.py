import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools import agent_verify as gate


class AgentVerifyTest(unittest.TestCase):
    def test_deck_routes_behavior_and_keeps_device_pending(self):
        result = gate.plan(gate.ROOT, [gate.SOURCE + "java/tv/withaibuild/customiuizer/mods/recents/DeckMotion.kt"])
        command = result["host_commands"][0]
        self.assertIn("tv.withaibuild.customiuizer.mods.recents.DeckContinuityTest", command)
        self.assertEqual(["deck-open-return"], result["device_scenarios"])
        self.assertTrue(any("Runtime" in risk for risk in result["pending"]))

    def test_shared_runtime_and_deleted_tests_use_full(self):
        for path in (gate.SOURCE + "java/shared.kt", gate.TEST_ROOT + "MissingTest.kt", gate.TEST_ROOT + "TestSupport.kt"):
            self.assertEqual(["python", "tools/verify.py", "full"], gate.plan(gate.ROOT, [path])["host_commands"][0])

    def test_empty_selection_is_not_a_host_pass(self):
        with patch.object(gate, "task_paths", return_value=[]):
            self.assertEqual(2, gate.main(["host"]))

    def test_missing_tests_fail_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(ValueError, "No current behavior tests"):
                gate.plan(Path(tmp), [gate.SOURCE + "java/mods/recents/Deck.kt"])

    def test_diff_includes_committed_staged_unstaged_and_untracked(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            def git(*args):
                return subprocess.check_output(["git", *args], cwd=root, text=True).strip()
            git("init", "-q")
            git("config", "user.name", "Fixture")
            git("config", "user.email", "fixture@example.invalid")
            (root / "tracked").write_text("initial")
            git("add", ".")
            git("commit", "-qm", "base")
            base = git("rev-parse", "HEAD")
            (root / "committed").write_text("new")
            git("add", ".")
            git("commit", "-qm", "task")
            (root / "staged").write_text("new")
            git("add", "staged")
            (root / "tracked").write_text("edited")
            (root / "untracked").write_text("new")
            self.assertEqual(["committed", "staged", "tracked", "untracked"], gate.task_paths(root, base))


if __name__ == "__main__":
    unittest.main()
