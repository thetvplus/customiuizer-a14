package tv.withaibuild.customiuizer.mods.island

import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Rect
import android.graphics.Outline
import android.graphics.Path
import android.view.View
import android.view.ViewGroup
import kotlin.math.ceil

/** One short-lived display list, never a bitmap or a second live activity.
 * Capturing the last drawn content freezes clocks/waves without another lease. */
internal class IslandContentSnapshot {
    private val node = RenderNode("Island content handoff")
    private var width = 0
    private var height = 0
    private var blur = -1
    private val clip = Rect()
    private val outline = Outline()
    private val outlinePath = Path()
    var ready = false
        private set

    fun capture(owner: View, content: View, sx: Float, sy: Float): Boolean {
        if (!owner.isHardwareAccelerated || owner.width <= 0 || owner.height <= 0) return false
        clear()
        width = ceil(owner.width * sx).toInt().coerceAtLeast(1)
        height = ceil(owner.height * sy).toInt().coerceAtLeast(1)
        node.setPosition(0, 0, width, height)
        val canvas = node.beginRecording(width, height)
        try {
            canvas.scale(sx, sy)
            record(canvas, content)
        } finally { node.endRecording() }
        ready = true
        return true
    }

    /** The content containers are transparent Frame/LinearLayouts. Calling
     * ViewGroup.draw on a RecordingCanvas would retain mutable child RenderNodes;
     * record the leaf commands instead, preserving each ancestor's presentation. */
    private fun record(canvas: Canvas, child: View) {
        if (child.visibility != View.VISIBLE || child.alpha <= .001f) return
        val save = canvas.save()
        canvas.translate(child.left.toFloat(), child.top.toFloat())
        canvas.concat(child.matrix)
        if ((child.parent as? ViewGroup)?.clipChildren == true)
            canvas.clipRect(0, 0, child.width, child.height)
        if (child.getClipBounds(clip)) canvas.clipRect(clip)
        if (child.clipToOutline) {
            outline.setEmpty()
            child.outlineProvider?.getOutline(child, outline)
            // Our photo leaf uses the existing square icon's round-rect / circle
            // background. View.draw alone does not apply this RenderNode clip.
            if (outline.getRect(clip)) {
                outlinePath.rewind()
                outlinePath.addRoundRect(clip.left.toFloat(), clip.top.toFloat(),
                    clip.right.toFloat(), clip.bottom.toFloat(), outline.radius, outline.radius, Path.Direction.CW)
                canvas.clipPath(outlinePath)
            }
        }
        if (child.alpha < 1f) canvas.saveLayerAlpha(0f, 0f, child.width.toFloat(), child.height.toFloat(),
            (child.alpha * 255).toInt())
        if (child is ViewGroup) {
            if (child.clipToPadding) canvas.clipRect(child.paddingLeft, child.paddingTop,
                child.width - child.paddingRight, child.height - child.paddingBottom)
            canvas.translate(-child.scrollX.toFloat(), -child.scrollY.toFloat())
            for (index in 0 until child.childCount) record(canvas, child.getChildAt(index))
        } else child.draw(canvas)
        canvas.restoreToCount(save)
    }

    fun pose(alpha: Float, level: Int, effect: RenderEffect?) {
        node.alpha = alpha
        if (blur != level) { blur = level; node.setRenderEffect(effect) }
    }

    fun draw(canvas: Canvas, targetWidth: Int, targetHeight: Int, sx: Float, sy: Float) {
        if (!ready || !canvas.isHardwareAccelerated) return
        val save = canvas.save()
        canvas.translate(targetWidth * .5f, targetHeight * .5f)
        canvas.scale(1f / sx, 1f / sy)
        canvas.translate(-width * .5f, -height * .5f)
        canvas.drawRenderNode(node)
        canvas.restoreToCount(save)
    }

    fun clear() {
        ready = false; blur = -1
        node.setRenderEffect(null); node.alpha = 1f
        node.discardDisplayList()
    }
}
