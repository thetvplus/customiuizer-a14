package com.android.systemui.statusbar.policy

import com.android.systemui.statusbar.SignalIcon
import com.android.systemui.statusbar.connectivity.SignalCallback
import com.android.systemui.statusbar.connectivity.SignalController

open class BatteryControllerImpl {
    @JvmField var mLevel = 0
    @JvmField var mCharging = false
    @JvmField var mPowerSave = false
    open fun fireBatteryLevelChanged() {}
    fun firePowerSaveChanged() {}
}

class MiuiBatteryControllerImpl : BatteryControllerImpl() {
    override fun fireBatteryLevelChanged() {}
}

class MiuiWifiSignalController : SignalController(WifiState()) {
    class WifiState : SignalIcon.State()
    // Deliberately erased, as in HyperOS 1, rather than returning WifiState.
    override fun cleanState(): SignalIcon.State = WifiState()
    override fun notifyListeners(callback: SignalCallback?) {}
}

@Suppress("UNUSED_PARAMETER")
class BluetoothControllerImpl {
    @JvmField var mEnabled = false
    @JvmField var mConnectionState = 0
    @JvmField var mAudioProfileOnly = false
    @JvmField var mIsActive = false
    @JvmField var mBluetoothBattery = -1
    @JvmField val mConnectedDevices = ArrayList<Any>()
    fun setBluetoothBattery(step: Int) { mBluetoothBattery = step }
    fun onConnectionStatusFetched(status: Any?) {}
    fun onActiveDeviceChanged(profile: Int, device: Any?) {}
    fun onBluetoothStateChanged(state: Int) {}
}
