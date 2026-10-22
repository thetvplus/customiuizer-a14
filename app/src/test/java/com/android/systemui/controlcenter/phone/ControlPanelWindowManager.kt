package com.android.systemui.controlcenter.phone

import android.view.MotionEvent

/** Only the fields and signature used by the two fuxi ROMs, without vendor implementation code. */
@Suppress("UNUSED_PARAMETER")
class ControlPanelWindowManager {
    @JvmField var added = true
    @JvmField val controlCenterController = Controller()
    @JvmField var downX = -1f
    @JvmField var windowView: WindowView? = WindowView()
    fun dispatchToControlPanel(event: MotionEvent, width: Float) = false

    class Controller { @JvmField var useControlCenter = true }
    class WindowView {
        var dispatches = 0
        fun handleMotionEvent(event: MotionEvent, fromStatusBar: Boolean): Boolean {
            dispatches++
            return true
        }
    }
}
