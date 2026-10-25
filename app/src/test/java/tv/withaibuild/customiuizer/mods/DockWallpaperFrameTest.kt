package tv.withaibuild.customiuizer.mods

import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DockWallpaperFrameTest {
    @Test fun romIgnoringCropIsAcceptedOnlyForTheKnownDefaultDisplaySize() {
        val crop = Rect(0, 1880, 1080, 2360)
        assertEquals(Rect(0, 658, 378, 826), DockWallpaperFrame.crop(378, 840, crop, .35f, 1080, 2400))
        assertNull(DockWallpaperFrame.crop(378, 900, crop, .35f, 1080, 2400))
        assertNull(DockWallpaperFrame.crop(378, 840, crop, .35f, 0, 0))
    }
    @Test fun actualCroppedFramesPassThroughAndUnknownOrOutOfRangeCoordinatesFailClosed() {
        val crop = Rect(0, 1880, 1080, 2360)
        assertEquals(Rect(0, 0, 378, 168), DockWallpaperFrame.crop(378, 168, crop, .35f, 1080, 2400))
        assertNull(DockWallpaperFrame.crop(378, 840, Rect(-1, 1880, 1080, 2360), .35f, 1080, 2400))
        assertNull(DockWallpaperFrame.crop(378, 840, crop, Float.NaN, 1080, 2400))
    }
}
