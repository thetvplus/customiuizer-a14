package tv.withaibuild.customiuizer.mods.utils

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger
import tv.withaibuild.customiuizer.BuildConfig

/**
 * Process-scoped, in-memory collector for Hook installation diagnostics.
 *
 * This object lives in the module's class loader inside the host process. It
 * only records cold-path install events (class lookup, hook installation,
 * constructor/method batch hooks, DexKit queries, RemotePreferences load).
 * It does **not** record inside Hook callbacks, draw loops, or other hot paths.
 *
 * Records are keyed by a stable tuple so the same missing target is only
 * counted once. The store is bounded; if it grows beyond [detailBound] entries
 * are dropped by arbitrary eviction (the cheapest safe operation on a
 * [ConcurrentHashMap]). Release builds keep successful installs as counters
 * and retain a smaller failure-detail bound. Records contain only strings and
 * enums — no Context, ClassLoader, MethodHook, Throwable, or user data is retained.
 */
object HookDiagnostics {

    enum class Status {
        INSTALLED,
        TARGET_CLASS_MISSING,
        TARGET_MEMBER_MISSING,
        INSTALL_FAILED,
        SILENTLY_SKIPPED,
        DEXKIT_FAILED,
        DEXKIT_NO_MATCH,
        PREFERENCES_UNAVAILABLE,
        RECEIVER_UNREGISTER_FAILED,
        RECEIVER_STALE_DROPPED,
        DUPLICATE_FEATURE,
    }

    enum class Kind {
        METHOD,
        CONSTRUCTOR,
        ALL_METHODS,
        ALL_CONSTRUCTORS,
        DEXKIT_QUERY,
        REMOTE_PREFERENCES,
        RECEIVER,
        FEATURE,
    }

    data class Record(
        val process: String,
        val kind: Kind,
        val targetClass: String,
        val targetMember: String,
        val descriptor: String,
        val status: Status,
        val exceptionType: String,
    ) {
        internal val key: String
            get() = "$process|$kind|$targetClass|$targetMember|$descriptor|$status"
    }

    const val VERBOSE_DETAIL_BOUND = 256
    const val RELEASE_DETAIL_BOUND = 32

    private val records = ConcurrentHashMap<String, Record>()
    private val printedStages = CopyOnWriteArraySet<String>()
    private val compactInstalled = AtomicInteger()
    private val compactSilent = AtomicInteger()

    /**
     * The real process name set by [MainModule] from [XC_LoadPackage.LoadPackageParam.getProcessName].
     * Never the package name: a package can have multiple processes (e.g. `com.miui.securitycenter:ui`).
     */
    @JvmField
    var currentProcessName: String? = null

    /**
     * Record an installation result. Duplicate keys overwrite silently,
     * so repeated failures of the same target do not inflate the map.
     */
    @JvmStatic
    fun isVerboseDiagnostics(): Boolean = BuildConfig.BUILD_TYPE != "release"

    @JvmStatic
    fun detailBound(verbose: Boolean = isVerboseDiagnostics()): Int =
        if (verbose) VERBOSE_DETAIL_BOUND else RELEASE_DETAIL_BOUND

    @JvmStatic
    fun shouldRetainDetail(status: Status, verbose: Boolean = isVerboseDiagnostics()): Boolean {
        if (verbose) return true
        return status != Status.INSTALLED && status != Status.SILENTLY_SKIPPED
    }

    @JvmStatic
    fun record(record: Record) {
        if (!shouldRetainDetail(record.status)) {
            if (record.status == Status.INSTALLED) {
                compactInstalled.incrementAndGet()
            } else {
                compactSilent.incrementAndGet()
            }
            return
        }
        records[record.key] = record
        val bound = detailBound()
        if (records.size > bound) {
            // Drop entries to avoid unbounded growth on a pathological ROM.
            // ConcurrentHashMap does not preserve insertion order, so this is
            // arbitrary eviction, not FIFO.
            val toDrop = records.size - bound
            val keys = records.keys.take(toDrop)
            keys.forEach { records.remove(it) }
        }
    }

    /**
     * Return a snapshot of the current records. Safe to call from tests.
     */
    @JvmStatic
    fun snapshot(): List<Record> = records.values.toList()

    /**
     * Return the summary counts. Does not allocate per successful hook.
     */
    @JvmStatic
    fun summary(): Map<Status, Int> {
        val counts = Status.entries.associateWith { 0 }.toMutableMap()
        for (record in records.values) {
            counts[record.status] = (counts[record.status] ?: 0) + 1
        }
        if (!isVerboseDiagnostics()) {
            counts[Status.INSTALLED] = (counts[Status.INSTALLED] ?: 0) + compactInstalled.get()
            counts[Status.SILENTLY_SKIPPED] = (counts[Status.SILENTLY_SKIPPED] ?: 0) + compactSilent.get()
        }
        return counts
    }

    /**
     * Print a one-line summary for the given [stage]. Each stage is only printed
     * once per process unless [reset] is called. Stages keep the final summary
     * from being frozen before delayed hooks finish. Uses the existing LSPosed log path.
     */
    @JvmStatic
    @JvmOverloads
    fun printSummaryForStage(stage: String, prefix: String = "CustoMIUIzer") {
        if (!printedStages.add(stage)) return
        val s = summary()
        val process = currentProcessName ?: android.os.Process.myPid().toString()
        val installed = s[Status.INSTALLED] ?: 0
        val missingClass = s[Status.TARGET_CLASS_MISSING] ?: 0
        val missingMember = s[Status.TARGET_MEMBER_MISSING] ?: 0
        val failed = s[Status.INSTALL_FAILED] ?: 0
        val silent = s[Status.SILENTLY_SKIPPED] ?: 0
        val dexkit = s[Status.DEXKIT_FAILED] ?: 0
        val dexkitNoMatch = s[Status.DEXKIT_NO_MATCH] ?: 0
        val prefs = s[Status.PREFERENCES_UNAVAILABLE] ?: 0
        XposedHelpers.log(
            "$prefix HookSummary stage=$stage process=$process " +
                "installed=$installed " +
                "classMissing=$missingClass " +
                "memberMissing=$missingMember " +
                "failed=$failed " +
                "silentSkipped=$silent " +
                "dexkitFailed=$dexkit " +
                "dexkitNoMatch=$dexkitNoMatch " +
                "prefsUnavailable=$prefs"
        )
    }

    /**
     * Legacy single-stage summary. Equivalent to [printSummaryForStage] with
     * stage `"default"`.
     */
    @JvmStatic
    @JvmOverloads
    fun printSummaryOnce(prefix: String = "CustoMIUIzer") = printSummaryForStage("default", prefix)

    /**
     * Reset the collector. Used when the module is reloaded in the same process.
     */
    @JvmStatic
    fun reset() {
        records.clear()
        printedStages.clear()
        compactInstalled.set(0)
        compactSilent.set(0)
    }

    /**
     * Helper for the common case of recording a single installation outcome.
     */
    @JvmStatic
    @JvmOverloads
    fun record(
        process: String,
        kind: Kind,
        targetClass: String,
        targetMember: String,
        descriptor: String = "",
        status: Status,
        exceptionType: String = "",
    ) {
        record(
            Record(
                process = process,
                kind = kind,
                targetClass = targetClass,
                targetMember = targetMember,
                descriptor = descriptor,
                status = status,
                exceptionType = exceptionType,
            )
        )
    }

    /**
     * Record a RemotePreferences failure once for this process.
     */
    @JvmStatic
    @JvmOverloads
    fun recordPreferencesUnavailable(
        exceptionType: String = "",
        detail: String = "",
    ) {
        record(
            process = currentProcessName ?: android.os.Process.myPid().toString(),
            kind = Kind.REMOTE_PREFERENCES,
            targetClass = "io.github.libxposed.service.RemotePreferences",
            targetMember = detail.ifEmpty { "load/register" },
            descriptor = "",
            status = Status.PREFERENCES_UNAVAILABLE,
            exceptionType = exceptionType,
        )
    }

    /**
     * Record an empty-but-unconfirmed RemotePreferences state once for this process.
     */
    @JvmStatic
    fun recordPreferencesEmptyPending() {
        record(
            Record(
                process = currentProcessName ?: android.os.Process.myPid().toString(),
                kind = Kind.REMOTE_PREFERENCES,
                targetClass = "io.github.libxposed.service.RemotePreferences",
                targetMember = "getAll",
                descriptor = "empty-pending",
                status = Status.PREFERENCES_UNAVAILABLE,
                exceptionType = "",
            )
        )
    }

    /**
     * Record that a package's hooks may have missed the real preference snapshot because
     * the RemotePreferences state was not LOADED or VALID_EMPTY when the package was ready.
     */
    @JvmStatic
    fun recordPreferencesMissed(packageName: String, state: String) {
        record(
            Record(
                process = currentProcessName ?: android.os.Process.myPid().toString(),
                kind = Kind.REMOTE_PREFERENCES,
                targetClass = packageName,
                targetMember = "onPackageReady",
                descriptor = state,
                status = Status.PREFERENCES_UNAVAILABLE,
                exceptionType = "",
            )
        )
    }

    /**
     * Record a DexKit query result. [noMatch] means the bridge worked but the
     * query returned nothing, which is a different failure from bridge/exception
     * failures.
     */
    @JvmStatic
    @JvmOverloads
    fun recordDexKit(
        targetClass: String,
        targetMember: String,
        exceptionType: String = "",
        noMatch: Boolean = false,
    ) {
        record(
            process = currentProcessName ?: android.os.Process.myPid().toString(),
            kind = Kind.DEXKIT_QUERY,
            targetClass = targetClass,
            targetMember = targetMember,
            descriptor = "",
            status = if (exceptionType.isNotEmpty()) Status.DEXKIT_FAILED
                else if (noMatch) Status.DEXKIT_NO_MATCH
                else Status.INSTALLED,
            exceptionType = exceptionType,
        )
    }

    /**
     * Determine whether a thrown exception indicates the target class was not
     * found. This is best-effort; when in doubt, classify as INSTALL_FAILED.
     */
    @JvmStatic
    fun isClassMissingException(t: Throwable): Boolean {
        val name = t.javaClass.name
        return name.contains("ClassNotFound", ignoreCase = true) ||
            t.message?.contains("Class", ignoreCase = true) == true && t.message?.contains("not found", ignoreCase = true) == true
    }

    /**
     * Determine whether a thrown exception indicates the target member was not
     * found. Best-effort; any NoSuchMethod/Field constructor counts.
     */
    @JvmStatic
    fun isMemberMissingException(t: Throwable): Boolean {
        val name = t.javaClass.name
        return name.contains("NoSuchMethod", ignoreCase = true) ||
            name.contains("NoSuchField", ignoreCase = true) ||
            t.message?.contains("No method", ignoreCase = true) == true ||
            t.message?.contains("No constructor", ignoreCase = true) == true
    }
}
