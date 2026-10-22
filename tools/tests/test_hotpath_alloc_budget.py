"""Tests for tools/check_hotpath_alloc_budget.py."""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from tools import check_hotpath_alloc_budget as scanner

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SCANNER = REPO_ROOT / "tools" / "check_hotpath_alloc_budget.py"
BASELINE = REPO_ROOT / "tools" / "HOTPATH_ALLOC_BASELINE.json"


class HotpathAllocScannerTest(unittest.TestCase):
    def test_check_passes_within_ceiling(self):
        result = subprocess.run(
            [sys.executable, "-X", "utf8", str(SCANNER), "--check"],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
            encoding="utf-8",
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
                [sys.executable, "-X", "utf8", str(SCANNER), "--write-baseline", "--baseline", str(out)],
                cwd=REPO_ROOT,
                capture_output=True,
                text=True,
                encoding="utf-8",
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
                [sys.executable, "-X", "utf8", str(SCANNER), "--check", "--baseline", str(fake_baseline)],
                cwd=REPO_ROOT,
                capture_output=True,
                text=True,
                encoding="utf-8",
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("FAILED", result.stdout)

    def test_file_budget_cannot_be_borrowed_from_another_file(self):
        reports = [scanner.FileReport("new.kt", [scanner.Hit(7, "ArrayList(", "ArrayList(")])]
        baseline = {"hook_body_alloc_total": 1, "files": {"old.kt": 1}}
        self.assertEqual(
            scanner.ceiling_violations(reports, baseline),
            ["new.kt: 1 exceeds file ceiling 0"],
        )

    def test_missing_baseline_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            result = subprocess.run(
                [sys.executable, "-X", "utf8", str(SCANNER), "--check", "--baseline", str(Path(tmpdir) / "missing.json")],
                cwd=REPO_ROOT, capture_output=True, text=True, encoding="utf-8",
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("FAILED", result.stdout)

    def test_invalid_baseline_is_rejected(self):
        for data in (
            [], {}, {"hook_body_alloc_total": True, "files": {}},
            {"hook_body_alloc_total": -1, "files": {}},
            {"hook_body_alloc_total": 1, "files": {}},
            {"hook_body_alloc_total": 1, "files": {"a.kt": "1"}},
            {"hook_body_alloc_total": 0, "files": {"a.kt": False}},
        ):
            with self.subTest(data=data), tempfile.TemporaryDirectory() as tmpdir:
                path = Path(tmpdir) / "baseline.json"
                path.write_text(json.dumps(data), encoding="utf-8")
                with self.assertRaises(ValueError):
                    scanner.load_baseline(path)

    def test_write_cannot_raise_or_move_an_existing_ceiling(self):
        report = scanner.FileReport("new.kt", [scanner.Hit(1, "ArrayList(", "ArrayList(")])
        for data in (
            {"hook_body_alloc_total": 0, "files": {}},
            {"hook_body_alloc_total": 1, "files": {"old.kt": 1}},
        ):
            with self.subTest(data=data), tempfile.TemporaryDirectory() as tmpdir:
                path = Path(tmpdir) / "baseline.json"
                original = json.dumps(data)
                path.write_text(original, encoding="utf-8")
                with self.assertRaisesRegex(ValueError, "refusing to raise"):
                    scanner.write_baseline([report], path)
                self.assertEqual(original, path.read_text(encoding="utf-8"))

    def test_write_can_lower_an_existing_ceiling(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            path = Path(tmpdir) / "baseline.json"
            path.write_text('{"hook_body_alloc_total": 1, "files": {"old.kt": 1}}', encoding="utf-8")
            scanner.write_baseline([], path)
            self.assertEqual(scanner.load_baseline(path), {"hook_body_alloc_total": 0, "files": {}})


if __name__ == "__main__":
    unittest.main()
