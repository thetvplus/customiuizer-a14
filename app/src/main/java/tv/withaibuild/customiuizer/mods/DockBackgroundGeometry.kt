package tv.withaibuild.customiuizer.mods

import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.floor

/** Layout coordinates only: never move native icons or loosen an ancestor's clipping. */
internal object DockBackgroundGeometry {
    fun fit(icons: Rect, safe: Rect, sideInset: Int, adjustment: Float, out: Rect) {
        if (icons.isEmpty || safe.isEmpty) { out.setEmpty(); return }
        val cx = icons.exactCenterX()
        val cy = icons.exactCenterY()
        val roomY = minOf(cy - safe.top, safe.bottom - cy)
        val padding = minOf((icons.height() * 0.2f).toInt(),
            (roomY - icons.height() * 0.5f).toInt().coerceAtLeast(0))
        val halfWidth = if (sideInset > 0) safe.width() * 0.5f - sideInset
            else icons.width() * 0.5f + padding
        val halfHeight = icons.height() * 0.5f + padding
        centered(cx, cy, halfWidth, halfHeight, safe, adjustment, out)
    }

    fun constrain(rect: Rect, safe: Rect, out: Rect) {
        if (rect.isEmpty) { out.setEmpty(); return }
        centered(rect.exactCenterX(), rect.exactCenterY(), rect.width() * 0.5f,
            rect.height() * 0.5f, safe, 0f, out)
    }

    private fun centered(centerX: Float, centerY: Float, requestedX: Float, requestedY: Float,
        safe: Rect, adjustment: Float, out: Rect,
    ) {
        if (safe.isEmpty) { out.setEmpty(); return }
        // A native icon center outside the visible/safe area has no centered solution. Only in
        // that case move to the nearest drawable pixel. Otherwise the center never moves.
        val cx = centerX.coerceIn(safe.left + 0.5f, safe.right - 0.5f)
        val cy = centerY.coerceIn(safe.top + 0.5f, safe.bottom - 0.5f)
        val minX = if (cx % 1f == 0f) 1f else 0.5f
        val minY = if (cy % 1f == 0f) 1f else 0.5f
        val roomX = minOf(cx - safe.left, safe.right - cx)
        val roomY = minOf(cy - safe.top, safe.bottom - cy)
        val baseX = cx - ceil(cx - requestedX.coerceIn(minX, roomX))
        val baseY = cy - ceil(cy - requestedY.coerceIn(minY, roomY))
        // ONE delta for all four edges. A navigation/host limit restricts the whole expansion,
        // rather than pinning one edge and moving the center. Contraction may cover icons.
        val limited = adjustment.coerceIn(maxOf(minX - baseX, minY - baseY),
            minOf(roomX - baseX, roomY - baseY))
        val delta = if (limited >= 0f) floor(limited) else ceil(limited)
        out.set(ceil(cx - baseX - delta).toInt(), ceil(cy - baseY - delta).toInt(),
            floor(cx + baseX + delta).toInt(), floor(cy + baseY + delta).toInt())
    }
}
