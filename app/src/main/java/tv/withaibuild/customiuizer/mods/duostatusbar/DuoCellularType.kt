package tv.withaibuild.customiuizer.mods.duostatusbar

import java.lang.reflect.Field
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** Optional text metadata. Native display names include the ROM's NSA/SA and carrier decisions. */
internal class DuoCellularType private constructor(private val name: Field) {
    @Volatile private var failed = false

    fun read(state: Any): Int {
        if (failed) return 0
        return try { generation(name.get(state) as String?) } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            failed = true
            XposedHelpers.log("Duo cellular text disabled: ${t.message}")
            0
        }
    }

    companion object {
        fun resolve(abi: DuoAbi): DuoCellularType? = try {
            DuoCellularType(abi.field(abi.cls("statusbar.SignalIcon\$MobileState"), "showName", String::class.java))
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("Duo cellular text unavailable: ${t.message}")
            null
        }

        /** Fixed labels, no formatting/allocation or guesses for unknown carrier names. */
        fun generation(name: String?): Int = when (name) {
            "5G", "5G+", "5G UW", "5G UWB" -> 5
            "4G", "4G+", "LTE", "LTE+", "LTE-A", "4G LTE", "5GE", "5Ge", "5G E" -> 4
            "3G", "3G+", "H", "H+", "HSPA", "HSPA+" -> 3
            "2G", "G", "E", "1X", "1x", "GPRS", "EDGE" -> 2
            else -> 0
        }

        fun label(generation: Int): String = when (generation) {
            2 -> "2G"
            3 -> "3G"
            4 -> "4G"
            5 -> "5G"
            else -> ""
        }
    }
}
