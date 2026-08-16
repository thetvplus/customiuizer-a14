package tv.withaibuild.customiuizer.mods

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural regression guards for the P1-B hot paths.
 *
 * These tests scan source files to verify that the identified callbacks do not reach the
 * preference map directly and that the expected snapshot/cached helpers are present. They do
 * not exercise runtime behavior; behavioral coverage lives in the dedicated behavior test files.
 */
class UiHotPathPreferenceSnapshotTest {

    @Test
    fun keyguardSetTranslationReadsSnapshotFlags() {
        val body = hookBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt",
            "\"com.android.keyguard.KeyguardMoveHelper\"",
        )

        assertFalse("setTranslation runs per touch sample", body.contains("MainModule.mPrefs"))
        assertTrue(body.contains("swipeRightOff"))
        assertTrue(body.contains("swipeLeftOff"))
    }

    @Test
    fun folderLayoutReadsSnapshotAndCachedFields() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt")
        val body = hookBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt",
            "\"onLayout\"",
        )

        assertFalse("onLayout is a layout callback", body.contains("MainModule.mPrefs"))
        assertFalse("field lookups belong to the cold path", body.contains("XposedHelpers.getObjectField"))
        assertTrue(body.contains("folderWidthEnabled"))
        assertTrue(source.contains("private fun resolveFolderLayoutFields("))
    }

    @Test
    fun folderBlurReadsSnapshotRatio() {
        val body = hookBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt",
            "\"getLauncherBlur\"",
        )

        assertFalse(body.contains("MainModule.mPrefs"))
        assertTrue(body.contains("folderBlurRatio"))
    }

    @Test
    fun folderBlurInterceptsLauncherOpenCloseEntry() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt")
        val body = hookBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt",
            "\"fastBlurWhenOpenOrCloseFolder\"",
        )

        assertFalse(body.contains("MainModule.mPrefs"))
        assertTrue(body.contains("folderBlurRatio"))
        assertTrue(body.contains("\"fastBlur\""))
        assertFalse("do not depend on FolderCling open/close ABI", source.contains("\"com.miui.home.launcher.FolderCling\""))
    }

    @Test
    fun batteryUpdateAllReadsOneSnapshotAndAvoidsHotPathAllocation() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt")
        val body = hookBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt",
            "\"updateAll\"",
        )
        val reconcileBody = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt",
            "internal fun reconcileBatteryView"
        )
        val matchBody = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt",
            "internal fun matchesTarget"
        )

        assertFalse("updateAll must not read nine preferences per call", body.contains("MainModule.mPrefs"))
        assertTrue(body.contains("val style = batteryStyle ?: return"))
        assertFalse("child order must be checked before mutating the hierarchy", body.contains("removeView"))
        assertTrue("updateAll delegates to reconcile helper", body.contains("reconcileBatteryView("))
        assertTrue("snapshot-driven style is applied from the helper", reconcileBody.contains("applyBatteryStyle("))
        assertTrue("baseline is restored when style returns to default", reconcileBody.contains("restoreBatteryBaseline("))
        assertTrue("swap helper is still used", source.contains("applyBatteryChildSwapIfNeeded("))
        assertTrue("text size changes are idempotent", source.contains("setTextSizeIfChanged("))
        assertTrue("padding changes are idempotent", source.contains("setPaddingRelativeIfChanged("))

        assertFalse("repeated target check must not allocate Pair", matchBody.contains("Pair<"))
        assertFalse("repeated target check must not use destructured pair", matchBody.contains(" to "))
        assertFalse("repeated target check must not call computePaddings", matchBody.contains("computePaddings("))
        assertFalse("repeated target check must not construct new Padding", matchBody.contains("Padding("))
    }

    /** Returns the brace-balanced hook body that follows the first occurrence of [marker]. */
    private fun hookBody(relativePath: String, marker: String): String {
        val source = source(relativePath)
        val markerOffset = source.indexOf(marker)
        check(markerOffset >= 0) { "Marker not found: $marker" }
        var index = source.indexOf("object : MethodHook() {", markerOffset)
        check(index >= 0) { "Hook body not found after $marker" }
        index = source.indexOf('{', index)
        var depth = 0
        while (true) {
            when (source[index]) {
                '{' -> depth++
                '}' -> depth--
            }
            if (depth == 0) return source.substring(markerOffset, index + 1)
            index++
        }
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
