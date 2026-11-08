package tv.withaibuild.customiuizer.mods.statusicons

import android.animation.ArgbEvaluator
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Modifier
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.PreparedHookBatch
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

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
        val evaluator = ArgbEvaluator()
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
        fun sync(view: View, ink: IosBatteryInk) {
            ink.active = false
            val nativeStyle = style.getInt(view)
            // Preserve OEM quick-charge/performance/theme glyphs and native digit
            // modes until their separate presentation contracts are supported.
            val admitted = !legacy.getBoolean(view) && !performance.getBoolean(view) && !quick.getBoolean(view) &&
                (nativeStyle == 0 || nativeStyle == 3) && level.getInt(view) in 0..100
            if (!admitted) return
            val regions = areas.get(view)
            val base = if (useTint.getBoolean(view)) getTint.invoke(null, regions, view, tint.getInt(view)) as Int
                else if (inAreas.invoke(null, regions, view) == true)
                    evaluator.evaluate(intensity.getFloat(view).coerceIn(0f, 1f), light.getInt(view), dark.getInt(view)) as Int
                else light.getInt(view)
            ink.update(level.getInt(view), base, lowColor.getInt(view), saverColor.getInt(view), chargeColor.getInt(view),
                saver.getBoolean(view), low.getBoolean(view), charging.getBoolean(view))
            ink.active = true
        }
        fun ink(view: View): IosBatteryInk = XposedHelpers.getAdditionalInstanceField(view, OWNER) as? IosBatteryInk
            ?: IosBatteryInk().also { sync(view, it); XposedHelpers.setAdditionalInstanceField(view, OWNER, it) }
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
                    } catch (error: Throwable) { XposedHelpers.log(error) }
                    return result
                }
            },
            draw to object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val drawn = try {
                        val view = chain.thisObject as View
                        view.javaClass == owner && ink(view).draw(chain.getArg(0) as Canvas, view)
                    } catch (error: Throwable) { XposedHelpers.log(error); false }
                    return if (drawn) null else chain.proceed()
                }
            },
        ))
    }
}

/** No View/Context references, timers or frame clock. Paints are reused. */
internal class IosBatteryInk {
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
    var active = false
    private var level = 0
    fun update(level: Int, color: Int, lowColor: Int, saverColor: Int, chargingColor: Int,
        saver: Boolean, low: Boolean, charging: Boolean) {
        this.level = level.coerceIn(0, 100)
        // 22G100's verified precedence is saver, low, then charging. Color values
        // remain native HyperOS theme values, not unverified Apple RGBA constants.
        fill.color = when { saver -> saverColor; low -> lowColor; charging -> chargingColor; else -> color }
        body.color = (color and 0x00ffffff) or (((color ushr 24) * .24f).toInt() shl 24)
        outline.color = (color and 0x00ffffff) or (((color ushr 24) * .55f).toInt() shl 24)
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
        val saved = canvas.save()
        try {
            canvas.translate(view.paddingLeft + (width - 22f * scale) * .5f,
                view.paddingTop + (height - 11.5f * scale) * .5f)
            canvas.scale(scale, scale)
            canvas.drawRoundRect(.5f, .5f, 19.8f, 11f, 2.8f, 2.8f, body)
            canvas.drawRoundRect(.5f, .5f, 19.8f, 11f, 2.8f, 2.8f, outline)
            canvas.drawRoundRect(20.5f, 3.8f, 22f, 7.7f, .7f, .7f, body)
            val clip = canvas.save()
            try {
                canvas.clipRect(1.8f, 1.8f, 1.8f + 16.7f * level / 100f, 9.7f)
                canvas.drawRoundRect(1.8f, 1.8f, 18.5f, 9.7f, 1.5f, 1.5f, fill)
            } finally { canvas.restoreToCount(clip) }
        } finally { canvas.restoreToCount(saved) }
        return true
    }
}
