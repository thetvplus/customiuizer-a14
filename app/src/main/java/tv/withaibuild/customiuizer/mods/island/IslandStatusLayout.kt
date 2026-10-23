package tv.withaibuild.customiuizer.mods.island

import android.text.TextPaint
import kotlin.math.ceil

/** One complete status line; failure keeps the native presentation rather than clipping meaning. */
internal object IslandStatusLayout {
    data class Fit(val size: Float, val height: Int)

    fun fit(text: String, source: TextPaint, room: Int, initial: Float, minimum: Float,
        maximumHeight: Int): Fit? {
        if (room <= 1 || maximumHeight <= 0 || text.any { it == '\n' || it == '\r' }) return null
        val paint = TextPaint(source)
        var size = initial
        val step = (initial - minimum).coerceAtLeast(1f) / 4f
        while (true) {
            paint.textSize = size
            val metrics = paint.fontMetrics
            val height = ceil(metrics.descent - metrics.ascent).toInt()
            if (paint.measureText(text) <= room - 1 && height <= maximumHeight) return Fit(size, height)
            if (size <= minimum) return null
            size = maxOf(minimum, size - step)
        }
    }
}
