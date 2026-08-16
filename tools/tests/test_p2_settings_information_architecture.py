"""P2 Settings information-architecture structural contract checks.

Compares current user-facing preference XMLs against P2 base SHA and enforces
the contract: only approved summary removals and category-title renames are
allowed; all other preference and category attributes must be identical.
"""

import collections
import subprocess
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
P2_BASE_SHA = "f9bee6adf1f56ae18b342417a18730eaac40bd2d"

ANDROID_NS = "http://schemas.android.com/apk/res/android"
MIUIZER_NS = "http://schemas.android.com/apk/res-auto"
APP_NS = "http://schemas.android.com/apk/res-auto"

ATTR_KEY = f"{{{ANDROID_NS}}}key"
ATTR_TITLE = f"{{{ANDROID_NS}}}title"
CATEGORY_CLASS = "tv.withaibuild.customiuizer.prefs.PreferenceCategoryEx"

# Approved P2 production changes ------------------------------------------------

# Preference keys whose android:summary was intentionally removed.
P2_REMOVED_SUMMARY_KEYS = frozenset({
    "pref_key_system_fivegtile",
    "pref_key_system_recents_blur",
})

# Category title renames approved for P2.
# Key = file name
# Value = { child_key_set: (old_title_ref, new_title_ref) }
P2_ALLOWED_CATEGORY_RENAMES: dict[str, dict[frozenset[str], tuple[str, str]]] = {
    "prefs_system_alarmonlock.xml": {
        frozenset({"pref_key_system_lsalarm_all", "pref_key_system_lsalarm_format"}): (
            "@string/settings",
            "@string/system_lsalarm_title",
        ),
    },
    "prefs_system_albumartonlock.xml": {
        frozenset({"pref_key_system_albumartonlock_gray", "pref_key_system_albumartonlock_blur", "pref_key_system_albumartonlock_scale"}): (
            "@string/settings",
            "@string/system_albumartonlock_title",
        ),
    },
    "prefs_system_autobrightness.xml": {
        frozenset({
            "pref_key_system_autobrightness_limitmin",
            "pref_key_system_autobrightness_min",
            "pref_key_system_autobrightness_limitmax",
            "pref_key_system_autobrightness_max",
        }): (
            "@string/settings",
            "@string/system_autobrightness_title",
        ),
    },
    "prefs_system_batteryindicator.xml": {
        frozenset({
            "pref_key_system_batteryindicator_align",
            "pref_key_system_batteryindicator_limitvis",
            "pref_key_system_batteryindicator_rounded",
            "pref_key_system_batteryindicator_centered",
            "pref_key_system_batteryindicator_height",
            "pref_key_system_batteryindicator_padding",
            "pref_key_system_batteryindicator_transp",
            "pref_key_system_batteryindicator_glow",
            "pref_key_system_batteryindicator_color",
            "pref_key_system_batteryindicator_colorval1",
            "pref_key_system_batteryindicator_colorval2",
            "pref_key_system_batteryindicator_colorval3",
            "pref_key_system_batteryindicator_colorval4",
            "pref_key_system_batteryindicator_lowlevel",
            "pref_key_system_batteryindicator_test",
        }): (
            "@string/settings",
            "@string/system_batteryindicator_title",
        ),
    },
    "prefs_system_charginginfo.xml": {
        frozenset({
            "pref_key_system_charginginfo_view",
            "pref_key_system_charginginfo_fontsize",
            "pref_key_system_charginginfo_current",
            "pref_key_system_charginginfo_voltage",
            "pref_key_system_charginginfo_wattage",
            "pref_key_system_charginginfo_temp",
        }): (
            "@string/settings",
            "@string/system_charginginfo_title",
        ),
    },
    "prefs_system_controlcenter_clock.xml": {
        frozenset({
            "pref_key_system_cc_clock_fontsize",
            "pref_key_system_cc_clock_verticaloffset",
            "pref_key_system_cc_clock_customformat",
        }): (
            "@string/settings",
            "@string/system_statusbar_clocktweak_title",
        ),
    },
    "prefs_system_lockscreenshortcuts.xml": {
        frozenset({
            "pref_key_system_lockscreenshortcuts_right_off",
            "pref_key_system_lockscreenshortcuts_right",
            "pref_key_system_lockscreenshortcuts_left_off",
            "pref_key_system_lockscreenshortcuts_left_tapaction",
        }): (
            "@string/settings",
            "@string/system_lockscreenshortcuts_title",
        ),
    },
    "prefs_system_noscreenlock.xml": {
        frozenset({
            "pref_key_system_noscreenlock",
            "pref_key_system_noscreenlock_req",
            "pref_key_system_noscreenlock_skip",
            "pref_key_system_noscreenlock_wifi",
            "pref_key_system_noscreenlock_bt",
        }): (
            "@string/settings",
            "@string/system_noscreenlock_title",
        ),
    },
    "prefs_system_screenshot.xml": {
        frozenset({
            "pref_key_system_screenshot_format",
            "pref_key_system_screenshot_quality",
            "pref_key_system_screenshot_path",
            "pref_key_system_screenshot_mypath",
        }): (
            "@string/settings",
            "@string/system_screenshot_title",
        ),
    },
    "prefs_system_statusbar_batterytempandcurrent.xml": {
        frozenset({
            "pref_key_system_statusbar_batterytempandcurrent_content",
            "pref_key_system_statusbar_batterytempandcurrent_hideunit",
            "pref_key_system_statusbar_batterytempandcurrent_atright",
            "pref_key_system_statusbar_batterytempandcurrent_temp_decimal",
            "pref_key_system_statusbar_batterytempandcurrent_positive",
            "pref_key_system_statusbar_batterytempandcurrent_fixcurrentratio",
            "pref_key_system_statusbar_batterytempandcurrent_singlerow",
            "pref_key_system_statusbar_batterytempandcurrent_reverseorder",
            "pref_key_system_statusbar_batterytempandcurrent_incharge",
            "pref_key_system_statusbar_batterytempandcurrent_fontsize",
            "pref_key_system_statusbar_batterytempandcurrent_bold",
            "pref_key_system_statusbar_batterytempandcurrent_align",
            "pref_key_system_statusbar_batterytempandcurrent_fixedcontent_width",
            "pref_key_system_statusbar_batterytempandcurrent_leftmargin",
            "pref_key_system_statusbar_batterytempandcurrent_rightmargin",
            "pref_key_system_statusbar_batterytempandcurrent_verticaloffset",
        }): (
            "@string/settings",
            "@string/system_statusbar_batterytempandcurrent_title",
        ),
    },
    "prefs_system_statusbar_clock.xml": {
        frozenset({
            "pref_key_system_statusbar_clock_fontsize",
            "pref_key_system_statusbar_clock_align",
            "pref_key_system_statusbar_clock_bold",
            "pref_key_system_statusbar_clock_chip",
            "pref_key_system_statusbar_clock_chip_usemonet",
            "pref_key_system_statusbar_clock_chip_orientation_vertical",
            "pref_key_system_statusbar_clock_chip_startcolor",
            "pref_key_system_statusbar_clock_chip_endcolor",
            "pref_key_system_statusbar_clock_chip_customtextcolor",
            "pref_key_system_statusbar_clock_chip_textcolor",
            "pref_key_system_statusbar_clock_chip_horizpadding",
            "pref_key_system_statusbar_clock_chip_verticalpadding",
            "pref_key_system_statusbar_clock_chip_radius",
            "pref_key_system_statusbar_clock_leftmargin",
            "pref_key_system_statusbar_clock_rightmargin",
            "pref_key_system_statusbar_clock_verticaloffset",
            "pref_key_system_statusbar_clock_show_seconds",
            "pref_key_system_statusbar_clock_fixedcontent_width",
            "pref_key_system_statusbar_clock_24hour_format",
            "pref_key_system_statusbar_clock_show_ampm",
            "pref_key_system_statusbar_clock_leadingzero",
            "pref_key_system_statusbar_clock_customformat_enable",
            "pref_key_system_statusbar_clock_customformat",
        }): (
            "@string/settings",
            "@string/system_statusbar_clocktweak_title",
        ),
    },
    "prefs_system_statusbar_showdevicetemperature.xml": {
        frozenset({
            "pref_key_system_statusbar_showdevicetemperature_content",
            "pref_key_system_statusbar_showdevicetemperature_hideunit",
            "pref_key_system_statusbar_showdevicetemperature_atright",
            "pref_key_system_statusbar_showdevicetemperature_singlerow",
            "pref_key_system_statusbar_showdevicetemperature_reverseorder",
            "pref_key_system_statusbar_showdevicetemperature_fontsize",
            "pref_key_system_statusbar_showdevicetemperature_bold",
            "pref_key_system_statusbar_showdevicetemperature_align",
            "pref_key_system_statusbar_showdevicetemperature_fixedcontent_width",
            "pref_key_system_statusbar_showdevicetemperature_leftmargin",
            "pref_key_system_statusbar_showdevicetemperature_rightmargin",
            "pref_key_system_statusbar_showdevicetemperature_verticaloffset",
        }): (
            "@string/settings",
            "@string/system_statusbar_showdevicetemperature_title",
        ),
    },
    "prefs_system_visualizer.xml": {
        frozenset({
            "pref_key_system_visualizer_custom",
            "pref_key_system_visualizer_drawer",
            "pref_key_system_visualizer_controller",
            "pref_key_system_visualizer_animdur",
            "pref_key_system_visualizer_transp",
            "pref_key_system_visualizer_glowlevel",
            "pref_key_system_visualizer_render",
            "pref_key_system_visualizer_style",
            "pref_key_system_visualizer_color",
            "pref_key_system_visualizer_dyntime",
            "pref_key_system_visualizer_colorval",
        }): (
            "@string/settings",
            "@string/system_visualizer_title",
        ),
    },
    "prefs_various_calluibright.xml": {
        frozenset({
            "pref_key_various_calluibright_type",
            "pref_key_various_calluibright_val",
            "pref_key_various_calluibright_night",
            "pref_key_various_calluibright_night_start",
            "pref_key_various_calluibright_night_end",
        }): (
            "@string/settings",
            "@string/various_calluibright_title",
        ),
    },
}

# Categories that are allowed to be removed vs the base (file, child_keys, old_title_ref).
P2_ALLOWED_REMOVED_CATEGORIES: set[tuple[str, frozenset[str], str]] = {
    ("prefs_system_secureqs.xml", frozenset({"pref_key_system_secureqs_keepopened"}), "@string/settings"),
    ("prefs_system_statusbar_batterystyle.xml", frozenset(), "@string/settings"),
}

# New categories that did not exist in the base (file, child_keys, new_title_ref).
P2_ALLOWED_NEW_CATEGORIES: set[tuple[str, frozenset[str], str]] = {
    ("prefs_system.xml", frozenset(), "@string/system_mods_connectivity"),
}

# Preferences added after the P2 freeze that are independently authorized.
# Keyed by file name, value is a set of allowed added keys.
POST_P2_ALLOWED_NEW_PREFERENCES: dict[str, set[str]] = {
    "prefs_controls.xml": {
        "pref_key_controls_hide_ime_dismiss_button",
    },
    "prefs_system.xml": {
        "pref_key_system_strong_toast_island_offset",
    },
}

# r14.20.0 removes the Dynamic Island Bottom-only configuration. Persisted values are
# intentionally migrated to TOP by StrongToastPosition; the UI keys must not reappear.
POST_P2_ALLOWED_REMOVED_PREFERENCES: dict[str, set[str]] = {
    "prefs_system.xml": {
        "pref_key_system_strong_toast_position",
        "pref_key_system_strong_toast_bottom_offset",
    },
}

# Attribute changes authorized after P2 (e.g. resourceizing hard-coded literals).
# Tuple: (file_name, preference_key, attr, old_value, new_value)
POST_P2_ALLOWED_ATTR_CHANGES: set[tuple[str, str, str, str, str]] = {
    ("prefs_system_hideicons.xml", "pref_key_system_statusbaricons_sim1", f"{{{ANDROID_NS}}}title", "SIM 1", "@string/system_hideicons_sim1_title"),
    ("prefs_system_hideicons.xml", "pref_key_system_statusbaricons_sim2", f"{{{ANDROID_NS}}}title", "SIM 2", "@string/system_hideicons_sim2_title"),
}

# -------------------------------------------------------------------------------


def _git_show(path: str, ref: str = P2_BASE_SHA) -> str:
    return subprocess.run(
        ["git", "show", f"{ref}:{path}"],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        check=True,
    ).stdout


def _load_strings(folder: str) -> set[str]:
    tree = ET.parse(RES_ROOT / folder / "strings.xml")
    return {el.attrib["name"] for el in tree.getroot() if el.tag == "string"}


def _parse_xml(text: str) -> ET.Element:
    return ET.fromstring(text)


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


def _child_keys(el: ET.Element) -> frozenset[str]:
    keys: set[str] = set()
    for child in el:
        if not _is_category(child):
            key = child.get(ATTR_KEY)
            if key:
                keys.add(key)
        else:
            keys.update(_child_keys(child))
    return frozenset(keys)


def _attr_map(el: ET.Element) -> dict[str, str]:
    return dict(el.attrib)


def _pref_class(tag: str) -> str:
    return tag.rsplit(".", 1)[-1] if "." in tag else tag


class P2SettingsInformationArchitectureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.base_strings = {locale: _load_strings(locale) for locale in LOCALES}

    # ------------------------------------------------------------------ Locale

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

    def test_default_and_locales_cover_new_category_string(self):
        for locale in LOCALES:
            with self.subTest(locale=locale):
                self.assertIn(
                    "system_mods_connectivity",
                    self.base_strings[locale],
                    f"system_mods_connectivity must exist in {locale}/strings.xml",
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

    # ----------------------------------------------------------- Duplicate keys

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

    # ----------------------------------------------------------------- Contract

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

    def test_full_structural_contract_against_base(self):
        failures = []

        for path in sorted(XML_DIR.glob("prefs_*.xml")):
            current_root = ET.parse(path).getroot()
            try:
                base_text = _git_show(f"app/src/main/res/xml/{path.name}")
            except subprocess.CalledProcessError:
                continue
            base_root = _parse_xml(base_text)

            # --- keyed preferences ---
            base_prefs: dict[str, dict[str, str]] = {}
            current_prefs: dict[str, dict[str, str]] = {}

            def collect_prefs(root: ET.Element, dest: dict[str, dict[str, str]], source: str):
                for el in _collect_elements(root):
                    if _is_category(el):
                        continue
                    key = el.get(ATTR_KEY)
                    if not key:
                        continue
                    if key in dest:
                        failures.append(f"{source} duplicate key {key}")
                        continue
                    dest[key] = _attr_map(el)

            collect_prefs(base_root, base_prefs, f"BASE/{path.name}")
            collect_prefs(current_root, current_prefs, f"CURRENT/{path.name}")

            added_keys = set(current_prefs) - set(base_prefs)
            removed_keys = set(base_prefs) - set(current_prefs)

            # Allow post-P2 authorized additions (e.g. P4 feature wiring).
            allowed_added = POST_P2_ALLOWED_NEW_PREFERENCES.get(path.name, set())
            added_keys -= allowed_added
            allowed_removed = POST_P2_ALLOWED_REMOVED_PREFERENCES.get(path.name, set())
            removed_keys -= allowed_removed

            if added_keys or removed_keys:
                failures.append(
                    f"{path.name}: added keys={sorted(added_keys)}, removed keys={sorted(removed_keys)}"
                )
                continue

            for key, base_attrs in base_prefs.items():
                if key in allowed_removed:
                    continue
                cur_attrs = current_prefs[key]
                for attr, base_val in base_attrs.items():
                    # Allow removal of android:summary only for approved keys.
                    if attr.endswith("}summary"):
                        if not cur_attrs.get(attr) and key in P2_REMOVED_SUMMARY_KEYS:
                            continue
                        if cur_attrs.get(attr) == base_val:
                            continue
                        failures.append(
                            f"{path.name}:{key}:{attr}: summary changed from {base_val!r} to {cur_attrs.get(attr)!r}"
                        )
                        continue

                    if cur_attrs.get(attr) != base_val:
                        if (path.name, key, attr, base_val, cur_attrs.get(attr, "")) in POST_P2_ALLOWED_ATTR_CHANGES:
                            continue
                        failures.append(
                            f"{path.name}:{key}:{attr}: {base_val!r} -> {cur_attrs.get(attr)!r}"
                        )

                # Disallow new attributes on keyed preferences.
                for attr in set(cur_attrs) - set(base_attrs):
                    failures.append(
                        f"{path.name}:{key}: new attribute {attr}={cur_attrs[attr]!r}"
                    )

            # --- categories ---
            base_cats = self._collect_categories(base_root, path.name)
            current_cats = self._collect_categories(current_root, path.name)

            base_cat_ids = {c["id"] for c in base_cats}
            current_cat_ids = {c["id"] for c in current_cats}

            for b in base_cats:
                cid = b["id"]
                match = next((c for c in current_cats if c["id"] == cid), None)
                if match is None:
                    if (path.name, b["child_keys"], b["title"]) in P2_ALLOWED_REMOVED_CATEGORIES:
                        continue
                    failures.append(
                        f"{path.name}: base category {b['desc']} removed without approval"
                    )
                    continue

                base_attrs = _attr_map(b["el"])
                cur_attrs = _attr_map(match["el"])

                # Apply approved category title renames.
                allowed_new_title = None
                renames = P2_ALLOWED_CATEGORY_RENAMES.get(path.name, {})
                if b["child_keys"] in renames:
                    old_title, new_title = renames[b["child_keys"]]
                    if b["title"] == old_title:
                        allowed_new_title = new_title

                for attr, base_val in base_attrs.items():
                    if attr == f"{{{ANDROID_NS}}}title" and allowed_new_title:
                        if cur_attrs.get(attr) == allowed_new_title:
                            continue
                    if cur_attrs.get(attr) != base_val:
                        failures.append(
                            f"{path.name}:category {b['desc']} {attr} changed from {base_val!r} to {cur_attrs.get(attr)!r}"
                        )

                for attr in set(cur_attrs) - set(base_attrs):
                    failures.append(
                        f"{path.name}:category {b['desc']} new attribute {attr}={cur_attrs[attr]!r}"
                    )

            for c in current_cats:
                if c["id"] not in base_cat_ids:
                    if (path.name, c["child_keys"], c["title"]) in P2_ALLOWED_NEW_CATEGORIES:
                        continue
                    failures.append(
                        f"{path.name}: new category {c['desc']} not approved"
                    )

        self.assertFalse(failures, "\n".join(failures))

    def _collect_categories(self, root: ET.Element, filename: str) -> list[dict]:
        result = []

        def visit(parent: ET.Element):
            for child in parent:
                if _is_category(child):
                    key = child.get(ATTR_KEY)
                    title = child.get(ATTR_TITLE, "")
                    child_keys = _child_keys(child)

                    if key:
                        cat_id = (filename, f"key={key}")
                    elif child_keys:
                        cat_id = (filename, f"children={sorted(child_keys)}")
                    else:
                        # Empty/header category: identify by title so multiple
                        # section headers in the same file remain distinct.
                        cat_id = (filename, f"title={title}")

                    desc = f"title={title} key={key!r} children={len(child_keys)}"
                    result.append({
                        "el": child,
                        "id": cat_id,
                        "desc": desc,
                        "title": title,
                        "child_keys": child_keys,
                    })
                    visit(child)

        visit(root)
        return result


if __name__ == "__main__":
    unittest.main()
