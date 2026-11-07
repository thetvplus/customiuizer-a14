package tv.withaibuild.customiuizer.mods.recents

import android.graphics.RectF
import android.view.View
import tv.withaibuild.customiuizer.mods.utils.CleanupActions

/** Native opening-window clock, adapted to the Deck's unequal card spacing.
 * The native formula assumes every neighboring center is one card-width apart.
 * Capture the actual presentation once; retain its depth matrix and task order. */
internal class DeckOpenAlignment(val selectedId: Int,private val start: RectF) {
    private class Pose(val view: View,val header: View,val centerX: Float,val centerY: Float,val slots: Int) {
        val x=view.translationX; val y=view.translationY
        val sx=view.scaleX; val sy=view.scaleY
        val px=view.pivotX; val py=view.pivotY; val alpha=header.alpha
        var lastX=x; var lastY=y; var lastScaleX=sx; var lastScaleY=sy; var lastAlpha=alpha
        fun apply(source: RectF,current: RectF,ratio: Float,progress: Float,headerAlpha: Float) {
            // Center-pivot scaling preserves the owned center-scale matrix.
            view.pivotX=view.width*.5f; view.pivotY=view.height*.5f
            lastScaleX=sx*ratio; lastScaleY=sy*ratio
            lastX=x+current.centerX()-source.centerX()+(centerX-source.centerX())*(ratio-1f)
            // Deck overlaps at rest. Unfold those actual offsets into the native
            // fullscreen slots as the same window progress advances; otherwise
            // the front card still covers a wide strip at the final handoff.
            lastX+=(slots*current.width()-(centerX-source.centerX())*ratio)*progress+slots*25f*progress
            lastY=y+current.centerY()-source.centerY()+(centerY-source.centerY())*(ratio-1f)
            view.scaleX=lastScaleX; view.scaleY=lastScaleY
            view.translationX=lastX; view.translationY=lastY
            lastAlpha=headerAlpha; header.alpha=lastAlpha
        }
        fun release() {
            if(view.translationX==lastX) view.translationX=x
            if(view.translationY==lastY) view.translationY=y
            if(view.scaleX==lastScaleX) view.scaleX=sx
            if(view.scaleY==lastScaleY) view.scaleY=sy
            if(view.pivotX==view.width*.5f) view.pivotX=px
            if(view.pivotY==view.height*.5f) view.pivotY=py
            if(header.alpha==lastAlpha) header.alpha=alpha
        }
    }
    private val neighbors=ArrayList<Pose>(8)
    fun add(view: View,header: View,content: RectF,slots: Int) {
        neighbors.add(Pose(view,header,content.centerX(),content.centerY(),slots))
    }
    fun apply(current: RectF,progress: Float,headerAlpha: Float) {
        if(start.width()<=0f || current.isEmpty) return
        val ratio=current.width()/start.width()
        if(!ratio.isFinite() || ratio<=0f) return
        val fraction=progress.coerceIn(0f,1f)
        for(i in 0 until neighbors.size) neighbors[i].apply(start,current,ratio,fraction,headerAlpha)
    }
    fun release() {
        val cleanup=CleanupActions()
        for(pose in neighbors) cleanup.run { pose.release() }
        neighbors.clear(); cleanup.finish()
    }
}
