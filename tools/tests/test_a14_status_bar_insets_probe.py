#!/usr/bin/env python3
"""Tests for a14_status_bar_insets_probe.py.

Uses fixture strings; no real adb or repo mutations.
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(REPO_ROOT / "tools"))

import a14_status_bar_insets_probe as probe


def _write_fixture(text: str) -> Path:
    fd, path = tempfile.mkstemp(prefix="a14-sb-fixture-", suffix=".txt")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        f.write(text)
    return Path(path)


def _run(fixture_text: str, configured_dp: float, package: str = "com.example.test", keep_raw: bool = False) -> dict:
    fixture_path = _write_fixture(fixture_text)
    with tempfile.TemporaryDirectory() as tmpdir:
        out = Path(tmpdir) / "out.json"
        argv = [
            sys.executable,
            str(REPO_ROOT / "tools" / "a14_status_bar_insets_probe.py"),
            "--fixture", str(fixture_path),
            "--package", package,
            "--configured-height-dp", str(configured_dp),
            "--output", str(out),
        ]
        if keep_raw:
            argv.append("--keep-raw")
        proc = subprocess.run(argv, capture_output=True, text=True, timeout=30)
        if proc.returncode != 0:
            raise AssertionError(f"probe failed: {proc.stderr}")
        return json.loads(out.read_text(encoding="utf-8"))


AOSP = """
========== DEVICES ==========
List of devices attached
ABCD1234               device product:genymotion model:Pixel

========== WM_SIZE ==========
Physical size: 1080x2400
Override size: 1080x2400

========== WM_DENSITY ==========
Physical density: 480
Override density: 480

========== DUMPSYS_WINDOW_DISPLAYS ==========
Display: mDisplayId=0
    init=1080x2400 480dpi cur=1080x2400 app=1080x2298
    rotation=0

========== DUMPSYS_WINDOW_WINDOWS ==========
  Window #5 Window{3c7e8d5 u0 com.example.test/com.example.test.MainActivity}:
    mDisplayId=0
    mFrame=[0,0][1080,2400]
    mContentFrame=[0,123][1080,2400]
    mVisibleFrame=[0,123][1080,2400]
    mActivityComponent=com.example.test/.MainActivity
    mWindowingMode=WINDOWING_MODE_FULLSCREEN
    layoutInDisplayCutoutMode=LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
    decorFitsSystemWindows=true
    mRequestedVisibleTypes=statusBars|navigationBars

========== DUMPSYS_WINDOW_INSETS ==========
InsetsState
  mDisplayFrame=[0,0][1080,2400]
  InsetsSource type=ITYPE_STATUS_BAR frame=Rect(0, 0 - 1080, 123) visible=true
    source(id=ITYPE_STATUS_BAR, frame=[0,0][1080,123], visible=true, insets=Insets{top=123})
  mStableInsets=Rect(0, 123 - 0, 88)
  mContentInsets=Rect(0, 123 - 0, 0)

========== DUMPSYS_DISPLAY ==========
DisplayDeviceInfo: ... density 480 ... cutout=DisplayCutout{insets=Rect(0, 0 - 0, 0) safeInsets=Rect(0, 0 - 0, 0)}
""".strip()

HYPEROS = """
========== DEVICES ==========
List of devices attached
MI9ABCD                device product:cetus model:Mi_11

========== WM_SIZE ==========
Physical size: 1080x2340

========== WM_DENSITY ==========
Physical density: 440

========== DUMPSYS_WINDOW_DISPLAYS ==========
Display 0:
    mDisplayId=0
    mRotation=ROTATION_0
    mDisplayInfo=DisplayInfo{... 1080x2340, 440.0dpi}

========== DUMPSYS_WINDOW_WINDOWS ==========
  Window #2 Window{e3f4a56 u0 com.miui.home/com.miui.home.launcher.Launcher}:
    mDisplayId=0
    mFrame=[0,0][1080,2340]
    mActivityComponent=com.miui.home/.launcher.Launcher
    mWindowingMode=WINDOWING_MODE_FULLSCREEN

  Window #5 Window{7b8c9d0 u0 com.example.edge/.MainActivity}:
    mDisplayId=0
    mFrame=[0,0][1080,2340]
    mActivityComponent=com.example.edge/.MainActivity
    mWindowingMode=WINDOWING_MODE_FULLSCREEN
    layoutInDisplayCutoutMode=LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    decorFitsSystemWindows=false
    mRequestedVisibleTypes=statusBars|navigationBars

========== DUMPSYS_WINDOW_INSETS ==========
InsetsState
  mDisplayFrame=[0,0][1080,2340]
  Source 0 ITYPE_STATUS_BAR
    frame=Rect(0, 0 - 1080, 111)
    visible=true
    insets=Insets{top=111}
  mStableInsets=Rect(0, 111 - 0, 111)

========== DUMPSYS_DISPLAY ==========
DisplayDeviceInfo: ... 440dpi ... cutout=DisplayCutout{insets=Rect(0, 95 - 0, 0) safeInsets=Rect(0, 95 - 0, 0)}
""".strip()

NO_INSETS = """
========== DEVICES ==========
List of devices attached
ABCD1234               device product:genymotion

========== WM_SIZE ==========
Physical size: 1080x2400

========== WM_DENSITY ==========
Physical density: 480

========== DUMPSYS_WINDOW_DISPLAYS ==========
Display: mDisplayId=0
    rotation=0

========== DUMPSYS_WINDOW_WINDOWS ==========
  Window #5 Window{3c7e8d5 u0 com.example.test/com.example.test.MainActivity}:
    mDisplayId=0
    mActivityComponent=com.example.test/.MainActivity
    mWindowingMode=WINDOWING_MODE_FULLSCREEN
    decorFitsSystemWindows=true

========== DUMPSYS_WINDOW_INSETS ==========
ERROR: unrecognized command: insets

========== DUMPSYS_DISPLAY ==========
DisplayDeviceInfo: ... density 480 ...
""".strip()

SPLIT = """
========== DEVICES ==========
List of devices attached
ABCD1234               device product:genymotion

========== WM_SIZE ==========
Physical size: 1080x2400

========== WM_DENSITY ==========
Physical density: 480

========== DUMPSYS_WINDOW_DISPLAYS ==========
Display: mDisplayId=0
    rotation=0

========== DUMPSYS_WINDOW_WINDOWS ==========
  Window #3 Window{1a2b3c4 u0 com.example.test/com.example.test.MainActivity}:
    mDisplayId=0
    mFrame=[0,1200][1080,2400]
    mActivityComponent=com.example.test/.MainActivity
    mWindowingMode=WINDOWING_MODE_SPLIT_SCREEN_SECONDARY

========== DUMPSYS_WINDOW_INSETS ==========
InsetsState
  mDisplayFrame=[0,0][1080,2400]
  InsetsSource type=ITYPE_STATUS_BAR frame=Rect(0, 0 - 1080, 123) visible=true
  mStableInsets=Rect(0, 123 - 0, 0)

========== DUMPSYS_DISPLAY ==========
DisplayDeviceInfo: ...
""".strip()

HIDDEN = """
========== DEVICES ==========
List of devices attached
ABCD1234               device product:genymotion

========== WM_SIZE ==========
Physical size: 1080x2400

========== WM_DENSITY ==========
Physical density: 480

========== DUMPSYS_WINDOW_DISPLAYS ==========
Display: mDisplayId=0
    rotation=0

========== DUMPSYS_WINDOW_WINDOWS ==========
  Window #5 Window{fullscrn u0 com.example.video/.PlayerActivity}:
    mDisplayId=0
    mActivityComponent=com.example.video/.PlayerActivity
    mWindowingMode=WINDOWING_MODE_FULLSCREEN

========== DUMPSYS_WINDOW_INSETS ==========
InsetsState
  mDisplayFrame=[0,0][1080,2400]
  InsetsSource type=ITYPE_STATUS_BAR frame=Rect(0, 0 - 1080, 123) visible=false
    insets=Insets{top=123}
  mStableInsets=Rect(0, 123 - 0, 0)

========== DUMPSYS_DISPLAY ==========
DisplayDeviceInfo: ...
""".strip()

CUTOUT_HIGHER = """
========== DEVICES ==========
List of devices attached
ABCD1234               device product:genymotion

========== WM_SIZE ==========
Physical size: 1080x2400

========== WM_DENSITY ==========
Physical density: 480

========== DUMPSYS_WINDOW_DISPLAYS ==========
Display: mDisplayId=0
    rotation=0

========== DUMPSYS_WINDOW_WINDOWS ==========
  Window #5 Window{app u0 com.example.test/.MainActivity}:
    mDisplayId=0
    mActivityComponent=com.example.test/.MainActivity
    mWindowingMode=WINDOWING_MODE_FULLSCREEN

========== DUMPSYS_WINDOW_INSETS ==========
InsetsState
  mDisplayFrame=[0,0][1080,2400]
  InsetsSource type=ITYPE_STATUS_BAR frame=Rect(0, 0 - 1080, 100) visible=true

========== DUMPSYS_DISPLAY ==========
DisplayDeviceInfo: ... cutout=DisplayCutout{insets=Rect(0, 120 - 0, 0) safeInsets=Rect(0, 120 - 0, 0) boundingRect=Rect(498, 0 - 84, 120)}
""".strip()

NO_DENSITY = """
========== DEVICES ==========
List of devices attached
ABCD1234               device product:genymotion

========== WM_SIZE ==========
Physical size: 1080x2400

========== WM_DENSITY ==========

========== DUMPSYS_WINDOW_DISPLAYS ==========
Display: mDisplayId=0

========== DUMPSYS_WINDOW_WINDOWS ==========
  Window #5 Window{app u0 com.example.test/.MainActivity}:
    mDisplayId=0
    mWindowingMode=WINDOWING_MODE_FULLSCREEN

========== DUMPSYS_WINDOW_INSETS ==========
InsetsState
  InsetsSource type=ITYPE_STATUS_BAR frame=Rect(0, 0 - 1080, 123) visible=true

========== DUMPSYS_DISPLAY ==========
DisplayDeviceInfo: ...
""".strip()


class TestProbe(unittest.TestCase):
    def test_aosp_consistent(self) -> None:
        data = _run(AOSP, 41.0)
        self.assertEqual(data["schemaVersion"], 1)
        self.assertEqual(data["target"]["packageName"], "com.example.test")
        self.assertEqual(data["configured"]["heightPx"], 123)
        self.assertEqual(data["insets"]["statusBarsHeightPx"], 123)
        self.assertEqual(data["analysis"]["classification"], "CONSISTENT")

    def test_aosp_resource_greater(self) -> None:
        data = _run(AOSP, 48.0)
        self.assertEqual(data["configured"]["heightPx"], 144)
        self.assertEqual(data["analysis"]["classification"], "RESOURCE_GREATER_THAN_INSET")
        self.assertGreater(data["analysis"]["mismatchPx"], 1)

    def test_aosp_inset_greater(self) -> None:
        data = _run(AOSP, 30.0)
        self.assertEqual(data["configured"]["heightPx"], 90)
        self.assertEqual(data["analysis"]["classification"], "INSET_GREATER_THAN_RESOURCE")

    def test_aosp_one_pixel_consistent(self) -> None:
        data = _run(AOSP, 122.0 / 3.0)
        self.assertEqual(data["configured"]["heightPx"], 122)
        self.assertEqual(data["analysis"]["classification"], "CONSISTENT")

    def test_hyperos_edge_to_edge(self) -> None:
        data = _run(HYPEROS, 37.0, package="com.example.edge")
        self.assertEqual(data["target"]["packageName"], "com.example.edge")
        self.assertIn("LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES", data["target"]["edgeToEdgeEvidence"])
        self.assertIn("decorFitsSystemWindows=false", data["target"]["edgeToEdgeEvidence"])
        self.assertEqual(data["insets"]["statusBarsHeightPx"], 111)

    def test_no_insets_unsupported(self) -> None:
        data = _run(NO_INSETS, 41.0)
        self.assertEqual(data["analysis"]["classification"], "INSUFFICIENT_EVIDENCE")
        self.assertFalse(data["commands"][5]["supported"])

    def test_split_screen(self) -> None:
        data = _run(SPLIT, 41.0)
        self.assertEqual(data["target"]["windowingMode"], "WINDOWING_MODE_SPLIT_SCREEN_SECONDARY")
        self.assertEqual(data["insets"]["statusBarsHeightPx"], 123)

    def test_hidden_status_bar(self) -> None:
        data = _run(HIDDEN, 41.0)
        self.assertEqual(data["insets"]["statusBarsHeightPx"], 123)
        self.assertEqual(data["insets"]["statusBarsVisible"], False)

    def test_cutout_higher(self) -> None:
        data = _run(CUTOUT_HIGHER, 100.0 / 3.0)
        self.assertEqual(data["configured"]["heightPx"], 100)
        self.assertEqual(data["insets"]["statusBarsHeightPx"], 100)
        self.assertEqual(data["insets"]["displayCutoutSafeTopPx"], 120)
        self.assertEqual(data["analysis"]["classification"], "CONSISTENT")

    def test_no_density_insufficient(self) -> None:
        data = _run(NO_DENSITY, 41.0)
        self.assertEqual(data["analysis"]["classification"], "INSUFFICIENT_EVIDENCE")
        self.assertIsNone(data["configured"]["heightPx"])

    def test_package_injection_rejected(self) -> None:
        for bad in ["com.example; rm -rf /", "com.example test", "com..example", "com.example`"]:
            with self.subTest(bad=bad):
                self.assertIsNone(probe.PACKAGE_RE.match(bad))

    def test_serial_not_shell(self) -> None:
        runner = probe.AdbRunner(serial="; rm -rf /", timeout=1.0)
        argv = runner._build(["adb", "shell", "wm", "size"])
        self.assertEqual(argv, ["adb", "-s", "; rm -rf /", "shell", "wm", "size"])

    def test_command_timeout(self) -> None:
        runner = probe.AdbRunner(timeout=0.001)
        result = runner.run("sleep", "sleep", [sys.executable, "-c", "import time; time.sleep(10)"])
        self.assertEqual(result["exitCode"], -1)
        self.assertFalse(result["supported"])

    def test_unsupported_command(self) -> None:
        runner = probe.AdbRunner()
        result = runner.run("missing", "missing", ["this_command_does_not_exist_xyz"])
        self.assertEqual(result["exitCode"], 127)
        self.assertFalse(result["supported"])

    def test_null_not_zero(self) -> None:
        data = _run(NO_INSETS, 41.0)
        self.assertIsNone(data["analysis"]["mismatchPx"])
        self.assertIsNone(data["insets"]["statusBarsHeightPx"])

    def test_json_schema(self) -> None:
        data = _run(AOSP, 41.0)
        for key in ["schemaVersion", "timestamp", "device", "target", "configured", "insets", "analysis", "commands"]:
            self.assertIn(key, data)
        for key in ["statusBarsHeightPx", "statusBarsVisible", "displayCutoutSafeTopPx"]:
            self.assertIn(key, data["insets"])

    def test_raw_deleted_by_default(self) -> None:
        data = _run(AOSP, 41.0)
        self.assertNotIn("rawDir", data)

    def test_keep_raw(self) -> None:
        data = _run(AOSP, 41.0, keep_raw=True)
        self.assertIn("rawDir", data)
        raw_dir = Path(data["rawDir"])
        self.assertTrue(raw_dir.exists())
        # cleanup
        for p in raw_dir.iterdir():
            p.unlink()
        raw_dir.rmdir()

    def test_fixture_section_mapping(self) -> None:
        fixture = probe._load_fixture(str(_write_fixture(AOSP)))
        self.assertIn("dumpsys_window_insets", fixture)
        self.assertIn("dumpsys_window_windows", fixture)


if __name__ == "__main__":
    unittest.main()
