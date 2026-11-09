package tv.withaibuild.customiuizer.mods.statusicons

import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.PreparedHookBatch
import kotlin.math.roundToInt
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** Padding participates in the native status container's measured width (margins do not).
 * Adjacent presentations share max(right, left), with the right-hand owner keeping its
 * left contribution. No container, layout observer or global collection of Views. */
internal class StatusIconSpacing(private val view: View) : View.OnAttachStateChangeListener {
    private var nativeLeft = view.paddingLeft
    private var nativeRight = view.paddingRight
    private var lastLeft = nativeLeft
    private var lastRight = nativeRight
    private var left = 0
    private var right = 0
    private var closed = false

    init {
        view.addOnAttachStateChangeListener(this)
        XposedHelpers.setAdditionalInstanceField(view, KEY, WeakReference(this))
    }

    fun update(leftHalfDp: Int, rightHalfDp: Int) {
        val unit = view.resources.displayMetrics.density / 2f
        val nextLeft = (leftHalfDp.coerceIn(0, 16) * unit).roundToInt()
        val nextRight = (rightHalfDp.coerceIn(0, 16) * unit).roundToInt()
        if (left == nextLeft && right == nextRight && view.paddingLeft == lastLeft && view.paddingRight == lastRight) return
        left = nextLeft; right = nextRight
        apply()
        neighbor(false)?.apply()
    }

    private fun apply() {
        if (closed) return
        if (view.paddingLeft != lastLeft) nativeLeft = view.paddingLeft
        if (view.paddingRight != lastRight) nativeRight = view.paddingRight
        val nextLeft = nativeLeft + left
        val adjacent = neighbor(true)
        val nextRight = nativeRight + if (adjacent == null) right else (right - adjacent.left).coerceAtLeast(0)
        if (view.paddingLeft != nextLeft || view.paddingRight != nextRight)
            view.setPadding(nextLeft, view.paddingTop, nextRight, view.paddingBottom)
        lastLeft = nextLeft; lastRight = nextRight
    }

    // Follow only the adjacent boundary branch, never scan the status bar tree.
    // An intervening visible Wi-Fi/other icon ends coordination immediately.
    private fun neighbor(toRight: Boolean): StatusIconSpacing? {
        var child = view
        repeat(6) {
            val parent = child.parent as? ViewGroup ?: return null
            val step = if (toRight != (parent.layoutDirection == View.LAYOUT_DIRECTION_RTL)) 1 else -1
            var index = parent.indexOfChild(child) + step
            while (index in 0 until parent.childCount) {
                val sibling = parent.getChildAt(index)
                if (sibling.visibility != View.GONE) return boundary(sibling, toRight)
                index += step
            }
            child = parent
        }
        return null
    }

    private fun boundary(candidate: View, fromLeft: Boolean): StatusIconSpacing? {
        binding(candidate)?.let { return it.takeUnless { value -> value.closed } }
        val group = candidate as? ViewGroup ?: return null
        val step = if (fromLeft != (group.layoutDirection == View.LAYOUT_DIRECTION_RTL)) 1 else -1
        var index = if (step == 1) 0 else group.childCount - 1
        while (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            if (child.visibility != View.GONE) return boundary(child, fromLeft)
            index += step
        }
        return null
    }

    fun close() {
        if (closed) return
        val previous = neighbor(false)
        closed = true
        view.removeOnAttachStateChangeListener(this)
        if (binding(view) === this) XposedHelpers.removeAdditionalInstanceField(view, KEY)
        val restoreLeft = if (view.paddingLeft == lastLeft) nativeLeft else view.paddingLeft
        val restoreRight = if (view.paddingRight == lastRight) nativeRight else view.paddingRight
        if (view.paddingLeft != restoreLeft || view.paddingRight != restoreRight)
            view.setPadding(restoreLeft, view.paddingTop, restoreRight, view.paddingBottom)
        previous?.apply()
    }

    override fun onViewAttachedToWindow(v: View) { apply(); neighbor(false)?.apply() }
    override fun onViewDetachedFromWindow(v: View) = close()

    companion object {
        private const val KEY = "a14.status.spacing"
        private const val MEASURE = "a14.status.spacing.measure"

        fun install(loader: ClassLoader): FeatureInstallResult = try {
            val owner = Class.forName("com.android.systemui.statusbar.views.MiuiStatusBatteryContainer", false, loader)
            require(ViewGroup::class.java.isAssignableFrom(owner))
            install(owner.getDeclaredMethod("onMeasure", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType))
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            XposedHelpers.log("Status icon spacing: native measurement unavailable", error)
            FeatureInstallResult.FAILED_PERMANENT
        }

        internal fun install(method: Method,
            hook: (Method, MethodHook) -> CustomMethodUnhooker? = ModuleHelper::hookMethod): FeatureInstallResult {
            val owner = method.declaringClass
            require(ViewGroup::class.java.isAssignableFrom(owner) && method.name == "onMeasure" &&
                method.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) &&
                method.returnType == Void.TYPE && !Modifier.isStatic(method.modifiers))
            return synchronized(owner) {
                val batch = (XposedHelpers.getAdditionalStaticField(owner, MEASURE) as? PreparedHookBatch)
                    ?: PreparedHookBatch(hook).also { XposedHelpers.setAdditionalStaticField(owner, MEASURE, it) }
                batch.install(listOf(method to measureHook()))
            }
        }

        internal fun measureHook() = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                ModuleHelper.guarded { reconcile(chain.thisObject as ViewGroup, 2) }
                return chain.proceed()
            }
        }

        // The verified native root has two branches: battery and the icon row.
        // Reconcile before either branch is measured; never visit the rest of the
        // status bar or allocate a View snapshot/listener on a stable traversal.
        private fun reconcile(view: View, depth: Int) {
            if (view.visibility == View.GONE) return
            val owner = binding(view)
            if (owner != null) { owner.apply(); return }
            if (view !is ViewGroup || depth == 0) return
            for (index in 0 until view.childCount) reconcile(view.getChildAt(index), depth - 1)
        }
        private fun binding(view: View) =
            (XposedHelpers.getAdditionalInstanceField(view, KEY) as? WeakReference<*>)?.get() as? StatusIconSpacing

        fun update(view: View, left: Int, right: Int) {
            if (left == 0 && right == 0) { restore(view); return }
            (binding(view) ?: StatusIconSpacing(view)).update(left, right)
        }

        fun restore(view: View) { binding(view)?.close() }

        fun slot(image: View, className: String): View {
            var current: View? = image
            repeat(4) {
                val candidate = current ?: return image
                if (candidate.javaClass.name == className) return candidate
                current = candidate.parent as? View
            }
            return image
        }
    }
}
