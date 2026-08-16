package tv.withaibuild.customiuizer.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupRestoreTest {

    // Legacy ObjectOutputStream type codes used for raw malicious fixtures.
    private val TC_NULL = 0x70
    private val TC_CLASSDESC = 0x72
    private val TC_OBJECT = 0x73
    private val TC_ENDBLOCKDATA = 0x78

    @Test
    fun generateBackupFilenameHasCorrectPrefixAndTimestamp() {
        val filename = BackupRestore.generateBackupFilename()
        assertTrue("Filename should start with r14bak_", filename.startsWith("r14bak_"))
        assertEquals("r14bak_".length + 10, filename.length)
        val timestamp = filename.substring("r14bak_".length)
        assertTrue("Timestamp should be 10 digits", timestamp.matches(Regex("\\d{10}")))
    }

    @Test
    fun capturePreRestoreSnapshotDefensiveCopiesStringSet() {
        val prefs = FakeSharedPreferences()
        val original = HashSet<String>().apply { add("a") }
        prefs.put("key1", original)

        val snapshot = BackupRestore.capturePreRestoreSnapshot(prefs)
        @Suppress("UNCHECKED_CAST")
        val copied = snapshot["key1"] as HashSet<String>

        assertEquals(original, copied)
        copied.add("b")

        @Suppress("UNCHECKED_CAST")
        val live = prefs.getStringSet("key1", emptySet()) as Set<String>
        assertFalse(live.contains("b"))
    }

    @Test
    fun decodeLegacyBackupAcceptsHashMap() {
        val map = HashMap<String, Any?>()
        map["pref_key_enabled"] = true
        val bytes = serialize(map)

        val decoded = BackupRestore.decodeLegacyBackup(bytes)
        assertNotNull(decoded)
        assertEquals(true, decoded["pref_key_enabled"])
    }

    @Test
    fun decodeLegacyBackupRejectsNonMapRoot() {
        val bytes = serialize("not a map")
        try {
            BackupRestore.decodeLegacyBackup(bytes)
            org.junit.Assert.fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue(e.message?.contains("not a Map") == true)
        }
    }

    @Test
    fun validateAndNormalizeEntriesSkipsTombstones() {
        val map = LinkedHashMap<Any?, Any?>()
        map["pref_key_system_notif_disable_strong_toast_always"] = true
        map["pref_key_system_notif_strong_toast_width"] = 100
        map["pref_key_miuizer_launchericon"] = true

        val (normalized, counts) = BackupRestore.validateAndNormalizeEntries(map)

        assertFalse(normalized.containsKey("pref_key_system_notif_disable_strong_toast_always"))
        assertFalse(normalized.containsKey("pref_key_system_notif_strong_toast_width"))
        assertEquals(true, normalized["pref_key_miuizer_launchericon"])
        assertEquals(2, counts.deprecatedIgnored)
        assertEquals(0, counts.unknownIgnored)
        assertEquals(1, counts.restored)
    }

    @Test
    fun validateAndNormalizeEntriesMigratesLegacyStrongToastDisable() {
        val map = LinkedHashMap<Any?, Any?>()
        map["pref_key_system_notif_disable_strong_toast"] = true
        map["pref_key_miuizer_locale"] = "en"

        val (normalized, counts) = BackupRestore.validateAndNormalizeEntries(map)

        assertFalse(normalized.containsKey("pref_key_system_notif_disable_strong_toast"))
        assertEquals("2", normalized["pref_key_system_strong_toast_mode"])
        assertEquals("en", normalized["pref_key_miuizer_locale"])
        assertEquals(1, counts.migrated)
        assertEquals(2, counts.restored)
        assertEquals(0, counts.unknownIgnored)
        assertEquals(0, counts.invalidSkipped)
    }

    @Test
    fun validateAndNormalizeEntriesIgnoresUnknownKeys() {
        val map = LinkedHashMap<Any?, Any?>()
        map["pref_key_removed_old_feature"] = true
        map["pref_key_miuizer_launchericon"] = false

        val (normalized, counts) = BackupRestore.validateAndNormalizeEntries(map)

        assertFalse(normalized.containsKey("pref_key_removed_old_feature"))
        assertEquals(false, normalized["pref_key_miuizer_launchericon"])
        assertEquals(1, counts.unknownIgnored)
        assertEquals(0, counts.invalidSkipped)
        assertEquals(1, counts.restored)
    }

    @Test
    fun validateAndNormalizeEntriesSkipsNonStringKey() {
        val map = LinkedHashMap<Any?, Any?>()
        map[123] = "value"
        map["pref_key_miuizer_launchericon"] = true

        val (normalized, counts) = BackupRestore.validateAndNormalizeEntries(map)

        assertEquals(1, counts.invalidSkipped)
        assertEquals(1, counts.restored)
        assertTrue(normalized.containsKey("pref_key_miuizer_launchericon"))
    }

    @Test
    fun validateAndNormalizeEntriesSkipsUnsupportedValue() {
        val map = LinkedHashMap<Any?, Any?>()
        map["pref_key_double"] = 1.5
        map["pref_key_miuizer_launchericon"] = 42

        val (normalized, counts) = BackupRestore.validateAndNormalizeEntries(map)

        assertEquals(1, counts.invalidSkipped)
        assertEquals(1, counts.restored)
        assertEquals(42, normalized["pref_key_miuizer_launchericon"])
    }

    @Test
    fun validateAndNormalizeEntriesSkipsMalformedStringSet() {
        val map = LinkedHashMap<Any?, Any?>()
        map["pref_key_system_blocktoasts_apps"] = LinkedHashSet<Any?>().apply { add("com.example"); add(123) }
        map["pref_key_miuizer_locale"] = "keep"

        val (normalized, counts) = BackupRestore.validateAndNormalizeEntries(map)

        assertEquals(1, counts.invalidSkipped)
        assertEquals(1, counts.restored)
        assertNull(normalized["pref_key_system_blocktoasts_apps"])
        assertEquals("keep", normalized["pref_key_miuizer_locale"])
    }

    @Test
    fun performRestoreReturnsSuccessForValidLegacyBackup() {
        val map = HashMap<String, Any?>()
        map["pref_key_miuizer_launchericon"] = false
        map["pref_key_miuizer_locale"] = "en"
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences().apply {
            put("pref_key_removed_old_feature", "value")
        }

        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertTrue(result.commitSucceeded)
        assertTrue(result.deviceReconciled)
        assertEquals(2, result.restored)
        assertFalse(prefs.getBoolean("pref_key_miuizer_launchericon", true))
        assertEquals("en", prefs.getString("pref_key_miuizer_locale", null))
        assertEquals("", prefs.getString(AppLocaleController.APPLIED_LOCALE_PREF_KEY, null))
        assertNull(prefs.getString("pref_key_removed_old_feature", null))
    }

    @Test
    fun performRestorePrimaryCommitFalseAndRollbackTrueUpdatesAndRestoresVisibleState() {
        val map = HashMap<String, Any?>()
        map["pref_key_system_strong_toast_mode"] = "2"
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences().apply {
            put("pref_key_miuizer_locale", "original")
            put(AppLocaleController.APPLIED_LOCALE_PREF_KEY, "original-marker")
            commitSequence = listOf(false, true).iterator()
        }

        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.FAILURE, result.status)
        assertFalse(result.commitSucceeded)
        assertTrue(result.rollbackAttempted)
        assertTrue(result.rollbackSucceeded)

        // Primary commit applies to in-memory map even though durability result is false.
        val primaryState = prefs.commitSnapshot(0)
        assertNull(primaryState["pref_key_miuizer_locale"])
        assertEquals("2", primaryState["pref_key_system_strong_toast_mode"])
        assertEquals("", primaryState[AppLocaleController.APPLIED_LOCALE_PREF_KEY])

        // Rollback restores original snapshot.
        val rollbackState = prefs.commitSnapshot(1)
        assertEquals("original", rollbackState["pref_key_miuizer_locale"])
        assertNull(rollbackState["pref_key_system_strong_toast_mode"])
        assertEquals("original-marker", rollbackState[AppLocaleController.APPLIED_LOCALE_PREF_KEY])

        // Final live state is original.
        assertEquals("original", prefs.getString("pref_key_miuizer_locale", null))
        assertNull(prefs.getString("pref_key_system_strong_toast_mode", null))
        assertEquals("original-marker", prefs.getString(AppLocaleController.APPLIED_LOCALE_PREF_KEY, null))
    }

    @Test
    fun performRestorePrimaryCommitFalseAndRollbackFalseRestoresOriginalReconcileMarker() {
        val map = HashMap<String, Any?>()
        map["pref_key_system_strong_toast_mode"] = "2"
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences().apply {
            put("pref_key_miuizer_locale", "original")
            put(AppLocaleController.APPLIED_LOCALE_PREF_KEY, "original-marker")
            commitSequence = listOf(false, false).iterator()
        }

        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.FAILURE, result.status)
        assertFalse(result.commitSucceeded)
        assertTrue(result.rollbackAttempted)
        assertFalse(result.rollbackSucceeded)

        // Both commits applied to in-memory map; rollback attempted even if durability result false.
        assertEquals("original", prefs.getString("pref_key_miuizer_locale", null))
        assertNull(prefs.getString("pref_key_system_strong_toast_mode", null))
        assertEquals("original-marker", prefs.getString(AppLocaleController.APPLIED_LOCALE_PREF_KEY, null))
    }

    @Test
    fun performRestoreDoesNotReconcileWhenCommitFails() {
        val map = HashMap<String, Any?>()
        map["pref_key_system_strong_toast_mode"] = "2"
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences().apply {
            put("pref_key_miuizer_locale", "value")
            commitSequence = listOf(false, true).iterator()
        }

        var reconcileCalled = false
        BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = {
                reconcileCalled = true
                true
            },
        )

        assertFalse(reconcileCalled)
    }

    @Test
    fun performRestoreReturnsPartialFailureWhenReconcileFails() {
        val map = HashMap<String, Any?>()
        map["pref_key_miuizer_launchericon"] = true
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences()

        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { false },
        )

        assertEquals(BackupRestore.Status.PARTIAL_FAILURE, result.status)
        assertTrue(result.commitSucceeded)
        assertFalse(result.deviceReconciled)
    }

    @Test
    fun performRestorePrimaryCommitFalseRestoresOriginalReconcileMarker() {
        val map = HashMap<String, Any?>()
        map["pref_key_miuizer_locale"] = "en"
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences().apply {
            put("pref_key_miuizer_locale", "zh-CN")
            put(AppLocaleController.APPLIED_LOCALE_PREF_KEY, "zh-CN")
            commitSequence = listOf(false, true).iterator()
        }

        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.FAILURE, result.status)
        assertFalse(result.commitSucceeded)
        assertTrue(result.rollbackAttempted)
        assertTrue(result.rollbackSucceeded)
        assertEquals(1, result.restored)

        // Primary commit in-memory state contains the local reconcile marker.
        val primaryState = prefs.commitSnapshot(0)
        assertEquals("en", primaryState["pref_key_miuizer_locale"])
        assertEquals("", primaryState[AppLocaleController.APPLIED_LOCALE_PREF_KEY])

        // Rollback restores the original snapshot, including its original marker.
        val rollbackState = prefs.commitSnapshot(1)
        assertEquals("zh-CN", rollbackState["pref_key_miuizer_locale"])
        assertEquals("zh-CN", rollbackState[AppLocaleController.APPLIED_LOCALE_PREF_KEY])

        // Final live state is the original snapshot.
        assertEquals("zh-CN", prefs.getString("pref_key_miuizer_locale", null))
        assertEquals("zh-CN", prefs.getString(AppLocaleController.APPLIED_LOCALE_PREF_KEY, null))
    }

    @Test
    fun performRestoreReconcilesBothLocaleAndLauncherWhenBothSucceed() {
        val map = HashMap<String, Any?>()
        map["pref_key_miuizer_locale"] = "en"
        map["pref_key_miuizer_launchericon"] = true
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences()

        var launcherCalled = false
        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { enabled ->
                launcherCalled = true
                assertEquals(true, enabled)
                true
            },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertTrue(result.commitSucceeded)
        assertTrue(result.deviceReconciled)
        assertTrue(launcherCalled)
        assertEquals(2, result.restored)
        assertEquals("", prefs.getString(AppLocaleController.APPLIED_LOCALE_PREF_KEY, null))
    }

    @Test
    fun performRestorePrimaryCommitSnapshotContainsReconcileMarker() {
        val map = HashMap<String, Any?>()
        map["pref_key_miuizer_locale"] = "en"
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences()

        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertTrue(result.commitSucceeded)
        assertEquals(1, result.restored)
        assertEquals(1, prefs.commitSnapshotCount())
        assertEquals("en", prefs.commitSnapshot(0)["pref_key_miuizer_locale"])
        assertEquals("", prefs.commitSnapshot(0)[AppLocaleController.APPLIED_LOCALE_PREF_KEY])
        assertEquals("en", prefs.getString("pref_key_miuizer_locale", null))
        assertEquals("", prefs.getString(AppLocaleController.APPLIED_LOCALE_PREF_KEY, null))
    }

    @Test
    fun performRestoreAutoLocaleAfterExplicitLocalLeavesReconcileMarker() {
        val map = HashMap<String, Any?>()
        map["pref_key_miuizer_locale"] = "auto"
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences().apply {
            put("pref_key_miuizer_locale", "en")
            put(AppLocaleController.APPLIED_LOCALE_PREF_KEY, "en")
        }

        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertTrue(result.commitSucceeded)
        assertEquals(1, result.restored)
        assertEquals("auto", prefs.getString("pref_key_miuizer_locale", null))
        assertEquals("", prefs.getString(AppLocaleController.APPLIED_LOCALE_PREF_KEY, null))
    }

    @Test
    fun performRestoreDoesNotCallApplyForLocaleMarker() {
        val map = HashMap<String, Any?>()
        map["pref_key_miuizer_locale"] = "en"
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences().apply {
            // If the restore path uses apply() for the marker, this will throw.
            applyShouldThrow = true
        }

        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertTrue(result.commitSucceeded)
        assertEquals(1, prefs.commitSnapshotCount())
        assertEquals("", prefs.getString(AppLocaleController.APPLIED_LOCALE_PREF_KEY, null))
    }

    @Test
    fun performRestoreRejectsTruncatedInput() {
        val map = HashMap<String, Any?>()
        map["pref_key_enabled"] = true
        val full = serialize(map)
        val truncated = full.copyOfRange(0, full.size / 2)
        val input = ByteArrayInputStream(truncated)

        val prefs = FakeSharedPreferences()
        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.FAILURE, result.status)
        assertFalse(result.commitSucceeded)
    }

    @Test
    fun performRestoreRejectsOversizedInput() {
        val huge = ByteArray(BackupFormatV2.MAX_FILE_SIZE.toInt() + 1)
        val input = ByteArrayInputStream(huge)

        val prefs = FakeSharedPreferences()
        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.FAILURE, result.status)
        assertFalse(result.commitSucceeded)
    }

    @Test
    fun performRestoreSanitizesAppSelectionsAndReportsRestoredCount() {
        val map = HashMap<String, Any?>()
        map["pref_key_system_clock_app"] = "com.missing|com.missing.Clock"
        map["pref_key_system_clock_app_user"] = "123"
        map["pref_key_system_blocktoasts_apps"] = HashSet<String>().apply {
            add("com.present")
            add("com.missing")
        }
        map["pref_key_miuizer_locale"] = "keep"
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences()

        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = setOf("com.present"),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertEquals(2, result.appSelectionsSanitized)
        // Only pref_key_system_blocktoasts_apps and pref_key_miuizer_locale survive.
        assertEquals(2, result.restored)
        assertEquals("keep", prefs.getString("pref_key_miuizer_locale", null))
        assertNull(prefs.getString("pref_key_system_clock_app", null))
        assertNull(prefs.getString("pref_key_system_clock_app_user", null))
        @Suppress("UNCHECKED_CAST")
        val set = prefs.getStringSet("pref_key_system_blocktoasts_apps", emptySet()) as Set<String>
        assertEquals(setOf("com.present"), set)
    }

    @Test
    fun performRestoreIgnoresDeviceDerivedKeys() {
        val map = HashMap<String, Any?>()
        map["pref_key_miuizer_locale"] = "en"
        map["pref_key_miuizer_locale_applied"] = "zh"
        map["pref_key_miuizer_synced_from_lsposed"] = true
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences()

        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertEquals(2, result.deprecatedIgnored)
        assertEquals(1, result.restored)
        assertEquals("en", prefs.getString("pref_key_miuizer_locale", null))
        // Source marker ignored; stageReconcileMarker writes the local reconcile marker.
        assertEquals("", prefs.getString(AppLocaleController.APPLIED_LOCALE_PREF_KEY, null))
        assertNull(prefs.getString("pref_key_miuizer_synced_from_lsposed", null))
    }

    @Test
    fun putSupportedPreferenceEntriesWritesAllSupportedTypes() {
        val prefs = FakeSharedPreferences()
        val editor = prefs.edit()
        val entries = LinkedHashMap<String, Any?>()
        entries["bool"] = true
        entries["int"] = 1
        entries["long"] = 2L
        entries["float"] = 1.5f
        entries["string"] = "value"
        entries["set"] = LinkedHashSet<String>().apply { add("a") }

        BackupRestore.putSupportedPreferenceEntries(editor, entries)
        assertTrue((editor as FakeSharedPreferences.FakeEditor).commit())

        assertTrue(prefs.getBoolean("bool", false))
        assertEquals(1, prefs.getInt("int", 0))
        assertEquals(2L, prefs.getLong("long", 0L))
        assertEquals(1.5f, prefs.getFloat("float", 0f), 0.001f)
        assertEquals("value", prefs.getString("string", null))
        @Suppress("UNCHECKED_CAST")
        assertEquals(setOf("a"), prefs.getStringSet("set", emptySet()) as Set<String>)
    }

    @Test
    fun decodeLegacyBackupRejectsLinkedHashMap() {
        val map = LinkedHashMap<String, Any?>()
        map["pref_key_test"] = true
        val bytes = serialize(map)
        try {
            BackupRestore.decodeLegacyBackup(bytes)
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue(e.cause is java.io.InvalidClassException)
        }
    }

    @Test
    fun performRestoreDetectsV2AndSucceeds() {
        val entries = linkedMapOf(
            "pref_key_miuizer_launchericon" to false,
        )
        val input = ByteArrayInputStream(BackupFormatV2.encode(entries))

        val prefs = FakeSharedPreferences()
        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertTrue(result.commitSucceeded)
        assertEquals(1, result.restored)
        assertFalse(prefs.getBoolean("pref_key_miuizer_launchericon", true))
    }

    @Test
    fun performRestoreDetectsLegacyJavaSerialization() {
        val map = HashMap<String, Any?>()
        map["pref_key_miuizer_launchericon"] = true
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences()
        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertTrue(result.commitSucceeded)
        assertTrue(prefs.getBoolean("pref_key_miuizer_launchericon", false))
    }

    @Test
    fun performRestoreRejectsUnrecognizedFormat() {
        val input = ByteArrayInputStream(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06))

        val prefs = FakeSharedPreferences()
        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.FAILURE, result.status)
        assertFalse(result.commitSucceeded)
    }

    @Test
    fun performBackupWritesV2AndFiltersDroppedAndNonExportable() {
        val prefs = FakeSharedPreferences().apply {
            put("pref_key_miuizer_launchericon", true)
            put("pref_key_miuizer_locale", "en")
            put("pref_key_system_notif_disable_strong_toast", true)
            put("pref_key_removed_old_feature", true)
            put("pref_key_miuizer_locale_applied", "zh")
        }

        val output = ByteArrayOutputStream()
        val success = BackupRestore.performBackup(prefs, output)

        assertTrue(success)

        val decoded = BackupFormatV2.decode(output.toByteArray())
        assertTrue(decoded.containsKey("pref_key_miuizer_launchericon"))
        assertTrue(decoded.containsKey("pref_key_miuizer_locale"))
        assertEquals("2", decoded["pref_key_system_strong_toast_mode"])
        assertFalse(decoded.containsKey("pref_key_system_notif_disable_strong_toast"))
        assertFalse(decoded.containsKey("pref_key_removed_old_feature"))
        assertFalse(decoded.containsKey("pref_key_miuizer_locale_applied"))
    }

    @Test
    fun performBackupFailsOnUnsupportedValueType() {
        val prefs = FakeSharedPreferences().apply {
            put("pref_key_miuizer_launchericon", 1.5)
        }

        try {
            BackupRestore.performBackup(prefs, ByteArrayOutputStream())
            fail("Expected BackupFormatV2.BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("Unsupported") == true)
        }
    }

    @Test
    fun performRestoreV2IgnoresDroppedAndDeviceDerivedKeys() {
        val entries = linkedMapOf(
            "pref_key_miuizer_launchericon" to true,
            "pref_key_system_notif_strong_toast_width" to 100,
            "pref_key_removed_old_feature" to true,
            "pref_key_miuizer_locale" to "en",
            "pref_key_miuizer_locale_applied" to "zh",
        )
        val input = ByteArrayInputStream(BackupFormatV2.encode(entries))

        val prefs = FakeSharedPreferences()
        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertEquals(2, result.deprecatedIgnored)
        assertEquals(1, result.unknownIgnored)
        assertEquals(0, result.invalidSkipped)
        assertEquals(2, result.restored)
        assertTrue(prefs.getBoolean("pref_key_miuizer_launchericon", false))
        assertEquals("en", prefs.getString("pref_key_miuizer_locale", null))
        assertNull(prefs.getString("pref_key_system_notif_strong_toast_width", null))
        assertNull(prefs.getString("pref_key_removed_old_feature", null))
        // Device-derived marker gets local reconcile marker, not source value.
        assertEquals("", prefs.getString(AppLocaleController.APPLIED_LOCALE_PREF_KEY, null))
    }

    @Test
    fun decodeLegacyBackupRejectsCustomReadObjectBeforeExecution() {
        EvilSerializable.readObjectCalled = false

        val map = HashMap<String, Any?>()
        map["pref_key_evil"] = EvilSerializable()
        val bytes = serialize(map)

        try {
            BackupRestore.decodeLegacyBackup(bytes)
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertFalse("Custom readObject must not run", EvilSerializable.readObjectCalled)
        }
    }

    @Test
    fun decodeLegacyBackupRejectsOversizedHashMapCapacity() {
        val map = HashMap<String, String>()
        repeat(4) { map["k$it"] = "v" }
        val bytes = serialize(map).toMutableList()

        // Locate the HashMap custom block: TC_BLOCKDATA (0x77) followed by length 8 (0x08).
        val blockIndex = (0 until bytes.size - 1)
            .first { i -> bytes[i] == 0x77.toByte() && bytes[i + 1] == 0x08.toByte() } + 2

        // Patch the declared capacity to exceed LEGACY_MAX_ARRAY_LENGTH.
        val cap = (BackupRestore.LEGACY_MAX_ARRAY_LENGTH + 1).toInt()
        bytes[blockIndex] = (cap ushr 24).toByte()
        bytes[blockIndex + 1] = (cap ushr 16 and 0xFF).toByte()
        bytes[blockIndex + 2] = (cap ushr 8 and 0xFF).toByte()
        bytes[blockIndex + 3] = (cap and 0xFF).toByte()

        try {
            BackupRestore.decodeLegacyBackup(bytes.toByteArray())
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected capacity bound", e.message?.contains("capacity") == true)
        }
    }

    @Test
    fun decodeLegacyBackupRejectsDeepGraph() {
        val root = HashMap<String, Any?>()
        var current = root
        repeat(20) {
            val next = HashMap<String, Any?>()
            current["x"] = next
            current = next
        }
        current["x"] = true

        try {
            BackupRestore.decodeLegacyBackup(serialize(root))
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected depth bound", e.message?.contains("depth") == true)
        }
    }

    @Test
    fun decodeLegacyBackupContainsAllSupportedTypes() {
        val map = HashMap<String, Any?>()
        map["bool"] = true
        map["int"] = 42
        map["long"] = 1234567890123L
        map["float"] = 3.14f
        map["string"] = "hello"
        map["set"] = HashSet<String>().apply { add("a"); add("b") }

        val decoded = BackupRestore.decodeLegacyBackup(serialize(map)) as Map<String, Any?>

        assertEquals(true, decoded["bool"])
        assertEquals(42, decoded["int"])
        assertEquals(1234567890123L, decoded["long"])
        assertEquals(3.14f, decoded["float"])
        assertEquals("hello", decoded["string"])
        @Suppress("UNCHECKED_CAST")
        val set = decoded["set"] as Set<String>
        assertEquals(setOf("a", "b"), set)
    }

    @Test
    fun decodeLegacyBackupRejectsTrailingByte() {
        val map = HashMap<String, Any?>()
        map["key"] = true
        val bytes = serialize(map).toMutableList()
        bytes.add(0x00)

        try {
            BackupRestore.decodeLegacyBackup(bytes.toByteArray())
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected trailing bytes rejection", e.message?.contains("trailing") == true)
        }
    }

    @Test
    fun decodeLegacyBackupRejectsSecondRootObject() {
        val map = HashMap<String, Any?>()
        map["key"] = true

        val output = ByteArrayOutputStream()
        ObjectOutputStream(output).use { oos ->
            oos.writeObject(map)
            oos.writeObject(map)
        }

        try {
            BackupRestore.decodeLegacyBackup(output.toByteArray())
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected trailing bytes rejection", e.message?.contains("trailing") == true)
        }
    }

    @Test
    fun decodeLegacyBackupRejectsDeepClassDescriptorChainWithoutStackOverflow() {
        val output = ByteArrayOutputStream()
        val data = java.io.DataOutputStream(output)

        // Stream header.
        data.writeByte(0xAC)
        data.writeByte(0xED)
        data.writeByte(0x00)
        data.writeByte(0x05)

        // Root TC_OBJECT.
        data.writeByte(TC_OBJECT)

        // Build a 20-level class-descriptor chain using java.lang.Number (an
        // allowlisted descriptor with no instance data). Each descriptor's
        // superclass is another TC_CLASSDESC, forcing superclass recursion.
        val chainDepth = 20
        val numberName = "java.lang.Number"
        val numberSuid = -8742448824652078965L
        val numberFlags = 0x02

        repeat(chainDepth) { index ->
            data.writeByte(TC_CLASSDESC)
            data.writeUTF(numberName)
            data.writeLong(numberSuid)
            data.writeByte(numberFlags)
            data.writeShort(0)
            data.writeByte(TC_ENDBLOCKDATA)
            if (index == chainDepth - 1) {
                data.writeByte(TC_NULL)
            }
        }

        try {
            BackupRestore.decodeLegacyBackup(output.toByteArray())
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected descriptor depth bound", e.message?.contains("descriptor depth") == true)
        }
    }

    @Test
    fun decodeLegacyBackupRejectsHandleLimit() {
        val map = HashMap<String, HashSet<String>>()
        repeat(100) { i ->
            val set = HashSet<String>()
            repeat(BackupFormatV2.MAX_SET_ITEMS) { j ->
                set.add(String.format("s%05d", i * BackupFormatV2.MAX_SET_ITEMS + j))
            }
            map["set$i"] = set
        }

        try {
            BackupRestore.decodeLegacyBackup(serialize(map))
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected handle bound", e.message?.contains("handle") == true)
        }
    }

    @Test
    fun performRestoreLegacyCountsNonStringKeyAsInvalidSkipped() {
        val map = HashMap<Any?, Any?>()
        map[123] = "bad-key"
        map["pref_key_miuizer_launchericon"] = true

        val prefs = FakeSharedPreferences()
        val result = BackupRestore.performRestore(
            ByteArrayInputStream(serialize(map)),
            prefs,
            emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertEquals(1, result.invalidSkipped)
        assertEquals(1, result.restored)
        assertTrue(prefs.getBoolean("pref_key_miuizer_launchericon", false))
        assertNull(prefs.getString("123", null))
    }

    @Test
    fun performRestoreLegacyCountsMalformedSetAsInvalidSkipped() {
        val map = HashMap<Any?, Any?>()
        val set = HashSet<Any?>()
        set.add("com.ok")
        set.add(123)
        map["bad_set"] = set
        map["pref_key_miuizer_launchericon"] = true

        val prefs = FakeSharedPreferences()
        val result = BackupRestore.performRestore(
            ByteArrayInputStream(serialize(map)),
            prefs,
            emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertEquals(1, result.invalidSkipped)
        assertEquals(1, result.restored)
        assertNull(prefs.getStringSet("bad_set", null))
        assertTrue(prefs.getBoolean("pref_key_miuizer_launchericon", false))
    }

    @Test
    fun performRestoreLegacyCountsNestedMapAsInvalidSkipped() {
        val map = HashMap<Any?, Any?>()
        val nested = HashMap<String, Any?>()
        nested["x"] = true
        map["bad_value"] = nested
        map["pref_key_miuizer_launchericon"] = true

        val prefs = FakeSharedPreferences()
        val result = BackupRestore.performRestore(
            ByteArrayInputStream(serialize(map)),
            prefs,
            emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertEquals(1, result.invalidSkipped)
        assertEquals(1, result.restored)
        assertNull(prefs.getString("bad_value", null))
        assertTrue(prefs.getBoolean("pref_key_miuizer_launchericon", false))
    }

    @Test
    fun legacyHashMapRejectsNaNLoadFactor() {
        val map = HashMap<String, Any?>()
        map["key"] = true
        val bytes = patchHashMapLoadFactor(serialize(map), Float.NaN)

        try {
            BackupRestore.decodeLegacyBackup(bytes)
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected loadFactor validation", e.message?.contains("loadFactor") == true)
        }
    }

    @Test
    fun legacyHashMapRejectsZeroOrNegativeLoadFactor() {
        val map = HashMap<String, Any?>()
        map["key"] = true
        val bytes = patchHashMapLoadFactor(serialize(map), -0.5f)

        try {
            BackupRestore.decodeLegacyBackup(bytes)
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected loadFactor validation", e.message?.contains("loadFactor") == true)
        }
    }

    @Test
    fun legacyHashMapRejectsPositiveInfinityLoadFactor() {
        val map = HashMap<String, Any?>()
        map["key"] = true
        val bytes = patchHashMapLoadFactor(serialize(map), Float.POSITIVE_INFINITY)

        try {
            BackupRestore.decodeLegacyBackup(bytes)
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected loadFactor validation", e.message?.contains("loadFactor") == true)
        }
    }

    @Test
    fun legacyHashMapRejectsNegativeExplicitCapacity() {
        val map = HashMap<String, Any?>()
        map["key"] = true
        val bytes = patchHashMapCapacity(serialize(map), -1)

        try {
            BackupRestore.decodeLegacyBackup(bytes)
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected negative capacity rejection", e.message?.contains("capacity") == true)
        }
    }

    @Test
    fun legacyHashSetRejectsNegativeCapacity() {
        val set = HashSet<String>()
        set.add("x")
        val bytes = patchHashSetCapacity(serialize(set), -1)

        try {
            BackupRestore.decodeLegacyBackup(bytes)
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected negative capacity rejection", e.message?.contains("capacity") == true)
        }
    }

    @Test
    fun legacyHashSetRejectsNaNLoadFactor() {
        val set = HashSet<String>()
        set.add("x")
        val bytes = patchHashSetLoadFactor(serialize(set), Float.NaN)

        try {
            BackupRestore.decodeLegacyBackup(bytes)
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected loadFactor validation", e.message?.contains("loadFactor") == true)
        }
    }

    @Test
    fun legacyHashSetRejectsZeroOrNegativeLoadFactor() {
        val set = HashSet<String>()
        set.add("x")
        val bytes = patchHashSetLoadFactor(serialize(set), 0f)

        try {
            BackupRestore.decodeLegacyBackup(bytes)
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected loadFactor validation", e.message?.contains("loadFactor") == true)
        }
    }

    @Test
    fun legacyHashSetRejectsPositiveInfinityLoadFactor() {
        val set = HashSet<String>()
        set.add("x")
        val bytes = patchHashSetLoadFactor(serialize(set), Float.POSITIVE_INFINITY)

        try {
            BackupRestore.decodeLegacyBackup(bytes)
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected loadFactor validation", e.message?.contains("loadFactor") == true)
        }
    }

    @Test
    fun catalogKnownPreferenceRoundTripsWithoutBackupWhitelist() {
        assertTrue(
            "launchericon must come from the generated catalog, not BackupRestore.DROPPED_KEYS",
            "pref_key_miuizer_launchericon" !in BackupRestore.DROPPED_KEYS,
        )
        val prefs = FakeSharedPreferences().apply {
            put("pref_key_miuizer_launchericon", true)
        }
        val output = ByteArrayOutputStream()
        assertTrue(BackupRestore.performBackup(prefs, output))

        val restored = FakeSharedPreferences()
        val result = BackupRestore.performRestore(
            ByteArrayInputStream(output.toByteArray()),
            restored,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )
        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertEquals(0, result.unknownIgnored)
        assertTrue(restored.getBoolean("pref_key_miuizer_launchericon", false))
    }

    @Test
    fun backupOmitsOrphanKeysWhileKeepingCurrentKeys() {
        val prefs = FakeSharedPreferences().apply {
            put("pref_key_miuizer_locale", "en")
            put("pref_key_removed_old_feature", true)
        }
        val output = ByteArrayOutputStream()
        assertTrue(BackupRestore.performBackup(prefs, output))
        val decoded = BackupFormatV2.decode(output.toByteArray())
        assertEquals("en", decoded["pref_key_miuizer_locale"])
        assertFalse(decoded.containsKey("pref_key_removed_old_feature"))
    }

    @Test
    fun restoreIgnoresUnknownKeysWithoutTreatingThemAsCorruption() {
        val entries = linkedMapOf(
            "pref_key_miuizer_launchericon" to true,
            "pref_key_removed_old_feature" to true,
        )
        val result = BackupRestore.performRestore(
            ByteArrayInputStream(BackupFormatV2.encode(entries)),
            FakeSharedPreferences(),
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )
        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertEquals(1, result.unknownIgnored)
        assertEquals(0, result.invalidSkipped)
        assertEquals(1, result.restored)
        assertTrue(result.commitSucceeded)
    }

    @Test
    fun restoreMigratesLegacyStrongToastDisableAndDropsOldKey() {
        val entries = linkedMapOf(
            "pref_key_system_notif_disable_strong_toast" to true,
            "pref_key_miuizer_locale" to "en",
        )
        val prefs = FakeSharedPreferences()
        val result = BackupRestore.performRestore(
            ByteArrayInputStream(BackupFormatV2.encode(entries)),
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )
        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertEquals(1, result.migrated)
        assertEquals("2", prefs.getString("pref_key_system_strong_toast_mode", null))
        assertNull(prefs.getString("pref_key_system_notif_disable_strong_toast", null))
        assertEquals("en", prefs.getString("pref_key_miuizer_locale", null))
    }

    @Test
    fun restoreKeepsDynamicFamilyAndInternalKeys() {
        val uuid = "0123456789abcdef0123456789abcdef"
        val entries = linkedMapOf<String, Any?>(
            "pref_key_launcher_renameapps_list:com.foo|com.foo.Bar|0" to "Renamed",
            "pref_key_system_cleanopenwith_apps_com.foo_bar|0" to 7,
            "pref_key_system_lockscreenshortcuts_right_${uuid}_action" to 8,
            "pref_key_system_clock_app_user" to 10,
            "pref_key_system_betterpopups_allowfloat_apps_black" to HashSet(setOf("com.present")),
            "pref_key_system_vibration_amp_period_startstart_hour" to 22,
            "internal_updater_service_names" to "svc.one",
            "pref_key_qs_autorotate_state" to 1,
        )
        val prefs = FakeSharedPreferences()
        val result = BackupRestore.performRestore(
            ByteArrayInputStream(BackupFormatV2.encode(entries)),
            prefs,
            installedPackages = setOf("com.present"),
            launcherReconciler = { true },
        )
        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertEquals(0, result.unknownIgnored)
        assertEquals("Renamed", prefs.getString("pref_key_launcher_renameapps_list:com.foo|com.foo.Bar|0", null))
        assertEquals(7, prefs.getInt("pref_key_system_cleanopenwith_apps_com.foo_bar|0", 0))
        assertEquals(8, prefs.getInt("pref_key_system_lockscreenshortcuts_right_${uuid}_action", 0))
        assertEquals(10, prefs.getInt("pref_key_system_clock_app_user", 0))
        @Suppress("UNCHECKED_CAST")
        assertEquals(
            setOf("com.present"),
            prefs.getStringSet("pref_key_system_betterpopups_allowfloat_apps_black", emptySet()) as Set<String>,
        )
        assertEquals(22, prefs.getInt("pref_key_system_vibration_amp_period_startstart_hour", 0))
        assertEquals("svc.one", prefs.getString("internal_updater_service_names", null))
        assertEquals(1, prefs.getInt("pref_key_qs_autorotate_state", 0))
    }

    @Test
    fun localOrphanPruneRemovesStaleKeysOnce() {
        val prefs = FakeSharedPreferences().apply {
            put("pref_key_miuizer_locale", "en")
            put("pref_key_removed_old_feature", true)
            put("pref_key_system_notif_disable_strong_toast", true)
            put("internal_updater_service_names", "svc.one")
            put(AppLocaleController.APPLIED_LOCALE_PREF_KEY, "en")
        }
        assertTrue(CurrentPreferenceContract.pruneOrphanPreferences(prefs))
        assertEquals("en", prefs.getString("pref_key_miuizer_locale", null))
        assertEquals("2", prefs.getString("pref_key_system_strong_toast_mode", null))
        assertNull(prefs.getString("pref_key_removed_old_feature", null))
        assertNull(prefs.getString("pref_key_system_notif_disable_strong_toast", null))
        assertEquals("svc.one", prefs.getString("internal_updater_service_names", null))
        assertEquals("en", prefs.getString(AppLocaleController.APPLIED_LOCALE_PREF_KEY, null))
        assertEquals(
            CurrentPreferenceContract.CONTRACT_REVISION,
            prefs.getInt(CurrentPreferenceContract.CONTRACT_REVISION_KEY, 0),
        )
        assertFalse(CurrentPreferenceContract.pruneOrphanPreferences(prefs))
    }

    private fun findFirstClassDataEnd(bytes: List<Byte>): Int {
        for (i in 0 until bytes.size - 1) {
            if (bytes[i] == TC_ENDBLOCKDATA.toByte() && bytes[i + 1] == TC_NULL.toByte()) {
                return i + 2
            }
        }
        throw IllegalArgumentException("Class data end not found in serialized fixture")
    }

    private fun patchHashMapLoadFactor(bytes: ByteArray, newLoadFactor: Float): ByteArray {
        val list = bytes.toMutableList()
        val end = findFirstClassDataEnd(list)
        val bits = java.lang.Float.floatToIntBits(newLoadFactor)
        list[end] = (bits ushr 24).toByte()
        list[end + 1] = (bits ushr 16).toByte()
        list[end + 2] = (bits ushr 8).toByte()
        list[end + 3] = bits.toByte()
        return list.toByteArray()
    }

    private fun patchHashMapCapacity(bytes: ByteArray, newCapacity: Int): ByteArray {
        val list = bytes.toMutableList()
        val end = findFirstClassDataEnd(list)
        // JDK 17 HashMap custom block: TC_BLOCKDATA(1) + len(1) + capacity(4) + size(4)
        val capIndex = end + 8 + 2
        list[capIndex] = (newCapacity ushr 24).toByte()
        list[capIndex + 1] = (newCapacity ushr 16).toByte()
        list[capIndex + 2] = (newCapacity ushr 8).toByte()
        list[capIndex + 3] = newCapacity.toByte()
        return list.toByteArray()
    }

    private fun patchHashSetCapacity(bytes: ByteArray, newCapacity: Int): ByteArray {
        val list = bytes.toMutableList()
        val end = findFirstClassDataEnd(list)
        // JDK 17 HashSet custom block: TC_BLOCKDATA(1) + len(1) + capacity(4) + loadFactor(4) + size(4)
        val capIndex = end + 2
        list[capIndex] = (newCapacity ushr 24).toByte()
        list[capIndex + 1] = (newCapacity ushr 16).toByte()
        list[capIndex + 2] = (newCapacity ushr 8).toByte()
        list[capIndex + 3] = newCapacity.toByte()
        return list.toByteArray()
    }

    private fun patchHashSetLoadFactor(bytes: ByteArray, newLoadFactor: Float): ByteArray {
        val list = bytes.toMutableList()
        val end = findFirstClassDataEnd(list)
        // JDK 17 HashSet custom block: TC_BLOCKDATA(1) + len(1) + capacity(4) + loadFactor(4) + size(4)
        val lfIndex = end + 6
        val bits = java.lang.Float.floatToIntBits(newLoadFactor)
        list[lfIndex] = (bits ushr 24).toByte()
        list[lfIndex + 1] = (bits ushr 16).toByte()
        list[lfIndex + 2] = (bits ushr 8).toByte()
        list[lfIndex + 3] = bits.toByte()
        return list.toByteArray()
    }

    private fun serialize(obj: Any): ByteArray {
        val output = ByteArrayOutputStream()
        ObjectOutputStream(output).use { it.writeObject(obj) }
        return output.toByteArray()
    }

    class EvilSerializable : java.io.Serializable {
        companion object {
            @JvmField
            var readObjectCalled = false
        }

        private fun readObject(s: java.io.ObjectInputStream) {
            readObjectCalled = true
        }
    }
}
