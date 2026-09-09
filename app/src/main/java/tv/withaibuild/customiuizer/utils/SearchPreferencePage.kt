package tv.withaibuild.customiuizer.utils

import tv.withaibuild.customiuizer.SubFragment
import tv.withaibuild.customiuizer.subs.System as SubSystem
import tv.withaibuild.customiuizer.subs.System_AutoBrightness
import tv.withaibuild.customiuizer.subs.System_BatteryIndicator
import tv.withaibuild.customiuizer.subs.System_NoScreenLock
import tv.withaibuild.customiuizer.subs.System_ScreenshotConfig
import tv.withaibuild.customiuizer.subs.System_VibrationAmp
import tv.withaibuild.customiuizer.subs.System_Visualizer
import tv.withaibuild.customiuizer.subs.Various_CallUIBright
import tv.withaibuild.customiuizer.subs.Various_HiddenFeatures

/** Shared by all indexed rows in one standalone page; contains no UI instance. */
class SearchPreferencePage(
    val resource: Int,
    val title: String,
    val controller: String,
    val dynamic: Boolean,
) {
    fun createFragment(): SubFragment? = when (controller) {
        "System" -> SubSystem()
        "SubFragment" -> SubFragment()
        "System_AutoBrightness" -> System_AutoBrightness()
        "System_Visualizer" -> System_Visualizer()
        "System_VibrationAmp" -> System_VibrationAmp()
        "System_BatteryIndicator" -> System_BatteryIndicator()
        "System_NoScreenLock" -> System_NoScreenLock()
        "System_ScreenshotConfig" -> System_ScreenshotConfig()
        "Various_CallUIBright" -> Various_CallUIBright()
        "Various_HiddenFeatures" -> Various_HiddenFeatures()
        else -> null
    }
}
