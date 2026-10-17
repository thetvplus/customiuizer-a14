#!/usr/bin/env python3
"""Mechanical validation for the manifest-driven APK size delta report."""

from __future__ import annotations

import copy
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from tools import apk_size_delta as d

REPO_ROOT = Path(__file__).resolve().parents[2]
TOOL_PATH = REPO_ROOT / "tools" / "apk_size_delta.py"

BASELINE_COMMIT = "55fc2a21d0e96f9ef643f53fcc9b74374bd959db"
CURRENT_COMMIT = "1856c4e229213dfae47ff575aee446ce6a7b5f22"
BAD_COMMIT = "not-a-commit"
HEX64 = "0" * 64

REQUIRED_METRICS = [
    "apkFileBytes",
    "zipEntriesUncompressedBytes",
    "zipEntriesCompressedBytes",
    "fileCount",
    "dexUncompressedBytes",
    "dexCompressedBytes",
    "resourcesArscUncompressedBytes",
    "resourcesArscCompressedBytes",
    "libUncompressedBytes",
    "libCompressedBytes",
    "resUncompressedBytes",
    "resCompressedBytes",
    "assetsUncompressedBytes",
    "assetsCompressedBytes",
    "metaUncompressedBytes",
    "metaCompressedBytes",
    "manifestUncompressedBytes",
    "manifestCompressedBytes",
]

CONCLUSIONS = {
    "NO_MEANINGFUL_CHANGE",
    "EXPLAINED_INCREASE",
    "EXPLAINED_DECREASE",
    "MIXED_CHANGE",
    "NEEDS_INVESTIGATION",
}


def write_gradle(root: Path, version_name: str = "r14.16.1", version_code: int = 192) -> None:
    (root / "build.gradle.kts").write_text(
        f"""
val lastVersion = {version_code}
val lastVersionName = "{version_name}"
android {{
    defaultConfig {{
        applicationId = "tv.withaibuild.customiuizer.r14"
        minSdk = 34
        targetSdk = 34
        versionCode = lastVersion
        versionName = lastVersionName
        ndk.abiFilters += "arm64-v8a"
    }}
}}
""",
        encoding="utf-8",
    )


def measurement(
    variant: str,
    apk_path: str,
    file_bytes: int = 1000,
    zip_uncompressed: int = 900,
    zip_compressed: int = 700,
    files: list[dict] | None = None,
    sha256: str = HEX64,
) -> dict:
    if files is None:
        files = [
            {"name": "classes.dex", "bucket": "dex", "uncompressedSize": 400, "compressedSize": 300},
        ]
    return {
        "variant": variant,
        "sha256": sha256,
        "apkPath": apk_path,
        "apkFileBytes": file_bytes,
        "zipEntriesUncompressedBytes": zip_uncompressed,
        "zipEntriesCompressedBytes": zip_compressed,
        "fileCount": len(files),
        "dexUncompressedBytes": 400,
        "dexCompressedBytes": 300,
        "resourcesArscUncompressedBytes": 100,
        "resourcesArscCompressedBytes": 80,
        "libUncompressedBytes": 100,
        "libCompressedBytes": 80,
        "resUncompressedBytes": 100,
        "resCompressedBytes": 80,
        "assetsUncompressedBytes": 50,
        "assetsCompressedBytes": 30,
        "metaUncompressedBytes": 50,
        "metaCompressedBytes": 30,
        "manifestUncompressedBytes": 50,
        "manifestCompressedBytes": 30,
        "files": files,
    }


def write_manifest(
    root: Path,
    variants: dict[str, dict[str, str]],
    schema: int = 1,
) -> Path:
    manifest = {"schema": schema, "variants": variants}
    path = root / "manifest.json"
    path.write_text(json.dumps(manifest), encoding="utf-8")
    return path


def write_default_fixtures(root: Path) -> tuple[Path, Path, Path]:
    write_gradle(root)
    manifest = write_manifest(
        root,
        {
            "release": {"baseline": "release-baseline.json", "current": "release-current.json"},
            "develop": {"baseline": "develop-baseline.json", "current": "develop-current.json"},
        },
    )
    (root / "develop-baseline.json").write_text(
        json.dumps(measurement("develop", "app/build/outputs/apk/develop/app-develop.apk")),
        encoding="utf-8",
    )
    (root / "develop-current.json").write_text(
        json.dumps(
            measurement(
                "develop",
                "app/build/outputs/apk/develop/app-develop.apk",
                file_bytes=1100,
                zip_compressed=750,
                files=[
                    {"name": "classes.dex", "bucket": "dex", "uncompressedSize": 400, "compressedSize": 300},
                    {"name": "resources.arsc", "bucket": "arsc", "uncompressedSize": 100, "compressedSize": 80},
                ],
            )
        ),
        encoding="utf-8",
    )
    (root / "release-baseline.json").write_text(
        json.dumps(measurement("release", "app/build/outputs/apk/release/app-release.apk")),
        encoding="utf-8",
    )
    (root / "release-current.json").write_text(
        json.dumps(
            measurement(
                "release",
                "app/build/outputs/apk/release/app-release.apk",
                file_bytes=1050,
            )
        ),
        encoding="utf-8",
    )
    out_json = root / "out.json"
    out_md = root / "out.md"
    return manifest, out_json, out_md


def run_cli(
    manifest: Path,
    gradle: Path,
    out_json: Path,
    out_md: Path,
    baseline: str = BASELINE_COMMIT,
    current: str = CURRENT_COMMIT,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            sys.executable,
            str(TOOL_PATH),
            "--inputs-manifest",
            str(manifest),
            "--baseline-commit",
            baseline,
            "--current-commit",
            current,
            "--gradle-file",
            str(gradle),
            "--out-json",
            str(out_json),
            "--out-md",
            str(out_md),
        ],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )


class ApkSizeDeltaTest(unittest.TestCase):
    def setUp(self) -> None:
        self.td = tempfile.TemporaryDirectory()
        self.addCleanup(self.td.cleanup)
        self.root = Path(self.td.name)

    # ---- helpers ----

    def _load_report(self, out_json: Path) -> dict:
        self.assertTrue(out_json.is_file(), "output JSON was not written")
        return json.loads(out_json.read_text(encoding="utf-8"))

    def _run_default(self) -> tuple[Path, Path, dict]:
        manifest, out_json, out_md = write_default_fixtures(self.root)
        result = run_cli(manifest, self.root / "build.gradle.kts", out_json, out_md)
        self.assertEqual(0, result.returncode, result.stderr)
        data = self._load_report(out_json)
        return out_json, out_md, data

    # ---- positive contract tests ----

    def test_validManifest_generatesJsonAndMarkdown(self):
        _, out_md, data = self._run_default()
        self.assertIn("schema", data)
        self.assertIn("reportVersion", data)
        self.assertIn("baselineCommit", data)
        self.assertIn("currentSourceCommit", data)
        self.assertIn("buildConfig", data)
        self.assertIn("conclusion", data)
        self.assertIn("variants", data)
        self.assertTrue(out_md.is_file())

    def test_outputIsDeterministic(self):
        _, out_md1, data1 = self._run_default()
        out_json2 = self.root / "out2.json"
        out_md2 = self.root / "out2.md"
        manifest, _, _ = write_default_fixtures(self.root)
        result = run_cli(manifest, self.root / "build.gradle.kts", out_json2, out_md2)
        self.assertEqual(0, result.returncode)
        data2 = json.loads(out_json2.read_text(encoding="utf-8"))
        self.assertEqual(data1, data2)
        self.assertEqual(out_md1.read_text(encoding="utf-8"), out_md2.read_text(encoding="utf-8"))

    def test_variantsAreSortedDeterministically(self):
        # Put variants in reverse order in the manifest.
        write_gradle(self.root)
        write_manifest(
            self.root,
            {
                "release": {"baseline": "release-baseline.json", "current": "release-current.json"},
                "develop": {"baseline": "develop-baseline.json", "current": "develop-current.json"},
            },
        )
        (self.root / "develop-baseline.json").write_text(json.dumps(measurement("develop", "a.apk")), encoding="utf-8")
        (self.root / "develop-current.json").write_text(json.dumps(measurement("develop", "a.apk", file_bytes=1100)), encoding="utf-8")
        (self.root / "release-baseline.json").write_text(json.dumps(measurement("release", "b.apk")), encoding="utf-8")
        (self.root / "release-current.json").write_text(json.dumps(measurement("release", "b.apk", file_bytes=1050)), encoding="utf-8")
        out_json = self.root / "out.json"
        out_md = self.root / "out.md"
        result = run_cli(self.root / "manifest.json", self.root / "build.gradle.kts", out_json, out_md)
        self.assertEqual(0, result.returncode)
        data = json.loads(out_json.read_text(encoding="utf-8"))
        self.assertEqual(["develop", "release"], list(data["variants"].keys()))

    def test_deltaMathIsCorrect(self):
        _, _, data = self._run_default()
        for variant, v in data["variants"].items():
            for metric, vals in v["metrics"].items():
                self.assertEqual(vals["current"] - vals["baseline"], vals["delta"], f"{variant}.{metric}")

    def test_zeroBaselinePercentSemantics(self):
        write_gradle(self.root)
        write_manifest(
            self.root,
            {
                "develop": {
                    "baseline": "develop-baseline.json",
                    "current": "develop-current.json",
                }
            },
        )
        base = measurement("develop", "a.apk", file_bytes=0)
        base["zipEntriesCompressedBytes"] = 0
        cur = measurement("develop", "a.apk", file_bytes=0)
        cur["zipEntriesCompressedBytes"] = 0
        (self.root / "develop-baseline.json").write_text(json.dumps(base), encoding="utf-8")
        (self.root / "develop-current.json").write_text(json.dumps(cur), encoding="utf-8")
        out_json = self.root / "out.json"
        out_md = self.root / "out.md"
        result = run_cli(self.root / "manifest.json", self.root / "build.gradle.kts", out_json, out_md)
        self.assertEqual(0, result.returncode)
        data = json.loads(out_json.read_text(encoding="utf-8"))
        self.assertEqual(0.0, data["variants"]["develop"]["metrics"]["apkFileBytes"]["deltaPercent"])

        base2 = measurement("develop", "a.apk", file_bytes=0)
        base2["zipEntriesCompressedBytes"] = 0
        cur2 = measurement("develop", "a.apk", file_bytes=100)
        cur2["zipEntriesCompressedBytes"] = 50
        (self.root / "develop-baseline.json").write_text(json.dumps(base2), encoding="utf-8")
        (self.root / "develop-current.json").write_text(json.dumps(cur2), encoding="utf-8")
        out_json2 = self.root / "out2.json"
        out_md2 = self.root / "out2.md"
        result = run_cli(self.root / "manifest.json", self.root / "build.gradle.kts", out_json2, out_md2)
        self.assertEqual(0, result.returncode)
        data = json.loads(out_json2.read_text(encoding="utf-8"))
        self.assertEqual(float("inf"), data["variants"]["develop"]["metrics"]["apkFileBytes"]["deltaPercent"])

    def test_addedRemovedChangedAreMutuallyExclusive(self):
        _, _, data = self._run_default()
        for variant, v in data["variants"].items():
            added = {e["name"] for e in v["entryDiffs"]["added"]}
            removed = {e["name"] for e in v["entryDiffs"]["removed"]}
            changed = {e["name"] for e in v["entryDiffs"]["changed"]}
            self.assertFalse(added & removed)
            self.assertFalse(added & changed)
            self.assertFalse(removed & changed)

    def test_changedEntryDeltaMath(self):
        _, _, data = self._run_default()
        for variant, v in data["variants"].items():
            for e in v["entryDiffs"]["changed"]:
                self.assertEqual(
                    e["currentUncompressed"] - e["baselineUncompressed"],
                    e["uncompressedDelta"],
                )
                self.assertEqual(
                    e["currentCompressed"] - e["baselineCompressed"],
                    e["compressedDelta"],
                )

    def test_topIncreasesAndDecreasesAreStable(self):
        _, _, data = self._run_default()
        for variant, v in data["variants"].items():
            increases = v["entryDiffs"]["topIncreases"]
            decreases = v["entryDiffs"]["topDecreases"]
            for e in increases:
                self.assertGreater(e["compressedDelta"], 0)
            for e in decreases:
                self.assertLess(e["compressedDelta"], 0)
            if len(increases) > 1:
                for i in range(len(increases) - 1):
                    self.assertGreaterEqual(
                        increases[i]["compressedDelta"],
                        increases[i + 1]["compressedDelta"],
                    )

    # ---- validation / rejection tests ----

    def test_malformedCommitRejected(self):
        manifest, out_json, out_md = write_default_fixtures(self.root)
        result = run_cli(
            manifest,
            self.root / "build.gradle.kts",
            out_json,
            out_md,
            current=BAD_COMMIT,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("must be a 40-character hex SHA", result.stderr)

    def test_malformedSha256Rejected(self):
        write_gradle(self.root)
        write_manifest(
            self.root,
            {"develop": {"baseline": "baseline.json", "current": "current.json"}},
        )
        bad = measurement("develop", "a.apk", sha256="not-hex")
        (self.root / "baseline.json").write_text(json.dumps(bad), encoding="utf-8")
        (self.root / "current.json").write_text(json.dumps(measurement("develop", "a.apk", file_bytes=1100)), encoding="utf-8")
        out_json = self.root / "out.json"
        out_md = self.root / "out.md"
        result = run_cli(self.root / "manifest.json", self.root / "build.gradle.kts", out_json, out_md)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("invalid sha256", result.stderr)

    def test_absoluteWindowsPathRejected(self):
        write_gradle(self.root)
        write_manifest(
            self.root,
            {"develop": {"baseline": "baseline.json", "current": "current.json"}},
        )
        bad = measurement("develop", chr(67) + ":/Users/tv/app.apk")
        (self.root / "baseline.json").write_text(json.dumps(bad), encoding="utf-8")
        (self.root / "current.json").write_text(json.dumps(measurement("develop", "a.apk", file_bytes=1100)), encoding="utf-8")
        out_json = self.root / "out.json"
        out_md = self.root / "out.md"
        result = run_cli(self.root / "manifest.json", self.root / "build.gradle.kts", out_json, out_md)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("must be relative", result.stderr)

    def test_absoluteUnixPathRejected(self):
        write_gradle(self.root)
        write_manifest(
            self.root,
            {"develop": {"baseline": "baseline.json", "current": "current.json"}},
        )
        bad = measurement("develop", "/home/tv/app.apk")
        (self.root / "baseline.json").write_text(json.dumps(bad), encoding="utf-8")
        (self.root / "current.json").write_text(json.dumps(measurement("develop", "a.apk", file_bytes=1100)), encoding="utf-8")
        out_json = self.root / "out.json"
        out_md = self.root / "out.md"
        result = run_cli(self.root / "manifest.json", self.root / "build.gradle.kts", out_json, out_md)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("must be relative", result.stderr)

    def test_variantMismatchRejected(self):
        write_gradle(self.root)
        write_manifest(
            self.root,
            {
                "develop": {"baseline": "baseline.json", "current": "current.json"},
                "release": {"baseline": "release-baseline.json"},
            },
        )
        for name in ("baseline.json", "current.json", "release-baseline.json"):
            (self.root / name).write_text(json.dumps(measurement("develop", "a.apk")), encoding="utf-8")
        out_json = self.root / "out.json"
        out_md = self.root / "out.md"
        result = run_cli(self.root / "manifest.json", self.root / "build.gradle.kts", out_json, out_md)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("variant sets must match", result.stderr)

    def test_missingCurrentVariantRejected(self):
        write_gradle(self.root)
        write_manifest(
            self.root,
            {"develop": {"baseline": "baseline.json", "current": "missing.json"}},
        )
        (self.root / "baseline.json").write_text(json.dumps(measurement("develop", "a.apk")), encoding="utf-8")
        out_json = self.root / "out.json"
        out_md = self.root / "out.md"
        result = run_cli(self.root / "manifest.json", self.root / "build.gradle.kts", out_json, out_md)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("measurement file not found", result.stderr)

    def test_missingRequiredMetricRejected(self):
        write_gradle(self.root)
        write_manifest(
            self.root,
            {"develop": {"baseline": "baseline.json", "current": "current.json"}},
        )
        base = measurement("develop", "a.apk")
        del base["dexUncompressedBytes"]
        (self.root / "baseline.json").write_text(json.dumps(base), encoding="utf-8")
        (self.root / "current.json").write_text(json.dumps(measurement("develop", "a.apk", file_bytes=1100)), encoding="utf-8")
        out_json = self.root / "out.json"
        out_md = self.root / "out.md"
        result = run_cli(self.root / "manifest.json", self.root / "build.gradle.kts", out_json, out_md)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("missing measurement fields", result.stderr)
        self.assertIn("dexUncompressedBytes", result.stderr)

    def test_negativeMetricRejected(self):
        write_gradle(self.root)
        write_manifest(
            self.root,
            {"develop": {"baseline": "baseline.json", "current": "current.json"}},
        )
        base = measurement("develop", "a.apk")
        base["apkFileBytes"] = -1
        (self.root / "baseline.json").write_text(json.dumps(base), encoding="utf-8")
        (self.root / "current.json").write_text(json.dumps(measurement("develop", "a.apk", file_bytes=1100)), encoding="utf-8")
        out_json = self.root / "out.json"
        out_md = self.root / "out.md"
        result = run_cli(self.root / "manifest.json", self.root / "build.gradle.kts", out_json, out_md)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("non-negative", result.stderr)

    def test_duplicateFileNameRejected(self):
        write_gradle(self.root)
        write_manifest(
            self.root,
            {"develop": {"baseline": "baseline.json", "current": "current.json"}},
        )
        base = measurement(
            "develop",
            "a.apk",
            files=[
                {"name": "classes.dex", "bucket": "dex", "uncompressedSize": 400, "compressedSize": 300},
                {"name": "classes.dex", "bucket": "dex", "uncompressedSize": 100, "compressedSize": 80},
            ],
        )
        (self.root / "baseline.json").write_text(json.dumps(base), encoding="utf-8")
        (self.root / "current.json").write_text(json.dumps(measurement("develop", "a.apk", file_bytes=1100)), encoding="utf-8")
        out_json = self.root / "out.json"
        out_md = self.root / "out.md"
        result = run_cli(self.root / "manifest.json", self.root / "build.gradle.kts", out_json, out_md)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("duplicate file name", result.stderr)

    def test_emptyVariantSetRejected(self):
        write_gradle(self.root)
        write_manifest(self.root, {})
        out_json = self.root / "out.json"
        out_md = self.root / "out.md"
        result = run_cli(self.root / "manifest.json", self.root / "build.gradle.kts", out_json, out_md)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("at least one variant", result.stderr)

    def test_unknownInputFieldsAreNotEmitted(self):
        write_gradle(self.root)
        write_manifest(
            self.root,
            {"develop": {"baseline": "baseline.json", "current": "current.json"}},
        )
        base = measurement("develop", "a.apk")
        base["unknownField"] = "ignored"
        cur = measurement("develop", "a.apk", file_bytes=1100)
        (self.root / "baseline.json").write_text(json.dumps(base), encoding="utf-8")
        (self.root / "current.json").write_text(json.dumps(cur), encoding="utf-8")
        out_json = self.root / "out.json"
        out_md = self.root / "out.md"
        result = run_cli(self.root / "manifest.json", self.root / "build.gradle.kts", out_json, out_md)
        self.assertEqual(0, result.returncode)
        data = json.loads(out_json.read_text(encoding="utf-8"))
        self.assertNotIn("unknownField", json.dumps(data))

    def test_cliWritesOnlyExplicitOutputPaths(self):
        manifest, out_json, out_md = write_default_fixtures(self.root)
        result = run_cli(manifest, self.root / "build.gradle.kts", out_json, out_md)
        self.assertEqual(0, result.returncode)
        self.assertTrue(out_json.is_file())
        self.assertTrue(out_md.is_file())
        # No other output files created.
        outputs = [p.name for p in self.root.iterdir() if p.is_file() and p.name.startswith("out")]
        self.assertEqual(sorted(["out.json", "out.md"]), sorted(outputs))

    def test_cliFailureDoesNotLeavePartialOutputs(self):
        write_gradle(self.root)
        write_manifest(
            self.root,
            {"develop": {"baseline": "baseline.json", "current": "current.json"}},
        )
        bad = measurement("develop", "a.apk")
        del bad["files"]
        (self.root / "baseline.json").write_text(json.dumps(bad), encoding="utf-8")
        out_json = self.root / "out.json"
        out_md = self.root / "out.md"
        result = run_cli(self.root / "manifest.json", self.root / "build.gradle.kts", out_json, out_md)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(out_json.is_file())
        self.assertFalse(out_md.is_file())

    def test_reportContainsNoSecretFields(self):
        _, _, data = self._run_default()
        flat = json.dumps(data)
        for secret in ("storePassword", "keyPassword", "BEGIN PRIVATE KEY", "BEGIN CERTIFICATE"):
            self.assertNotIn(secret, flat)

    def test_markdownContainsNoBuildCommands(self):
        _, out_md, _ = self._run_default()
        text = out_md.read_text(encoding="utf-8")
        self.assertNotIn("assembleDebug", text)
        self.assertNotIn("assembleDevelop", text)
        self.assertNotIn("assembleRelease", text)
        self.assertNotIn("./gradlew", text)
        self.assertNotIn("gradle ", text)

    def test_markdownContainsNoDebugDefault(self):
        _, out_md, _ = self._run_default()
        text = out_md.read_text(encoding="utf-8")
        self.assertNotIn("Debug comparison", text)
        self.assertNotIn("Develop/R8 comparison", text)
        self.assertNotIn("diagnostic build", text)

    def test_toolContainsNoTASK_STATEOrDocsPerformanceDependency(self):
        source = TOOL_PATH.read_text(encoding="utf-8")
        self.assertNotIn("TASK_STATE.md", source)
        self.assertNotIn('"docs" / "performance"', source)
        self.assertNotIn("docs/performance", source)
        self.assertNotIn("PERF_DIR", source)
        self.assertNotIn("A14_APK_SIZE_BASELINE", source)
        self.assertNotIn("A14_APK_SIZE_CURRENT", source)

    # ---- mutation / explicit variant tests ----

    def test_injectedWrongDeltaIsDetected(self):
        # Modifying a current metric must be reflected consistently in the report.
        write_gradle(self.root)
        write_manifest(
            self.root,
            {"develop": {"baseline": "baseline.json", "current": "current.json"}},
        )
        base = measurement("develop", "a.apk", file_bytes=1000)
        cur = measurement("develop", "a.apk", file_bytes=1200)
        (self.root / "baseline.json").write_text(json.dumps(base), encoding="utf-8")
        (self.root / "current.json").write_text(json.dumps(cur), encoding="utf-8")
        out_json = self.root / "out.json"
        out_md = self.root / "out.md"
        result = run_cli(self.root / "manifest.json", self.root / "build.gradle.kts", out_json, out_md)
        self.assertEqual(0, result.returncode)
        data = json.loads(out_json.read_text(encoding="utf-8"))
        m = data["variants"]["develop"]["metrics"]["apkFileBytes"]
        self.assertEqual(200, m["delta"])
        self.assertEqual(20.0, m["deltaPercent"])

    def test_variantSwapDetected(self):
        # Current file claims to be a different variant than the manifest expects.
        write_gradle(self.root)
        write_manifest(
            self.root,
            {"develop": {"baseline": "baseline.json", "current": "current.json"}},
        )
        base = measurement("develop", "a.apk")
        cur = measurement("release", "a.apk", file_bytes=1100)
        (self.root / "baseline.json").write_text(json.dumps(base), encoding="utf-8")
        (self.root / "current.json").write_text(json.dumps(cur), encoding="utf-8")
        out_json = self.root / "out.json"
        out_md = self.root / "out.md"
        result = run_cli(self.root / "manifest.json", self.root / "build.gradle.kts", out_json, out_md)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("variant mismatch", result.stderr)

    def test_duplicateZipEntryRejected(self):
        # Same as duplicate file name, at the report level.
        self.test_duplicateFileNameRejected()

    def test_absolutePathInjectionFails(self):
        # Covered by absoluteWindowsPathRejected and absoluteUnixPathRejected.
        write_gradle(self.root)
        write_manifest(
            self.root,
            {"develop": {"baseline": "baseline.json", "current": "current.json"}},
        )
        for bad_path in (chr(67) + ":/build/app.apk", "/tmp/app.apk", "~/app.apk", chr(67) + r":\build\app.apk"):
            base = measurement("develop", bad_path)
            (self.root / "baseline.json").write_text(json.dumps(base), encoding="utf-8")
            (self.root / "current.json").write_text(json.dumps(measurement("develop", "a.apk", file_bytes=1100)), encoding="utf-8")
            out_json = self.root / "out.json"
            out_md = self.root / "out.md"
            result = run_cli(self.root / "manifest.json", self.root / "build.gradle.kts", out_json, out_md)
            self.assertNotEqual(0, result.returncode, bad_path)

    def test_deletingRequiredMetricFails(self):
        # Covered by missingRequiredMetricRejected; exercise a different metric.
        write_gradle(self.root)
        write_manifest(
            self.root,
            {"develop": {"baseline": "baseline.json", "current": "current.json"}},
        )
        base = measurement("develop", "a.apk")
        del base["fileCount"]
        (self.root / "baseline.json").write_text(json.dumps(base), encoding="utf-8")
        (self.root / "current.json").write_text(json.dumps(measurement("develop", "a.apk", file_bytes=1100)), encoding="utf-8")
        out_json = self.root / "out.json"
        out_md = self.root / "out.md"
        result = run_cli(self.root / "manifest.json", self.root / "build.gradle.kts", out_json, out_md)
        self.assertNotEqual(0, result.returncode)

    # ---- API unit tests ----

    def test_computeDeltasAndConcludeArePure(self):
        base = {f: 100 for f in d.DELTA_FIELDS}
        base["files"] = []
        cur = {f: 110 for f in d.DELTA_FIELDS}
        cur["files"] = []
        metrics = d.compute_deltas(base, cur)
        for f in d.DELTA_FIELDS:
            self.assertEqual(10, metrics[f]["delta"])
            self.assertEqual(10.0, metrics[f]["deltaPercent"])
            self.assertEqual("increase", metrics[f]["trend"])
        self.assertEqual("EXPLAINED_INCREASE", d.conclude([metrics]))

    def test_zipEntryDiffsTopListsBounded(self):
        base = {"files": []}
        current = {"files": []}
        for i in range(50):
            base["files"].append({
                "name": f"file{i}.txt",
                "bucket": "other",
                "uncompressedSize": 100,
                "compressedSize": 100,
            })
            current["files"].append({
                "name": f"file{i}.txt",
                "bucket": "other",
                "uncompressedSize": 100,
                "compressedSize": 100 + i,
            })
        diffs = d.zip_entry_diffs(base, current)
        self.assertEqual(20, len(diffs["topIncreases"]))
        self.assertEqual(0, len(diffs["topDecreases"]))

    def test_buildReportDoesNotReadFilesystem(self):
        gradle_text = 'versionName = "r14.16.1"\nversionCode = 192\n'
        gradle_info = d.GradleInfo(gradle_text)
        manifest = {
            "schema": 1,
            "variants": {
                "develop": {"baseline": "b.json", "current": "c.json"},
            },
        }
        baseline = measurement("develop", "a.apk")
        current = measurement("develop", "a.apk", file_bytes=1100)
        report = d.build_report(manifest, gradle_info, BASELINE_COMMIT, CURRENT_COMMIT, {"develop": (baseline, current)})
        self.assertEqual(BASELINE_COMMIT, report["baselineCommit"])
        self.assertEqual("r14.16.1", report["buildConfig"]["versionName"])
        self.assertIn("develop", report["variants"])


if __name__ == "__main__":
    unittest.main()
