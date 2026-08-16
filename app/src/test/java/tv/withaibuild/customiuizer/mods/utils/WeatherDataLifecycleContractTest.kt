package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WeatherDataLifecycleContractTest {

    @Test
    fun processSingletonDoesNotRetainClockControllerOrViewContext() {
        val controller = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/WeatherDataController.kt")
        val hook = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt")

        assertTrue(controller.contains("private var updateTarget: WeakReference<Any>?"))
        assertTrue(controller.contains("val appContext = context.applicationContext"))
        assertTrue(controller.contains("updateTarget = WeakReference(clockController)"))
        assertTrue(controller.contains("@Volatile\n    var weatherInfo"))
        assertTrue(controller.contains("if (!queryFailureLogged)"))
        assertFalse(controller.contains("private var weakReferenceRunnable: Runnable?"))
        assertFalse(hook.contains("val mWeatherRunnable = Runnable"))
        assertTrue(hook.contains("WeatherDataController.initContext(mContext, thisObject)"))
        assertTrue(hook.contains("WeatherDataController.initContext(mContext, thisObject)\n\n                } catch (oom: OutOfMemoryError)"))
    }

    @Test
    fun asynchronousFailuresDoNotConsumeOutOfMemory() {
        val controller = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/WeatherDataController.kt")
        val moduleHelper = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt")

        assertTrue(controller.contains("catch (oom: OutOfMemoryError) {\n            throw oom"))
        assertTrue(moduleHelper.contains("if (throwable is OutOfMemoryError) throw throwable"))
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
