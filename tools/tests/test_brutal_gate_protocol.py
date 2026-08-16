import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from tools import brutal_gate_protocol as protocol


class ValidateIdTest(unittest.TestCase):
    def test_accepts_safe(self):
        self.assertEqual("abc_123.X", protocol.validate_id("abc_123.X"))

    def test_rejects_space(self):
        with self.assertRaises(ValueError):
            protocol.validate_id("bad id")

    def test_rejects_shell_metacharacter(self):
        with self.assertRaises(ValueError):
            protocol.validate_id("rm;-rf")


class RenderCommandTest(unittest.TestCase):
    def test_renders_mutator_placeholder(self):
        argv = protocol.render_command(
            ["python", "tools/scan.py", "--mutation", "{mutator}"],
            mutator="remove_jvm_static",
        )
        self.assertEqual(
            ["python", "tools/scan.py", "--mutation", "remove_jvm_static"],
            argv,
        )

    def test_renders_name_placeholder(self):
        argv = protocol.render_command(
            ["echo", "{name}"],
            name="some_mutation",
        )
        self.assertEqual(["echo", "some_mutation"], argv)

    def test_unresolved_placeholder_raises(self):
        with self.assertRaises(ValueError):
            protocol.render_command(["echo", "{unknown}"])

    def test_unsafe_placeholder_value_raises(self):
        with self.assertRaises(ValueError):
            protocol.render_command(["echo", "{mutator}"], mutator="bad;id")


class ClassifyExitCodeTest(unittest.TestCase):
    def test_apply_codes(self):
        self.assertEqual(protocol.MUTATION_APPLIED, protocol.classify_apply(0))
        self.assertEqual(protocol.CANNOT_VERIFY, protocol.classify_apply(2))
        self.assertEqual(protocol.GATE_ERROR, protocol.classify_apply(3))
        self.assertEqual(protocol.MUTATION_NOT_APPLIED, protocol.classify_apply(4))

    def test_kill_codes(self):
        self.assertEqual(protocol.SURVIVED, protocol.classify_kill(0))
        self.assertEqual(protocol.INDEPENDENT_GATE_KILLED, protocol.classify_kill(1))
        self.assertEqual(protocol.CANNOT_VERIFY, protocol.classify_kill(2))
        self.assertEqual(protocol.GATE_ERROR, protocol.classify_kill(3))
        self.assertEqual(protocol.GATE_ERROR, protocol.classify_kill(255))


class RunCommandTest(unittest.TestCase):
    def test_runs_argv_no_shell(self):
        with tempfile.TemporaryDirectory() as td:
            script = Path(td) / "exit42.py"
            script.write_text("import sys; sys.exit(42)", encoding="utf-8")
            code, output = protocol.run_command([sys.executable, str(script)], Path(td), 10)
            self.assertEqual(42, code)

    def test_respects_env(self):
        with tempfile.TemporaryDirectory() as td:
            script = Path(td) / "env.py"
            script.write_text("import os; print(os.environ['BRUTAL_TEST_FLAG'])", encoding="utf-8")
            code, output = protocol.run_command(
                [sys.executable, str(script)], Path(td), 10, env={"BRUTAL_TEST_FLAG": "ok"}
            )
            self.assertEqual(0, code)
            self.assertIn("ok", output)


if __name__ == "__main__":
    import sys
    unittest.main()
