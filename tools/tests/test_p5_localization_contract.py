"""P5-C / P6-B2 localization contract: user-visible resources must be localized,
correctly formatted, and free of hard-coded visible text.

User-visible required set (generic, not hardcoded):
* XML (prefs)       : android:title / android:summary
* Layouts           : android:text / android:hint
* Menus             : android:title
* Kotlin/Java       : R.string.* and R.array.* references in production code
* string-array refs : @string/... references inside <string-array> in arrays.xml

Non-translatable authority: merged base values/*.xml translatable="false".
"""

import re
import json
import sys
import unittest
import tempfile
from unittest.mock import patch
import xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
RES = REPO / "app" / "src" / "main" / "res"
JAVA_DIR = REPO / "app" / "src" / "main" / "java"

ANDROID_NS = "http://schemas.android.com/apk/res/android"
USER_VISIBLE_ATTRS = ("title", "summary", "text", "hint", "entries")

PLACEHOLDER_RE = re.compile(
    r"%"
    r"(?:\d+\$)?"
    r"(?:[-+#0,]+)?"
    r"(?:\d+|\*)?"
    r"(?:\.\d+)?"
    r"([sdxXfFc%])"
)


def _is_language_qualifier(qualifier: str) -> bool:
    # Match a language token, not prefixes: hi/hu/hr are languages, h600dp is not.
    return re.fullmatch(r"[a-z]{2}(?:-r[A-Z]{2})?(?:-[A-Za-z0-9]+)*", qualifier) is not None


def discover_formal_locales() -> list[str]:
    locales = []
    for d in RES.iterdir():
        if not d.is_dir() or not d.name.startswith("values-"):
            continue
        qualifier = d.name[len("values-"):]
        if _is_language_qualifier(qualifier):
            locales.append(d.name)
    return sorted(set(locales))


def _parse(path: Path) -> ET.Element:
    return ET.parse(path).getroot()


def _merged_values(directory: Path) -> ET.Element:
    root = ET.Element("resources")
    names = set()
    for resource in sorted(directory.glob("*.xml")):
        for el in _parse(resource):
            key = (el.tag, el.get("name"))
            if el.tag in ("string", "string-array") and key[1]:
                if key in names:
                    raise AssertionError(f"Duplicate {el.tag} in {directory.name}: {key[1]}")
                names.add(key)
            root.append(el)
    return root


def _load_strings(path: Path) -> tuple[dict[str, str], set[str], set[str]]:
    # Android merges every values XML, including feature-specific files.
    return _strings(_merged_values(path.parent))


def _strings(root: ET.Element) -> tuple[dict[str, str], set[str], set[str]]:
    result = {}
    non_translatable = set()
    non_formatted = set()
    for el in root:
        if el.tag == "string" and "name" in el.attrib:
            if el.attrib["name"] in result:
                raise AssertionError(f"Duplicate string: {el.attrib['name']}")
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


def _visible_definitions():
    for subdir in ("xml", "layout", "menu"):
        for f in (RES / subdir).rglob("*.xml"):
            yield f, _parse(f)
    from tools.settings_schema import read_element
    for source in (REPO / "app/settings").glob("*.json"):
        yield source, read_element(source)


def _find_xml_visible_refs() -> tuple[set[str], set[str]]:
    string_refs = set()
    array_refs = set()
    for _, root in _visible_definitions():
        for node in root.iter():
            for attr in USER_VISIBLE_ATTRS:
                value = node.get(f"{{{ANDROID_NS}}}{attr}", "")
                if value.startswith("@string/"):
                    string_refs.add(value[8:])
                elif value.startswith("@array/"):
                    array_refs.add(value[7:])
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
    root = _merged_values(RES / "values")
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


class ResourceDiscoveryTest(unittest.TestCase):
    def _run_contract(self, repo, method):
        with patch.multiple(sys.modules[__name__], REPO=repo, RES=repo / "app/src/main/res",
                            JAVA_DIR=repo / "app/src/main/java"):
            result = unittest.TestResult()
            P5LocalizationContractTest(method).run(result)
            self.assertEqual(1, result.testsRun)
            self.assertFalse(result.skipped, result.skipped)
            return result

    def test_language_tokens_do_not_confuse_languages_with_screen_qualifiers(self):
        for qualifier in ("hi", "hu", "hr", "zh-rCN", "en-land"):
            self.assertTrue(_is_language_qualifier(qualifier), qualifier)
        for qualifier in ("night", "land", "v31", "h600dp", "w600dp", "sw600dp", "xxxhdpi"):
            self.assertFalse(_is_language_qualifier(qualifier), qualifier)

    def test_array_contract_reads_split_resources_once_and_rejects_real_drift(self):
        with tempfile.TemporaryDirectory() as raw:
            repo = Path(raw)
            res = repo / "app/src/main/res"
            for directory in (res / "values", res / "values-es-rES", repo / "app/settings"):
                directory.mkdir(parents=True)
            base = res / "values/feature.xml"
            locale = res / "values-es-rES/feature.xml"
            base_text = '<resources><string name="label">%1$s</string><string-array name="choices"><item>@string/label</item></string-array></resources>'
            locale_text = '<resources><string name="label">%1$s</string></resources>'
            base.write_text(base_text, encoding="utf-8")
            locale.write_text(locale_text, encoding="utf-8")
            (repo / "app/settings/prefs_main.json").write_text(json.dumps({
                "type": "screen", "attributes": {"android:entries": "@array/choices"}
            }), encoding="utf-8")
            method = "test_user_visible_string_arrays_have_resolved_translations"
            with patch(__name__ + "._merged_values", wraps=_merged_values) as reads:
                result = self._run_contract(repo, method)
            self.assertTrue(result.wasSuccessful(), result.failures + result.errors)
            self.assertEqual(2, reads.call_count, "base and locale must each be merged once")
            for source, text, reason in (
                (base, base_text.replace('name="choices"', 'name="other"'), "Missing arrays"),
                (locale, '<resources/>', "missing translation"),
                (locale, locale_text.replace('%1$s', '%1$d'), "placeholder mismatch"),
                (locale, locale_text.replace('</resources>', '<string-array name="choices"/></resources>'), "item count"),
            ):
                with self.subTest(reason=reason):
                    base.write_text(base_text, encoding="utf-8")
                    locale.write_text(locale_text, encoding="utf-8")
                    source.write_text(text, encoding="utf-8")
                    result = self._run_contract(repo, method)
                    self.assertFalse(result.errors, result.errors)
                    self.assertEqual(1, len(result.failures), result.failures)
                    self.assertIn(reason, result.failures[0][1])

    def test_visible_text_contract_checks_json_and_does_not_ignore_broken_xml(self):
        with tempfile.TemporaryDirectory() as raw:
            repo = Path(raw)
            settings = repo / "app/settings"
            settings.mkdir(parents=True)
            source = settings / "prefs_main.json"
            source.write_text(json.dumps({"type": "screen", "attributes": {"android:title": "Literal title"}}), encoding="utf-8")
            method = "test_no_hardcoded_user_visible_text"
            result = self._run_contract(repo, method)
            self.assertFalse(result.errors, result.errors)
            self.assertEqual(1, len(result.failures))
            self.assertIn("Literal title", result.failures[0][1])
            source.write_text(json.dumps({"type": "screen", "attributes": {"android:title": "@string/title"}}), encoding="utf-8")
            self.assertTrue(self._run_contract(repo, method).wasSuccessful())
            xml = repo / "app/src/main/res/xml"
            xml.mkdir(parents=True)
            (xml / "broken.xml").write_text('<broken', encoding="utf-8")
            result = self._run_contract(repo, method)
            self.assertEqual(1, len(result.errors))
            self.assertIn("ParseError", result.errors[0][1])

    def test_split_resources_preserve_strings_arrays_and_format_flags(self):
        with tempfile.TemporaryDirectory() as raw:
            directory = Path(raw)
            (directory / "strings.xml").write_text('<resources><string name="base">%1$s</string></resources>', encoding="utf-8")
            (directory / "feature.xml").write_text('<resources><string name="feature" translatable="false">%1$d</string><string-array name="choices"><item>@string/base</item></string-array></resources>', encoding="utf-8")
            strings, non_translatable, _ = _load_strings(directory / "strings.xml")
            self.assertEqual({"base": "%1$s", "feature": "%1$d"}, strings)
            self.assertEqual({"feature"}, non_translatable)
            self.assertEqual(["choices"], [el.get("name") for el in _merged_values(directory) if el.tag == "string-array"])
            self.assertEqual({"missing"}, {"base", "feature", "missing"} - strings.keys())

    def test_duplicate_resources_in_separate_files_fail(self):
        with tempfile.TemporaryDirectory() as raw:
            directory = Path(raw)
            for tag in ("string", "string-array"):
                for name in ("strings.xml", "feature.xml"):
                    (directory / name).write_text(f'<resources><{tag} name="duplicate" /></resources>', encoding="utf-8")
                with self.assertRaisesRegex(AssertionError, "Duplicate"):
                    _merged_values(directory)


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
        for f, root in _visible_definitions():
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
        base_root = _merged_values(RES / "values")
        base, base_non_tr, _ = _strings(base_root)
        locales = discover_formal_locales()
        required_arrays = _find_user_visible_array_refs()
        arrays = {el.get("name"): el for el in base_root if el.tag in ("string-array", "integer-array", "array")}
        self.assertFalse(required_arrays - arrays.keys(), f"Missing arrays: {required_arrays - arrays.keys()}")
        # One parse per locale for this invocation; fixtures and later runs remain fresh.
        locale_roots = {loc: _merged_values(RES / loc) for loc in locales}
        locale_strings = {loc: _strings(root)[0] for loc, root in locale_roots.items()}
        locale_arrays = {loc: {el.get("name"): el for el in root if el.tag == "string-array"}
                         for loc, root in locale_roots.items()}

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
                loc_items = None
                loc_arr = locale_arrays[loc].get(name)
                if loc_arr is not None:
                    loc_items = ["".join(item.itertext()).strip() for item in loc_arr if item.tag == "item"]
                    if len(loc_items) != len(base_items):
                        mismatches.append(f"{loc}/{name}: item count {len(loc_items)} != {len(base_items)}")
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
                        loc_strings = locale_strings[loc]
                        if key not in base or key not in loc_strings:
                            mismatches.append(f"{loc}/{name}[{i}]/{key}: missing translation")
                        elif _extract_placeholders(base[key]) != _extract_placeholders(loc_strings[key]):
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
