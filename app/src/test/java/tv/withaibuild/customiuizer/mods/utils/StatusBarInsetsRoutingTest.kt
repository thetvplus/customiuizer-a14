package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.feature.CommonPackageFeatures
import tv.withaibuild.customiuizer.mods.utils.feature.StatusBarHeightFeatureId
import tv.withaibuild.customiuizer.mods.utils.feature.StatusBarHeightInsetsFeatureId
import tv.withaibuild.customiuizer.mods.utils.feature.SystemServerFeatures
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Proxy

class StatusBarInsetsRoutingTest {

    @Test
    fun resourceFeature_routesToPackageReady_ANY() {
        val feature = CommonPackageFeatures.all(fakePackageReadyParam("android"), PrefMap().apply {
            put("system_statusbarheight", 40)
        }).find { it.id == StatusBarHeightFeatureId }

        assertNotNull("Status bar resource feature must be in CommonPackageFeatures", feature)
        assertEquals("system_statusbarheight", feature?.preferenceKey)
        assertEquals("Status Bar Height", feature?.name)
        assertEquals(FeatureTarget.ANY, feature?.target)
        assertEquals(InstallPhase.PACKAGE_READY, feature?.phase)
    }

    @Test
    fun insetsSync_routesToSystemServer_SYSTEM_SERVER_STARTING() {
        val feature = SystemServerFeatures.all(fakeSystemServerStartingParam()).find {
            it.id == StatusBarHeightInsetsFeatureId
        }

        assertNotNull("Status bar Insets feature must be in SystemServerFeatures", feature)
        assertEquals("system_statusbarheight", feature?.preferenceKey)
        assertEquals("Status Bar Height Insets", feature?.name)
        assertEquals(FeatureTarget.SYSTEM_SERVER, feature?.target)
        assertEquals(InstallPhase.SYSTEM_SERVER_STARTING, feature?.phase)
    }

    @Test
    fun resourceFeature_disabledWhenPrefIsDefault() {
        val feature = CommonPackageFeatures.all(fakePackageReadyParam("com.android.systemui"), PrefMap())
            .find { it.id == StatusBarHeightFeatureId }
        assertNotNull(feature)
        assertFalse(feature!!.isEnabled(PrefMap()))
    }

    @Test
    fun resourceFeature_enabledWhenPrefAboveSentinel() {
        val feature = CommonPackageFeatures.all(fakePackageReadyParam("com.android.systemui"), PrefMap())
            .find { it.id == StatusBarHeightFeatureId }
        assertNotNull(feature)
        assertTrue(feature!!.isEnabled(PrefMap().apply { put("system_statusbarheight", 40) }))
    }

    @Test
    fun insetsFeature_disabledWhenPrefIsDefault() {
        val feature = SystemServerFeatures.all(fakeSystemServerStartingParam())
            .find { it.id == StatusBarHeightInsetsFeatureId }
        assertNotNull(feature)
        assertFalse(feature!!.isEnabled(PrefMap()))
    }

    @Test
    fun insetsFeature_enabledWhenPrefAboveSentinel() {
        val feature = SystemServerFeatures.all(fakeSystemServerStartingParam())
            .find { it.id == StatusBarHeightInsetsFeatureId }
        assertNotNull(feature)
        assertTrue(feature!!.isEnabled(PrefMap().apply { put("system_statusbarheight", 40) }))
    }

    @Test
    fun insetsFeature_notPresentInCommonPackageFeatures() {
        val features = CommonPackageFeatures.all(fakePackageReadyParam("android"), PrefMap())
        assertTrue(features.none { it.id == StatusBarHeightInsetsFeatureId })
    }

    @Test
    fun resourceFeature_notPresentInSystemServerFeatures() {
        val features = SystemServerFeatures.all(fakeSystemServerStartingParam())
        assertTrue(features.none { it.id == StatusBarHeightFeatureId })
    }

    private fun fakePackageReadyParam(packageName: String): io.github.libxposed.api.XposedModuleInterface.PackageReadyParam {
        return Proxy.newProxyInstance(
            io.github.libxposed.api.XposedModuleInterface.PackageReadyParam::class.java.classLoader,
            arrayOf(io.github.libxposed.api.XposedModuleInterface.PackageReadyParam::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getPackageName" -> packageName
                "getClassLoader" -> ClassLoader.getSystemClassLoader()
                "isFirstPackage" -> true
                else -> null
            }
        } as io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
    }

    private fun fakeSystemServerStartingParam(): io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam {
        return Proxy.newProxyInstance(
            io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam::class.java.classLoader,
            arrayOf(io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getClassLoader" -> ClassLoader.getSystemClassLoader()
                else -> null
            }
        } as io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
    }
}
