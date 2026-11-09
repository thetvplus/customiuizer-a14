package tv.withaibuild.customiuizer.mods.statusicons

import android.animation.ArgbEvaluator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Modifier
import kotlin.math.roundToInt
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.CleanupActions
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.PreparedHookBatch
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.G4RoundRectGeometry

/** Only the native battery ImageView's ink. Native slots, percent text, state,
 * accessibility, hiding and invalidation stay owned by SystemUI. */
internal object IosBatteryHooks {
    private const val OWNER = "a14.status.battery.ink"

    fun install(param: PackageReadyParam): FeatureInstallResult {
        if (param.packageName != "com.android.systemui") return FeatureInstallResult.FAILED_PERMANENT
        val owner = Class.forName("com.android.systemui.statusbar.views.MiuiBatteryMeterIconView", false, param.classLoader)
        require(android.widget.ImageView::class.java.isAssignableFrom(owner))
        fun field(name: String, type: Class<*>) = XposedHelpers.findField(owner, name).apply {
            require(this.type == type && !Modifier.isStatic(modifiers)); isAccessible = true
        }
        val integer = Int::class.javaPrimitiveType!!; val boolean = Boolean::class.javaPrimitiveType!!
        val digitOwner = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.views.MiuiBatteryMeterView", param.classLoader)
        val digitField = digitOwner?.let { XposedHelpers.findFieldIfExists(it, "mBatteryTextDigitView") }
            ?.takeIf { TextView::class.java.isAssignableFrom(it.type) }
        val evaluator = ArgbEvaluator()
        val leftSpacing = MainModule.mPrefs.getInt("system_statusbar_ios_battery_leftmargin", 0)
        val rightSpacing = MainModule.mPrefs.getInt("system_statusbar_ios_battery_rightmargin", 0)
        val level = field("mLevel", integer); val style = field("mBatteryStyle", integer)
        val legacy = field("mUseLegacyDrawable", boolean); val performance = field("mPerformanceMode", boolean)
        val quick = field("mQuickCharging", boolean); val charging = field("mCharging", boolean)
        val saver = field("mPowerSave", boolean); val low = field("mLow", boolean)
        val useTint = field("mUseTint", boolean); val tint = field("mTintColor", integer)
        val light = field("mLightColor", integer); val dark = field("mDarkColor", integer)
        val intensity = field("mDarkIntensity", Float::class.javaPrimitiveType!!)
        val areas = field("mTintAreas", java.util.ArrayList::class.java)
        val lowColor = field("mBatteryLowColor", integer); val saverColor = field("mBatteryPowerSaveColor", integer)
        val chargeColor = field("mBatteryChargingColor", integer)
        val dispatcher = Class.forName("com.android.systemui.plugins.DarkIconDispatcher", false, owner.classLoader)
        val getTint = dispatcher.getDeclaredMethod("getTint", java.util.ArrayList::class.java, View::class.java, integer)
        val inAreas = dispatcher.getDeclaredMethod("isInAreas", java.util.ArrayList::class.java, View::class.java)
        require(Modifier.isStatic(getTint.modifiers) && getTint.returnType == integer &&
            Modifier.isStatic(inAreas.modifiers) && inAreas.returnType == boolean)
        val draw = owner.getDeclaredMethod("onDraw", Canvas::class.java)
        val update = owner.getDeclaredMethod("onDarkChangeInternal")
        require(listOf(draw, update).all { !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE })
        val spacingReady = (leftSpacing != 0 || rightSpacing != 0) &&
            StatusIconSpacing.install(param.classLoader).isActive
        fun sync(view: View, ink: IosBatteryInk) {
            ink.active = false
            val nativeStyle = style.getInt(view)
            // Style 1's number is a separate native TextView in the parent frame.
            // Only replace the ink underneath it; style 2 is digit-only and stays native.
            val slot = StatusIconSpacing.slot(view, "com.android.systemui.statusbar.views.MiuiBatteryMeterView")
            val number = if (nativeStyle == 1 && digitOwner?.isInstance(slot) == true) digitField?.get(slot) as? TextView else null
            val admitted = !legacy.getBoolean(view) && !performance.getBoolean(view) && !quick.getBoolean(view) &&
                (nativeStyle == 0 || nativeStyle == 3 || nativeStyle == 1 && number != null) && level.getInt(view) in 0..100
            if (!admitted) { ink.clearDigits(); StatusIconSpacing.restore(slot); return }
            val regions = areas.get(view)
            val base = if (useTint.getBoolean(view)) getTint.invoke(null, regions, view, tint.getInt(view)) as Int
                else if (inAreas.invoke(null, regions, view) == true)
                    evaluator.evaluate(intensity.getFloat(view).coerceIn(0f, 1f), light.getInt(view), dark.getInt(view)) as Int
                else light.getInt(view)
            ink.update(level.getInt(view), base, lowColor.getInt(view), saverColor.getInt(view), chargeColor.getInt(view),
                saver.getBoolean(view), low.getBoolean(view), charging.getBoolean(view))
            ink.inlineDigits(view, number)
            ink.active = true
            StatusIconSpacing.update(slot, if (spacingReady) leftSpacing else 0, if (spacingReady) rightSpacing else 0)
        }
        fun clear(view: View, ink: IosBatteryInk?, failure: Throwable? = null) {
            val cleanup = CleanupActions(failure)
            cleanup.run { ink?.let { it.active = false; it.clearDigits() } }
            cleanup.run { StatusIconSpacing.restore(StatusIconSpacing.slot(view,
                "com.android.systemui.statusbar.views.MiuiBatteryMeterView")) }
            cleanup.finish()
        }
        fun ink(view: View): IosBatteryInk {
            (XposedHelpers.getAdditionalInstanceField(view, OWNER) as? IosBatteryInk)?.let { return it }
            val created = IosBatteryInk()
            try {
                sync(view, created)
                XposedHelpers.setAdditionalInstanceField(view, OWNER, created)
                return created
            } catch (error: Throwable) {
                // First sync can fail after installing the native text shader or
                // slot spacing, before this ink has been published on its View.
                clear(view, created, error)
                throw error
            }
        }
        return PreparedHookBatch().install(listOf(
            update to object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    try {
                        val view = chain.thisObject as View
                        if (view.javaClass == owner) {
                            val existing = XposedHelpers.getAdditionalInstanceField(view, OWNER) as? IosBatteryInk
                            if (existing != null) sync(view, existing) else ink(view)
                        }
                    } catch (error: Throwable) {
                        val view = chain.thisObject as View
                        try { clear(view, XposedHelpers.getAdditionalInstanceField(view, OWNER) as? IosBatteryInk, error) }
                        catch (released: Throwable) { XposedHelpers.log(released) }
                    }
                    return result
                }
            },
            draw to object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val drawn = try {
                        val view = chain.thisObject as View
                        view.javaClass == owner && ink(view).draw(chain.getArg(0) as Canvas, view)
                    } catch (error: Throwable) {
                        val view = chain.thisObject as View
                        try { clear(view, XposedHelpers.getAdditionalInstanceField(view, OWNER) as? IosBatteryInk, error) }
                        catch (released: Throwable) { XposedHelpers.log(released) }
                        false
                    }
                    return if (drawn) null else chain.proceed()
                }
            },
        ))
    }
}

/** No View/Context references, timers or frame clock. Unit ink is prepared once. */
internal class IosBatteryInk {
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
    private val cap = Paint(Paint.ANTI_ALIAS_FLAG)
    private val frame = Path().apply { G4RoundRectGeometry.build(this, 19.3f, 10.5f, 2.8f); offset(.5f, .5f) }
    private val terminal = Path().apply { G4RoundRectGeometry.build(this, 1.5f, 3.9f, .7f); offset(20.5f, 3.8f) }
    private val content = Path().apply { G4RoundRectGeometry.build(this, 16.7f, 7.9f, 1.5f); offset(1.8f, 1.8f) }
    var active = false
    private var level = 0
    private var base = 0
    private var digits: BatteryDigitContrast? = null
    fun inlineDigits(icon: View, text: TextView?) {
        if (text == null) { clearDigits(); return }
        val current = digits
        val binding = if (current?.matches(icon, text) == true) current else {
            current?.close(); BatteryDigitContrast(icon, text).also { digits = it }
        }
        binding.update(level, base, fill.color)
    }
    fun clearDigits() { digits?.close(); digits = null }
    fun update(level: Int, color: Int, lowColor: Int, saverColor: Int, chargingColor: Int,
        saver: Boolean, low: Boolean, charging: Boolean) {
        this.level = level.coerceIn(0, 100)
        base = color
        // 22G100's verified precedence is saver, low, then charging. Color values
        // remain native HyperOS theme values, not unverified Apple RGBA constants.
        fill.color = when { saver -> saverColor; low -> lowColor; charging -> chargingColor; else -> color }
        body.color = (color and 0x00ffffff) or (((color ushr 24) * .24f).toInt() shl 24)
        outline.color = (color and 0x00ffffff) or (((color ushr 24) * .55f).toInt() shl 24)
        cap.color = outline.color
    }
    fun draw(canvas: Canvas, view: View): Boolean {
        if (!active) return false
        val width = view.width - view.paddingLeft - view.paddingRight
        val height = view.height - view.paddingTop - view.paddingBottom
        if (width <= 0 || height <= 0) return false
        // Fit the existing native allocation. 22 x 11.5 is a verified iconSize=1
        // branch, selected here as an Android ratio, not the measured 16 Pro default.
        // The rounded frame is an Android approximation. The parent meter owns
        // charging glyphs; drawing another bolt here would duplicate its overlay.
        val scale = minOf(width / 22f, height / 11.5f)
        // Keep the physical stroke at a whole pixel at fractional display densities.
        // Paths remain cached in unit space; only the paint width follows the allocation.
        outline.strokeWidth = scale.roundToInt().coerceAtLeast(1) / scale
        val saved = canvas.save()
        try {
            canvas.translate(view.paddingLeft + (width - 22f * scale) * .5f,
                view.paddingTop + (height - 11.5f * scale) * .5f)
            canvas.scale(scale, scale)
            canvas.drawPath(frame, body)
            canvas.drawPath(frame, outline)
            canvas.drawPath(terminal, cap)
            val clip = canvas.save()
            try {
                canvas.clipRect(1.8f, 1.8f, 1.8f + 16.7f * level / 100f, 9.7f)
                canvas.drawPath(content, fill)
            } finally { canvas.restoreToCount(clip) }
        } finally { canvas.restoreToCount(saved) }
        return true
    }
}
