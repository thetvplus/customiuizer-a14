package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.FrameLayout
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Constructor
import kotlin.math.min
import kotlin.math.roundToInt

/** A non-interactive decoration owned by HotSeats, below its native icon content. */
object LauncherDockBackground {
    @JvmStatic
    fun install(lpparam: PackageReadyParam) {
        val config = DockBackgroundConfig.read(MainModule.mPrefs)
        val hotseats = XposedHelpers.findClass("com.miui.home.launcher.hotseats.HotSeats", lpparam.classLoader)
        check(FrameLayout::class.java.isAssignableFrom(hotseats))
        val content = DockContentAccess.resolve(lpparam.classLoader)
        val launcher = XposedHelpers.findClass("com.miui.home.launcher.Launcher", lpparam.classLoader)
        val state = XposedHelpers.findClass("com.miui.home.launcher.LauncherState", lpparam.classLoader)
        val overview = XposedHelpers.findClass("com.miui.home.recents.OverviewState", lpparam.classLoader)
        val owner = XposedHelpers.findField(hotseats, "mLauncher")
        val getHotSeats = launcher.getMethod("getHotSeats")
        val getStateManager = launcher.getMethod("getStateManager")
        val getState = getStateManager.returnType.getMethod("getState")
        val material = XposedHelpers.findClassIfExists(
            "com.miui.blur.sdk.backdrop.BackdropBlurRelativeLayout", lpparam.classLoader
        )?.constructors?.firstOrNull { it.parameterTypes.contentEquals(arrayOf(Context::class.java)) }
        ModuleHelper.findAndHookMethod(hotseats, "updateContent", object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                val host = callback.getThisObject() as FrameLayout
                val manager = owner.get(host)?.let { getStateManager.invoke(it) }
                val visible = !overview.isInstance(manager?.let { getState.invoke(it) })
                attach(host, config, material, content, visible)
            }
        })
        ModuleHelper.findAndHookMethod(launcher, "onStateSetStart", state, object : MethodHook() {
            override fun before(callback: BeforeHookCallback) {
                val host = getHotSeats.invoke(callback.getThisObject()) as? FrameLayout ?: return
                findBackground(host)?.visibility =
                    if (overview.isInstance(callback.getArg(0))) View.INVISIBLE else View.VISIBLE
            }
        })
        ModuleHelper.findAndHookMethod(hotseats, "onMeasure", Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    findBackground(callback.getThisObject() as FrameLayout)?.scheduleGeometry()
                }
            })
        ModuleHelper.findAndHookMethod(hotseats, "onWallpaperColorChanged", object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                findBackground(callback.getThisObject() as FrameLayout)?.refreshAppearance()
            }
        })
    }

    internal fun attach(host: FrameLayout, config: DockBackgroundConfig, material: Constructor<*>?,
        content: DockContentAccess? = null, visible: Boolean = true,
    ) {
        findBackground(host)?.let {
            it.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            it.scheduleGeometry()
            return
        }
        val decoration = DockBackgroundView(host.context, config, material, content).apply {
            visibility = if (visible) View.VISIBLE else View.INVISIBLE
        }
        val inset = ((if (config.sideInsetDp == 0) 14 else config.sideInsetDp) * host.resources.displayMetrics.density).roundToInt()
        host.addView(decoration, 0, FrameLayout.LayoutParams(-1, -1).apply {
            leftMargin = inset
            rightMargin = inset
        })
    }

    private fun findBackground(host: FrameLayout): DockBackgroundView? {
        for (index in 0 until host.childCount) {
            val child = host.getChildAt(index)
            if (child is DockBackgroundView) return child
        }
        return null
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

    fun isDragging(host: ViewGroup): Boolean {
        val controller = dragController?.get(host) ?: return false
        return dragging?.getBoolean(controller) == true
    }

    fun content(host: ViewGroup): ViewGroup? =
        (content.get(host) as? ViewGroup)?.takeIf { it.parent === host }

    fun bounds(host: ViewGroup, row: ViewGroup, out: Rect, scratch: Rect) {
        out.setEmpty()
        collect(host, row, out, scratch)
    }

    private fun collect(host: ViewGroup, parent: ViewGroup, out: Rect, scratch: Rect) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.visibility == View.GONE) continue
            if (iconType.isInstance(child)) {
                include(host, icon.get(child) as? View, out, scratch)
                if (!hiddenTitle.getBoolean(child)) include(host, title.get(child) as? View, out, scratch)
            } else if (child is ViewGroup) collect(host, child, out, scratch)
        }
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
    val shrinkDp: Int = 1,
) {
    companion object {
        fun read(prefs: PrefMap) = DockBackgroundConfig(
            material = if (prefs.getStringAsInt("launcher_dock_background_material", 0) == 2) 2 else 0,
            opacity = prefs.getInt("launcher_dock_background_opacity", 90).coerceIn(0, 100),
            radiusDp = prefs.getInt("launcher_dock_background_radius", 0).coerceIn(0, 80),
            sideInsetDp = prefs.getInt("launcher_dock_background_inset", 0).coerceIn(0, 40),
            shrinkDp = prefs.getInt("launcher_dock_background_shrink", 13).coerceIn(0, 24) - 12,
        )
    }
}

/** The ROM owns rendering; layout and native scene changes are the only business update sources. */
internal class DockBackgroundView(
    context: Context,
    private val config: DockBackgroundConfig,
    private val materialConstructor: Constructor<*>?,
    private val contentAccess: DockContentAccess? = null,
) : FrameLayout(context), ViewTreeObserver.OnPreDrawListener, View.OnLayoutChangeListener {
    private val tint = DockBackgroundTint()
    private var nativeMaterial: View? = null
    private var materialEnabled = false
    private var screenRadius = 0f
    private val contentBounds = Rect()
    private val scratch = Rect()
    private var pendingObserver: ViewTreeObserver? = null
    private var observedContent: ViewGroup? = null
    private var contentFailed = false
    private var horizontalInset = 0f
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
            installMaterial()
            syncNativeMaterialVisibility(isShown)
            refreshAppearance()
            scheduleGeometry()
        }
    }

    private fun installMaterial() {
        if (nativeMaterial != null || materialConstructor == null || config.opacity == 0) return
        var candidate: View? = null
        try {
            candidate = materialConstructor.newInstance(context) as View
            val supported = XposedHelpers.callMethod(candidate, "isBackdropBlurSupported") == true ||
                XposedHelpers.callMethod(candidate, "isGoogleBlurSupported") == true
            if (!supported) return
            candidate.isClickable = false
            candidate.isFocusable = false
            candidate.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            candidate.alpha = config.opacity / 100f
            candidate.outlineProvider = materialOutline
            candidate.clipToOutline = true
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

    private fun blurRadius(): Int =
        if (config.material == 2) (35 * resources.displayMetrics.density).roundToInt() else 12

    private fun configureGlassBlend(material: View) {
        if (config.material != 2 || XposedHelpers.callMethod(material, "isGoogleBlurSupported") != true) return
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
                cancelGeometry()
                observeContent(null)
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
            screenRadius = 0f
            readScreenRadius(rootWindowInsets)
            refreshAppearance()
            scheduleGeometry()
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        ModuleHelper.guarded {
            readScreenRadius(insets)
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
            if (isVisible) scheduleGeometry() else cancelGeometry()
            syncNativeMaterialVisibility(isVisible)
        }
    }

    private fun syncNativeMaterialVisibility(isVisible: Boolean) {
        val material = nativeMaterial ?: return
        try {
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
            if (isVisible) material.background?.let { drawable ->
                drawable.alpha = (config.opacity * 2.55f).roundToInt()
                // A hidden bounds/corner update leaves BackgroundBlurDrawable's path empty.
                val bounds = drawable.bounds
                drawable.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
            }
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            retireMaterial()
            XposedHelpers.log("Dock background: native material retired after visibility failure", error)
        }
    }

    fun scheduleGeometry() {
        if (!isAttachedToWindow || !isShown || pendingObserver != null) return
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
    }

    override fun onPreDraw(): Boolean {
        cancelGeometry()
        var ready = true
        ModuleHelper.guarded {
            val host = parent as? FrameLayout ?: return@guarded
            if (!isShown || host.height <= 0) return@guarded
            val dragging = contentAccess?.isDragging(host) == true
            contentBounds.setEmpty()
            if (!dragging && !contentFailed && contentAccess != null) try {
                val row = contentAccess.content(host)
                observeContent(row)
                if (row != null) contentAccess.bounds(host, row, contentBounds, scratch)
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
            var inset = ((if (config.sideInsetDp == 0) 14 else config.sideInsetDp) * density).roundToInt()
            var topInset = params?.topMargin ?: 0
            var bottomInset = params?.bottomMargin ?: 0
            if (dragging && params != null) {
                inset = params.leftMargin
            } else if (!contentBounds.isEmpty) {
                var padding = maxOf(8 * density, contentBounds.height() * 0.3f).roundToInt()
                val maxPadding = if (host.clipChildren || host.clipToPadding)
                    minOf(contentBounds.top - host.paddingTop,
                        host.height - host.paddingBottom - contentBounds.bottom).coerceAtLeast(0)
                else Int.MAX_VALUE
                padding = minOf(padding, maxPadding)
                val sideRoom = minOf(contentBounds.left - host.paddingLeft,
                    host.width - host.paddingRight - contentBounds.right)
                if (config.sideInsetDp == 0) {
                    // Fit dense native rows without resizing icons or changing their layout.
                    inset = minOf(inset, (sideRoom - contentBounds.height() * 0.25f).roundToInt())
                        .coerceAtLeast(minOf((8 * density).roundToInt(), sideRoom.coerceAtLeast(0)))
                }
                // One signed inset for all four edges, bounded by the native icon/host space.
                val minimumPadding = (4 * density).roundToInt()
                val maxShrink = minOf((padding - minimumPadding).coerceAtLeast(0),
                    (sideRoom - inset - minimumPadding).coerceAtLeast(0))
                val minShrink = maxOf(-inset, padding - maxPadding)
                val shrink = (config.shrinkDp * density).roundToInt().coerceIn(minShrink, maxShrink)
                padding -= shrink
                inset += shrink
                // Native HotSeats permits overflow. Fit its background around the native icon row,
                // leaving animation landing coordinates intact, with a tiny optical trim at the top.
                val trim = minOf(padding, (0.5f * density).roundToInt().coerceAtLeast(1))
                topInset = contentBounds.top - host.paddingTop - padding + trim
                bottomInset = host.height - host.paddingBottom - contentBounds.bottom - padding
            }
            if (params != null && (params.leftMargin != inset || params.rightMargin != inset ||
                    params.topMargin != topInset || params.bottomMargin != bottomInset)) {
                params.leftMargin = inset
                params.rightMargin = inset
                params.topMargin = topInset
                params.bottomMargin = bottomInset
                layoutParams = params
                ready = false // apply the new geometry before presenting it
            }
            val leftGap = scratch.left + host.paddingLeft + inset
            val rightGap = root.width - scratch.right + host.paddingRight + inset
            // Raising the Dock to follow native icons must not reduce its corner radius.
            horizontalInset = maxOf(leftGap, rightGap).toFloat()
            readScreenRadius(rootWindowInsets)
            updateGeometry()
        }
        return ready
    }

    private fun observeContent(row: ViewGroup?) {
        if (observedContent === row) return
        observedContent?.removeOnLayoutChangeListener(this)
        observedContent = row
        row?.addOnLayoutChangeListener(this)
    }

    private fun readScreenRadius(insets: WindowInsets?) {
        val left = insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0
        val right = insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0
        val radius = maxOf(left, right).toFloat()
        if (radius > 0f) screenRadius = radius
        // Insets can briefly omit corners while window focus / floating-window state changes.
        if (screenRadius > 0f) return
        val id = resources.getIdentifier("rounded_corner_radius_bottom", "dimen", "android")
        if (id != 0) screenRadius = resources.getDimension(id)
        if (screenRadius <= 0f) {
            val fallback = resources.getIdentifier("rounded_corner_radius", "dimen", "android")
            if (fallback != 0) screenRadius = resources.getDimension(fallback)
        }
    }

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
        tint.rounded = nativeMaterial != null
        updateGeometry()
        scheduleGeometry()
    }

    private fun updateGeometry() {
        val density = resources.displayMetrics.density
        val fitted = DockCornerGeometry.radius(screenRadius, horizontalInset, config.radiusDp * density,
            28f * density, min(width, height) * 0.5f)
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
    var color: Int
        get() = paint.color
        set(value) { if (paint.color != value) { paint.color = value; invalidateSelf() } }
    var radius = 0f
        set(value) { if (field != value) { field = value; rebuild(); invalidateSelf() } }
    var rounded = false
        set(value) { if (field != value) { field = value; rebuild(); invalidateSelf() } }

    override fun onBoundsChange(bounds: android.graphics.Rect) = rebuild()

    private fun rebuild() {
        path.rewind()
        if (bounds.isEmpty) return
        val r = radius.coerceIn(0f, min(bounds.width(), bounds.height()) * 0.5f)
        if (rounded) path.addRoundRect(0f, 0f, bounds.width().toFloat(), bounds.height().toFloat(), r, r, Path.Direction.CW)
        else DockCornerGeometry.build(path, bounds.width().toFloat(), bounds.height().toFloat(), r)
        path.offset(bounds.left.toFloat(), bounds.top.toFloat())
    }

    override fun draw(canvas: Canvas) { canvas.drawPath(path, paint) }
    override fun setAlpha(alpha: Int) { if (paint.alpha != alpha) { paint.alpha = alpha; invalidateSelf() } }
    override fun getAlpha(): Int = paint.alpha
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Drawable requires this legacy API; use alpha for opacity.")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
