package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.utils.PrefMap

class DockBackgroundFeatureTest {
    @Test
    fun missingOrDisabledMasterNeverTouchesTheLauncherLoader() {
        val param = Proxy.newProxyInstance(PackageReadyParam::class.java.classLoader,
            arrayOf(PackageReadyParam::class.java)) { _, method, _ ->
            error("Disabled Dock feature accessed ${method.name}")
        } as PackageReadyParam
        for (enabled in listOf(null, false, true)) {
            val prefs = PrefMap().apply {
                if (enabled != null) put("launcher_dock_background", enabled)
                put("launcher_dock_background_radius", 48)
            }
            val spec = LauncherPackageReadyFeatures.all(param, prefs).single { it.id == LauncherDockBackgroundFeatureId }
            assertEquals(FeatureTarget.LAUNCHER, spec.target)
            assertEquals(enabled == true, spec.isEnabled(prefs))
        }
    }
}
