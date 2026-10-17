package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NavBarButtonsHotPathContractTest {

    @Test
    fun darkIntensityOnlyReloadsIconsAcrossTheThreshold() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt")
        val hookStart = source.indexOf("fun NavBarButtonsHook(")
        val hook = source.substring(hookStart)
        val stateRead = hook.indexOf("getAdditionalInstanceField(navbar, NAV_BAR_DARK_STATE_FIELD)")
        val unchangedReturn = hook.indexOf("if (previousDark == isDark)")
        val resourceLookup = hook.indexOf("ModuleHelper.getModuleContext(navbar.context)")

        assertTrue(stateRead >= 0)
        assertTrue(unchangedReturn > stateRead)
        assertTrue(resourceLookup > unchangedReturn)
        assertTrue(hook.contains("setAdditionalInstanceField(navbar, NAV_BAR_DARK_STATE_FIELD, isDark)"))
        assertTrue(hook.contains("removeAdditionalInstanceField(navbar, NAV_BAR_DARK_STATE_FIELD)"))
    }

    @Test
    fun darkIntensityReadsOneArgumentWithoutMaterializingTheArray() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt")
        val hookStart = source.indexOf("fun NavBarButtonsHook(")
        val hook = source.substring(hookStart)
        assertTrue(hook.contains("chain.getArg(0) as Float"))
        assertFalse(hook.contains("chain.getArgs()[0] as Float"))
    }

    private fun source(relativePath: String): String {
        var directory = File(java.lang.System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }
}
