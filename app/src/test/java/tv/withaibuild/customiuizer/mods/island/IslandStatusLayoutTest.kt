package tv.withaibuild.customiuizer.mods.island

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.content.res.Configuration
import java.util.Locale
import tv.withaibuild.customiuizer.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandStatusLayoutTest {
    @Test fun oneCompleteStatusLineKeepsFullSemanticsAccessibleAndRestoresMediaLabels() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val shell = IslandActivityView(activity).apply { powerSaveMotion = true }
        activity.setContentView(shell, FrameLayout.LayoutParams(240, 44))
        val mode = "Enabled"; val action = "Do not disturb mode"
        val ordinaryWidth = shell.textWidth("A saved image")
        val ordinaryFit = shell.fitsToast("A saved image", 200, 40)
        val width = shell.toastWidth(action,240,400,40,40,expanded=false,status=true)
        val height = shell.statusHeight(action, width, 40, 40)!!
        shell.bind(mode, action, null, false, false, false, 0, false, status = true)
        shell.geometry(width, height, 40)
        shell.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        shell.layout(0, 0, width, height)
        val label = (shell.contentAt(1) as LinearLayout).getChildAt(0) as TextView
        assertEquals(action, label.text.toString())
        assertTrue(shell.contentDescription.toString().contains(action))
        assertNull(label.ellipsize)
        assertEquals(label.text.length, label.layout.getLineEnd(label.layout.lineCount - 1))
        assertEquals(1,label.maxLines)
        assertEquals(1,label.layout.lineCount)
        assertTrue(label.height >= label.layout.height)
        for (line in 0 until label.layout.lineCount) assertEquals(0, label.layout.getEllipsisCount(line))
        val image = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        shell.draw(android.graphics.Canvas(image))
        var visibleTextPixels = 0
        for (y in 0 until height) for (x in (width+40)/2 until width-16) {
            val pixel = image.getPixel(x, y)
            if (android.graphics.Color.red(pixel) > 150 && android.graphics.Color.alpha(pixel) > 150) visibleTextPixels++
        }
        assertTrue("The status must actually be drawn in its right wing", visibleTextPixels > 50)
        image.recycle()
        assertEquals(ordinaryWidth, shell.textWidth("A saved image"), .001f)
        assertEquals(ordinaryFit, shell.fitsToast("A saved image", 200, 40))
        shell.bind("Track", "Artist", null, true, true, true, 0, false)
        assertEquals(1, label.maxLines)
        assertEquals(android.text.TextUtils.TruncateAt.END, label.ellipsize)
        activity.finish()
    }

    @Test fun localizedStatusUsesOneCompleteLineAcrossFontScalesCameraRoomsAndOffsets() {
        val base=org.robolectric.RuntimeEnvironment.getApplication()
        for(language in listOf("zh-CN","zh-TW","en","ja","cs-CZ","es-ES","pt-BR","ru-RU","tr-TR","vi-VN"))
            for(scale in floatArrayOf(.85f,1f,1.15f,1.3f)) {
                val config=Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)); fontScale=scale }
                val context=base.createConfigurationContext(config)
                val full=context.getString(R.string.system_island_mode_dnd)
                val short=context.getString(R.string.system_island_mode_dnd_short)
                for(gap in intArrayOf(0,40,64)) for(offset in floatArrayOf(-12f,0f,12f)) {
                    val shell=IslandActivityView(context).apply { powerSaveMotion=true }
                    val width=shell.toastWidth(full,180,440,gap,44,expanded=false,status=true,offset=offset)
                    assertNotNull("$language $scale $gap $offset",shell.statusHeight(full,width,gap,44,offset,short))
                    shell.bind("Enabled",full,null,false,false,false,0,false,status=true)
                    shell.geometry(width,44,gap,cameraOffset=offset)
                    shell.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(44,View.MeasureSpec.EXACTLY))
                    shell.layout(0,0,width,44)
                    val label=(shell.contentAt(1) as LinearLayout).getChildAt(0) as TextView
                    assertEquals(full,label.text.toString())
                    assertEquals(1,label.maxLines); assertEquals(1,label.layout.lineCount)
                    assertEquals(full.length,label.layout.getLineEnd(0)); assertEquals(0,label.layout.getEllipsisCount(0))
                    assertTrue(label.layout.getLineWidth(0)<=label.width)
                    assertTrue(label.layout.height<=label.height)
                    assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_LTR,label.textDirection)
                }
            }
    }

    @Test fun singleLineShrinksBeforeWrappingAndShortLabelKeepsFullDescription() {
        val context=org.robolectric.RuntimeEnvironment.getApplication()
        val shell=IslandActivityView(context).apply { powerSaveMotion=true }
        val full="Do Not Disturb"; val paint=android.text.TextPaint().apply { textSize=13f }
        val fit=IslandStatusLayout.fit(full,paint,(paint.measureText(full)*.92f).toInt(),13f,11f,40)!!
        assertEquals(1,fit.lines); assertTrue(fit.size<13f)
        val long="A very long localized mode name that cannot fit"
        assertNotNull(shell.statusHeight(long,180,40,40,shortLabel="DND"))
        shell.bind("Enabled",long,null,false,false,false,0,false,status=true)
        val label=(shell.contentAt(1) as LinearLayout).getChildAt(0) as TextView
        assertEquals("DND",label.text.toString()); assertTrue(shell.contentDescription.toString().contains(long))
        assertNull(IslandStatusLayout.fit("first\nsecond",paint,200,13f,11f,40))
    }
}
