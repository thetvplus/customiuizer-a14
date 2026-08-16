package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DynamicIslandShellContractTest {
    @Test
    fun dynamicIslandReshapesTheRomRowWithoutReparenting() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt")
        assertTrue(source.contains("applyDynamicIslandCapsule"))
        assertTrue(source.contains("IslandPillOutline"))
        assertTrue(source.contains("clipToOutline = true"))
        assertTrue(source.contains("setPadding(0, 0, 0, 0)"))
        assertFalse(source.contains("DynamicIslandHost.shared"))
        assertFalse(source.contains("parent.removeView("))
        assertFalse(source.contains("layoutParams.width = 1"))
        assertTrue(source.contains("DynamicIslandStatusBarFade.acquire(strongToast)"))
        assertTrue(source.contains("DynamicIslandStatusBarFade.release(root)"))
        assertFalse(functionBody(source, "applyDynamicIslandCapsule").contains("DynamicIslandStatusBarFade"))
        val match = source.substringAfter("StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT -> {")
            .substringBefore("StrongToastPresentationMode.DYNAMIC_ISLAND")
        assertFalse(match.contains("DynamicIslandStatusBarFade"))
        val fade = source("app/src/main/java/tv/withaibuild/customiuizer/mods/DynamicIslandStatusBarFade.kt")
        assertTrue(fade.contains("status_bar_contents"))
        assertTrue(fade.contains("mStatusBarLeftContainer"))
        assertTrue(fade.contains("view.animate().cancel()"))
        assertTrue(fade.contains("view.animate().alpha(target)"))
        assertFalse(fade.contains("setDuration"))
        assertFalse(fade.contains("PathInterpolator"))
        assertFalse(fade.contains("View.GONE"))
        assertFalse(fade.contains("View.INVISIBLE"))
    }

    @Test
    fun dynamicIslandDoesNotUseStatusBarHeightAsAnchor() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt")
        val apply = functionBody(source, "applyDynamicIslandCapsule")
        assertFalse(apply.contains("currentStatusBarInsetPx"))
        assertFalse(apply.contains("WindowInsets.Type.statusBars"))
        val margin = functionBody(source, "resolveIslandTopMarginPx")
        assertTrue(margin.contains("userOffsetPx"))
        assertTrue(margin.contains("cutoutTopPx"))
        assertFalse(margin.contains("statusBar"))
    }

    private fun functionBody(source: String, functionName: String): String {
        val start = source.indexOf("fun $functionName(")
        require(start >= 0)
        var depth = 0
        var opened = false
        for (index in start until source.length) {
            when (source[index]) {
                '{' -> { opened = true; depth++ }
                '}' -> if (opened && --depth == 0) return source.substring(start, index + 1)
            }
        }
        error("Unclosed function")
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
