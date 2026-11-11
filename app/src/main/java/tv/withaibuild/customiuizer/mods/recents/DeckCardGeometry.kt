package tv.withaibuild.customiuizer.mods.recents

import android.graphics.Matrix
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

/** Presentation matrix has one owner. Native gesture scale/translation and its
 * dismissal thresholds remain untouched. Matrix objects are reused on frames. */
internal class DeckCardGeometry(val view: View, val parent: ViewGroup) {
    private val previous = view.animationMatrix?.let(::Matrix)
    private val matrix = Matrix()
    private val combined = Matrix()
    private val inverse = Matrix()
    private val point = FloatArray(2)
    private var w = -1
    private var h = -1
    private var swipe = false
    private var released = false
    private var committed = false
    private var offset = 0f
    private var releaseY = 0f
    private var appliedX = Float.NaN
    private var appliedY = Float.NaN
    private var appliedShiftX = Float.NaN
    private var appliedOffset = Float.NaN
    private var nativeAlpha = 1f
    private var appliedAlpha = Float.NaN
    var scale = 1f
        private set
    var owned = previous == null || previous.isIdentity
        private set
    var onScaleChanged: ((Float)->Unit)?=null

    fun apply(value: Float): Boolean {
        if (!owned) return false
        val current = view.animationMatrix
        if (w >= 0 && current != null && current != matrix) { owned=false; return false }
        val width=view.width; val height=view.height
        val centerX=width*.5f; val centerY=height*.5f
        val nativeX=if(swipe) view.scaleX.coerceAtLeast(.001f) else 1f
        val nativeY=if(swipe) view.scaleY.coerceAtLeast(.001f) else 1f
        val x=value/nativeX; val y=value/nativeY
        val desiredY = if (released && !committed && releaseY != 0f)
            (releaseY + offset) * (view.translationY / releaseY) else view.translationY + offset
        // Native entry/swipe may change its pivot. Our frame and window endpoint
        // always share the frame center; compensate native gesture scale without
        // changing the pivot, scale or displacement used by native qualification.
        val shiftX=if(swipe) x*(1f-nativeX)*(centerX-view.pivotX) else 0f
        val shiftY=if(swipe) desiredY-y*view.translationY+
            y*(1f-nativeY)*(centerY-view.pivotY) else 0f
        if (w==width && h==height && scale==value && current!=null &&
            appliedX==x && appliedY==y && appliedShiftX==shiftX && appliedOffset==shiftY) return true
        val scaleChanged=w!=width || h!=height || scale!=value
        w=width; h=height; scale=value
        appliedX=x; appliedY=y; appliedShiftX=shiftX; appliedOffset=shiftY
        matrix.setScale(x,y,centerX,centerY)
        matrix.postTranslate(shiftX,shiftY)
        view.setAnimationMatrix(matrix)
        if (scaleChanged) onScaleChanged?.invoke(value)
        return true
    }

    /** Raw VerticalSwipe input and its already-qualified native displacement arrive
     * in the same Main-thread call. Never alter the scale used by the .8 gate. */
    fun trackSwipe(raw: Float, nativeDelta: Float): Boolean {
        if (!owned) return false
        if (raw >= 0f && !swipe) return true // Downward lock gesture remains native.
        if (!swipe) nativeAlpha=view.alpha
        swipe=true; released=false; committed=false
        offset=raw-nativeDelta
        holdAlpha()
        return apply(scale)
    }
    fun releaseSwipe() { if (swipe) { released=true; releaseY=view.translationY } }
    /** The verified native MOVE always writes root alpha, even when it equals
     * our opaque value. Keep that explicit write distinct from observing ours. */
    fun nativeAlphaWritten(value: Float) { nativeAlpha=value }
    /** Return alpha before native spring construction captures its start value. */
    fun snapSwipe() { releaseSwipe(); restoreAlpha() }
    fun commitSwipe() { committed=true; appliedAlpha=Float.NaN }
    private fun holdAlpha() {
        if (view.alpha!=appliedAlpha) nativeAlpha=view.alpha
        appliedAlpha=1f
        if (view.alpha!=1f) view.alpha=1f
    }
    private fun restoreAlpha() {
        if (view.alpha==appliedAlpha) view.alpha=nativeAlpha
        appliedAlpha=Float.NaN
    }
    fun finishSwipe() {
        if (!swipe) return
        swipe=false; released=false; committed=false; offset=0f
        restoreAlpha()
        apply(scale)
    }
    fun updateSwipe(): Boolean {
        if (!owned) return false
        if (!swipe) return true
        if (!released && !committed) holdAlpha()
        return apply(scale)
    }

    private fun parentMatrix(): Matrix {
        combined.set(view.matrix)
        combined.postConcat(matrix)
        combined.postTranslate((view.left-parent.scrollX).toFloat(),(view.top-parent.scrollY).toFloat())
        return combined
    }

    fun contains(x: Float,y: Float): Boolean {
        if (!owned || !parentMatrix().invert(inverse)) return false
        point[0]=x; point[1]=y; inverse.mapPoints(point)
        return point[0]>=0f && point[0]<view.width && point[1]>=0f && point[1]<view.height
    }

    fun dispatch(event: MotionEvent): Boolean {
        if (!owned || !parentMatrix().invert(inverse)) return false
        val copy=MotionEvent.obtain(event)
        return try { copy.transform(inverse); view.dispatchTouchEvent(copy) } finally { copy.recycle() }
    }

    fun screenContent(rect: RectF,header: Float) {
        combined.reset(); parent.transformMatrixToGlobal(combined)
        combined.preTranslate((view.left-parent.scrollX).toFloat(),(view.top-parent.scrollY).toFloat())
        combined.preConcat(matrix); combined.preConcat(view.matrix)
        rect.set(0f,header,view.width.toFloat(),view.height.toFloat()); combined.mapRect(rect)
    }

    fun localContent(rect: RectF,header: Float) {
        combined.setTranslate((view.left-parent.scrollX).toFloat(),(view.top-parent.scrollY).toFloat())
        combined.preConcat(matrix); combined.preConcat(view.matrix)
        rect.set(0f,header,view.width.toFloat(),view.height.toFloat()); combined.mapRect(rect)
    }

    /** Change native layout coordinates without changing the rendered start pose
     * captured by the next native spring. The stable endpoint remains root=1/0. */
    fun rebaseContent(before: RectF,header: Float,scratch: RectF): Boolean {
        if(!owned || swipe || before.isEmpty || view.width<=0 || view.height<=header ||
            scale<=0f || view.rotation!=0f || view.rotationX!=0f || view.rotationY!=0f) return false
        val x=before.width()/(view.width*scale)
        val y=before.height()/((view.height-header)*scale)
        if(view.scaleX!=x) view.scaleX=x
        if(view.scaleY!=y) view.scaleY=y
        localContent(scratch,header)
        val tx=view.translationX+(before.centerX()-scratch.centerX())/scale
        val ty=view.translationY+(before.centerY()-scratch.centerY())/scale
        if(view.translationX!=tx) view.translationX=tx
        if(view.translationY!=ty) view.translationY=ty
        return true
    }

    /** Feed the native gesture pose solver in its own coordinates. It still
     * owns root properties and springs; the owned matrix must not scale its
     * already-screen-sized window a second time. Window geometry folds page
     * spacing into the real Deck endpoints without another animation clock. */
    fun windowInput(window: RectF,reference: RectF,target: RectF,slots: Int,gap: Float,
        fullWidth: Float,bend: Float,header: Float,out: RectF): Boolean {
        if(!owned || swipe || view.width<=0 || view.height<=header || scale<=0f ||
            window.isEmpty || reference.isEmpty || target.isEmpty ||
            !fullWidth.isFinite() || !bend.isFinite()) return false
        // Native may prepare the card before its first layout. Reconcile the
        // matrix here, in the existing pose callback, before using its inverse.
        if((w!=view.width || h!=view.height) && !apply(scale)) return false
        val range=fullWidth-reference.width()
        val p=if(range>0f) ((fullWidth-window.width())/range).coerceIn(0f,1f) else 1f
        val ratio=window.width()/reference.width()
        val width=window.width()*(1f+(target.width()/reference.width()-1f)*p)
        val height=width*(view.height-header)/view.width
        val pages=slots*(window.width()+gap)
        val dx=pages+((target.centerX()-reference.centerX())*ratio-pages)*p
        val cy=window.centerY()+(target.centerY()-reference.centerY())*ratio*p
        val cx=view.left+view.width*.5f; val frameY=view.top+view.height*.5f
        val rawWidth=width/scale
        val left=cx+(window.centerX()+dx-cx)/scale-rawWidth*.5f-slots*(rawWidth+gap)
        val top=frameY+(cy-height*.5f-frameY)/scale-bend
        out.set(left,top,left+rawWidth,top+height/scale)
        return true
    }

    /** Same horizontal pose consumer as native alignTaskByVisualRectF, with the
     * input already expressed before our presentation matrix. Native close/end
     * callbacks drive this and reset the pose; no independently retained motion. */
    fun alignClosing(input: RectF,slots: Int,gap: Float,header: Float) {
        val s=input.width()/view.width
        view.pivotX=view.width*.5f;view.pivotY=view.height*.5f
        view.scaleX=s;view.scaleY=s
        view.translationX=input.centerX()-(view.left+view.width*.5f)+slots*(input.width()+gap)
        view.translationY=input.top+(view.height-header)*s*.5f-header*s*.5f-(view.top+view.height*.5f)
    }

    fun release() {
        finishSwipe()
        onScaleChanged=null
        if (owned && w>=0 && view.animationMatrix == matrix) view.setAnimationMatrix(previous)
        owned=false
    }
}
