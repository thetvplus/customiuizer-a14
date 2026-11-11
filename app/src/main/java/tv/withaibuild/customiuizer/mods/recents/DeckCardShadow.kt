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
    // Keep sibling order on one common plane, with a shallower settled shadow
    // so a dense stack of overlapping outlines does not darken the wallpaper.
    val height = 8f * view.resources.displayMetrics.density
    private var windowOpacity = 0f
    private var awaitingThumbnail = false

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
        val previous=maxOf(contentAlpha,windowOpacity)
        contentAlpha=contentOpacity(fullscreenProgress)
        if(!hasContentBounds() || (awaitingThumbnail && contentAlpha>=.999f)) {
            windowOpacity=0f;awaitingThumbnail=false
        }
        if(previous!=maxOf(contentAlpha,windowOpacity) && owned && !closed && view.outlineProvider===this) view.invalidateOutline()
    }

    override fun getOutline(view: View, outline: Outline) {
        val top = header.height.coerceIn(0, view.height)
        // Use the card's own silhouette on every ROM. A window-space outline
        // can survive a legacy rebase and cast a detached rectangular shadow.
        val opacity=if(hasContentBounds()) maxOf(windowOpacity,contentOpacity(fullscreenProgress)) else 0f
        if (top == view.height || view.width <= 0 || opacity==0f) outline.setEmpty()
        else outline.setRoundRect(0, top, view.width, view.height, corner.coerceAtLeast(0f))
        // The stack's existing pre-draw reconciles native thumbnail writes and
        // fullscreen progress before projection. No second shadow animation.
        outline.alpha=opacity
    }

    private fun hasContentBounds(): Boolean =
        content.visibility==View.VISIBLE && content.width>0 && content.height>0

    private fun contentOpacity(fullscreenProgress: Float): Float =
        if(hasContentBounds() && fullscreenProgress.isFinite())
            content.alpha.coerceIn(0f,1f)*(1f-fullscreenProgress.coerceIn(0f,1f)) else 0f

    fun followWindow(progress: Float) {
        if(!owned || closed || view.outlineProvider!==this) return
        val p=progress.coerceIn(0f,1f)
        awaitingThumbnail=false
        val next=if(hasContentBounds()) p*p*(3f-2f*p) else 0f
        val changed=maxOf(contentAlpha,windowOpacity)!=maxOf(contentAlpha,next)
        windowOpacity=next
        if(changed) view.invalidateOutline()
    }

    fun finishWindow(settled: Boolean) {
        if(windowOpacity==0f) return
        if(!settled || !hasContentBounds() || contentOpacity(fullscreenProgress)>=.999f) clearWindow()
        else awaitingThumbnail=true
    }

    fun clearWindow() {
        if(windowOpacity==0f) return
        val changed=windowOpacity>contentAlpha
        windowOpacity=0f;awaitingThumbnail=false
        if(changed && view.outlineProvider===this) view.invalidateOutline()
    }

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
