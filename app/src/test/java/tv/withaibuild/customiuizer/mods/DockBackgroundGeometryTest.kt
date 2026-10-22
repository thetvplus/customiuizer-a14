package tv.withaibuild.customiuizer.mods

import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DockBackgroundGeometryTest {
    @Test
    fun offCenterIconsAndUnevenNavigationRoomKeepTheirExactCenterForEveryAdjustment() {
        val random = Random(0xA14)
        val safe = Rect(7, 4, 353, 105)
        repeat(240) {
            val count = random.nextInt(1, 7)
            val width = count * 43
            val x = random.nextInt(18, 335 - width)
            val y = random.nextInt(6, 43)
            val icons = Rect(x, y, x + width, y + 49)
            for (inset in intArrayOf(0, 12, 40)) {
                val base = Rect()
                DockBackgroundGeometry.fit(icons, safe, inset, 0f, base)
                for (adjustment in floatArrayOf(-10000f, -17f, -4f, 0f, 4f, 17f, 10000f)) {
                    val result = Rect()
                    DockBackgroundGeometry.fit(icons, safe, inset, adjustment, result)
                    assertFalse(result.isEmpty)
                    assertTrue(safe.contains(result))
                    assertEquals(icons.exactCenterX(), result.exactCenterX(), 0f)
                    assertEquals(icons.exactCenterY(), result.exactCenterY(), 0f)
                    val delta = base.left - result.left
                    assertEquals(delta, result.right - base.right)
                    assertEquals(delta, base.top - result.top)
                    assertEquals(delta, result.bottom - base.bottom)
                }
            }
        }
    }

    @Test
    fun navigationLimitRestrictsTheWholeExpansionAndNeverOnlyTheBottom() {
        val safe = Rect(0, 0, 360, 74)
        val icons = Rect(36, 16, 324, 64)
        val base = Rect()
        val expanded = Rect()
        DockBackgroundGeometry.fit(icons, safe, 0, 0f, base)
        DockBackgroundGeometry.fit(icons, safe, 0, 17f, expanded)
        assertEquals(74, expanded.bottom)
        val delta = base.left - expanded.left
        assertEquals(1, delta)
        assertEquals(delta, base.top - expanded.top)
        assertEquals(delta, expanded.right - base.right)
        assertEquals(delta, expanded.bottom - base.bottom)
    }

    @Test
    fun impossibleFullIconCoverageShrinksAboutTheIconCenterAndEmptyRowsStayEmpty() {
        val safe = Rect(0, 0, 360, 58)
        val icons = Rect(20, 0, 340, 80)
        val result = Rect()
        DockBackgroundGeometry.fit(icons, safe, 0, 17f, result)
        assertEquals(40f, result.exactCenterY(), 0f)
        assertEquals(22, result.top)
        assertEquals(58, result.bottom)
        DockBackgroundGeometry.constrain(Rect(), safe, result)
        assertTrue(result.isEmpty)
    }
}
