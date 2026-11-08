"""Observable stack extraction, including malformed input from imported logs."""
import subprocess
import sys
import unittest
from pathlib import Path

from tools.analyze_lsposed_log import extract_module_stack


class ModuleStackTest(unittest.TestCase):
    def test_stack_frames_and_incomplete_lines(self):
        cases = {
            "at tv.withaibuild.customiuizer.mods.Hook.run(Hook.kt:42)":
                "tv.withaibuild.customiuizer.mods.Hook.run",
            "at tv.withaibuild.customiuizer.mods.Hook$1.invoke(Hook.kt)":
                "tv.withaibuild.customiuizer.mods.Hook$1.invoke",
            "at tv.withaibuild.customiuizer.Hook.<init>(Hook.java:1)":
                "tv.withaibuild.customiuizer.Hook",
            "at tv.withaibuild.customiuizer.mods.Hook":
                "tv.withaibuild.customiuizer.mods.Hook",
            "at tv.withaibuild.customiuizer$Hook.run(Hook.java)":
                "tv.withaibuild.customiuizer$Hook.run",
            "at tv.withaibuild.customiuizerOther.Hook.run(Hook.kt)": "",
            "at com.android.systemui.Hook.run(Hook.java)": "",
            "unrelated log message": "",
        }
        for line, expected in cases.items():
            with self.subTest(line=line):
                self.assertEqual(expected, extract_module_stack(line))

    def test_malformed_dollar_frames_do_not_stall_import(self):
        # A missing '(' made the old overlapping '$' branches backtrack
        # exponentially. A subprocess bounds the failure without hanging CI.
        code = (
            "from tools.analyze_lsposed_log import extract_module_stack; "
            "frame='tv.withaibuild.customiuizer.$'+'$$.$'*500; "
            "assert extract_module_stack('at '+frame+'!') == frame"
        )
        result = subprocess.run(
            [sys.executable, "-B", "-c", code],
            cwd=Path(__file__).resolve().parents[2],
            capture_output=True, text=True, timeout=5,
        )
        self.assertEqual(0, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main()
