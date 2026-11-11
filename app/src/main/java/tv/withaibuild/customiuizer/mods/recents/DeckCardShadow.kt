package tv.withaibuild.customiuizer.mods.recents

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import tv.withaibuild.customiuizer.mods.utils.CleanupActions

/** One native projection on the TaskView RenderNode. It follows the card's
 * existing matrix/alpha and never borrows the application's Surface. */
internal class DeckCardShadow(private val view: View, private val header: View, radius: Float,
    private val content: View,private var fullscreenProgress: Float) :
    ViewOutlineProvider(), View.OnLayoutChangeListener {
    private val original = view.outlineProvider
    private var corner = radius
    private var contentAlpha = contentOpacity(fullscreenProgress)
    private var closed = false
    // A clipped root would cut its header with this content-only outline.
    val owned = !view.clipToOutline
    // Every sibling shares this plane, including a card retaining a native
    // clipped outline. Mixing planes would make shadow capability reorder cards.
    val height = 16f * view.resources.displayMetrics.density

    init {
        if (owned) {
            try {
                view.outlineProvider = this
                view.addOnLayoutChangeListener(this)
                header.addOnLayoutChangeListener(this)
            } catch (error: Throwable) {
                val cleanup=CleanupActions(error); cleanup.run { close() }; cleanup.finish()
            }
        }
    }

    fun radius(value: Float) {
        if (corner == value) return
        corner = value
        if (owned && !closed && view.outlineProvider === this) view.invalidateOutline()
    }

    fun syncContentAlpha(fullscreenProgress: Float) {
        this.fullscreenProgress=fullscreenProgress
        val next=contentOpacity(fullscreenProgress)
        if(contentAlpha==next) return
        contentAlpha=next
        if(owned && !closed && view.outlineProvider===this) view.invalidateOutline()
    }

    override fun getOutline(view: View, outline: Outline) {
        val top = header.height.coerceIn(0, view.height)
        val opacity=contentOpacity(fullscreenProgress)
        if (top == view.height || view.width <= 0 || opacity==0f) outline.setEmpty()
        else outline.setRoundRect(0, top, view.width, view.height, corner.coerceAtLeast(0f))
        // The stack's existing pre-draw reconciles native thumbnail writes and
        // fullscreen progress before projection. No second shadow animation.
        outline.alpha=opacity
    }

    private fun contentOpacity(fullscreenProgress: Float): Float =
        if(content.visibility==View.VISIBLE && content.width>0 && content.height>0 && fullscreenProgress.isFinite())
            content.alpha.coerceIn(0f,1f)*(1f-fullscreenProgress.coerceIn(0f,1f)) else 0f

    fun surfaceShare(): Float = if(!owned || closed) 0f else 1f-contentOpacity(fullscreenProgress)*
        (if(view.visibility==View.VISIBLE) view.alpha.coerceIn(0f,1f) else 0f)

    override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
        if (!closed && view.outlineProvider === this) view.invalidateOutline()
    }

    fun close() {
        if (closed || !owned) return
        closed = true
        view.removeOnLayoutChangeListener(this); header.removeOnLayoutChangeListener(this)
        if (view.outlineProvider === this) view.outlineProvider = original
    }
}
