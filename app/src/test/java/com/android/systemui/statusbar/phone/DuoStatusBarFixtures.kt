package com.android.systemui.statusbar.phone

class MiuiPhoneStatusBarView {
    fun handleEvent(@Suppress("UNUSED_PARAMETER") event: android.view.MotionEvent) = false
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
