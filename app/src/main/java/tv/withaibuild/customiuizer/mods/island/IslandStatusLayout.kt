package tv.withaibuild.customiuizer.mods.island

import android.text.TextPaint
import android.text.StaticLayout
import android.text.Layout
import android.text.TextDirectionHeuristics
import android.graphics.text.LineBreaker

/** A complete system status stays on one row before width or wording changes. */
internal object IslandStatusLayout {
    data class Fit(val size: Float, val height: Int, val lines: Int = 1)

    fun fit(text: String, source: TextPaint, room: Int, initial: Float, minimum: Float,
        maximumHeight: Int): Fit? {
        if (room <= 1 || maximumHeight <= 0 || text.any { it == '\n' || it == '\r' }) return null
        val paint = TextPaint(source)
        var size = initial
        val step = (initial - minimum).coerceAtLeast(1f) / 4f
        while (true) {
            paint.textSize = size
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, room - 1)
                .setIncludePad(false).setMaxLines(1)
                .setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR).build()
            if (layout.lineCount == 1 && layout.height <= maximumHeight &&
                layout.getLineEnd(0) == text.length && layout.getLineWidth(0) <= room - 1f) {
                return Fit(size, layout.height)
            }
            if (size <= minimum) return null
            size = maxOf(minimum, size - step)
        }
    }
}
