package tv.withaibuild.customiuizer.mods

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

/** A short island keeps the native clock and battery (including Duo), without reparenting them. */
internal class IslandStatusBarCompact internal constructor(root: ViewGroup, clock: View, battery: View,
    private val animationsEnabled: () -> Boolean = {
        !MainModule.mPrefs.getBoolean("system_island_simplified") && ValueAnimator.areAnimatorsEnabled()
    },
    private val blurEnabled: () -> Boolean = { !MainModule.mPrefs.getBoolean("system_disable_window_blurs") }) {
    private val root = WeakReference(root)
    private class Kept(view: View, val left: Boolean) {
        val view = WeakReference(view)
        var baseTranslation = view.translationX
        var shift = 0f
        var alpha = view.alpha
        var suppressed = false
    }
    private class Hidden(view: View) {
        val view = WeakReference(view)
        var alpha = view.alpha
        var ownedAlpha = view.alpha
        var occluded = false
    }
    private class Clip(view: ViewGroup) {
        val view = WeakReference(view)
        val children = view.clipChildren
        val padding = view.clipToPadding
    }
    private val kept = arrayOf(Kept(clock, true), Kept(battery, false))
    private val hidden = ArrayList<Hidden>()
    private val clips = ArrayList<Clip>()
    private val position = IntArray(2)
    private val windowPosition = IntArray(2)
    private val island = Rect()
    private val visibleBounds = Rect()
    private var active = false
    private var dirty = true
    private var restoring = false
    private var visibility = 1f
    private var motion: ValueAnimator? = null
    private var snapshot: Snapshot? = null
    /** Only this decoration receives RenderEffect: native render effects remain untouched. */
    private class Snapshot(host: ViewGroup, val pixels: Bitmap) : View(host.context) {
        init { setWillNotDraw(false); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onDraw(canvas: Canvas) { canvas.drawBitmap(pixels, 0f, 0f, null) }
    }
    private val layout = ViewTreeObserver.OnGlobalLayoutListener { dirty = true }
    private val draw = ViewTreeObserver.OnPreDrawListener {
        ModuleHelper.guarded {
            if (active) {
                if (dirty) { dirty = false; collectBranches() }
                updateOcclusion()
                applyHiddenAlpha()
                positionKept()
            }
        }
        true
    }
    private val attachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) = restore()
    }

    fun show(islandWidth: Int) {
        val host = root.get() ?: return
        host.getLocationOnScreen(position)
        val center = position[0] + host.width / 2
        show(Rect(center - islandWidth / 2, position[1], center + (islandWidth + 1) / 2, position[1] + host.height))
    }

    fun show(bounds: Rect) {
        island.set(bounds)
        val host = root.get() ?: return
        if (active && !restoring) { updateOcclusion(); applyHiddenAlpha(); positionKept(); return }
        val wasRestoring = restoring
        cancelMotion()
        restoring = false
        var entering = false
        if (!active) {
            active = true
            entering = true
            visibility = 1f
            dirty = true
            for (entry in kept) entry.view.get()?.let {
                entry.baseTranslation = it.translationX
                entry.shift = 0f
                entry.alpha = it.alpha
                entry.suppressed = false
            }
            host.viewTreeObserver.addOnGlobalLayoutListener(layout)
            host.viewTreeObserver.addOnPreDrawListener(draw)
            host.addOnAttachStateChangeListener(attachment)
            collectBranches()
        }
        updateOcclusion()
        positionKept()
        if (entering || wasRestoring) transition(reveal = false) else applyHiddenAlpha()
    }

    fun maximumWidth(screenWidth: Int): Int? {
        val host = root.get() ?: return null
        val clock = kept[0].view.get() ?: return null
        val battery = kept[1].view.get() ?: return null
        if (host.width <= 0 || clock.width <= 0 || battery.width <= 0) return null
        host.getLocationOnScreen(position)
        val gap = (16 * host.resources.displayMetrics.density).roundToInt()
        val left = position[0] + host.paddingLeft + clock.width + gap
        val right = screenWidth - (position[0] + host.width - host.paddingRight) + battery.width + gap
        return (screenWidth - 2 * maxOf(left, right)).coerceAtLeast(0)
    }

    private fun collectBranches() {
        val host = root.get() ?: return
        val leaves = ArrayList<View>()
        val seen = Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
        var visited = 0
        fun visit(group: ViewGroup, depth: Int = 0) {
            if (depth >= 32) return
            for (index in 0 until group.childCount) {
                if (visited++ >= 256) return
                val child = group.getChildAt(index)
                if (kept.any { it.view.get() === child }) continue
                if (child is ViewGroup && child.childCount > 0) visit(child, depth + 1)
                else if (leaves.size < 128) { leaves.add(child); seen.add(child) }
            }
        }
        visit(host)
        val entries = hidden.iterator()
        while (entries.hasNext()) {
            val entry = entries.next()
            val view = entry.view.get()
            if (view == null || view !in seen) {
                if (view != null && abs(view.alpha - entry.ownedAlpha) <= .001f) view.alpha = entry.alpha
                entries.remove()
                clearSnapshot()
            }
        }
        for (view in leaves) if (hidden.none { it.view.get() === view }) hidden.add(Hidden(view))
    }

    private fun updateOcclusion() {
        var changed = false
        for (entry in hidden) {
            val view = entry.view.get()
            var overlaps = false
            if (view != null && view.isShown && view.getGlobalVisibleRect(visibleBounds)) {
                // Global visible bounds are in the ViewRoot coordinate space; the island
                // lives in another window, so compare both in physical screen coordinates.
                view.getLocationOnScreen(position)
                view.getLocationInWindow(windowPosition)
                visibleBounds.offset(position[0] - windowPosition[0], position[1] - windowPosition[1])
                overlaps = Rect.intersects(island, visibleBounds)
            }
            if (entry.occluded != overlaps) { entry.occluded = overlaps; changed = true }
        }
        // An entrance/morph may change which individual icons are covered. A previous
        // decoration must never duplicate an icon that is now completely clear.
        if (changed) clearSnapshot()
    }

    private fun applyHiddenAlpha() {
        val fraction = if (snapshot != null) 0f else visibility
        for (index in hidden.indices) {
            val entry = hidden[index]
            val view = entry.view.get() ?: continue
            // A native visibility animator may update its target while our island is present.
            if (abs(view.alpha - entry.ownedAlpha) > .001f) entry.alpha = view.alpha
            val target = entry.alpha * if (entry.occluded) fraction else 1f
            if (view.alpha != target) view.alpha = target
            entry.ownedAlpha = target
        }
    }

    private fun capture(host: ViewGroup): Snapshot? {
        if (hidden.none { it.occluded } || !host.isHardwareAccelerated || !blurEnabled() || host.width <= 0 || host.height <= 0 ||
            host.width.toLong() * host.height > MAX_SNAPSHOT_PIXELS) return null
        var pixels: Bitmap? = null
        return try {
            pixels = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(pixels)
            host.getLocationOnScreen(position)
            val originX = position[0]
            val originY = position[1]
            for (entry in hidden) {
                val view = entry.view.get() ?: continue
                if (!entry.occluded || view.visibility != View.VISIBLE || view.width <= 0 || view.height <= 0 || entry.alpha <= 0f) continue
                view.getLocationOnScreen(position)
                val saved = canvas.save()
                canvas.translate((position[0] - originX).toFloat(), (position[1] - originY).toFloat())
                canvas.saveLayerAlpha(0f, 0f, view.width.toFloat(), view.height.toFloat(),
                    (entry.alpha.coerceIn(0f, 1f) * 255).roundToInt())
                view.draw(canvas)
                canvas.restoreToCount(saved)
            }
            Snapshot(host, pixels).apply { layout(0, 0, host.width, host.height) }
        } catch (failure: Throwable) {
            pixels?.recycle()
            FatalErrors.rethrowIfFatal(failure)
            null // Local alpha animation remains available on unsupported renderers.
        }
    }

    private fun transition(reveal: Boolean) {
        val host = root.get() ?: return restore()
        if (hidden.none { it.occluded } || !host.isAttachedToWindow || !host.isShown || !animationsEnabled()) {
            visibility = if (reveal) 1f else 0f
            applyHiddenAlpha()
            if (reveal) finishRestore()
            return
        }
        val from = visibility
        val target = if (reveal) 1f else 0f
        if (from == target) {
            applyHiddenAlpha()
            if (reveal) finishRestore()
            return
        }
        snapshot = capture(host)?.also { it.alpha = visibility; host.overlay.add(it) }
        val density = host.resources.displayMetrics.density
        // Six reusable GPU blur levels; no RenderEffect creation or bitmap capture per frame.
        val effects = try {
            if (snapshot == null) null else Array(BLUR_STEPS) { index ->
                val radius = (index + 1) * density
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)
            }
        } catch (failure: Throwable) {
            clearSnapshot()
            FatalErrors.rethrowIfFatal(failure)
            null
        }
        ValueAnimator.ofFloat(from, target).apply {
            duration = if (reveal) 400 else 320
            interpolator = PathInterpolator(.2f, .75f, .25f, 1f)
            addUpdateListener { animator -> ModuleHelper.guarded {
                if (motion !== animator) return@guarded
                visibility = (animator.animatedValue as Float).coerceIn(0f, 1f)
                snapshot?.let {
                    it.alpha = visibility
                    val step = ((1f - visibility) * BLUR_STEPS).roundToInt()
                    it.setRenderEffect(if (step <= 0) null else effects?.get(step.coerceAtMost(BLUR_STEPS) - 1))
                }
                applyHiddenAlpha()
            } }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = ModuleHelper.guarded {
                    if (motion !== animation) return@guarded
                    motion = null
                    clearSnapshot()
                    applyHiddenAlpha()
                    if (restoring) finishRestore()
                }
            })
            motion = this
            start()
        }
    }

    private fun clearSnapshot() {
        val previous = snapshot ?: return
        snapshot = null
        root.get()?.overlay?.remove(previous)
        previous.setRenderEffect(null)
        previous.pixels.recycle()
    }

    private fun cancelMotion() {
        val previous = motion
        motion = null
        previous?.cancel()
        clearSnapshot()
    }

    private fun positionKept() {
        val host = root.get() ?: return
        val gap = 8 * host.resources.displayMetrics.density
        host.getLocationOnScreen(position)
        val outerLeft = position[0] + host.paddingLeft.toFloat()
        val outerRight = position[0] + host.width - host.paddingRight.toFloat()
        val islandLeft = island.left - gap
        val islandRight = island.right + gap
        for (item in kept) {
            val view = item.view.get() ?: continue
            if (abs(view.translationX - item.baseTranslation - item.shift) > .01f) {
                item.baseTranslation = view.translationX
                item.shift = 0f
            }
            view.getLocationOnScreen(position)
            val baseLeft = position[0] - item.shift
            val overlaps = position[1] < island.bottom && position[1] + view.height > island.top &&
                baseLeft < island.right && baseLeft + view.width > island.left
            val desired = if (!overlaps) baseLeft else if (item.left) minOf(baseLeft, islandLeft - view.width).coerceAtLeast(outerLeft)
                else maxOf(baseLeft, islandRight).coerceAtMost(outerRight - view.width)
            val fits = !overlaps || if (item.left) desired + view.width <= islandLeft else desired >= islandRight
            if (!fits) { view.alpha = 0f; item.suppressed = true }
            else if (item.suppressed) { view.alpha = item.alpha; item.suppressed = false }
            item.shift = (desired - baseLeft).roundToInt().toFloat()
            if (item.shift != 0f) allowTranslation(view)
            val target = item.baseTranslation + item.shift
            if (abs(view.translationX - target) > .01f) view.translationX = target
        }
    }

    private fun allowTranslation(view: View) {
        var group = view.parent as? ViewGroup
        var depth = 0
        while (group != null && depth++ < 32) {
            if (clips.none { it.view.get() === group }) {
                if (clips.size >= 64) return
                clips.add(Clip(group))
            }
            group.clipChildren = false
            group.clipToPadding = false
            if (group === root.get()) return
            group = group.parent as? ViewGroup
        }
    }

    fun restore(animate: Boolean = false) {
        if (!active) return
        if (animate && !restoring) {
            cancelMotion()
            restoring = true
            transition(reveal = true)
            return
        }
        if (animate) return
        cancelMotion()
        finishRestore()
    }

    private fun finishRestore() {
        active = false
        restoring = false
        visibility = 1f
        root.get()?.let { host ->
            if (host.viewTreeObserver.isAlive) {
                host.viewTreeObserver.removeOnGlobalLayoutListener(layout)
                host.viewTreeObserver.removeOnPreDrawListener(draw)
            }
            host.removeOnAttachStateChangeListener(attachment)
        }
        for (entry in hidden) entry.view.get()?.let {
            if (abs(it.alpha - entry.ownedAlpha) <= .001f) it.alpha = entry.alpha
        }
        for (item in kept) item.view.get()?.let { view ->
            if (abs(view.translationX - item.baseTranslation - item.shift) < .01f) view.translationX = item.baseTranslation
            if (item.suppressed && view.alpha == 0f) view.alpha = item.alpha
            item.shift = 0f
            item.suppressed = false
        }
        for (entry in clips) entry.view.get()?.let { it.clipChildren = entry.children; it.clipToPadding = entry.padding }
        hidden.clear(); clips.clear()
    }

    companion object {
        private const val MAX_SNAPSHOT_PIXELS = 262144 // One ARGB image, at most 1 MiB per transition.
        private const val BLUR_STEPS = 6
        fun resolve(contents: View): IslandStatusBarCompact? {
            val host = contents as? ViewGroup ?: return null
            fun find(name: String): View? {
                val id = host.resources.getIdentifier(name, "id", "com.android.systemui")
                return if (id == 0) null else host.findViewById(id)
            }
            return IslandStatusBarCompact(host, find("clock") ?: return null, find("battery") ?: return null)
        }
    }
}
