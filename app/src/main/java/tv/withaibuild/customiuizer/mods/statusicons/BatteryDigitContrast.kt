package tv.withaibuild.customiuizer.mods.statusicons

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.view.View
import android.widget.TextView
import java.lang.ref.WeakReference

/** One native TextView, one shader; no second percentage or drawing callback. */
internal class BatteryDigitContrast(icon: View, text: TextView) : View.OnLayoutChangeListener,
    View.OnAttachStateChangeListener {
    private val icon = WeakReference(icon)
    private val text = WeakReference(text)
    private val iconLocation = IntArray(2)
    private val textLocation = IntArray(2)
    private var nativeShader = text.paint.shader
    private var appliedShader: Shader? = null
    private var level = 0
    private var base = Color.WHITE
    private var fill = Color.WHITE
    private var boundary = Float.NaN
    private var lastBase = 0
    private var lastInside = 0
    private var closed = false

    init {
        icon.addOnLayoutChangeListener(this); text.addOnLayoutChangeListener(this)
        icon.addOnAttachStateChangeListener(this); text.addOnAttachStateChangeListener(this)
    }

    fun matches(icon: View, text: TextView) = !closed && this.icon.get() === icon && this.text.get() === text

    fun update(level: Int, base: Int, fill: Int) {
        if (closed) return
        this.level = level.coerceIn(0, 100); this.base = base; this.fill = fill
        refreshGeometry()
    }

    private fun refreshGeometry() {
        if (closed) return
        val icon = icon.get() ?: return
        val text = text.get() ?: return
        val width = icon.width - icon.paddingLeft - icon.paddingRight
        val height = icon.height - icon.paddingTop - icon.paddingBottom
        if (width <= 0 || height <= 0 || text.width <= 0 || icon.rootView !== text.rootView) return
        val scale = minOf(width / 22f, height / 11.5f)
        icon.getLocationInWindow(iconLocation); text.getLocationInWindow(textLocation)
        // TextView translates its Layout canvas by compoundPaddingLeft before
        // painting glyphs. Express the icon's real fill edge in that same space.
        val edge = iconLocation[0] + icon.paddingLeft + (width - 22f * scale) * .5f +
            (1.8f + 16.7f * level / 100f) * scale - textLocation[0] - text.compoundPaddingLeft
        val inside = if (Color.luminance(fill) > .179f) Color.BLACK else Color.WHITE
        if (edge == boundary && base == lastBase && inside == lastInside && text.paint.shader === appliedShader) return
        val current = text.paint.shader
        if (current !== appliedShader) nativeShader = current
        // A narrow transition follows the antialiased fill edge; every glyph is
        // still painted exactly once by the native TextView in its native font.
        val shader = LinearGradient(edge - .01f, 0f, edge + .01f, 0f, inside, base, Shader.TileMode.CLAMP)
        boundary = edge; lastBase = base; lastInside = inside; appliedShader = shader
        text.paint.shader = shader
        text.invalidate()
    }

    fun close() {
        if (closed) return
        closed = true
        icon.get()?.let { it.removeOnLayoutChangeListener(this); it.removeOnAttachStateChangeListener(this) }
        text.get()?.let {
            it.removeOnLayoutChangeListener(this); it.removeOnAttachStateChangeListener(this)
            if (appliedShader != null && it.paint.shader === appliedShader) {
                it.paint.shader = nativeShader; it.invalidate()
            }
        }
        appliedShader = null
        icon.clear(); text.clear()
    }

    override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, oldL: Int, oldT: Int, oldR: Int, oldB: Int) = refreshGeometry()
    override fun onViewAttachedToWindow(v: View) = refreshGeometry()
    override fun onViewDetachedFromWindow(v: View) = close()
}
