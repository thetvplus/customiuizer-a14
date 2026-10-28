package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.junit.Assert.*
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.ProcessRouter
import tv.withaibuild.customiuizer.mods.utils.ProcessScope
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Proxy

class PocoFolderBlurRoutingTest {
    private val poco = "com.mi.android.globallauncher"
    private val enabled = PrefMap().apply { put("launcher_folder_icon_blur", true) }

    @Test fun onlyTheOptedInPocoMainProcessCanRequestTheAttachHook() {
        assertTrue(GenericAppFeatures.shouldInstallPocoFolderIconBlur(enabled, poco, poco))
        for (process in listOf<String?>(null, "$poco:remote", "com.miui.home", "other")) {
            assertFalse(GenericAppFeatures.shouldInstallPocoFolderIconBlur(enabled, poco, process))
        }
        for (pkg in listOf("com.miui.home", "com.other.launcher")) {
            assertFalse(GenericAppFeatures.shouldInstallPocoFolderIconBlur(enabled, pkg, pkg))
        }
        assertFalse(GenericAppFeatures.shouldInstallPocoFolderIconBlur(PrefMap(), poco, poco))
        assertFalse(GenericAppFeatures.shouldInstallPocoFolderIconBlur(
            PrefMap().apply { put("launcher_folderblur_opacity", 80) }, poco, poco))
    }

    @Test fun pocoUsesOnlyTheFolderSpecAndKeepsTheGenericProcessRoute() {
        assertEquals(ProcessScope.GENERIC_APP, ProcessRouter.resolve(poco, poco))
        val param = Proxy.newProxyInstance(PackageReadyParam::class.java.classLoader,
            arrayOf(PackageReadyParam::class.java)) { _, method, _ ->
            when (method.name) {
                "getPackageName" -> poco
                "getClassLoader" -> javaClass.classLoader
                else -> null
            }
        } as PackageReadyParam
        assertTrue(GenericAppFeatures.selected(param, enabled, false, false, false, false).isEmpty())
        val specs = GenericAppFeatures.selected(param, enabled, false, false, false, false, true)
        val spec = specs.single()
        assertEquals(PocoFolderIconBlurFeatureId, spec.id)
        assertEquals(FeatureTarget.ANY, spec.target)
        assertEquals(InstallPhase.APPLICATION_ATTACHED, spec.phase)
        assertTrue(spec.isEnabled(enabled))
        assertFalse(spec.isEnabled(PrefMap()))
        assertFalse(LauncherPostAttachFeature.evaluateEnabled(enabled, poco))
    }
}
