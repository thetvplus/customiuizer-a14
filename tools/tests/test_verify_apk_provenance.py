#!/usr/bin/env python3
"""Unit tests for APK provenance verification."""

import io
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import verify_apk_provenance


def make_fake_apk(props: dict[str, str]) -> Path:
    """Create a minimal fake APK with the given provenance properties."""
    text = "\n".join(f"{k}={v}" for k, v in props.items()) + "\n"
    handle, path = tempfile.mkstemp(suffix=".apk")
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("assets/build-provenance.properties", text.encode("utf-8"))
    return Path(path)


class VerifyApkProvenanceTest(unittest.TestCase):
    def test_valid_provenance_matches(self) -> None:
        apk = make_fake_apk(
            {
                "revision": "50a0ad4f",
                "versionName": "r14.16.1",
                "versionCode": "192",
                "buildType": "debug",
            }
        )
        result = verify_apk_provenance.verify_apk_provenance(apk, "50a0ad4f")
        self.assertEqual("50a0ad4f", result["revision"])
        self.assertEqual("debug", result["buildType"])

    def test_case_insensitive_match(self) -> None:
        apk = make_fake_apk(
            {
                "revision": "50A0AD4F",
                "versionName": "r14.16.1",
                "versionCode": "192",
                "buildType": "debug",
            }
        )
        result = verify_apk_provenance.verify_apk_provenance(apk, "50a0ad4f")
        self.assertEqual("50A0AD4F", result["revision"])

    def test_revision_mismatch(self) -> None:
        apk = make_fake_apk(
            {
                "revision": "50a0ad4f",
                "versionName": "r14.16.1",
                "versionCode": "192",
                "buildType": "debug",
            }
        )
        with self.assertRaises(RuntimeError):
            verify_apk_provenance.verify_apk_provenance(apk, "88809450")

    def test_invalid_expected_revision(self) -> None:
        apk = make_fake_apk(
            {
                "revision": "50a0ad4f",
                "versionName": "r14.16.1",
                "versionCode": "192",
                "buildType": "debug",
            }
        )
        with self.assertRaises(ValueError):
            verify_apk_provenance.verify_apk_provenance(apk, "not-a-sha")

    def test_missing_provenance_file(self) -> None:
        handle, path = tempfile.mkstemp(suffix=".apk")
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as zf:
            zf.writestr("assets/other.txt", b"other")
        with self.assertRaises(RuntimeError):
            verify_apk_provenance.verify_apk_provenance(Path(path), "50a0ad4f")

    def test_missing_required_field(self) -> None:
        apk = make_fake_apk({"revision": "50a0ad4f"})
        with self.assertRaises(RuntimeError):
            verify_apk_provenance.verify_apk_provenance(apk, "50a0ad4f")

    def test_non_integer_version_code(self) -> None:
        apk = make_fake_apk(
            {
                "revision": "50a0ad4f",
                "versionName": "r14.16.1",
                "versionCode": "not-an-int",
                "buildType": "debug",
            }
        )
        with self.assertRaises(RuntimeError):
            verify_apk_provenance.verify_apk_provenance(apk, "50a0ad4f")

    def test_wrong_build_type(self) -> None:
        apk = make_fake_apk(
            {
                "revision": "50a0ad4f",
                "versionName": "r14.16.1",
                "versionCode": "192",
                "buildType": "release",
            }
        )
        with self.assertRaises(RuntimeError):
            verify_apk_provenance.verify_apk_provenance(apk, "50a0ad4f")

    def test_release_build_type_matches(self) -> None:
        apk = make_fake_apk(
            {
                "revision": "50a0ad4f",
                "versionName": "r14.18.0",
                "versionCode": "193",
                "buildType": "release",
            }
        )
        result = verify_apk_provenance.verify_apk_provenance(
            apk, "50a0ad4f", expected_build_type="release"
        )
        self.assertEqual(result["buildType"], "release")

    def test_develop_build_type_matches(self) -> None:
        apk = make_fake_apk(
            {
                "revision": "50a0ad4f",
                "versionName": "r14.18.0",
                "versionCode": "193",
                "buildType": "develop",
            }
        )
        result = verify_apk_provenance.verify_apk_provenance(
            apk, "50a0ad4f", expected_build_type="develop"
        )
        self.assertEqual(result["buildType"], "develop")

    def test_version_name_mismatch(self) -> None:
        apk = make_fake_apk(
            {
                "revision": "50a0ad4f",
                "versionName": "r14.18.0",
                "versionCode": "193",
                "buildType": "release",
            }
        )
        with self.assertRaises(RuntimeError):
            verify_apk_provenance.verify_apk_provenance(
                apk,
                "50a0ad4f",
                expected_build_type="release",
                expected_version_name="r14.17.0",
            )

    def test_version_code_mismatch(self) -> None:
        apk = make_fake_apk(
            {
                "revision": "50a0ad4f",
                "versionName": "r14.18.0",
                "versionCode": "193",
                "buildType": "release",
            }
        )
        with self.assertRaises(RuntimeError):
            verify_apk_provenance.verify_apk_provenance(
                apk,
                "50a0ad4f",
                expected_build_type="release",
                expected_version_code="192",
            )


if __name__ == "__main__":
    unittest.main()
