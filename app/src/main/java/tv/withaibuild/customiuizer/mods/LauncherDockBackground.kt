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
        val material = if (config.material != 1) {
            XposedHelpers.findClassIfExists(
                "com.miui.blur.sdk.backdrop.BackdropBlurRelativeLayout", lpparam.classLoader
            )?.constructors?.firstOrNull { it.parameterTypes.contentEquals(arrayOf(Context::class.java)) }
        } else null
        ModuleHelper.findAndHookMethod(hotseats, "updateContent", object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                attach(callback.getThisObject() as FrameLayout, config, material, content)
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
        content: DockContentAccess? = null,
    ) {
        findBackground(host)?.let { it.scheduleGeometry(); return }
        val decoration = DockBackgroundView(host.context, config, material, content)
        val inset = ((if (config.sideInsetDp == 0) 12 else config.sideInsetDp) * host.resources.displayMetrics.density).roundToInt()
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
    private val positionChanged = content.type.getMethod("onTranslationChanged")

    fun positionChanged(row: ViewGroup) { positionChanged.invoke(row) }

    fun content(host: ViewGroup): ViewGroup? =
        (content.get(host) as? ViewGroup)?.takeIf { it.parent === host }

    fun bounds(host: ViewGroup, row: ViewGroup, out: Rect, scratch: Rect) {
        out.setEmpty()
        collect(host, row, out, scratch)
    }

    private fun collect(host: ViewGroup, parent: ViewGroup, out: Rect, scratch: Rect) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (iconType.isInstance(child)) {
                include(host, icon.get(child) as? View, out, scratch)
                if (!hiddenTitle.getBoolean(child)) include(host, title.get(child) as? View, out, scratch)
            } else if (child is ViewGroup) collect(host, child, out, scratch)
        }
    }

    private fun include(host: ViewGroup, view: View?, out: Rect, scratch: Rect) {
        if (view == null || view.visibility != View.VISIBLE || view.alpha == 0f || view.width <= 0 || view.height <= 0) return
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
) {
    companion object {
        fun read(prefs: PrefMap) = DockBackgroundConfig(
            material = prefs.getStringAsInt("launcher_dock_background_material", 0).coerceIn(0, 2),
            opacity = prefs.getInt("launcher_dock_background_opacity", 90).coerceIn(0, 100),
            radiusDp = prefs.getInt("launcher_dock_background_radius", 0).coerceIn(0, 80),
            sideInsetDp = prefs.getInt("launcher_dock_background_inset", 0).coerceIn(0, 40),
        )
    }
}

/** The ROM component owns blur sampling and animation alpha; this owner only handles cold updates. */
internal class DockBackgroundView(
    context: Context,
    private val config: DockBackgroundConfig,
    private val materialConstructor: Constructor<*>?,
    private val contentAccess: DockContentAccess? = null,
) : FrameLayout(context), ViewTreeObserver.OnPreDrawListener, View.OnLayoutChangeListener {
    private val tint = DockBackgroundTint()
    private var nativeMaterial: View? = null
    private var screenRadius = 0f
    private val contentBounds = Rect()
    private val scratch = Rect()
    private var pendingObserver: ViewTreeObserver? = null
    private var alignedContent: ViewGroup? = null
    private var appliedTop = 0
    private var appliedOffset = 0
    private var alignmentFailed = false
    private var edgeInset = 0f
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
            val blurRadius = if (config.material == 2) (28 * resources.displayMetrics.density).roundToInt() else 12
            XposedHelpers.callMethod(candidate, "setBlurRadius", blurRadius)
            XposedHelpers.callMethod(candidate, "setCornerRadius", tint.radius)
            XposedHelpers.callMethod(candidate, "setBlurEnabled", true)
            if (config.material == 2 && XposedHelpers.callMethod(candidate, "isGoogleBlurSupported") == true) {
                // Keep the native background blur; omit the SDK's glass color-blend layers.
                try {
                    XposedHelpers.callMethod(XposedHelpers.getObjectField(candidate, "mBlurLayerHolder"), "setEnable", false)
                } catch (error: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(error)
                    XposedHelpers.log("Dock background: using native glass blend", error)
                }
            }
            addView(candidate, LayoutParams(-1, -1))
            nativeMaterial = candidate
        } catch (error: Throwable) {
            try {
                if (candidate?.parent === this) removeView(candidate)
            } finally {
                FatalErrors.unwrapAndRethrowIfFatal(error)
            }
            XposedHelpers.log("Dock background: native material unavailable", error)
        }
    }

    override fun onDetachedFromWindow() {
        try {
            ModuleHelper.guarded {
                cancelGeometry()
                restoreContent()
                val material = nativeMaterial
                nativeMaterial = null
                if (material != null) {
                    // Retire the old root's blur drawable before a possible reattach.
                    ModuleHelper.guarded { material.background?.alpha = 0 }
                    try {
                        removeView(material)
                    } finally {
                        material.background = null
                    }
                }
                tint.rounded = false
            }
        } finally {
            super.onDetachedFromWindow()
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
        ModuleHelper.guarded { if (isVisible) scheduleGeometry() else cancelGeometry() }
    }

    fun scheduleGeometry() {
        if (!isAttachedToWindow || pendingObserver != null) return
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
        ModuleHelper.guarded {
            val host = parent as? FrameLayout ?: return@guarded
            if (!isShown || host.height <= 0) return@guarded
            if (!alignmentFailed && contentAccess != null) try {
                alignContent(host)
            } catch (error: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(error)
                alignmentFailed = true
                restoreContent()
                XposedHelpers.log("Dock background: native alignment disabled", error)
            }
            val root = rootView as? ViewGroup ?: return@guarded
            // Layout coordinates exclude the launcher's scale/translation animations.
            scratch.set(0, 0, host.width, host.height)
            root.offsetDescendantRectToMyCoords(host, scratch)
            val bottomGap = (root.height - scratch.top - bottom).coerceAtLeast(0)
            val density = resources.displayMetrics.density
            val inset = ((if (config.sideInsetDp == 0) 12 else config.sideInsetDp) * density).roundToInt()
            val params = layoutParams as? LayoutParams
            if (params != null && (params.leftMargin != inset || params.rightMargin != inset)) {
                params.leftMargin = inset
                params.rightMargin = inset
                layoutParams = params
            }
            val leftGap = scratch.left + host.paddingLeft + inset
            val rightGap = root.width - scratch.right + host.paddingRight + inset
            edgeInset = maxOf(leftGap, rightGap, bottomGap).toFloat()
            readScreenRadius(rootWindowInsets)
            updateGeometry()
        }
        return true
    }

    private fun alignContent(host: FrameLayout) {
        val access = contentAccess ?: return
        val row = access.content(host)
        if (alignedContent !== row) {
            restoreContent()
            alignedContent = row
            row?.addOnLayoutChangeListener(this)
        }
        if (row == null) return
        if (row.top != appliedTop) appliedOffset = 0 // native layout replaced our old position
        access.bounds(host, row, contentBounds, scratch)
        if (contentBounds.isEmpty || contentBounds.height() > height) return
        val shift = ((top + bottom - contentBounds.top - contentBounds.bottom) / 2f).roundToInt()
            .coerceIn(top - contentBounds.top, bottom - contentBounds.bottom)
        if (shift != 0) row.offsetTopAndBottom(shift)
        alignedContent = row
        appliedOffset += shift
        appliedTop = row.top
        if (shift != 0) access.positionChanged(row)
    }

    private fun restoreContent() {
        val row = alignedContent
        val offset = appliedOffset
        row?.removeOnLayoutChangeListener(this)
        alignedContent = null
        appliedOffset = 0
        if (row != null && row.top == appliedTop && offset != 0) {
            row.offsetTopAndBottom(-offset)
            if (row.parent === parent) ModuleHelper.guarded { contentAccess?.positionChanged(row) }
        }
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
        tint.alpha = (config.opacity * 0.55f).roundToInt()
        tint.rounded = nativeMaterial != null
        updateGeometry()
        scheduleGeometry()
    }

    private fun updateGeometry() {
        val density = resources.displayMetrics.density
        val fitted = DockCornerGeometry.radius(screenRadius, edgeInset, config.radiusDp * density,
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
