"""Current preference XML, locale and released-control contracts."""

import collections
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
XML_DIR = REPO_ROOT / "app" / "src" / "main" / "res" / "xml"
RES_ROOT = REPO_ROOT / "app" / "src" / "main" / "res"
LOCALES = (
    "values",
    "values-zh-rCN",
    "values-zh-rTW",
    "values-ru-rRU",
    "values-ja-rJP",
    "values-vi-rVN",
    "values-cs-rCZ",
    "values-pt-rBR",
    "values-tr-rTR",
    "values-es-rES",
)

ANDROID_NS = "http://schemas.android.com/apk/res/android"

MIUIZER_NS = "http://schemas.android.com/apk/res-auto"

ATTR_KEY = f"{{{ANDROID_NS}}}key"

ATTR_TITLE = f"{{{ANDROID_NS}}}title"

CATEGORY_CLASS = "tv.withaibuild.customiuizer.prefs.PreferenceCategoryEx"

def _load_strings(folder: str) -> set[str]:
    tree = ET.parse(RES_ROOT / folder / "strings.xml")
    return {el.attrib["name"] for el in tree.getroot() if el.tag == "string"}

def _collect_elements(root: ET.Element) -> list[ET.Element]:
    """Return all non-root elements in document order (preflattened)."""
    result = []

    def visit(el: ET.Element):
        for child in el:
            result.append(child)
            visit(child)

    visit(root)
    return result

def _is_category(el: ET.Element) -> bool:
    return el.tag == CATEGORY_CLASS

def _pref_class(tag: str) -> str:
    return tag.rsplit(".", 1)[-1] if "." in tag else tag

class PreferenceResourceContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.base_strings = {locale: _load_strings(locale) for locale in LOCALES}

    def test_dock_page_preserves_geometry_keys_and_gates_only_background_options(self):
        root = ET.parse(XML_DIR / "prefs_launcher.xml").getroot()
        category = next(el for el in root if el.get(ATTR_KEY) == "pref_key_launcher_cat_dock")
        order = [el.get(ATTR_KEY) for el in root]
        dock = order.index("pref_key_launcher_cat_dock")
        self.assertEqual(["pref_key_launcher_cat_titles", "pref_key_launcher_cat_dock", "pref_key_launcher_cat_privacyapps"],
                         order[dock - 1:dock + 2])
        controls = {el.get(ATTR_KEY): el for el in category}
        master = "pref_key_launcher_dock_background"
        self.assertEqual("false", controls[master].get(f"{{{ANDROID_NS}}}defaultValue"))
        for suffix in ("material", "opacity", "radius", "inset"):
            self.assertEqual(master, controls[f"{master}_{suffix}"].get(f"{{{ANDROID_NS}}}dependency"))
        for key in ("launcher_unlockhotseat", "launcher_dock_height", "launcher_dock_topmargin", "launcher_dock_bottommargin"):
            self.assertNotIn(f"{{{ANDROID_NS}}}dependency", controls[f"pref_key_{key}"].attrib)
        self.assertEqual("60", controls["pref_key_launcher_dock_height"].get(f"{{{ANDROID_NS}}}defaultValue"))
        self.assertEqual("90", controls[f"{master}_opacity"].get(f"{{{ANDROID_NS}}}defaultValue"))
        arrays = ET.parse(RES_ROOT / "values" / "arrays.xml").getroot()
        material_entries = arrays.find("string-array[@name='launcher_dock_background_material']")
        material_values = arrays.find("string-array[@name='launcher_dock_background_material_values']")
        self.assertEqual(["0", "2"], [el.text for el in material_values])
        self.assertEqual("@string/launcher_dock_background_gaussian", material_entries[1].text)
        required = {"launcher_dock_category", "launcher_dock_background_title", "launcher_dock_background_summ",
                    "launcher_dock_background_material_title", "launcher_dock_background_auto_glass",
                    "launcher_dock_background_opacity_title",
                    "launcher_dock_background_gaussian", "launcher_dock_background_material_summ",
                    "launcher_dock_background_radius_title", "launcher_dock_background_inset_title",
                    "launcher_dock_background_auto"}
        for locale, strings in self.base_strings.items():
            with self.subTest(locale=locale):
                self.assertTrue(required <= strings, required - strings)

    def test_duo_charging_bolt_keeps_independent_exact_attributes(self):
        root = ET.parse(XML_DIR / "prefs_system_statusbar_duo.xml").getroot()
        key = "pref_key_system_statusbar_duo_chargingbolt"
        matches = [el for el in root.iter() if el.get(ATTR_KEY) == key]
        self.assertEqual(1, len(matches), f"missing or duplicate charging bolt control: {key}")
        self.assertEqual("tv.withaibuild.customiuizer.prefs.CheckBoxPreferenceEx", matches[0].tag)
        self.assertEqual({
            ATTR_KEY: key,
            ATTR_TITLE: "@string/system_statusbar_duo_chargingbolt_title",
            f"{{{ANDROID_NS}}}summary": "@string/system_statusbar_duo_chargingbolt_summ",
            f"{{{ANDROID_NS}}}dependency": "pref_key_system_statusbar_duo",
            f"{{{ANDROID_NS}}}defaultValue": "true",
        }, dict(matches[0].attrib))

    def test_tile_background_color_depends_on_enabled_color_exactly(self):
        root = ET.parse(XML_DIR / "prefs_system_controlcenter_themestyle.xml").getroot()
        key = "pref_key_system_cc_tile_enabled_color_custom"
        matches = [el for el in root.iter() if el.get(ATTR_KEY) == key]
        self.assertEqual(1, len(matches), f"missing or duplicate tile background color: {key}")
        self.assertEqual("tv.withaibuild.customiuizer.prefs.ColorPreferenceEx", matches[0].tag)
        self.assertEqual({
            ATTR_KEY: key,
            ATTR_TITLE: "@string/common_background_color",
            f"{{{ANDROID_NS}}}dependency": "pref_key_system_cc_tile_enabled_color",
            f"{{{MIUIZER_NS}}}indentLevel": "1",
        }, dict(matches[0].attrib))

    def test_released_left_icon_controls_keep_exact_attributes(self):
        root = ET.parse(XML_DIR / "prefs_system_statusbar_righticons.xml").getroot()
        expected = {
            "pref_key_system_statusbar_lefticons_scale": {
                f"{{{ANDROID_NS}}}title": "@string/system_statusbar_lefticons_scale_title",
                f"{{{ANDROID_NS}}}summary": "@string/system_statusbar_lefticons_adjust_summ",
                f"{{{ANDROID_NS}}}defaultValue": "100",
                f"{{{MIUIZER_NS}}}minValue": "75",
                f"{{{MIUIZER_NS}}}maxValue": "125",
                f"{{{MIUIZER_NS}}}stepValue": "5",
                f"{{{MIUIZER_NS}}}format": "%d%%",
            },
            "pref_key_system_statusbar_lefticons_verticaloffset": {
                f"{{{ANDROID_NS}}}title": "@string/system_statusbar_lefticons_verticaloffset_title",
                f"{{{ANDROID_NS}}}summary": "@string/system_statusbar_lefticons_offset_summ",
                f"{{{ANDROID_NS}}}defaultValue": "12",
                f"{{{MIUIZER_NS}}}minValue": "0",
                f"{{{MIUIZER_NS}}}maxValue": "24",
                f"{{{MIUIZER_NS}}}negativeShift": "12",
                f"{{{MIUIZER_NS}}}displayDividerValue": "2",
                f"{{{MIUIZER_NS}}}stepValue": "1",
                f"{{{MIUIZER_NS}}}format": "%.1f dp",
            },
        }
        for key, attrs in expected.items():
            with self.subTest(key=key):
                matches = [el for el in root.iter() if el.get(ATTR_KEY) == key]
                self.assertEqual(1, len(matches), f"missing or duplicate released key: {key}")
                self.assertEqual("tv.withaibuild.customiuizer.prefs.SeekBarPreference", matches[0].tag)
                self.assertEqual({
                    ATTR_KEY: key,
                    f"{{{ANDROID_NS}}}dependency": "pref_key_system_statusbaricons_wifi_mobile_atleft",
                    f"{{{MIUIZER_NS}}}indentLevel": "1",
                    **attrs,
                }, dict(matches[0].attrib))

    def test_new_category_titles_localized_in_all_locales(self):
        required = {
            "system_mods_connectivity",
            "system_lsalarm_title",
            "system_albumartonlock_title",
            "system_autobrightness_title",
            "system_batteryindicator_title",
            "system_charginginfo_title",
            "system_statusbar_clocktweak_title",
            "system_lockscreenshortcuts_title",
            "system_noscreenlock_title",
            "system_screenshot_title",
            "system_statusbar_batterytempandcurrent_title",
            "system_statusbar_showdevicetemperature_title",
            "system_visualizer_title",
            "various_calluibright_title",
        }
        missing: dict[str, list[str]] = collections.defaultdict(list)
        for locale, names in self.base_strings.items():
            for key in required:
                if key not in names:
                    missing[locale].append(key)
        self.assertFalse(
            dict(missing),
            f"Required category title strings missing in locales: {dict(missing)}",
        )

    def test_all_category_title_references_resolve_in_default(self):
        unresolved = set()
        for path in sorted(XML_DIR.glob("prefs_*.xml")):
            root = ET.parse(path).getroot()
            for el in _collect_elements(root):
                if _is_category(el):
                    title_ref = el.get(ATTR_TITLE, "")
                    if title_ref.startswith("@string/"):
                        name = title_ref[8:]
                        if name not in self.base_strings["values"]:
                            unresolved.add((path.name, name))
        self.assertFalse(
            unresolved,
            f"Category title references missing in values/strings.xml: {unresolved}",
        )

    def test_no_duplicate_preference_keys(self):
        seen: dict[str, str] = {}
        for path in sorted(XML_DIR.glob("prefs_*.xml")):
            root = ET.parse(path).getroot()
            for el in _collect_elements(root):
                if _is_category(el):
                    continue
                key = el.get(ATTR_KEY)
                if not key:
                    continue
                self.assertNotIn(
                    key,
                    seen,
                    f"Duplicate preference key {key!r} in {path.name} and {seen.get(key)}",
                )
                seen[key] = path.name

    def test_usb_preference_contract(self):
        root = ET.parse(XML_DIR / "prefs_system.xml").getroot()
        usb = None
        for el in _collect_elements(root):
            if el.get(ATTR_KEY) == "pref_key_system_usb_default_function":
                usb = el
                break
        self.assertIsNotNone(usb)
        self.assertEqual(_pref_class(usb.tag), "ListPreferenceEx")
        self.assertEqual(usb.get(f"{{{ANDROID_NS}}}title"), "@string/system_usb_default_function_title")
        self.assertEqual(usb.get(f"{{{ANDROID_NS}}}entries"), "@array/usb_default_functions")
        self.assertEqual(usb.get(f"{{{ANDROID_NS}}}entryValues"), "@array/usb_default_functions_val")
        self.assertEqual(usb.get(f"{{{ANDROID_NS}}}defaultValue"), "0")

    def test_recents_hide_app_name_contract(self):
        root = ET.parse(XML_DIR / "prefs_system.xml").getroot()
        recents = None
        for el in _collect_elements(root):
            if el.get(ATTR_KEY) == "pref_key_system_recents_card_style":
                recents = el
                break
        self.assertIsNotNone(recents)
        self.assertEqual(_pref_class(recents.tag), "CheckBoxPreferenceEx")
        self.assertEqual(
            recents.get(f"{{{ANDROID_NS}}}title"),
            "@string/system_recents_card_style_hide_title",
        )

if __name__ == "__main__":
    unittest.main()
