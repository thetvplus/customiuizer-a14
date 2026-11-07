package tv.withaibuild.customiuizer.mods.recents

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import tv.withaibuild.customiuizer.mods.utils.CleanupActions

/** One native projection on the TaskView RenderNode. It follows the card's
 * existing matrix/alpha and never borrows the application's Surface. */
internal class DeckCardShadow(private val view: View, private val header: View, radius: Float,
    private val content: View) :
    ViewOutlineProvider(), View.OnLayoutChangeListener {
    private val original = view.outlineProvider
    private val ambient = view.outlineAmbientShadowColor
    private val spot = view.outlineSpotShadowColor
    private var corner = radius
    private var contentAlpha = content.alpha
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
                view.outlineAmbientShadowColor = AMBIENT
                view.outlineSpotShadowColor = SPOT
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

    fun syncContentAlpha() {
        val next=content.alpha.coerceIn(0f,1f)
        if(contentAlpha==next) return
        contentAlpha=next
        if(owned && !closed && view.outlineProvider===this) view.invalidateOutline()
    }

    override fun getOutline(view: View, outline: Outline) {
        val top = header.height.coerceIn(0, view.height)
        if (top == view.height || view.width <= 0) outline.setEmpty()
        else outline.setRoundRect(0, top, view.width, view.height, corner.coerceAtLeast(0f))
        // Registration can follow a native setter, and binding can still update
        // the thumbnail afterwards. Read the real content on the first draw.
        outline.alpha=content.alpha.coerceIn(0f,1f)
    }

    override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
        if (!closed && view.outlineProvider === this) view.invalidateOutline()
    }

    fun close() {
        if (closed || !owned) return
        closed = true
        view.removeOnLayoutChangeListener(this); header.removeOnLayoutChangeListener(this)
        if (view.outlineProvider === this) view.outlineProvider = original
        if (view.outlineAmbientShadowColor == AMBIENT) view.outlineAmbientShadowColor = ambient
        if (view.outlineSpotShadowColor == SPOT) view.outlineSpotShadowColor = spot
    }

    companion object {
        // Android projection tuning, not recovered CoreMaterial filter values.
        private const val AMBIENT = 0x24000000
        private const val SPOT = 0x50000000
    }
}
