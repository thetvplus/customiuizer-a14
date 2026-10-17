package tv.withaibuild.customiuizer.mods

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerBlurSteadyStateTest {

    @Test
    fun doFrameSteadyPathHasNoHotAllocations() {
        val body = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt",
            "internal fun onDoFrame"
        )

        assertFalse("doFrame must not allocate WeakReference on hot path", body.contains("WeakReference("))
        assertFalse("doFrame must not create State on hot path", body.contains("State()"))
        assertFalse("doFrame must not touch additional instance fields", body.contains("setAdditionalInstanceField"))
        assertFalse("doFrame must not touch additional instance fields", body.contains("removeAdditionalInstanceField"))
        assertFalse("doFrame must not read preferences", body.contains("MainModule.mPrefs"))
        assertTrue("doFrame resolves a cached target reference", body.contains("resolveDrawerBlurTargetRef("))
    }

    @Test
    fun applyBlurSteadyPathHasNoHotAllocations() {
        val body = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt",
            "internal fun onApplyBlur"
        )

        assertFalse("applyBlur must not allocate WeakReference", body.contains("WeakReference("))
        assertFalse("applyBlur must not create State", body.contains("State()"))
        assertFalse("applyBlur must not read preferences", body.contains("MainModule.mPrefs"))
        assertTrue("applyBlur checks the active scope", body.contains("isActive()"))
    }

    private fun methodBody(relativePath: String, prefix: String): String {
        val source = source(relativePath)
        val start = source.indexOf(prefix)
        check(start >= 0) { "Method prefix not found: $prefix" }
        var open = source.indexOf("{", start)
        check(open >= 0) { "Method body not found for: $prefix" }
        var depth = 0
        var i = open
        val n = source.length
        while (i < n) {
            val c = source[i]
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) return source.substring(start, i + 1)
            }
            i++
        }
        error("Unbalanced method body for: $prefix")
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
