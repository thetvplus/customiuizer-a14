package tv.withaibuild.customiuizer.mods.homeindicator

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.view.View
import android.view.WindowManager
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.PreparedHookBatch
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.G4RoundRectGeometry
import tv.withaibuild.customiuizer.utils.HomeIndicatorSupport
import tv.withaibuild.customiuizer.mods.utils.HookDiagnostics
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier

/** Replaces only the native handle's fill. No window, input, visibility, alpha,
 * luma sampling, gesture detector, timer or extra animation owner. */
internal object HomeIndicatorHooks {
    const val KEY = "controls_ios_home_indicator"
    private const val OWNER = "a14.home.indicator.owner"
    private const val MODE = "a14.home.indicator.mode"
    fun install(param: PackageReadyParam): FeatureInstallResult {
        if (!HomeIndicatorSupport.supported(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS) ||
            param.packageName != "com.android.systemui" || HookDiagnostics.currentProcessName != "com.android.systemui")
            return FeatureInstallResult.FAILED_PERMANENT
        val loader = param.classLoader
        val handle = XposedHelpers.findClass("com.android.systemui.navigationbar.gestural.NavigationHandle", loader)
        val bar = XposedHelpers.findClass("com.android.systemui.navigationbar.NavigationBarView", loader)
        val transitions = XposedHelpers.findClass("com.android.systemui.navigationbar.NavigationBarTransitions", loader)
        return PreparedHookBatch().install(prepare(handle, bar, transitions))
    }
    internal fun prepare(handle: Class<*>, bar: Class<*>, transitions: Class<*>): List<Pair<java.lang.reflect.Method, MethodHook>> {
        val draw = handle.getDeclaredMethod("onDraw", Canvas::class.java)
        // setNavBarMode writes mNavBarMode then calls updateIcons (not
        // updateNavButtonIcons). This also covers configuration reinflation.
        val update = bar.getDeclaredMethod("updateIcons", android.content.res.Configuration::class.java)
        val mode = bar.getDeclaredField("mNavBarMode").apply { isAccessible = true }
        val paint = handle.getDeclaredField("mPaint").apply { isAccessible = true }
        val tintDuration = transitions.getDeclaredMethod("getTintAnimationDuration")
        val tintView = transitions.getDeclaredField("mView").apply { isAccessible = true }
        val tintMode = transitions.getDeclaredField("mNavBarMode").apply { isAccessible = true }
        // The native Paint is constructor-owned. A mutable replacement contract
        // cannot safely share this owner's cached instance without a new lifecycle.
        require(handle.superclass == View::class.java && View::class.java.isAssignableFrom(bar) &&
            draw.returnType == Void.TYPE && !Modifier.isStatic(draw.modifiers) &&
            update.returnType == Void.TYPE && !Modifier.isStatic(update.modifiers) &&
            mode.type == Int::class.javaPrimitiveType && !Modifier.isStatic(mode.modifiers) &&
            paint.type == Paint::class.java && Modifier.isFinal(paint.modifiers) && !Modifier.isStatic(paint.modifiers) &&
            tintDuration.returnType == Int::class.javaPrimitiveType && !Modifier.isStatic(tintDuration.modifiers) &&
            tintView.type == bar && !Modifier.isStatic(tintView.modifiers) &&
            tintMode.type == Int::class.javaPrimitiveType && !Modifier.isStatic(tintMode.modifiers))
        return listOf(
            tintDuration to object : MethodHook() {
                override fun before(callback: BeforeHookCallback) = ModuleHelper.guarded {
                    val native = callback.getThisObject()
                    val navigation = tintView.get(native) as View
                    // Reuse the system's retargetable tint animator and easing.
                    // This 250 ms A14 mapping affects ordinary gesture tint only;
                    // explicit window-handoff durations remain native.
                    if (navigation.display?.displayId == 0 && tintMode.getInt(native) == 2)
                        callback.returnAndSkip(250)
                }
            },
            update to object : MethodHook() {
                override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                    if (callback.getThrowable() == null) XposedHelpers.setAdditionalInstanceField(
                        callback.getThisObject(), MODE, mode.getInt(callback.getThisObject()) == 2)
                }
            },
            draw to object : MethodHook() {
                override fun before(callback: BeforeHookCallback) = ModuleHelper.guarded {
                    val view = callback.getThisObject() as View
                    // Separate tutorial/CC/lockscreen hosts and subclasses keep
                    // native rendering until their own contracts are verified.
                    if (view.javaClass != handle || view.display?.displayId != 0) return@guarded
                    val owner = owner(view) ?: run {
                        var parent = view.parent
                        var depth = 0
                        while (parent is View && !bar.isInstance(parent) && depth++ < 8) parent = parent.parent
                        val navigation = parent as? View ?: return@guarded
                        if (!bar.isInstance(navigation)) return@guarded
                        if (XposedHelpers.getAdditionalInstanceField(navigation, MODE) == null)
                            XposedHelpers.setAdditionalInstanceField(navigation, MODE, mode.getInt(navigation) == 2)
                        if (XposedHelpers.getAdditionalInstanceField(navigation, MODE) != true) return@guarded
                        Owner(view, navigation, paint.get(view) as Paint).also {
                            view.addOnAttachStateChangeListener(it)
                            // The attach listener owns the renderer for this handle.
                            XposedHelpers.setAdditionalInstanceField(view, OWNER, WeakReference(it))
                        }
                    }
                    if (XposedHelpers.getAdditionalInstanceField(owner.navigation, MODE) != true) return@guarded
                    if (owner.draw(callback.getArg(0) as Canvas)) callback.returnAndSkip(null)
                }
            })
    }
    private fun owner(view: View): Owner? =
        (XposedHelpers.getAdditionalInstanceField(view, OWNER) as? WeakReference<*>)?.get() as? Owner

    private class Owner(val view: View, val navigation: View, val paint: Paint) : View.OnAttachStateChangeListener {
        private val path = Path()
        private val windows = navigation.context.getSystemService(WindowManager::class.java)
        private var w = -1; private var h = -1
        private var rootWidth = -1
        private var ready = false
        fun draw(canvas: Canvas): Boolean {
            val root = navigation.rootView
            if (w != view.width || h != view.height || rootWidth != root.width) {
                // SystemUI Resources can retain portrait metrics while its nav
                // window has rotated. Query the full display's WindowMetrics
                // only on geometry invalidation, never on stable draw frames.
                val screen = windows.maximumWindowMetrics.bounds
                if (root.width != screen.width()) return false
                w = view.width; h = view.height; rootWidth = root.width
                path.rewind()
                val frame = HomeIndicatorGeometry.frame(w, h, screen.width(), screen.height())
                ready = frame != null
                if (frame != null) {
                    G4RoundRectGeometry.build(path, frame.width, frame.height, frame.height * .5f, 1f)
                    path.offset(frame.left, frame.top)
                }
            }
            if (!ready) return false
            canvas.drawPath(path, paint)
            return true
        }
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) {
            v.removeOnAttachStateChangeListener(this)
            if (owner(v) === this)
                XposedHelpers.removeAdditionalInstanceField(v, OWNER)
            path.rewind(); ready = false
        }
    }
}
