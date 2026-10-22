package tv.withaibuild.customiuizer.mods

import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewTreeObserver
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import kotlin.math.ceil
import kotlin.math.floor

/** One window/event lease. Preparing a window never reserves status-bar space. No frame scheduling. */
internal class IslandWindowPresentation(
    private val root: View,
    private val target: () -> View = { root },
    private val eligible: () -> Boolean,
    private val shown: (Any) -> Unit = {},
    private val hidden: (Any) -> Unit = {},
) : ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
    enum class Phase { IDLE, PREPARED, VISIBLE, EXITING }
    var phase = Phase.IDLE
        private set
    private var identity: Any? = null
    private var visible: Any? = null
    private var width = 0
    private var observer: ViewTreeObserver? = null
    private var listening = false
    private val bounds = Rect()
    private val screenPosition = IntArray(2)
    private val transformedBounds = RectF()
    private val transformedOrigin = FloatArray(2)

    fun prepare(event: Any, compactWidth: Int) {
        if (identity !== event) {
            release()
            identity = event
            phase = Phase.PREPARED
        }
        width = compactWidth.coerceAtLeast(0)
        if (!listening) { root.addOnAttachStateChangeListener(this); listening = true }
        observe()
        // Even an attached window waits for pre-draw after its final layout.
    }

    fun exiting() { if (identity != null) phase = Phase.EXITING }

    fun rebind() { release(); refresh() }

    fun refresh() {
        try { refreshFrame() }
        catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            cancel()
            XposedHelpers.log("Dynamic Island presentation", error)
        }
    }

    private fun refreshFrame() {
        val event = identity ?: return
        val view = target()
        var parent: View? = view
        var opaque = true
        while (parent != null) {
            if (parent.alpha <= .01f) { opaque = false; break }
            parent = parent.parent as? View
        }
        val drawable = eligible() && root.isAttachedToWindow && view.isAttachedToWindow &&
            view.windowVisibility == View.VISIBLE && view.isShown && view.isLaidOut &&
            !view.isLayoutRequested && view.width > 0 && view.height > 0 && opaque &&
            view.getGlobalVisibleRect(bounds) && !bounds.isEmpty
        if (!drawable) {
            release()
            if (phase != Phase.EXITING) phase = Phase.PREPARED
            return
        }
        if (visible !== event) {
            visible = event
            shown(event)
            // A callback may invalidate this event, detach the window or prepare a successor.
            if (identity !== event || visible !== event) return
        }
        if (width > 0) {
            view.getLocationOnScreen(screenPosition)
            // ViewRootImpl does not apply a window root's own matrix to its global
            // visible rect. Map it explicitly, including drawing into Surface outsets.
            transformedBounds.set(0f, 0f, view.width.toFloat(), view.height.toFloat())
            view.matrix.mapRect(transformedBounds)
            transformedOrigin[0] = 0f; transformedOrigin[1] = 0f
            view.matrix.mapPoints(transformedOrigin)
            transformedBounds.offset(screenPosition[0] - transformedOrigin[0], screenPosition[1] - transformedOrigin[1])
            bounds.set(floor(transformedBounds.left).toInt(), floor(transformedBounds.top).toInt(),
                ceil(transformedBounds.right).toInt(), ceil(transformedBounds.bottom).toInt())
            DynamicIslandStatusBarFade.acquire(event, bounds)
        }
        if (phase != Phase.EXITING) phase = Phase.VISIBLE
    }

    private fun release() {
        val previous = visible ?: return
        visible = null
        try { DynamicIslandStatusBarFade.release(previous) }
        finally { hidden(previous) }
    }

    fun cancel() {
        identity = null
        phase = Phase.IDLE
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
        observer = null
        if (listening) root.removeOnAttachStateChangeListener(this)
        listening = false
        release()
    }

    private fun observe() {
        val next = root.viewTreeObserver
        if (observer === next) return
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
        observer = next
        next.addOnPreDrawListener(this)
    }

    override fun onPreDraw(): Boolean {
        refresh()
        return true
    }
    override fun onViewAttachedToWindow(v: View) = observe()
    override fun onViewDetachedFromWindow(v: View) = cancel()
}
