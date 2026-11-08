import argparse
import hashlib
import subprocess
import tempfile
import unittest
import zipfile
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

    def test_unsupported_sampling_request_is_explicit_before_device_actions(self):
        args = argparse.Namespace(scenario="fixture-smoke", adb="adb", serial=None, run=True, apk=None, perfetto_rounds=3)
        with tempfile.TemporaryDirectory() as tmp, patch.object(gate, "device") as device:
            self.assertEqual("NOT_APPLICABLE", gate.exercise(args, Path(tmp))["status"])
            device.assert_not_called()

    def test_truncated_partial_proof_cannot_interrupt_cleanup_reporting(self):
        args = argparse.Namespace(scenario="island-takeover", adb="adb", serial=None, run=True, apk=None)
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "hook-evidence.json").write_text('{"partial":')
            with patch.object(gate, "device", side_effect=RuntimeError("offline")):
                report = gate.exercise(args, root)
            self.assertEqual("PENDING", report["status"])
            self.assertIn("Incomplete hook evidence", report["cleanup_errors"][0])

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
            with zipfile.ZipFile(args.fixture_apk, "w") as archive:
                archive.writestr("AndroidManifest.xml", '<manifest package="org.example.a14fixture"/>')
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
                self.assertEqual(str(root / "agent-device-home"), kwargs["env"]["AGENT_DEVICE_STATE_DIR"])
                if command[2:4] == ["daemon", "stop"]:
                    self.assertEqual(str(root / "agent-device-home"), command[-1])
                if command[2] == "test":
                    (root / "junit.xml").write_text('<testsuite tests="0"/>')
                return subprocess.CompletedProcess(command, 0, "", "")
            with patch.object(gate, "device", return_value="fixture"), patch.object(gate, "adb_command", side_effect=adb), \
                    patch.object(gate.subprocess, "run", side_effect=cli), patch.object(gate, "sleep_and_lock") as lock:
                report = gate.exercise(args, root)
                self.assertEqual("PENDING", report["status"])
                self.assertIn(("uninstall", gate.FIXTURE), operations)
                self.assertNotIn(("uninstall", gate.HELPERS[0]), operations)
                lock.assert_called_once()

    def test_report_is_bound_to_exact_registered_scenario(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            script = root / "selected.ad"
            report = root / "junit.xml"
            report.write_text('<testsuite tests="1"><testcase name="other.ad" file="other.ad"/></testsuite>')
            with self.assertRaisesRegex(ValueError, "another scenario"):
                gate.scenario_result(0, report, script)
            report.write_text(f'<testsuite tests="1"><testcase name="selected.ad" file="{script.as_posix()}"><failure>"reason": "wait_target_absent"</failure></testcase></testsuite>')
            self.assertEqual("FAILED", gate.scenario_result(1, report, script))
            report.write_text(report.read_text().replace('"reason": "wait_target_absent"', 'ADB disconnected'))
            self.assertEqual("PENDING", gate.scenario_result(1, report, script))

    def test_native_heads_up_cannot_satisfy_island_window_attribution(self):
        snapshot = {"snapshotQuality": {"state": "healthy"}, "nodes": [
            {"index": 0, "bundleId": "com.android.systemui", "rect": {"x": 0, "y": 0, "width": 1080, "height": 2460}},
            {"index": 1, "parentIndex": 0, "label": "fixture", "visibleToUser": True}]}
        self.assertFalse(gate.in_island(snapshot, "fixture", (0, 0, 1080, 250)))
        snapshot["nodes"][0]["rect"]["height"] = 250
        self.assertTrue(gate.in_island(snapshot, "fixture", (0, 0, 1080, 250)))
        snapshot["nodes"][0]["bundleId"] = "org.example.other"
        self.assertFalse(gate.in_island(snapshot, "fixture", (0, 0, 1080, 250)))
        snapshot["nodes"][0]["bundleId"] = "com.android.systemui"
        snapshot["truncated"] = True
        with self.assertRaises(RuntimeError): gate.in_island(snapshot, "fixture", (0, 0, 1080, 250))

    def test_loaded_revision_must_match_candidate_even_when_api_id_is_confirmed(self):
        props = dict(versionName="fixture", versionCode="1", revision="12345678")
        log = "CustoMIUIzer fixture (1) [12345678] loaded in com.android.systemui\nsystemui.init (confirmed)"
        self.assertTrue(gate.host_log_matches(log, props))
        self.assertFalse(gate.host_log_matches(log.replace("12345678", "87654321"), props))

    def test_restore_compares_shared_preference_values_not_serializer_order(self):
        self.assertEqual(gate.preference_values('<map><set name="x"><string>a</string><string>b</string></set></map>'),
                         gate.preference_values('<map>\n<set name="x"><string>b</string><string>a</string></set></map>'))

    def test_noisy_or_incompatible_performance_never_becomes_a_pass(self):
        samples = [{"admitted": bool(i % 2), "stable": True, "conditions": {"refresh_hz": 120},
                    "frames": {"total_produced_frames": 80, "avg_frame_dur_ms": [4, 3.9, 4.5, 4, 3.8, 4.3][i]}}
                   for i in range(6)]
        comparisons = [{"status": "complete", "metrics": [{"comparable": True}]}] * 3
        self.assertEqual("INCONCLUSIVE_NOISE", gate.performance_verdict(samples, comparisons)["status"])
        samples[-1]["conditions"] = {"refresh_hz": 60}
        self.assertEqual("INCONCLUSIVE", gate.performance_verdict(samples, comparisons)["status"])
        self.assertEqual("INCONCLUSIVE", gate.performance_verdict(samples[:2], comparisons[:1])["status"])
        samples[-1]["conditions"] = {"refresh_hz": 120}
        self.assertEqual("INCONCLUSIVE", gate.performance_verdict(samples, [{"status": "partial"}] * 3)["status"])


if __name__ == "__main__":
    unittest.main()
