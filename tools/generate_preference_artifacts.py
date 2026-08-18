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


@dataclasses.dataclass(frozen=True)
class VariousGroup:
    key: str
    title: str
    output_name: str
    source_title: str | None = None


CATEGORY_SOURCES = (
    CategorySource("pref_key_system", "prefs_system.xml", "@string/system_mods"),
    CategorySource("pref_key_launcher", "prefs_launcher.xml", "@string/launcher_title"),
    CategorySource("pref_key_controls", "prefs_controls.xml", "@string/controls_mods"),
    CategorySource("pref_key_various", "prefs_various.xml", "@string/various_mods"),
)

VARIOUS_GROUPS = (
    VariousGroup(
        "pref_key_various_cat_exclusive",
        "@string/various_exclusive_features_cat_title",
        "prefs_various_exclusive.xml",
    ),
    VariousGroup(
        "pref_key_various_cat_general",
        "@string/various_general_cat_title",
        "prefs_various_general.xml",
    ),
    VariousGroup(
        "pref_key_various_cat_package_installer",
        "@string/various_package_installer_cat_title",
        "prefs_various_package_installer.xml",
    ),
    VariousGroup(
        "pref_key_various_cat_security_center",
        "@string/various_securitycenter_unlock_title",
        "prefs_various_security_center.xml",
    ),
    VariousGroup("pref_key_various_cat_calls", "@string/calls", "prefs_various_calls.xml"),
    VariousGroup(
        "pref_key_various_cat_settings",
        "@string/various_app_management_cat_title",
        "prefs_various_settings.xml",
    ),
    VariousGroup("pref_key_various_cat_gboard", "@string/gboard", "prefs_various_gboard.xml"),
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
    for source_category in list(source_root):
        key = source_category.get(ANDROID_KEY, "")
        if not key:
            raise ValueError(
                f"{category.source_name} has an unkeyed top-level preference; "
                "structured sources must contain only keyed category wrappers"
            )
        title = source_category.get(ANDROID_TITLE, "")
        if source_category.tag != PREFERENCE_CATEGORY or not title:
            raise ValueError(f"Unexpected top-level node {source_category.tag!r} in {category.source_name}")

        split_root = _new_screen(source_root)
        split_root.append(copy.deepcopy(source_category))
        _write_xml(xml_dir / _split_name(category, key), split_root)

        selector.append(ET.Element(PREFERENCE_ITEM, {ANDROID_KEY: key, ANDROID_TITLE: title}))
        found += 1

    if found == 0:
        raise ValueError(f"No categories found in {category.source_name}")
    _write_xml(xml_dir / f"prefs_{category.suffix}_cat.xml", selector)


def _partition_various(source_root: ET.Element) -> list[list[ET.Element]]:
    groups: list[list[ET.Element]] = [[] for _ in VARIOUS_GROUPS]
    group_index = -1
    for child in list(source_root):
        if child.tag == PREFERENCE_CATEGORY:
            group_index += 1
            if group_index >= len(VARIOUS_GROUPS):
                raise ValueError("prefs_various.xml contains more category headers than expected")
            spec = VARIOUS_GROUPS[group_index]
            expected_title = spec.source_title or spec.title
            actual_title = child.get(ANDROID_TITLE, "")
            if actual_title != expected_title:
                raise ValueError(
                    f"Unexpected prefs_various.xml header {actual_title!r}; expected {expected_title!r}"
                )
            continue
        if group_index < 0:
            raise ValueError("prefs_various.xml must start with an explicit category header")
        groups[group_index].append(child)

    if group_index != len(VARIOUS_GROUPS) - 1:
        raise ValueError("prefs_various.xml category header count changed; update the explicit grouping contract")
    if any(not group for group in groups):
        raise ValueError("Every generated prefs_various group must contain at least one preference")
    return groups


def _various_categories(source_root: ET.Element, xml_dir: Path) -> dict[int, str]:
    selector = _selector_screen()
    route_by_element: dict[int, str] = {}
    for spec, source_children in zip(VARIOUS_GROUPS, _partition_various(source_root), strict=True):
        split_root = _new_screen(source_root)
        wrapper = ET.Element(PREFERENCE_CATEGORY, {ANDROID_KEY: spec.key, ANDROID_TITLE: spec.title})
        for child in source_children:
            wrapper.append(copy.deepcopy(child))
            for descendant in child.iter():
                route_by_element[id(descendant)] = spec.key
        split_root.append(wrapper)
        _write_xml(xml_dir / spec.output_name, split_root)
        selector.append(ET.Element(PREFERENCE_ITEM, {ANDROID_KEY: spec.key, ANDROID_TITLE: spec.title}))

    _write_xml(xml_dir / "prefs_various_cat.xml", selector)
    return route_by_element


def _search_entries(
    category: CategorySource,
    source_root: ET.Element,
    various_routes: dict[int, str],
) -> list[dict[str, str]]:
    entries: list[dict[str, str]] = []
    last_sub = ""
    last_sub_title = ""
    last_sub_sub_title = ""
    order = 0

    for element in source_root.iter():
        if element is source_root:
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
        if title.startswith("@"):
            route_sub = (
                various_routes.get(id(element), "")
                if category.key == "pref_key_various"
                else last_sub
            )
            item = {
                "title": title,
                "key": element.get(ANDROID_KEY, ""),
                "category": category.key,
                "categoryTitle": category.title,
                "order": str(order),
            }
            if route_sub:
                item["routeSub"] = route_sub
            if last_sub_title:
                item["breadcrumbSubTitle"] = last_sub_title
            if last_sub_sub_title:
                item["breadcrumbSubSubTitle"] = last_sub_sub_title
            entries.append(item)
        order += 1
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
    current_group: tuple[str, str] | None = None
    current_section = ""

    for item in entries:
        group = (
            item.get("routeSub", ""),
            item.get("breadcrumbSubTitle", ""),
        )
        if group != current_group:
            group_attributes = {"routeSub": group[0]}
            if group[1]:
                group_attributes["breadcrumbTitle"] = group[1]
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

    for category in CATEGORY_SOURCES[:3]:
        _structured_categories(category, roots[category.key], xml_dir)
    various_routes = _various_categories(roots["pref_key_various"], xml_dir)

    index_root = ET.Element("mod-search-index")
    for category in CATEGORY_SOURCES:
        _append_compact_search_category(
            index_root,
            category,
            _search_entries(category, roots[category.key], various_routes),
        )
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
