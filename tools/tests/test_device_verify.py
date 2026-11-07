import argparse
import hashlib
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools import device_verify as gate


class DeviceVerifyTest(unittest.TestCase):
    def test_zero_skipped_contradictory_and_failed_reports_cannot_pass(self):
        reports = (
            '<testsuite tests="0"/>',
            '<testsuite tests="1" skipped="1"><testcase><skipped/></testcase></testsuite>',
            '<testsuite tests="1" failures="1"><testcase/></testsuite>',
            '<testsuite tests="1"><testcase><error/></testcase></testsuite>',
            '<testsuite tests="2"><testcase/></testsuite>',
        )
        with tempfile.TemporaryDirectory() as tmp:
            report = Path(tmp) / "junit.xml"
            for text in reports:
                report.write_text(text)
                self.assertFalse(gate.junit_passed(report), text)
            report.write_text('<testsuites><testsuite tests="1"><testcase name="real"/></testsuite></testsuites>')
            self.assertTrue(gate.junit_passed(report))

    def test_locked_or_unknown_keyguard_is_not_ready(self):
        self.assertTrue(gate.unlocked("mWakefulness=Awake", "showing=false"))
        self.assertFalse(gate.unlocked("mWakefulness=Asleep", "showing=false"))
        self.assertFalse(gate.unlocked("mWakefulness=Awake", ""))
        self.assertFalse(gate.unlocked("mWakefulness=Awake", "showing=false\nmIsShowing=true"))

    def test_unavailable_device_is_pending_and_never_starts_automation(self):
        args = argparse.Namespace(scenario="fixture-smoke", adb="adb", serial=None, run=True, apk=None)
        with tempfile.TemporaryDirectory() as tmp, patch.object(gate, "device", side_effect=RuntimeError("offline")), patch.object(gate.subprocess, "run") as run:
            report = gate.exercise(args, Path(tmp))
            self.assertEqual("PENDING", report["status"])
            self.assertEqual("UNVERIFIED", report["hook_effect"])
            run.assert_not_called()

    def test_candidate_mismatch_never_installs_or_starts_automation(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "candidate.apk"
            apk.write_bytes(b"candidate")
            args = argparse.Namespace(scenario="fixture-smoke", adb="adb", serial=None, run=False, apk=apk)
            def adb(_binary, _serial, *parts):
                if parts[:2] == ("shell", "getprop"):
                    return {"ro.build.version.sdk": "34", "ro.build.version.release": "14", "ro.mi.os.version.name": "OS1.0", "ro.product.cpu.abilist": "arm64-v8a"}.get(parts[2], "fixture")
                if parts[:3] == ("shell", "pm", "path"):
                    return "package:/data/app/fixture/base.apk"
                if parts[:2] == ("shell", "sha256sum"):
                    return hashlib.sha256(b"different").hexdigest() + "  base.apk"
                self.fail(f"Unexpected mutation during preflight: {parts}")
            with patch.object(gate, "device", return_value="fixture"), patch.object(gate, "adb_command", side_effect=adb), patch.object(gate.subprocess, "run") as run:
                report = gate.exercise(args, Path(tmp))
                self.assertEqual("PENDING", report["status"])
                self.assertIn("differs", report["error"])
                run.assert_not_called()

    def test_zero_exit_without_assertions_cannot_pass_and_existing_helper_is_preserved(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            entry = root / "cli.mjs"
            entry.touch()
            args = argparse.Namespace(scenario="fixture-smoke", adb="adb", serial=None, run=True, apk=None,
                                      agent_device=entry, fixture_apk=root / "fixture.apk", node="node")
            operations = []
            def adb(_binary, _serial, *parts):
                operations.append(parts)
                if parts[:2] == ("shell", "getprop"):
                    return {"ro.build.version.sdk": "34", "ro.build.version.release": "14", "ro.mi.os.version.name": "OS1.0", "ro.product.cpu.abilist": "arm64-v8a"}.get(parts[2], "fixture")
                if parts == ("shell", "dumpsys", "power"): return "mWakefulness=Awake"
                if parts == ("shell", "dumpsys", "window", "policy"): return "showing=false"
                if parts == ("shell", "pm", "list", "packages"): return "package:" + gate.HELPERS[0]
                if parts[:2] == ("shell", "pidof"): return "42"
                return "Success"
            def cli(command, **kwargs):
                if command[2] == "test":
                    (root / "junit.xml").write_text('<testsuite tests="0"/>')
                return subprocess.CompletedProcess(command, 0, "", "")
            with patch.object(gate, "device", return_value="fixture"), patch.object(gate, "adb_command", side_effect=adb), \
                    patch.object(gate.subprocess, "run", side_effect=cli), patch.object(gate, "sleep_and_lock") as lock:
                report = gate.exercise(args, root)
                self.assertEqual("FAILED", report["status"])
                self.assertIn(("uninstall", gate.FIXTURE), operations)
                self.assertNotIn(("uninstall", gate.HELPERS[0]), operations)
                lock.assert_called_once()


if __name__ == "__main__":
    unittest.main()
