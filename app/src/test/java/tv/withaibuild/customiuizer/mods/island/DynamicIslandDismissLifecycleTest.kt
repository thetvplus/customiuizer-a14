package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DynamicIslandDismissLifecycleTest {
    @Test fun moduleWindowAndItsPresentationAreReleasedTogether() {
        var root = File(java.lang.System.getProperty("user.dir")!!).absoluteFile
        while (!File(root, "app/src/main").exists()) root = root.parentFile
        val source = File(root, "app/src/main/java/tv/withaibuild/customiuizer/mods/island/IslandActivityController.kt").readText()
        assertTrue(source.contains("window?.removeViewImmediate(shell)"))
        assertTrue(source.contains("presentation.cancel()"))
    }
}
