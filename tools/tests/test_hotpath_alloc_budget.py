"""Tests for tools/check_hotpath_alloc_budget.py."""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SCANNER = REPO_ROOT / "tools" / "check_hotpath_alloc_budget.py"
BASELINE = REPO_ROOT / "tools" / "HOTPATH_ALLOC_BASELINE.json"


class HotpathAllocScannerTest(unittest.TestCase):
    def test_check_passes_within_ceiling(self):
        result = subprocess.run(
            [sys.executable, str(SCANNER), "--check"],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("ceiling held", result.stdout)

    def test_baseline_file_exists_and_valid(self):
        self.assertTrue(BASELINE.exists(), "HOTPATH_ALLOC_BASELINE.json must exist")
        data = json.loads(BASELINE.read_text(encoding="utf-8"))
        self.assertIn("hook_body_alloc_total", data)
        self.assertIsInstance(data["hook_body_alloc_total"], int)
        self.assertGreaterEqual(data["hook_body_alloc_total"], 0)

    def test_write_baseline_creates_file(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            out = Path(tmpdir) / "baseline.json"
            result = subprocess.run(
                [sys.executable, str(SCANNER), "--write-baseline", "--baseline", str(out)],
                cwd=REPO_ROOT,
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertTrue(out.exists())
            data = json.loads(out.read_text(encoding="utf-8"))
            self.assertIn("hook_body_alloc_total", data)

    def test_ceiling_violation_detected(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            fake_baseline = Path(tmpdir) / "baseline.json"
            fake_baseline.write_text('{"hook_body_alloc_total": 0, "files": {}}')
            result = subprocess.run(
                [sys.executable, str(SCANNER), "--check", "--baseline", str(fake_baseline)],
                cwd=REPO_ROOT,
                capture_output=True,
                text=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("FAILED", result.stdout)


if __name__ == "__main__":
    unittest.main()
