package tv.withaibuild.customiuizer.mods

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Activity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RecentsBlurPolicyTest {

    @Test fun resetThatDoesNoBlurWorkCannotMarkALaterFolderOrDesktopCall() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val window = activity.window
        val scope = RecentsBlurScope()
        try {
            scope.withWindow(window) { assertTrue(scope.matches(window)) }
            assertFalse(scope.matches(window))
            try {
                scope.withWindow(window) { throw IllegalStateException("interrupted reset") }
            } catch (_: IllegalStateException) { }
            assertFalse(scope.matches(window))
            scope.withWindow(window) {
                scope.withWindow(null) { assertFalse(scope.matches(window)) }
                assertTrue(scope.matches(window))
            }
            assertEquals(0.7f, Launcher.resolveRecentsFastBlurRatio(
                .7f, true, false, false, .3f, scope.matches(window)), .001f)
        } finally { activity.finish() }
    }

    @Test fun completeAnimationPreservesZeroAndScalesCapturedProgressWithoutAStaticMarker() {
        assertEquals(0f, Launcher.resolveCompleteRecentsBlurRatio(0f, true, false, .3f), .001f)
        assertEquals(.15f, Launcher.resolveCompleteRecentsBlurRatio(.5f, true, false, .3f), .001f)
        assertEquals(.7f, Launcher.resolveCompleteRecentsBlurRatio(.7f, true, true, .3f), .001f)
        assertEquals(0f, Launcher.resolveRecentsFastBlurRatio(0f, true, true, false, .3f, true), .001f)
    }

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
    fun scopedResetRatioIsIdempotentAcrossFastBlurOverloads() {
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
