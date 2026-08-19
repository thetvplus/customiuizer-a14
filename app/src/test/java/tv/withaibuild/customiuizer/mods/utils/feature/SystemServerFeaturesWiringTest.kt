package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.utils.PrefMap
import java.io.File
import java.lang.reflect.Proxy

class SystemServerFeaturesWiringTest {

    @Test
    fun installMethodNoLongerContainsDirectIfConditions() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemServerInstaller.kt")
        val installMethod = methodBody(source, "fun install(lpparam:")

        // The old direct preference guards and hook calls should be gone from the installer body.
        assertFalse(
            "install() should not directly check system_screenshot_overlay",
            installMethod.contains("mPrefs.getBoolean(\"system_screenshot_overlay\")")
        )
        assertFalse(
            "install() should not directly call TempHideOverlayAppHook",
            installMethod.contains("SystemWindowHooks.TempHideOverlayAppHook(lpparam)")
        )
        assertFalse(
            "install() should not directly check system_screenanim_duration",
            installMethod.contains("mPrefs.getInt(\"system_screenanim_duration\"")
        )
        assertFalse(
            "install() should not directly call ScreenAnimHook",
            installMethod.contains("SystemDisplayHooks.ScreenAnimHook(lpparam)")
        )
        assertFalse(
            "install() should not directly check various_alarmcompat",
            installMethod.contains("mPrefs.getBoolean(\"various_alarmcompat\")")
        )

        // It should now delegate to the registry and the generated features list.
        assertTrue(
            "install() should register features from SystemServerFeatures",
            installMethod.contains("SystemServerFeatures.all(lpparam)")
        )
        assertTrue(
            "install() should call installAll for SYSTEM_SERVER at SYSTEM_SERVER_STARTING",
            installMethod.contains("installAll(FeatureTarget.SYSTEM_SERVER, InstallPhase.SYSTEM_SERVER_STARTING, mPrefs)")
        )
    }

    @Test
    fun allSystemServerFeaturesAreRegistered() {
        val features = SystemServerFeatures.all(fakeSystemServerStartingParam())

        assertEquals(
            "All 54 system_server features should be present",
            54,
            features.size
        )
        val uniqueIds = features.map { it.id }.toSet()
        assertEquals("Every feature must have a unique FeatureId", features.size, uniqueIds.size)

        for (feature in features) {
            assertEquals(FeatureTarget.SYSTEM_SERVER, feature.target)
            assertEquals(InstallPhase.SYSTEM_SERVER_STARTING, feature.phase)
        }
    }

    @Test
    fun tempHideOverlayAppFeatureHasCorrectGuard() {
        val feature = TempHideOverlayAppFeature(fakeSystemServerStartingParam())

        assertEquals(TempHideOverlayAppFeatureId, feature.id)
        assertEquals("system_screenshot_overlay", feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_SERVER, feature.target)
        assertEquals(InstallPhase.SYSTEM_SERVER_STARTING, feature.phase)

        val disabled = PrefMap()
        assertFalse(feature.isEnabled(disabled))

        val enabled = PrefMap().apply { put("system_screenshot_overlay", true) }
        assertTrue(feature.isEnabled(enabled))
    }

    @Test
    fun openAppInFreeFormFeatureHasCorrectMultiKeyGuard() {
        val feature = OpenAppInFreeFormFeature(fakeSystemServerStartingParam())

        assertEquals(OpenAppInFreeFormFeatureId, feature.id)
        assertEquals("system_notify_openinfw", feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_SERVER, feature.target)

        val off = PrefMap()
        assertFalse(feature.isEnabled(off))

        val on = PrefMap().apply { put("system_fw_forcein_actionsend", true) }
        assertTrue(feature.isEnabled(on))
    }

    @Test
    fun navBarActionsFeatureIsAlwaysInstalledForFirstEnable() {
        val feature = NavBarActionsFeature(fakeSystemServerStartingParam())

        assertEquals(NavBarActionsFeatureId, feature.id)
        assertEquals("controls_backlong_action", feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_SERVER, feature.target)

        assertTrue(feature.isEnabled(PrefMap()))
        assertTrue(feature.isEnabled(PrefMap().apply { put("controls_menulong_action", 5) }))
    }

    @Test
    fun powerDoubleTapActionFeatureIsAlwaysInstalledForFirstEnable() {
        val feature = PowerDoubleTapActionFeature(fakeSystemServerStartingParam())

        assertEquals(PowerDoubleTapActionFeatureId, feature.id)
        assertEquals("controls_powerdt_action", feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_SERVER, feature.target)

        assertTrue(feature.isEnabled(PrefMap()))
        assertTrue(feature.isEnabled(PrefMap().apply { put("controls_powerdt_action", 8) }))
    }

    @Test
    fun screenAnimFeatureHasCorrectIntThresholdGuard() {
        val feature = ScreenAnimFeature(fakeSystemServerStartingParam())

        assertEquals(ScreenAnimFeatureId, feature.id)
        assertEquals("system_screenanim_duration", feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_SERVER, feature.target)

        val off = PrefMap()
        assertFalse(feature.isEnabled(off))

        val on = PrefMap().apply { put("system_screenanim_duration", 350) }
        assertTrue(feature.isEnabled(on))
    }

    @Test
    fun allRotationsFeatureHasCorrectStringAsIntGuard() {
        val feature = AllRotationsFeature(fakeSystemServerStartingParam())

        assertEquals(AllRotationsFeatureId, feature.id)
        assertEquals("system_allrotations2", feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_SERVER, feature.target)

        val off = PrefMap()
        assertFalse(feature.isEnabled(off))

        val on = PrefMap().apply { put("system_allrotations2", "2") }
        assertTrue(feature.isEnabled(on))
    }

    @Test
    fun statusBarHeightInsetsFeatureEnabled_forAllCustomValues() {
        val feature = StatusBarHeightInsetsFeature(fakeSystemServerStartingParam())

        assertEquals(StatusBarHeightInsetsFeatureId, feature.id)
        assertEquals("system_statusbarheight", feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_SERVER, feature.target)

        val off = PrefMap()
        assertFalse(feature.isEnabled(off))

        assertTrue(feature.isEnabled(PrefMap().apply { put("system_statusbarheight", 12) }))
        assertTrue(feature.isEnabled(PrefMap().apply { put("system_statusbarheight", 27) }))
        assertTrue(feature.isEnabled(PrefMap().apply { put("system_statusbarheight", 28) }))
        assertTrue(feature.isEnabled(PrefMap().apply { put("system_statusbarheight", 35) }))
        assertTrue(feature.isEnabled(PrefMap().apply { put("system_statusbarheight", 38) }))
        assertTrue(feature.isEnabled(PrefMap().apply { put("system_statusbarheight", 40) }))
        assertFalse(feature.isEnabled(PrefMap().apply { put("system_statusbarheight", 11) }))
    }

    @Test
    fun disableWindowBlursPolicy_isAlwaysInstalledFromDefaultOffState() {
        val feature = DisableWindowBlursFeature(fakeSystemServerStartingParam())

        assertEquals(DisableWindowBlursFeatureId, feature.id)
        assertEquals("system_disable_window_blurs", feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_SERVER, feature.target)
        assertTrue(feature.isEnabled(PrefMap()))
        assertTrue(feature.isEnabled(PrefMap().apply { put("system_disable_window_blurs", true) }))
    }

    @Test
    fun noAccessDeviceLogsRequestFeatureHandlesNonHookSuffixMethod() {
        val feature = NoAccessDeviceLogsRequestFeature(fakeSystemServerStartingParam())

        assertEquals(NoAccessDeviceLogsRequestFeatureId, feature.id)
        assertEquals("various_disable_access_devicelogs", feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_SERVER, feature.target)

        val on = PrefMap().apply { put("various_disable_access_devicelogs", true) }
        assertTrue(feature.isEnabled(on))
    }

    @Test
    fun usbDefaultFunctionFeatureIsAlwaysInstalled() {
        val feature = UsbDefaultFunctionFeature(fakeSystemServerStartingParam())

        assertEquals(UsbDefaultFunctionFeatureId, feature.id)
        assertEquals("system_usb_default_function", feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_SERVER, feature.target)
        assertEquals(InstallPhase.SYSTEM_SERVER_STARTING, feature.phase)

        val off = PrefMap()
        assertTrue("USB default function feature must be always-on", feature.isEnabled(off))

        val on = PrefMap().apply { put("system_usb_default_function", 2) }
        assertTrue(feature.isEnabled(on))
    }

    private fun fakeSystemServerStartingParam(): XposedModuleInterface.SystemServerStartingParam {
        return Proxy.newProxyInstance(
            XposedModuleInterface.SystemServerStartingParam::class.java.classLoader,
            arrayOf(XposedModuleInterface.SystemServerStartingParam::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getClassLoader" -> ClassLoader.getSystemClassLoader()
                "toString" -> "FakeSystemServerStartingParam"
                "equals" -> false
                "hashCode" -> 0
                else -> null
            }
        } as XposedModuleInterface.SystemServerStartingParam
    }

    private fun source(relativePath: String): String {
        var directory = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }

    private fun methodBody(source: String, header: String): String {
        val start = source.indexOf(header)
        check(start >= 0) { "Method header '$header' not found" }
        val bodyStart = source.indexOf('{', start)
        check(bodyStart >= 0) { "Method body start not found for '$header'" }
        var braceCount = 0
        var i = bodyStart
        while (i < source.length) {
            when (source[i]) {
                '{' -> braceCount++
                '}' -> {
                    braceCount--
                    if (braceCount == 0) {
                        return source.substring(start, i + 1)
                    }
                }
            }
            i++
        }
        error("Method closing brace not found for '$header'")
    }
}
