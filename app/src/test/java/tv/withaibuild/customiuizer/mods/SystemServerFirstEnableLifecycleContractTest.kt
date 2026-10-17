package tv.withaibuild.customiuizer.mods

import java.io.File
import java.lang.reflect.Proxy
import io.github.libxposed.api.XposedModuleInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.feature.SystemServerFeatures
import tv.withaibuild.customiuizer.utils.PrefMap

class SystemServerFirstEnableLifecycleContractTest {

    @Test
    fun everySystemServerFeatureIsClassified() {
        val features = SystemServerFeatures.all(fakeSystemServerStartingParam())
        assertEquals(54, features.size)
        assertEquals(features.map { it.id.name }.toSet(), SystemServerFirstEnableContract.names())
    }

    @Test
    fun liveEnablementMatchesInstallMode() {
        val prefs = PrefMap()
        for (feature in SystemServerFeatures.all(fakeSystemServerStartingParam())) {
            val row = SystemServerFirstEnableContract.row(feature.id.name)!!
            val enabled = feature.isEnabled(prefs)
            if (row.installMode == TriggerHookInstallMode.ALWAYS) {
                assertTrue("${feature.id.name} must install with empty prefs", enabled)
            } else {
                assertFalse("${feature.id.name} must stay start-gated with empty prefs", enabled)
            }
        }
    }

    @Test
    fun startGatedFeaturesAreExplicitlyClassified() {
        for (row in SystemServerFirstEnableContract.rows) {
            when (row.installMode) {
                TriggerHookInstallMode.ALWAYS -> {
                    assertTrue(row.featureIdName, row.firstEnableWorksWithoutReboot)
                    assertTrue(
                        row.featureIdName,
                        row.classification == FirstEnableClassification.A_STABLE_TRIGGER ||
                            row.classification == FirstEnableClassification.INFRA_ALWAYS,
                    )
                }
                TriggerHookInstallMode.PREF_GATED_AT_START -> {
                    assertFalse(row.featureIdName, row.firstEnableWorksWithoutReboot)
                    assertTrue(row.featureIdName, row.canEnableAfterBoot)
                    assertEquals(row.featureIdName, FirstEnableClassification.B_KEEP_LAZY, row.classification)
                    assertFalse(
                        "${row.featureIdName} restart must not claim system_server",
                        row.restartMask.contains("SYSTEM_SERVER"),
                    )
                }
            }
        }
        assertTrue(
            SystemServerFirstEnableContract.rows.none {
                it.classification == FirstEnableClassification.C_REBOOT_REQUIRED
            },
        )
    }

    @Test
    fun globalActionSystemServerTriggersCannotStayLazy() {
        val triggerRows = SystemServerFirstEnableContract.rows.filter {
            it.classification == FirstEnableClassification.A_STABLE_TRIGGER
        }
        assertEquals(
            setOf("nav_bar_actions", "power_double_tap_action"),
            triggerRows.map { it.featureIdName }.toSet(),
        )
        for (row in triggerRows) {
            assertEquals(TriggerHookInstallMode.ALWAYS, row.installMode)
            assertTrue(row.firstEnableWorksWithoutReboot)
        }
    }

    @Test
    fun powerAndNavbarHooksKeepRuntimePrefGates() {
        val controls = File(repoRoot(), "app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt").readText()
        val powerHook = methodBody(controls, "fun PowerDoubleTapActionHook")
        val navHook = methodBody(controls, "fun NavBarActionsHook")

        assertTrue(powerHook.contains("controlsConfig.powerDtAction > 1"))
        assertTrue(powerHook.contains("chain.proceed"))
        assertFalse(powerHook.contains("if (dtFromVolumeDown) {"))
        assertTrue(powerHook.contains("if (controlsConfig.volumeDownDtTorch)"))

        assertTrue(navHook.contains("controlsConfig.backLongAction > 1"))
        assertTrue(navHook.contains("controlsConfig.homeLongAction > 1"))
        assertTrue(navHook.contains("controlsConfig.menuLongAction > 1"))
        assertTrue(navHook.contains("chain.proceed()"))
    }

    @Test
    fun restartInvariantForbidsUndocumentedFourthStateOnGlobalActionTriggers() {
        for (spec in GlobalActionRuntimeContract.triggerSpecs) {
            when (spec.triggerOwner) {
                GlobalActionOwner.SYSTEM_SERVER -> {
                    assertEquals(spec.preferenceKey, TriggerInstallMode.ALWAYS, spec.triggerInstallMode)
                }
                GlobalActionOwner.LAUNCHER, GlobalActionOwner.SYSTEMUI -> {
                    assertEquals(spec.preferenceKey, TriggerInstallMode.PREF_GATED_AT_START, spec.triggerInstallMode)
                    assertTrue(
                        spec.preferenceKey,
                        spec.restartRequirement.contains("LAUNCHER") || spec.restartRequirement.contains("SYSTEMUI"),
                    )
                }
                else -> error("Unexpected trigger owner for ${spec.preferenceKey}")
            }
        }
    }

    private fun fakeSystemServerStartingParam(): XposedModuleInterface.SystemServerStartingParam {
        return Proxy.newProxyInstance(
            XposedModuleInterface.SystemServerStartingParam::class.java.classLoader,
            arrayOf(XposedModuleInterface.SystemServerStartingParam::class.java),
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

    private fun repoRoot(): File {
        var directory = File(java.lang.System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            if (File(directory, "app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt").isFile) {
                return directory
            }
            directory = directory.parentFile ?: error("Repository root not found")
        }
    }

    private fun methodBody(source: String, header: String): String {
        val start = source.indexOf(header)
        check(start >= 0) { "Method header '$header' not found" }
        val bodyStart = source.indexOf('{', start)
        var braceCount = 0
        var i = bodyStart
        while (i < source.length) {
            when (source[i]) {
                '{' -> braceCount++
                '}' -> {
                    braceCount--
                    if (braceCount == 0) return source.substring(start, i + 1)
                }
            }
            i++
        }
        error("Method closing brace not found for '$header'")
    }
}
