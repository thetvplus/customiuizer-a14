package tv.withaibuild.customiuizer.mods.recents

import android.graphics.Outline
import android.graphics.Rect
import android.graphics.RectF
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
    private val windowBounds = Rect()
    private var windowOpacity = -1f
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
        val next=contentOpacity(fullscreenProgress)
        val changed=contentAlpha!=next
        contentAlpha=next
        if(awaitingThumbnail && next>=.999f) clearWindow()
        else if(windowOpacity<0f && changed && owned && !closed && view.outlineProvider===this) view.invalidateOutline()
    }

    override fun getOutline(view: View, outline: Outline) {
        val top = header.height.coerceIn(0, view.height)
        if(windowOpacity>=0f && !awaitingThumbnail) {
            if(windowOpacity==0f || windowBounds.isEmpty) outline.setEmpty()
            else outline.setRoundRect(windowBounds,corner.coerceAtLeast(0f)*windowBounds.width()/view.width.coerceAtLeast(1))
            outline.alpha=windowOpacity
            return
        }
        val opacity=if(awaitingThumbnail) 1f else contentOpacity(fullscreenProgress)
        if (top == view.height || view.width <= 0 || opacity==0f) outline.setEmpty()
        else outline.setRoundRect(0, top, view.width, view.height, corner.coerceAtLeast(0f))
        // The stack's existing pre-draw reconciles native thumbnail writes and
        // fullscreen progress before projection. No second shadow animation.
        outline.alpha=opacity
    }

    private fun contentOpacity(fullscreenProgress: Float): Float =
        if(content.visibility==View.VISIBLE && content.width>0 && content.height>0 && fullscreenProgress.isFinite())
            content.alpha.coerceIn(0f,1f)*(1f-fullscreenProgress.coerceIn(0f,1f)) else 0f

    fun projectWindow(bounds: RectF,progress: Float) {
        if(!owned || closed || view.outlineProvider!==this) return
        val p=progress.coerceIn(0f,1f)
        val opacity=p*p*(3f-2f*p)
        val l=kotlin.math.round(bounds.left).toInt();val t=kotlin.math.round(bounds.top).toInt()
        val r=kotlin.math.round(bounds.right).toInt();val b=kotlin.math.round(bounds.bottom).toInt()
        if(!awaitingThumbnail && windowOpacity==opacity && windowBounds.left==l && windowBounds.top==t &&
            windowBounds.right==r && windowBounds.bottom==b) return
        awaitingThumbnail=false;windowOpacity=opacity;windowBounds.set(l,t,r,b)
        view.invalidateOutline()
    }

    fun finishWindow(settled: Boolean) {
        if(windowOpacity<0f) return
        if(!settled || contentOpacity(fullscreenProgress)>=.999f) clearWindow()
        else { awaitingThumbnail=true; view.invalidateOutline() }
    }

    fun clearWindow() {
        if(windowOpacity<0f) return
        windowOpacity=-1f;awaitingThumbnail=false
        if(view.outlineProvider===this) view.invalidateOutline()
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
