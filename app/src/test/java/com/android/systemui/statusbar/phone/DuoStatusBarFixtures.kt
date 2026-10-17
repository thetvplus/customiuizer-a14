package com.android.systemui.statusbar.phone

class MiuiPhoneStatusBarView
class KeyguardStatusBarView

open class PhoneStatusBarPolicy {
    @JvmField val mIntentReceiver = HeadsetReceiver(this)
    @Suppress("UNUSED_PARAMETER")
    class HeadsetReceiver(@JvmField val `this$0`: PhoneStatusBarPolicy) {
        fun onReceive(context: Any?, intent: Any?) {}
    }
}

class MiuiPhoneStatusBarPolicy : PhoneStatusBarPolicy() {
    @JvmField val mHeadsetMap = HashMap<String, Boolean>()
}

class StatusBarSignalPolicy {
    class WifiIconState { @JvmField var visible = false }
    class MobileIconState { @JvmField var visible = false }
}
