package tv.withaibuild.customiuizer.mods.recents

import android.os.SystemClock
import android.view.MotionEvent

/** The stack captures an owned stream once; its native handler still decides
 * scrolling/swiping. Only the pending child tap receives inverse-mapped copies. */
internal class DeckTouchRouter(private val nativeIntercept: (MotionEvent)->Boolean) {
    private var target: DeckCardGeometry?=null
    private var native=false
    private var active=false
    private var down=0L

    fun begin(event: MotionEvent,intercepted: Boolean,card: DeckCardGeometry?) {
        cancel()
        active=true; down=event.downTime; native=intercepted || card==null; target=if(native) null else card
    }

    /** null delegates this event to the existing stack onTouch exactly once. */
    fun touch(event: MotionEvent): Boolean? {
        if(!active) return null
        val action=event.actionMasked
        if(!native && action!=MotionEvent.ACTION_DOWN) {
            val taken=nativeIntercept(event)
            if(taken || action==MotionEvent.ACTION_POINTER_DOWN || action==MotionEvent.ACTION_POINTER_UP) {
                cancelChild(); native=true
            }
        }
        val delegated=native
        val child=target
        // A native click can synchronously begin the window transition. Release
        // the stream first so that transition cleanup cannot send a post-UP CANCEL.
        if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL) { active=false; target=null }
        if(!native && child?.dispatch(event)!=true && action==MotionEvent.ACTION_DOWN) {
            target=null; native=true
        }
        return if(delegated || native) null else true
    }

    private fun cancelChild() {
        val child=target ?: return
        target=null
        val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,0f,0f,0)
        try { child.dispatch(event) } finally { event.recycle() }
    }
    fun cancel() { try { cancelChild() } finally { active=false; native=false } }
}
