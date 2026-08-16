package tv.withaibuild.customiuizer.mods.utils

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepCounterLifecycleContractTest {

    @Test
    fun stepCounterOwnsObserversOnlyWhileAViewIsAttached() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/StepCounterController.kt")

        assertTrue(source.contains("fun bindStepView(sv: TextView)"))
        assertTrue(source.contains("sv.addOnAttachStateChangeListener"))
        assertTrue(source.contains("ensureScreenStateRegistered(ctx)"))
        assertTrue(source.contains("if (stepViewList.isEmpty()) releaseInactiveState()"))
        assertTrue(source.contains("ScreenStateController.removeListener(this)"))
        assertFalse(source.contains("fun removeStepViewByTag"))
    }

    @Test
    fun controlCenterBindsNewStepViewsToLifecycleOwner() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt")

        assertTrue(source.contains("StepCounterController.bindStepView(stepView)"))
        assertFalse(source.contains("StepCounterController.addStepView(stepView)"))
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
