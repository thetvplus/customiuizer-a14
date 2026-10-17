package tv.withaibuild.customiuizer.mods.duostatusbar

import java.lang.reflect.Field
import java.lang.reflect.Method
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** Per-install/ClassLoader metadata only. A missing required member leaves every native icon alone. */
internal class DuoAbi(private val loader: ClassLoader) {
    fun cls(name: String): Class<*> = Class.forName("com.android.systemui.$name", false, loader)
    fun field(type: Class<*>, name: String, valueType: Class<*>? = null): Field {
        val result = XposedHelpers.findField(type, name)
        require(valueType == null || result.type == valueType) { "Duo: incompatible $name" }
        return result
    }
    fun method(type: Class<*>, name: String, count: Int): Method {
        var current: Class<*>? = type
        while (current != null && current.name.startsWith("com.android.systemui.")) {
            val found = current.declaredMethods.firstOrNull {
                it.name == name && it.parameterCount == count && !it.isBridge
            }
            if (found != null) return found.apply { isAccessible = true }
            current = current.superclass
        }
        error("Duo: missing ${type.name}.$name/$count")
    }

    val batteryView = cls("statusbar.views.MiuiBatteryMeterView")
    val statusBar = cls("statusbar.phone.MiuiPhoneStatusBarView")
    val keyguard = cls("statusbar.phone.KeyguardStatusBarView")
    // This is a SystemUI view embedded by the plugin, using the same battery and signal classes.
    // Optional so an absent Control Center host cannot disable the regular status bar.
    val controlCenter: Class<*>? = try {
        cls("controlcenter.phone.widget.ControlCenterStatusBarIcon")
    } catch (_: ClassNotFoundException) { null }
    // HyperOS swaps to this separate native host while closing Control Center.
    val transition: Class<*>? = try {
        cls("controlcenter.phone.widget.ControlCenterFakeStatusIcons")
    } catch (_: ClassNotFoundException) { null }
    val wifiView = cls("statusbar.StatusBarWifiView")
    val mobileView = cls("statusbar.StatusBarMobileView")
    val attach = method(batteryView, "onAttachedToWindow", 0)
    val detach = method(batteryView, "onDetachedFromWindow", 0)
    val updateBattery = method(batteryView, "updateAll", 0)
    val darkBattery = method(batteryView, "onDarkChanged", 6)
    val percentView = field(batteryView, "mBatteryPercentView")

    val batteryController = cls("statusbar.policy.MiuiBatteryControllerImpl")
    val batteryLevel = field(batteryController, "mLevel", Int::class.javaPrimitiveType)
    val charging = field(batteryController, "mCharging", Boolean::class.javaPrimitiveType)
    val saver = field(batteryController, "mPowerSave", Boolean::class.javaPrimitiveType)
    val batteryChanged = method(batteryController, "fireBatteryLevelChanged", 0)
    val saverChanged = method(batteryController, "firePowerSaveChanged", 0)

    val wifiController = cls("statusbar.policy.MiuiWifiSignalController")
    val mobileController = cls("statusbar.connectivity.MobileSignalController")
    val wifiChanged = method(wifiController, "notifyListeners", 1)
    val mobileChanged = method(mobileController, "notifyListeners", 1)
    val wifiState = field(wifiController, "mCurrentState")
    val mobileState = field(mobileController, "mCurrentState")
    // HyperOS 1 cleanState() erases both concrete states to SignalIcon.State.
    // Resolve the actual state classes; the base class has no dataSim field.
    private val wifiStateType = cls("statusbar.policy.MiuiWifiSignalController\$WifiState")
    private val mobileStateType = cls("statusbar.SignalIcon\$MobileState")
    val wifiLevel = field(wifiStateType, "level", Int::class.javaPrimitiveType)
    val wifiEnabled = field(wifiStateType, "enabled", Boolean::class.javaPrimitiveType)
    val wifiConnected = field(wifiStateType, "connected", Boolean::class.javaPrimitiveType)
    val wifiInternet = field(wifiStateType, "inetCondition", Int::class.javaPrimitiveType)
    val mobileLevel = field(mobileStateType, "level", Int::class.javaPrimitiveType)
    val mobileConnected = field(mobileStateType, "connected", Boolean::class.javaPrimitiveType)
    val mobileDataSim = field(mobileStateType, "dataSim", Boolean::class.javaPrimitiveType)
    // Xiaomi publishes 0..5, AOSP-like variants 0..4. Resolve the scale once, never per frame.
    val mobileMaxLevel = (field(cls("statusbar.mobile.TelephonyIcons"), "TELEPHONY_SIGNAL_STRENGTH",
        IntArray::class.java).get(null) as IntArray).size.let { size ->
        require(size in 5..6) { "Duo: unsupported cellular strength scale" }
        size - 1
    }
    val subscription = field(mobileController, "mSubscriptionInfo")

    val networkController = cls("statusbar.connectivity.NetworkControllerImpl")
    val networkWifi = field(networkController, "mWifiSignalController")
    val networkMobile = field(networkController, "mMobileSignalControllers")
    val airplane = field(networkController, "mAirplaneMode", Boolean::class.javaPrimitiveType)
    private val callbackHandler = cls("statusbar.connectivity.CallbackHandler")
    val airplaneChanged = method(callbackHandler, "setIsAirplaneMode", 1)
    val subscriptionsChanged = method(callbackHandler, "setSubs", 1)
    val airplaneVisible = field(airplaneChanged.parameterTypes[0], "visible", Boolean::class.javaPrimitiveType)

    val applyWifi = method(wifiView, "applyWifiState", 1)
    val applyMobile = method(mobileView, "applyMobileState", 1)
    val updateMobile = method(mobileView, "updateState", 1)
    val wifiVisibleState = method(wifiView, "setVisibleState", 2)
    val mobileVisibleState = method(mobileView, "setVisibleState", 2)
    val wifiViewState = field(wifiView, "mState")
    val mobileViewState = field(mobileView, "mState")
    val wifiVisible = field(applyWifi.parameterTypes[0], "visible", Boolean::class.javaPrimitiveType)
    val mobileVisible = field(applyMobile.parameterTypes[0], "visible", Boolean::class.javaPrimitiveType)

    companion object {
        fun resolve(loader: ClassLoader): DuoAbi? = try {
            DuoAbi(loader)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("Duo status bar unavailable: ${t.message}")
            null
        }
    }
}
