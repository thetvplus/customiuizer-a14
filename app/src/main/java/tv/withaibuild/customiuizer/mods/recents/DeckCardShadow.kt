package tv.withaibuild.customiuizer.mods.recents

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import tv.withaibuild.customiuizer.mods.utils.CleanupActions
import kotlin.math.roundToInt

/** The TaskView foreground records the bottom edge in the same RenderNode as the thumbnail. */
internal class DeckCardShadow(private val view: View, private val content: View,
    private var fullscreenProgress: Float) : Drawable() {
    private val originalOutline = view.outlineProvider
    private val depth = 5f * view.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, 0f, depth, intArrayOf(0x24000000, 0x0a000000, 0), null, Shader.TileMode.CLAMP)
    }
    private val unlitOutline = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            originalOutline?.getOutline(view, outline)
            outline.alpha = 0f
        }
    }
    private var closed = false
    private var contentAlpha = 0
    private var left = 0f
    private var right = 0f
    private var bottom = 0f
    val owned = (view.parent as? ViewGroup)?.clipChildren == false && !view.clipToOutline && view.foreground == null

    init {
        if (owned) {
            try {
                view.outlineProvider = unlitOutline
                view.foreground = this
            } catch (error: Throwable) {
                val cleanup = CleanupActions(error)
                cleanup.run { close() }
                cleanup.finish()
            }
        }
    }

    fun syncContentAlpha(fullscreenProgress: Float) {
        if (!owned || closed) return
        if (view.foreground !== this) {
            close()
            return
        }
        this.fullscreenProgress = fullscreenProgress
        if (updateInputs()) view.invalidate()
    }

    override fun draw(canvas: Canvas) {
        if (!owned || closed) return
        // The first display list can record before the first Deck pre-draw.
        updateInputs()
        if (contentAlpha == 0) return
        paint.alpha = contentAlpha
        val save = canvas.save()
        canvas.translate(0f, bottom)
        canvas.drawRoundRect(left, 0f, right, depth, depth, depth, paint)
        canvas.restoreToCount(save)
    }

    private fun updateInputs(): Boolean {
        val alpha = contentOpacity()
        if (alpha != 0) {
            val inset = minOf(depth * 2f, content.width * .12f)
            val nextLeft = maxOf(0, content.left) + inset
            val nextRight = minOf(view.width, content.right) - inset
            val nextBottom = minOf(view.height, content.bottom).toFloat()
            if (nextRight - nextLeft >= depth * 2f && nextBottom > 0f) {
                if (contentAlpha == alpha && left == nextLeft && right == nextRight && bottom == nextBottom) return false
                contentAlpha = alpha
                left = nextLeft
                right = nextRight
                bottom = nextBottom
                return true
            }
        }
        if (contentAlpha == 0) return false
        contentAlpha = 0
        return true
    }

    private fun contentOpacity(): Int {
        if (view.clipToOutline || content.parent !== view || content.visibility != View.VISIBLE || content.width <= 0 ||
            content.height <= 0 || !fullscreenProgress.isFinite()) return 0
        val alpha = content.alpha
        if (!alpha.isFinite()) return 0
        return (255f * alpha.coerceIn(0f, 1f) * (1f - fullscreenProgress.coerceIn(0f, 1f))).roundToInt()
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Required by Drawable") override fun getOpacity() = PixelFormat.TRANSLUCENT

    fun close() {
        if (closed || !owned) return
        closed = true
        val cleanup = CleanupActions()
        cleanup.run { if (view.foreground === this) view.foreground = null }
        cleanup.run { if (view.outlineProvider === unlitOutline) view.outlineProvider = originalOutline }
        cleanup.finish()
    }
}
