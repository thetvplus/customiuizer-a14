package tv.withaibuild.customiuizer.mods.utils

/**
 * Hit-region math for Dynamic Island recall. Visual capsule geometry is unchanged;
 * the parent may attach a [android.view.TouchDelegate] using [delegateRect].
 *
 * Returns a plain int box so unit tests do not depend on the stub
 * `android.graphics.Rect` constructor.
 */
object IslandRecallHit {

    data class Box(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val isEmpty: Boolean get() = right <= left || bottom <= top
        val width: Int get() = (right - left).coerceAtLeast(0)
        val height: Int get() = (bottom - top).coerceAtLeast(0)
    }

    /**
     * Extra touch margin around the capsule. Two scaled touch slops, never below 12 dp
     * and never a full status-bar band.
     */
    @JvmStatic
    fun extraMarginPx(density: Float, scaledTouchSlopPx: Int): Int {
        val floor = (12f * density.coerceAtLeast(1f) + 0.5f).toInt()
        val fromSlop = (scaledTouchSlopPx * 2).coerceAtLeast(0)
        val cap = (36f * density.coerceAtLeast(1f) + 0.5f).toInt()
        return fromSlop.coerceAtLeast(floor).coerceAtMost(cap)
    }

    /**
     * Capsule bounds expanded by [marginPx], clamped to the parent. The result stays
     * around the island; it cannot become the full parent unless the capsule already is.
     */
    @JvmStatic
    fun delegateRect(
        parentWidth: Int,
        parentHeight: Int,
        childLeft: Int,
        childTop: Int,
        childWidth: Int,
        childHeight: Int,
        marginPx: Int,
    ): Box {
        if (parentWidth <= 0 || parentHeight <= 0 || childWidth <= 0 || childHeight <= 0) {
            return Box(0, 0, 0, 0)
        }
        val margin = marginPx.coerceAtLeast(0)
        val left = (childLeft - margin).coerceAtLeast(0)
        val top = (childTop - margin).coerceAtLeast(0)
        val right = (childLeft + childWidth + margin).coerceAtMost(parentWidth)
        val bottom = (childTop + childHeight + margin).coerceAtMost(parentHeight)
        if (right <= left || bottom <= top) return Box(0, 0, 0, 0)
        return Box(left, top, right, bottom)
    }
}
