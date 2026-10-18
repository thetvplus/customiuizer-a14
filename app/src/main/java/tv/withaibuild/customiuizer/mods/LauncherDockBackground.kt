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
import android.graphics.drawable.Drawable
import android.view.RoundedCorner
import android.view.View
import android.view.ViewOutlineProvider
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

/** A non-interactive decoration owned by HotSeats, below its unmodified icon content. */
object LauncherDockBackground {
    @JvmStatic
    fun install(lpparam: PackageReadyParam) {
        val config = DockBackgroundConfig.read(MainModule.mPrefs)
        val hotseats = XposedHelpers.findClass("com.miui.home.launcher.hotseats.HotSeats", lpparam.classLoader)
        check(FrameLayout::class.java.isAssignableFrom(hotseats))
        val material = if (config.material == 0) {
            XposedHelpers.findClassIfExists(
                "com.miui.blur.sdk.backdrop.BackdropBlurRelativeLayout", lpparam.classLoader
            )?.constructors?.firstOrNull { it.parameterTypes.contentEquals(arrayOf(Context::class.java)) }
        } else null
        ModuleHelper.findAndHookMethod(hotseats, "updateContent", object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                attach(callback.getThisObject() as FrameLayout, config, material)
            }
        })
        ModuleHelper.findAndHookMethod(hotseats, "onWallpaperColorChanged", object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                findBackground(callback.getThisObject() as FrameLayout)?.refreshAppearance()
            }
        })
    }

    internal fun attach(host: FrameLayout, config: DockBackgroundConfig, material: Constructor<*>?) {
        if (findBackground(host) != null) return
        val decoration = DockBackgroundView(host.context, config, material)
        val inset = ((if (config.sideInsetDp == 0) 4 else config.sideInsetDp) * host.resources.displayMetrics.density).roundToInt()
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

internal data class DockBackgroundConfig(
    val material: Int = 0,
    val opacity: Int = 65,
    val radiusDp: Int = 0,
    val sideInsetDp: Int = 0,
) {
    companion object {
        fun read(prefs: PrefMap) = DockBackgroundConfig(
            material = prefs.getStringAsInt("launcher_dock_background_material", 0).coerceIn(0, 1),
            opacity = prefs.getInt("launcher_dock_background_opacity", 65).coerceIn(0, 100),
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
) : FrameLayout(context) {
    private val tint = DockBackgroundTint()
    private var nativeMaterial: View? = null
    private var screenRadius = 0f
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
            XposedHelpers.callMethod(candidate, "setBlurRadius", 12)
            XposedHelpers.callMethod(candidate, "setCornerRadius", tint.radius)
            XposedHelpers.callMethod(candidate, "setBlurEnabled", true)
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
            readScreenRadius(rootWindowInsets)
            refreshAppearance()
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        ModuleHelper.guarded {
            readScreenRadius(insets)
            updateGeometry()
        }
        return super.onApplyWindowInsets(insets)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ModuleHelper.guarded { updateGeometry() }
    }

    private fun readScreenRadius(insets: WindowInsets?) {
        val left = insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0
        val right = insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0
        screenRadius = maxOf(left, right).toFloat()
        if (screenRadius > 0f) return
        val id = resources.getIdentifier("rounded_corner_radius_bottom", "dimen", "android")
        if (id != 0) screenRadius = resources.getDimension(id)
        if (screenRadius <= 0f) {
            val fallback = resources.getIdentifier("rounded_corner_radius", "dimen", "android")
            if (fallback != 0) screenRadius = resources.getDimension(fallback)
        }
    }

    fun refreshAppearance() {
        val inset = ((if (config.sideInsetDp == 0) 4 else config.sideInsetDp) * resources.displayMetrics.density).roundToInt()
        val params = layoutParams as? LayoutParams
        if (params != null && (params.leftMargin != inset || params.rightMargin != inset)) {
            params.leftMargin = inset
            params.rightMargin = inset
            layoutParams = params
        }
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
    }

    private fun updateGeometry() {
        val limit = min(width, height) * 0.5f
        val radius = if (config.radiusDp > 0) config.radiusDp * resources.displayMetrics.density
            else maxOf(height * 0.35f, screenRadius * 0.5f)
        val fitted = radius.coerceIn(0f, limit)
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
