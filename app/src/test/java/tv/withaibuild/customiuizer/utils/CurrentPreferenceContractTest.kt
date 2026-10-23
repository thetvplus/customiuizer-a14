package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentPreferenceContractTest {

    @Test fun retiredIslandPanelWidthIsDroppedAndCannotReturnFromABackup() {
        assertEquals(CurrentPreferenceContract.Kind.DROPPED,
            CurrentPreferenceContract.classify("pref_key_system_island_expanded_width"))
        assertFalse(CurrentPreferenceContract.isExportable("system_island_expanded_width"))
        val preferences = PrefMap().apply { put("system_island_expanded_width", 600) }
        val previous = tv.withaibuild.customiuizer.mods.island.IslandOptions.read(preferences)
        preferences.put("system_island_expanded_width", 240)
        assertEquals(previous, tv.withaibuild.customiuizer.mods.island.IslandOptions.read(preferences))
    }

    @Test
    fun catalogXmlKeyIsCurrent() {
        assertEquals(
            CurrentPreferenceContract.Kind.CURRENT,
            CurrentPreferenceContract.classify("pref_key_miuizer_launchericon"),
        )
        assertEquals(
            CurrentPreferenceContract.Kind.CURRENT,
            CurrentPreferenceContract.classify("system_strong_toast_mode"),
        )
    }

    @Test
    fun unknownKeyIsUnknown() {
        assertEquals(
            CurrentPreferenceContract.Kind.UNKNOWN,
            CurrentPreferenceContract.classify("pref_key_removed_old_feature"),
        )
        assertFalse(CurrentPreferenceContract.isExportable("pref_key_removed_old_feature"))
    }

    @Test
    fun droppedAndLegacyKeysAreDistinct() {
        assertEquals(
            CurrentPreferenceContract.Kind.LEGACY_MIGRATABLE,
            CurrentPreferenceContract.classify("pref_key_system_notif_disable_strong_toast"),
        )
        assertEquals(
            CurrentPreferenceContract.Kind.DROPPED,
            CurrentPreferenceContract.classify("pref_key_system_notif_strong_toast_width"),
        )
        assertEquals(
            CurrentPreferenceContract.Kind.DROPPED,
            CurrentPreferenceContract.classify("pref_key_system_strong_toast_position"),
        )
        assertEquals(
            CurrentPreferenceContract.Kind.DROPPED,
            CurrentPreferenceContract.classify("pref_key_system_strong_toast_bottom_offset"),
        )
    }

    @Test
    fun retiredHyperOsOnePreferencesAreDroppedInBothKeyForms() {
        val keys = listOf(
            "system_betterpopups_nohide",
            "system_betterpopups_delay",
            "system_minimalnotifview",
            "launcher_nozoomanim",
            "system_shortcut_app",
            "system_shortcut_app_user",
            "system_hidestatusbar_whenscreenrecord",
            "system_cc_tile_enabled_color_usemonet",
            "system_statusbar_icons_atleft_onkeyguard",
            "various_memorystats",
            "various_appusagestats",
            "various_aospnotiflog",
        )
        for (key in keys) {
            for (form in listOf(key, "pref_key_$key")) {
                assertEquals(form, CurrentPreferenceContract.Kind.DROPPED, CurrentPreferenceContract.classify(form))
                assertFalse(form, CurrentPreferenceContract.isExportable(form))
            }
        }
    }

    @Test
    fun retainedColorShortcutAndDuoPreferencesRemainCurrent() {
        val keys = listOf(
            "system_cc_tile_enabled_color",
            "system_cc_tile_enabled_color_custom",
            "system_cc_tile_enabled_iconcolor_custom",
            "system_clock_app",
            "system_clock_app_user",
            "system_calendar_app",
            "system_calendar_app_user",
            "system_statusbar_duo",
            "system_statusbar_duo_percent",
            "system_statusbar_duo_chargingbolt",
            "system_statusbar_duo_audio",
            "system_statusbar_duo_bold",
            "system_statusbar_duo_networkfallback",
            "system_statusbar_duo_cellularstyle",
            "system_statusbar_duo_headphonestyle",
            "system_statusbar_duo_headphonescale",
            "system_statusbar_duo_btbattery",
            "system_statusbar_duo_autosize",
            "system_statusbar_duo_size",
            "system_statusbar_duo_verticaloffset",
            "system_statusbar_duo_horizontalpadding",
        )
        for (key in keys) {
            assertEquals(key, CurrentPreferenceContract.Kind.CURRENT, CurrentPreferenceContract.classify(key))
            assertTrue(key, CurrentPreferenceContract.isExportable("pref_key_$key"))
        }
    }

    @Test
    fun internalAndExtraCurrentKeysAreRecognized() {
        assertEquals(
            CurrentPreferenceContract.Kind.INTERNAL,
            CurrentPreferenceContract.classify("internal_updater_service_names"),
        )
        assertEquals(
            CurrentPreferenceContract.Kind.CURRENT,
            CurrentPreferenceContract.classify("pref_key_qs_autorotate_state"),
        )
        assertTrue(CurrentPreferenceContract.isExportable("internal_miui_daemon_application_state"))
    }

    @Test
    fun revisionMarkerIsNonExportable() {
        assertEquals(
            CurrentPreferenceContract.Kind.NON_EXPORTABLE,
            CurrentPreferenceContract.classify(CurrentPreferenceContract.CONTRACT_REVISION_KEY),
        )
        assertFalse(CurrentPreferenceContract.isExportable(CurrentPreferenceContract.CONTRACT_REVISION_KEY))
    }

    @Test
    fun dynamicFamiliesStayCurrent() {
        val uuid = "0123456789abcdef0123456789abcdef"
        val keys = listOf(
            "pref_key_launcher_renameapps_list:com.foo|com.foo.Bar|0",
            "pref_key_system_cleanopenwith_apps_com.foo_bar|0",
            "pref_key_system_lockscreenshortcuts_right_$uuid",
            "pref_key_system_lockscreenshortcuts_right_${uuid}_action",
            "pref_key_system_clock_app_user",
            "pref_key_system_betterpopups_allowfloat_apps_black",
            "pref_key_system_vibration_amp_period_startstart_hour",
            "pref_key_various_disable_update_services_packages",
        )
        for (key in keys) {
            assertEquals(key, CurrentPreferenceContract.Kind.CURRENT, CurrentPreferenceContract.classify(key))
        }
    }

    @Test
    fun structuralPrefixIsNotADynamicFamily() {
        assertEquals(
            CurrentPreferenceContract.Kind.UNKNOWN,
            CurrentPreferenceContract.classify("pref_key_system_deleted_experiment"),
        )
        assertEquals(
            CurrentPreferenceContract.Kind.UNKNOWN,
            CurrentPreferenceContract.classify("pref_key_system:com.foo|bar|0"),
        )
    }

    @Test
    fun storageAndCanonicalFormsShareOneNormalizer() {
        assertEquals("system_strong_toast_mode", canonicalPreferenceKey("pref_key_system_strong_toast_mode"))
        assertEquals("system_strong_toast_mode", canonicalPreferenceKey("system_strong_toast_mode"))
        assertEquals("pref_key_system_strong_toast_mode", storagePreferenceKey("system_strong_toast_mode"))
        assertEquals("pref_key_system_strong_toast_mode", storagePreferenceKey("pref_key_system_strong_toast_mode"))
    }
}
