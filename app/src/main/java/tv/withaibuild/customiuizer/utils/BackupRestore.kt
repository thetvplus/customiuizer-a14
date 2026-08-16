package tv.withaibuild.customiuizer.utils

import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import tv.withaibuild.customiuizer.mods.utils.FatalErrors

/**
 * M2 backup / restore owner.
 *
 * This object is intentionally cold-path only: no startup cost, no resident state,
 * no observer, no background worker. The only mutable data it creates lives for the
 * duration of one user-initiated backup or restore.
 *
 * M2 final format:
 * - Backup writes V2 (CUI2 / typed entries / CRC32).
 * - Restore auto-detects V2 or legacy Java serialization.
 * - Legacy reader is a focused restricted decoder; no `ObjectInputStream` / `ObjectInputFilter`.
 */
object BackupRestore {

    /** Default backup filename prefix + MMddHHmmss local wall-clock timestamp. */
    private val BACKUP_FILENAME_FORMATTER =
        DateTimeFormatter.ofPattern("MMddHHmmss", Locale.US)

    /** SharedPreferences storage keys removed in the A14/HyperOS 1 line. */
    val DROPPED_KEYS = setOf(
        "pref_key_system_notif_disable_strong_toast",
        "pref_key_system_notif_disable_strong_toast_always",
        "pref_key_system_notif_disable_strong_toast_dnd",
        "pref_key_system_notif_strong_toast_width",
        "pref_key_system_strong_toast_position",
        "pref_key_system_strong_toast_bottom_offset",
    )

    /** Source-device derived or runtime markers that must not be trusted on restore. */
    val NON_EXPORTABLE_KEYS = setOf(
        "pref_key_miuizer_locale_applied",
        "pref_key_miuizer_synced_from_lsposed",
        CurrentPreferenceContract.CONTRACT_REVISION_KEY,
    )

    /** Legacy Java serialization stream header: 0xAC 0xED 0x00 0x05. */
    private val LEGACY_MAGIC = byteArrayOf(0xAC.toByte(), 0xED.toByte(), 0x00, 0x05)

    /**
     * Legacy deserialization graph limits.
     *
     * Evidence from a JDK 17 fixture (5 entries + 3-item HashSet):
     *   maxDepth = 3, maxReferences = 23, maxArrayLength = 16.
     * Headroom for a full 4096-entry HashMap/HashSet table (next power-of-two
     * above 4096/0.75 is 8192). 16384 provides margin while staying small.
     */
    const val LEGACY_MAX_DEPTH = 16L
    const val LEGACY_MAX_DESCRIPTOR_DEPTH = 8L
    const val LEGACY_MAX_REFERENCES = 100_000L
    const val LEGACY_MAX_ARRAY_LENGTH = 16_384L

    enum class Status { SUCCESS, PARTIAL_FAILURE, FAILURE }

    data class RestoreResult(
        val status: Status,
        val restored: Int = 0,
        val deprecatedIgnored: Int = 0,
        val invalidSkipped: Int = 0,
        val unknownIgnored: Int = 0,
        val appSelectionsSanitized: Int = 0,
        val migrated: Int = 0,
        val commitSucceeded: Boolean = false,
        val commitConfirmedDurable: Boolean = false,
        val deviceReconciled: Boolean = false,
        val rollbackAttempted: Boolean = false,
        val rollbackSucceeded: Boolean = false,
    ) {
        val isSuccess: Boolean get() = status == Status.SUCCESS
    }

    /**
     * Generates a backup filename using an immutable / thread-safe
     * [java.time.DateTimeFormatter] and the device-local wall clock.
     */
    @JvmStatic
    fun generateBackupFilename(): String =
        "r14bak_" + BACKUP_FILENAME_FORMATTER.format(LocalDateTime.now())

    /**
     * Copies and filters the current preference entries for backup output.
     *
     * Only current-contract exportable keys are encoded. Dropped, non-exportable,
     * and unknown keys are omitted. Unsupported value types on exportable keys
     * cause a [BackupFormatV2.BackupFormatException] rather than silent partial output.
     */
    @JvmStatic
    fun filterBackupEntries(prefs: SharedPreferences): Map<String, Any?> {
        val source = prefs.all
        val filtered = LinkedHashMap<String, Any?>(source.size)
        for ((key, value) in source) {
            if (!CurrentPreferenceContract.isExportable(key)) continue
            if (value != null && !isSupportedValue(value)) {
                throw BackupFormatV2.BackupFormatException(
                    "Unsupported backup value type for key '$key': ${value.javaClass}"
                )
            }
            filtered[key] = when (value) {
                is Set<*> -> HashSet<String>(value.size).apply {
                    @Suppress("UNCHECKED_CAST")
                    addAll(value as Set<String>)
                }
                else -> value
            }
        }
        CurrentPreferenceContract.applyMigrations(filtered)
        val exportable = LinkedHashMap<String, Any?>(filtered.size)
        for ((key, value) in filtered) {
            when (CurrentPreferenceContract.classify(key)) {
                CurrentPreferenceContract.Kind.CURRENT,
                CurrentPreferenceContract.Kind.INTERNAL -> exportable[key] = value
                else -> Unit
            }
        }
        return exportable
    }

    @JvmStatic
    private fun isSupportedValue(value: Any?): Boolean {
        return value == null ||
            value is Boolean ||
            value is Int ||
            value is Long ||
            value is Float ||
            value is String ||
            (value is Set<*> && value.all { it is String })
    }

    /**
     * Writes the current preferences to [output] in the M2 V2 format.
     *
     * The output stream is closed by this function.
     *
     * @return true if the encoded bytes were fully written.
     * @throws BackupFormatV2.BackupFormatException if a bound is exceeded or a
     *     value is unsupported.
     */
    @JvmStatic
    fun performBackup(
        prefs: SharedPreferences,
        output: OutputStream,
    ): Boolean {
        val entries = filterBackupEntries(prefs)
        val encoded = BackupFormatV2.encode(entries)
        output.use { out ->
            out.write(encoded)
            out.flush()
        }
        return true
    }

    /**
     * Captures a defensive copy of the current preferences for best-effort rollback.
     *
     * String sets are copied so later mutation of the snapshot cannot affect the
     * original and vice-versa. The snapshot lives only for the current restore call.
     */
    @JvmStatic
    fun capturePreRestoreSnapshot(prefs: SharedPreferences): Map<String, Any?> {
        val snapshot = HashMap<String, Any?>(prefs.all.size * 4 / 3 + 1)
        for ((key, value) in prefs.all) {
            snapshot[key] = when (value) {
                is Set<*> -> HashSet<String>(value.size).apply {
                    @Suppress("UNCHECKED_CAST")
                    addAll(value as Set<String>)
                }
                else -> value
            }
        }
        return snapshot
    }

    /**
     * Bounded read of a backup input. Returns `null` if the input exceeds
     * [BackupFormatV2.MAX_FILE_SIZE].
     */
    @JvmStatic
    @Throws(IOException::class)
    fun readBoundedInputStream(input: InputStream, maxBytes: Long = BackupFormatV2.MAX_FILE_SIZE): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    /**
     * Decodes a V2 or restricted legacy Java-serialized backup.
     *
     * - First 4 bytes `CUI2` → V2.
     * - First 4 bytes `AC ED 00 05` → legacy.
     * - Anything else → structural failure.
     *
     * The legacy path uses the focused restricted decoder (no `ObjectInputStream` /
     * `ObjectInputFilter`) and validates the root object. No unchecked
     * `Map<String, Any?>` cast is performed before format detection.
     *
     * @throws BackupRestoreException if the file is not a recognized backup.
     */
    @JvmStatic
    fun decodeBackup(bytes: ByteArray): Map<*, *> {
        if (bytes.size < 4) {
            throw BackupRestoreException("Backup file too short")
        }

        val firstFour = ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)

        val decoded = when {
            firstFour == BackupFormatV2.MAGIC -> BackupFormatV2.decode(bytes)
            bytes.size >= LEGACY_MAGIC.size &&
                bytes[0] == LEGACY_MAGIC[0] &&
                bytes[1] == LEGACY_MAGIC[1] &&
                bytes[2] == LEGACY_MAGIC[2] &&
                bytes[3] == LEGACY_MAGIC[3] -> decodeLegacyBackup(bytes)
            else -> throw BackupRestoreException("Unrecognized backup format")
        }

        postDecodeBoundsCheck(decoded)
        return decoded
    }

    /**
     * Decodes a legacy Java-serialized backup using the focused restricted decoder.
     *
     * No `ObjectInputStream` / `ObjectInputFilter` is used; the decoder parses
     * the proven historical ObjectOutputStream subset and rejects everything else
     * before any application class can be instantiated or any custom `readObject`
     * can run.
     */
    @JvmStatic
    fun decodeLegacyBackup(bytes: ByteArray): Map<*, *> {
        try {
            return LegacyBackupDecoder.decode(bytes)
        } catch (e: BackupRestoreException) {
            throw e
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            throw BackupRestoreException("Legacy decode failed", t)
        }
    }

    // Legacy reader is implemented in LegacyBackupDecoder (focused parser, no
    // ObjectInputStream / ObjectInputFilter, so no per-stream reflection is
    // required at Android runtime).

    /**
     * Post-decode bounds check for both V2 and legacy formats.
     *
     * Defense in depth: the V2 decoder and legacy focused parser already
     * enforce most bounds, but this pass guarantees the decoded in-memory map
     * also respects the final M2 constants before normalization.
     */
    @JvmStatic
    private fun postDecodeBoundsCheck(raw: Map<*, *>) {
        if (raw.size > BackupFormatV2.MAX_ENTRY_COUNT) {
            throw BackupRestoreException("Decoded entry count ${raw.size} exceeds ${BackupFormatV2.MAX_ENTRY_COUNT}")
        }
        for ((rawKey, rawValue) in raw) {
            if (rawKey !is String) {
                continue // handled by normalization, but avoid null cast
            }
            val keyBytes = rawKey.toByteArray(Charsets.UTF_8)
            if (keyBytes.size > BackupFormatV2.MAX_KEY_BYTES) {
                throw BackupRestoreException("Decoded key length ${keyBytes.size} exceeds ${BackupFormatV2.MAX_KEY_BYTES}")
            }
            when (rawValue) {
                is String -> {
                    val stringBytes = rawValue.toByteArray(Charsets.UTF_8)
                    if (stringBytes.size > BackupFormatV2.MAX_STRING_BYTES) {
                        throw BackupRestoreException("Decoded string length ${stringBytes.size} exceeds ${BackupFormatV2.MAX_STRING_BYTES}")
                    }
                }
                is Set<*> -> {
                    if (rawValue.size > BackupFormatV2.MAX_SET_ITEMS) {
                        throw BackupRestoreException("Decoded StringSet size ${rawValue.size} exceeds ${BackupFormatV2.MAX_SET_ITEMS}")
                    }
                    for (item in rawValue) {
                        if (item !is String) continue
                        val itemBytes = item.toByteArray(Charsets.UTF_8)
                        if (itemBytes.size > BackupFormatV2.MAX_STRING_BYTES) {
                            throw BackupRestoreException("Decoded set item length ${itemBytes.size} exceeds ${BackupFormatV2.MAX_STRING_BYTES}")
                        }
                    }
                }
            }
        }
    }

    /**
     * Validates and normalizes every entry of a decoded backup map.
     *
     * - Root is already known to be a `Map<*, *>`.
     * - Non-`String` keys and unsupported values are treated as **entry-level**
     *   invalid input: the entire entry is skipped and `invalidSkipped` is incremented.
     * - `StringSet` members must all be non-null `String`s; otherwise the entire
     *   `StringSet` entry is skipped.
     * - `NON_EXPORTABLE_KEYS` and explicit tombstones are counted in `deprecatedIgnored`.
     * - Unknown / stale keys are counted in `unknownIgnored` and are not written back.
     * - Legacy aliases are migrated before the current-contract prune.
     *
     * This function performs no `SharedPreferences` mutation.
     */
    @JvmStatic
    fun validateAndNormalizeEntries(
        raw: Map<*, *>,
    ): Pair<Map<String, Any?>, RestoreResult> {
        val normalized = LinkedHashMap<String, Any?>(raw.size)
        var invalidSkipped = 0
        var deprecatedIgnored = 0
        var unknownIgnored = 0

        for ((rawKey, rawValue) in raw) {
            if (rawKey !is String) {
                invalidSkipped++
                continue
            }

            val value = normalizeValue(rawValue)
            if (value == null) {
                invalidSkipped++
                continue
            }

            when (CurrentPreferenceContract.classify(rawKey)) {
                CurrentPreferenceContract.Kind.NON_EXPORTABLE,
                CurrentPreferenceContract.Kind.DROPPED -> {
                    deprecatedIgnored++
                }
                CurrentPreferenceContract.Kind.UNKNOWN -> {
                    unknownIgnored++
                }
                CurrentPreferenceContract.Kind.CURRENT,
                CurrentPreferenceContract.Kind.INTERNAL,
                CurrentPreferenceContract.Kind.LEGACY_MIGRATABLE -> {
                    normalized[rawKey] = value
                }
            }
        }

        val migrated = CurrentPreferenceContract.applyMigrations(normalized)
        val kept = LinkedHashMap<String, Any?>(normalized.size)
        for ((key, value) in normalized) {
            when (CurrentPreferenceContract.classify(key)) {
                CurrentPreferenceContract.Kind.CURRENT,
                CurrentPreferenceContract.Kind.INTERNAL -> kept[key] = value
                CurrentPreferenceContract.Kind.DROPPED,
                CurrentPreferenceContract.Kind.NON_EXPORTABLE -> deprecatedIgnored++
                CurrentPreferenceContract.Kind.UNKNOWN -> unknownIgnored++
                CurrentPreferenceContract.Kind.LEGACY_MIGRATABLE -> unknownIgnored++
            }
        }

        val counts = RestoreResult(
            status = Status.FAILURE,
            restored = kept.size,
            deprecatedIgnored = deprecatedIgnored,
            invalidSkipped = invalidSkipped,
            unknownIgnored = unknownIgnored,
            migrated = migrated,
        )
        return Pair(kept, counts)
    }

    /**
     * Validates and normalizes a single value.
     *
     * Returns `null` if the value is unsupported or a `StringSet` contains an
     * invalid member.
     */
    @JvmStatic
    private fun normalizeValue(rawValue: Any?): Any? {
        return when (rawValue) {
            is Boolean, is Int, is Long, is Float, is String -> rawValue
            is Set<*> -> {
                val set = LinkedHashSet<String>(rawValue.size)
                for (element in rawValue) {
                    if (element !is String) return null
                    set.add(element)
                }
                set
            }
            else -> null
        }
    }

    /**
     * Writes [entries] into a [SharedPreferences.Editor].
     *
     * Only supported types are written; unsupported types are silently skipped.
     */
    @JvmStatic
    fun putSupportedPreferenceEntries(
        editor: SharedPreferences.Editor,
        entries: Map<String, Any?>,
    ) {
        for ((key, value) in entries) {
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> {
                    val set = LinkedHashSet<String>(value.size)
                    @Suppress("UNCHECKED_CAST")
                    for (element in value as Set<String>) {
                        set.add(element)
                    }
                    editor.putStringSet(key, set)
                }
            }
        }
    }

    /**
     * Reconciles the launcher icon enabled state after a successful restore commit.
     *
     * @param enabled the desired enabled state; `null` falls back to enabled.
     * @return `true` if the `PackageManager` call succeeds.
     */
    @JvmStatic
    fun reconcileLauncherIcon(
        packageManager: PackageManager,
        componentName: ComponentName,
        enabled: Boolean?,
    ): Boolean {
        return try {
            val target = when (enabled) {
                null, true -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            packageManager.setComponentEnabledSetting(
                componentName,
                target,
                PackageManager.DONT_KILL_APP,
            )
            true
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            false
        }
    }

    /**
     * Performs the full M2 restore pipeline.
     *
     * - bounded input read (final M2 `MAX_FILE_SIZE`);
     * - V2 or legacy format detection;
     * - V2 decode with CRC32 and bounds;
     * - restricted legacy focused decoder (no `ObjectInputStream` / `ObjectInputFilter`);
     * - entry validation and tombstone filtering;
     * - app-selection sanitization;
     * - pre-restore snapshot capture;
     * - `SharedPreferences` clear + put + stage locale reconcile marker + commit;
     * - best-effort rollback on commit failure;
     * - launcher side-effect reconcile only after durable commit.
     */
    @JvmStatic
    fun performRestore(
        inputStream: InputStream,
        prefs: SharedPreferences,
        installedPackages: Set<String>,
        launcherReconciler: ((Boolean?) -> Boolean)? = null,
    ): RestoreResult = inputStream.use { stream ->
        val bytes = try {
            readBoundedInputStream(stream) ?: return@use RestoreResult(
                status = Status.FAILURE,
                commitSucceeded = false,
                commitConfirmedDurable = false,
            )
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            return@use RestoreResult(
                status = Status.FAILURE,
                commitSucceeded = false,
                commitConfirmedDurable = false,
            )
        }

        val rawRoot = try {
            decodeBackup(bytes)
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            return@use RestoreResult(
                status = Status.FAILURE,
                commitSucceeded = false,
                commitConfirmedDurable = false,
            )
        }

        val (entriesAfterValidation, validationCounts) = validateAndNormalizeEntries(rawRoot)
        val invalidSkipped = validationCounts.invalidSkipped
        val deprecatedIgnored = validationCounts.deprecatedIgnored
        val unknownIgnored = validationCounts.unknownIgnored
        val migrated = validationCounts.migrated
        var restored = validationCounts.restored

        val (sanitizedEntries, appSelectionsSanitized) = try {
            val sanitized = AppSelectionSanitizer.sanitizeRestoredEntries(
                entriesAfterValidation,
                installedPackages,
            )
            Pair(sanitized.entries, sanitized.changedPrimaryCount)
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            return@use RestoreResult(
                status = Status.FAILURE,
                invalidSkipped = invalidSkipped,
                deprecatedIgnored = deprecatedIgnored,
                unknownIgnored = unknownIgnored,
                migrated = migrated,
                appSelectionsSanitized = 0,
                restored = restored,
                commitSucceeded = false,
                commitConfirmedDurable = false,
            )
        }

        restored = sanitizedEntries.size

        val snapshot = capturePreRestoreSnapshot(prefs)

        val primaryEditor = prefs.edit()
        primaryEditor.clear()
        putSupportedPreferenceEntries(primaryEditor, sanitizedEntries)
        AppLocaleController.stageReconcileMarker(primaryEditor)
        primaryEditor.putInt(
            CurrentPreferenceContract.CONTRACT_REVISION_KEY,
            CurrentPreferenceContract.CONTRACT_REVISION,
        )
        val primaryCommit = primaryEditor.commit()

        if (!primaryCommit) {
            // Best-effort rollback from snapshot. The in-memory map may already have
            // changed, so we try to restore the previous durable state.
            val rollbackEditor = prefs.edit()
            rollbackEditor.clear()
            putSupportedPreferenceEntries(rollbackEditor, snapshot)
            val rollbackCommit = rollbackEditor.commit()

            return@use RestoreResult(
                status = Status.FAILURE,
                restored = restored,
                deprecatedIgnored = deprecatedIgnored,
                invalidSkipped = invalidSkipped,
                unknownIgnored = unknownIgnored,
                migrated = migrated,
                appSelectionsSanitized = appSelectionsSanitized,
                commitSucceeded = false,
                commitConfirmedDurable = false,
                deviceReconciled = false,
                rollbackAttempted = true,
                rollbackSucceeded = rollbackCommit,
            )
        }

        // Primary commit succeeded. The locale reconcile marker is already durable
        // because it was staged into the same transaction. Reconcile launcher only.
        val launcherEnabled = prefs.getBoolean("pref_key_miuizer_launchericon", true)
        val launcherReconciled = launcherReconciler?.invoke(launcherEnabled) ?: true
        val status = if (launcherReconciled) Status.SUCCESS else Status.PARTIAL_FAILURE

        RestoreResult(
            status = status,
            restored = restored,
            deprecatedIgnored = deprecatedIgnored,
            invalidSkipped = invalidSkipped,
            unknownIgnored = unknownIgnored,
            migrated = migrated,
            appSelectionsSanitized = appSelectionsSanitized,
            commitSucceeded = true,
            commitConfirmedDurable = true,
            deviceReconciled = launcherReconciled,
            rollbackAttempted = false,
            rollbackSucceeded = false,
        )
    }

    /**
     * Performs the full M2 restore pipeline using a `PackageManager`.
     *
     * Package query and launcher reconcile are performed outside the destructive
     * transaction. This is the production overload used by [PreferenceFragmentBase].
     */
    @JvmStatic
    fun performRestore(
        inputStream: InputStream,
        packageManager: PackageManager,
        prefs: SharedPreferences,
        componentName: ComponentName? = null,
    ): RestoreResult {
        val installedPackages = try {
            AppSelectionSanitizer.queryInstalledPackageNames(packageManager)
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            return RestoreResult(
                status = Status.FAILURE,
                commitSucceeded = false,
                commitConfirmedDurable = false,
            )
        }

        val reconciler: ((Boolean?) -> Boolean)? = if (componentName != null) { enabled ->
            reconcileLauncherIcon(packageManager, componentName, enabled)
        } else null

        return performRestore(inputStream, prefs, installedPackages, reconciler)
    }

    class BackupRestoreException(message: String, cause: Throwable? = null) : Exception(message, cause)
}
