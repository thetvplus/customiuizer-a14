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
        val x = if (swipe) value / view.scaleX.coerceAtLeast(.001f) else value
        val y = if (swipe) value / view.scaleY.coerceAtLeast(.001f) else value
        val desiredY = if (released && !committed && releaseY != 0f)
            (releaseY + offset) * (view.translationY / releaseY) else view.translationY + offset
        // animationMatrix also transforms native translation. Cancel that factor
        // so the displayed displacement stays in the finger's screen coordinates.
        val shift = if (swipe) desiredY - y * view.translationY else 0f
        if (w==view.width && h==view.height && scale==value && current!=null &&
            appliedX==x && appliedY==y && appliedOffset==shift) return true
        val scaleChanged=w!=view.width || h!=view.height || scale!=value
        w=view.width; h=view.height; scale=value
        appliedX=x; appliedY=y; appliedOffset=shift
        matrix.setScale(x,y,view.pivotX,view.pivotY)
        matrix.postTranslate(0f,shift)
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

    fun release() {
        finishSwipe()
        onScaleChanged=null
        if (owned && w>=0 && view.animationMatrix == matrix) view.setAnimationMatrix(previous)
        owned=false
    }
}
