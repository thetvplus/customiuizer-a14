package tv.withaibuild.customiuizer.utils

import org.junit.Assert.*
import org.junit.Test

class IslandPreferenceDefaultsTest {
    @Test fun retiredEffectScopeAndSecondAppListArePrunedAndNeverRestored() {
        val retired = listOf("pref_key_system_island_media_effect_scope", "pref_key_system_island_media_return_apps")
        val entries = mapOf(retired[0] to "custom", retired[1] to setOf("old.media"),
            "pref_key_system_island_media_apps" to setOf("chosen.media"))
        val prefs = FakeSharedPreferences().apply {
            put(CurrentPreferenceContract.CONTRACT_REVISION_KEY, CurrentPreferenceContract.CONTRACT_REVISION)
            entries.forEach { (key, value) -> put(key, value) }
        }
        CurrentPreferenceContract.pruneOrphanPreferences(prefs)
        retired.forEach { assertFalse(prefs.contains(it)); assertFalse(CurrentPreferenceContract.isExportable(it)) }
        assertEquals(setOf("chosen.media"), prefs.getStringSet("pref_key_system_island_media_apps", emptySet()))
        val restored = FakeSharedPreferences()
        assertEquals(BackupRestore.Status.SUCCESS, BackupRestore.performRestore(BackupFormatV2.encode(entries).inputStream(),
            restored, installedPackages = setOf("old.media", "chosen.media"), launcherReconciler = { true }).status)
        retired.forEach { assertFalse(restored.contains(it)) }
        assertEquals(setOf("chosen.media"), restored.getStringSet("pref_key_system_island_media_apps", emptySet()))
    }

    @Test fun instantAndAbsorptionSelectionsRoundTripWithoutEnablingAnUnselectedMediaApp() {
        for (mode in listOf("none", "instant", "absorption")) {
            val prefs = FakeSharedPreferences()
            val result = BackupRestore.performRestore(BackupFormatV2.encode(mapOf(
                IslandPreferenceDefaults.absorptionKey to mode,
                "pref_key_system_island_media" to true,
                "pref_key_system_island_media_apps" to setOf("chosen.media"),
                "pref_key_system_island_media_return_apps" to setOf("effect.media")
            )).inputStream(), prefs, installedPackages = setOf("chosen.media", "effect.media"), launcherReconciler = { true })
            assertEquals(BackupRestore.Status.SUCCESS, result.status)
            assertEquals(mode, prefs.getString(IslandPreferenceDefaults.absorptionKey, null))
            assertEquals(setOf("chosen.media"), prefs.getStringSet("pref_key_system_island_media_apps", emptySet()))
            assertFalse(prefs.contains("pref_key_system_island_media_return_apps"))
        }
    }

    @Test fun retiredMotionPreferenceIsPrunedEvenAfterPriorMigrationAndNeverRestored() {
        val retired = "pref_key_system_island_simplified"
        val windows = "pref_key_system_island_media_apps"
        val prefs = FakeSharedPreferences().apply {
            put(CurrentPreferenceContract.CONTRACT_REVISION_KEY, CurrentPreferenceContract.CONTRACT_REVISION)
            put(retired, true); put("system_island_simplified", true)
            put(windows, setOf("music.example"))
        }
        CurrentPreferenceContract.pruneOrphanPreferences(prefs)
        assertFalse(prefs.contains(retired)); assertFalse(prefs.contains("system_island_simplified"))
        assertEquals(setOf("music.example"), prefs.getStringSet(windows, emptySet()))
        assertFalse(CurrentPreferenceContract.isExportable(retired))
        val restored = FakeSharedPreferences()
        val result = BackupRestore.performRestore(BackupFormatV2.encode(mapOf(retired to true,
            windows to setOf("display.example"))).inputStream(), restored,
            installedPackages = setOf("music.example", "display.example"), launcherReconciler = { true })
        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertFalse(restored.contains(retired))
        assertEquals(setOf("display.example"), restored.getStringSet(windows, emptySet()))
        assertEquals(setOf("display.example"), restored.getStringSet("pref_key_system_island_media_apps", emptySet()))
    }

    @Test fun initializationAndBackupRestorePersistBooleanAbsorptionBeforeSwitchCreation() {
        for ((old, enabled) in listOf("0" to false, "1" to true, false to false, true to true)) {
            val prefs = FakeSharedPreferences().apply { put(IslandPreferenceDefaults.absorptionKey, old) }
            IslandPreferenceDefaults.initialize(prefs)
            assertEquals(if (enabled) "absorption" else "none", prefs.getString(IslandPreferenceDefaults.absorptionKey, null))
            val writes = prefs.commitSnapshots.size
            IslandPreferenceDefaults.initialize(prefs)
            assertEquals(writes, prefs.commitSnapshots.size)
            val restored = FakeSharedPreferences()
            val result = BackupRestore.performRestore(
                BackupFormatV2.encode(mapOf(IslandPreferenceDefaults.absorptionKey to old)).inputStream(),
                restored, installedPackages = emptySet(), launcherReconciler = { true })
            assertEquals(BackupRestore.Status.SUCCESS, result.status)
            assertEquals(if (enabled) "absorption" else "none", restored.getString(IslandPreferenceDefaults.absorptionKey, null))
        }
    }
    @Test fun absorptionUsesTheSameKeyAndMigratesOldChoicesToModesIdempotently() {
        for ((raw, enabled) in listOf("1" to true, "0" to false, true to true, false to false,
            1 to true, 0 to false, "true" to true, "false" to false, "2" to false)) {
            val entries = mutableMapOf<String, Any?>(IslandPreferenceDefaults.absorptionKey to raw)
            IslandPreferenceDefaults.migrate(entries)
            assertEquals(if (enabled) "absorption" else "none", entries[IslandPreferenceDefaults.absorptionKey])
            val snapshot = entries.toMap()
            IslandPreferenceDefaults.migrate(entries)
            assertEquals(snapshot, entries)
        }
        val fresh = mutableMapOf<String, Any?>()
        IslandPreferenceDefaults.migrate(fresh)
        assertFalse(fresh.containsKey(IslandPreferenceDefaults.absorptionKey))
    }
    @Test fun retiredMessageKindsAreRemovedOnRestoreWithoutEnablingOrBroadeningAppAdmission() {
        val entries = mutableMapOf<String, Any?>("pref_key_system_island_live_kinds" to setOf("sms", "calendar", "timer"),
            "pref_key_system_island_chat" to false, "pref_key_system_island_notification_apps" to setOf("chosen.app"))
        IslandPreferenceDefaults.migrate(entries)
        assertEquals(setOf("timer"), entries["pref_key_system_island_live_kinds"])
        assertEquals(false, entries["pref_key_system_island_chat"])
        assertEquals(setOf("chosen.app"), entries["pref_key_system_island_notification_apps"])
        val snapshot = entries.toMap()
        IslandPreferenceDefaults.migrate(entries)
        assertEquals(snapshot, entries)
    }

    @Test fun automaticHeightPreservesExistingManualSizesAndExplicitChoices() {
        for (height in listOf(null, 32, 39, 40, 64)) {
            val entries = mutableMapOf<String, Any?>()
            if (height != null) entries["pref_key_system_island_height"] = height
            IslandPreferenceDefaults.migrate(entries)
            assertEquals(height == null || height == 40, entries[IslandPreferenceDefaults.heightAutoKey])
            entries[IslandPreferenceDefaults.heightAutoKey] = false
            IslandPreferenceDefaults.migrate(entries)
            assertEquals(false, entries[IslandPreferenceDefaults.heightAutoKey])
            entries[IslandPreferenceDefaults.heightAutoKey] = true
            IslandPreferenceDefaults.migrate(entries)
            assertEquals(true, entries[IslandPreferenceDefaults.heightAutoKey])
        }
    }

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
