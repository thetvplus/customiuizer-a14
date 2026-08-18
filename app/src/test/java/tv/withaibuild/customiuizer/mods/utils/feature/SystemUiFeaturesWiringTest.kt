package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.installers.SystemUiInstaller
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallRegistry
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallState
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallStateTestAccess
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.utils.PrefMap
import java.io.File
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

class SystemUiFeaturesWiringTest {

    @Test
    fun installMethodNoLongerContainsDirectIfConditions() {
        // 1. Reflection: the public static install() signature is stable.
        val installerClass = Class.forName("tv.withaibuild.customiuizer.installers.SystemUiInstaller")
        val install = installerClass.getMethod(
            "install",
            XposedModuleInterface.PackageReadyParam::class.java,
            PrefMap::class.java
        )
        assertTrue("install() must be public", Modifier.isPublic(install.modifiers))
        assertTrue("install() must be static", Modifier.isStatic(install.modifiers))

        // 2. Registry probe: all SystemUi features can be registered and installAll() works.
        FeatureInstallStateTestAccess.clear()
        val registry = FeatureInstallRegistry()
        val lpparam = fakePackageReadyParam()
        val mPrefs = PrefMap()
        val features = SystemUiFeatures.all(lpparam, mPrefs)
        for (feature in features) {
            registry.register(feature)
        }
        val results = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, mPrefs, true)
        assertEquals(
            "installAll(SYSTEM_UI, PACKAGE_READY) must be called for every registered feature",
            features.size,
            results.size
        )

        // 3. Auxiliary source checks: ensure the source still delegates to SystemUiFeatures and installAll.
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/installers/SystemUiInstaller.java")
        assertFalse(
            "install() source should not directly read mPrefs",
            source.contains("mPrefs.get")
        )
        assertTrue(
            "install() source should use SystemUiFeatures",
            source.contains("SystemUiFeatures")
        )
        assertTrue(
            "install() source should call installAll",
            source.contains("installAll")
        )
        assertTrue(
            "install() source should target SYSTEM_UI",
            source.contains("FeatureTarget.SYSTEM_UI")
        )
        assertTrue(
            "install() source should use PACKAGE_READY",
            source.contains("InstallPhase.PACKAGE_READY")
        )
    }

    @Test
    fun allSystemUiFeaturesAreRegistered() {
        val features = SystemUiFeatures.all(fakePackageReadyParam(), PrefMap())

        assertEquals(
            "All 98 preference-guarded SystemUI features should be present",
            98,
            features.size
        )
        val uniqueIds = features.map { it.id }.toSet()
        assertEquals("Every feature must have a unique FeatureId", features.size, uniqueIds.size)

        for (feature in features) {
            assertEquals(FeatureTarget.SYSTEM_UI, feature.target)
            assertEquals(InstallPhase.PACKAGE_READY, feature.phase)
        }
    }

    @Test
    fun foregroundMonitorFeatureHasCorrectMultiKeyGuard() {
        val feature = ForegroundMonitorFeature(fakePackageReadyParam(), PrefMap())

        assertEquals(ForegroundMonitorFeatureId, feature.id)
        assertEquals("various_showcallui", feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_UI, feature.target)
        assertEquals(InstallPhase.PACKAGE_READY, feature.phase)

        val off = PrefMap()
        assertFalse(feature.isEnabled(off))

        val on = PrefMap().apply { put("various_showcallui", 2) }
        assertTrue(feature.isEnabled(on))

        val onByCursor = PrefMap().apply { put("controls_volumecursor", true) }
        assertTrue(feature.isEnabled(onByCursor))
    }

    @Test
    fun navBarButtonsFeatureHasCorrectMultiIntGuard() {
        val feature = NavBarButtonsFeature(fakePackageReadyParam(), PrefMap())

        assertEquals(NavBarButtonsFeatureId, feature.id)
        assertEquals("controls_navbarleft_action", feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_UI, feature.target)

        val off = PrefMap()
        assertFalse(feature.isEnabled(off))

        val on = PrefMap().apply { put("controls_navbarrightlong_action", 5) }
        assertTrue(feature.isEnabled(on))
    }

    @Test
    fun hideImeDismissButtonFeatureHasCorrectSingleBooleanGuard() {
        val feature = HideImeDismissButtonFeature(fakePackageReadyParam(), PrefMap())

        assertEquals(HideImeDismissButtonFeatureId, feature.id)
        assertEquals("controls_hide_ime_dismiss_button", feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_UI, feature.target)
        assertEquals(InstallPhase.PACKAGE_READY, feature.phase)

        val off = PrefMap()
        assertFalse(feature.isEnabled(off))

        val on = PrefMap().apply { put("controls_hide_ime_dismiss_button", true) }
        assertTrue(feature.isEnabled(on))
    }

    @Test
    fun statusBarClockTweakFeatureHasCorrectMultiPreferenceGuard() {
        val feature = StatusBarClockTweakFeature(fakePackageReadyParam(), PrefMap())

        assertEquals(StatusBarClockTweakFeatureId, feature.id)
        assertEquals("system_statusbar_clocktweak", feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_UI, feature.target)

        val off = PrefMap()
        assertFalse(feature.isEnabled(off))

        val on = PrefMap().apply { put("system_cc_dateformat", "yyyy") }
        assertTrue(feature.isEnabled(on))
    }

    @Test
    fun monitorDeviceInfoFeatureHasCorrectMultiPreferenceGuard() {
        val feature = MonitorDeviceInfoFeature(fakePackageReadyParam(), PrefMap())

        assertEquals(MonitorDeviceInfoFeatureId, feature.id)
        assertEquals("system_statusbar_batterytempandcurrent", feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_UI, feature.target)

        val off = PrefMap()
        assertFalse(feature.isEnabled(off))

        val on = PrefMap().apply { put("system_statusbar_showdevicetemperature", true) }
        assertTrue(feature.isEnabled(on))
    }

    @Test
    fun statusBarContentGeometryFeatureIsAlwaysEnabled() {
        val feature = StatusBarContentGeometryFeature(fakePackageReadyParam(), PrefMap())

        assertEquals(StatusBarContentGeometryFeatureId, feature.id)
        assertEquals("system_statusbar_content_vertical_offset", feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_UI, feature.target)
        assertEquals(InstallPhase.PACKAGE_READY, feature.phase)
        assertTrue(feature.isEnabled(PrefMap()))
        assertEquals(253, StatusBarContentGeometryFeatureId.id)
    }

    @Test
    fun dualRowSignalFeaturePreservesElseIfSemantics() {
        val feature = DualRowSignalFeature(fakePackageReadyParam(), PrefMap())

        assertEquals(DualRowSignalFeatureId, feature.id)
        assertEquals("system_statusbar_dualsimin2rows", feature.preferenceKey)

        val digitalOn = PrefMap().apply {
            put("system_statusbar_mobile_digital_signal", true)
            put("system_statusbar_dualsimin2rows", true)
        }
        assertFalse(feature.isEnabled(digitalOn))

        val dualOnly = PrefMap().apply {
            put("system_statusbar_dualsimin2rows", true)
        }
        assertTrue(feature.isEnabled(dualOnly))
    }

    private fun fakePackageReadyParam(): XposedModuleInterface.PackageReadyParam {
        return Proxy.newProxyInstance(
            XposedModuleInterface.PackageReadyParam::class.java.classLoader,
            arrayOf(XposedModuleInterface.PackageReadyParam::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getClassLoader" -> ClassLoader.getSystemClassLoader()
                "toString" -> "FakePackageReadyParam"
                "equals" -> false
                "hashCode" -> 0
                else -> null
            }
        } as XposedModuleInterface.PackageReadyParam
    }

    private fun source(relativePath: String): String {
        var directory = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        val candidates = when {
            relativePath.endsWith(".java") -> listOf(
                relativePath.replace(".java", ".kt"),
                relativePath
            )
            relativePath.endsWith(".kt") -> listOf(
                relativePath,
                relativePath.replace(".kt", ".java")
            )
            else -> listOf(relativePath)
        }
        while (true) {
            for (path in candidates) {
                val candidate = File(directory, path)
                if (candidate.isFile) return candidate.readText()
            }
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }
}
