"""Static resource checks for network speed row spacing and localization."""
import pathlib
import re
import unittest
import xml.etree.ElementTree as ET


REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent.parent
RES_ROOT = REPO_ROOT / "app" / "src" / "main" / "res"
LOCALES = [
    "values",
    "values-ru-rRU",
    "values-zh-rCN",
    "values-zh-rTW",
    "values-ja-rJP",
    "values-vi-rVN",
    "values-cs-rCZ",
    "values-pt-rBR",
    "values-tr-rTR",
    "values-es-rES",
]
STRING_KEYS = [
    "system_netspeed_use_clock_style_title",
    "system_netspeed_rowspacing_title",
    "system_netspeed_rowspacing_summ",
    "system_netspeed_prerequisite_note",
]


class NetSpeedResourceTest(unittest.TestCase):

    def test_rowspacing_preference_attributes(self):
        xml_path = RES_ROOT / "xml" / "prefs_system_detailednetspeed.xml"
        tree = ET.parse(xml_path)
        ns = {
            "android": "http://schemas.android.com/apk/res/android",
            "miuizer": "http://schemas.android.com/apk/res-auto",
        }
        row = None
        for seekbar in tree.iter("tv.withaibuild.customiuizer.prefs.SeekBarPreference"):
            key = seekbar.get(f"{{{ns['android']}}}key")
            if key == "pref_key_system_netspeed_rowspacing":
                row = seekbar
                break
        self.assertIsNotNone(row, "rowspacing SeekBarPreference not found")

        # Format must be %d%%
        fmt = row.get(f"{{{ns['miuizer']}}}format")
        self.assertEqual("%d%%", fmt, f"unexpected format: {fmt!r}")

        # No displayDividerValue
        self.assertIsNone(row.get(f"{{{ns['miuizer']}}}displayDividerValue"), "rowspacing must not set displayDividerValue")

        self.assertEqual("70", row.get(f"{{{ns['miuizer']}}}minValue"))
        self.assertEqual("130", row.get(f"{{{ns['miuizer']}}}maxValue"))
        self.assertEqual("100", row.get(f"{{{ns['android']}}}defaultValue"))
        self.assertEqual("5", row.get(f"{{{ns['miuizer']}}}stepValue"))

    def test_prerequisite_preference_non_interactive(self):
        xml_path = RES_ROOT / "xml" / "prefs_system_detailednetspeed.xml"
        text = xml_path.read_text(encoding="utf-8")
        self.assertIn('android:selectable="false"', text, "prerequisite note must not be clickable")
        self.assertIn('android:persistent="false"', text, "prerequisite note must not save state")

    def test_all_locale_strings_present_and_unique(self):
        for locale in LOCALES:
            strings_path = RES_ROOT / locale / "strings.xml"
            text = strings_path.read_text(encoding="utf-8")
            for key in STRING_KEYS:
                matches = re.findall(rf'name="{re.escape(key)}"', text)
                self.assertEqual(
                    1, len(matches),
                    f"{locale}/strings.xml: {key} appears {len(matches)} times",
                )

    def test_clock_title_no_trailing_space(self):
        """The previous base string had a trailing space; ensure it is gone."""
        text = (RES_ROOT / "values" / "strings.xml").read_text(encoding="utf-8")
        match = re.search(r'<string name="system_netspeed_use_clock_style_title">(.*?)</string>', text, re.S)
        self.assertIsNotNone(match)
        value = match.group(1).strip()
        self.assertFalse(value.endswith(" "), f"clock title still has trailing space: {value!r}")


if __name__ == "__main__":
    unittest.main()
