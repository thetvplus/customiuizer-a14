package tv.withaibuild.customiuizer.mods.volumedialogautohide

import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper

/**
 * Thin hook installer for the VolumeDialogAutohideDelay feature.
 *
 * Resolves the frozen ABI, installs the process-scoped runtime state, and wires
 * the [MethodHook] to the [VolumeDialogAutohideDelayEffect].
 */
internal object VolumeDialogAutohideDelayHook {

    @JvmStatic
    fun install(classLoader: ClassLoader) {
        val abi = VolumeDialogAutohideDelayResolver.resolve(classLoader)
        val runtimeState = VolumeDialogAutohideDelayRuntimeState.install()
        val effect = VolumeDialogAutohideDelayEffect(abi, runtimeState.snapshotRef)

        val hook = object : MethodHook() {
            override fun before(callback: BeforeHookCallback) {
                effect.before(callback)
            }
        }

        ModuleHelper.findAndHookMethod(
            "com.android.systemui.miui.volume.MiuiVolumeDialogImpl",
            classLoader,
            "computeTimeoutH",
            hook,
        )
    }
}
