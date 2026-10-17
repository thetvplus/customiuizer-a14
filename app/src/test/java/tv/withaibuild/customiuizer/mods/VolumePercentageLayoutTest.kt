package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VolumePercentageLayoutTest {
    @Test
    fun volumePercentageUsesLiveStatusBarBottomNotStoredDp() {
        assertEquals(0, SystemUIControlCenterHooks.resolveVolumePctTopMarginPx(-4))
        assertEquals(0, SystemUIControlCenterHooks.resolveVolumePctTopMarginPx(0))
        assertEquals(104, SystemUIControlCenterHooks.resolveVolumePctTopMarginPx(104))
        assertEquals(180, SystemUIControlCenterHooks.resolveVolumePctTopMarginPx(180))
    }

    @Test
    fun volumeOverlayDoesNotReuseBrightnessTopPreference() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt")
        val apply = functionBody(source, "applyPctTopMargin")
        assertTrue(apply.contains("PCT_SOURCE_VOLUME"))
        assertTrue(apply.contains("resolveVolumePctTopMarginPx"))
        assertTrue(apply.contains("statusBarBottomPx"))
        assertTrue(functionBody(source, "statusBarBottomPx").contains("WindowInsets.Type.statusBars"))
        val volumeHook = functionBody(source, "ShowVolumePctHook")
        assertFalse(volumeHook.contains("system_showpct_top"))
        assertTrue(functionBody(source, "initPct").contains("applyPctTopMargin"))
    }

    private fun functionBody(source: String, functionName: String): String {
        val start = source.indexOf("fun $functionName(")
        require(start >= 0) { "missing $functionName" }
        var depth = 0
        var opened = false
        for (index in start until source.length) {
            when (source[index]) {
                '{' -> { opened = true; depth++ }
                '}' -> if (opened && --depth == 0) return source.substring(start, index + 1)
            }
        }
        error("Unclosed $functionName")
    }

    private fun source(path: String): String {
        var directory = File(java.lang.System.getProperty("user.dir")!!).absoluteFile
        while (true) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: error("Repository root not found")
        }
    }
}
