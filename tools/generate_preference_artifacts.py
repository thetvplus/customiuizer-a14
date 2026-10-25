#!/usr/bin/env python3
"""Generate lazy preference pages and the compact search index from canonical XML."""

from __future__ import annotations

import argparse
import copy
import dataclasses
import re
import xml.etree.ElementTree as ET
from pathlib import Path


ANDROID_NS = "http://schemas.android.com/apk/res/android"
AUTO_NS = "http://schemas.android.com/apk/res-auto"
ANDROID_KEY = f"{{{ANDROID_NS}}}key"
ANDROID_TITLE = f"{{{ANDROID_NS}}}title"
ANDROID_LAYOUT_WIDTH = f"{{{ANDROID_NS}}}layout_width"
ANDROID_LAYOUT_HEIGHT = f"{{{ANDROID_NS}}}layout_height"
AUTO_CHILD = f"{{{AUTO_NS}}}child"
AUTO_VISIBLE = f"{{{AUTO_NS}}}isPreferenceVisible"
PREFERENCE_CATEGORY = "tv.withaibuild.customiuizer.prefs.PreferenceCategoryEx"
PREFERENCE_ITEM = "tv.withaibuild.customiuizer.prefs.PreferenceEx"

ET.register_namespace("android", ANDROID_NS)
ET.register_namespace("app", AUTO_NS)


@dataclasses.dataclass(frozen=True)
class CategorySource:
    key: str
    source_name: str
    title: str

    @property
    def suffix(self) -> str:
        return self.key.removeprefix("pref_key_")


CATEGORY_SOURCES = (
    CategorySource("pref_key_system", "prefs_system.xml", "@string/system_mods"),
    CategorySource("pref_key_launcher", "prefs_launcher.xml", "@string/launcher_title"),
    CategorySource("pref_key_controls", "prefs_controls.xml", "@string/controls_mods"),
    CategorySource("pref_key_various", "prefs_various.xml", "@string/various_mods"),
)

# Standalone pages opened by the category controllers. Search must use the same
# controller as normal navigation so dependent controls keep their behavior.
# (entry key, XML resource, controller, dynamic title marker)
STANDALONE_PAGES = (
    ("pref_key_system_island_feedback_cat", "prefs_system_island_feedback", "System", False),
    ("pref_key_system_island_music_cat", "prefs_system_island_music", "System", False),
    ("pref_key_system_island_actions_cat", "prefs_system_island_actions", "System", False),
    ("pref_key_system_island_layout_cat", "prefs_system_island_layout", "System", False),
    ("pref_key_system_island_notifications_cat", "prefs_system_island_notifications", "System", False),
    ("pref_key_system_autobrightness_cat", "prefs_system_autobrightness", "System_AutoBrightness", False),
    ("pref_key_system_visualizer_cat", "prefs_system_visualizer", "System_Visualizer", False),
    ("pref_key_system_vibration_amp_cat", "prefs_system_vibration_amp", "System_VibrationAmp", False),
    ("pref_key_system_detailednetspeed_cat", "prefs_system_detailednetspeed", "System", False),
    ("pref_key_system_statusbar_batterytempandcurrent_cat", "prefs_system_statusbar_batterytempandcurrent", "System", True),
    ("prefs_system_statusbar_showdevicetemperature_cat", "prefs_system_statusbar_showdevicetemperature", "System", True),
    ("pref_key_system_statusbar_batterystyle_cat", "prefs_system_statusbar_batterystyle", "System", False),
    ("pref_key_system_statusbar_duo_cat", "prefs_system_statusbar_duo", "System", True),
    ("pref_key_system_statusbar_mobile_signal_cat", "prefs_system_statusbar_mobilesignal", "System", True),
    ("pref_key_system_statusbaricons_cat", "prefs_system_hideicons", "System", True),
    ("pref_key_system_statusbaricons_atright_cat", "prefs_system_statusbar_righticons", "System", True),
    ("pref_key_system_statusbar_clocktweak_cat", "prefs_system_statusbar_clock", "System", True),
    ("pref_key_system_batteryindicator_cat", "prefs_system_batteryindicator", "System_BatteryIndicator", False),
    ("pref_key_system_statusbarcontrols_cat", "prefs_system_statusbarcontrols", "System", True),
    ("pref_key_system_cc_clocktweak_cat", "prefs_system_controlcenter_clock", "System", True),
    ("pref_key_system_cc_tile_style_cat", "prefs_system_controlcenter_themestyle", "System", False),
    ("pref_key_system_noscreenlock_cat", "prefs_system_noscreenlock", "System_NoScreenLock", False),
    ("pref_key_system_lockscreenshortcuts_cat", "prefs_system_lockscreenshortcuts", "System", True),
    ("pref_key_system_albumartonlock_cat", "prefs_system_albumartonlock", "SubFragment", False),
    ("pref_key_system_charginginfo_cat", "prefs_system_charginginfo", "SubFragment", False),
    ("pref_key_system_lsalarm_cat", "prefs_system_alarmonlock", "SubFragment", False),
    ("pref_key_system_secureqs_cat", "prefs_system_secureqs", "SubFragment", False),
    ("pref_key_system_screenshot_cat", "prefs_system_screenshot", "System_ScreenshotConfig", False),
    ("pref_key_various_calluibright_cat", "prefs_various_calluibright", "Various_CallUIBright", False),
    ("pref_key_various_hiddenfeatures_cat", "prefs_various_hiddenfeatures", "Various_HiddenFeatures", False),
)

def _new_screen(source_root: ET.Element) -> ET.Element:
    return ET.Element(source_root.tag, dict(source_root.attrib))


def _selector_screen() -> ET.Element:
    return ET.Element(
        "PreferenceScreen",
        {
            ANDROID_LAYOUT_WIDTH: "match_parent",
            ANDROID_LAYOUT_HEIGHT: "match_parent",
            ANDROID_KEY: "pref_key_cat",
        },
    )


def _write_xml(path: Path, root: ET.Element) -> None:
    tree = ET.ElementTree(root)
    ET.indent(tree, space="\t")
    tree.write(path, encoding="utf-8", xml_declaration=True, short_empty_elements=True)


def _split_name(category: CategorySource, key: str) -> str:
    prefix = f"{category.key}_cat_"
    if not key.startswith(prefix):
        raise ValueError(f"Unexpected category key {key!r} in {category.source_name}")
    return f"prefs_{category.suffix}_{key.removeprefix(prefix)}.xml"


def _structured_categories(
    category: CategorySource,
    source_root: ET.Element,
    xml_dir: Path,
) -> None:
    selector = _selector_screen()
    found = 0
    def append_group(source_group: ET.Element, selector_group: ET.Element) -> None:
        nonlocal found
        for source_category in source_group:
            key = source_category.get(ANDROID_KEY, "")
            title = source_category.get(ANDROID_TITLE, "")
            if source_category.tag != PREFERENCE_CATEGORY or not title:
                raise ValueError(f"Invalid directory node in {category.source_name}: {source_category.tag}")
            if not key:
                if not len(source_category):
                    raise ValueError(f"Empty directory section in {category.source_name}: {title}")
                section = ET.SubElement(selector_group, PREFERENCE_CATEGORY, {ANDROID_TITLE: title})
                append_group(source_category, section)
                continue

            split_root = _new_screen(source_root)
            split_root.append(copy.deepcopy(source_category))
            _write_xml(xml_dir / _split_name(category, key), split_root)
            selector_group.append(ET.Element(PREFERENCE_ITEM, {ANDROID_KEY: key, ANDROID_TITLE: title}))
            found += 1

    append_group(source_root, selector)
    if found == 0:
        raise ValueError(f"No categories found in {category.source_name}")
    _write_xml(xml_dir / f"prefs_{category.suffix}_cat.xml", selector)


def _search_entries(
    category: CategorySource,
    source_root: ET.Element,
) -> list[dict[str, str]]:
    entries: list[dict[str, str]] = []
    order = 0

    def visit(group: ET.Element, route: str = "", page_title: str = "", section: str = "") -> None:
        nonlocal order
        for element in group:
            if element.get(AUTO_VISIBLE, "true").lower() == "false":
                continue
            title = element.get(ANDROID_TITLE, "")
            key = element.get(ANDROID_KEY, "")
            if element.tag == PREFERENCE_CATEGORY:
                order += 1
                if key:
                    order = 1
                    visit(element, key, title)
                else:
                    # A hidden dependency container does not add a breadcrumb.
                    heading = section if element.get(f"{{{AUTO_NS}}}state") in ("1", "2") else title
                    visit(element, route, page_title, heading)
                continue
            if title.startswith("@") and key:
                item = {
                    "title": title,
                    "key": key,
                    "category": category.key,
                    "categoryTitle": category.title,
                    "order": str(order),
                }
                if route:
                    item["routeSub"] = route
                if page_title:
                    item["breadcrumbSubTitle"] = page_title
                if section:
                    item["breadcrumbSubSubTitle"] = section
                entries.append(item)
            order += 1

    visit(source_root)
    return entries


def _standalone_search_entries(
    source_dir: Path,
    category: CategorySource,
    parent_entries: list[dict[str, str]],
) -> list[dict[str, str]]:
    parents = {entry["key"]: entry for entry in parent_entries}
    entries: list[dict[str, str]] = []
    for entry_key, resource, controller, dynamic in STANDALONE_PAGES:
        parent = parents.get(entry_key)
        if parent is None:
            continue
        root = ET.parse(source_dir / f"{resource}.xml").getroot()
        for item in _search_entries(category, root):
            section = item.get("breadcrumbSubSubTitle") or item.get("breadcrumbSubTitle", "")
            item.update(
                routeSub=entry_key,
                breadcrumbSubTitle=parent.get("breadcrumbSubTitle", ""),
                breadcrumbSubSubTitle=section,
                page=f"@xml/{resource}",
                pageTitle=parent["title"],
                pageFragment=controller,
                pageDynamic=str(dynamic).lower(),
            )
            entries.append(item)
    return entries


def _append_compact_search_category(
    index_root: ET.Element,
    category: CategorySource,
    entries: list[dict[str, str]],
) -> None:
    category_element = ET.SubElement(
        index_root,
        "category",
        {"key": category.key, "title": category.title},
    )
    group_element: ET.Element | None = None
    current_group: tuple[str, ...] | None = None
    current_section = ""

    for item in entries:
        group = (
            item.get("routeSub", ""),
            item.get("breadcrumbSubTitle", ""),
            item.get("page", ""),
            item.get("pageTitle", ""),
            item.get("pageFragment", ""),
            item.get("pageDynamic", ""),
        )
        if group != current_group:
            group_attributes = {"routeSub": group[0]}
            if group[1]:
                group_attributes["breadcrumbTitle"] = group[1]
            for name, value in zip(("page", "pageTitle", "pageFragment", "pageDynamic"), group[2:], strict=True):
                if value:
                    group_attributes[name] = value
            group_element = ET.SubElement(category_element, "group", group_attributes)
            current_group = group
            current_section = ""

        section = item.get("breadcrumbSubSubTitle", "")
        if section != current_section:
            if section:
                ET.SubElement(group_element, "section", {"title": section})
            else:
                ET.SubElement(group_element, "section")
            current_section = section

        ET.SubElement(
            group_element,
            "mod",
            {
                "title": item["title"],
                "key": item["key"],
                "order": item["order"],
            },
        )


FEATURE_PREFERENCE_KEY = re.compile(r'preferenceKey\s*=\s*"([A-Za-z][A-Za-z0-9_]*)"')

XML_VALUE_TYPES = {
    "androidx.preference.MultiSelectListPreference": "STRING_SET",
    "tv.withaibuild.customiuizer.prefs.CheckBoxPreferenceEx": "BOOLEAN",
    "tv.withaibuild.customiuizer.prefs.ListPreferenceEx": "STRING",
    "tv.withaibuild.customiuizer.prefs.DropDownPreferenceEx": "STRING",
    "tv.withaibuild.customiuizer.prefs.EditTextPreferenceEx": "STRING",
    "tv.withaibuild.customiuizer.prefs.SeekBarPreference": "INT",
    "tv.withaibuild.customiuizer.prefs.ColorPreferenceEx": "INT",
}


def _storage_key(raw: str) -> str:
    if raw.startswith("pref_key_"):
        return raw
    return f"pref_key_{raw}"


def collect_xml_storage_keys(xml_dir: Path) -> set[str]:
    keys: set[str] = set()
    for path in sorted(xml_dir.glob("*.xml")):
        root = ET.parse(path).getroot()
        for element in root.iter():
            key = element.get(ANDROID_KEY)
            if key:
                keys.add(_storage_key(key))
    return keys


def collect_xml_storage_types(xml_dir: Path) -> dict[str, str]:
    types: dict[str, str] = {}
    for path in sorted(xml_dir.glob("*.xml")):
        root = ET.parse(path).getroot()
        for element in root.iter():
            key = element.get(ANDROID_KEY)
            if not key:
                continue
            storage = _storage_key(key)
            tag_type = XML_VALUE_TYPES.get(element.tag)
            if tag_type is None and storage.endswith("_apps"):
                tag_type = "STRING_SET"
            if tag_type:
                types[storage] = tag_type
    return types


def collect_feature_storage_keys(java_dir: Path) -> set[str]:
    keys: set[str] = set()
    if not java_dir.is_dir():
        return keys
    for path in java_dir.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        for match in FEATURE_PREFERENCE_KEY.finditer(text):
            keys.add(_storage_key(match.group(1)))
    return keys


def write_preference_catalog(
    output_dir: Path,
    keys: set[str],
    types: dict[str, str] | None = None,
) -> None:
    package_dir = output_dir / "tv" / "withaibuild" / "customiuizer" / "utils"
    package_dir.mkdir(parents=True, exist_ok=True)
    lines = [
        "package tv.withaibuild.customiuizer.utils",
        "",
        "/** Generated from preference XML and feature preferenceKey declarations. Do not edit. */",
        "internal object CurrentPreferenceCatalog {",
        "    @JvmField",
        "    val STORAGE_KEYS: Set<String> = hashSetOf(",
    ]
    for key in sorted(keys):
        lines.append(f'        "{key}",')
    lines.append("    )")
    lines.append("")
    lines.append("    @JvmField")
    lines.append("    val VALUE_TYPES: Map<String, PreferenceValueType> = hashMapOf(")
    resolved = types or {}
    for key in sorted(resolved):
        lines.append(f'        "{key}" to PreferenceValueType.{resolved[key]},')
    lines.extend(
        [
            "    )",
            "}",
            "",
        ]
    )
    (package_dir / "CurrentPreferenceCatalog.kt").write_text("\n".join(lines), encoding="utf-8")


def generate(
    source_dir: Path,
    output_dir: Path,
    catalog_output: Path | None = None,
    java_dir: Path | None = None,
) -> None:
    xml_dir = output_dir / "xml"
    xml_dir.mkdir(parents=True, exist_ok=True)
    for stale_xml in xml_dir.glob("*.xml"):
        stale_xml.unlink()

    roots: dict[str, ET.Element] = {}
    for category in CATEGORY_SOURCES:
        source_path = source_dir / category.source_name
        if not source_path.is_file():
            raise FileNotFoundError(source_path)
        roots[category.key] = ET.parse(source_path).getroot()

    for category in CATEGORY_SOURCES:
        _structured_categories(category, roots[category.key], xml_dir)

    index_root = ET.Element("mod-search-index")
    all_parent_keys: set[str] = set()
    for category in CATEGORY_SOURCES:
        entries = _search_entries(category, roots[category.key])
        all_parent_keys.update(item["key"] for item in entries)
        entries += _standalone_search_entries(source_dir, category, entries)
        _append_compact_search_category(
            index_root,
            category,
            entries,
        )
    missing_pages = {page[0] for page in STANDALONE_PAGES} - all_parent_keys
    if missing_pages:
        raise ValueError(f"Standalone search pages have no navigation entry: {sorted(missing_pages)}")
    _write_xml(xml_dir / "mod_search_index.xml", index_root)

    if catalog_output is not None:
        keys = collect_xml_storage_keys(source_dir)
        types = collect_xml_storage_types(source_dir)
        if java_dir is not None:
            feature_keys = collect_feature_storage_keys(java_dir)
            keys.update(feature_keys)
            for key in feature_keys:
                types.setdefault(key, "BOOLEAN")
        write_preference_catalog(catalog_output, keys, types)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--catalog-output", type=Path, required=False)
    parser.add_argument("--java-dir", type=Path, required=False)
    args = parser.parse_args()
    generate(
        args.source_dir.resolve(),
        args.output_dir.resolve(),
        None if args.catalog_output is None else args.catalog_output.resolve(),
        None if args.java_dir is None else args.java_dir.resolve(),
    )


if __name__ == "__main__":
    main()
