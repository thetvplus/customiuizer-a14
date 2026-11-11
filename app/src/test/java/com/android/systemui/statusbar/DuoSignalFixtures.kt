package com.android.systemui.statusbar

import com.android.systemui.statusbar.phone.StatusBarSignalPolicy

/** Hand-written ABI fixtures; no View construction or ROM implementation code. */
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
    @JvmField var mWifiActivityView: android.view.View? = null
    @JvmField var mState: StatusBarSignalPolicy.WifiIconState? = null
    fun applyWifiState(state: StatusBarSignalPolicy.WifiIconState) {}
    fun setVisibleState(state: Int, animate: Boolean) {}
}

@Suppress("UNUSED_PARAMETER")
// Match the real View owner so attachment and slot restoration use Android lifecycle.
class StatusBarMobileView : android.widget.FrameLayout(org.robolectric.RuntimeEnvironment.getApplication()) {
    @JvmField var mState: StatusBarSignalPolicy.MobileIconState? = null
    @JvmField var mMobile: android.widget.ImageView? = null
    @JvmField var mColor = -1
    fun applyMobileState(state: StatusBarSignalPolicy.MobileIconState) {}
    fun updateState(state: StatusBarSignalPolicy.MobileIconState) = false
    fun applyDarknessInternal() {}
    fun setDripEnd(enabled: Boolean) {}
    fun setVisibleState(state: Int, animate: Boolean) {}
}
