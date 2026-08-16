"""Behavior-parity contracts for generated lazy preference resources and search data."""

from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SOURCE_DIR = REPO_ROOT / "app" / "src" / "main" / "res" / "xml"
GENERATOR = REPO_ROOT / "tools" / "generate_preference_artifacts.py"
ANDROID_NS = "http://schemas.android.com/apk/res/android"
AUTO_NS = "http://schemas.android.com/apk/res-auto"
ANDROID_KEY = f"{{{ANDROID_NS}}}key"
ANDROID_TITLE = f"{{{ANDROID_NS}}}title"
ANDROID_DEPENDENCY = f"{{{ANDROID_NS}}}dependency"
AUTO_CHILD = f"{{{AUTO_NS}}}child"
PREFERENCE_CATEGORY = "tv.withaibuild.customiuizer.prefs.PreferenceCategoryEx"

CATEGORY_SOURCES = {
    "pref_key_system": ("prefs_system.xml", "@string/system_mods"),
    "pref_key_launcher": ("prefs_launcher.xml", "@string/launcher_title"),
    "pref_key_controls": ("prefs_controls.xml", "@string/controls_mods"),
    "pref_key_various": ("prefs_various.xml", "@string/various_mods"),
}

EXPECTED_SPLITS = {
    "pref_key_system_cat_screen": "prefs_system_screen.xml",
    "pref_key_system_cat_audio": "prefs_system_audio.xml",
    "pref_key_system_cat_vibration": "prefs_system_vibration.xml",
    "pref_key_system_cat_toasts": "prefs_system_toasts.xml",
    "pref_key_system_cat_statusbar": "prefs_system_statusbar.xml",
    "pref_key_system_cat_drawer": "prefs_system_drawer.xml",
    "pref_key_system_cat_notifications": "prefs_system_notifications.xml",
    "pref_key_system_cat_qs": "prefs_system_qs.xml",
    "pref_key_system_cat_recents": "prefs_system_recents.xml",
    "pref_key_system_cat_betterpopups": "prefs_system_betterpopups.xml",
    "pref_key_system_cat_floatingwindows": "prefs_system_floatingwindows.xml",
    "pref_key_system_cat_applock": "prefs_system_applock.xml",
    "pref_key_system_cat_lockscreen": "prefs_system_lockscreen.xml",
    "pref_key_system_cat_other": "prefs_system_other.xml",
    "pref_key_launcher_cat_folders": "prefs_launcher_folders.xml",
    "pref_key_launcher_cat_titles": "prefs_launcher_titles.xml",
    "pref_key_launcher_cat_privacyapps": "prefs_launcher_privacyapps.xml",
    "pref_key_launcher_cat_gestures": "prefs_launcher_gestures.xml",
    "pref_key_launcher_cat_bugfixes": "prefs_launcher_bugfixes.xml",
    "pref_key_launcher_cat_other": "prefs_launcher_other.xml",
    "pref_key_controls_cat_fingerprint": "prefs_controls_fingerprint.xml",
    "pref_key_controls_cat_power": "prefs_controls_power.xml",
    "pref_key_controls_cat_volume": "prefs_controls_volume.xml",
    "pref_key_controls_cat_navbar": "prefs_controls_navbar.xml",
    "pref_key_controls_cat_fsg": "prefs_controls_fsg.xml",
    "pref_key_various_cat_general": "prefs_various_general.xml",
    "pref_key_various_cat_package_installer": "prefs_various_package_installer.xml",
    "pref_key_various_cat_security_center": "prefs_various_security_center.xml",
    "pref_key_various_cat_calls": "prefs_various_calls.xml",
    "pref_key_various_cat_settings": "prefs_various_settings.xml",
    "pref_key_various_cat_exclusive": "prefs_various_exclusive.xml",
    "pref_key_various_cat_gboard": "prefs_various_gboard.xml",
}

VARIOUS_GROUPS = (
    ("pref_key_various_cat_exclusive", "@string/various_exclusive_features_cat_title"),
    ("pref_key_various_cat_general", "@string/various_general_cat_title"),
    ("pref_key_various_cat_package_installer", "@string/various_package_installer_cat_title"),
    ("pref_key_various_cat_security_center", "@string/various_securitycenter_unlock_title"),
    ("pref_key_various_cat_calls", "@string/calls"),
    ("pref_key_various_cat_settings", "@string/various_app_management_cat_title"),
    ("pref_key_various_cat_gboard", "@string/gboard"),
)


def _signature(element: ET.Element) -> tuple:
    return (
        element.tag,
        tuple(sorted(element.attrib.items())),
        tuple(_signature(child) for child in list(element)),
    )


def _legacy_search_entries() -> list[dict[str, str]]:
    entries: list[dict[str, str]] = []
    for category, (source_name, category_title) in CATEGORY_SOURCES.items():
        root = ET.parse(SOURCE_DIR / source_name).getroot()
        last_sub = ""
        last_sub_title = ""
        last_sub_sub_title = ""
        order = 0
        for element in root.iter():
            if element is root:
                continue
            if element.tag == PREFERENCE_CATEGORY:
                key = element.get(ANDROID_KEY, "")
                if key:
                    last_sub = key
                    last_sub_title = element.get(ANDROID_TITLE, "")
                    last_sub_sub_title = ""
                    order = 1
                else:
                    last_sub_sub_title = element.get(ANDROID_TITLE, "")
                    order += 1
                continue

            if element.get(AUTO_CHILD, "false").lower() == "true":
                order += 1
                continue

            title = element.get(ANDROID_TITLE, "")
            if title.startswith("@string/"):
                entries.append(
                    {
                        "title": title,
                        "key": element.get(ANDROID_KEY, ""),
                        "category": category,
                        "categoryTitle": category_title,
                        "breadcrumbSubTitle": last_sub_title,
                        "breadcrumbSubSubTitle": last_sub_sub_title,
                        "legacySub": last_sub,
                        "order": str(order),
                    }
                )
            order += 1
    return entries


def _generated_search_entries(index_root: ET.Element) -> list[dict[str, str]]:
    entries: list[dict[str, str]] = []
    for category in list(index_root):
        for group in list(category):
            section_title = ""
            for child in list(group):
                if child.tag == "section":
                    section_title = child.get("title", "")
                    continue
                if child.tag != "mod":
                    raise AssertionError(f"Unexpected search index node: {child.tag}")
                entries.append(
                    {
                        **child.attrib,
                        "category": category.get("key", ""),
                        "categoryTitle": category.get("title", ""),
                        "routeSub": group.get("routeSub", ""),
                        "breadcrumbSubTitle": group.get("breadcrumbTitle", ""),
                        "breadcrumbSubSubTitle": section_title,
                    }
                )
    return entries


class GeneratePreferenceArtifactsTest(unittest.TestCase):

    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.output_dir = Path(self.temp_dir.name) / "generated"
        subprocess.run(
            [
                sys.executable,
                str(GENERATOR),
                "--source-dir",
                str(SOURCE_DIR),
                "--output-dir",
                str(self.output_dir),
            ],
            cwd=REPO_ROOT,
            check=True,
        )
        self.xml_dir = self.output_dir / "xml"

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_every_structured_category_is_an_exact_single_category_slice(self) -> None:
        found_keys: set[str] = set()
        for category in ("system", "launcher", "controls"):
            source_root = ET.parse(SOURCE_DIR / f"prefs_{category}.xml").getroot()
            for source_category in list(source_root):
                key = source_category.get(ANDROID_KEY, "")
                if not key:
                    continue
                found_keys.add(key)
                split_root = ET.parse(self.xml_dir / EXPECTED_SPLITS[key]).getroot()
                self.assertEqual(1, len(split_root))
                self.assertEqual(_signature(source_category), _signature(split_root[0]))

        self.assertEqual(set(EXPECTED_SPLITS) - {key for key, _ in VARIOUS_GROUPS}, found_keys)

    def test_various_groups_preserve_every_real_preference_once(self) -> None:
        source_root = ET.parse(SOURCE_DIR / "prefs_various.xml").getroot()
        source_preferences = [
            _signature(child)
            for child in list(source_root)
            if child.tag != PREFERENCE_CATEGORY
        ]
        generated_preferences: list[tuple] = []

        for key, title in VARIOUS_GROUPS:
            split_root = ET.parse(self.xml_dir / EXPECTED_SPLITS[key]).getroot()
            self.assertEqual(1, len(split_root))
            wrapper = split_root[0]
            self.assertEqual(PREFERENCE_CATEGORY, wrapper.tag)
            self.assertEqual(key, wrapper.get(ANDROID_KEY))
            self.assertEqual(title, wrapper.get(ANDROID_TITLE))
            generated_preferences.extend(_signature(child) for child in list(wrapper))

        self.assertEqual(source_preferences, generated_preferences)

    def test_every_dependency_target_remains_in_the_same_lazy_page(self) -> None:
        for output_name in EXPECTED_SPLITS.values():
            root = ET.parse(self.xml_dir / output_name).getroot()
            keys = {
                element.get(ANDROID_KEY)
                for element in root.iter()
                if element.get(ANDROID_KEY)
            }
            missing = {
                element.get(ANDROID_DEPENDENCY)
                for element in root.iter()
                if element.get(ANDROID_DEPENDENCY) not in (None, *keys)
            }
            self.assertEqual(set(), missing, output_name)

    def test_category_selectors_are_derived_from_generated_slices(self) -> None:
        for category in CATEGORY_SOURCES:
            suffix = category.removeprefix("pref_key_")
            selector = ET.parse(self.xml_dir / f"prefs_{suffix}_cat.xml").getroot()
            actual = [
                (child.get(ANDROID_KEY), child.get(ANDROID_TITLE))
                for child in list(selector)
            ]
            if category == "pref_key_various":
                expected = list(VARIOUS_GROUPS)
            else:
                source = ET.parse(SOURCE_DIR / f"prefs_{suffix}.xml").getroot()
                expected = [
                    (child.get(ANDROID_KEY), child.get(ANDROID_TITLE))
                    for child in list(source)
                    if child.get(ANDROID_KEY)
                ]
            self.assertEqual(expected, actual)

    def test_search_index_preserves_legacy_display_metadata_and_order(self) -> None:
        legacy = _legacy_search_entries()
        index_root = ET.parse(self.xml_dir / "mod_search_index.xml").getroot()
        generated = _generated_search_entries(index_root)
        comparable_generated = [
            {
                key: item.get(key, "")
                for key in (
                    "title",
                    "key",
                    "category",
                    "categoryTitle",
                    "breadcrumbSubTitle",
                    "breadcrumbSubSubTitle",
                    "order",
                )
            }
            for item in generated
        ]
        comparable_legacy = [
            {key: item[key] for key in comparable_generated[0]}
            for item in legacy
        ]
        self.assertEqual(comparable_legacy, comparable_generated)

        various_routes = {
            item["key"]: item.get("routeSub", "")
            for item in generated
            if item["category"] == "pref_key_various"
        }
        self.assertTrue(various_routes)
        self.assertNotIn("", various_routes.values())
        self.assertTrue(set(various_routes.values()).issubset({key for key, _ in VARIOUS_GROUPS}))
        expected_various_routes: dict[str, str] = {}
        various_root = ET.parse(SOURCE_DIR / "prefs_various.xml").getroot()
        group_index = -1
        for child in list(various_root):
            if child.tag == PREFERENCE_CATEGORY:
                group_index += 1
                continue
            for element in child.iter():
                key = element.get(ANDROID_KEY, "")
                title = element.get(ANDROID_TITLE, "")
                if key and title.startswith("@string/"):
                    expected_various_routes[key] = VARIOUS_GROUPS[group_index][0]
        self.assertEqual(expected_various_routes, various_routes)
        for expected, actual in zip(legacy, generated, strict=True):
            if expected["category"] != "pref_key_various":
                self.assertEqual(expected["legacySub"], actual.get("routeSub", ""))

    def test_generation_is_byte_for_byte_deterministic(self) -> None:
        subprocess.run(
            [
                sys.executable,
                str(GENERATOR),
                "--source-dir",
                str(SOURCE_DIR),
                "--output-dir",
                str(self.output_dir),
            ],
            cwd=REPO_ROOT,
            check=True,
        )
        second_output = Path(self.temp_dir.name) / "generated-second"
        subprocess.run(
            [
                sys.executable,
                str(GENERATOR),
                "--source-dir",
                str(SOURCE_DIR),
                "--output-dir",
                str(second_output),
            ],
            cwd=REPO_ROOT,
            check=True,
        )
        first_files = {
            path.name: path.read_bytes()
            for path in sorted(self.xml_dir.glob("*.xml"))
        }
        second_files = {
            path.name: path.read_bytes()
            for path in sorted((second_output / "xml").glob("*.xml"))
        }
        self.assertEqual(first_files, second_files)

    def test_runtime_uses_only_the_generated_index_and_build_wires_generation(self) -> None:
        helpers = (
            REPO_ROOT
            / "app/src/main/java/tv/withaibuild/customiuizer/utils/Helpers.kt"
        ).read_text(encoding="utf-8")
        build_script = (REPO_ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn("res.getXml(R.xml.mod_search_index)", helpers)
        self.assertNotIn("parsePrefXml(context", helpers)
        self.assertIn("generatePreferenceArtifacts", build_script)
        self.assertIn("dependsOn(generatePreferenceArtifacts)", build_script)
        self.assertIn("--catalog-output", build_script)
        self.assertIn("generatedPreferenceCatalogDir", build_script)
        self.assertIn("kotlin.directories.add", build_script)

        expected_files = set(EXPECTED_SPLITS.values()) | {
            "prefs_system_cat.xml",
            "prefs_launcher_cat.xml",
            "prefs_controls_cat.xml",
            "prefs_various_cat.xml",
            "mod_search_index.xml",
        }
        self.assertEqual(expected_files, {path.name for path in self.xml_dir.glob("*.xml")})
        canonical_size = sum(
            (SOURCE_DIR / source_name).stat().st_size
            for source_name, _ in CATEGORY_SOURCES.values()
        )
        self.assertLess(
            (self.xml_dir / "mod_search_index.xml").stat().st_size,
            canonical_size // 2,
        )

    def test_catalog_is_generated_from_xml_and_feature_keys(self) -> None:
        catalog_dir = Path(self.temp_dir.name) / "catalog"
        java_dir = REPO_ROOT / "app" / "src" / "main" / "java"
        subprocess.run(
            [
                sys.executable,
                str(GENERATOR),
                "--source-dir",
                str(SOURCE_DIR),
                "--output-dir",
                str(self.output_dir),
                "--catalog-output",
                str(catalog_dir),
                "--java-dir",
                str(java_dir),
            ],
            cwd=REPO_ROOT,
            check=True,
        )
        catalog = (
            catalog_dir
            / "tv"
            / "withaibuild"
            / "customiuizer"
            / "utils"
            / "CurrentPreferenceCatalog.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("internal object CurrentPreferenceCatalog", catalog)
        self.assertIn('"pref_key_miuizer_launchericon"', catalog)
        self.assertIn('"pref_key_system_strong_toast_mode"', catalog)
        self.assertNotIn('"pref_key_..."', catalog)
        self.assertNotIn("val ALL_VALID_KEYS", catalog)


if __name__ == "__main__":
    unittest.main()
