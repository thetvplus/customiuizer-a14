package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PreferenceClickFeedbackContractTest {

    @Test
    fun remoteMirrorWorkDoesNotRunInsidePreferenceCallback() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/utils/XposedServiceManager.kt")
        val listenerStart = source.indexOf("private val prefsChanged")
        val listenerEnd = source.indexOf("private fun requestPreferenceWrite", listenerStart)
        val listener = source.substring(listenerStart, listenerEnd)

        assertTrue(listener.contains("requestPreferenceWrite(sharedPreferences, key, generation)"))
        assertFalse(listener.contains("sharedPreferences.all[key]"))
        assertFalse(listener.contains("remote.edit()"))
        assertTrue(source.contains("Dispatchers.Default.limitedParallelism(1)"))
        assertTrue(source.contains("mirrorScope.launch { runMirror(generation, reason) }"))
    }

    @Test
    fun switchReceivesImmediateParentPressedState() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/prefs/CheckBoxPreferenceEx.kt")
        assertTrue(source.contains("android.R.id.switch_widget"))
        assertTrue(source.contains("isDuplicateParentStateEnabled = true"))
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
