package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSelectionSanitizerTest {

    @Test
    fun sanitizeSelectionDropsMissingPackagesAndKeepsUserIdentifiers() {
        val selected = linkedSetOf(
            "com.example.present",
            "com.example.present|999",
            "com.example.missing",
        )

        val sanitized = AppSelectionSanitizer.sanitizeSelection(
            selected,
            setOf("com.example.present"),
        )

        assertEquals(
            linkedSetOf("com.example.present", "com.example.present|999"),
            sanitized,
        )
    }

    @Test
    fun sanitizeRestoredEntriesCoversWhiteBlackAndSingleAppSelectors() {
        val restored = linkedMapOf<String, Any?>(
            "pref_key_system_blocktoasts_apps" to linkedSetOf(
                "com.example.present",
                "com.example.missing",
            ),
            "pref_key_system_betterpopups_allowfloat_apps_black" to linkedSetOf(
                "com.example.missing",
            ),
            "pref_key_system_clock_app" to "com.example.missing|com.example.Clock",
            "pref_key_system_clock_app_user" to 999,
            "pref_key_controls_powerdt" to linkedSetOf("not", "an", "app-list"),
        )

        val result = AppSelectionSanitizer.sanitizeRestoredEntries(
            restored,
            setOf("com.example.present"),
        )
        val sanitized = result.entries

        assertEquals(
            linkedSetOf("com.example.present"),
            sanitized["pref_key_system_blocktoasts_apps"],
        )
        assertEquals(emptySet<String>(), sanitized["pref_key_system_betterpopups_allowfloat_apps_black"])
        assertFalse(sanitized.containsKey("pref_key_system_clock_app"))
        assertFalse(sanitized.containsKey("pref_key_system_clock_app_user"))
        assertEquals(linkedSetOf("not", "an", "app-list"), sanitized["pref_key_controls_powerdt"])
        assertTrue(result.changedPrimaryCount > 0)
    }

    @Test
    fun sanitizeAvailableSelectionNormalizesLegacyPrimaryUserAndDropsUnlistedHandlers() {
        val selected = linkedSetOf(
            "com.example.primary",
            "com.example.dual|999",
            "com.example.installed.but.not.handler|0",
        )

        val sanitized = AppSelectionSanitizer.sanitizeAvailableSelection(
            selected,
            setOf("com.example.primary|0", "com.example.dual|999"),
            multiUser = true,
        )

        assertEquals(
            linkedSetOf("com.example.primary|0", "com.example.dual|999"),
            sanitized,
        )
    }

    @Test
    fun selectorKeyDetectionDoesNotTouchUnrelatedSetsOrStrings() {
        assertTrue(AppSelectionSanitizer.isMultiAppSelectionKey("pref_key_system_blocktoasts_apps"))
        assertTrue(AppSelectionSanitizer.isMultiAppSelectionKey("pref_key_system_blocktoasts_apps_black"))
        assertFalse(AppSelectionSanitizer.isMultiAppSelectionKey("pref_key_system_noscreenlock_wifi"))

        assertTrue(AppSelectionSanitizer.isSingleAppSelectionKey("pref_key_system_shortcut_app"))
        assertTrue(AppSelectionSanitizer.isSingleAppSelectionKey("pref_key_controls_backlong_app"))
        assertTrue(AppSelectionSanitizer.isSingleAppSelectionKey("pref_key_controls_backlong_activity"))
        assertFalse(AppSelectionSanitizer.isSingleAppSelectionKey("pref_key_various_appsort"))
    }
}
