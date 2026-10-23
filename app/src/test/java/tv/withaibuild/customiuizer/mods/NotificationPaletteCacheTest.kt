package tv.withaibuild.customiuizer.mods

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationPaletteCacheTest {
    private fun bitmap() = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
    @Test fun repeatedInflationReusesOneCompletePalette() {
        val cache = NotificationPaletteCache()
        val icon = bitmap()
        var computations = 0
        repeat(50) {
            val palette = cache.resolve(icon, 10023, "app", false) { computations++; NotificationPalette(1, 2, 3, Color.RED) }
            assertEquals(0x9eff0000.toInt(), palette.tint)
        }
        assertEquals(1, computations)
    }

    @Test fun themeUserPackageIconAndBitmapGenerationInvalidateTheSingleEntry() {
        val cache = NotificationPaletteCache()
        var icon = bitmap()
        var computations = 0
        fun resolve(uid: Int = 10023, pkg: String = "app", dark: Boolean = false) = cache.resolve(icon, uid, pkg, dark) {
            computations++; NotificationPalette(computations, 2, 3, 4)
        }
        resolve(); resolve(dark = true); resolve(uid = 1010023); resolve(pkg = "other")
        icon = bitmap(); resolve()
        icon.eraseColor(Color.BLUE); resolve()
        assertEquals(6, computations)
        assertNull(cache.current(1010023, "app", false))
        assertNull(cache.current(10023, "app", true))
        assertNotNull(cache.current(10023, "app", false))
    }

    @Test fun failedComputationNeverPublishesPartialColors() {
        val cache = NotificationPaletteCache()
        val first = NotificationPalette(1, 2, 3, 4)
        cache.resolve(bitmap(), 10023, "app", false) { first }
        try {
            cache.resolve(bitmap(), 10023, "app", true) { throw IllegalStateException("icon unavailable") }
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertSame(first, cache.current(10023, "app", false))
        assertNull(cache.current(10023, "app", true))
    }
}
