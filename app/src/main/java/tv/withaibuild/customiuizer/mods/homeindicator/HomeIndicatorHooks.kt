package tv.withaibuild.customiuizer.mods.homeindicator

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Matrix
import android.os.Build
import android.view.Surface
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
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Replaces only the native handle's fill. No window, input, visibility, alpha,
 * luma sampling, gesture detector, timer or extra animation owner. */
internal object HomeIndicatorHooks {
    const val KEY = "controls_ios_home_indicator"
    private const val NAVIGATION = "com.android.systemui.navigationbar."
    private const val LAUNCHER_HANDLE = "com.miui.home.recents.views.FakeNavigationBarView"
    private const val OWNER = "a14.home.indicator.owner"
    private const val MODE = "a14.home.indicator.mode"
    fun install(param: PackageReadyParam): FeatureInstallResult {
        if (!HomeIndicatorSupport.supported(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS) ||
            param.packageName != "com.android.systemui" || HookDiagnostics.currentProcessName != "com.android.systemui")
            return FeatureInstallResult.FAILED_PERMANENT
        val loader = param.classLoader
        val handle = XposedHelpers.findClass("${NAVIGATION}gestural.NavigationHandle", loader)
        val bar = XposedHelpers.findClass("${NAVIGATION}NavigationBarView", loader)
        val transitions = XposedHelpers.findClass("${NAVIGATION}NavigationBarTransitions", loader)
        val result = PreparedHookBatch().install(prepare(handle, bar, transitions))
        if (result != FeatureInstallResult.INSTALLED) return result
        // The secondary window is optional on a ROM. Missing ABI leaves only
        // that window native and never rolls back the ordinary handle.
        val oriented = XposedHelpers.findClassIfExists("${NAVIGATION}gestural.QuickswitchOrientedNavHandle", loader)
            ?: return result
        val entry = ModuleHelper.guarded<Pair<Method, MethodHook>?>(null) {
            prepareOriented(handle, oriented)
        } ?: return result
        val secondary = PreparedHookBatch().install(listOf(entry))
        if (secondary != FeatureInstallResult.INSTALLED) XposedHelpers.log("[HomeIndicator] secondary handle: $secondary")
        return result
    }

    fun installLauncher(param: PackageReadyParam): FeatureInstallResult {
        if (!HomeIndicatorSupport.supported(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS) ||
            param.packageName != HookDiagnostics.currentProcessName ||
            !supportsLauncher(param.packageName)) return FeatureInstallResult.FAILED_PERMANENT
        val handle = XposedHelpers.findClass(LAUNCHER_HANDLE, param.classLoader)
        return PreparedHookBatch().install(listOf(prepareLauncher(handle)))
    }

    fun supportsLauncher(packageName: String?) =
        packageName == "com.miui.home" || packageName == "com.mi.android.globallauncher"

    internal fun prepareLauncher(handle: Class<*>): Pair<Method, MethodHook> {
        require(handle.superclass == View::class.java)
        val offset = instanceField(handle, "mOffset", Int::class.javaPrimitiveType!!)
        val width = instanceField(handle, "mNavShowWidth", Int::class.javaPrimitiveType!!)
        return alternateDraw(handle, nativePaint(handle)) { view, bounds, _ ->
            // Launcher rotates this View inside a portrait window. Its local
            // width and native center already belong to the visual axes.
            HomeIndicatorGeometry.frame(view.width, view.height, view.width, minOf(bounds.width(), bounds.height()),
                offset.getInt(view) + width.getInt(view) * .5f)
        }
    }

    internal fun prepareOriented(handle: Class<*>, oriented: Class<*>): Pair<Method, MethodHook> {
        require(oriented.superclass == handle)
        val delta = instanceField(oriented, "mDeltaRotation", Int::class.javaPrimitiveType!!, final = false)
        val bounds = oriented.getDeclaredMethod("computeHomeHandleBounds").apply { isAccessible = true }
        require(bounds.returnType == RectF::class.java && !Modifier.isStatic(bounds.modifiers))
        // NavigationBar creates the secondary window only for gesture navigation.
        // Its temporary RectF supplies the native center, including window offset.
        return alternateDraw(oriented, nativePaint(handle), rotation = { delta.getInt(it) }) { view, screen, rotation ->
            val native = bounds.invoke(view) as RectF
            if (native.isEmpty) return@alternateDraw null
            when (rotation) {
                Surface.ROTATION_0, Surface.ROTATION_180 ->
                    HomeIndicatorGeometry.frame(view.width, view.height, screen.width(), screen.height(), native.centerX())
                Surface.ROTATION_90, Surface.ROTATION_270 -> {
                    val center = if (rotation == Surface.ROTATION_90) native.centerY() else view.height - native.centerY()
                    HomeIndicatorGeometry.frame(view.height, view.width, screen.width(), screen.height(), center)
                }
                else -> null
            }
        }
    }

    private fun instanceField(type: Class<*>, name: String, value: Class<*>, final: Boolean = true) =
        type.getDeclaredField(name).apply {
            require(this.type == value && !Modifier.isStatic(modifiers) && (!final || Modifier.isFinal(modifiers)))
            isAccessible = true
        }

    private fun nativePaint(type: Class<*>) = instanceField(type, "mPaint", Paint::class.java)

    private fun alternateDraw(
        handle: Class<*>,
        paint: Field,
        rotation: (View) -> Int = { Surface.ROTATION_0 },
        geometry: (View, Rect, Int) -> HomeIndicatorGeometry.Frame?,
    ): Pair<Method, MethodHook> {
        val draw = handle.getDeclaredMethod("onDraw", Canvas::class.java)
        require(draw.returnType == Void.TYPE && !Modifier.isStatic(draw.modifiers))
        return draw to object : MethodHook() {
            override fun before(callback: BeforeHookCallback) = ModuleHelper.guarded {
                val view = callback.getThisObject() as View
                if (view.javaClass != handle || view.display?.displayId != 0) return@guarded
                val renderer = owner(view) ?: bind(view, view, paint.get(view) as Paint)
                if (renderer.draw(callback.getArg(0) as Canvas, rotation(view), geometry)) callback.returnAndSkip(null)
            }
        }
    }
    internal fun prepare(handle: Class<*>, bar: Class<*>, transitions: Class<*>): List<Pair<Method, MethodHook>> {
        val draw = handle.getDeclaredMethod("onDraw", Canvas::class.java)
        // setNavBarMode writes mNavBarMode then calls updateIcons (not
        // updateNavButtonIcons). This also covers configuration reinflation.
        val update = bar.getDeclaredMethod("updateIcons", android.content.res.Configuration::class.java)
        val mode = bar.getDeclaredField("mNavBarMode").apply { isAccessible = true }
        val paint = nativePaint(handle)
        val tintDuration = transitions.getDeclaredMethod("getTintAnimationDuration")
        val tintView = transitions.getDeclaredField("mView").apply { isAccessible = true }
        val tintMode = transitions.getDeclaredField("mNavBarMode").apply { isAccessible = true }
        // The native Paint is constructor-owned. A mutable replacement contract
        // cannot safely share this owner's cached instance without a new lifecycle.
        require(handle.superclass == View::class.java && View::class.java.isAssignableFrom(bar) &&
            draw.returnType == Void.TYPE && !Modifier.isStatic(draw.modifiers) &&
            update.returnType == Void.TYPE && !Modifier.isStatic(update.modifiers) &&
            mode.type == Int::class.javaPrimitiveType && !Modifier.isStatic(mode.modifiers) &&
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
                        bind(view, navigation, paint.get(view) as Paint)
                    }
                    if (XposedHelpers.getAdditionalInstanceField(owner.navigation, MODE) != true) return@guarded
                    if (owner.draw(callback.getArg(0) as Canvas)) callback.returnAndSkip(null)
                }
            })
    }
    private fun owner(view: View): Owner? =
        (XposedHelpers.getAdditionalInstanceField(view, OWNER) as? WeakReference<*>)?.get() as? Owner

    private fun bind(view: View, navigation: View, paint: Paint) = Owner(view, navigation, paint).also {
        view.addOnAttachStateChangeListener(it)
        XposedHelpers.setAdditionalInstanceField(view, OWNER, WeakReference(it))
    }

    private val bottomGeometry: (View, Rect, Int) -> HomeIndicatorGeometry.Frame? = { view, screen, _ ->
        HomeIndicatorGeometry.frame(view.width, view.height, screen.width(), screen.height())
    }

    private class Owner(val view: View, val navigation: View, val paint: Paint) : View.OnAttachStateChangeListener {
        private val path = Path()
        private val transform = Matrix()
        private val windows = navigation.context.getSystemService(WindowManager::class.java)
        private var w = -1; private var h = -1
        private var rootWidth = -1
        private var rootHeight = -1
        private var displayRotation = -1
        private var handleRotation = -1
        private var ready = false
        fun draw(
            canvas: Canvas,
            rotation: Int = Surface.ROTATION_0,
            geometry: (View, Rect, Int) -> HomeIndicatorGeometry.Frame? = bottomGeometry,
        ): Boolean {
            val root = navigation.rootView
            val currentRotation = view.display?.rotation ?: return false
            if (w != view.width || h != view.height || rootWidth != root.width || rootHeight != root.height ||
                displayRotation != currentRotation || handleRotation != rotation) {
                val screen = windows.maximumWindowMetrics.bounds
                val frame = geometry(view, screen, rotation)
                w = view.width; h = view.height; rootWidth = root.width; rootHeight = root.height
                displayRotation = currentRotation; handleRotation = rotation
                path.rewind()
                ready = frame != null
                if (frame != null) {
                    G4RoundRectGeometry.build(path, frame.width, frame.height, frame.height * .5f, 1f)
                    path.offset(frame.left, frame.top)
                    when (rotation) {
                        Surface.ROTATION_90 -> {
                            transform.setRotate(90f)
                            transform.postTranslate(w.toFloat(), 0f)
                            path.transform(transform)
                        }
                        Surface.ROTATION_270 -> {
                            transform.setRotate(-90f)
                            transform.postTranslate(0f, h.toFloat())
                            path.transform(transform)
                        }
                    }
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
