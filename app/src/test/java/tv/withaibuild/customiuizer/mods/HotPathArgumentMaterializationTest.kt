package tv.withaibuild.customiuizer.mods

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HotPathArgumentMaterializationTest {

    @Test
    fun launcherAndGesturePassThroughDoNotMaterializeArguments() {
        val launcher = source("app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt")
            .section("fun FixAnimHook", "fun NoUnlockAnimationHook")
        assertAfter(launcher, "if (scale == 1.0f)", "val args = XposedHelpers.getArgsArray(chain)")
        assertTrue(launcher.contains("ValueAnimator.getDurationScale()"))
        assertTrue(!launcher.contains("Helpers.getAnimationScale"))
        assertTrue(launcher.contains("com.miui.home.launcher.animate.SpringAnimator"))
        assertTrue(launcher.contains("chain.proceed(args)"))
        assertTrue(!launcher.contains("RectFSpringAnim"))
        assertTrue(!launcher.contains("setFloatField"))

        val controls = source("app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt")
            .section("fun BackGestureAreaWidthHook", "fun HideNavBarHook")
        assertAfter(controls, "if (requestedSize == mGestureStubDefaultSize)", "val args = XposedHelpers.getArgsArray(chain)")
    }

    @Test
    fun screenshotPassThroughDoesNotMaterializeArguments() {
        val system = source("app/src/main/java/tv/withaibuild/customiuizer/mods/System.kt")
        val dexKitHook = system.section("val changeFormatHook", "if (methodData != null)")
        assertAfter(dexKitHook, "if (chain.args.size < 7)", "val args = XposedHelpers.getArgsArray(chain)")

        val bitmapHook = system.section(
            "ModuleHelper.hookAllMethods(\"android.graphics.Bitmap\"",
            "fun ToastTimeHook"
        )
        assertAfter(bitmapHook, "chain.getArg(2) is ByteArrayOutputStream", "val args = XposedHelpers.getArgsArray(chain)")
    }

    @Test
    fun systemServerPassThroughDoesNotMaterializeArguments() {
        val audio = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt")
            .section("internal fun interceptVibrationScale", "internal fun vibrationPeriodContains")
        assertTrue("native scaling never copies the argument array", !audio.contains("getArgsArray"))
        assertAfter(audio, "val nativeEffect = chain.proceed()", "val cfg = audioHapticsConfig")
        assertAfter(audio, "if (ratio == 1f)", "scale.invoke(nativeEffect, ratio)")
        assertAfter(audio, "if (!vibrationPeriodContains", "scale.invoke(nativeEffect, ratio)")

        val windows = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt")
            .section("fun TempHideOverlayAppHook", "fun BetterPopupsAllowFloatHook")
        assertTrue(
            "overlay type gate must remain before any flag mutation",
            windows.contains("WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY"),
        )
        assertTrue(
            "TempHideOverlayAppHook must not materialize constructor args",
            !windows.contains("getArgsArray"),
        )

        val helper = source("app/src/main/java/tv/withaibuild/customiuizer/mods/WindowSurfaceControlArgs.kt")
            .section("fun interceptFlags", "private fun isAospLegacySurfaceConstructor")
        assertAfter(helper, "transform(flags, windowType)", "val args = XposedHelpers.getArgsArray(chain)")
    }

    private fun assertAfter(text: String, earlier: String, later: String) {
        val earlierIndex = text.indexOf(earlier)
        val laterIndex = text.indexOf(later)
        assertTrue("Missing expected source token: $earlier", earlierIndex >= 0)
        assertTrue("Missing expected source token: $later", laterIndex >= 0)
        assertTrue("$later must stay after $earlier", laterIndex > earlierIndex)
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

    private fun String.section(start: String, end: String): String {
        val startIndex = indexOf(start)
        val endIndex = indexOf(end, startIndex + start.length)
        check(startIndex >= 0 && endIndex > startIndex) {
            "Could not extract source section between '$start' and '$end'"
        }
        return substring(startIndex, endIndex)
    }
}
