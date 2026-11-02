package tv.withaibuild.customiuizer.mods

import android.animation.ValueAnimator
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Insets
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.WindowInsets
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.Transformation
import android.widget.FrameLayout
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.ref.WeakReference
import java.util.function.Consumer
import kotlin.math.min
import kotlin.math.roundToInt

/** A non-interactive decoration owned by HotSeats, below its native icon content. */
object LauncherDockBackground {
    private class EditingOwner(animation: AnimationSet, owner: DockBackgroundView) {
        val animation = WeakReference(animation)
        val owner = WeakReference(owner)
    }
    // Binding/release publishes a new snapshot. Global frame callbacks never probe the shared
    // additional-field table, acquire a lock or retain a View/Animation beyond its owner.
    @Volatile private var editingOwners: List<EditingOwner> = emptyList()
    private const val FULL_WIDTH_OWNER = "customiuizer_dock_full_width_owner"
    @JvmStatic
    fun install(lpparam: PackageReadyParam) {
        val config = DockBackgroundConfig.read(MainModule.mPrefs)
        val hotseats = XposedHelpers.findClass("com.miui.home.launcher.hotseats.HotSeats", lpparam.classLoader)
        check(FrameLayout::class.java.isAssignableFrom(hotseats))
        val content = DockContentAccess.resolve(lpparam.classLoader)
        val material = XposedHelpers.findClassIfExists(
            "com.miui.blur.sdk.backdrop.BackdropBlurRelativeLayout", lpparam.classLoader
        )?.constructors?.firstOrNull { it.parameterTypes.contentEquals(arrayOf(Context::class.java)) }
        // Resolve every declaration before registering any callback. Attachment is last so
        // no decoration can activate with an incomplete native frame/lifecycle mesh.
        val hooks = ArrayList<Pair<Method, MethodHook>>()
        fun prepare(type: Class<*>, name: String, vararg signatureAndHook: Any?) {
            val callback = signatureAndHook.last() as MethodHook
            val method = XposedHelpers.findMethodExact(type, name, *signatureAndHook.copyOf(signatureAndHook.size - 1))
            hooks.add(method to callback)
        }
        val launcher = XposedHelpers.findClass("com.miui.home.launcher.Launcher", lpparam.classLoader)
        if (content != null) {
            // Recents can hide only the native content while HotSeats itself remains visible
            // at alpha 1. Its state owns the decoration too, independent of blur backend.
            val overview = XposedHelpers.findClass("com.miui.home.recents.OverviewState", lpparam.classLoader)
            for ((name, visible) in listOf("onStateEnabled" to false, "onExitState" to true)) {
                prepare(overview, name, launcher, object : MethodHook() {
                    override fun after(callback: AfterHookCallback) {
                        if (callback.getThrowable() != null) return
                        callback.getArg(0)?.let { content.hostFromLauncher(it) }
                            ?.let { findBackground(it)?.setSceneVisible(visible) }
                    }
                })
            }
        }
        val sceneAlpha = XposedHelpers.findMethodExactIfExists(launcher, "setScreenContentAlpha", Float::class.javaPrimitiveType)
        val sceneTransforms = listOf("setTranslationX", "setTranslationY", "setScaleY").map {
            XposedHelpers.findMethodExactIfExists(hotseats, it, Float::class.javaPrimitiveType)
                ?.takeIf { method -> method.declaringClass === hotseats }
        }
        // These Launcher/HotSeats declarations carry native scene changes. Never hook View setters.
        val visualEvents = config.opacity > 0 && content != null && sceneAlpha != null && sceneTransforms.all { it != null }
        if (visualEvents) {
            hooks.add(sceneAlpha to object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    if (callback.getThrowable() != null) return
                    callback.getThisObject()?.let { content.hostFromLauncher(it) }
                        ?.let { findBackground(it)?.scheduleVisualSync() }
                }
            })
            val visualHook = object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    if (callback.getThrowable() == null)
                        findBackground(callback.getThisObject() as FrameLayout)?.scheduleVisualSync()
                }
            }
            sceneTransforms.forEach { hooks.add(it!! to visualHook) }
        }
        prepare(hotseats, "onMeasure", Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, hotseatMeasureHook())
        // Phone HotSeats lays out its children immediately without changing the row's frame.
        // An OnLayoutChangeListener on mContent cannot observe that layout.
        XposedHelpers.findClassIfExists("com.miui.home.launcher.hotseats.HotSeatsScreenViewContent",
            lpparam.classLoader)?.let { row ->
            prepare(row, "onLayout", Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, contentLayoutHook())
        }

        content?.dragControllerType?.let { controller ->
            prepare(controller, "endDrag", object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    if (callback.getThrowable() != null) return
                    callback.getThisObject()?.let { onDragFinished(it, content) }
                }
            })
        }
        prepare(hotseats, "onWallpaperColorChanged", object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                if (callback.getThrowable() != null) return
                findBackground(callback.getThisObject() as FrameLayout)?.refreshAppearance()
            }
        })
        prepare(hotseats, "getUserPresentAnimationChildList", object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                if (callback.getThrowable() != null) return
                val children = callback.getResult() as? List<*> ?: return
                val background = findBackground(callback.getThisObject() as FrameLayout) ?: return
                callback.setResult(userPresentChildren(children, background))
            }
        })
        if (config.opacity > 0) {
            // Only the two native editing AnimationSets receive an owner. Read their already-computed
            // transformation; do not invoke getTransformation again or replace a native listener.
            prepare(AnimationSet::class.java, "getTransformation",
                Long::class.javaPrimitiveType, Transformation::class.java, editingTransformationHook())
            prepare(launcher, "showBottomAreaEditPanelIfNeed", View::class.java,
                Boolean::class.javaPrimitiveType, object : MethodHook() {
                    override fun after(callback: AfterHookCallback) {
                        if (callback.getThrowable() != null) return
                        val host = callback.getArg(0) as? FrameLayout ?: return
                        if (!hotseats.isInstance(host)) return
                        val animation = host.animation as? AnimationSet ?: return
                        findBackground(host)?.bindEditingAnimation(animation, callback.getArg(1) != true)
                    }
                })
        }
        prepare(hotseats, "updateContent", object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                if (callback.getThrowable() != null) return
                val host = callback.getThisObject() as FrameLayout
                attach(host, config, material, content, visualEvents = visualEvents)
            }
        })
        check(installDockBackgroundHooks(hooks)) { "Dock background Hook group incomplete" }
    }

    internal fun hotseatMeasureHook() = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = try { chain.proceed() } catch (error: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(error)
                throw error
            }
            ModuleHelper.guarded { findBackground(chain.thisObject as FrameLayout)?.scheduleGeometry() }
            return result
        }
    }

    internal fun contentLayoutHook() = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = try { chain.proceed() } catch (error: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(error)
                throw error
            }
            ModuleHelper.guarded { onContentLayout(chain.thisObject as View) }
            return result
        }
    }

    internal fun editingTransformationHook() = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            val owners = editingOwners
            if (owners.isEmpty()) return result
            ModuleHelper.guarded {
                val animation = chain.thisObject
                for (index in owners.indices) {
                    val entry = owners[index]
                    val target = entry.animation.get() ?: continue
                    if (target !== animation) continue
                    val owner = entry.owner.get() ?: return@guarded
                    val transformation = chain.getArg(1) as Transformation
                    owner.onEditingFrame(target, transformation.alpha, result == true, transformation.matrix)
                    return@guarded
                }
            }
            return result
        }
    }

    internal fun attach(host: FrameLayout, config: DockBackgroundConfig, material: Constructor<*>?,
        content: DockContentAccess? = null,
        visualEvents: Boolean = false,
    ) {
        if (config.fullWidth && !host.isAttachedToWindow) {
            if (XposedHelpers.getAdditionalInstanceField(host, FULL_WIDTH_OWNER) != null) return
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    XposedHelpers.removeAdditionalInstanceField(v, FULL_WIDTH_OWNER)
                    // This listener is also a value in the global weak-key field table.
                    // Use the callback owner so its value cannot keep its own key alive.
                    attach(v as FrameLayout, config, material, content, visualEvents)
                }
                override fun onViewDetachedFromWindow(v: View) = Unit
            }
            XposedHelpers.setAdditionalInstanceField(host, FULL_WIDTH_OWNER, listener)
            host.addOnAttachStateChangeListener(listener)
            return
        }
        findBackground(host)?.let {
            it.scheduleGeometry()
            return
        }
        val container = if (config.fullWidth) host.rootView as? FrameLayout ?: host else host
        val full = config.fullWidth && container !== host
        val decoration = DockBackgroundView(host.context, if (full) config else config.copy(fullWidth = false),
            material, content, if (full) host else null, visualEvents)
        val inset = ((if (config.sideInsetDp == 0) 14 else config.sideInsetDp) * host.resources.displayMetrics.density).roundToInt()
        val layer = if (full) DockFullWidthContainer(host.context).also {
            container.addView(it, 0, FrameLayout.LayoutParams(-1, -1))
        } else container
        layer.addView(decoration, 0, FrameLayout.LayoutParams(-1, -1).apply {
            leftMargin = if (full) 0 else inset
            rightMargin = if (full) 0 else inset
        })
        if (full) {
            XposedHelpers.setAdditionalInstanceField(host, FULL_WIDTH_OWNER, decoration)
            host.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    XposedHelpers.removeAdditionalInstanceField(v, FULL_WIDTH_OWNER)
                    (decoration.parent as? ViewGroup)?.removeView(decoration)
                    (layer.parent as? ViewGroup)?.removeView(layer)
                    v.removeOnAttachStateChangeListener(this)
                }
            })
        }
    }

    internal fun userPresentChildren(children: List<*>, background: DockBackgroundView): List<*> {
        if (!background.isAttachedToWindow || background in children) return children
        // The ROM prepares, animates and resets arbitrary Views from this list. Keep native
        // icon owners untouched and let that same controller own the decoration's springs.
        background.prepareNativeAnimation()
        return ArrayList<Any?>(children.size + 1).apply { addAll(children); add(background) }
    }

    private fun findBackground(host: FrameLayout): DockBackgroundView? {
        (XposedHelpers.getAdditionalInstanceField(host, FULL_WIDTH_OWNER) as? DockBackgroundView)?.let { return it }
        for (index in 0 until host.childCount) {
            val child = host.getChildAt(index)
            if (child is DockBackgroundView) return child
        }
        return null
    }

    internal fun onContentLayout(row: View) {
        (row.parent as? FrameLayout)?.let { findBackground(it)?.scheduleGeometry() }
    }

    internal fun onDragFinished(controller: Any, content: DockContentAccess) {
        // endDrag clears mDragging before notifying native listeners, including cancellation.
        // Resolve the current HotSeats from its owner, so rebuilt rows cannot retain stale owners.
        content.host(controller)?.let { findBackground(it)?.scheduleGeometry() }
    }

    @Synchronized internal fun setEditingOwner(animation: AnimationSet, owner: DockBackgroundView?) {
        editingOwners = buildList {
            for (entry in editingOwners) {
                val target = entry.animation.get()
                if (target != null && target !== animation && entry.owner.get() != null) add(entry)
            }
            if (owner != null) add(EditingOwner(animation, owner))
        }
    }

    @Synchronized internal fun clearEditingOwner(animation: AnimationSet, owner: DockBackgroundView) {
        editingOwners = editingOwners.filter { entry ->
            val target = entry.animation.get()
            val current = entry.owner.get()
            target != null && current != null && (target !== animation || current !== owner)
        }
    }
}

/** A failed optional mesh leaves no background/frame Hook installed. */
internal fun installDockBackgroundHooks(hooks: List<Pair<Method, MethodHook>>): Boolean {
    val handles = ArrayList<CustomMethodUnhooker>(hooks.size)
    var complete = false
    var failure: Throwable? = null
    try {
        for ((method, callback) in hooks) handles.add(ModuleHelper.hookMethod(method, callback) ?: return false)
        complete = true
        return true
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        if (!complete) {
            var cleanupFailure = failure
            for (handle in handles.asReversed()) {
                try { ModuleHelper.guarded { handle.unhook() } }
                catch (error: Throwable) {
                    val previous = cleanupFailure
                    if (previous == null) cleanupFailure = error
                    else if (previous !== error) previous.addSuppressed(error)
                }
            }
            if (failure == null) cleanupFailure?.let { throw it }
        }
    }
}

/** Per-launcher metadata. Only layout events read native views; no icon snapshots or frame polling. */
internal class DockContentAccess(hotseatType: Class<*>, private val iconType: Class<*>) {
    private val content = XposedHelpers.findField(hotseatType, "mContent")
    private val icon = XposedHelpers.findField(iconType, "mIconImageView")
    private val title = XposedHelpers.findField(iconType, "mTitleContainer")
    private val hiddenTitle = XposedHelpers.findField(iconType, "mIsHideTitle")
    private val dragController = XposedHelpers.findFieldIfExists(hotseatType, "mDragController")
    private val dragging = dragController?.type?.let { XposedHelpers.findFieldIfExists(it, "mDragging") }
    private val launcher = dragController?.type?.let { XposedHelpers.findFieldIfExists(it, "mLauncher") }
    private val hotseats = launcher?.type?.let { XposedHelpers.findFieldIfExists(it, "mHotSeats") }
    val dragControllerType: Class<*>? get() = if (hotseats != null) dragController?.type else null

    fun host(controller: Any): FrameLayout? = launcher?.get(controller)?.let { hotseats?.get(it) as? FrameLayout }
    fun hostFromLauncher(owner: Any): FrameLayout? = hotseats?.get(owner) as? FrameLayout

    fun isDragging(host: ViewGroup): Boolean {
        val controller = dragController?.get(host) ?: return false
        return dragging?.getBoolean(controller) == true
    }

    fun content(host: ViewGroup): ViewGroup? =
        (content.get(host) as? ViewGroup)?.takeIf { it.parent === host }

    fun bounds(host: ViewGroup, row: ViewGroup, out: Rect, scratch: Rect): Int {
        out.setEmpty()
        return collect(host, row, out, scratch)
    }

    private fun collect(host: ViewGroup, parent: ViewGroup, out: Rect, scratch: Rect): Int {
        var count = 0
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.visibility == View.GONE) continue
            if (iconType.isInstance(child)) {
                val image = icon.get(child) as? View
                if (image == null || image.visibility == View.GONE || image.width <= 0 || image.height <= 0) continue
                image.getDrawingRect(scratch)
                host.offsetDescendantRectToMyCoords(image, scratch)
                if (scratch.right <= 0 || scratch.left >= host.width || scratch.bottom <= 0 || scratch.top >= host.height) continue
                count++
                out.union(scratch)
                if (!hiddenTitle.getBoolean(child)) include(host, title.get(child) as? View, out, scratch)
            } else if (child is ViewGroup) count += collect(host, child, out, scratch)
        }
        return count
    }

    private fun include(host: ViewGroup, view: View?, out: Rect, scratch: Rect) {
        // Floating launch/return icons temporarily hide their native source, without relayout.
        if (view == null || view.visibility == View.GONE || view.width <= 0 || view.height <= 0) return
        view.getDrawingRect(scratch)
        host.offsetDescendantRectToMyCoords(view, scratch)
        if (scratch.right > 0 && scratch.left < host.width) out.union(scratch)
    }

    companion object {
        fun resolve(loader: ClassLoader): DockContentAccess? = try {
            DockContentAccess(
                XposedHelpers.findClass("com.miui.home.launcher.hotseats.HotSeats", loader),
                XposedHelpers.findClass("com.miui.home.launcher.ItemIcon", loader),
            )
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            XposedHelpers.log("Dock background: native alignment unavailable", error)
            null
        }
    }
}

internal data class DockBackgroundConfig(
    val material: Int = 0,
    val opacity: Int = 90,
    val radiusDp: Int = 0,
    val sideInsetDp: Int = 0,
    val sizeAdjustmentDp: Int = 0,
    val fullWidth: Boolean = false,
) {
    companion object {
        fun read(prefs: PrefMap) = DockBackgroundConfig(
            material = if (prefs.getStringAsInt("launcher_dock_background_material", 0) == 2) 2 else 0,
            opacity = prefs.getInt("launcher_dock_background_opacity", 90).coerceIn(0, 100),
            radiusDp = prefs.getInt("launcher_dock_background_radius", 0).coerceIn(0, 80),
            sideInsetDp = prefs.getInt("launcher_dock_background_inset", 0).coerceIn(0, 40),
            // Retain the key and raw encoding. Positive displayed values now expand all four edges.
            sizeAdjustmentDp = prefs.getInt("launcher_dock_background_shrink", 17).coerceIn(0, 34) - 17,
            fullWidth = prefs.getBoolean("launcher_dock_background_full_width"),
        )
    }
}

/** The ROM owns rendering; layout and native scene changes are the only business update sources. */
internal class DockBackgroundView(
    context: Context,
    private val config: DockBackgroundConfig,
    private val materialConstructor: Constructor<*>?,
    private val contentAccess: DockContentAccess? = null,
    layoutHost: FrameLayout? = null,
    private val visualEvents: Boolean = false,
) : FrameLayout(context), ViewTreeObserver.OnPreDrawListener, View.OnLayoutChangeListener {
    private val fullWidthHost = WeakReference(layoutHost)
    private val tint = DockBackgroundTint()
    private var nativeMaterial: View? = null
    private var materialEnabled = false
    private var materialUnavailable = false
    private var opacityDrawable: Drawable? = null
    private var appliedDrawableAlpha = -1
    private var googleBlur = false
    private var editingAnimation: AnimationSet? = null
    private var editingAlpha = 1f
    private val editingMatrix = Matrix()
    private var screenRadius = 0f
    private var panelRadius = 20f * resources.displayMetrics.density
    private val contentBounds = Rect()
    private val scratch = Rect()
    private var pendingObserver: ViewTreeObserver? = null
    private var geometryPending = false
    private var observedContent: ViewGroup? = null
    private var contentFailed = false
    private var navigationInsets = Insets.NONE
    private val safeBounds = Rect()
    private val targetBounds = Rect()
    private var lastIconCount = -1
    private var lastHostWidth = 0
    private var lastHostHeight = 0
    private var pendingVisualWidth = 0f
    private var widthAnimation: ValueAnimator? = null
    private var widthStartScale = 1f
    private var visualReady = false
    private var sceneShown = true
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private var observingBlur = false
    private val blurChanged = Consumer<Boolean> { ModuleHelper.guarded { materialUnavailable = false; refreshBlurPolicy() } }
    private val powerChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { ModuleHelper.guarded { materialUnavailable = false; refreshBlurPolicy() } }
    }

    init {
        isClickable = false
        isLongClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        foreground = tint
        setWillNotDraw(true)
        visualReady = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ModuleHelper.guarded {
            readScreenRadius(rootWindowInsets)
            navigationInsets = rootWindowInsets?.getInsets(WindowInsets.Type.navigationBars()) ?: Insets.NONE
            materialUnavailable = false
            observeBlurPolicy(true)
            installMaterial()
            syncNativeMaterialVisibility(isShown)
            refreshAppearance()
            scheduleGeometry()
        }
    }

    private fun installMaterial() {
        if (nativeMaterial != null || materialUnavailable || materialConstructor == null || config.material != 2 || config.opacity == 0 || !blurAllowed()) return
        var candidate: View? = null
        try {
            candidate = materialConstructor.newInstance(context) as View
            googleBlur = XposedHelpers.callMethod(candidate, "isGoogleBlurSupported") == true
            val supported = googleBlur || XposedHelpers.callMethod(candidate, "isBackdropBlurSupported") == true
            if (!supported) { materialUnavailable = true; return }
            candidate.isClickable = false
            candidate.isFocusable = false
            candidate.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            candidate.alpha = config.opacity / 100f
            // This decoration already owns its rounded contour. HyperOS SmoothCorner
            // snapshots a null SkSurface when Recents draws this extra native material.
            // Disable only our material's vendor corner pass, never the host/global flag.
            XposedHelpers.findMethodExactIfExists(View::class.java, "setSmoothCornerEnabled",
                Boolean::class.javaPrimitiveType)?.invoke(candidate, false)
            candidate.visibility = INVISIBLE
            XposedHelpers.callMethod(candidate, "setCornerRadius", tint.radius)
            XposedHelpers.callMethod(candidate, "setBlurEnabled", false)
            candidate.addOnLayoutChangeListener(this)
            addView(candidate, LayoutParams(-1, -1))
            nativeMaterial = candidate
            materialEnabled = false
            candidate.background?.let { applyNativeOpacity(it, 0) }
        } catch (error: Throwable) {
            materialUnavailable = true
            googleBlur = false
            materialEnabled = false
            try {
                if (candidate?.parent === this) removeView(candidate)
            } finally {
                FatalErrors.unwrapAndRethrowIfFatal(error)
            }
            XposedHelpers.log("Dock background: native material unavailable", error)
        }
    }

    private fun blurRadius(): Int = (35 * resources.displayMetrics.density).roundToInt()

    private fun configureGlassBlend(material: View) {
        if (config.material != 2 || !googleBlur) return
        try {
            XposedHelpers.callMethod(XposedHelpers.getObjectField(material, "mBlurLayerHolder"), "setEnable", false)
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            XposedHelpers.log("Dock background: using native glass blend", error)
        }
    }

    override fun onDetachedFromWindow() {
        try {
            ModuleHelper.guarded {
                cancelWidthAnimation()
                widthAnimation?.removeAllUpdateListeners()
                widthAnimation = null
                lastIconCount = -1
                cancelGeometry()
                observeContent(null)
                clearEditingAnimation()
                observeBlurPolicy(false)
                retireMaterial()
            }
        } finally {
            super.onDetachedFromWindow()
        }
    }

    private fun retireMaterial() {
        val material = nativeMaterial ?: return
        material.removeOnLayoutChangeListener(this)
        val enabled = materialEnabled
        nativeMaterial = null
        materialEnabled = false
        googleBlur = false
        opacityDrawable = null
        appliedDrawableAlpha = -1
        ModuleHelper.guarded {
            material.background?.alpha = 0
            if (enabled) XposedHelpers.callMethod(material, "setBlurEnabled", false)
        }
        try {
            removeView(material)
        } finally {
            material.background = null
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ModuleHelper.guarded {
            cancelWidthAnimation()
            lastIconCount = -1
            screenRadius = 0f
            readScreenRadius(rootWindowInsets)
            refreshAppearance()
            scheduleGeometry()
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        ModuleHelper.guarded {
            val previousRadius = screenRadius
            readScreenRadius(insets)
            val navigation = insets.getInsets(WindowInsets.Type.navigationBars())
            val changed = navigation != navigationInsets || screenRadius != previousRadius
            if (!changed) return@guarded
            if (navigation != navigationInsets) cancelWidthAnimation()
            navigationInsets = navigation
            updateGeometry()
            scheduleGeometry()
        }
        return super.onApplyWindowInsets(insets)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ModuleHelper.guarded { updateGeometry(); scheduleGeometry() }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        ModuleHelper.guarded {
            if (!isVisible) cancelWidthAnimation()
            if (isVisible) scheduleGeometry() else if (editingAnimation == null) cancelGeometry()
            val drawingAnimation = editingAnimation != null
            observeBlurPolicy(isVisible || drawingAnimation)
            if (isVisible || drawingAnimation) refreshBlurPolicy()
            if (!isVisible && drawingAnimation) nativeMaterial?.onVisibilityAggregated(true)
            syncNativeMaterialVisibility(isVisible || drawingAnimation)
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (!isAttachedToWindow) return
        ModuleHelper.guarded {
            // Keyguard can replace the window surface without changing View visibility.
            // Retire the old blur region while hidden and restore it once on return.
            if (visibility != VISIBLE) {
                observeBlurPolicy(false)
                cancelWidthAnimation()
                cancelGeometry()
                syncNativeMaterialVisibility(false)
            } else {
                observeBlurPolicy(isShown)
                refreshBlurPolicy()
                syncNativeMaterialVisibility(isShown || editingAnimation != null)
                scheduleGeometry()
            }
        }
    }

    internal fun bindEditingAnimation(animation: AnimationSet, showing: Boolean) {
        cancelWidthAnimation()
        if ((!config.fullWidth && !googleBlur) || !isAttachedToWindow || !sceneShown) return
        if (editingAnimation === animation && animation.startTime != -1L) return
        clearEditingAnimation()
        editingAnimation = animation
        editingAlpha = if (showing) 0f else 1f
        LauncherDockBackground.setEditingOwner(animation, this)
        nativeMaterial?.onVisibilityAggregated(true)
        syncNativeMaterialVisibility(true)
        observeFrames()
    }

    internal fun onEditingFrame(animation: AnimationSet, alpha: Float, running: Boolean,
        matrix: android.graphics.Matrix? = null,
    ) {
        if (editingAnimation !== animation || !isAttachedToWindow) return
        editingAlpha = alpha.coerceIn(0f, 1f)
        if (matrix == null) editingMatrix.reset() else editingMatrix.set(matrix)
        syncFullWidthHost()
        syncNativeOpacity()
        // false is returned while ViewGroup is still drawing the native terminal
        // transformation. Keep that matrix/alpha through this draw; retire only after
        // the host clears/replaces its animation, observed by the next pre-draw.
        if (!running) postInvalidateOnAnimation()
    }

    private fun clearEditingAnimation() {
        editingAnimation?.let { LauncherDockBackground.clearEditingOwner(it, this) }
        editingAnimation = null
        editingAlpha = 1f
        editingMatrix.reset()
    }

    private fun finishEditingAnimation() {
        clearEditingAnimation()
        syncFullWidthHost()
        nativeMaterial?.onVisibilityAggregated(isShown)
        syncNativeMaterialVisibility(isShown)
        observeBlurPolicy(isShown)
    }

    private fun blurAllowed(): Boolean = powerManager?.isPowerSaveMode != true &&
        windowManager?.isCrossWindowBlurEnabled == true

    private fun refreshBlurPolicy() {
        if (!isAttachedToWindow) return
        if (!blurAllowed()) retireMaterial()
        else if (windowVisibility == VISIBLE && (isShown || editingAnimation != null)) installMaterial()
        syncNativeMaterialVisibility(isShown || editingAnimation != null)
        scheduleVisualSync()
    }

    private fun observeBlurPolicy(visible: Boolean) {
        val needed = visible && isAttachedToWindow && windowVisibility == VISIBLE && config.opacity > 0 && config.material == 2 && materialConstructor != null
        if (needed == observingBlur) return
        if (needed) {
            context.registerReceiver(powerChanged, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), Context.RECEIVER_NOT_EXPORTED)
            try {
                windowManager?.addCrossWindowBlurEnabledListener(context.mainExecutor, blurChanged)
                observingBlur = true
            } catch (error: Throwable) {
                context.unregisterReceiver(powerChanged)
                throw error
            }
        } else {
            observingBlur = false
            try { windowManager?.removeCrossWindowBlurEnabledListener(blurChanged) }
            finally { context.unregisterReceiver(powerChanged) }
        }
    }

    internal fun scheduleVisualSync() {
        if (visualReady && isAttachedToWindow && windowVisibility == VISIBLE && (isShown || editingAnimation != null)) observeFrames()
    }

    internal fun setSceneVisible(visible: Boolean) {
        if (sceneShown == visible) return
        sceneShown = visible
        if (!visible) {
            clearEditingAnimation()
            cancelWidthAnimation()
            cancelGeometry()
            observeBlurPolicy(false)
            syncNativeMaterialVisibility(false)
        }
        visibility = if (visible) VISIBLE else INVISIBLE
        if (visible && isAttachedToWindow) {
            refreshBlurPolicy()
            scheduleGeometry()
        }
    }

    override fun setAlpha(value: Float) {
        val changed = alpha != value
        super.setAlpha(value)
        if (changed) scheduleVisualSync()
    }

    override fun setScaleX(value: Float) {
        val changed = scaleX != value
        super.setScaleX(value)
        if (changed) scheduleVisualSync()
    }

    override fun setScaleY(value: Float) {
        val changed = scaleY != value
        super.setScaleY(value)
        if (changed) scheduleVisualSync()
    }

    override fun setTranslationX(value: Float) {
        val changed = translationX != value
        super.setTranslationX(value)
        if (changed) scheduleVisualSync()
    }

    override fun setTranslationY(value: Float) {
        val changed = translationY != value
        super.setTranslationY(value)
        if (changed) scheduleVisualSync()
    }

    private fun syncNativeMaterialVisibility(requestedVisible: Boolean, frameOpacity: Int = -1,
        frameReady: Boolean = false,
    ) {
        val material = nativeMaterial ?: return
        // Hiding is immediate. Positive lifecycle events only request a finite native frame;
        // activation belongs after that frame has settled layout, contour and host transform.
        if (requestedVisible && !frameReady) {
            // A native editing handoff may immediately lower the alpha of an existing
            // drawable, but cannot activate a new material before geometry is ready.
            if (materialEnabled && editingAnimation != null) syncNativeOpacity()
            scheduleVisualSync()
            return
        }
        try {
            val isVisible = requestedVisible && sceneShown && windowVisibility == VISIBLE &&
                width > 0 && height > 0 && material.width == width && material.height == height
            // Retire the Google region before the SDK clears its background; disable the MIUI binder too.
            if (!isVisible) material.background?.let { applyNativeOpacity(it, 0) }
            if (materialEnabled != isVisible) {
                if (isVisible) {
                    // setBlurEnabled(false) also resets the SDK's saved radius to zero.
                    XposedHelpers.callMethod(material, "setBlurRadius", blurRadius())
                }
                XposedHelpers.callMethod(material, "setBlurEnabled", isVisible)
                materialEnabled = isVisible
                if (isVisible) configureGlassBlend(material)
            }
            if (isVisible) material.background?.let { drawable ->
                val alpha = if (googleBlur) {
                    if (frameOpacity >= 0) frameOpacity else nativeBlurAlpha()
                } else (config.opacity * 2.55f).roundToInt()
                applyNativeOpacity(drawable, alpha, material.width, material.height)
            }
            // Publish visibility last, with the native drawable already using this frame's bounds/alpha.
            val visibility = if (isVisible) VISIBLE else INVISIBLE
            if (material.visibility != visibility) material.visibility = visibility
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            retireMaterial()
            XposedHelpers.log("Dock background: native material retired after visibility failure", error)
        }
    }

    fun scheduleGeometry() {
        if (!isAttachedToWindow || !isShown) return
        geometryPending = true
        observeFrames()
    }

    private fun observeFrames() {
        if (pendingObserver != null) return
        viewTreeObserver.let { observer ->
            pendingObserver = observer
            observer.addOnPreDrawListener(this)
        }
    }

    override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int,
        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int,
    ) {
        // A policy/visibility callback may create the child after the parent's layout.
        // Its own settled bounds wake one commit, even if the earlier PreDraw retired.
        if (view === nativeMaterial) scheduleVisualSync() else scheduleGeometry()
    }

    private fun cancelGeometry() {
        pendingObserver?.let { if (it.isAlive) it.removeOnPreDrawListener(this) }
        pendingObserver = null
        geometryPending = false
    }

    private fun cancelWidthAnimation() {
        val ownsScale = pendingVisualWidth > 0f || widthAnimation?.isStarted == true
        pendingVisualWidth = 0f
        widthAnimation?.cancel()
        if (ownsScale) scaleX = 1f
    }

    internal fun prepareNativeAnimation() { cancelWidthAnimation() }

    private fun syncFullWidthHost() {
        val layer = parent as? DockFullWidthContainer ?: return
        fullWidthHost.get()?.let { layer.sync(it, editingMatrix, editingAlpha, editingAnimation != null) }
    }

    private fun animatePendingWidth() {
        val from = pendingVisualWidth
        pendingVisualWidth = 0f
        if (from <= 0f || width <= 0 || !ValueAnimator.areAnimatorsEnabled()) return
        widthStartScale = from / width
        if (widthStartScale == 1f) return
        val animator = widthAnimation ?: ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180L
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                ModuleHelper.guarded { scaleX = widthStartScale + (1f - widthStartScale) * it.animatedFraction }
            }
        }.also { widthAnimation = it }
        // Layout, icon bounds and material paths are final. Only this decoration's RenderNode
        // changes during the short easing; native icons and their touch areas keep their layout.
        scaleX = widthStartScale
        animator.setCurrentFraction(0f)
        animator.start()
    }

    private fun nativeBlurAlpha(): Int {
        var opacity = config.opacity / 100f * if (config.fullWidth) 1f else editingAlpha
        var ancestor: View? = this
        while (ancestor != null) {
            opacity *= ancestor.alpha
            ancestor = ancestor.parent as? View
        }
        return (255 * opacity).roundToInt().coerceIn(0, 255)
    }

    private fun syncNativeOpacity(frameOpacity: Int = -1) {
        if (!googleBlur || !materialEnabled) return
        try {
            val material = nativeMaterial ?: return
            val drawable = material.background ?: return
            val alpha = if (frameOpacity >= 0) frameOpacity else nativeBlurAlpha()
            applyNativeOpacity(drawable, alpha, material.width, material.height)
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            retireMaterial()
            XposedHelpers.log("Dock background: native material retired after opacity failure", error)
        }
    }

    private fun applyNativeOpacity(drawable: Drawable, alpha: Int, width: Int = -1, height: Int = -1) {
        // A14 BackgroundBlurDrawable.setAlpha writes mAlpha but inherits Drawable.getAlpha,
        // which returns 255. Read-back therefore cannot tell whether this region is transparent.
        // Track only this owner's last write, scoped to the current native drawable identity.
        val previous = if (opacityDrawable === drawable) appliedDrawableAlpha else -1
        if (previous != alpha) {
            drawable.alpha = alpha
            opacityDrawable = drawable
            appliedDrawableAlpha = alpha
        }
        val bounds = drawable.bounds
        val sized = width > 0 && height > 0
        val changed = sized && (bounds.left != 0 || bounds.top != 0 || bounds.right != width || bounds.bottom != height)
        if (alpha > 0 && (previous <= 0 || changed)) {
            if (sized) drawable.setBounds(0, 0, width, height)
            else drawable.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
        }
    }

    override fun onPreDraw(): Boolean {
        var ready = true
        ModuleHelper.guarded {
            if (editingAnimation != null && (fullWidthHost.get() ?: parent as? View)?.animation !== editingAnimation)
                finishEditingAnimation()
            if (config.opacity > 0) syncFullWidthHost()
            val host = fullWidthHost.get() ?: parent as? FrameLayout ?: return@guarded
            ready = updateFrameGeometry(host)
            if (ready && !geometryPending) {
                val opacity = if (config.opacity > 0 && nativeMaterial != null) nativeBlurAlpha() else 0
                val drawingAnimation = editingAnimation != null
                syncNativeMaterialVisibility(drawingAnimation || isShown && opacity > 0, opacity, frameReady = true)
            }
        }
        if (!geometryPending && (visualEvents && editingAnimation == null || config.opacity == 0 ||
                !config.fullWidth && !googleBlur)) cancelGeometry()
        return ready
    }

    /** Geometry changes settle before the native material can become visible in this frame. */
    private fun updateFrameGeometry(host: FrameLayout): Boolean {
        var ready = true
        if (!geometryPending) return true
        geometryPending = false
        if (!isShown || host.height <= 0) return true
        val dragging = contentAccess?.isDragging(host) == true
        val sceneActive = editingAnimation != null || host.animation?.hasEnded() == false ||
            widthAnimation?.isStarted != true && (alpha != 1f || scaleX != 1f || scaleY != 1f ||
                translationX != 0f || translationY != 0f)
        val sameHostSize = host.width == lastHostWidth && host.height == lastHostHeight
        if (dragging || sceneActive || !sameHostSize) cancelWidthAnimation()
        contentBounds.setEmpty()
        var rowAvailable = false
        var iconCount = -1
        if (!config.fullWidth && !dragging && !contentFailed && contentAccess != null) try {
            val row = contentAccess.content(host)
            observeContent(row)
            if (row != null) {
                iconCount = contentAccess.bounds(host, row, contentBounds, scratch)
                rowAvailable = true
            }
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            contentFailed = true
            observeContent(null)
            contentBounds.setEmpty()
            XposedHelpers.log("Dock background: native bounds unavailable", error)
        }
        val root = rootView as? ViewGroup ?: return true
        // Layout coordinates exclude the launcher's scale/translation animations.
        scratch.set(0, 0, host.width, host.height)
        root.offsetDescendantRectToMyCoords(host, scratch)
        val density = resources.displayMetrics.density
        val params = layoutParams as? LayoutParams
        // An empty native row has no panel; keep the View attached for the next native layout.
        safeBounds.set(maxOf(host.paddingLeft, navigationInsets.left - scratch.left),
            maxOf(host.paddingTop, navigationInsets.top - scratch.top),
            minOf(host.width - host.paddingRight, root.width - navigationInsets.right - scratch.left),
            minOf(host.height - host.paddingBottom, root.height - navigationInsets.bottom - scratch.top))
        // Also honor every clipping ancestor in layout space. Do not use animation-transformed
        // global visible rects to feed geometry back into native launch/return transitions.
        var ancestor = host.parent as? ViewGroup
        while (ancestor != null) {
            if (ancestor.clipChildren) {
                targetBounds.set(if (ancestor.clipToPadding) ancestor.paddingLeft else 0,
                    if (ancestor.clipToPadding) ancestor.paddingTop else 0,
                    ancestor.width - if (ancestor.clipToPadding) ancestor.paddingRight else 0,
                    ancestor.height - if (ancestor.clipToPadding) ancestor.paddingBottom else 0)
                root.offsetDescendantRectToMyCoords(ancestor, targetBounds)
                targetBounds.offset(-scratch.left, -scratch.top)
                if (!safeBounds.intersect(targetBounds)) safeBounds.setEmpty()
            }
            if (ancestor === root) break
            ancestor = ancestor.parent as? ViewGroup
        }
        if (config.fullWidth) {
            // A separate decoration under the root owns the strip; no native icon layout,
            // ancestor clipping or touch bounds are changed. Include the navigation area.
            val top = scratch.top + host.paddingTop
            targetBounds.set(0, top.coerceIn(0, root.height), root.width, root.height)
        } else if (dragging && params != null) {
            targetBounds.set(left, top, right, bottom)
            DockBackgroundGeometry.constrain(targetBounds, safeBounds, targetBounds)
        } else if (!contentBounds.isEmpty) {
            DockBackgroundGeometry.fit(contentBounds, safeBounds,
                (config.sideInsetDp * density).roundToInt(), config.sizeAdjustmentDp * density, targetBounds)
        } else if (rowAvailable || safeBounds.isEmpty) {
            targetBounds.setEmpty()
        } else {
            // Native metadata unavailable: retain the bounded legacy panel fallback.
            val inset = ((if (config.sideInsetDp == 0) 14 else config.sideInsetDp) * density).roundToInt()
            targetBounds.set(safeBounds)
            targetBounds.inset(minOf(inset, (safeBounds.width() - 1) / 2), 0)
            DockBackgroundGeometry.constrain(targetBounds, safeBounds, targetBounds)
        }
        val panelWidth = targetBounds.width().coerceAtLeast(0)
        val panelHeight = targetBounds.height().coerceAtLeast(0)
        val leftInset = targetBounds.left - if (config.fullWidth) 0 else host.paddingLeft
        val rightInset = if (config.fullWidth) 0 else host.width - host.paddingRight - targetBounds.right
        val topInset = targetBounds.top - if (config.fullWidth) 0 else host.paddingTop
        val bottomInset = if (config.fullWidth) 0 else host.height - host.paddingBottom - targetBounds.bottom
        val countChanged = rowAvailable && lastIconCount > 0 && iconCount > 0 && iconCount != lastIconCount
        val canAnimate = !config.fullWidth && countChanged && sameHostSize && !dragging && !sceneActive && ValueAnimator.areAnimatorsEnabled()
        if (params != null && (params.width != panelWidth || params.height != panelHeight || params.leftMargin != leftInset || params.rightMargin != rightInset ||
                params.topMargin != topInset || params.bottomMargin != bottomInset)) {
            val targetWidth = panelWidth
            val visualWidth = width * scaleX
            if (canAnimate && width > 0 && targetWidth > 0 && targetWidth != width) {
                cancelWidthAnimation()
                pendingVisualWidth = visualWidth
            } else if (pendingVisualWidth == 0f || targetWidth <= 0) cancelWidthAnimation()
            params.width = panelWidth
            params.height = panelHeight
            params.leftMargin = leftInset
            params.rightMargin = rightInset
            params.topMargin = topInset
            params.bottomMargin = bottomInset
            layoutParams = params
            ready = false // apply the new geometry before presenting it
            geometryPending = true
        }
        if (!dragging) lastIconCount = iconCount
        lastHostWidth = host.width
        lastHostHeight = host.height
        if (ready && !sceneActive) {
            pivotX = width * 0.5f
            pivotY = height * 0.5f
            if (!dragging && !sceneActive) animatePendingWidth()
        }
        updateGeometry()
        return ready
    }

    private fun observeContent(row: ViewGroup?) {
        if (observedContent === row) return
        observedContent?.removeOnLayoutChangeListener(this)
        observedContent = row
        row?.addOnLayoutChangeListener(this)
    }

    private fun readScreenRadius(insets: WindowInsets?) {
        val panelId = resources.getIdentifier("enforced_rounded_corner_max_radius", "dimen", "com.miui.home")
        panelRadius = if (panelId != 0) resources.getDimension(panelId) else 20f * resources.displayMetrics.density
        // Display corners survive window/Insets clipping and follow display resolution/rotation.
        // Only attach, configuration and Insets callbacks query Display; never a rendered frame.
        val displayLeft = display?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0
        val displayRight = display?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0
        val left = insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0
        val right = insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0
        val radius = nativeCornerRadius(displayLeft, displayRight).takeIf { it > 0 }
            ?: nativeCornerRadius(left, right)
        if (radius > 0f) screenRadius = radius
        // Insets can briefly omit corners while window focus / floating-window state changes.
        if (screenRadius > 0f) return
        val id = resources.getIdentifier("rounded_corner_radius_bottom", "dimen", "android")
        if (id != 0) {
            val adjustment = resources.getIdentifier("rounded_corner_radius_bottom_adjustment", "dimen", "android")
            screenRadius = (resources.getDimension(id) - if (adjustment != 0) resources.getDimension(adjustment) else 0f)
                .coerceAtLeast(0f)
        }
        if (screenRadius <= 0f) {
            val fallback = resources.getIdentifier("rounded_corner_radius", "dimen", "android")
            if (fallback != 0) {
                val adjustment = resources.getIdentifier("rounded_corner_radius_adjustment", "dimen", "android")
                screenRadius = (resources.getDimension(fallback) - if (adjustment != 0) resources.getDimension(adjustment) else 0f)
                    .coerceAtLeast(0f)
            }
        }
    }

    private fun nativeCornerRadius(left: Int, right: Int): Float = when {
        left <= 0 -> right.coerceAtLeast(0)
        right <= 0 -> left
        else -> minOf(left, right)
    }.toFloat()

    fun refreshAppearance() {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val wallpaper = XposedHelpers.findClassIfExists("com.miui.home.launcher.WallpaperUtils", context.classLoader)
        val lightWallpaper = if (wallpaper == null) false else try {
            XposedHelpers.callStaticMethod(wallpaper, "hasAppliedLightWallpaper") == true
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            false
        }
        tint.color = if (night || lightWallpaper) Color.rgb(24, 26, 30) else Color.WHITE
        tint.alpha = (config.opacity * 0.85f).roundToInt()
        updateGeometry()
        scheduleGeometry()
    }

    private fun updateGeometry() {
        val density = resources.displayMetrics.density
        val fitted = if (config.fullWidth) 0f else DockCornerGeometry.radius(screenRadius, config.radiusDp * density,
            panelRadius, min(width, height) * 0.5f)
        if (tint.radius == fitted) return
        tint.radius = fitted
        nativeMaterial?.let {
            XposedHelpers.callMethod(it, "setCornerRadius", tint.radius)
            it.invalidateOutline()
        }
    }
}

/** Cached geometry; color/alpha changes never rebuild a path. */
internal class DockBackgroundTint : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.TRANSPARENT }
    private val path = Path()
    private var explicitAlpha = false
    var color: Int
        get() = paint.color
        set(value) {
            if (paint.color != value) {
                val alpha = paint.alpha
                paint.color = value
                if (explicitAlpha) paint.alpha = alpha
                invalidateSelf()
            }
        }
    var radius = 0f
        set(value) { if (field != value) { field = value; rebuild(); invalidateSelf() } }

    override fun onBoundsChange(bounds: Rect) { rebuild() }
    private fun rebuild() {
        path.rewind()
        if (bounds.isEmpty) return
        DockCornerGeometry.outline(path, bounds.width().toFloat(), bounds.height().toFloat(), radius)
        path.offset(bounds.left.toFloat(), bounds.top.toFloat())
    }
    override fun draw(canvas: Canvas) {
        if (paint.alpha != 0) canvas.drawPath(path, paint)
    }
    override fun setAlpha(alpha: Int) {
        explicitAlpha = true
        if (paint.alpha != alpha) { paint.alpha = alpha; invalidateSelf() }
    }
    override fun getAlpha(): Int = paint.alpha
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Drawable requires this legacy API; use alpha for opacity.")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
