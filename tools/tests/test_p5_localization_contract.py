"""P5-C / P6-B2 localization contract: user-visible resources must be localized,
correctly formatted, and free of hard-coded visible text.

User-visible required set (generic, not hardcoded):
* XML (prefs)       : android:title / android:summary
* Layouts           : android:text / android:hint
* Menus             : android:title
* Kotlin/Java       : R.string.* and R.array.* references in production code
* string-array refs : @string/... references inside <string-array> in arrays.xml

Non-translatable authority: base values/strings.xml android:translatable="false".
"""

import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
RES = REPO / "app" / "src" / "main" / "res"
JAVA_DIR = REPO / "app" / "src" / "main" / "java"

ANDROID_NS = "http://schemas.android.com/apk/res/android"
USER_VISIBLE_ATTRS = ("title", "summary", "text", "hint", "entries")

NON_LANG_PREFIXES = (
    "night", "land", "port", "sw", "w", "h",
)

PLACEHOLDER_RE = re.compile(
    r"%"
    r"(?:\d+\$)?"
    r"(?:[-+#0,]+)?"
    r"(?:\d+|\*)?"
    r"(?:\.\d+)?"
    r"([sdxXfFc%])"
)


def _is_language_qualifier(qualifier: str) -> bool:
    lower = qualifier.lower()
    if lower.startswith("v") and re.match(r"^v\d+", lower):
        return False
    for non in NON_LANG_PREFIXES:
        if lower.startswith(non):
            return False
    if re.match(r"^[a-zA-Z]{2}(-r[A-Z]{2})?(-.*)?$", qualifier):
        return True
    return False


def discover_formal_locales() -> list[str]:
    locales = []
    for d in RES.iterdir():
        if not d.is_dir() or d.name == "values":
            continue
        qualifier = d.name[len("values-"):]
        if _is_language_qualifier(qualifier):
            locales.append(d.name)
    return sorted(set(locales))


def _parse(path: Path) -> ET.Element:
    return ET.parse(path).getroot()


def _load_strings(path: Path) -> tuple[dict[str, str], set[str], set[str]]:
    root = _parse(path)
    result = {}
    non_translatable = set()
    non_formatted = set()
    for el in root:
        if el.tag == "string" and "name" in el.attrib:
            result[el.attrib["name"]] = "".join(el.itertext())
            if (el.get("translatable", "").lower() == "false" or
                el.get(f"{{{ANDROID_NS}}}translatable", "").lower() == "false"):
                non_translatable.add(el.attrib["name"])
            if (el.get("formatted", "").lower() == "false" or
                el.get(f"{{{ANDROID_NS}}}formatted", "").lower() == "false"):
                non_formatted.add(el.attrib["name"])
    return result, non_translatable, non_formatted


def _extract_placeholders(text: str) -> list:
    placeholders = []
    literals = []
    seq = 1
    for m in PLACEHOLDER_RE.finditer(text):
        conv = m.group(1)
        raw = m.group(0)
        if conv == "%":
            literals.append(("literal", "%"))
            continue
        idx_match = re.match(r"%(\d+)\$", raw)
        if idx_match:
            idx = int(idx_match.group(1))
        else:
            idx = seq
            seq += 1
        placeholders.append(("placeholder", idx, conv.lower()))
    placeholders.sort(key=lambda t: t[1])
    return placeholders + literals


def _find_xml_visible_refs() -> tuple[set[str], set[str]]:
    string_refs = set()
    array_refs = set()
    for subdir in ("xml", "layout", "menu"):
        for f in (RES / subdir).rglob("*.xml"):
            try:
                root = _parse(f)
            except ET.ParseError:
                continue
            for el in root.iter():
                for attr in USER_VISIBLE_ATTRS:
                    val = el.get(f"{{{ANDROID_NS}}}{attr}", "")
                    if val.startswith("@string/"):
                        string_refs.add(val[8:])
                    elif val.startswith("@array/"):
                        array_refs.add(val[7:])
    return string_refs, array_refs


def _find_code_string_refs() -> set[str]:
    refs = set()
    pattern = re.compile(r"(?<![\w.])R\.string\.([A-Za-z_][A-Za-z0-9_]*)\b")
    for f in JAVA_DIR.rglob("*"):
        if f.suffix not in (".kt", ".java"):
            continue
        if "/test/" in str(f) or "\\test\\" in str(f):
            continue
        text = f.read_text(encoding="utf-8", errors="ignore")
        for m in pattern.finditer(text):
            refs.add(m.group(1))
    return refs


def _find_code_array_refs() -> set[str]:
    refs = set()
    pattern = re.compile(r"(?<![\w.])R\.array\.([A-Za-z_][A-Za-z0-9_]*)\b")
    for f in JAVA_DIR.rglob("*"):
        if f.suffix not in (".kt", ".java"):
            continue
        if "/test/" in str(f) or "\\test\\" in str(f):
            continue
        text = f.read_text(encoding="utf-8", errors="ignore")
        for m in pattern.finditer(text):
            refs.add(m.group(1))
    return refs


def _find_array_string_refs() -> set[str]:
    refs = set()
    arrays_xml = RES / "values" / "arrays.xml"
    if not arrays_xml.exists():
        return refs
    root = _parse(arrays_xml)
    for arr in root:
        if arr.tag != "string-array":
            continue
        for item in arr:
            if item.tag != "item":
                continue
            text = "".join(item.itertext()).strip()
            if text.startswith("@string/"):
                refs.add(text[8:])
    return refs


def _find_user_visible_string_refs() -> set[str]:
    xml_strings, _ = _find_xml_visible_refs()
    return xml_strings | _find_code_string_refs() | _find_array_string_refs()


def _find_user_visible_array_refs() -> set[str]:
    _, xml_arrays = _find_xml_visible_refs()
    return xml_arrays | _find_code_array_refs()


class PlaceholderContractTest(unittest.TestCase):

    def _contract(self, text: str) -> list:
        return _extract_placeholders(text)

    def test_simple_placeholders(self):
        self.assertEqual(self._contract("Hello %s"), [("placeholder", 1, "s")])
        self.assertEqual(self._contract("Count: %d"), [("placeholder", 1, "d")])

    def test_positional_placeholders(self):
        self.assertEqual(
            self._contract("%1$s %2$d"),
            [("placeholder", 1, "s"), ("placeholder", 2, "d")],
        )

    def test_percent_literal(self):
        self.assertEqual(self._contract("Battery %% charged"), [("literal", "%")])

    def test_lone_percent_followed_by_word_is_not_placeholder(self):
        self.assertEqual(self._contract("100% charged"), [])
        self.assertEqual(self._contract("0% durante"), [])

    def test_mixed_positional_and_literal(self):
        self.assertEqual(
            self._contract("Hello %1$s: %2$d%%"),
            [("placeholder", 1, "s"), ("placeholder", 2, "d"), ("literal", "%")],
        )

    def test_width_and_precision(self):
        contract = self._contract("Value %05d or %.2f")
        self.assertEqual(
            contract,
            [("placeholder", 1, "d"), ("placeholder", 2, "f")],
        )

    def test_reordering_keeps_contract(self):
        base = self._contract("%1$s: %2$d")
        tr = self._contract("%2$d %1$s")
        self.assertEqual(base, tr)

    def test_missing_placeholder_fails_contract(self):
        base = self._contract("%1$s: %2$d")
        tr = self._contract("%1$s")
        self.assertNotEqual(base, tr)

    def test_type_mismatch_fails_contract(self):
        base = self._contract("%1$s")
        tr = self._contract("%1$d")
        self.assertNotEqual(base, tr)

    def test_index_swap_mismatch_fails_contract(self):
        base = self._contract("%1$s %2$d")
        tr = self._contract("%2$s %1$d")
        self.assertNotEqual(base, tr)


class P5LocalizationContractTest(unittest.TestCase):

    def test_formal_locales_are_discovered(self):
        locales = discover_formal_locales()
        self.assertIn("values-zh-rCN", locales)
        self.assertIn("values-zh-rTW", locales)
        self.assertIn("values-cs-rCZ", locales)
        self.assertIn("values-es-rES", locales)
        self.assertIn("values-ja-rJP", locales)
        self.assertIn("values-pt-rBR", locales)
        self.assertIn("values-ru-rRU", locales)
        self.assertIn("values-tr-rTR", locales)
        self.assertIn("values-vi-rVN", locales)
        self.assertNotIn("values-night", locales)
        self.assertNotIn("values-v31", locales)
        self.assertNotIn("values-land", locales)

    def test_no_hardcoded_user_visible_text(self):
        violations = []
        for subdir in ("xml", "layout", "menu"):
            for f in (RES / subdir).rglob("*.xml"):
                try:
                    root = _parse(f)
                except ET.ParseError:
                    continue
                for el in root.iter():
                    for attr in USER_VISIBLE_ATTRS:
                        val = el.get(f"{{{ANDROID_NS}}}{attr}", "")
                        if val and not val.startswith("@") and not val.startswith("?"):
                            violations.append(f"{f.relative_to(REPO)} android:{attr}={val!r}")
        self.assertFalse(violations, "\n".join(violations))

    def test_user_visible_strings_exist_in_all_formal_locales(self):
        base, base_non_tr, _ = _load_strings(RES / "values" / "strings.xml")
        locales = discover_formal_locales()
        locale_data = {loc: _load_strings(RES / loc / "strings.xml") for loc in locales}
        locale_strings = {loc: d[0] for loc, d in locale_data.items()}

        required = _find_user_visible_string_refs() - base_non_tr

        missing_base = [k for k in required if k not in base]
        self.assertFalse(missing_base, f"Missing in values: {missing_base}")

        missing_by_locale = {}
        for loc in locales:
            missing = [k for k in required if k not in locale_strings[loc]]
            if missing:
                missing_by_locale[loc] = missing
        self.assertFalse(missing_by_locale, f"Missing translations: {missing_by_locale}")

    def test_placeholder_parity(self):
        base, base_non_tr, base_non_fmt = _load_strings(RES / "values" / "strings.xml")
        locales = discover_formal_locales()
        locale_data = {loc: _load_strings(RES / loc / "strings.xml") for loc in locales}
        locale_strings = {loc: d[0] for loc, d in locale_data.items()}
        locale_non_fmt = {loc: d[2] for loc, d in locale_data.items()}

        required = _find_user_visible_string_refs() - base_non_tr

        mismatches = []
        for loc in locales:
            loc_map = locale_strings[loc]
            for key in required:
                if key not in base or key not in loc_map:
                    continue
                if key in base_non_fmt or key in locale_non_fmt[loc]:
                    continue
                base_contract = _extract_placeholders(base[key])
                loc_contract = _extract_placeholders(loc_map[key])
                if base_contract != loc_contract:
                    mismatches.append(f"{loc}/{key}: {base_contract} != {loc_contract}")
        self.assertFalse(mismatches, "\n".join(mismatches[:50]))

    def test_user_visible_string_arrays_have_resolved_translations(self):
        """Base string-arrays that reference @string/... must resolve to
        translated strings in every formal locale. If a locale overrides the
        array, it must preserve item count and item placeholder contracts."""
        base, base_non_tr, _ = _load_strings(RES / "values" / "strings.xml")
        locales = discover_formal_locales()

        base_arrays = RES / "values" / "arrays.xml"
        if not base_arrays.exists():
            self.skipTest("No base arrays.xml")
        base_root = _parse(base_arrays)

        required_arrays = _find_user_visible_array_refs()

        mismatches = []
        inline_missing = []
        for arr in base_root:
            if arr.tag != "string-array":
                continue
            name = arr.attrib.get("name")
            if not name or name not in required_arrays:
                continue
            base_items = ["".join(item.itertext()).strip() for item in arr if item.tag == "item"]
            for loc in locales:
                loc_path = RES / loc / "arrays.xml"
                loc_items = None
                if loc_path.exists():
                    loc_root = _parse(loc_path)
                    loc_arr = None
                    for el in loc_root:
                        if el.tag == "string-array" and el.attrib.get("name") == name:
                            loc_arr = el
                            break
                    if loc_arr is not None:
                        loc_items = ["".join(item.itertext()).strip() for item in loc_arr if item.tag == "item"]
                        if len(loc_items) != len(base_items):
                            mismatches.append(
                                f"{loc}/{name}: item count {len(loc_items)} != {len(base_items)}"
                            )
                            continue

                for i, b in enumerate(base_items):
                    if b.startswith("@string/"):
                        key = b[8:]
                        if key in base_non_tr:
                            continue
                        if loc_items is not None:
                            l = loc_items[i]
                            if l != f"@string/{key}":
                                mismatches.append(f"{loc}/{name}[{i}]: base refs @{key}, locale has {l!r}")
                        loc_strings = _load_strings(RES / loc / "strings.xml")[0]
                        if key in base and key in loc_strings:
                            base_contract = _extract_placeholders(base[key])
                            loc_contract = _extract_placeholders(loc_strings[key])
                            if base_contract != loc_contract:
                                mismatches.append(f"{loc}/{name}[{i}]/{key}: placeholder mismatch")
                    elif loc_items is not None:
                        # If the locale overrides the array, inline user-visible
                        # text must be provided and the placeholder contract must
                        # match the base.
                        if not loc_items[i]:
                            inline_missing.append(f"{loc}/{name}[{i}]")
                        else:
                            base_contract = _extract_placeholders(b)
                            loc_contract = _extract_placeholders(loc_items[i])
                            if base_contract != loc_contract:
                                mismatches.append(f"{loc}/{name}[{i}]: {base_contract} != {loc_contract}")
        if inline_missing:
            self.fail(f"Missing inline array items: {inline_missing[:50]}")
        if mismatches:
            self.fail(f"Array mismatches: {mismatches[:50]}")
