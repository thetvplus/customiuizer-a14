package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract gate for SystemUiResourceBootstrap.
 *
 * Verifies that the moved bootstrap function preserves the exact resource
 * registrations, preference keys, default values and ordering from the original
 * SystemUIStatusBarHooks.setupStatusBar implementation.
 */
class SystemUiResourceBootstrapContractTest {

    @Test
    fun bootstrap_preservesStatusbarTextIconFakeResourceRegistration() {
        val bootstrap = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt")

        assertTrue(
            "statusbar_text_icon fake resource registration must be present",
            bootstrap.contains("statusbarTextIconLayoutResId = MainModule.resHooks.addFakeResource(\"statusbar_text_icon\", R.layout.statusbar_text_icon, \"layout\")")
        )
    }

    @Test
    fun bootstrap_preservesStatusBarTopMarginResource() {
        val bootstrap = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt")

        assertTrue(
            "system_statusbar_topmargin and status_bar_padding_top replacement must be present",
            bootstrap.contains("system_statusbar_topmargin") &&
                bootstrap.contains("\"status_bar_padding_top\"")
        )
    }

    @Test
    fun bootstrap_preservesStatusBarHorizMarginResource() {
        val bootstrap = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt")

        assertTrue(
            "system_statusbar_horizmargin and status_bar_padding_start/end replacement must be present",
            bootstrap.contains("system_statusbar_horizmargin") &&
                bootstrap.contains("\"status_bar_padding_start\"") &&
                bootstrap.contains("\"status_bar_padding_end\"")
        )
    }

    @Test
    fun bootstrap_preservesControlCenterStyleSwitchResource() {
        val bootstrap = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt")

        assertTrue(
            "system_cc_enable_style_switch and force_use_control_panel replacement must be present",
            bootstrap.contains("system_cc_enable_style_switch") &&
                bootstrap.contains("\"force_use_control_panel\"")
        )
    }

    @Test
    fun bootstrap_preservesVolumeTimerResource() {
        val bootstrap = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt")

        assertTrue(
            "system_volumetimer and miui_volume_timer_segments replacement must be present",
            bootstrap.contains("system_volumetimer") &&
                bootstrap.contains("\"miui_volume_timer_segments\"")
        )
    }

    @Test
    fun bootstrap_preservesStatusBarIconSizeResources() {
        val bootstrap = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt")

        assertTrue(
            "system_statusbar_iconsize and icon size dimen replacements must be present",
            bootstrap.contains("system_statusbar_iconsize") &&
                bootstrap.contains("\"status_bar_icon_size\"") &&
                bootstrap.contains("\"status_bar_clock_size\"") &&
                bootstrap.contains("\"status_bar_icon_drawing_size\"") &&
                bootstrap.contains("\"status_bar_icon_drawing_size_dark\"") &&
                bootstrap.contains("\"status_bar_notification_icon_padding\"") &&
                bootstrap.contains("\"status_bar_icon_height\"")
        )
    }

    @Test
    fun bootstrap_preservesStepCounterInit() {
        val bootstrap = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt")

        assertTrue(
            "system_cc_show_stepcount gate and StepCounterController.initContext call must be present",
            bootstrap.contains("system_cc_show_stepcount") &&
                bootstrap.contains("StepCounterController.initContext(mContext)")
        )
    }

    @Test
    fun bootstrap_preservesDrawerDateSizeResource() {
        val bootstrap = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt")

        assertTrue(
            "system_drawer_hidedate / system_drawer_date_fontsize and qs_control_header_date_size replacement must be present",
            bootstrap.contains("system_drawer_hidedate") &&
                bootstrap.contains("system_drawer_date_fontsize") &&
                bootstrap.contains("\"qs_control_header_date_size\"")
        )
    }

    @Test
    fun bootstrap_preservesTapToUnlockResource() {
        val bootstrap = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt")

        assertTrue(
            "system_taptounlock and default_lockscreen_unlock_hint_text replacement must be present",
            bootstrap.contains("system_taptounlock") &&
                bootstrap.contains("\"default_lockscreen_unlock_hint_text\"")
        )
    }

    @Test
    fun bootstrap_preservesLockScreenTimeoutResource() {
        val bootstrap = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt")

        assertTrue(
            "system_lstimeout and config_lockScreenDisplayTimeout replacement must be present",
            bootstrap.contains("system_lstimeout") &&
                bootstrap.contains("\"config_lockScreenDisplayTimeout\"")
        )
    }

    @Test
    fun bootstrap_preservesRestartTimestampWrite() {
        val bootstrap = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt")

        assertTrue(
            "systemui_restart_time write must remain at the end of bootstrap",
            bootstrap.contains("Settings.System.putLong(") &&
                bootstrap.contains("\"systemui_restart_time\"")
        )
    }

    @Test
    fun bootstrap_doesNotMoveResourceCallsIntoFeatureInstallers() {
        val bootstrap = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt")

        assertFalse(
            "Resource bootstrap must not call install* / setup* feature functions",
            bootstrap.contains("installHook()") ||
                bootstrap.contains(".install(") ||
                bootstrap.contains("ModuleHelper.findAndHookMethod")
        )
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
