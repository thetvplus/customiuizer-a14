package tv.withaibuild.customiuizer.installers

import android.app.Application
import android.content.Context
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallRegistry
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.HookDiagnostics
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.feature.GenericAppFeatures
import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Installer for the shared [Application.attach] hook.
 *
 * This is the single place where launcher post-attach hooks, status-bar color,
 * overscroll suppression and media-player volume hooks are installed, once the
 * target application has a live context.
 */
object GenericAppInstaller {

    @JvmStatic
    fun installPostAttach(
        lpparam: PackageReadyParam,
        mPrefs: PrefMap,
        isLauncherPkg: Boolean,
        isStatusBarColor: Boolean,
        isNoOverscroll: Boolean,
        controlMedia: Boolean
    ) {
        ModuleHelper.findAndHookMethod(
            Application::class.java,
            "attach",
            Context::class.java,
            object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val registry = FeatureInstallRegistry()
                    for (feature: FeatureSpec in GenericAppFeatures.selected(
                        lpparam,
                        mPrefs,
                        isLauncherPkg,
                        isStatusBarColor,
                        isNoOverscroll,
                        controlMedia
                    )) {
                        registry.register(feature)
                    }

                    if (isLauncherPkg) {
                        registry.installAll(FeatureTarget.LAUNCHER, InstallPhase.APPLICATION_ATTACHED, mPrefs)
                    }

                    if (isStatusBarColor || isNoOverscroll || controlMedia) {
                        registry.installAll(FeatureTarget.ANY, InstallPhase.APPLICATION_ATTACHED, mPrefs)
                    }

                    HookDiagnostics.printSummaryForStage("post-attach")
                }
            }
        )
    }
}
