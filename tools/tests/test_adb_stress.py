"""No hardware assumptions: exercise the bounded driver and evidence semantics."""
import argparse
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools import adb_stress as stress


class AdbStressTest(unittest.TestCase):
    def test_navigation_is_bounded_reproducible_and_only_uses_reversible_keys(self):
        sequence = stress.navigation(0xA14, 24)
        self.assertEqual(sequence, stress.navigation(0xA14, 24))
        self.assertEqual(24, len(sequence))
        self.assertEqual("KEYCODE_HOME", sequence[0])
        self.assertEqual("KEYCODE_HOME", sequence[-1])
        self.assertLessEqual(set(sequence), {"KEYCODE_HOME", "KEYCODE_BACK", "KEYCODE_APP_SWITCH"})

    def test_missing_metrics_remain_unknown_and_declared_zero_remains_zero(self):
        self.assertEqual({"pss_kb": None, "threads": None}, stress.memory("Permission denied", ""))
        self.assertEqual({"pss_kb": 0, "threads": 12}, stress.memory(" TOTAL PSS: 0", "Threads:\t12\n"))

    def test_missing_or_ambiguous_devices_never_receive_input(self):
        for devices in ("List of devices attached\n", "List of devices attached\na\tdevice\nb\tdevice\n",
                        "List of devices attached\na\toffline\n"):
            with patch.object(stress, "adb_command", return_value=devices) as command:
                with self.assertRaises(RuntimeError):
                    stress.device("adb", None)
                command.assert_called_once_with("adb", None, "devices")

    def test_connection_must_remain_ready_on_both_readbacks(self):
        with patch.object(stress, "adb_command", side_effect=["List of devices attached\na\tdevice\n", "device", "offline"]):
            with self.assertRaisesRegex(RuntimeError, "not stable"):
                stress.device("adb", None)

    def test_interrupted_capture_is_unavailable_and_does_not_claim_zero_crashes(self):
        args = argparse.Namespace(adb="adb", serial=None, scenario="navigation", seed=0xA14,
                                  steps=4, pause_ms=0, observe_seconds=0)
        def command(adb, serial, *values):
            if values == ("shell", "date", "-Iseconds"): return "2026-10-21T12:00:00+08:00"
            if values == ("shell", "input", "keyevent", "223"): return ""
            if values == ("shell", "dumpsys", "power"): return "mWakefulness=Dozing"
            if values == ("shell", "dumpsys", "window", "policy"): return "mIsShowing=true"
            raise RuntimeError("disconnected")
        with tempfile.TemporaryDirectory() as td, patch.object(stress, "device", return_value="device"), \
                patch.object(stress, "capture", return_value={}), patch.object(stress.time, "sleep"), \
                patch.object(stress, "adb_command", side_effect=command) as calls:
            result = stress.exercise(args, Path(td))
        self.assertEqual("UNAVAILABLE", result["status"])
        self.assertIsNone(result["crash_anr_events"])
        self.assertEqual(0, result["accepted_navigation_commands"])
        self.assertTrue(result["screen_locked"])
        self.assertIn(unittest.mock.call("adb", "device", "shell", "input", "keyevent", "223"), calls.call_args_list)

    def test_success_collects_evidence_but_never_labels_it_as_device_acceptance(self):
        args = argparse.Namespace(adb="adb", serial=None, scenario="navigation", seed=0xA14,
                                  steps=4, pause_ms=0, observe_seconds=0)
        def command(adb, serial, *values):
            if values == ("shell", "date", "-Iseconds"): return "2026-10-21T12:00:00+08:00"
            if values == ("get-state",): return "device"
            if values[0] == "logcat": return "am_anr: [0, 42, com.miui.home, reason]"
            if values == ("shell", "dumpsys", "power"): return "mWakefulness=Asleep"
            if values == ("shell", "dumpsys", "window", "policy"): return "showing=true"
            return ""
        with tempfile.TemporaryDirectory() as td, patch.object(stress, "device", return_value="device"), \
                patch.object(stress, "capture", return_value={}), patch.object(stress.time, "sleep"), \
                patch.object(stress, "adb_command", side_effect=command):
            result = stress.exercise(args, Path(td))
        self.assertEqual("COLLECTED", result["status"])
        self.assertEqual(4, result["accepted_navigation_commands"])
        self.assertEqual(1, len(result["crash_anr_events"]))
        self.assertTrue(result["screen_locked"])

    def test_lock_failure_is_reported_even_when_observation_completed(self):
        args = argparse.Namespace(adb="adb", serial=None, scenario="observe", seed=0xA14,
                                  steps=4, pause_ms=0, observe_seconds=0)
        with tempfile.TemporaryDirectory() as td, patch.object(stress, "device", return_value="device"), \
                patch.object(stress, "capture", return_value={}), patch.object(stress.time, "sleep"), \
                patch.object(stress, "adb_command", return_value="device"):
            result = stress.exercise(args, Path(td))
        self.assertEqual("UNAVAILABLE", result["status"])
        self.assertFalse(result["screen_locked"])
        self.assertIn("could not be confirmed", result["cleanup_error"])

    def test_lock_readback_waits_for_asynchronous_keyguard_without_repeating_input(self):
        with patch.object(stress, "adb_command", side_effect=["", "mWakefulness=Awake", "showing=false",
                                                            "mWakefulness=Dozing", "showing=true"]) as commands, \
                patch.object(stress.time, "sleep") as wait:
            stress.sleep_and_lock("adb", "device")
        self.assertEqual(5, commands.call_count)
        self.assertEqual(1, sum(call.args[2:] == ("shell", "input", "keyevent", "223")
                                for call in commands.call_args_list))
        wait.assert_called_once_with(0.25)

