#!/usr/bin/env python3
"""Invariants for module App UI text styling and attribution display.

This test guards against two recurring regressions:

1. Custom Preference subclasses losing AndroidX default style by passing
   defStyleAttr = 0 to the parent constructor (A13 regression).
2. The module App UI hard-coding a font family or typeface instead of
   inheriting the system/Theme font.

SystemUI/status bar/lock screen hooks are allowed to manipulate TextView
fonts because they replace ROM UI, not the module's own UI.
"""

import re
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
APP_ROOT = REPO_ROOT / "app" / "src" / "main"
PREFS_DIR = APP_ROOT / "java" / "tv" / "withaibuild" / "customiuizer" / "prefs"
DUO_DIR = APP_ROOT / "java" / "tv" / "withaibuild" / "customiuizer" / "mods" / "duostatusbar"

# File globs that are part of the module's own App UI and must not
# hard-code font families.
APP_UI_DIRS = (
    APP_ROOT / "java" / "tv" / "withaibuild" / "customiuizer",
    APP_ROOT / "res" / "layout",
    APP_ROOT / "res" / "values",
    APP_ROOT / "res" / "xml",
)

# Only these specific mod files are allowed to manipulate Typeface because they
# target ROM/SystemUI surfaces. The rest of the App UI must not use Typeface
# outside of StyleSpan(Typeface.ITALIC/BOLD).
TYPEFACE_ALLOWLIST = {
    APP_ROOT / "java" / "tv" / "withaibuild" / "customiuizer" / "mods" / "SystemUIStatusBarHooks.kt",
    APP_ROOT / "java" / "tv" / "withaibuild" / "customiuizer" / "mods" / "SystemClockHooks.kt",
    APP_ROOT / "java" / "tv" / "withaibuild" / "customiuizer" / "mods" / "SystemUIBatteryHooks.kt",
    APP_ROOT / "java" / "tv" / "withaibuild" / "customiuizer" / "mods" / "utils" / "StatusBarTextFit.kt",
    # The status-bar renderer keeps its nullable local font; the single cold
    # resource owner below is separate from the module settings typography.
    DUO_DIR / "DuoStatusBarView.kt",
    DUO_DIR / "DuoIosFont.kt",
    # Helpers.applyNewMod() uses StyleSpan(Typeface.ITALIC); explicit files
    # will be checked for StyleSpan separately below.
    APP_ROOT / "java" / "tv" / "withaibuild" / "customiuizer" / "utils" / "Helpers.kt",
}

# Classes that must use the AndroidX two-parameter parent constructor.
TWO_ARG_PREFERENCE_CLASSES = {
    "CheckBoxPreferenceEx": ("SwitchPreference", 2),
    "EditTextPreferenceEx": ("EditTextPreference", 2),
    "ListPreferenceEx": ("ListPreference", 2),
    "PreferenceCategoryEx": ("PreferenceCategory", 2),
    "PreferenceEx": ("Preference", 2),
}


def _all_kotlin_and_xml(paths):
    for base in paths:
        if not base.exists():
            continue
        for p in base.rglob("*"):
            if p.is_file() and (p.suffix in (".kt", ".java") or p.name.endswith(".xml")):
                yield p


class AppTextInvariantsTest(unittest.TestCase):

    def test_seekbar_preference_uses_correct_three_arg_constructor(self):
        """SeekBarPreference must keep the explicit defStyleAttr pattern."""
        path = PREFS_DIR / "SeekBarPreference.kt"
        self.assertTrue(path.is_file(), "SeekBarPreference.kt must exist")
        text = path.read_text(encoding="utf-8")

        class_match = re.search(
            r"class\s+SeekBarPreference\s+(@JvmOverloads\s+)?constructor\s*\(\s*"
            r"context:\s*Context\s*,\s*"
            r"attrs:\s*AttributeSet\?\s*,\s*"
            r"defStyleAttr:\s*Int\s*=\s*([^)]+)\s*\)\s*:\s*"
            r"(\w+)\s*\(\s*context\s*,\s*attrs\s*,\s*([^)]+)\s*\)",
            text,
            re.DOTALL,
        )
        self.assertIsNotNone(class_match, "SeekBarPreference must use @JvmOverloads three-arg constructor")

        def_style_default = class_match.group(2).strip()
        parent_class = class_match.group(3)
        parent_third_arg = class_match.group(4).strip()

        self.assertEqual(
            "androidx.preference.R.attr.preferenceStyle",
            def_style_default,
            "SeekBarPreference defStyleAttr default must be androidx.preference.R.attr.preferenceStyle",
        )
        self.assertEqual(
            "Preference",
            parent_class,
            "SeekBarPreference must extend androidx.preference.Preference",
        )
        self.assertEqual(
            "defStyleAttr",
            parent_third_arg,
            "SeekBarPreference parent call must pass defStyleAttr",
        )

        # Reject common broken patterns.
        self.assertNotIn(
            "defStyleAttr: Int = 0",
            text,
            "SeekBarPreference must not default defStyleAttr to 0",
        )
        self.assertNotIn(
            "Preference(context, attrs, 0)",
            text,
            "SeekBarPreference parent call must not pass literal 0",
        )
        self.assertNotIn(
            "Preference(context, attrs)",
            text,
            "SeekBarPreference parent call must pass defStyleAttr",
        )

    def test_two_arg_preferences_use_correct_parent_constructor(self):
        """AndroidX two-arg preference subclasses must not lose default style."""
        bad = []
        for name, (expected_parent, expected_arg_count) in TWO_ARG_PREFERENCE_CLASSES.items():
            path = PREFS_DIR / f"{name}.kt"
            if not path.is_file():
                bad.append(f"{name}.kt must exist")
                continue
            text = path.read_text(encoding="utf-8")

            # Class declaration: class Foo(context: Context, attrs: AttributeSet?) : Parent(context, attrs)
            pattern = re.compile(
                rf"class\s+{re.escape(name)}\s*\(\s*"
                rf"context:\s*Context\s*,\s*"
                rf"attrs:\s*AttributeSet\?\s*\)\s*:\s*"
                rf"(\w+)\s*\(\s*context\s*,\s*attrs\s*([^)]*)\)",
                re.DOTALL,
            )
            m = pattern.search(text)
            if not m:
                bad.append(f"{name}: constructor signature does not match expected two-arg AndroidX pattern")
                continue

            parent = m.group(1)
            extra_args = m.group(2).strip()
            if parent != expected_parent:
                bad.append(f"{name}: parent is {parent}, expected {expected_parent}")
            # Two-arg call must have no extra positional arguments. An optional
            # trailing comma after the second argument is accepted, anything
            # beyond that (e.g. a third arg or a comma plus another value) is not.
            if extra_args and extra_args.strip().rstrip(","):
                bad.append(f"{name}: parent call has extra arguments: {extra_args}")

            # Reject explicit three-arg parent calls in these files.
            if re.search(rf"{re.escape(expected_parent)}\s*\(\s*context\s*,\s*attrs\s*,\s*\d+\s*\)", text):
                bad.append(f"{name}: must not use three-arg parent constructor with literal defStyleAttr")

        self.assertFalse(bad, "\n".join(bad))

    def test_app_ui_layouts_do_not_hardcode_font_family(self):
        """App UI XML must not set android:fontFamily or android:typeface."""
        bad = []
        for path in sorted((APP_ROOT / "res" / "layout").rglob("*.xml")):
            text = path.read_text(encoding="utf-8")
            for m in re.finditer(r"android:(?:fontFamily|typeface)\s*=", text):
                bad.append(f"{path.relative_to(REPO_ROOT)}:{m.start()}")
        self.assertFalse(
            bad,
            "App UI layouts must not hardcode fontFamily/typeface: "
            + ", ".join(bad),
        )

    def test_app_ui_values_do_not_hardcode_font_family(self):
        """App theme/values must not set fontFamily or typeface attributes."""
        bad = []
        for path in sorted((APP_ROOT / "res" / "values").rglob("*.xml")):
            text = path.read_text(encoding="utf-8")
            for m in re.finditer(r"(fontFamily|typeface)\s*[=:]", text, re.IGNORECASE):
                bad.append(f"{path.relative_to(REPO_ROOT)}:{m.start()}")
        self.assertFalse(
            bad,
            "App theme/values must not hardcode fontFamily/typeface: "
            + ", ".join(bad),
        )

    def test_app_ui_code_does_not_use_typeface_for_font_family(self):
        """App UI code must not create or apply a Typeface to force a font.

        StyleSpan(Typeface.ITALIC/BOLD) is allowed as a style-only change.
        Only the explicit allowlist may touch Typeface for ROM surfaces.
        """
        bad = []
        style_span_pattern = re.compile(r"StyleSpan\s*\(\s*Typeface\.(?:ITALIC|BOLD)\s*\)")

        for path in sorted(_all_kotlin_and_xml((APP_ROOT / "java" / "tv" / "withaibuild" / "customiuizer",))):
            if path in TYPEFACE_ALLOWLIST:
                # Even allowlisted Helpers.kt is only allowed StyleSpan usage.
                if path.name == "Helpers.kt":
                    text = path.read_text(encoding="utf-8")
                    text_without_style_spans = style_span_pattern.sub("", text)
                    text_without_imports = re.sub(r"^\s*import\s+.*Typeface.*$", "", text_without_style_spans, flags=re.MULTILINE)
                    for m in re.finditer(r"\bTypeface\b", text_without_imports):
                        bad.append(f"{path.relative_to(REPO_ROOT)}:{m.start()}")
                elif path == DUO_DIR / "DuoStatusBarView.kt":
                    text = path.read_text(encoding="utf-8")
                    text_without_imports = re.sub(r"^\s*import\s+.*Typeface.*$", "", text, flags=re.MULTILINE)
                    font_reuse_only = re.sub(r"\bTypeface(?:\s*\?|\.DEFAULT\b)", "", text_without_imports)
                    for m in re.finditer(r"\bTypeface\b", font_reuse_only):
                        bad.append(f"{path.relative_to(REPO_ROOT)}:{m.start()}")
                continue

            # All other App UI code must not reference Typeface at all.
            text = path.read_text(encoding="utf-8")
            text_without_style_spans = style_span_pattern.sub("", text)
            text_without_imports = re.sub(r"^\s*import\s+.*Typeface.*$", "", text_without_style_spans, flags=re.MULTILINE)
            for m in re.finditer(r"\bTypeface\b", text_without_imports):
                bad.append(f"{path.relative_to(REPO_ROOT)}:{m.start()}")

        self.assertFalse(
            bad,
            "App UI code must not use Typeface to set a font family "
            "(allowed: StyleSpan(Typeface.ITALIC/BOLD) and explicit ROM surface hooks): "
            + ", ".join(bad),
        )

    def test_duo_font_is_owned_by_one_cold_host_resource_loader(self):
        """Only the explicit status-bar font owner may load the bundled subset."""
        binding = (DUO_DIR / "DuoBinding.kt").read_text(encoding="utf-8")
        self.assertIn("if (view.usesCellularText) view.syncTypeface()", binding)
        owner = DUO_DIR / "DuoIosFont.kt"
        resource_loader = owner.read_text(encoding="utf-8")
        self.assertIn("@Synchronized", resource_loader)
        self.assertIn("font ?: requireNotNull(", resource_loader)
        self.assertIn("ModuleHelper.getModuleRes(context).getFont(R.font.duo_rounded_heavy)", resource_loader)
        banned_loaders = re.compile(r"\b(?:createFromAsset|createFromFile|FontRequest|FontsContract|ResourcesCompat\.getFont)\b")
        for path in sorted(DUO_DIR.glob("*.kt")):
            with self.subTest(path=path.name):
                text = path.read_text(encoding="utf-8")
                self.assertIsNone(banned_loaders.search(text))
                if path != owner:
                    self.assertNotIn(".getFont(", text)

    def test_about_attribution_text_views_allow_wrapping(self):
        """The about page attribution TextViews must not be constrained to one line."""
        source = APP_ROOT / "java/tv/withaibuild/customiuizer/AboutFragment.kt"
        text = source.read_text(encoding="utf-8")
        for name in ("about_maintainer", "about_based_on", "about_version"):
            with self.subTest(resource=name):
                declaration = re.search(r"Text\(stringResource\(R\.string\." + name + r"\b[^\n]+", text)
                self.assertIsNotNone(declaration)
                for restriction in ("maxLines", "overflow", "softWrap = false"):
                    self.assertNotIn(restriction, declaration[0])

    def test_about_attribution_text_not_shortened(self):
        """The attribution strings must name the current maintainer and upstream authors."""
        strings = APP_ROOT / "res" / "values" / "strings.xml"
        text = strings.read_text(encoding="utf-8")

        m = re.search(r'<string name="about_maintainer">(.*?)</string>', text, re.DOTALL)
        self.assertIsNotNone(m, "about_maintainer string missing")
        maintainer = m.group(1)
        self.assertIn("thetvplus", maintainer.lower(), "about_maintainer must name thetvplus")

        m = re.search(r'<string name="about_based_on">(.*?)</string>', text, re.DOTALL)
        self.assertIsNotNone(m, "about_based_on string missing")
        based_on = m.group(1)
        self.assertIn("mikanoshi", based_on.lower(), "about_based_on must name Mikanoshi")
        self.assertIn("monwf", based_on.lower(), "about_based_on must name MonwF")

    def test_font_resource_is_limited_to_the_explicit_host_subset(self):
        """Settings retain system typography; only the Duo subset is bundled."""
        font_dir = APP_ROOT / "res" / "font"
        self.assertEqual(["duo_rounded_heavy.ttf"], sorted(p.name for p in font_dir.iterdir()))
        font = (font_dir / "duo_rounded_heavy.ttf").read_bytes()
        self.assertEqual(b"\x00\x01\x00\x00", font[:4])
        consumers = {p.relative_to(APP_ROOT).as_posix()
                     for p in _all_kotlin_and_xml((APP_ROOT / "java", APP_ROOT / "res"))
                     if "R.font.duo_rounded_heavy" in p.read_text(encoding="utf-8")
                     or "@font/duo_rounded_heavy" in p.read_text(encoding="utf-8")}
        self.assertEqual({"java/tv/withaibuild/customiuizer/mods/duostatusbar/DuoIosFont.kt"}, consumers)


if __name__ == "__main__":
    unittest.main()
