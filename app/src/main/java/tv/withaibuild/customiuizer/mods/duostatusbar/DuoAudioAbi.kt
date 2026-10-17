package tv.withaibuild.customiuizer.mods.duostatusbar

import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Optional audio ABI: absence disables audio indicators, never the battery/network feature. */
internal class DuoAudioAbi(abi: DuoAbi, batteryHints: Boolean = true) {
    val bluetooth = abi.cls("statusbar.policy.BluetoothControllerImpl")
    val enabled = abi.field(bluetooth, "mEnabled", Boolean::class.javaPrimitiveType)
    val connection = abi.field(bluetooth, "mConnectionState", Int::class.javaPrimitiveType)
    val audioOnly = abi.field(bluetooth, "mAudioProfileOnly", Boolean::class.javaPrimitiveType)
    val active = abi.field(bluetooth, "mIsActive", Boolean::class.javaPrimitiveType)
    val connectionChanged = abi.method(bluetooth, "onConnectionStatusFetched", 1)
    val activeChanged = abi.method(bluetooth, "onActiveDeviceChanged", 2)
    val enabledChanged = abi.method(bluetooth, "onBluetoothStateChanged", 1)
    val battery = if (batteryHints) DuoBluetoothBatteryAbi.resolve(bluetooth) else null

    val policy = abi.cls("statusbar.phone.MiuiPhoneStatusBarPolicy")
    val constructor = policy.declaredConstructors.single()
    val headsetMap = abi.field(policy, "mHeadsetMap")
    // Follow the declared native receiver type; do not hard-code an anonymous-class ordinal.
    private val receiver = abi.field(abi.cls("statusbar.phone.PhoneStatusBarPolicy"), "mIntentReceiver").type
    val headsetChanged = abi.method(receiver, "onReceive", 2)
    val receiverOwner = abi.field(receiver, "this\$0")

    companion object {
        fun resolve(abi: DuoAbi, batteryHints: Boolean = true): DuoAudioAbi? = try { DuoAudioAbi(abi, batteryHints) } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("Duo audio indicators unavailable: ${t.message}")
            null
        }
    }
}

/** Xiaomi's ten-step hands-free battery is optional and independent of connection indicators. */
internal class DuoBluetoothBatteryAbi private constructor(type: Class<*>) {
    val level: Field = type.getDeclaredField("mBluetoothBattery").apply {
        require(this.type == Int::class.javaPrimitiveType)
        isAccessible = true
    }
    val devices: Field = type.getDeclaredField("mConnectedDevices").apply { isAccessible = true }
    val changed: Method = type.getDeclaredMethod("setBluetoothBattery", Int::class.javaPrimitiveType).apply {
        isAccessible = true
    }
    companion object {
        fun resolve(type: Class<*>): DuoBluetoothBatteryAbi? = try { DuoBluetoothBatteryAbi(type) } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("Duo Bluetooth battery colors unavailable: ${t.message}")
            null
        }
    }
}
