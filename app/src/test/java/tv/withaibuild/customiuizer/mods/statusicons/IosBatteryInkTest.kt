package tv.withaibuild.customiuizer.mods.statusicons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.withaibuild.customiuizer.mods.utils.feature.IosStatusBarBatteryFeature
import tv.withaibuild.customiuizer.utils.PrefMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosBatteryInkTest {
    private fun fillColor(ink: IosBatteryInk) = org.robolectric.util.ReflectionHelpers.getField<android.graphics.Paint>(ink, "fill").color
    private fun update(ink: IosBatteryInk, level: Int, saver: Boolean = false, low: Boolean = false, charging: Boolean = false) =
        ink.update(level, Color.WHITE, Color.RED, Color.YELLOW, Color.GREEN, saver, low, charging)

    @Test fun saverAndLowStateHavePriorityOverChargingWithoutLosingNativeColors() {
        val ink = IosBatteryInk()
        update(ink, 10, saver = true, low = true, charging = true)
        assertEquals(Color.YELLOW, fillColor(ink))
        update(ink, 10, low = true, charging = true)
        assertEquals(Color.RED, fillColor(ink))
        update(ink, 80, charging = true)
        assertEquals(Color.GREEN, fillColor(ink))
        update(ink, 80)
        assertEquals(Color.WHITE, fillColor(ink))
    }
    @Test fun fillReflectsActualLevelAndStaysInsideTheNativeAllocation() {
        val view = View(RuntimeEnvironment.getApplication()).apply {
            setPadding(8, 4, 8, 4); layout(0, 0, 88, 44)
        }
        val ink = IosBatteryInk().apply { active = true }
        val amounts = listOf(0, 50, 100).map { level ->
            val bitmap = Bitmap.createBitmap(88, 44, Bitmap.Config.ARGB_8888)
            try {
                update(ink, level)
                val canvas = Canvas(bitmap); val saved = canvas.saveCount
                assertTrue(ink.draw(canvas, view))
                assertEquals(saved, canvas.saveCount)
                assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0))
                assertEquals(Color.TRANSPARENT, bitmap.getPixel(87, 43))
                val pixels = IntArray(88 * 44); bitmap.getPixels(pixels, 0, 88, 0, 0, 88, 44)
                pixels.count { Color.alpha(it) > 240 }
            } finally { bitmap.recycle() }
        }
        assertTrue(amounts[0] < amounts[1]); assertTrue(amounts[1] < amounts[2])
    }
    @Test fun unadmittedAndEmptyViewsKeepTheNativeDrawPathAvailable() {
        val view = View(RuntimeEnvironment.getApplication())
        val ink = IosBatteryInk()
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        try {
            assertFalse(ink.draw(Canvas(bitmap), view))
            ink.active = true
            assertFalse(ink.draw(Canvas(bitmap), view))
            assertEquals(Color.TRANSPARENT, bitmap.getPixel(5, 5))
        } finally { bitmap.recycle() }
    }
    @Test fun disabledAndConflictingPresentationOwnersCannotInstallTheBatteryStyle() {
        val prefs = PrefMap()
        assertFalse(IosStatusBarBatteryFeature.evaluateEnabled(prefs))
        prefs.put("system_statusbar_ios_battery", true)
        assertTrue(IosStatusBarBatteryFeature.evaluateEnabled(prefs))
        for (key in listOf("system_statusbar_duo", "system_statusbar_batterystyle", "system_statusbaricons_battery1")) {
            prefs.put(key, true)
            assertFalse(key, IosStatusBarBatteryFeature.evaluateEnabled(prefs))
            prefs.put(key, false)
        }
        assertTrue(IosStatusBarBatteryFeature.evaluateEnabled(prefs))
    }
}
