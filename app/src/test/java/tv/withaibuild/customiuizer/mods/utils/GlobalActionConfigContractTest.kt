package tv.withaibuild.customiuizer.mods.utils

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalActionConfigContractTest {

    private val config = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/GlobalActionConfig.kt")
    private val globalActions = source("app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActions.kt")
    private val systemUiBootstrap = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiBootstrapCoordinator.kt")
    private val systemServerInstaller = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemServerInstaller.kt")

    @Test
    fun configFileOwnsIndexAndIsIndependent() {
        assertTrue(config.contains("internal val customActionKeys = arrayOf("))
        assertTrue(config.contains("private fun ensureCustomActionMaps()"))
        assertTrue(config.contains("fun hasConfiguredGlobalActions()"))
        assertTrue(config.contains("fun hasConfiguredActionCode("))
        assertTrue(config.contains("fun hasConfiguredToggle("))
        assertTrue(config.contains("MainModule.mPrefs.getInt(key + \"_action\", 1)"))
        assertTrue(config.contains("if (action > 1)"))
        assertTrue(config.contains("MainModule.mPrefs.getInt(key + \"_toggle\", 0)"))
        assertTrue(config.contains("if (toggle > 0)"))
        assertTrue(config.contains("private val customActionConfigLock = Any()"))
        assertTrue(config.contains("synchronized(customActionConfigLock)"))
        assertFalse(config.contains("GlobalActions."))
        assertFalse(config.contains("import tv.withaibuild.customiuizer.mods.GlobalActions"))
        assertFalse(config.contains("GlobalActions::class.java"))
    }

    @Test
    fun globalActionsNoLongerOwnsConfigState() {
        assertFalse(globalActions.contains("private val customActionKeys"))
        assertFalse(globalActions.contains("fun ensureCustomActionMaps()"))
        assertFalse(globalActions.contains("fun hasCustomActions()"))
        assertFalse(globalActions.contains("fun hasActionCode("))
        assertFalse(globalActions.contains("fun hasToggle("))
        assertFalse(globalActions.contains("val fastRebootReceiver"))
    }

    @Test
    fun bootstrapAndInstallerUseConfigGate() {
        assertFalse(systemUiBootstrap.contains("if (hasConfiguredGlobalActions()) GlobalActionSystemServerHooks.setupStatusBar(lpparam)"))
        assertTrue(systemUiBootstrap.contains("GlobalActionSystemServerHooks.setupStatusBar(lpparam)"))
        assertFalse(systemUiBootstrap.contains("GlobalActions.hasCustomActions()"))
        assertTrue(systemServerInstaller.contains("GlobalActionSystemServerHooks.setupGlobalActions(lpparam)"))
        assertFalse(
            "PWM receiver must not wait for configured actions",
            systemServerInstaller.contains("hasConfiguredGlobalActions()"),
        )
        assertFalse(systemServerInstaller.contains("GlobalActions.hasCustomActions()"))
    }

    private fun source(relativePath: String): String {
        var directory = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: error("Repository root not found while locating $relativePath")
        }
    }
}
