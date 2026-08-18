package tv.withaibuild.customiuizer.mods

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentsBlurPolicyTest {

    @Test
    fun zeroPercentSkipsGestureEnterAndForcesSessionBlur() {
        assertEquals(0f, Launcher.resolveRecentsBlurRatio(0), 0.001f)
        assertTrue(Launcher.resolveRecentsBlurOverrideEnabled(0))
        assertTrue(Launcher.shouldSkipOriginalEnterRecents(true, 0f))
        assertTrue(Launcher.shouldSkipOriginalEnterRecents(false, 0.4f))
        assertFalse(Launcher.shouldSkipOriginalEnterRecents(true, 0.4f))
        assertEquals(
            0f,
            Launcher.resolveRecentsFastBlurRatio(
                requested = 1f,
                recentsOverride = true,
                recentsSessionActive = true,
                folderActive = false,
                recentsRatio = 0f,
                pendingCustomRatio = false
            ),
            0.001f
        )
    }

    @Test
    fun recentsZeroDoesNotOverrideAnActiveFolder() {
        assertEquals(
            0.7f,
            Launcher.resolveRecentsFastBlurRatio(
                requested = 0.7f,
                recentsOverride = true,
                recentsSessionActive = true,
                folderActive = true,
                recentsRatio = 0f,
                pendingCustomRatio = true
            ),
            0.001f
        )
    }

    @Test
    fun leftoverCustomRatioIsConsumedEvenOnFourArgFastBlur() {
        assertEquals(
            0.3f,
            Launcher.resolveRecentsFastBlurRatio(
                requested = 1f,
                recentsOverride = true,
                recentsSessionActive = false,
                folderActive = false,
                recentsRatio = 0.3f,
                pendingCustomRatio = true
            ),
            0.001f
        )
        assertEquals(
            1f,
            Launcher.resolveRecentsFastBlurRatio(
                requested = 1f,
                recentsOverride = true,
                recentsSessionActive = true,
                folderActive = false,
                recentsRatio = 0.3f,
                pendingCustomRatio = false
            ),
            0.001f
        )
    }

    @Test
    fun recentsBlurHookDoesNotShareWindowBlurPolicy() {
        val recents = source("app/src/main/java/tv/withaibuild/customiuizer/mods/Launcher.kt")
        val window = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt")
        assertFalse(recents.contains("system_disable_window_blurs"))
        assertFalse(recents.contains("getBlurDisabledSetting"))
        assertFalse(window.contains("system_recents_blur"))
        assertFalse(window.contains("fastBlurWhenEnterRecents"))
    }

    private fun source(relativePath: String): String {
        var directory = File(requireNotNull(java.lang.System.getProperty("user.dir"))).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }
}
