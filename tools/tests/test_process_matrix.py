"""Compare documented process eligibility with the current production source."""
import csv
import io
import json
import re
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch

from tools import extract_process_matrix
from tools.extract_process_matrix import FEATURE_DIR, extract_class_body
from tools.catalog_contract_probe import balanced_blocks, parse_feature_ids, parse_lazy_spec

ROOT = Path(__file__).resolve().parents[2]
MATRIX = ROOT / "docs/rom-intelligence/A14_PROCESS_MATRIX.json"


class ProcessMatrixGenerationTest(unittest.TestCase):
    def test_machine_outputs_preserve_all_features_scope_and_routing(self):
        with tempfile.TemporaryDirectory() as temporary:
            output_root = Path(temporary)
            with patch.object(extract_process_matrix, "REPO_ROOT", output_root), redirect_stdout(io.StringIO()):
                self.assertEqual(0, extract_process_matrix.main())
            output = output_root / "docs" / "rom-intelligence"
            self.assertEqual({"A14_PROCESS_MATRIX.json", "A14_PROCESS_MATRIX.csv"},
                             {path.name for path in output.iterdir()})
            generated = json.loads((output / "A14_PROCESS_MATRIX.json").read_text(encoding="utf-8"))
            self.assertEqual(json.loads(MATRIX.read_text(encoding="utf-8")), generated)
            with (output / "A14_PROCESS_MATRIX.csv").open(encoding="utf-8", newline="") as handle:
                rows = list(csv.DictReader(handle))
            for row in rows:
                row["featureIdInt"] = int(row["featureIdInt"])
            self.assertEqual(generated["features"], rows)


class ProcessMatrixEligibilityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.matrix = json.loads(MATRIX.read_text(encoding="utf-8"))
        cls.rows = {row["featureIdName"]: row for row in cls.matrix["features"]}

    def test_status_bar_height_matches_its_resource_package_allowlist(self):
        source = (FEATURE_DIR / "CommonPackageFeatures.kt").read_text(encoding="utf-8")
        package_set = re.search(r"RESOURCE_PACKAGES\s*=\s*setOf\((.*?)\)", source, re.S).group(1)
        actual = set(re.findall(r'"([^"]+)"', package_set))
        self.assertEqual(actual, set(self.rows["status_bar_height"]["allowedProcess"].split("; ")))

    def test_only_the_power_warning_enters_the_exact_security_center_power_process(self):
        rows = [row for row in self.rows.values()
                if "com.miui.securitycenter.remote" in row["allowedProcess"].split("; ")]
        self.assertEqual(["system_hidelowbatwarn"], [row["preferenceKey"] for row in rows])
        router = (ROOT / "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ProcessRouter.kt").read_text(encoding="utf-8")
        self.assertIn('"com.miui.securitycenter.remote" -> ProcessScope.SECURITY_CENTER_POWER', router)

    def test_every_registered_feature_is_present_in_the_current_matrix(self):
        identities = parse_feature_ids(FEATURE_DIR / "FeatureIds.kt")
        registered = set()
        for path in FEATURE_DIR.glob("*Features.kt"):
            for block in balanced_blocks(path.read_text(encoding="utf-8"), "LazyFeatureSpec"):
                spec = parse_lazy_spec(block)
                if spec is not None:
                    registered.add(identities[spec["id"]])
        self.assertEqual(registered, set(self.rows))

    def test_media_rows_match_their_package_enable_conditions(self):
        source = (FEATURE_DIR / "MediaFeatures.kt").read_text(encoding="utf-8")
        features = {
            "media_disable_unlock_wallpaper_scale": "MediaDisableUnlockWallpaperScaleFeature",
            "media_screenshot_config": "MediaScreenshotConfigFeature",
            "media_gallery_screenshot_path": "MediaGalleryScreenshotPathFeature",
        }
        for identifier, class_name in features.items():
            with self.subTest(feature=identifier):
                body = extract_class_body(source, class_name)
                package = re.search(r'packageName\s*==\s*"([^"]+)"', body).group(1)
                self.assertEqual(package, self.rows[identifier]["allowedProcess"])

    def test_package_installer_rows_match_router_package_allowlist(self):
        router = ROOT / "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ProcessRouter.kt"
        source = router.read_text(encoding="utf-8")
        packages = re.search(r"packageInstallerPackages\s*=\s*setOf\((.*?)\)", source, re.S).group(1)
        allowed = set(re.findall(r'"([^"]+)"', packages))
        for row in self.rows.values():
            if row["installer"] == "PackageInstallerFeatures":
                self.assertEqual(allowed, set(row["allowedProcess"].split("; ")))
        routing = {package for package, installer in self.matrix["routing"].items() if installer == "PackageInstallerRouter"}
        self.assertEqual(allowed, routing)

    def test_input_method_documentation_does_not_deny_scoped_keyboards(self):
        keyboard_rows = [row for row in self.rows.values() if row["installer"] == "InputMethodFeatures"]
        self.assertTrue(keyboard_rows)
        for row in keyboard_rows:
            for package in row["allowedProcess"].split("; "):
                if "*" not in package:
                    self.assertIn(package, self.matrix["scope"])
            self.assertNotEqual("not in scope.list", row["deniedProcess"])
