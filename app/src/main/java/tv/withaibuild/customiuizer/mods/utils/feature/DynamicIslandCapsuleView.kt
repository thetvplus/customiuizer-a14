package tv.withaibuild.customiuizer.mods.utils.feature

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.widget.FrameLayout

/**
 * Module-owned Dynamic Island capsule and the single shape owner of the island.
 *
 * One cached [Path] defines both the painted pill and the clip applied to every child, so the
 * black area the user sees and the area ROM content is allowed to draw in can never disagree.
 * The path is rebuilt only in [onSizeChanged]; drawing allocates nothing.
 *
 * The pill is painted directly on the [Canvas] instead of being delegated to a
 * [android.graphics.drawable.GradientDrawable] background. HyperOS 1 enables
 * `persist.sys.support_view_smoothcorner` and substitutes its own smooth-corner implementation for
 * the platform rounded-rect drawables, so a `GradientDrawable` background does not reproduce the
 * measured View bounds exactly and the resulting pill loses pixels at one edge.
 *
 * Rounded clipping is done with [Canvas.clipPath] in [dispatchDraw]. An
 * [android.view.ViewOutlineProvider] plus `clipToOutline` would be a second, independently
 * rasterised rounded shape on the same View, so outline clipping is deliberately left off.
 */
@SuppressLint("ViewConstructor")
internal class DynamicIslandCapsuleView(context: Context) : FrameLayout(context) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }
    private val shapeRect = RectF()
    private val shapePath = Path()

    init {
        setWillNotDraw(false)
        clipToOutline = false
        background = null
    }

    /** Capsule fill colour. Kept settable so a future themed island can reuse this View. */
    var fillColor: Int
        get() = fillPaint.color
        set(value) {
            if (fillPaint.color != value) {
                fillPaint.color = value
                invalidate()
            }
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shapeRect.set(0f, 0f, w.toFloat(), h.toFloat())
        val radius = h / 2f
        shapePath.reset()
        if (w > 0 && h > 0) {
            shapePath.addRoundRect(shapeRect, radius, radius, Path.Direction.CW)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (shapePath.isEmpty) return
        canvas.drawPath(shapePath, fillPaint)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (shapePath.isEmpty) {
            super.dispatchDraw(canvas)
            return
        }
        val save = canvas.save()
        canvas.clipPath(shapePath)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }
}
