#!/usr/bin/env python3
"""Verify that build-provenance.properties is generated per variant with correct values."""

import configparser
import os
import re
import shutil
import subprocess
import sys
import unittest
from pathlib import Path

_REPO_ROOT = Path(__file__).resolve().parent.parent.parent
_GRADLEW = "gradlew.bat" if os.name == "nt" else "gradlew"
_GRADLEW_PATH = _REPO_ROOT / _GRADLEW

_VARIANTS = ("debug", "develop", "release")


def _current_revision() -> str:
    result = subprocess.run(
        ["git", "rev-parse", "--short=8", "HEAD"],
        cwd=_REPO_ROOT,
        capture_output=True,
        text=True,
        check=True,
    )
    rev = result.stdout.strip()
    if not re.fullmatch(r"[0-9a-fA-F]{8}", rev):
        raise RuntimeError(f"invalid 8-character revision: {rev!r}")
    return rev.lower()


def _run_gradle(*args: str) -> str:
    if not _GRADLEW_PATH.is_file():
        raise unittest.SkipTest(f"gradle wrapper not found: {_GRADLEW_PATH}")
    cmd = [str(_GRADLEW_PATH), "--no-daemon", "--no-configuration-cache"] + list(args)
    env = os.environ.copy()
    java_home = Path(env.get("JAVA_HOME", "").strip().strip('"'))
    java_name = "java.exe" if sys.platform == "win32" else "java"
    if java_home.name.lower() == "bin" and (java_home / java_name).is_file():
        env["JAVA_HOME"] = str(java_home.parent)
    result = subprocess.run(
        cmd,
        cwd=_REPO_ROOT,
        env=env,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise AssertionError(
            f"gradle failed: {' '.join(cmd)}\nstdout:\n{result.stdout}\nstderr:\n{result.stderr}"
        )
    return result.stdout


def _read_generated_props(variant: str) -> dict[str, str]:
    path = _REPO_ROOT / "app" / "build" / "generated" / "assets" / "build-provenance" / variant / "build-provenance.properties"
    if not path.is_file():
        raise AssertionError(f"missing generated provenance for {variant}: {path}")
    cp = configparser.ConfigParser()
    cp.optionxform = str  # preserve original key case
    # Add a dummy section so ConfigParser can read bare key=value files.
    with path.open("r", encoding="utf-8") as f:
        cp.read_string("[DEFAULT]\n" + f.read())
    return dict(cp["DEFAULT"])


def _read_merged_props(variant: str) -> dict[str, str]:
    variant_cap = variant.capitalize()
    path = _REPO_ROOT / "app" / "build" / "intermediates" / "assets" / variant / f"merge{variant_cap}Assets" / "build-provenance.properties"
    if not path.is_file():
        raise AssertionError(f"missing merged provenance for {variant}: {path}")
    cp = configparser.ConfigParser()
    cp.optionxform = str
    with path.open("r", encoding="utf-8") as f:
        cp.read_string("[DEFAULT]\n" + f.read())
    return dict(cp["DEFAULT"])


class BuildProvenanceVariantTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.revision = _current_revision()
        # Generate all three provenance files and merge them into assets.
        _run_gradle(
            ":app:mergeDebugAssets",
            ":app:mergeDevelopAssets",
            ":app:mergeReleaseAssets",
            "--rerun-tasks",
            f"-PbuildRevision={cls.revision}",
            "-PrequireBuildRevision=true",
        )

    def _expected_version(self) -> tuple[str, str]:
        build_file = (_REPO_ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
        version_name_match = re.search(r'val lastVersionName\s*=\s*"([^"]+)"', build_file)
        version_code_match = re.search(r'val lastVersion\s*=\s*(\d+)', build_file)
        if not version_name_match or not version_code_match:
            raise AssertionError("could not find lastVersionName or lastVersion in build.gradle.kts")
        return version_name_match.group(1), version_code_match.group(1)

    def test_revision_is_eight_character_hex(self):
        self.assertTrue(
            re.fullmatch(r"[0-9a-fA-F]{8}", self.revision),
            f"revision must be 8-character hex: {self.revision}",
        )

    def test_each_variant_generated_provenance_is_correct(self):
        expected_version_name, expected_version_code = self._expected_version()
        for variant in _VARIANTS:
            with self.subTest(variant=variant):
                props = _read_generated_props(variant)
                self.assertEqual(props["buildType"], variant)
                self.assertEqual(props["revision"].lower(), self.revision)
                self.assertEqual(props["versionName"], expected_version_name)
                self.assertEqual(props["versionCode"], expected_version_code)

    def test_each_variant_merged_assets_include_provenance(self):
        expected_version_name, expected_version_code = self._expected_version()
        for variant in _VARIANTS:
            with self.subTest(variant=variant):
                props = _read_merged_props(variant)
                self.assertEqual(props["buildType"], variant)
                self.assertEqual(props["revision"].lower(), self.revision)
                self.assertEqual(props["versionName"], expected_version_name)
                self.assertEqual(props["versionCode"], expected_version_code)

    def test_release_uses_release_directory_not_debug(self):
        # The release provenance must live under build-provenance/release,
        # not share the legacy top-level or debug directory.
        release_path = _REPO_ROOT / "app" / "build" / "generated" / "assets" / "build-provenance" / "release" / "build-provenance.properties"
        self.assertTrue(release_path.is_file())

        top_level_path = _REPO_ROOT / "app" / "build" / "generated" / "assets" / "build-provenance" / "build-provenance.properties"
        if top_level_path.is_file():
            # If an old build left a top-level file, it must not be the one
            # referenced by the release merged assets.
            merged_release = _read_merged_props("release")
            self.assertEqual(merged_release["buildType"], "release")

        # Release merged path should not sit under debug.
        self.assertNotIn("debug", release_path.as_posix().lower())

    def test_merge_release_assets_depends_on_write_release_build_provenance(self):
        output = _run_gradle(":app:mergeReleaseAssets", "--dry-run")
        self.assertIn("writeReleaseBuildProvenance", output)
        self.assertIn("mergeReleaseAssets", output)
        write_pos = output.find("writeReleaseBuildProvenance")
        merge_pos = output.find("mergeReleaseAssets")
        self.assertLess(
            write_pos,
            merge_pos,
            "writeReleaseBuildProvenance must appear before mergeReleaseAssets in dry-run",
        )


if __name__ == "__main__":
    unittest.main()
