package tv.withaibuild.customiuizer.mods.island

import kotlin.math.abs

/** Physical screen coordinates. A cutout may occupy only the island's empty center. */
internal object IslandPosition {
    fun left(width: Int, safeLeft: Int, safeRight: Int, alignment: Int, offset: Int,
        gap: Int, holeLeft: Int = 0, holeRight: Int = 0, overlapsHole: Boolean = false): Int? {
        if (width <= 0 || safeRight - safeLeft < width) return null
        val maximum = safeRight - width
        val desired = ((when (alignment) {
            0 -> safeLeft
            2 -> maximum
            else -> safeLeft + (safeRight - safeLeft - width) / 2
        }).toLong() + offset).coerceIn(safeLeft.toLong(), maximum.toLong()).toInt()
        fun safe(x: Int): Boolean = x in safeLeft..maximum && (!overlapsHole || holeRight <= holeLeft ||
            x + width <= holeLeft || x >= holeRight ||
            holeLeft >= x + (width - gap) / 2 && holeRight <= x + (width + gap) / 2)
        if (safe(desired)) return desired
        return intArrayOf(holeLeft - width, holeRight, (holeLeft + holeRight - width) / 2)
            .filter(::safe).minByOrNull { abs(it - desired) }
    }
}
