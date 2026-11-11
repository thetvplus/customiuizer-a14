package tv.withaibuild.customiuizer.mods.volume

import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import kotlin.math.abs

/** Same full-screen native dialog and input tree. No new Window, timer or frame request.
 * MIUI's finite motion still owns show/dismiss and alpha; its side translation is
 * reconciled before drawing/Insets while this central presentation is attached. */
internal class ClassicVolumePlacement(private val root: View, private val track: View,
    private val offsetPercent: Int = 0, private val footer: View? = null) : ViewTreeObserver.OnPreDrawListener {
    private val location = IntArray(2)
    private var nativeX = root.translationX
    private var nativeY = root.translationY
    private var appliedX = Float.NaN
    private var appliedY = Float.NaN
    private var closed = false
    init { root.viewTreeObserver.addOnPreDrawListener(this) }
    fun update() {
        if (closed || track.width <= 0 || track.height <= 0) return
        val window = root.rootView
        if (!track.isAttachedToWindow || !root.isAttachedToWindow || track.rootView !== window) return
        if (window.width <= 0 || window.height <= 0) return
        // Native show/dismiss can advance underneath the central presentation.
        // Expansion must resume its latest pose, not the old attach-time seed.
        if (root.translationX != appliedX) nativeX = root.translationX
        if (root.translationY != appliedY) nativeY = root.translationY
        track.getLocationInWindow(location)
        val trackX = location[0]
        val trackY = location[1]
        val insets = root.rootWindowInsets?.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val left = (insets?.left ?: 0).toFloat()
        val right = (window.width - (insets?.right ?: 0)).coerceAtLeast(left.toInt()).toFloat()
        val top = (insets?.top ?: 0).toFloat()
        val bottom = (window.height - (insets?.bottom ?: 0)).coerceAtLeast(top.toInt()).toFloat()
        var footerHeight = 0f
        if (footer?.isShown == true && footer.rootView === window) {
            footer.getLocationInWindow(location)
            footerHeight = (location[1] + footer.height - trackY - track.height).coerceAtLeast(0).toFloat()
        }
        val dx = (left + right) * .5f - trackX - track.width * .5f
        val dy = verticalCenter(top, bottom, minOf(track.width.toFloat(), right - left),
            track.height.toFloat(), footerHeight, offsetPercent) - trackY - track.height * .5f
        // getLocationInWindow rounds to integer pixels. Do not turn a half-pixel
        // measurement residual into alternating RenderNode invalidations.
        if (abs(dx) > .5f) { root.translationX += dx; appliedX = root.translationX }
        if (abs(dy) > .5f) { root.translationY += dy; appliedY = root.translationY }
    }
    companion object {
        // A percentage of the actual HUD width stays fine-grained across densities.
        // The one native expanded-controls footer shares the same safe-area limit.
        internal fun verticalCenter(top: Float, bottom: Float, width: Float, height: Float,
            footerHeight: Float, offsetPercent: Int): Float {
            val center = (top + bottom) * .5f
            val minimum = top + height * .5f
            val maximum = bottom - height * .5f - footerHeight
            if (maximum < minimum) return center
            return (center + width.coerceAtLeast(0f) * offsetPercent.coerceIn(-100, 100) / 100f)
                .coerceIn(minimum, maximum)
        }
    }
    override fun onPreDraw(): Boolean { update(); return true }
    fun close() {
        if (closed) return
        closed = true
        if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnPreDrawListener(this)
        if (root.translationX == appliedX) root.translationX = nativeX
        if (root.translationY == appliedY) root.translationY = nativeY
    }
}
