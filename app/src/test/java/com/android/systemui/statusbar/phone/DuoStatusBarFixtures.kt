package com.android.systemui.statusbar.phone

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

class MiuiPhoneStatusBarView(context: Context) : FrameLayout(context) {
    @JvmField var mUseControlCenter = true
    @JvmField var mMotionToControlCenter = false
    var nativeEvents = 0
        private set
    var dispatchToControlPanel: (MotionEvent, Float) -> Unit = { _, _ -> }

    fun handleEvent(event: MotionEvent): Boolean {
        nativeEvents++
        val action = event.actionMasked
        if (mUseControlCenter && action == MotionEvent.ACTION_DOWN && event.rawX >= width / 2f) {
            mMotionToControlCenter = true
        }
        val routed = mUseControlCenter && mMotionToControlCenter
        if (routed) dispatchToControlPanel(event, width.toFloat())
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mMotionToControlCenter = false
        }
        return routed
    }
}
class KeyguardStatusBarView

open class PhoneStatusBarPolicy {
    @JvmField val mIntentReceiver = HeadsetReceiver(this)
    @Suppress("UNUSED_PARAMETER")
    class HeadsetReceiver(@JvmField val `this$0`: PhoneStatusBarPolicy) {
        fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {}
    }
}

class MiuiPhoneStatusBarPolicy : PhoneStatusBarPolicy() {
    @JvmField val mHeadsetMap = HashMap<String, Boolean>()
}

class StatusBarSignalPolicy {
    class WifiIconState {
        @JvmField var visible = false
        @JvmField var wifiStandard = 0
        @JvmField var showWifiStandard = false
    }
    class MobileIconState {
        @JvmField var visible = false
        @JvmField var strengthId = 0
        @JvmField var airplane = false
    }
}
