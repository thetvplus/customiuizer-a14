package tv.withaibuild.customiuizer.mods.recents

import android.graphics.Outline
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import kotlin.math.roundToInt

/** The TaskView RenderNode projects the native thumbnail silhouette. */
internal class DeckCardShadow(
    private val view: View,
    val content: View
) : ViewOutlineProvider() {
    private val original = view.outlineProvider
    private val bounds = Rect()
    // Offset only the projected silhouette to compensate for HWUI's light.
    private val leftShift = view.resources.displayMetrics.density.roundToInt()
    private val upShift = (2f * view.resources.displayMetrics.density).roundToInt()
    private var opacity = 0f
    private var corner = 0
    var owned = (view.parent as? ViewGroup)?.clipChildren == false &&
        !view.clipToOutline && content.parent === view
        private set

    init { if (owned) view.outlineProvider = this }

    fun sync(fullscreenProgress: Float, radius: Int, header: Int, entryProgress: Float = 0f) {
        if (!owned) return
        if (view.outlineProvider !== this || view.clipToOutline) {
            close()
            return
        }
        val left = maxOf(0, content.left)
        // The native match-parent thumbnail translates its Canvas by the
        // same layout header before drawing the rounded screenshot.
        val top = maxOf(0, content.top + header)
        val right = minOf(view.width, content.right)
        val bottom = minOf(view.height, content.bottom)
        val visibleOpacity = if (left < right && top < bottom && view.visibility == View.VISIBLE &&
            view.alpha > 0f && content.parent === view && content.visibility == View.VISIBLE &&
            content.width > 0 && content.height > 0
        ) {
            val thumbnailOpacity = if (fullscreenProgress.isFinite() && content.alpha.isFinite())
                content.alpha.coerceIn(0f, 1f) * (1f - fullscreenProgress.coerceIn(0f, 1f)) else 0f
            maxOf(thumbnailOpacity, entryProgress)
        } else 0f
        if (visibleOpacity == 0f) {
            if (opacity != 0f) {
                opacity = 0f
                view.invalidateOutline()
            }
            return
        }
        if (opacity == visibleOpacity && bounds.left == left && bounds.top == top &&
            bounds.right == right && bounds.bottom == bottom && corner == radius) return
        opacity = visibleOpacity
        bounds.set(left, top, right, bottom)
        corner = radius
        view.invalidateOutline()
    }

    override fun getOutline(view: View, outline: Outline) {
        if (view.clipToOutline) {
            original?.getOutline(view, outline)
            return
        }
        if (opacity == 0f) outline.setEmpty()
        else outline.setRoundRect(bounds.left - leftShift, bounds.top - upShift,
            bounds.right - leftShift, bounds.bottom - upShift, corner.coerceAtLeast(0).toFloat())
        outline.alpha = opacity
    }

    fun close() {
        if (!owned) return
        owned = false
        if (view.outlineProvider === this) view.outlineProvider = original
    }
}
