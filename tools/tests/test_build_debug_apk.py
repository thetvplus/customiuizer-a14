"""Debug-build output discovery must follow AGP metadata, not an old filename."""
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools import build_debug_apk as b


class DebugOutputDiscoveryTest(unittest.TestCase):
    def resolve_fixture(self, metadata, filenames=("current-debug.apk",)):
        with tempfile.TemporaryDirectory() as td:
            output = Path(td)
            (output / "output-metadata.json").write_text(json.dumps(metadata), encoding="utf-8")
            for name in filenames:
                (output / name).write_bytes(b"fixture APK")
            with patch.object(b, "APK_OUTPUT_DIR", output), \
                    patch.object(b.check_release_metadata, "parse_gradle_version", return_value=(230, "r14.22.7")):
                return b.resolve_debug_apk().name

    def metadata(self, **element_fields):
        return {"variantName": "debug", "elements": [{
            "versionCode": 230, "versionName": "r14.22.7-debug",
            "outputFile": "current-debug.apk", **element_fields,
        }]}

    def test_current_metadata_selects_output_even_with_old_apk_present(self):
        self.assertEqual("current-debug.apk", self.resolve_fixture(
            self.metadata(), ("current-debug.apk", "old-debug.apk")))

    def test_stale_version_name_and_code_are_rejected(self):
        for fields in ({"versionName": "r14.22.6-debug"}, {"versionCode": 229}):
            with self.subTest(fields=fields), self.assertRaises(SystemExit):
                self.resolve_fixture(self.metadata(**fields))

    def test_release_metadata_is_rejected(self):
        metadata = self.metadata()
        metadata["variantName"] = "release"
        with self.assertRaises(SystemExit):
            self.resolve_fixture(metadata)

    def test_multiple_outputs_are_rejected(self):
        metadata = self.metadata()
        metadata["elements"] *= 2
        with self.assertRaises(SystemExit):
            self.resolve_fixture(metadata)

    def test_missing_apk_does_not_fall_back_to_old_file(self):
        with self.assertRaises(SystemExit):
            self.resolve_fixture(self.metadata(), ("old-debug.apk",))

    def test_parent_traversal_is_rejected(self):
        with self.assertRaises(SystemExit):
            self.resolve_fixture(self.metadata(outputFile="../other.apk"))

    def test_non_apk_output_is_rejected(self):
        with self.assertRaises(SystemExit):
            self.resolve_fixture(self.metadata(outputFile="current-debug.zip"), ("current-debug.zip",))
