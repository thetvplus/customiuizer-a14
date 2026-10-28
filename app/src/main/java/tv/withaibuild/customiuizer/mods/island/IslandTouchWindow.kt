package tv.withaibuild.customiuizer.mods.island

import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.Region
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.math.ceil
import kotlin.math.floor

/** Bounded input padding in the existing single window. Only the shell owns drawing and actions. */
internal class IslandTouchWindow(val shell: IslandActivityView) : FrameLayout(shell.context) {
    var inputEnabled = true
        set(value) {
            if (field == value) return
            field = value
            tracking = false
            shell.cancelInputs()
            requestLayout()
        }
    var margin = IslandRecallHit.extraMarginPx(resources.displayMetrics.density,
        ViewConfiguration.get(context).scaledTouchSlop)
        private set
    private val bounds = RectF()
    private val inverse = Matrix()
    private var tracking = false
    private var observer: ViewTreeObserver? = null
    private class Insets(val listener: Any, val add: Method, val remove: Method)
    private val insets: Insets? = ModuleHelper.guarded<Insets?>(null) {
        val listenerType = Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
        val infoType = Class.forName("android.view.ViewTreeObserver\$InternalInsetsInfo")
        val setMode = infoType.getMethod("setTouchableInsets", Int::class.javaPrimitiveType)
        val regionField = infoType.getField("touchableRegion")
        val owner = WeakReference(this)
        val listener = Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) { proxy, method, args ->
            when (method.name) {
                "onComputeInternalInsets" -> {
                    val root = owner.get()
                    if (root != null) {
                        val success = ModuleHelper.guarded(false) {
                            val info = args!![0]
                            val region = regionField.get(info) as Region
                            region.setEmpty()
                            setMode.invoke(info, 3) // TOUCHABLE_INSETS_REGION
                            if (root.inputEnabled && root.inputRegionAvailable && root.observer != null &&
                                root.shell.alpha > .01f && root.shell.visibility == VISIBLE) {
                                val box = root.hitBounds()
                                region.set(box.left, box.top, box.right, box.bottom)
                            }
                            true
                        }
                        if (!success) root.unavailable()
                    }
                    null
                }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                "toString" -> "IslandInputRegion"
                else -> null
            }
        }
        Insets(listener, ViewTreeObserver::class.java.getMethod("addOnComputeInternalInsetsListener", listenerType),
            ViewTreeObserver::class.java.getMethod("removeOnComputeInternalInsetsListener", listenerType))
    }
    var inputRegionAvailable: Boolean = insets != null
        private set
    var onUnavailable: (() -> Unit)? = null

    private fun unavailable() {
        if (!inputRegionAvailable) return
        inputRegionAvailable = false
        tracking = false
        ModuleHelper.guarded { shell.cancelInputs() }
        ModuleHelper.guarded { onUnavailable?.invoke() }
    }

    init {
        clipChildren = false
        clipToPadding = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        shell.touchMargin = margin
        addView(shell)
    }

    fun refreshConfiguration() {
        margin = IslandRecallHit.extraMarginPx(resources.displayMetrics.density,
            ViewConfiguration.get(context).scaledTouchSlop)
        shell.touchMargin = margin
    }

    fun geometry(width: Int, height: Int, left: Int = margin) {
        val params = shell.layoutParams as? LayoutParams
        if (params != null && params.width == width && params.height == height &&
            params.leftMargin == left && params.topMargin == margin) return
        shell.layoutParams = (params ?: LayoutParams(width, height)).apply {
            this.width = width; this.height = height; leftMargin = left; topMargin = margin
        }
    }

    internal fun hitBounds(): IslandRecallHit.Box {
        bounds.set(0f, 0f, shell.width.toFloat(), shell.height.toFloat())
        shell.matrix.mapRect(bounds)
        bounds.offset(shell.left.toFloat(), shell.top.toFloat())
        return IslandRecallHit.delegateRect(width, height, floor(bounds.left).toInt(), floor(bounds.top).toInt(),
            ceil(bounds.width()).toInt(), ceil(bounds.height()).toInt(), margin)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val box = hitBounds()
            tracking = inputEnabled && inputRegionAvailable && shell.visibility == VISIBLE && shell.alpha > .01f && event.x >= box.left && event.x < box.right &&
                event.y >= box.top && event.y < box.bottom
        }
        if (!tracking) return false
        if (!shell.matrix.invert(inverse)) { tracking = false; shell.cancelInputs(); return false }
        val local = MotionEvent.obtain(event)
        return try {
            local.offsetLocation(-shell.left.toFloat(), -shell.top.toFloat())
            local.transform(inverse)
            shell.dispatchFromWindow(local, event)
        } finally {
            local.recycle()
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) tracking = false
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val bridge = insets ?: return
        if (!inputRegionAvailable || observer != null) return
        observer = viewTreeObserver
        if (!ModuleHelper.guarded(false) { bridge.add.invoke(observer, bridge.listener); true }) unavailable()
    }

    override fun onDetachedFromWindow() {
        val previous = observer
        observer = null // Any retained callback becomes inert before reflection or cleanup.
        try {
            val removed = ModuleHelper.guarded(false) {
                if (previous?.isAlive == true) insets?.let { it.remove.invoke(previous, it.listener) }
                true
            }
            if (!removed) inputRegionAvailable = false
        } finally {
            tracking = false
            try { shell.cancelInputs() } finally { super.onDetachedFromWindow() }
        }
    }
}
