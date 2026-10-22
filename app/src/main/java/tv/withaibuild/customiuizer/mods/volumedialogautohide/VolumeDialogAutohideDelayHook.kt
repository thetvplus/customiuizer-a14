package tv.withaibuild.customiuizer.mods.volumedialogautohide

import android.view.accessibility.AccessibilityManager
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/**
 * Thin hook installer for the VolumeDialogAutohideDelay feature.
 *
 * Resolves the frozen ABI, installs the process-scoped runtime state, and wires
 * the [MethodHook] to the [VolumeDialogAutohideDelayEffect].
 */
internal object VolumeDialogAutohideDelayHook {

    @JvmStatic
    fun install(classLoader: ClassLoader) {
        val dialogClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.miui.volume.MiuiVolumeDialogImpl", classLoader,
        ) ?: return
        val recommend = resolveTimeoutRecommendation(dialogClass) ?: return
        val abi = VolumeDialogAutohideDelayResolver.resolve(dialogClass)
        val runtimeState = VolumeDialogAutohideDelayRuntimeState.install()
        val effect = VolumeDialogAutohideDelayEffect(abi, runtimeState.snapshotRef, recommend)

        val hook = object : MethodHook() {
            override fun before(callback: BeforeHookCallback) {
                effect.before(callback)
            }
        }

        ModuleHelper.findAndHookMethod(
            dialogClass,
            "computeTimeoutH",
            hook,
        )
    }

    // The native manager already holds the accessibility timeout settings. Resolve its field once
    // for this plugin loader; an unknown ABI leaves the original method and observer untouched.
    internal fun resolveTimeoutRecommendation(dialogClass: Class<*>): ((Any?, Int, Int) -> Int?)? {
        val managerField = XposedHelpers.findFieldIfExists(dialogClass, "mAccessibilityMgr") ?: return null
        if (!AccessibilityManager::class.java.isAssignableFrom(managerField.type)) return null
        return { owner, millis, flags ->
            (managerField.get(owner) as? AccessibilityManager)?.getRecommendedTimeoutMillis(millis, flags)
        }
    }
}
