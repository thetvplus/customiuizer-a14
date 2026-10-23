package tv.withaibuild.customiuizer.utils

import org.junit.Assert.*
import org.junit.Test

class IslandPreferenceDefaultsTest {
    @Test fun newInstallIsSilentAndLegacyIslandPreservesItsFormerFeedback() {
        val fresh = mutableMapOf<String,Any?>()
        IslandPreferenceDefaults.migrate(fresh)
        assertTrue(IslandPreferenceDefaults.feedbackKeys.all { fresh[it] == false })
        val upgraded = mutableMapOf<String,Any?>("pref_key_system_strong_toast_mode" to "3")
        IslandPreferenceDefaults.migrate(upgraded)
        assertTrue(IslandPreferenceDefaults.feedbackKeys.all { upgraded[it] == true })
        upgraded["pref_key_system_island_feedback_wifi"] = false
        IslandPreferenceDefaults.migrate(upgraded)
        assertEquals(false, upgraded["pref_key_system_island_feedback_wifi"])
    }

    @Test fun legacyModeFourAndWaveformOffMigrateWithoutChangingStoredChoices() {
        val entries = mutableMapOf<String, Any?>("pref_key_system_strong_toast_mode" to "4",
            "pref_key_system_island_waveform" to false)
        IslandPreferenceDefaults.migrate(entries)
        assertTrue(IslandPreferenceDefaults.feedbackKeys.all { entries[it] == true })
        assertEquals("0", entries[IslandPreferenceDefaults.waveformModeKey])
        assertEquals(false, entries["pref_key_system_island_waveform"])
        entries[IslandPreferenceDefaults.waveformModeKey] = "2"
        IslandPreferenceDefaults.migrate(entries)
        assertEquals("2", entries[IslandPreferenceDefaults.waveformModeKey])
        assertTrue(CurrentPreferenceContract.isExportable("pref_key_system_island_waveform"))
        assertTrue(CurrentPreferenceContract.isExportable(IslandPreferenceDefaults.waveformModeKey))
    }

    @Test fun importedWaveformModesUseAStringStorageContractBeforeListPreferenceInflation() {
        for ((raw, expected) in listOf(2 to "2", 0 to "0", 99 to "1", true to "1")) {
            val entries = mutableMapOf<String, Any?>(IslandPreferenceDefaults.waveformModeKey to raw,
                "pref_key_system_island_waveform" to false)
            IslandPreferenceDefaults.migrate(entries)
            assertEquals(expected, entries[IslandPreferenceDefaults.waveformModeKey])
            assertEquals(false, entries["pref_key_system_island_waveform"])
        }
    }

    @Test fun partialNewFormatBackupNeverReenablesUnselectedKindsAndAllGesturePayloadsAreExportable() {
        val restored = mutableMapOf<String,Any?>("pref_key_system_strong_toast_mode" to "3",
            "pref_key_system_island_feedback_dnd" to true)
        IslandPreferenceDefaults.migrate(restored)
        assertEquals(true,restored["pref_key_system_island_feedback_dnd"])
        assertEquals(false,restored["pref_key_system_island_feedback_wifi"])
        for (suffix in listOf("left","right","down","double","long")) {
            val key="pref_key_system_island_music_$suffix"
            for (payload in listOf("_action","_app","_app_user","_shortcut_intent","_activity","_activity_user"))
                assertTrue(key+payload, CurrentPreferenceContract.isExportable(key+payload))
        }
        for (key in IslandPreferenceDefaults.feedbackKeys + "pref_key_system_island_notification_apps")
            assertTrue(key,CurrentPreferenceContract.isExportable(key))
    }
}
