package tv.withaibuild.customiuizer.mods.recents

import android.graphics.RectF
import android.graphics.Matrix
import android.view.View
import tv.withaibuild.customiuizer.mods.utils.CleanupActions

/** Native opening-window clock, adapted to the Deck's unequal card spacing.
 * The native formula assumes every neighboring center is one card-width apart.
 * Capture the actual presentation once; retain its depth matrix and task order. */
internal class DeckOpenAlignment(val selectedId: Int,private val start: RectF) {
    private class Pose(val view: View,val header: View,val centerX: Float,val centerY: Float,val width: Float,val height: Float,
        val slots: Int,val scale: Float,val exitDelta: Float) {
        val x=view.translationX; val y=view.translationY
        val sx=view.scaleX; val sy=view.scaleY
        val px=view.pivotX; val py=view.pivotY; val alpha=header.alpha
        private val shownAlpha=view.alpha
        private var nativeAlpha=shownAlpha
        private var ownsAlpha=false
        var lastX=x; var lastY=y; var lastScaleX=sx; var lastScaleY=sy; var lastAlpha=alpha
        fun apply(source: RectF,current: RectF,ratio: Float,progress: Float,frontAdvance: Float,headerAlpha: Float,nativeFaded: Boolean) {
            if(nativeFaded) {
                // This callback is known to have written alpha, even if its value
                // equals ours on a reversed first/last frame. Keep its latest
                // state for release while the Deck's captured pose stays visible.
                nativeAlpha=view.alpha; ownsAlpha=true
                if(view.alpha!=shownAlpha) view.alpha=shownAlpha
            }
            if(slots>0) {
                lastX=x+(frontAdvance+exitDelta*progress*progress)/scale
                if(view.translationX!=lastX) view.translationX=lastX
                return
            }
            // Center-pivot scaling preserves the owned center-scale matrix.
            view.pivotX=view.width*.5f; view.pivotY=view.height*.5f
            lastScaleX=sx*ratio; lastScaleY=sy*ratio
            // Deck overlaps at rest. Unfold those actual offsets into the native
            // fullscreen slots as the same window progress advances; otherwise
            // the front card still covers a wide strip at the final handoff.
            val dx=offset(source,current,centerX,slots,ratio,progress)
            // Visual deltas pass through the owned center-scale matrix. Undo
            // that scale once, and retain the old pivot's visible center.
            lastX=x+(1f-sx)*(px-view.width*.5f)+dx/scale
            lastY=y+(1f-sy)*(py-view.height*.5f)+
                verticalOffset(source,current,centerY,ratio)/scale
            view.scaleX=lastScaleX; view.scaleY=lastScaleY
            view.translationX=lastX; view.translationY=lastY
            lastAlpha=headerAlpha; header.alpha=lastAlpha
        }
        fun release() {
            if(view.translationX==lastX) view.translationX=x
            if(slots<=0) {
                if(view.translationY==lastY) view.translationY=y
                if(view.scaleX==lastScaleX) view.scaleX=sx
                if(view.scaleY==lastScaleY) view.scaleY=sy
                if(view.pivotX==view.width*.5f) view.pivotX=px
                if(view.pivotY==view.height*.5f) view.pivotY=py
                if(header.alpha==lastAlpha) header.alpha=alpha
            }
            if(ownsAlpha && view.alpha==shownAlpha) view.alpha=nativeAlpha
        }
    }
    private val neighbors=ArrayList<Pose>(8)
    private var frontEdge=Float.POSITIVE_INFINITY
    private var minimumExit=Float.POSITIVE_INFINITY
    var surface: Surface?=null
        private set
    fun add(view: View,header: View,content: RectF,slots: Int,scale: Float=1f,exitLeft: Float=start.right) {
        val exit=(exitLeft-content.left).coerceAtLeast(0f)
        neighbors.add(Pose(view,header,content.centerX(),content.centerY(),content.width(),content.height(),slots,scale,exit))
        if(slots>0 && view.visibility==View.VISIBLE && view.alpha>.01f) {
            frontEdge=minOf(frontEdge,content.left)
            minimumExit=minOf(minimumExit,exit)
        }
    }
    /** The Legacy Surface clock runs before its posted UI callback. Capture only
     * immutable geometry here; its native frame can evaluate the same edge off Main. */
    class Surface internal constructor(val taskId: Int,val width: Float,val height: Float,
        private val start: RectF,private val toLocal: Matrix,private val axisX: Float,private val originX: Float,
        private val axisY: Float,private val originY: Float,private val frontEdge: Float,private val minimumExit: Float,
        private val fronts: FloatArray) {
        @Volatile var active=true
            internal set
        private var prepared=false
        private val bounds=FloatArray(fronts.size/5*4)
        private val current=RectF()
        internal val nativeBounds=RectF()
        internal val nativeMatrix=FloatArray(9)
        fun update(window: RectF,progress: Float) {
            prepared=false
            if(!active || !progress.isFinite()) return
            toLocal.mapRect(current,window)
            if(current.isEmpty || !current.right.isFinite()) return
            val p=progress.coerceIn(0f,1f)
            val advance=frontAdvance(start,current,frontEdge,minimumExit,p)
            var i=0;var at=0
            while(i<fronts.size) {
                val dx=advance+fronts[i+4]*p*p
                bounds[at++]=axisX*(fronts[i]+dx)+originX;bounds[at++]=axisY*fronts[i+1]+originY
                bounds[at++]=axisX*(fronts[i+2]+dx)+originX;bounds[at++]=axisY*fronts[i+3]+originY
                i+=5
            }
            prepared=true
        }
        fun boundary(surface: RectF): Float {
            if(!active || !prepared) return Float.NaN
            var edge=Float.POSITIVE_INFINITY;var at=0
            while(at<bounds.size) {
                if(bounds[at]<surface.right && surface.left<bounds[at+2] && bounds[at+1]<surface.bottom && surface.top<bounds[at+3])
                    edge=minOf(edge,bounds[at])
                at+=4
            }
            return edge
        }
    }
    fun prepareSurface(toLocal: Matrix,toSurface: Matrix,width: Float,height: Float) {
        fun front(pose: Pose)=pose.slots>0 && pose.view.visibility==View.VISIBLE && pose.view.alpha>.01f
        if(neighbors.none(::front) || start.width()<=0f) return
        val values=FloatArray(9);toSurface.getValues(values)
        if(!DeckSurfaceOcclusion.axisAligned(values)) return
        val fronts=FloatArray(neighbors.count(::front)*5)
        var at=0
        for(pose in neighbors) if(front(pose)) {
            fronts[at++]=pose.centerX-pose.width*.5f;fronts[at++]=pose.centerY-pose.height*.5f
            fronts[at++]=pose.centerX+pose.width*.5f;fronts[at++]=pose.centerY+pose.height*.5f
            fronts[at++]=pose.exitDelta
        }
        surface=Surface(selectedId,width,height,RectF(start),Matrix(toLocal),values[0],values[2],values[4],values[5],frontEdge,minimumExit,fronts)
    }
    fun apply(current: RectF,progress: Float,headerAlpha: Float,nativeFaded: Boolean=false) {
        if(start.width()<=0f || current.isEmpty) return
        val ratio=current.width()/start.width()
        if(!ratio.isFinite() || ratio<=0f) return
        val fraction=progress.coerceIn(0f,1f)
        val advance=frontAdvance(start,current,frontEdge,minimumExit,fraction)
        for(i in 0 until neighbors.size) neighbors[i].apply(start,current,ratio,fraction,advance,headerAlpha,nativeFaded)
    }
    fun release() {
        surface?.active=false
        val cleanup=CleanupActions()
        for(pose in neighbors) cleanup.run { pose.release() }
        neighbors.clear(); cleanup.finish()
    }
    companion object {
        // Advance the whole layout only when the app would otherwise cover more
        // than its initial overlap. A separate clamp on each card collapses gaps.
        private fun frontAdvance(start: RectF,current: RectF,edge: Float,exit: Float,p: Float): Float {
            if(!edge.isFinite()) return 0f
            val overlap=(start.right-edge).coerceAtLeast(0f)
            val travel=p*p
            return (current.right-edge-overlap*(1f-travel)+(25f-exit)*travel).coerceAtLeast(0f)
        }
        private fun offset(source: RectF,current: RectF,center: Float,slots: Int,ratio: Float,p: Float): Float =
            current.centerX()-source.centerX()+(center-source.centerX())*(ratio-1f)+
                (slots*current.width()-(center-source.centerX())*ratio)*p+slots*25f*p
        private fun verticalOffset(source: RectF,current: RectF,center: Float,ratio: Float): Float =
            current.centerY()-source.centerY()+(center-source.centerY())*(ratio-1f)
    }
}
