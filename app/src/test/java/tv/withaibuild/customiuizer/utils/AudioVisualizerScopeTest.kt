package tv.withaibuild.customiuizer.utils

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural guard: every [CoroutineScope] used by AudioVisualizer must carry the module's
 * host-process failure handler.
 *
 * AudioVisualizer is a custom View that is attached to the SystemUI notification panel and
 * lock screen. Its coroutines therefore run in the SystemUI process, where an unhandled
 * exception would kill the entire interface. Other scoped utilities (BatteryIndicator,
 * WeatherDataController, StepCounterController, LockScreenAlbumArtController) already attach
 * [tv.withaibuild.customiuizer.mods.utils.ModuleHelper.coroutineFailureHandler]; AudioVisualizer
 * must do the same.
 */
class AudioVisualizerScopeTest {

    @Test
    fun viewScopeAttachesCoroutineFailureHandler() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/utils/AudioVisualizer.kt")

        val viewScopeLine = source.lineSequence()
            .filter { it.contains("viewScope") && it.contains("CoroutineScope(") }
            .firstOrNull()

        assertTrue("viewScope CoroutineScope declaration must exist", viewScopeLine != null)
        assertTrue(
            "viewScope must use ModuleHelper.coroutineFailureHandler: $viewScopeLine",
            viewScopeLine!!.contains("ModuleHelper.coroutineFailureHandler")
        )
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
