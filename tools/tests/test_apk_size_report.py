"""APK attribution and the retained absolute-delta budget contract."""

from __future__ import annotations

import contextlib
import hashlib
import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from tools import apk_size_report as tool


class ApkSizeReportTest(unittest.TestCase):
    def test_zip_attribution_and_existing_report_cli(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "sample.apk"
            entries = {
                "classes.dex": b"dex" * 100,
                "lib/arm64-v8a/libsample.so": b"native" * 40,
                "resources.arsc": b"resources" * 30,
                "res/drawable/icon.png": b"image" * 20,
                "assets/settings.json": b"{}",
                "META-INF/xposed/module.prop": b"minApiVersion=101",
                "AndroidManifest.xml": b"manifest",
                "other.txt": b"other",
            }
            with zipfile.ZipFile(apk, "w", compression=zipfile.ZIP_DEFLATED) as archive:
                archive.writestr("assets/", b"")
                for name, payload in entries.items():
                    archive.writestr(name, payload)

            output = root / "baseline.json"
            self.assertEqual(tool.main([str(apk), "--out", str(output)]), 0)
            data = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(data["fileCount"], len(entries))
            self.assertEqual(data["apkFileBytes"], apk.stat().st_size)
            self.assertEqual(data["sha256"], hashlib.sha256(apk.read_bytes()).hexdigest())
            self.assertEqual(data["zipEntriesUncompressedBytes"], sum(map(len, entries.values())))
            prefixes = {
                "classes.dex": "dex", "lib/arm64-v8a/libsample.so": "lib",
                "resources.arsc": "resourcesArsc", "res/drawable/icon.png": "res",
                "assets/settings.json": "assets", "META-INF/xposed/module.prop": "meta",
                "AndroidManifest.xml": "manifest",
            }
            with zipfile.ZipFile(apk) as archive:
                for name, prefix in prefixes.items():
                    self.assertEqual(data[prefix + "UncompressedBytes"], len(entries[name]))
                    self.assertEqual(data[prefix + "CompressedBytes"], archive.getinfo(name).compress_size)
                self.assertEqual(data["zipEntriesCompressedBytes"], sum(archive.getinfo(name).compress_size for name in entries))
            with contextlib.redirect_stdout(io.StringIO()) as stdout:
                self.assertEqual(tool.main([str(apk)]), 0)
            self.assertEqual(json.loads(stdout.getvalue()), data)

    def compare_baselines(self, apk_delta: int = 0, dex_delta: int = 0, files=None):
        before = {
            "apkPath": "before.apk", "apkFileBytes": 4096,
            "zipEntriesCompressedBytes": 3000, "zipEntriesUncompressedBytes": 6000,
            "dexCompressedBytes": 2048, "dexUncompressedBytes": 4000,
            "resourcesArscCompressedBytes": 600, "libCompressedBytes": 200,
            "files": [{"name": "removed", "compressedSize": 10}],
        }
        after = dict(before, apkPath="after.apk", apkFileBytes=4096 + apk_delta,
                     dexCompressedBytes=2048 + dex_delta,
                     files=files if files is not None else before["files"])
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            paths = [root / "before.json", root / "after.json"]
            for path, data in zip(paths, (before, after)):
                path.write_text(json.dumps(data), encoding="utf-8")
            with contextlib.redirect_stdout(io.StringIO()) as stdout:
                code = tool.main([str(paths[0]), "--compare", str(paths[1]),
                                  "--apk-budget-kb", "1", "--dex-budget-kb", "1"])
            return code, stdout.getvalue()

    def test_absolute_budget_boundary_in_both_directions(self) -> None:
        for delta in (1024, -1024):
            with self.subTest(delta=delta):
                self.assertEqual(self.compare_baselines(apk_delta=delta, dex_delta=delta)[0], 0)
        for field in ("apk_delta", "dex_delta"):
            for delta in (1025, -1025):
                with self.subTest(field=field, delta=delta):
                    code, output = self.compare_baselines(**{field: delta})
                    self.assertEqual(code, 1)
                    self.assertIn("FAIL:", output)

    def test_all_metrics_added_removed_entries_and_top20(self) -> None:
        files = [{"name": f"added-{number:02}", "compressedSize": 30} for number in range(22)]
        code, output = self.compare_baselines(files=files)
        self.assertEqual(code, 0)
        for label in ("Real APK file size", "ZIP compressed total", "ZIP uncompressed total",
                      "classes*.dex (compressed)", "classes*.dex (uncompressed)",
                      "resources.arsc (compressed)", "lib/ (compressed)"):
            self.assertIn(label + ":", output)
        changes = [line for line in output.splitlines() if line.startswith("  ")]
        self.assertEqual(len(changes), 20)
        self.assertTrue(changes[0].startswith("  added-00:"))
        self.assertTrue(changes[-1].startswith("  added-19:"))
        _, removal_output = self.compare_baselines(files=[])
        self.assertIn("removed: -10 bytes", removal_output)


if __name__ == "__main__":
    unittest.main()
