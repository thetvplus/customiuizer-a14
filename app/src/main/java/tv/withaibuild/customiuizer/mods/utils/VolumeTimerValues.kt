package tv.withaibuild.customiuizer.mods.utils

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.icu.util.ULocale
import java.util.Locale

internal object VolumeTimerValues {
    // Seconds, shared by resource replacement and labels; callers own their array.
    fun segments(): IntArray =
        intArrayOf(0, 1800, 3600, 7200, 10800, 14400, 18000, 21600, 28800, 36000, 43200)

    fun labels(locale: Locale?, offLabel: String): Array<String> {
        val segments = segments()
        // Region-only zh-TW must resolve to Hant before ICU's resource fallback.
        val format = if (locale == null) null else MeasureFormat.getInstance(
            ULocale.addLikelySubtags(ULocale.forLocale(locale)), MeasureFormat.FormatWidth.WIDE,
        )
        return Array(segments.size) { index ->
            val seconds = segments[index]
            when {
                seconds == 0 -> offLabel
                seconds % 3600 == 0 -> if (format == null) "${seconds / 3600} h"
                    else format.format(Measure(seconds / 3600, MeasureUnit.HOUR))
                else -> if (format == null) "${seconds / 60} min"
                    else format.format(Measure(seconds / 60, MeasureUnit.MINUTE))
            }
        }
    }
}
