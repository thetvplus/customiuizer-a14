package tv.withaibuild.customiuizer.mods.volume

import android.view.View
import android.view.ViewTreeObserver
import kotlin.math.abs

/** Same full-screen native dialog and input tree. No new Window, timer or frame request.
 * MIUI's finite motion still owns show/dismiss and alpha; its side translation is
 * reconciled before drawing/Insets while this central presentation is attached. */
internal class ClassicVolumePlacement(private val root: View, private val track: View) : ViewTreeObserver.OnPreDrawListener {
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
        val dx = window.width * .5f - location[0] - track.width * .5f
        val dy = window.height * .5f - location[1] - track.height * .5f
        // getLocationInWindow rounds to integer pixels. Do not turn a half-pixel
        // measurement residual into alternating RenderNode invalidations.
        if (abs(dx) > .5f) { root.translationX += dx; appliedX = root.translationX }
        if (abs(dy) > .5f) { root.translationY += dy; appliedY = root.translationY }
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
