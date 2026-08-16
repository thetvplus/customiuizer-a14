"""Static contract and mutation tests for A14-UX1 lock screen charging info font size."""
import re
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent.parent
CHARGING_XML = REPO_ROOT / "app" / "src" / "main" / "res" / "xml" / "prefs_system_charginginfo.xml"
LOCKSCREEN_HOOKS = REPO_ROOT / "app" / "src" / "main" / "java" / "tv" / "withaibuild" / "customiuizer" / "mods" / "SystemLockScreenHooks.kt"
SYSTEM_UI_FEATURES = REPO_ROOT / "app" / "src" / "main" / "java" / "tv" / "withaibuild" / "customiuizer" / "mods" / "utils" / "feature" / "SystemUiFeatures.kt"


NS = {
    "android": "http://schemas.android.com/apk/res/android",
    "miuizer": "http://schemas.android.com/apk/res-auto",
}


def _qname(ns: str, name: str) -> str:
    return f"{{{NS[ns]}}}{name}"


class ChargingInfoFontSizeContractTest(unittest.TestCase):
    """Verify the A14-UX1 charging info font size implementation stays within its contract."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.xml_text = CHARGING_XML.read_text(encoding="utf-8")
        cls.kt_text = LOCKSCREEN_HOOKS.read_text(encoding="utf-8")
        cls.features_text = SYSTEM_UI_FEATURES.read_text(encoding="utf-8")

    # ---- shared contract checker ----

    def _assert_contract(self, xml_text: str, kt_text: str, features_text: str) -> None:
        tree = ET.fromstring(xml_text)
        seekbar = None
        for node in tree.iter("tv.withaibuild.customiuizer.prefs.SeekBarPreference"):
            key = node.get(_qname("android", "key"))
            if key == "pref_key_system_charginginfo_fontsize":
                seekbar = node
                break
        self.assertIsNotNone(seekbar, "pref_key_system_charginginfo_fontsize SeekBarPreference not found")

        self.assertEqual("16", seekbar.get(_qname("android", "defaultValue")), "default value must be 16")
        self.assertEqual("16", seekbar.get(_qname("miuizer", "minValue")), "min value must be 16")
        self.assertEqual("40", seekbar.get(_qname("miuizer", "maxValue")), "max value must be 40")
        self.assertEqual("1", seekbar.get(_qname("miuizer", "stepValue")), "step value must be 1")
        self.assertEqual("2", seekbar.get(_qname("miuizer", "displayDividerValue")), "display divider must be 2")
        self.assertEqual("@string/array_default", seekbar.get(_qname("miuizer", "offtext")), "off text must be @string/array_default")
        self.assertEqual("%s sp", seekbar.get(_qname("miuizer", "format")), "format must be %s sp")

        # Runtime key and default sentinel
        self.assertIn('getInt("system_charginginfo_fontsize", 16)', kt_text, "runtime key must be system_charginginfo_fontsize with default 16")

        # Resolve function and numeric contract
        self.assertIn("resolveChargingInfoFontSizeSp", kt_text, "resolve function must exist")
        self.assertRegex(
            kt_text,
            r"resolveChargingInfoFontSizeSp\(raw:\s*Int\):\s*Float\?",
            "resolve function signature mismatch",
        )
        self.assertRegex(
            kt_text,
            r"if\s*\(\s*raw\s+in\s+17\.\.40\s*\)\s+raw\s*/\s*2f",
            "resolve function must accept only 17..40 and divide by 2f",
        )

        # Unit and size API
        self.assertIn("TypedValue.COMPLEX_UNIT_SP", kt_text, "must use COMPLEX_UNIT_SP")
        self.assertIn("setTextSize(TypedValue.COMPLEX_UNIT_SP,", kt_text, "must call setTextSize with COMPLEX_UNIT_SP")

        forbidden = [
            ("COMPLEX_UNIT_DIP", "must not use COMPLEX_UNIT_DIP"),
            ("textScaleX", "must not use textScaleX"),
            (".scaleX", "must not use scaleX"),
            (".scaleY", "must not use scaleY"),
            ("maxLines", "must not set maxLines"),
            ("setLineSpacing", "must not set line spacing"),
        ]
        for token, msg in forbidden:
            self.assertNotIn(token, kt_text, msg)

        # COMPLEX_UNIT_PX is allowed only inside restoreChargingInfoTextSize,
        # where it restores the exact original pixel size. Custom sizing must
        # use COMPLEX_UNIT_SP.
        self.assertIn("COMPLEX_UNIT_PX", kt_text, "restoreChargingInfoTextSize must use COMPLEX_UNIT_PX")

        # Only one production install route (SystemUiFeatures is the allowed caller).
        install_calls = re.findall(r"SystemLockScreenHooks\.ChargingInfoHook\s*\(", features_text)
        self.assertIn("SystemLockScreenHooks.ChargingInfoHook(lpparam)", features_text, "SystemUiFeatures must call ChargingInfoHook")
        self.assertEqual(1, len(install_calls), f"expected one install call, found {install_calls}")

        # Default sentinel: the parser must turn 16 into null so setTextSize is not called.
        # Accept either ?.let or an explicit if-null guard on the resolved variable.
        has_guard = (
            re.search(r"resolveChargingInfoFontSizeSp\s*\(\s*fontSizeRaw\s*\)\s*\?\.let", kt_text) is not None
            or re.search(r"resolveChargingInfoFontSizeSp\s*\(\s*fontSizeRaw\s*\).*?resolvedSizeSp\s*!=\s*null", kt_text, re.S) is not None
        )
        self.assertTrue(
            has_guard,
            "setTextSize call must be guarded by resolve result (null = default, no call)",
        )

    # ---- positive tests ----

    def test_01_real_files_obey_contract(self) -> None:
        self._assert_contract(self.xml_text, self.kt_text, self.features_text)

    def test_02_font_size_resolver_matches_expected_table(self) -> None:
        """Cross-check the resolver table from the task spec against the source text."""
        match = re.search(
            r"internal fun resolveChargingInfoFontSizeSp\(.*?\}\s*",
            self.kt_text,
            re.S,
        )
        self.assertIsNotNone(match)
        body = match.group(0)
        self.assertIn("17..40", body)
        self.assertIn("raw / 2f", body)

    # ---- mutation tests (in-memory / temporary text) ----

    def _mutated(self, xml_text: str, kt_text: str, features_text: str) -> None:
        with self.assertRaises(AssertionError):
            self._assert_contract(xml_text, kt_text, features_text)

    def test_03_mutation_xml_key_wrong(self) -> None:
        xml = self.xml_text.replace(
            'android:key="pref_key_system_charginginfo_fontsize"',
            'android:key="pref_key_system_charginginfo_fontsz"',
        )
        self._mutated(xml, self.kt_text, self.features_text)

    def test_04_mutation_runtime_key_wrong(self) -> None:
        kt = self.kt_text.replace(
            'getInt("system_charginginfo_fontsize", 16)',
            'getInt("system_charginginfo_fontsz", 16)',
        )
        self._mutated(self.xml_text, kt, self.features_text)

    def test_05_mutation_default_24(self) -> None:
        xml = self.xml_text.replace(
            'android:defaultValue="16"',
            'android:defaultValue="24"',
        )
        self._mutated(xml, self.kt_text, self.features_text)

    def test_06_mutation_max_80(self) -> None:
        xml = self.xml_text.replace(
            'miuizer:maxValue="40"',
            'miuizer:maxValue="80"',
        )
        self._mutated(xml, self.kt_text, self.features_text)

    def test_07_mutation_sp_to_dip(self) -> None:
        kt = self.kt_text.replace(
            "TypedValue.COMPLEX_UNIT_SP",
            "TypedValue.COMPLEX_UNIT_DIP",
        )
        self._mutated(self.xml_text, kt, self.features_text)

    def test_08_mutation_raw_16_parsed_as_8sp(self) -> None:
        """The resolver must not accept 16 as a custom size."""
        kt = re.sub(
            r"if\s*\(\s*raw\s+in\s+17\.\.40\s*\)\s+raw\s*/\s*2f",
            "if (raw in 16..40) raw / 2f",
            self.kt_text,
        )
        self._mutated(self.xml_text, kt, self.features_text)

    def test_09_mutation_raw_over_40_accepted(self) -> None:
        """Values above 40 must be rejected."""
        kt = re.sub(
            r"if\s*\(\s*raw\s+in\s+17\.\.40\s*\)\s+raw\s*/\s*2f",
            "if (raw in 17..80) raw / 2f",
            self.kt_text,
        )
        self._mutated(self.xml_text, kt, self.features_text)

    def test_10_mutation_duplicate_charginginfo_install_route(self) -> None:
        """Only one install route may exist."""
        features = self.features_text + "\n" + "        SystemLockScreenHooks.ChargingInfoHook(lpparam)"
        self._mutated(self.xml_text, self.kt_text, features)

    def test_11_setnextindication_reapply_hook_exists(self) -> None:
        """A secondary hook on setNextIndication must re-apply the style after the ROM resets it.

        This is a static source contract check only; it does not prove HyperOS runtime
        behavior. It protects the corrective route from accidental removal or regression
        to a custom intercept state machine.
        """
        # The hook must target setNextIndication on KeyguardIndicationTextView.
        self.assertIn('"setNextIndication"', self.kt_text,
                      "ChargingInfoHook must hook setNextIndication")
        idx = self.kt_text.index('"setNextIndication"')
        block = self.kt_text[idx:idx + 400]
        # Must use the native after callback, not a custom intercept state machine.
        self.assertIn("override fun after", block,
                      "setNextIndication hook must use override fun after")
        self.assertIn("AfterHookCallback", block,
                      "setNextIndication hook must use AfterHookCallback")
        self.assertIn("getThisObject", block,
                      "setNextIndication hook must read thisObject via getThisObject")
        self.assertIn("applyChargingInfoStyle", block,
                      "setNextIndication hook must call applyChargingInfoStyle")
        # Must not regress to a custom intercept/chain.proceed state machine.
        self.assertNotIn("override fun intercept", block,
                         "setNextIndication hook must not use custom intercept")
        self.assertNotIn("chain.proceed", block,
                         "setNextIndication hook must not call chain.proceed directly")
        # The hook must be inside ChargingInfoHook (before the return INSTALLED).
        hook_idx = self.kt_text.index("fun ChargingInfoHook(")
        self.assertGreater(idx, hook_idx,
                           "setNextIndication hook must be inside ChargingInfoHook")
        return_idx = self.kt_text.index("return FeatureInstallResult.INSTALLED", idx)
        self.assertLess(idx, return_idx,
                        "setNextIndication hook must precede the INSTALLED return")


if __name__ == "__main__":
    unittest.main()
