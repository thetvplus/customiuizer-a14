package com.android.systemui.statusbar.connectivity

import com.android.systemui.statusbar.SignalIcon
import com.android.systemui.statusbar.policy.MiuiWifiSignalController

interface SignalCallback

open class SignalController(state: SignalIcon.State) {
    @JvmField var mCurrentState = state
    open fun cleanState(): SignalIcon.State = SignalIcon.State()
    open fun notifyListeners(callback: SignalCallback?) {}
}

class MobileSignalController : SignalController(SignalIcon.MobileState()) {
    @JvmField var mSubscriptionInfo: Any? = null
    // Deliberately erased, as in HyperOS 1, rather than returning MobileState.
    override fun cleanState(): SignalIcon.State = SignalIcon.MobileState()
    override fun notifyListeners(callback: SignalCallback?) {}
}

class NetworkControllerImpl {
    @JvmField var mWifiSignalController = MiuiWifiSignalController()
    @JvmField var mMobileSignalControllers: Any? = null
    @JvmField var mAirplaneMode = false
}

class IconState { @JvmField var visible = false }

@Suppress("UNUSED_PARAMETER")
class CallbackHandler {
    fun setIsAirplaneMode(state: IconState) {}
    fun setSubs(subscriptions: List<Any>) {}
}
