package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

class SearchPreferencePageTest {
    @Test
    fun standaloneResultsKeepTheirControllerAndCreateFreshPages() {
        for (controller in listOf(
            "System", "SubFragment", "System_AutoBrightness", "System_Visualizer",
            "System_VibrationAmp", "System_BatteryIndicator", "System_NoScreenLock",
            "System_ScreenshotConfig", "Various_CallUIBright", "Various_HiddenFeatures",
        )) {
            val page = SearchPreferencePage(1, "Page", controller, false)
            val first = page.createFragment()
            assertEquals(controller, first?.javaClass?.simpleName)
            assertNotSame(first, page.createFragment())
        }
    }

    @Test
    fun unknownControllerDoesNotOpenAnUnrelatedPage() {
        assertNull(SearchPreferencePage(1, "Page", "unknown", false).createFragment())
    }
}
