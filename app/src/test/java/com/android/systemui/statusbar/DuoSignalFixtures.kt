package com.android.systemui.statusbar

import com.android.systemui.statusbar.phone.StatusBarSignalPolicy

/** Hand-written ABI fixtures, without Android views or ROM implementation code. */
class SignalIcon {
    open class State {
        @JvmField var level = 0
        @JvmField var enabled = false
        @JvmField var connected = false
        @JvmField var inetCondition = 0
    }
    class MobileState : State() {
        @JvmField var dataSim = false
        @JvmField var showName: String? = null
    }
}

@Suppress("UNUSED_PARAMETER")
class StatusBarWifiView {
    @JvmField var mState: StatusBarSignalPolicy.WifiIconState? = null
    fun applyWifiState(state: StatusBarSignalPolicy.WifiIconState) {}
    fun setVisibleState(state: Int, animate: Boolean) {}
}

@Suppress("UNUSED_PARAMETER")
class StatusBarMobileView {
    @JvmField var mState: StatusBarSignalPolicy.MobileIconState? = null
    fun applyMobileState(state: StatusBarSignalPolicy.MobileIconState) {}
    fun updateState(state: StatusBarSignalPolicy.MobileIconState) = false
    fun setVisibleState(state: Int, animate: Boolean) {}
}
