package tv.withaibuild.customiuizer.mods.island

import kotlin.math.abs

/** Physical safe edges are anchors. A side never borrows the camera's central slot. */
internal object IslandPosition {
    data class Layout(val left: Int, val top: Int, val width: Int, val gap: Int)

    fun layout(width: Int, height: Int, minimumWidth: Int, safeLeft: Int, safeRight: Int,
        alignment: Int, offset: Int, top: Int, gap: Int, holeLeft: Int = 0,
        holeRight: Int = 0, holeTop: Int = 0, holeBottom: Int = 0, cameraMargin: Int = 0): Layout? {
        val available = safeRight - safeLeft
        if (available < minimumWidth || height <= 0 || width <= 0) return null
        var w = minOf(width, available)
        val overlaps = holeRight > holeLeft && top < holeBottom && top + height > holeTop
        if (alignment == 1) {
            val x = left(w, safeLeft, safeRight, alignment, offset, gap,
                holeLeft, holeRight, overlaps) ?: return null
            return Layout(x, top, w, gap)
        }
        // Offset is applied to the selected edge before width is constrained. Narrowing
        // a right island must keep its right edge, just as narrowing a left keeps its left.
        val edge = ((if (alignment == 0) safeLeft else safeRight).toLong() + offset)
            .coerceIn(safeLeft.toLong(), safeRight.toLong()).toInt()
        val room = if (alignment == 0) safeRight - edge else edge - safeLeft
        w = minOf(w, room)
        if (w < minimumWidth) return null
        var y = top
        if (overlaps) {
            val beside = if (alignment == 0) holeLeft - cameraMargin - edge
                else edge - holeRight - cameraMargin
            if (beside >= minimumWidth) w = minOf(w, beside)
            else if (if (alignment == 0) edge < holeRight else edge > holeLeft) {
                // Preserve the edge, even for a corner cutout or an inward offset.
                y = maxOf(top, holeBottom + cameraMargin)
            }
        }
        return Layout(if (alignment == 0) edge else edge - w, y, w, 0)
    }

    fun left(width: Int, safeLeft: Int, safeRight: Int, alignment: Int, offset: Int,
        gap: Int, holeLeft: Int = 0, holeRight: Int = 0, overlapsHole: Boolean = false): Int? {
        if (width <= 0 || safeRight - safeLeft < width) return null
        val maximum = safeRight - width
        val center = if (holeRight > holeLeft) (holeLeft + holeRight - width) / 2
            else safeLeft + (safeRight - safeLeft - width) / 2
        val desired = ((when (alignment) { 0 -> safeLeft; 2 -> maximum; else -> center }).toLong() + offset)
            .coerceIn(safeLeft.toLong(), maximum.toLong()).toInt()
        fun safe(x: Int): Boolean = x in safeLeft..maximum && (!overlapsHole || holeRight <= holeLeft ||
            x + width <= holeLeft || x >= holeRight || alignment == 1 &&
            holeLeft >= x + (width - gap) / 2 && holeRight <= x + (width + gap) / 2)
        if (safe(desired)) return desired
        if (alignment != 1) return null // Caller narrows or moves vertically; never recenter a side.
        return intArrayOf(holeLeft - width, holeRight, center)
            .filter(::safe).minByOrNull { abs(it - desired) }
    }
}
