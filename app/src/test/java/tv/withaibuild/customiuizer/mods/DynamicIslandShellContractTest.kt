package tv.withaibuild.customiuizer.mods

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DynamicIslandShellContractTest {
    @Test fun everyIslandActivityUsesTheModuleWindowAndNativeForeheadHasNoAdapter() {
        var root = File(java.lang.System.getProperty("user.dir")!!).absoluteFile
        while (!File(root, "app/src/main").exists()) root = root.parentFile
        val base = File(root, "app/src/main/java/tv/withaibuild/customiuizer/mods")
        val controller = File(base, "IslandActivityController.kt").readText()
        assertTrue(controller.contains("IslandActivityView(context)"))
        assertTrue(controller.contains("window!!.addView(shell, params)"))
        for (name in listOf("IslandNativeLayout.kt", "IslandNativeMotion.kt", "IslandNativeModeFeedback.kt"))
            assertFalse(File(base, name).exists())
        val hooks = File(base, "SystemUIStrongToastHooks.kt").readText()
        assertFalse(hooks.contains("setValue"))
        assertFalse(hooks.contains("AnimatorSet"))
        assertFalse(hooks.contains("DynamicIslandStatusBarFade.acquire("))
    }
}
