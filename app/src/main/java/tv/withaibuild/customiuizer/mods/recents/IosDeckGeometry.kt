package tv.withaibuild.customiuizer.mods.recents

import android.graphics.RectF
import kotlin.math.exp
import kotlin.math.ln

/** 22G100 Deck geometry, mapped to native Android scroll units and card bounds.
 * The native scroller/velocity/friction remains authoritative. Frame x is an
 * offset of a full-screen-width frame, followed by center scaling, not its final
 * visible left edge. A predicted endpoint uses one cursor for position and scale. */
internal class IosDeckGeometry(val width: Float, val baseWidth: Float, density: Float, val pageScale: Float = .75f) {
    private val translation = -18f * density
    private val margin = width * (1f-pageScale)*.5f
    private val minimumDepth = ln((margin - translation) / width) / 9f

    fun scale(index: Int, currentCursor: Float): Float =
        (pageScale * (1f + .12f * (minimumDepth + .13f * (currentCursor-index)))).coerceIn(.05f,1.5f) * width/baseWidth

    fun left(index: Int, cursor: Float, viewportLeft: Float): Float {
        val depth = minimumDepth + .13f * (cursor-index)
        val renderedScale = scale(index,cursor) * baseWidth/width
        val leading = (translation + width*exp(9f*depth) - margin) / pageScale * renderedScale
        return viewportLeft + leading + (width-baseWidth)*.5f
    }

    // The previous 1.5 cursor cutoff can still leave the card's leading edge
    // inside the viewport, especially at larger page sizes. Keep a bounded
    // tail and let Scope's rendered-rectangle intersection decide recycling.
    fun candidate(index: Int, count: Int, cursor: Float) = index in 0 until count && index-cursor in -3f..6f

    // Pool visibility must not invent a far-left origin for a card leaving on
    // the right. Bound the exponential's invisible tail, preserving direction.
    fun presentationCursor(index: Int,cursor: Float)=cursor.coerceIn(index-6f,index+3f)

    fun content(rect: RectF, scale: Float, header: Float) {
        val x=rect.centerX(); val y=rect.centerY()
        rect.set(x+(rect.left-x)*scale,y+(rect.top+header-y)*scale,
            x+(rect.right-x)*scale,y+(rect.bottom-y)*scale)
    }
}

/** A14 mapping of current container bounds, never a rotated portrait frame.
 * A uniform presentation scale leaves native swipe thresholds and root pose alone. */
internal object DeckViewport {
    fun pageScale(width: Float, height: Float, cardWidth: Float, cardHeight: Float, percent: Int): Float {
        val requested = if (percent == 0) .75f else percent.coerceIn(55,85)/100f
        return minOf(requested, .9f*height*cardWidth/(width*cardHeight)).coerceAtLeast(.05f)
    }
    fun top(nativeTop: Float, safeTop: Float, safeHeight: Float, cardHeight: Float, landscape: Boolean): Float =
        if (landscape) safeTop+(safeHeight-cardHeight)*.5f else nativeTop
}
