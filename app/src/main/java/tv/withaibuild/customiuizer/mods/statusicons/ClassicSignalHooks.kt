package tv.withaibuild.customiuizer.mods.statusicons

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.PreparedHookBatch
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Modifier
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/** Native slots and state remain authoritative; unknown/theme/no-service IDs stay native. */
internal object ClassicSignalHooks {
    private const val OWNER = "a14.status.signal.dots"
    fun install(param: PackageReadyParam): FeatureInstallResult {
        require(param.packageName == "com.android.systemui")
        val loader = param.classLoader
        val host = Class.forName("com.android.systemui.statusbar.StatusBarMobileView", false, loader)
        val state = Class.forName("com.android.systemui.statusbar.phone.StatusBarSignalPolicy\$MobileIconState", false, loader)
        val icons = Class.forName("com.android.systemui.statusbar.mobile.TelephonyIcons", false, loader)
        val tableField = icons.getDeclaredField("TELEPHONY_SIGNAL_STRENGTH").apply {
            require(type == IntArray::class.java && Modifier.isStatic(modifiers)); isAccessible = true
        }
        val levels = (tableField.get(null) as IntArray).clone()
        require(levels.size in 5..6 && levels.all { it != 0 } && levels.distinct().size == levels.size)
        fun field(type: Class<*>, name: String, expected: Class<*>) = XposedHelpers.findField(type, name).apply {
            require(this.type == expected && !Modifier.isStatic(modifiers)); isAccessible = true
        }
        val image = field(host, "mMobile", ImageView::class.java)
        val current = field(host, "mState", state)
        val strength = field(state, "strengthId", Int::class.javaPrimitiveType!!)
        val airplane = field(state, "airplane", Boolean::class.javaPrimitiveType!!)
        val color = field(host, "mColor", Int::class.javaPrimitiveType!!)
        val apply = host.getDeclaredMethod("applyMobileState", state)
        val darkness = host.getDeclaredMethod("applyDarknessInternal")
        require(listOf(apply, darkness).all { it.returnType == Void.TYPE && !Modifier.isStatic(it.modifiers) })
        fun sync(view: Any) {
            val target = image.get(view) as ImageView
            val previous = (XposedHelpers.getAdditionalInstanceField(view, OWNER) as? WeakReference<*>)?.get() as? Binding
            val old = previous?.takeIf { it.matches(target) }
            if (previous !== old) previous?.close()
            val native = current.get(view)
            val count = if (native == null || airplane.getBoolean(native) ||
                MainModule.mPrefs.getBoolean("system_statusbar_duo") ||
                !MainModule.mPrefs.getBoolean("system_statusbar_signal_dots")) null
                else dotCount(levels, strength.getInt(native))
            if (count == null) { old?.close(); XposedHelpers.removeAdditionalInstanceField(view, OWNER); return }
            // The image's attach listener owns the binding. A strong global
            // additional-field value would retain image -> parent -> weak key.
            val binding = old?.takeUnless { it.closed } ?: Binding(target).also {
                XposedHelpers.setAdditionalInstanceField(view, OWNER, WeakReference(it))
            }
            try { binding.update(count, color.getInt(view)) }
            catch (error: Throwable) { binding.close(); XposedHelpers.removeAdditionalInstanceField(view, OWNER); throw error }
        }
        val hook = object : MethodHook() {
            override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                val view = callback.getThisObject()!!
                if (callback.getThrowable() == null) sync(view)
                else {
                    ((XposedHelpers.getAdditionalInstanceField(view, OWNER) as? WeakReference<*>)?.get() as? Binding)?.close()
                    XposedHelpers.removeAdditionalInstanceField(view, OWNER)
                }
            }
        }
        return PreparedHookBatch().install(listOf(apply to hook, darkness to hook))
    }

    internal fun dotCount(table: IntArray, resource: Int): Int? {
        val index = table.indexOf(resource)
        return if (index < 0 || table.size !in 5..6) null else (index * 5f / (table.size - 1)).roundToInt()
    }

    internal class Binding(private val image: ImageView) : View.OnAttachStateChangeListener {
        private var native = image.drawable
        private var width = image.layoutParams?.width
        private var appliedWidth: Int? = null
        var closed = false
            private set
        private val ink = Dots(image.resources.displayMetrics.density)
        init { image.addOnAttachStateChangeListener(this) }
        fun matches(target: ImageView) = image === target
        fun update(count: Int, color: Int) {
            if (closed) return
            if (image.drawable !== ink) native = image.drawable
            var parent = image.parent
            var lock = false
            while (parent is View) {
                if (parent.javaClass.name == "com.android.systemui.statusbar.phone.KeyguardStatusBarView") lock = true
                parent = parent.parent
            }
            val changed = ink.color != color || ink.lock != lock
            ink.color = color; ink.lock = lock
            val levelChanged = ink.setLevel(count)
            image.layoutParams?.let { lp ->
                if (appliedWidth != null && lp.width != appliedWidth) width = lp.width
                val h = if (lp.height > 0) lp.height else ink.intrinsicHeight
                val w = (h * ink.ratio).roundToInt()
                if (lp.width != w) { lp.width = w; image.layoutParams = lp }
                appliedWidth = w
            }
            if (image.drawable !== ink) image.setImageDrawable(ink)
            if (changed && !levelChanged) ink.invalidateSelf()
        }
        fun close() {
            if (closed) return
            closed = true
            image.removeOnAttachStateChangeListener(this)
            if (image.drawable === ink) image.setImageDrawable(native)
            val restoreWidth = width
            image.layoutParams?.let { lp ->
                if (restoreWidth != null && lp.width == appliedWidth) { lp.width = restoreWidth; image.layoutParams = lp }
            }
        }
        override fun onViewAttachedToWindow(v: View) = Unit
        // Keep native data on the view; no global references or callbacks survive detach.
        override fun onViewDetachedFromWindow(v: View) { close() }
    }

    /** Independent circle drawing using measured normal/lockscreen aspect and spacing. */
    internal class Dots(private val density: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var lock = false
        var color = -1
        private var opacity = 255
        private var tint: ColorStateList? = null
        private var mode = PorterDuff.Mode.SRC_IN
        private var filter: ColorFilter? = null
        val ratio get() = if (lock) 127f / 72f else 102f / 60f
        override fun getIntrinsicHeight() = ((if (lock) 24f else 20f) * density).roundToInt()
        override fun getIntrinsicWidth() = (intrinsicHeight * ratio).roundToInt()
        override fun onLevelChange(level: Int): Boolean { invalidateSelf(); return true }
        override fun isStateful() = tint?.isStateful == true
        override fun onStateChange(state: IntArray): Boolean { updateFilter(); return true }
        override fun setTintList(tint: ColorStateList?) {
            if (this.tint === tint) return
            this.tint = tint; updateFilter()
        }
        override fun setTintMode(tintMode: PorterDuff.Mode?) {
            val next = tintMode ?: PorterDuff.Mode.SRC_IN
            if (mode == next) return
            mode = next; updateFilter()
        }
        private fun updateFilter() {
            filter = tint?.let { PorterDuffColorFilter(it.getColorForState(state, it.defaultColor), mode) }
            invalidateSelf()
        }
        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty) return
            val sw = if (lock) 127f else 102f; val sh = if (lock) 72f else 60f
            val scale = minOf(bounds.width() / sw, bounds.height() / sh)
            val left = bounds.exactCenterX() - sw * scale / 2
            val cy = bounds.exactCenterY() + if (lock) .5f * scale else 0f
            val radius = (if (lock) 10.5f else 8f) * scale
            paint.color = color; paint.colorFilter = filter
            for (i in 0..4) {
                val filled = i < level.coerceIn(0, 5)
                paint.alpha = opacity
                paint.style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
                paint.strokeWidth = scale
                canvas.drawCircle(left + ((if (lock) 12.5f else 10f) + i * (if (lock) 26f else 21f)) * scale, cy,
                    if (filled) radius else radius - scale * .5f, paint)
            }
        }
        override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { filter = colorFilter; invalidateSelf() }
        @Deprecated("Platform API") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
