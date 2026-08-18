package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DynamicIslandDismissLifecycleTest {
    @Test
    fun romDetachRestoresTheSharedBaseline() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt")
        assertTrue(source.contains("resetMatchModeCapsule(root)"))
        assertTrue(source.contains("clipToOutline = false"))
        assertTrue(source.contains("IslandPillOutline"))
        assertTrue(source.contains("DynamicIslandStatusBarFade.release(root)"))
        assertTrue(source.contains("applyIslandRecallHit("))
        assertTrue(source.contains("clearIslandRecallHit("))
        assertTrue(source.contains("host.touchDelegate = null"))
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
