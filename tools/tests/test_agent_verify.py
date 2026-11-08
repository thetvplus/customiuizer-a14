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
        self.assertIn("deck-open-return", result["device_scenarios"])
        self.assertIn("island-takeover", result["device_scenarios"])
        self.assertTrue(any("Runtime" in risk for risk in result["pending"]))

    def test_shared_runtime_and_deleted_tests_use_full(self):
        for path in (gate.SOURCE + "java/shared.kt", gate.TEST_ROOT + "MissingTest.kt", gate.TEST_ROOT + "TestSupport.kt"):
            self.assertEqual(["python", "tools/verify.py", "full"], gate.plan(gate.ROOT, [path])["host_commands"][0])

    def test_empty_selection_is_not_a_host_pass(self):
        with patch.object(gate, "task_paths", return_value=[]):
            self.assertEqual(2, gate.main(["host"]))

    def test_settings_generation_and_rom_matrix_use_existing_consumers(self):
        result = gate.plan(gate.ROOT, ["app/settings/prefs_launcher.json", "docs/rom-intelligence/A14_PROCESS_MATRIX.csv"])
        self.assertEqual(["python", "tools/verify.py", "fast"], result["host_commands"][0][:3])
        self.assertIn(["python", "tools/generate_feature_map.py", "--check"], result["host_commands"])
        self.assertTrue(any("unittest" in command for command in result["host_commands"]))

    def test_unimplemented_gesture_is_a_pending_requirement_not_executable(self):
        result = gate.plan(gate.ROOT, [gate.SOURCE + "java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureMachine.kt"])
        self.assertEqual([], result["device_scenarios"])
        self.assertEqual("NOT_IMPLEMENTED", result["scenario_requirements"][0]["status"])

    def test_leaf_tool_uses_actual_consumers_and_shared_gate_stays_broad(self):
        selected = gate.tool_tests(gate.ROOT, ["tools/device_verify.py"])
        self.assertIn("tools.tests.test_device_verify", selected)
        self.assertIn("tools.tests.test_agent_verify", selected)
        self.assertNotIn("tools.tests.test_rom_catalog", selected)
        self.assertIsNone(gate.tool_tests(gate.ROOT, ["tools/verify.py"]))

    def test_subprocess_consumers_are_not_lost_without_a_python_import(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'tools/tests').mkdir(parents=True)
            (root / 'tools/driver.py').write_text('value = 1')
            (root / 'tools/tests/test_driver.py').write_text('command = ["python", "tools/driver.py"]')
            self.assertIn('tools.tests.test_driver', gate.tool_tests(root, ['tools/driver.py']))

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
