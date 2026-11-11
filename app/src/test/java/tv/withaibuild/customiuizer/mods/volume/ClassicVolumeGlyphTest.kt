package tv.withaibuild.customiuizer.mods.volume

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.VectorDrawable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.withaibuild.customiuizer.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClassicVolumeGlyphTest {
    @Test fun nativeSelectedResourcesChooseTheMatchingGlyphAndUnknownRoutesStayNative() {
        assertEquals(R.drawable.ios12_volume_speaker, IosVolumeStyle.classicGlyphResource("ic_miui_volume_media_collpased", true))
        assertEquals(R.drawable.ios12_volume_mute, IosVolumeStyle.classicGlyphResource("ic_miui_volume_media_mute_collpased", true))
        assertEquals(R.drawable.ios12_volume_ringer, IosVolumeStyle.classicGlyphResource("ic_miui_volume_ringer_on_seekbar_collpased", false))
        assertEquals(R.drawable.ios12_volume_ringer_mute, IosVolumeStyle.classicGlyphResource("ic_miui_volume_ringer_mute_on_seekbar_collpased", false))
        for (name in listOf("ic_miui_volume_media", "ic_miui_volume_media_mute"))
            assertEquals("Headset, Bluetooth or remote route", 0, IosVolumeStyle.classicGlyphResource(name, false))
        for (name in listOf("ic_miui_volume_headset", "ic_miui_volume_headset_mute", "ic_miui_volume_voice", "vendor_unknown"))
            assertEquals(name, 0, IosVolumeStyle.classicGlyphResource(name, true))
        assertEquals(R.drawable.ios12_volume_speaker, IosVolumeStyle.classicGlyphResource("ic_miui_volume_speaker", false))
    }

    @Test fun allFourHudGlyphsRemainVectorResourcesWithDistinctNativeSilhouettes() {
        val resources = RuntimeEnvironment.getApplication().resources
        val ids = listOf(R.drawable.ios12_volume_speaker, R.drawable.ios12_volume_mute,
            R.drawable.ios12_volume_ringer, R.drawable.ios12_volume_ringer_mute)
        val pixels = ArrayList<IntArray>()
        for ((index, id) in ids.withIndex()) {
            val glyph = resources.getDrawable(id, null)
            assertTrue(glyph is VectorDrawable)
            assertEquals(if (index < 2) 77 else 69, glyph.intrinsicWidth)
            assertEquals(if (index < 2) 63 else 80, glyph.intrinsicHeight)
            val bitmap = Bitmap.createBitmap(231, 240, Bitmap.Config.ARGB_8888)
            try {
                glyph.setBounds(0, 0, glyph.intrinsicWidth * 3, glyph.intrinsicHeight * 3)
                glyph.draw(Canvas(bitmap))
                val data = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(data, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                assertTrue("The packaged vector must actually paint", data.count { it ushr 24 != 0 } > 1000)
                pixels.add(data)
            } finally { bitmap.recycle() }
        }
        assertFalse(pixels[0].contentEquals(pixels[1]))
        assertFalse(pixels[2].contentEquals(pixels[3]))
    }
}
