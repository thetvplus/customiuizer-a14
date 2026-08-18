package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-level wiring tests for the dark-tint registration route.
 *
 * These tests verify that the production call sites actually delegate to
 * [CustomTextIconTintRoute], that the route contains the attach/detach/dispose
 * lifecycle hooks, and that the right-hand path binds to the per-generation
 * [StatusBarDisplayRegistry].
 */
class CustomTextIconTintWiringTest {

    @Test
    fun leftPathRegistersWithCustomTextIconTintRoute() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/DeviceInfoMonitor.kt")
        val addHolder = methodBody(source, "private fun interceptAddHolder")

        assertTrue("left addHolder must call CustomTextIconTintRoute.register", addHolder.contains("CustomTextIconTintRoute.register(iconView, classLoader, \"left\")"))
    }

    @Test
    fun rightPathRegistersWithCustomTextIconTintRouteAndBindsToGenerationOwner() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt")
        val dualRows = methodBody(source, "fun DualRowsStatusbarHook")
        val addIcons = methodBody(source, "private fun addDualRowsDeviceInfoIcons")

        assertTrue("dual rows must create both left and right custom icon slots", dualRows.contains("addDualRowsDeviceInfoIcons"))
        assertTrue("right dual rows must target secondRight", dualRows.contains("parent = secondRight"))
        assertTrue("left dual rows must target leftContainer", dualRows.contains("parent = leftContainer"))
        assertTrue("dual-row custom icons must register a dark-tint handle", addIcons.contains("CustomTextIconTintRoute.register(iconView, classLoader, side)"))
        assertTrue("right must bind the returned handle to the status-bar generation owner", addIcons.contains("state.registrations.register(sbView)"))
        assertTrue("right owner release must call handle.release", addIcons.contains("handle.release(\"generation-replaced\")"))
    }

    @Test
    fun stopMonitoringReleasesAllCustomTextIconTints() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/DeviceInfoMonitor.kt")
        val stop = methodBody(source, "private fun stopMonitoring")

        assertTrue("stopMonitoring must release all custom text icon tints", stop.contains("CustomTextIconTintRoute.releaseAll()"))
    }

    @Test
    fun customTextIconTintRouteSplitsLifecycle() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/CustomTextIconTintRoute.kt")

        assertTrue("route must expose a handle interface", source.contains("interface DarkTintRegistrationHandle"))
        assertTrue("route must listen to onViewAttachedToWindow", source.contains("onViewAttachedToWindow"))
        assertTrue("route must listen to onViewDetachedFromWindow", source.contains("onViewDetachedFromWindow"))
        assertTrue("route must call removeDarkReceiver on detach", source.contains("\"removeDarkReceiver\""))
        assertTrue("route must call addDarkReceiver on attach", source.contains("\"addDarkReceiver\""))
        assertTrue("route must have a dispose terminal path", source.contains("fun dispose("))
        assertTrue("route must use weak references to avoid permanent strong hold", source.contains("WeakReference"))
        assertTrue("route must track disposed", source.contains("isDisposed"))
    }

    @Test
    fun darkTintRegistrationStateTracksLifecycle() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/DarkTintRegistrationState.kt")

        assertTrue("DarkTintRegistrationState must have register", source.contains("fun register("))
        assertTrue("DarkTintRegistrationState must have release", source.contains("fun release("))
        assertTrue("DarkTintRegistrationState must have dispose", source.contains("fun dispose("))
        assertTrue("DarkTintRegistrationState must track isActive", source.contains("val isActive"))
        assertTrue("DarkTintRegistrationState must track isReleased", source.contains("val isReleased"))
        assertTrue("DarkTintRegistrationState must track isDisposed", source.contains("val isDisposed"))
    }

    private fun source(relativePath: String): String {
        var directory = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.exists()) return candidate.readText()
            val parent = directory.parentFile
            if (parent == null || parent == directory) break
            directory = parent
        }
        throw IllegalStateException("Could not find $relativePath")
    }

    private fun methodBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        check(start >= 0) { "Method $signature not found" }
        val braces = source.indexOf("{", start)
        check(braces >= 0) { "No opening brace for $signature" }

        var depth = 0
        var pos = braces
        while (pos < source.length) {
            when (source[pos]) {
                '{' -> depth++
                '}' -> depth--
            }
            if (depth == 0) return source.substring(braces, pos + 1)
            pos++
        }
        throw IllegalStateException("Unterminated method body for $signature")
    }
}
