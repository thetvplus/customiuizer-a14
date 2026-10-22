package tv.withaibuild.customiuizer.mods

import java.io.File
import java.lang.ref.WeakReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemUiViewLifecycleTest {

    @Test
    fun percentageOverlayDoesNotKeepStrongProcessLifetimeView() {
        val fields = SystemUIControlCenterHooks::class.java.declaredFields.filter { it.name.startsWith("mPct") }
        assertEquals(setOf("mPctRef", "mPctOwnerRef"), fields.map { it.name }.toSet())
        fields.forEach { assertEquals("${it.name} must not retain its referent", WeakReference::class.java, it.type) }
    }

    @Test
    fun secureQsClickPathDoesNotBuildASet() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt")
        val hook = source.substring(
            source.indexOf("fun SecureQSTilesHook"),
            source.indexOf("private var mPctRef")
        )

        assertFalse(hook.contains("HashSet"))
        assertFalse(hook.contains("secureTitles"))
        assertFalse(hook.contains("MainModule.mPrefs"))
        assertTrue(hook.contains("isSecureQsTile"))
        assertTrue(source.contains("\"intent\", \"custom\" -> snapshot.custom"))
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
