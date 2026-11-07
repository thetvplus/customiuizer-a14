import argparse
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


if __name__ == "__main__":
    unittest.main()
