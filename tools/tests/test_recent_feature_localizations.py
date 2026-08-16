"""Localization contract for user-facing features added after r14.18.6."""

import collections
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
RES_ROOT = REPO_ROOT / "app" / "src" / "main" / "res"
LOCALES = (
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
RECENT_FEATURE_KEYS = """
about_compatibility_notes_title
about_contact_summary
about_contact_title
about_donate_image_description
about_donate_summary
about_donate_title
about_donation_unavailable
about_link_unavailable
about_notes_category
about_paypal_summary
about_paypal_title
about_repository_summary
about_repository_title
about_restart_notes_title
about_support_category
animation_scale_bridge_unavailable
animation_scale_write_failed
launcher_folderblur_disable
launcher_folderblur_disable_summ
settings_title
system_disable_window_blurs_summ
system_disable_window_blurs_title
system_recents_card_style_hide_title
system_recents_card_style_summ
system_strong_toast_island_offset_summ
system_strong_toast_island_offset_title
system_strong_toast_mode_dynamic_island
system_volume_mode_button_colors_summ
system_volume_mode_button_colors_title
various_block_location_permission_prompts_summ
various_block_location_permission_prompts_title
various_block_notification_permission_prompts_summ
various_block_notification_permission_prompts_title
various_clear_update_state_confirm
various_clear_update_state_failed
various_clear_update_state_success
various_clear_update_state_summ
various_clear_update_state_title
various_disable_miui_daemon_confirm
various_disable_miui_daemon_failed
various_disable_miui_daemon_success
various_disable_miui_daemon_summ
various_disable_miui_daemon_title
various_disable_miui_daemon_unavailable
various_disable_update_services_bridge_unavailable
various_disable_update_services_confirm
various_disable_update_services_failed
various_disable_update_services_summ
various_disable_update_services_title
various_disable_update_services_unavailable
various_disable_xiaomi_analytics_confirm
various_disable_xiaomi_analytics_summ
various_disable_xiaomi_analytics_title
various_exclusive_features_cat_title
various_permission_scope_failed
various_permission_scope_unavailable
various_remove_security_center_antivirus_confirm
various_remove_security_center_antivirus_summ
various_remove_security_center_antivirus_title
various_trim_miui_daemon_network_confirm
various_trim_miui_daemon_network_summ
various_trim_miui_daemon_network_title
various_trim_security_center_marketing_confirm
various_trim_security_center_marketing_summ
various_trim_security_center_marketing_title
various_xiaomi_trim_confirm_title
various_xiaomi_trim_failed
various_xiaomi_trim_success
various_xiaomi_trim_unavailable
""".split()
FORMAT_TOKEN = re.compile(r"%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z%]")


def _load_strings(folder: str) -> list[tuple[str, str]]:
    root = ET.parse(RES_ROOT / folder / "strings.xml").getroot()
    return [
        (element.attrib["name"], "".join(element.itertext()))
        for element in root
        if element.tag == "string"
    ]


class RecentFeatureLocalizationsTest(unittest.TestCase):
    def test_recent_strings_are_present_once_in_every_supported_locale(self):
        for locale in LOCALES:
            counts = collections.Counter(key for key, _ in _load_strings(locale))
            with self.subTest(locale=locale):
                invalid = {
                    key: counts[key]
                    for key in RECENT_FEATURE_KEYS
                    if counts[key] != 1
                }
                self.assertFalse(
                    invalid,
                    f"{locale}/strings.xml must contain every recent key once: {invalid}",
                )

    def test_recent_string_format_tokens_match_base_values(self):
        base = dict(_load_strings("values"))
        for locale in LOCALES:
            localized = dict(_load_strings(locale))
            with self.subTest(locale=locale):
                mismatches = {
                    key: (FORMAT_TOKEN.findall(base[key]), FORMAT_TOKEN.findall(localized[key]))
                    for key in RECENT_FEATURE_KEYS
                    if FORMAT_TOKEN.findall(base[key])
                    != FORMAT_TOKEN.findall(localized[key])
                }
                self.assertFalse(
                    mismatches,
                    f"{locale}/strings.xml format tokens differ from base: {mismatches}",
                )


if __name__ == "__main__":
    unittest.main()
