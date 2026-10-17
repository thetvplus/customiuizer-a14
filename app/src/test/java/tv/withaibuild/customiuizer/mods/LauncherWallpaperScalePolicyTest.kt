package tv.withaibuild.customiuizer.mods

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherWallpaperScalePolicyTest {

    @Test
    fun eitherPrefSuppressesLauncherWallpaperZoom() {
        assertFalse(LauncherAnimationHooks.shouldSuppressLauncherWallpaperZoom(false, false))
        assertTrue(LauncherAnimationHooks.shouldSuppressLauncherWallpaperZoom(true, false))
        assertTrue(LauncherAnimationHooks.shouldSuppressLauncherWallpaperZoom(false, true))
        assertTrue(LauncherAnimationHooks.shouldSuppressLauncherWallpaperZoom(true, true))
        assertTrue(LauncherAnimationHooks.shouldKeepRecentsWallpaperZoomDisabled(true, false))
        assertFalse(LauncherAnimationHooks.shouldDisableLauncherWallpaperZoomPermanently(false))
        assertTrue(LauncherAnimationHooks.shouldDisableLauncherWallpaperZoomPermanently(true))
    }

    @Test
    fun launcherPrefOwnsDimLayerRecentsPrefDoesNot() {
        assertFalse(LauncherAnimationHooks.shouldDisableLauncherWallpaperZoomPermanently(false))
        assertTrue(LauncherAnimationHooks.shouldDisableLauncherWallpaperZoomPermanently(true))
    }

    @Test
    fun suppressedZoomStaysAtHomeScale() {
        assertEquals(0f, LauncherAnimationHooks.wallpaperZoomOutValue(0.8f, true), 0.001f)
        assertEquals(0.8f, LauncherAnimationHooks.wallpaperZoomOutValue(0.8f, false), 0.001f)
        assertTrue(LauncherAnimationHooks.shouldSkipWallpaperZoomAnimation(true))
        assertFalse(LauncherAnimationHooks.shouldSkipWallpaperZoomAnimation(false))
    }

    @Test
    fun applyPathHooksDoNotDependOnZoomEnabledRestore() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt")
        val bodyStart = source.indexOf("fun DisableLauncherWallpaperScale")
        check(bodyStart >= 0) { "DisableLauncherWallpaperScale missing" }
        val colorMode = source.indexOf("fun WallpaperColorModeHook", bodyStart)
        check(colorMode > bodyStart) { "WallpaperColorModeHook missing" }
        val body = source.substring(bodyStart, colorMode)
        assertTrue(body.contains("\"setWallpaperZoomOut\""))
        assertTrue(body.contains("\"animateWallpaperZoom\""))
        assertTrue(body.contains("android.app.WallpaperManager"))
        assertFalse(
            "recents enter/exit must not be the only wallpaper-zoom switch",
            body.contains("\"onStateEnabled\""),
        )
        assertFalse(body.contains("\"fastBlurWhenEnterRecents\""))
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
