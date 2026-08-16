package tv.withaibuild.customiuizer.mods.utils

import android.os.Debug
import tv.withaibuild.customiuizer.BuildConfig

/**
 * Develop-only measurements for the cold Feature catalog and registry setup path.
 *
 * The release build sees a constant-false build-type guard and R8 removes the sampling and log
 * calls. Measurements never run from installed Hook callbacks or other hot paths.
 */
internal object FeatureInstallMetrics {

    internal const val UNKNOWN = -1L
    private const val ALLOCATED_BYTES_STAT = "art.gc.bytes-allocated"

    internal fun nowNanos(): Long =
        if (BuildConfig.BUILD_TYPE == "develop") System.nanoTime() else 0L

    internal fun allocatedBytes(): Long {
        if (BuildConfig.BUILD_TYPE != "develop") return UNKNOWN
        return try {
            Debug.getRuntimeStat(ALLOCATED_BYTES_STAT)?.toLongOrNull() ?: UNKNOWN
        } catch (_: Exception) {
            UNKNOWN
        }
    }

    internal fun elapsedMicros(startNanos: Long, endNanos: Long): Long {
        if (startNanos <= 0L || endNanos < startNanos) return UNKNOWN
        return (endNanos - startNanos) / 1_000L
    }

    internal fun allocationDelta(startBytes: Long, endBytes: Long): Long {
        if (startBytes < 0L || endBytes < startBytes) return UNKNOWN
        return endBytes - startBytes
    }

    internal fun recordCatalog(
        label: String,
        specCount: Int,
        catalogStartNanos: Long,
        catalogEndNanos: Long,
        catalogStartBytes: Long,
        catalogEndBytes: Long,
        registerStartNanos: Long,
        registerEndNanos: Long,
        registerStartBytes: Long,
        registerEndBytes: Long,
    ) {
        if (BuildConfig.BUILD_TYPE != "develop") return
        val process = HookDiagnostics.currentProcessName ?: android.os.Process.myPid().toString()
        XposedHelpers.log(
            "CustoMIUIzer FeaturePerf label=$label process=$process specs=$specCount " +
                "catalogUs=${elapsedMicros(catalogStartNanos, catalogEndNanos)} " +
                "catalogBytes=${allocationDelta(catalogStartBytes, catalogEndBytes)} " +
                "registerUs=${elapsedMicros(registerStartNanos, registerEndNanos)} " +
                "registerBytes=${allocationDelta(registerStartBytes, registerEndBytes)}"
        )
    }
}
