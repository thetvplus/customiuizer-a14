package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentPreferenceContractTest {

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
