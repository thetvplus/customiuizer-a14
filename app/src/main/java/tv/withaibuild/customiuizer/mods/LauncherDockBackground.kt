package tv.withaibuild.customiuizer.mods

import android.animation.ValueAnimator
import android.app.WallpaperManager
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
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.Transformation
import android.widget.FrameLayout
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Constructor
import java.lang.ref.WeakReference
import kotlin.math.min
import kotlin.math.roundToInt

/** A non-interactive decoration owned by HotSeats, below its native icon content. */
object LauncherDockBackground {
    private const val EDITING_OWNER = "customiuizer_dock_editing_owner"
    private const val FULL_WIDTH_OWNER = "customiuizer_dock_full_width_owner"
    @JvmStatic
    fun install(lpparam: PackageReadyParam) {
        val config = DockBackgroundConfig.read(MainModule.mPrefs)
        val hotseats = XposedHelpers.findClass("com.miui.home.launcher.hotseats.HotSeats", lpparam.classLoader)
        check(FrameLayout::class.java.isAssignableFrom(hotseats))
        val content = DockContentAccess.resolve(lpparam.classLoader)
        val wallpaper = if (config.material == 3 && config.opacity > 0)
            DockWallpaperCapture.resolve(lpparam.classLoader) else null
        val material = XposedHelpers.findClassIfExists(
            "com.miui.blur.sdk.backdrop.BackdropBlurRelativeLayout", lpparam.classLoader
        )?.constructors?.firstOrNull { it.parameterTypes.contentEquals(arrayOf(Context::class.java)) }
        ModuleHelper.findAndHookMethod(hotseats, "updateContent", object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                val host = callback.getThisObject() as FrameLayout
                attach(host, config, material, content, wallpaper)
            }
        })
        ModuleHelper.findAndHookMethod(hotseats, "onMeasure", Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    findBackground(callback.getThisObject() as FrameLayout)?.scheduleGeometry()
                }
            })
        // Phone HotSeats lays out its children immediately without changing the row's frame.
        // An OnLayoutChangeListener on mContent cannot observe that layout.
        XposedHelpers.findClassIfExists("com.miui.home.launcher.hotseats.HotSeatsScreenViewContent",
            lpparam.classLoader)?.let { row ->
            ModuleHelper.findAndHookMethod(row, "onLayout", Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, object : MethodHook() {
                    override fun after(callback: AfterHookCallback) {
                        onContentLayout(callback.getThisObject() as View)
                    }
                })
        }
        content?.dragControllerType?.let { controller ->
            ModuleHelper.findAndHookMethod(controller, "endDrag", object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    callback.getThisObject()?.let { onDragFinished(it, content) }
                }
            })
        }
        ModuleHelper.findAndHookMethod(hotseats, "onWallpaperColorChanged", object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                findBackground(callback.getThisObject() as FrameLayout)?.refreshAppearance()
            }
        })
        ModuleHelper.findAndHookMethod(hotseats, "getUserPresentAnimationChildList", object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                if (callback.getThrowable() != null) return
                val children = callback.getResult() as? List<*> ?: return
                val background = findBackground(callback.getThisObject() as FrameLayout) ?: return
                callback.setResult(userPresentChildren(children, background))
            }
        })
        if (config.opacity == 0) return
        // Only the two native editing AnimationSets receive an owner. Read their already-computed
        // transformation; do not invoke getTransformation again or replace a native listener.
        ModuleHelper.findAndHookMethod(AnimationSet::class.java, "getTransformation",
            Long::class.javaPrimitiveType, Transformation::class.java, editingTransformationHook())
        val launcher = XposedHelpers.findClass("com.miui.home.launcher.Launcher", lpparam.classLoader)
        ModuleHelper.findAndHookMethod(launcher, "showBottomAreaEditPanelIfNeed", View::class.java,
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

    internal fun editingTransformationHook() = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            ModuleHelper.guarded {
                val animation = chain.thisObject as AnimationSet
                @Suppress("UNCHECKED_CAST")
                val owner = (XposedHelpers.getAdditionalInstanceField(animation, EDITING_OWNER) as? WeakReference<DockBackgroundView>)
                    ?.get() ?: return@guarded
                val transformation = chain.getArg(1) as Transformation
                owner.onEditingFrame(animation, transformation.alpha, result == true, transformation.matrix)
            }
            return result
        }
    }

    internal fun attach(host: FrameLayout, config: DockBackgroundConfig, material: Constructor<*>?,
        content: DockContentAccess? = null,
        wallpaper: DockWallpaperSampler? = null,
    ) {
        if (config.fullWidth && !host.isAttachedToWindow) {
            if (XposedHelpers.getAdditionalInstanceField(host, FULL_WIDTH_OWNER) != null) return
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    XposedHelpers.removeAdditionalInstanceField(v, FULL_WIDTH_OWNER)
                    attach(host, config, material, content, wallpaper)
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
            material, content, wallpaper, if (full) host else null)
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

    internal fun setEditingOwner(animation: AnimationSet, owner: DockBackgroundView?) {
        if (owner == null) XposedHelpers.removeAdditionalInstanceField(animation, EDITING_OWNER)
        else XposedHelpers.setAdditionalInstanceField(animation, EDITING_OWNER, WeakReference(owner))
    }

    internal fun clearEditingOwner(animation: AnimationSet, owner: DockBackgroundView) {
        if ((XposedHelpers.getAdditionalInstanceField(animation, EDITING_OWNER) as? WeakReference<*>)?.get() === owner)
            setEditingOwner(animation, null)
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
    val blurAllowed: Boolean = true,
) {
    companion object {
        fun read(prefs: PrefMap) = DockBackgroundConfig(
            material = when (prefs.getStringAsInt("launcher_dock_background_material", 0)) { 2 -> 2; 3 -> 3; else -> 0 },
            opacity = prefs.getInt("launcher_dock_background_opacity", 90).coerceIn(0, 100),
            radiusDp = prefs.getInt("launcher_dock_background_radius", 0).coerceIn(0, 80),
            sideInsetDp = prefs.getInt("launcher_dock_background_inset", 0).coerceIn(0, 40),
            // Retain the key and raw encoding. Positive displayed values now expand all four edges.
            sizeAdjustmentDp = prefs.getInt("launcher_dock_background_shrink", 17).coerceIn(0, 34) - 17,
            fullWidth = prefs.getBoolean("launcher_dock_background_full_width"),
            blurAllowed = !prefs.getBoolean("system_disable_window_blurs"),
        )
    }
}

/** The ROM owns rendering; layout and native scene changes are the only business update sources. */
internal class DockBackgroundView(
    context: Context,
    private val config: DockBackgroundConfig,
    private val materialConstructor: Constructor<*>?,
    private val contentAccess: DockContentAccess? = null,
    private val wallpaperSampler: DockWallpaperSampler? = null,
    layoutHost: FrameLayout? = null,
) : FrameLayout(context), ViewTreeObserver.OnPreDrawListener, View.OnLayoutChangeListener {
    private val fullWidthHost = WeakReference(layoutHost)
    private val tint = DockBackgroundTint(config.material == 3, resources.displayMetrics.density)
    private var nativeMaterial: View? = null
    private var glassBackdrop: DockGlassBackdrop? = null
    private var materialEnabled = false
    private var googleBlur = false
    private var powerSave = false
    private var powerRegistered = false
    private var blurWindow: WindowManager? = null
    private var crossWindowBlur = false
    private val blurListener = java.util.function.Consumer<Boolean> { enabled ->
        ModuleHelper.guarded {
            if (crossWindowBlur != enabled) {
                crossWindowBlur = enabled
                syncNativeMaterialVisibility(isShown || editingAnimation != null)
                invalidate()
            }
        }
    }
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) ModuleHelper.guarded { updatePowerSave() }
        }
    }

    private fun updatePowerSave() {
        powerSave = ModuleHelper.guarded(true) { context.getSystemService(PowerManager::class.java)?.isPowerSaveMode != false }
        glassBackdrop?.setPowerSave(powerSave)
        invalidate()
    }
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
    private val materialOutline = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, tint.radius)
        }
    }

    init {
        isClickable = false
        isLongClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        foreground = tint
        setWillNotDraw(true)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ModuleHelper.guarded {
            readScreenRadius(rootWindowInsets)
            navigationInsets = rootWindowInsets?.getInsets(WindowInsets.Type.navigationBars()) ?: Insets.NONE
            installMaterial()
            installGlass()
            if (config.material == 3 && (glassBackdrop != null || nativeMaterial != null) && !powerRegistered) {
                context.registerReceiver(powerReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), Context.RECEIVER_NOT_EXPORTED)
                powerRegistered = true
                updatePowerSave()
            }
            if (config.material == 3 && googleBlur && blurWindow == null) {
                context.getSystemService(WindowManager::class.java)?.let { manager ->
                    crossWindowBlur = manager.isCrossWindowBlurEnabled
                    manager.addCrossWindowBlurEnabledListener(context.mainExecutor, blurListener)
                    blurWindow = manager
                }
            }
            syncNativeMaterialVisibility(isShown)
            refreshAppearance()
            scheduleGeometry()
        }
    }

    private fun installGlass() {
        if (config.material != 3 || config.opacity == 0 || wallpaperSampler == null || !DockWallpaperCapture.permitted(context)) return
        try {
            val live = WallpaperManager.getInstance(context).wallpaperInfo != null
            glassBackdrop = DockGlassBackdrop(this, wallpaperSampler, config.opacity, live)
            tint.backdrop = glassBackdrop
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            XposedHelpers.log("Dock lens: native material retained after source setup failure", error)
        }
    }

    private fun installMaterial() {
        if (nativeMaterial != null || materialConstructor == null || config.opacity == 0) return
        var candidate: View? = null
        try {
            candidate = materialConstructor.newInstance(context) as View
            googleBlur = XposedHelpers.callMethod(candidate, "isGoogleBlurSupported") == true
            val supported = googleBlur || XposedHelpers.callMethod(candidate, "isBackdropBlurSupported") == true
            if (!supported) return
            candidate.isClickable = false
            candidate.isFocusable = false
            candidate.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            candidate.alpha = config.opacity / 100f
            // Gaussian already has native drawable corners; a second RenderNode clip is redundant.
            if (config.material == 0) {
                candidate.outlineProvider = materialOutline
                candidate.clipToOutline = true
            }
            XposedHelpers.callMethod(candidate, "setBlurRadius", blurRadius())
            XposedHelpers.callMethod(candidate, "setCornerRadius", tint.radius)
            XposedHelpers.callMethod(candidate, "setBlurEnabled", true)
            configureGlassBlend(candidate)
            addView(candidate, LayoutParams(-1, -1))
            nativeMaterial = candidate
            materialEnabled = true
        } catch (error: Throwable) {
            try {
                if (candidate?.parent === this) removeView(candidate)
            } finally {
                FatalErrors.unwrapAndRethrowIfFatal(error)
            }
            XposedHelpers.log("Dock background: native material unavailable", error)
        }
    }

    private fun blurRadius(): Int = when (config.material) {
        2 -> (35 * resources.displayMetrics.density).roundToInt()
        3 -> (18 * resources.displayMetrics.density).roundToInt()
        else -> 12
    }

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
            blurWindow?.let { manager -> ModuleHelper.guarded { manager.removeCrossWindowBlurEnabledListener(blurListener) } }
            blurWindow = null
            if (powerRegistered) {
                powerRegistered = false
                ModuleHelper.guarded { context.unregisterReceiver(powerReceiver) }
            }
            ModuleHelper.guarded {
                cancelWidthAnimation()
                widthAnimation?.removeAllUpdateListeners()
                widthAnimation = null
                lastIconCount = -1
                cancelGeometry()
                observeContent(null)
                clearEditingAnimation()
                tint.backdrop = null
                glassBackdrop?.release()
                glassBackdrop = null
                retireMaterial()
            }
        } finally {
            super.onDetachedFromWindow()
        }
    }

    private fun retireMaterial() {
        val material = nativeMaterial ?: return
        nativeMaterial = null
        materialEnabled = false
        googleBlur = false
        ModuleHelper.guarded { material.background?.alpha = 0 }
        try {
            removeView(material)
        } finally {
            material.background = null
            tint.rounded = false
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
            readScreenRadius(insets)
            val navigation = insets.getInsets(WindowInsets.Type.navigationBars())
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
            glassBackdrop?.onFrame((isVisible || drawingAnimation) && nativeBlurAlpha() > 0)
            if (!isVisible && drawingAnimation) nativeMaterial?.onVisibilityAggregated(true)
            syncNativeMaterialVisibility(isVisible || drawingAnimation)
        }
    }

    internal fun bindEditingAnimation(animation: AnimationSet, showing: Boolean) {
        cancelWidthAnimation()
        if ((!config.fullWidth && !googleBlur && glassBackdrop == null) || !isAttachedToWindow) return
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
        if (!config.fullWidth) glassBackdrop?.setLegacyTransform(matrix)
        syncFullWidthHost()
        syncNativeOpacity()
        if (!running) finishEditingAnimation()
    }

    private fun clearEditingAnimation() {
        editingAnimation?.let { LauncherDockBackground.clearEditingOwner(it, this) }
        editingAnimation = null
        editingAlpha = 1f
        editingMatrix.reset()
        glassBackdrop?.setLegacyTransform(null)
    }

    private fun finishEditingAnimation() {
        clearEditingAnimation()
        syncFullWidthHost()
        nativeMaterial?.onVisibilityAggregated(isShown)
        syncNativeMaterialVisibility(isShown)
    }

    private fun syncNativeMaterialVisibility(requestedVisible: Boolean) {
        val material = nativeMaterial ?: return
        try {
            val fallbackAllowed = config.material != 3 || !powerSave && config.blurAllowed && (!googleBlur || crossWindowBlur)
            val isVisible = requestedVisible && !tint.lensActive && fallbackAllowed
            // Retire the Google region before the SDK clears its background; disable the MIUI binder too.
            if (!isVisible) material.background?.alpha = 0
            if (materialEnabled != isVisible) {
                if (isVisible) {
                    // setBlurEnabled(false) also resets the SDK's saved radius to zero.
                    XposedHelpers.callMethod(material, "setBlurRadius", blurRadius())
                }
                XposedHelpers.callMethod(material, "setBlurEnabled", isVisible)
                materialEnabled = isVisible
                if (isVisible) configureGlassBlend(material)
            }
            material.visibility = if (isVisible) VISIBLE else INVISIBLE
            if (isVisible) material.background?.let { drawable ->
                val wasTransparent = drawable.alpha == 0
                drawable.alpha = if (googleBlur) nativeBlurAlpha() else (config.opacity * 2.55f).roundToInt()
                if (wasTransparent && drawable.alpha > 0) {
                    val bounds = drawable.bounds
                    drawable.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
                }
            }
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            retireMaterial()
            XposedHelpers.log("Dock background: native material retired after visibility failure", error)
        }
    }

    override fun draw(canvas: Canvas) {
        if (config.material != 3) { super.draw(canvas); return }
        // Resolve the owner before drawing children: the first usable lens frame must
        // not blend with a second native blur region using a different corner profile.
        tint.beginFrame(canvas)
        syncNativeMaterialVisibility((isShown || editingAnimation != null) && nativeBlurAlpha() > 0)
        tint.nativeFallback = materialEnabled && nativeMaterial?.visibility == VISIBLE && !tint.lensActive
        tint.solidFallback = !tint.lensActive && !tint.nativeFallback
        tint.panelAlpha = (config.opacity * 2.3f).roundToInt().coerceIn(0, 255)
        try { super.draw(canvas) } finally { tint.endFrame() }
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
    ) { scheduleGeometry() }

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

    private fun syncNativeOpacity() {
        if (!googleBlur || !materialEnabled) return
        try {
            val drawable = nativeMaterial?.background ?: return
            val alpha = nativeBlurAlpha()
            if (drawable.alpha == alpha) return
            val wasTransparent = drawable.alpha == 0
            drawable.alpha = alpha
            if (wasTransparent && alpha > 0) {
                val bounds = drawable.bounds
                drawable.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
            }
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            retireMaterial()
            XposedHelpers.log("Dock background: native material retired after opacity failure", error)
        }
    }

    override fun onPreDraw(): Boolean {
        var ready = true
        ModuleHelper.guarded {
            if (editingAnimation != null && (fullWidthHost.get() ?: parent as? View)?.animation !== editingAnimation)
                finishEditingAnimation()
            syncFullWidthHost()
            // Google blur regions have their own compositor opacity. Follow native rendered frames;
            // never start a frame loop, animate separately or traverse icon bounds on idle frames.
            syncNativeOpacity()
            glassBackdrop?.onFrame((isShown || editingAnimation != null) && nativeBlurAlpha() > 0)
            val host = fullWidthHost.get() ?: parent as? FrameLayout ?: return@guarded
            if (config.fullWidth) {
                syncNativeMaterialVisibility(isShown && nativeBlurAlpha() > 0)
                syncNativeOpacity()
            }
            if (!geometryPending) return@guarded
            geometryPending = false
            if (!isShown || host.height <= 0) return@guarded
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
            val root = rootView as? ViewGroup ?: return@guarded
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
        }
        if (!geometryPending && !config.fullWidth && !(googleBlur && materialEnabled) && glassBackdrop == null) cancelGeometry()
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
        tint.alpha = (config.opacity * if (config.material == 3) 0.2f else 0.85f).roundToInt()
        tint.rounded = config.material != 3 && nativeMaterial != null
        glassBackdrop?.invalidateSource()
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
internal class DockBackgroundTint(withRim: Boolean = false, private val density: Float = 1f) : Drawable() {
    var backdrop: DockGlassBackdrop? = null
        set(value) { field = value; if (value == null) lensActive = false }
    var lensActive = false
        private set
    var nativeFallback = false
    var solidFallback = false
    var panelAlpha = 216
    private var prepared = false
    fun beginFrame(canvas: Canvas) {
        prepared = true
        lensActive = backdrop?.prepare(canvas, bounds, radius, density) == true
    }
    fun endFrame() { prepared = false }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.TRANSPARENT }
    private val path = Path()
    private val rim = if (withRim) Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density.coerceAtLeast(1f)
        alpha = 0
    } else null
    private val rimPath = if (withRim) Path() else null
    private val depth = if (withRim) Paint(Paint.ANTI_ALIAS_FLAG) else null
    private val optics = if (withRim) DockGlassOptics.create() else null
    private var gradientDepth: Shader? = null
    private var explicitAlpha = false
    var color: Int
        get() = paint.color
        set(value) {
            if (paint.color != value) {
                val alpha = paint.alpha
                paint.color = value
                if (explicitAlpha) paint.alpha = alpha
                optics?.setFloatUniform("dark", if (Color.red(value) < 128) 1f else 0f)
                invalidateSelf()
            }
        }
    var radius = 0f
        set(value) { if (field != value) { field = value; rebuild(); invalidateSelf() } }
    var rounded = false
        set(value) { if (field != value) { field = value; rebuild(); invalidateSelf() } }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        gradientDepth = if (depth == null || bounds.isEmpty) null else LinearGradient(
            0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(),
            intArrayOf(0x18FFFFFF, 0x00FFFFFF, 0x10000000),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        rim?.shader = if (bounds.isEmpty) null else LinearGradient(
            bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(),
            intArrayOf(0xB3FFFFFF.toInt(), 0x25FFFFFF, 0x00FFFFFF, 0x66FFFFFF),
            floatArrayOf(0f, 0.25f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        rebuild()
    }

    private fun rebuild() {
        path.rewind()
        rimPath?.rewind()
        if (bounds.isEmpty) return
        val r = radius.coerceIn(0f, min(bounds.width(), bounds.height()) * 0.5f)
        if (rounded) path.addRoundRect(0f, 0f, bounds.width().toFloat(), bounds.height().toFloat(), r, r, Path.Direction.CW)
        else DockCornerGeometry.build(path, bounds.width().toFloat(), bounds.height().toFloat(), r)
        path.offset(bounds.left.toFloat(), bounds.top.toFloat())
        depth?.shader = if (rounded && optics != null) optics.apply {
            setFloatUniform("origin", bounds.left.toFloat(), bounds.top.toFloat())
            setFloatUniform("size", bounds.width().toFloat(), bounds.height().toFloat())
            setFloatUniform("radius", r)
            setFloatUniform("density", density.coerceAtLeast(0.1f))
            setFloatUniform("dark", if (Color.red(paint.color) < 128) 1f else 0f)
        } else gradientDepth
        rim?.let { edge ->
            // Keep the softly curved G4 bevel inside the compositor's circular outer boundary.
            val inset = min(maxOf(edge.strokeWidth, r * 0.16f), min(bounds.width(), bounds.height()) * 0.2f)
            rimPath?.let { bevel ->
                DockCornerGeometry.buildSoft(bevel, bounds.width() - 2 * inset, bounds.height() - 2 * inset,
                    (r - inset).coerceAtLeast(0f), 0.3f * density)
                bevel.offset(bounds.left + inset, bounds.top + inset)
            }
        }
    }

    override fun draw(canvas: Canvas) {
        if (prepared) {
            if (lensActive) {
                if (backdrop?.drawPrepared(canvas) == true) return
                lensActive = false
                nativeFallback = false
                solidFallback = true
            }
        } else if (backdrop?.draw(canvas, bounds, radius, density) == true) return
        if (nativeFallback) return
        if (solidFallback) {
            val alpha = paint.alpha
            paint.alpha = panelAlpha
            try { canvas.drawPath(path, paint) } finally { paint.alpha = alpha }
            return
        }
        if (paint.alpha == 0) return
        canvas.drawPath(path, paint)
        depth?.let {
            // Bitmap/software draws cannot execute RuntimeShader. Keep the prepared gradient
            // for those callers and restore the same shader for the next hardware recording.
            val shader = it.shader
            if (!canvas.isHardwareAccelerated && shader === optics) it.shader = gradientDepth
            try { canvas.drawPath(path, it) }
            finally { if (it.shader !== shader) it.shader = shader }
        }
        rim?.let { canvas.drawPath(checkNotNull(rimPath), it) }
    }
    override fun setAlpha(alpha: Int) {
        explicitAlpha = true
        if (paint.alpha != alpha) {
            paint.alpha = alpha
            rim?.alpha = (alpha * 4).coerceIn(0, 255)
            depth?.alpha = (alpha * 6).coerceIn(0, 255)
            invalidateSelf()
        }
    }
    override fun getAlpha(): Int = paint.alpha
    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        rim?.colorFilter = colorFilter
        depth?.colorFilter = colorFilter
        invalidateSelf()
    }
    @Deprecated("Drawable requires this legacy API; use alpha for opacity.")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
