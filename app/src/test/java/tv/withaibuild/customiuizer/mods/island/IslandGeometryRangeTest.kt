package tv.withaibuild.customiuizer.mods.island

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandGeometryRangeTest {
    @Test fun heightChangesLinkTextArtworkWaveformAndMaskAndKeepTheDefaultMetrics() {
        val context: android.app.Application = RuntimeEnvironment.getApplication()
        val shell = IslandActivityView(context)
        val density = context.resources.displayMetrics.density
        val icon = ReflectionHelpers.getField<ImageView>(shell, "icon")
        val wave = ReflectionHelpers.getField<View>(shell, "wave")
        val title = ReflectionHelpers.getField<TextView>(shell, "title")
        var previousIcon = 0; var previousText = 0f; var previousWave = 0
        for (heightDp in listOf(32, 40, 64)) {
            val height = (heightDp * density).roundToInt()
            shell.contentHeight(heightDp)
            shell.geometry((320 * density).roundToInt(), height, (40 * density).roundToInt())
            assertTrue(icon.layoutParams.height in 1..height)
            assertTrue(wave.layoutParams.height in 1..height)
            assertTrue(wave.layoutParams.width <= icon.layoutParams.width)
            assertTrue(icon.layoutParams.height > previousIcon)
            assertTrue(title.textSize > previousText)
            assertTrue(wave.layoutParams.height > previousWave)
            val mask = ReflectionHelpers.getField<GradientDrawable>(shell, "iconMask")
            assertTrue(mask.cornerRadius < icon.layoutParams.width / 2f)
            if (heightDp == 40) {
                assertEquals((24 * density).roundToInt(), icon.layoutParams.width)
                assertEquals((20 * density).roundToInt(), wave.layoutParams.height)
                assertEquals((7 * density).roundToInt().toFloat(), mask.cornerRadius, .001f)
            }
            previousIcon = icon.layoutParams.height; previousText = title.textSize; previousWave = wave.layoutParams.height
        }
        assertFalse(shell.isAttachedToWindow)
    }
}
