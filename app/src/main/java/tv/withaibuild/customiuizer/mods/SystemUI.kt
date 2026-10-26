package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.MainModule
import io.github.libxposed.api.XposedInterface

/**
 * SystemUI hooks that belong to no larger surface.
 *
 * The status bar, control centre, lock screen, notification shade and screenshot
 * hooks live in their own `SystemUI*Hooks` objects. What remains here is the power
 * menu and the charge animation; neither surface shares runtime state.
 */
object SystemUI {
    @Volatile private var chargeAnimationOption = 1
    private val chargeObserver = object : ModuleHelper.PreferenceObserver {
        override fun onChange(key: String?) {
            if (key == null || key == "system_nolightuponcharges")
                chargeAnimationOption = MainModule.mPrefs.getStringAsInt("system_nolightuponcharges", 1)
        }
    }

    @JvmStatic
    fun ExtendedPowerMenuHook(lpparam: PackageReadyParam) {
        ExtendedPowerMenu.install(lpparam.classLoader)
    }

    @JvmStatic
    fun NoLightUpOnChargeHook(lpparam: PackageReadyParam) {
        chargeObserver.onChange(null)
        ModuleHelper.observePreferenceChange(chargeObserver, this)
        ModuleHelper.findAndHookMethod("com.miui.charge.MiuiChargeController", lpparam.classLoader,
            "shouldShowChargeAnim", chargeAnimationHook { chargeAnimationOption })
    }

    internal fun chargeAnimationHook(option: () -> Int) = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            return if (option() in 2..3) false else chain.proceed()
        }
    }
}
