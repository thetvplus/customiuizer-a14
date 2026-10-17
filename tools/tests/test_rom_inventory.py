#!/usr/bin/env python3
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
INVENTORY = REPO_ROOT / "tools" / "rom_inventory.py"
APK = REPO_ROOT / "tmp-apk" / "CustoMIUIzer-A14-r14.13.0.apk"
JAR = REPO_ROOT / "gradle" / "wrapper" / "gradle-wrapper.jar"
FRAMEWORK = REPO_ROOT / "app" / "lib" / "framework.jar"


class RomInventoryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Import the module directly for fast function-level tests.
        import importlib.util

        spec = importlib.util.spec_from_file_location(
            "rom_inventory", str(INVENTORY)
        )
        cls.inventory = importlib.util.module_from_spec(spec)
        sys.modules["rom_inventory"] = cls.inventory
        spec.loader.exec_module(cls.inventory)

    def _run_subprocess(self, *extra_args):
        args = [sys.executable, str(INVENTORY)]
        args.extend(extra_args)
        return subprocess.run(args, capture_output=True, text=True)

    def test_empty_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            catalog = self.inventory.scan_directory(Path(tmp))
        self.assertEqual(catalog["summary"]["totalFiles"], 0)
        self.assertEqual(catalog["records"], [])
        self.assertIn("scannedAt", catalog)

    def test_unknown_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            unknown = Path(tmp) / "firmware.bin"
            unknown.write_bytes(b"\x00NOTAPK\x00" * 20)
            catalog = self.inventory.scan_directory(Path(tmp))
        self.assertEqual(catalog["summary"]["totalFiles"], 1)
        self.assertEqual(catalog["summary"]["unknownFiles"], 1)
        rec = catalog["records"][0]
        self.assertEqual(rec["sampleType"], "UNKNOWN")
        self.assertEqual(rec["classCount"], 0)
        self.assertTrue(any("unknown" in w.lower() for w in rec["warnings"]))

    def test_apk_and_jar_recognition(self):
        if not APK.is_file() or not JAR.is_file():
            self.skipTest("APK or JAR fixture not available")
        with tempfile.TemporaryDirectory() as tmp:
            shutil.copy2(APK, Path(tmp) / "sample.apk")
            shutil.copy2(JAR, Path(tmp) / "wrapper.jar")
            catalog = self.inventory.scan_directory(Path(tmp))

        self.assertEqual(catalog["summary"]["totalFiles"], 2)
        self.assertEqual(catalog["summary"]["recognizedSamples"], 2)

        apk_rec = next(r for r in catalog["records"] if r["fileName"].endswith(".apk"))
        self.assertEqual(apk_rec["sampleType"], "APK")
        self.assertEqual(apk_rec["packageName"], "tv.withaibuild.customiuizer.r14")
        self.assertIsNotNone(apk_rec["versionCode"])
        self.assertGreater(apk_rec["classCount"], 0)
        self.assertIn(34, (apk_rec.get("minSdk"), apk_rec.get("targetSdk")))

        jar_rec = next(r for r in catalog["records"] if r["fileName"].endswith(".jar"))
        self.assertEqual(jar_rec["sampleType"], "JAR")
        self.assertGreater(jar_rec["classCount"], 0)

    def test_sha256(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = Path(tmp) / "sample.txt"
            f.write_text("hello rom intelligence", encoding="utf-8")
            catalog = self.inventory.scan_directory(Path(tmp))

        expected = hashlib.sha256(b"hello rom intelligence").hexdigest()
        self.assertEqual(catalog["records"][0]["sha256"], expected)

    def test_missing_external_tools_degrade_gracefully(self):
        if not APK.is_file():
            self.skipTest("APK fixture not available")
        with tempfile.TemporaryDirectory() as tmp:
            shutil.copy2(APK, Path(tmp) / "sample.apk")
            catalog = self.inventory.scan_directory(
                Path(tmp), tools={"apkanalyzer": None, "jadx": None, "javap": None}
            )
        self.assertEqual(catalog["summary"]["externalToolsAvailable"], [])
        self.assertEqual(
            sorted(catalog["summary"]["externalToolsMissing"]),
            ["apkanalyzer", "jadx", "javap"],
        )
        rec = catalog["records"][0]
        self.assertEqual(rec["packageName"], "tv.withaibuild.customiuizer.r14")
        self.assertGreater(rec["classCount"], 0)

    def test_duplicate_sample_handling(self):
        if not APK.is_file():
            self.skipTest("APK fixture not available")
        with tempfile.TemporaryDirectory() as tmp:
            a = Path(tmp) / "a.apk"
            b = Path(tmp) / "b.apk"
            shutil.copy2(APK, a)
            shutil.copy2(APK, b)
            catalog = self.inventory.scan_directory(Path(tmp))

        self.assertEqual(catalog["summary"]["totalFiles"], 2)
        self.assertEqual(catalog["summary"]["recognizedSamples"], 1)
        self.assertEqual(catalog["summary"]["duplicates"], 1)

        dup = next(r for r in catalog["records"] if r["isDuplicate"])
        self.assertTrue(dup["duplicateOf"])
        self.assertEqual(dup["classCount"], 0)

    def test_dex_sample(self):
        if not APK.is_file():
            self.skipTest("APK fixture not available")
        with tempfile.TemporaryDirectory() as tmp:
            with zipfile.ZipFile(APK, "r") as zf:
                dex_data = zf.read("classes.dex")
            dex = Path(tmp) / "classes.dex"
            dex.write_bytes(dex_data)
            catalog = self.inventory.scan_directory(Path(tmp))

        self.assertEqual(catalog["summary"]["totalFiles"], 1)
        self.assertEqual(catalog["summary"]["recognizedSamples"], 1)
        rec = catalog["records"][0]
        self.assertEqual(rec["sampleType"], "DEX")
        self.assertGreater(rec["classCount"], 0)
        self.assertGreater(rec["methodCount"], 0)

    def test_compile_stub_not_a_real_rom_sample(self):
        if not FRAMEWORK.is_file():
            self.skipTest("framework.jar not available")
        with tempfile.TemporaryDirectory() as tmp:
            shutil.copy2(FRAMEWORK, Path(tmp) / "framework.jar")
            catalog = self.inventory.scan_directory(Path(tmp))

        rec = catalog["records"][0]
        self.assertEqual(rec["sampleType"], "COMPILE_STUB")
        self.assertEqual(rec["verificationStatus"], "NOT_A_SAMPLE")
        self.assertGreater(rec["classCount"], 0)

    def test_json_csv_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = Path(tmp) / "sample.txt"
            f.write_text("x", encoding="utf-8")
            json_out = Path(tmp) / "out.json"
            csv_out = Path(tmp) / "out.csv"
            r = self._run_subprocess(
                str(tmp),
                "--output-json",
                str(json_out),
                "--output-csv",
                str(csv_out),
            )
            self.assertEqual(r.returncode, 0)
            self.assertTrue(json_out.is_file())
            self.assertTrue(csv_out.is_file())
            catalog = json.loads(json_out.read_text(encoding="utf-8"))
            self.assertEqual(catalog["summary"]["totalFiles"], 1)
            self.assertIn("sampleId", csv_out.read_text(encoding="utf-8"))

    def test_dex_parser(self):
        if not APK.is_file():
            self.skipTest("APK fixture not available")
        with zipfile.ZipFile(APK, "r") as zf:
            data = zf.read("classes.dex")
        manifest = self.inventory.parse_dex(data)
        self.assertGreater(manifest["classCount"], 0)
        self.assertGreater(manifest["methodCount"], 0)
        cls = manifest["classes"][0]
        self.assertIn("className", cls)
        self.assertIn("methods", cls)

    def test_class_file_parser(self):
        if not JAR.is_file():
            self.skipTest("JAR fixture not available")
        with zipfile.ZipFile(JAR, "r") as zf:
            class_name = next(n for n in zf.namelist() if n.endswith(".class"))
            info = self.inventory.parse_class_file(zf.read(class_name))
        self.assertTrue(info["className"])
        self.assertIsInstance(info["methods"], list)


if __name__ == "__main__":
    unittest.main()
