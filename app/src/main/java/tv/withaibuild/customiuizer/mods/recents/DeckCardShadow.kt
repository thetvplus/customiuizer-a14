package tv.withaibuild.customiuizer.mods.recents

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import tv.withaibuild.customiuizer.mods.utils.CleanupActions

/** The TaskView RenderNode projects the visible thumbnail in the card's own pose. */
internal class DeckCardShadow(private val view: View, private val header: View, radius: Float,
    private val content: View,private var fullscreenProgress: Float) :
    ViewOutlineProvider(), View.OnLayoutChangeListener {
    private val original = view.outlineProvider
    private var corner = radius
    private var contentAlpha = contentOpacity(fullscreenProgress)
    private var closed = false
    // A clipped root would cut its header with this content-only outline.
    val owned = !view.clipToOutline
    // Keep sibling order on one common plane, with a shallower settled shadow
    // so a dense stack of overlapping outlines does not darken the wallpaper.
    val height = 8f * view.resources.displayMetrics.density

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
        if(!owned || closed) return
        this.fullscreenProgress=fullscreenProgress
        val opacity=contentOpacity(fullscreenProgress)
        if(contentAlpha==opacity) return
        contentAlpha=opacity
        if(view.outlineProvider===this) view.invalidateOutline()
    }

    override fun getOutline(view: View, outline: Outline) {
        val top = header.height.coerceIn(0, view.height)
        // Native can update the thumbnail between binding and the first outline.
        val opacity=contentOpacity(fullscreenProgress)
        if (top == view.height || view.width <= 0 || opacity==0f) outline.setEmpty()
        else outline.setRoundRect(0, top, view.width, view.height, corner.coerceAtLeast(0f))
        outline.alpha=opacity
    }

    private fun hasContentBounds(): Boolean =
        content.visibility==View.VISIBLE && content.width>0 && content.height>0

    private fun contentOpacity(fullscreenProgress: Float): Float =
        if(hasContentBounds() && fullscreenProgress.isFinite())
            content.alpha.coerceIn(0f,1f)*(1f-fullscreenProgress.coerceIn(0f,1f)) else 0f

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
