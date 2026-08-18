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
        assertTrue(body.contains("isFolderActiveForBlur"))
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
        assertTrue(body.contains("resolveAppliedFolderBlurRatio"))
        assertTrue(body.contains("\"fastBlur\""))
        assertFalse("do not depend on FolderCling open/close ABI", source.contains("\"com.miui.home.launcher.FolderCling\""))
    }

    @Test
    fun wallpaperZoomHotPathReadsSnapshotFlags() {
        val body = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt",
            "fun DisableLauncherWallpaperScale",
        )

        assertFalse(body.contains("MainModule.mPrefs"))
        assertTrue(body.contains("suppressLauncherWallpaperZoom"))
        assertTrue(body.contains("\"setWallpaperZoomOut\""))
        assertTrue(body.contains("\"animateWallpaperZoom\""))
    }

    @Test
    fun recentsBlurFastBlurReadsSnapshotRatio() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/Launcher.kt")
        val fastBlurBody = hookBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/Launcher.kt",
            "hookAllMethods(utilsClass, \"fastBlur\"",
        )

        assertFalse(fastBlurBody.contains("MainModule.mPrefs"))
        assertTrue(fastBlurBody.contains("recentsBlurRatio"))
        assertTrue(fastBlurBody.contains("isFolderActiveForBlur"))
        assertFalse(source.contains("system_disable_window_blurs"))
        assertFalse(source.contains("getBlurDisabledSetting"))
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

    @Test
    fun dualRowsInflateReadsLayoutSnapshot() {
        val body = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt",
            "fun DualRowsStatusbarHook",
        )
        assertFalse("dual-row MethodHooks must not read PrefMap", body.contains("MainModule.mPrefs"))
        assertTrue(body.contains("currentOrBuildStatusBarLayoutSnapshot()"))
        assertTrue(body.contains("clockSpan2Rows"))
        assertTrue(body.contains("netspeedAtSecondRow"))
        assertTrue(body.contains("dualRowsLeftRatio"))
    }

    @Test
    fun digitalSignalUpdateReadsLayoutSnapshot() {
        val body = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt",
            "fun StatusBarDigitalSignalHook",
        )
        assertFalse("digital-signal update path must not read PrefMap", body.contains("MainModule.mPrefs"))
        assertTrue(body.contains("digitalSignalDualRows"))
        assertTrue(body.contains("formatDigitalSignalLabel"))
    }

    @Test
    fun netSpeedIntervalReadsSnapshotMs() {
        val body = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt",
            "fun NetSpeedIntervalHook",
        )
        assertFalse(body.contains("MainModule.mPrefs"))
        assertTrue(body.contains("netSpeedIntervalMs"))
    }

    @Test
    fun mobileNetworkTypeReadsSnapshotName() {
        val body = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt",
            "fun MobileNetworkTypeHook",
        )
        assertFalse(body.contains("MainModule.mPrefs"))
        assertTrue(body.contains("resolveMobileTypeDisplayName"))
    }

    @Test
    fun horizMarginReadsSnapshotInsets() {
        val body = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt",
            "fun HorizMarginHook",
        )
        assertFalse(body.contains("MainModule.mPrefs"))
        assertTrue(body.contains("horizMarginLeft"))
        assertTrue(body.contains("horizMarginRight"))
    }

    @Test
    fun hideMobileIndicatorReadsLayoutSnapshot() {
        val body = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt",
            "fun HideMobileNetworkIndicatorHook",
        )
        assertFalse(body.contains("MainModule.mPrefs"))
        assertTrue(body.contains("mobileTypeIconOpt"))
        assertTrue(body.contains("hideMobileNetworkIndicator"))
    }

    @Test
    fun secureQsClickReadsSnapshot() {
        val body = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt",
            "fun SecureQSTilesHook",
        )
        assertFalse(body.contains("MainModule.mPrefs"))
        assertTrue(body.contains("isSecureQsTile"))
        assertTrue(body.contains("secureQsSnapshot.keepOpened"))
    }

    @Test
    fun lockScreenAlbumArtReadsConfigSnapshot() {
        val body = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt",
            "fun LockScreenAlbumArtHook",
        )
        assertFalse("album-art update must not read PrefMap", body.contains("MainModule.mPrefs"))
        assertTrue(body.contains("lockScreenConfig"))
        assertTrue(body.contains("albumArtBlur"))
    }

    @Test
    fun lockScreenShortcutsReadConfigSnapshot() {
        val body = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt",
            "fun LockScreenShortcutHook",
        )
        assertFalse(body.contains("MainModule.mPrefs"))
        assertTrue(body.contains("leftTapAction"))
        assertTrue(body.contains("rightAction"))
    }

    @Test
    fun homescreenSwipeInterceptsReadSnapshot() {
        val body = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt",
            "fun HomescreenSwipesHook",
        )
        assertFalse(body.contains("MainModule.mPrefs"))
        assertTrue(body.contains("swipeDownCustom"))
        assertTrue(body.contains("swipeUpCustom"))
    }

    @Test
    fun pinchAndFsgHooksReadSnapshot() {
        val pinch = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt",
            "fun LauncherPinchHook",
        )
        assertFalse(pinch.contains("MainModule.mPrefs"))
        assertTrue(pinch.contains("pinchCustom"))

        val fsg = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt",
            "fun FSGesturesHook",
        )
        assertFalse(fsg.contains("MainModule.mPrefs"))
        assertTrue(fsg.contains("fsgHorizApps"))

        val swipeStop = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt",
            "fun SwipeAndStopActionHook",
        )
        assertFalse(swipeStop.contains("MainModule.mPrefs"))
        assertTrue(swipeStop.contains("disableSwipeAndStopVibrate"))
    }

    @Test
    fun titleFontAndMarginReadIconStyleSnapshot() {
        val font = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt",
            "fun TitleFontSizeHook",
        )
        assertFalse(font.contains("MainModule.mPrefs"))
        assertTrue(font.contains("iconStyleConfig.titleFontSizeSp"))

        val margin = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt",
            "fun TitleTopMarginHook",
        )
        assertFalse(margin.contains("MainModule.mPrefs"))
        assertTrue(margin.contains("iconStyleConfig.titleTopMargin"))
    }

    @Test
    fun folderCloseAndPrivacyReadSnapshot() {
        val close = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt",
            "fun CloseFolderOnLaunchHook",
        )
        assertFalse(close.contains("MainModule.mPrefs"))
        assertTrue(close.contains("closeFoldersMode"))

        val cols = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt",
            "fun FolderColumnsHook",
        )
        assertFalse(cols.contains("MainModule.mPrefs"))
        assertTrue(cols.contains("folderCols"))
        assertTrue(cols.contains("folderSpace"))

        val privacy = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt",
            "fun PrivacyFolderHook",
        )
        assertFalse("startSecurityHide must not read PrefMap", privacy.contains("MainModule.mPrefs"))
        assertTrue(privacy.contains("privacyGest"))
    }

    @Test
    fun controlsKeyPathsReadSnapshot() {
        val volume = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt",
            "fun VolumeCursorHook",
        )
        assertFalse(volume.contains("MainModule.mPrefs"))
        assertTrue(volume.contains("controlsConfig.volumeCursorApps"))

        val nav = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt",
            "fun NavBarActionsHook",
        )
        assertFalse(nav.contains("MainModule.mPrefs"))
        assertTrue(nav.contains("controlsConfig.backLongAction"))

        val fp = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt",
            "fun FingerprintHapticSuccessHook",
        )
        assertFalse(fp.contains("MainModule.mPrefs"))
        assertTrue(fp.contains("fingerprintSuccess"))
    }

    @Test
    fun audioHapticsReadSnapshot() {
        val qs = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt",
            "fun QSHapticHook",
        )
        assertFalse(qs.contains("MainModule.mPrefs"))
        assertTrue(qs.contains("audioHapticsConfig.qsHaptics"))

        val muffled = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt",
            "fun MuffledVibrationHook",
        )
        assertFalse(muffled.contains("MainModule.mPrefs"))
        assertTrue(muffled.contains("ampRinger"))
    }

    @Test
    fun variousAndSystemMiscReadSnapshots() {
        val callUi = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/Various.kt",
            "fun ShowCallUIHook",
        )
        assertFalse(callUi.contains("MainModule.mPrefs"))
        assertTrue(callUi.contains("variousConfig.showCallUi"))

        val toast = methodBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/System.kt",
            "fun ToastTimeHook",
        )
        assertFalse(toast.contains("MainModule.mPrefs"))
        assertTrue(toast.contains("systemMiscConfig.toastTime"))
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
