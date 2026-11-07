package com.android.systemui.qs.tileimpl

import android.view.View

/** Selected A14 tile contract; the real queue/renderer remains a device check. */
class QSTileImpl(private val spec: String = "night", state: Int = 2) {
    class State(@JvmField var state: Int)
    class Host {
        var collapses=0
        var failure: RuntimeException?=null
        var collapsing: (()->Unit)?=null
        fun collapsePanels() { failure?.let { throw it };collapses++;collapsing?.invoke() }
    }
    @JvmField val mHost=Host()
    @JvmField var mPendingShowStateMessage=false
    @JvmField var mClickEventId=0
    var stateFailure: RuntimeException?=null
    private val current=State(state)
    var nativeClicks=0
    var headerMessages=0
    fun getState(): State { stateFailure?.let { throw it };return current }
    fun getTileSpec()=spec
    fun click(view: View?) { nativeClicks++;mClickEventId++;mPendingShowStateMessage=true }
    var stateCallbacks=0
    var accessibilityCallbacks=0
    fun refresh(state: Int) {
        if(current.state==state) return
        current.state=state;stateCallbacks++;accessibilityCallbacks++
        if(mPendingShowStateMessage) { showStateMessage("State changed");mPendingShowStateMessage=false }
    }
    fun destroy() { mPendingShowStateMessage=false }
    fun showStateMessage(message: CharSequence?) { headerMessages++ }
}
