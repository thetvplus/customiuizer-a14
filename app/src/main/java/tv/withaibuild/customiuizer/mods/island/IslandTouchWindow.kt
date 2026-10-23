package tv.withaibuild.customiuizer.mods.island

import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.Region
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Proxy
import kotlin.math.ceil
import kotlin.math.floor

/** Bounded input padding in the existing single window. Only the shell owns drawing and actions. */
internal class IslandTouchWindow(val shell: IslandActivityView) : FrameLayout(shell.context) {
    var margin = IslandRecallHit.extraMarginPx(resources.displayMetrics.density,
        ViewConfiguration.get(context).scaledTouchSlop)
        private set
    private val bounds = RectF()
    private val inverse = Matrix()
    private var tracking = false
    private var observer: ViewTreeObserver? = null
    private val listenerType = Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
    private val listener = Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) { proxy, method, args ->
        when (method.name) {
            "onComputeInternalInsets" -> {
                ModuleHelper.guarded {
                    val info = args!![0]
                    XposedHelpers.callMethod(info, "setTouchableInsets", 3) // TOUCHABLE_INSETS_REGION
                    val region = XposedHelpers.getObjectField(info, "touchableRegion") as Region
                    if (shell.alpha <= .01f || shell.visibility != VISIBLE) region.setEmpty()
                    else {
                        val box = hitBounds()
                        region.set(box.left, box.top, box.right, box.bottom)
                    }
                }
                null
            }
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.get(0)
            "toString" -> "IslandInputRegion"
            else -> null
        }
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

    fun geometry(width: Int, height: Int) {
        shell.layoutParams = LayoutParams(width, height).apply { leftMargin = margin; topMargin = margin }
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
            tracking = shell.alpha > .01f && event.x >= box.left && event.x < box.right &&
                event.y >= box.top && event.y < box.bottom
        }
        if (!tracking) return false
        if (!shell.matrix.invert(inverse)) { tracking = false; shell.cancelInputs(); return false }
        val local = MotionEvent.obtain(event)
        return try {
            local.offsetLocation(-shell.left.toFloat(), -shell.top.toFloat())
            local.transform(inverse)
            shell.dispatchTouchEvent(local)
        } finally {
            local.recycle()
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) tracking = false
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observer = viewTreeObserver.also { XposedHelpers.callMethod(it, "addOnComputeInternalInsetsListener", listener) }
    }

    override fun onDetachedFromWindow() {
        observer?.takeIf { it.isAlive }?.let { XposedHelpers.callMethod(it, "removeOnComputeInternalInsetsListener", listener) }
        observer = null
        tracking = false
        shell.cancelInputs()
        super.onDetachedFromWindow()
    }
}
