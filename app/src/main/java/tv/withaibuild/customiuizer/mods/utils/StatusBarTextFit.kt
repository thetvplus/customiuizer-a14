package tv.withaibuild.customiuizer.mods.utils

import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import tv.withaibuild.customiuizer.utils.HookUtils
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Shared TextView metrics for status-bar custom text. No lifecycle owner, no
 * preference layer: callers apply this on view create, preference change,
 * configuration change, or layout size change — never on a 2s ticker.
 *
 * Default size is kept when it fits. Overflow shrinks downward toward a stored
 * requested size, which is restored when the row grows.
 */
internal object StatusBarTextFit {

    const val MIN_TEXT_SIZE_DP = 6f
    private const val OFFSET_GUARD = "customiuizer_sb_text_offset"
    private const val OFFSET_LISTENER = "customiuizer_sb_text_offset_listener"
    private const val SHRINK_REQUESTED = "customiuizer_sb_text_shrink_requested"
    private const val SHRINK_LINES = "customiuizer_sb_text_shrink_lines"
    private const val SHRINK_SPACING = "customiuizer_sb_text_shrink_spacing"
    private const val SHRINK_LISTENER = "customiuizer_sb_text_shrink_listener"
    private const val HOST_LISTENER = "customiuizer_sb_text_shrink_host_listener"
    private const val HOST_VIEWS = "customiuizer_sb_text_shrink_host_views"
    private const val ATTACH_LISTENER = "customiuizer_sb_text_shrink_attach"
    private const val HOST_WALK = 8
    private const val WINDOW_SUFFIX = "StatusBarWindowView"

    fun applyBoldPreservingFamily(textView: TextView, bold: Boolean) {
        if (!bold) return
        textView.typeface = Typeface.create(textView.typeface, Typeface.BOLD)
    }

    /**
     * KEEP_NATIVE_IF_IT_FITS. [textView.textSize] at this call is the requested
     * size for later restore. Layout retries fit; text-content changes do not.
     */
    fun enableShrinkToFit(
        textView: TextView,
        lineCount: Int,
        lineSpacingMultiplier: Float,
    ) {
        val requestedPx = textView.textSize
        if (requestedPx <= 0f) return
        XposedHelpers.setAdditionalInstanceField(textView, SHRINK_REQUESTED, requestedPx)
        XposedHelpers.setAdditionalInstanceField(textView, SHRINK_LINES, lineCount)
        XposedHelpers.setAdditionalInstanceField(textView, SHRINK_SPACING, lineSpacingMultiplier)
        applyShrinkNow(textView)
        ensureShrinkListeners(textView)
    }

    private fun ensureShrinkListeners(textView: TextView) {
        if (XposedHelpers.getAdditionalInstanceField(textView, SHRINK_LISTENER) == null) {
            val listener = View.OnLayoutChangeListener { v, _, top, _, bottom, _, oldTop, _, oldBottom ->
                if (bottom - top == oldBottom - oldTop && (bottom - top) > 0) return@OnLayoutChangeListener
                val tv = v as? TextView ?: return@OnLayoutChangeListener
                applyShrinkNow(tv)
            }
            textView.addOnLayoutChangeListener(listener)
            XposedHelpers.setAdditionalInstanceField(textView, SHRINK_LISTENER, true)
        }
        bindHostListener(textView)
        if (XposedHelpers.getAdditionalInstanceField(textView, ATTACH_LISTENER) != null) return
        val attach = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                val tv = v as? TextView ?: return
                bindHostListener(tv)
                applyShrinkNow(tv)
            }
            override fun onViewDetachedFromWindow(v: View) {
                unbindHostListener(v as? TextView ?: return)
            }
        }
        textView.addOnAttachStateChangeListener(attach)
        XposedHelpers.setAdditionalInstanceField(textView, ATTACH_LISTENER, true)
    }

    private fun bindHostListener(textView: TextView) {
        if (XposedHelpers.getAdditionalInstanceField(textView, HOST_LISTENER) != null) return
        val parent = textView.parent as? View ?: return
        val listener = View.OnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top == oldBottom - oldTop && (bottom - top) > 0) return@OnLayoutChangeListener
            applyShrinkNow(textView)
        }
        val hosts = ArrayList<View>(HOST_WALK)
        var host: View? = parent
        var steps = 0
        while (host != null && steps < HOST_WALK) {
            host.addOnLayoutChangeListener(listener)
            hosts.add(host)
            if (host.javaClass.name.endsWith(WINDOW_SUFFIX)) break
            host = host.parent as? View
            steps++
        }
        XposedHelpers.setAdditionalInstanceField(textView, HOST_LISTENER, listener)
        XposedHelpers.setAdditionalInstanceField(textView, HOST_VIEWS, hosts)
    }

    private fun unbindHostListener(textView: TextView) {
        val listener = XposedHelpers.getAdditionalInstanceField(textView, HOST_LISTENER)
            as? View.OnLayoutChangeListener
        val hosts = XposedHelpers.getAdditionalInstanceField(textView, HOST_VIEWS) as? ArrayList<*>
        if (listener != null && hosts != null) {
            for (i in hosts.indices) {
                (hosts[i] as? View)?.removeOnLayoutChangeListener(listener)
            }
        }
        XposedHelpers.removeAdditionalInstanceField(textView, HOST_LISTENER)
        XposedHelpers.removeAdditionalInstanceField(textView, HOST_VIEWS)
    }

    private fun applyShrinkNow(textView: TextView) {
        val requestedPx = XposedHelpers.getAdditionalInstanceField(textView, SHRINK_REQUESTED) as? Float ?: return
        val lineCount = XposedHelpers.getAdditionalInstanceField(textView, SHRINK_LINES) as? Int ?: 1
        val lineSpacingMultiplier = XposedHelpers.getAdditionalInstanceField(textView, SHRINK_SPACING) as? Float
            ?: textView.lineSpacingMultiplier
        val parentHeight = resolvedHeight(textView)
        val available = StatusbarViewMaths.availableTextHeightPx(
            parentHeight,
            textView.compoundPaddingTop,
            textView.compoundPaddingBottom,
        )
        if (available <= 0) return
        val minRequested = HookUtils.dp2px(MIN_TEXT_SIZE_DP).coerceAtLeast(1f)
        val fm = textView.paint.fontMetrics
        val fontH = maxOf(fm.bottom - fm.top, fm.descent - fm.ascent, textView.textSize * 1.25f)
        val next = StatusbarViewMaths.fittedTextSizePx(
            requestedPx,
            fontH,
            available,
            lineCount,
            lineSpacingMultiplier,
            minRequested,
        )
        if (abs(next - textView.textSize) > 0.5f) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, next)
        }
    }

    fun applyVerticalOffset(textView: TextView, requestedOffsetPx: Float) {
        XposedHelpers.setAdditionalInstanceField(textView, OFFSET_GUARD, requestedOffsetPx)
        applyVerticalOffsetNow(textView, requestedOffsetPx)
        if (requestedOffsetPx == 0f) return
        if (XposedHelpers.getAdditionalInstanceField(textView, OFFSET_LISTENER) != null) return
        val listener = View.OnLayoutChangeListener { v, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top == oldBottom - oldTop && (bottom - top) > 0) return@OnLayoutChangeListener
            val tv = v as? TextView ?: return@OnLayoutChangeListener
            val requested = XposedHelpers.getAdditionalInstanceField(tv, OFFSET_GUARD) as? Float ?: return@OnLayoutChangeListener
            applyVerticalOffsetNow(tv, requested)
        }
        textView.addOnLayoutChangeListener(listener)
        XposedHelpers.setAdditionalInstanceField(textView, OFFSET_LISTENER, true)
    }

    private fun applyVerticalOffsetNow(textView: TextView, requestedOffsetPx: Float) {
        val parentHeight = resolvedHeight(textView)
        val available = StatusbarViewMaths.availableTextHeightPx(
            parentHeight,
            textView.compoundPaddingTop,
            textView.compoundPaddingBottom,
        )
        val fm = textView.paint.fontMetrics
        val fontH = fm.bottom - fm.top
        val lineCount = textView.maxLines.coerceAtLeast(1)
        val textH = StatusbarViewMaths.occupiedHeightPx(
            fontH,
            lineCount,
            textView.lineSpacingMultiplier,
        ).roundToInt()
        textView.translationY = StatusbarViewMaths.clampVerticalOffsetPx(
            requestedOffsetPx,
            available,
            textH,
        )
    }

    private fun resolvedHeight(textView: TextView): Int {
        var envelope = 0
        var host: View? = textView.parent as? View
        var steps = 0
        while (host != null && steps < HOST_WALK) {
            val height = host.height
            if (height > 0) {
                envelope = if (envelope > 0) minOf(envelope, height) else height
            }
            if (host.javaClass.name.endsWith(WINDOW_SUFFIX)) break
            host = host.parent as? View
            steps++
        }
        return StatusbarViewMaths.resolvedTextFitHeightPx(textView.height, envelope)
    }
}
