package tv.withaibuild.customiuizer.mods

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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandStatusLayoutTest {
    @Test fun oneCompleteStatusLineKeepsFullSemanticsAccessibleAndRestoresMediaLabels() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val shell = IslandActivityView(activity).apply { simplified = true }
        activity.setContentView(shell, FrameLayout.LayoutParams(240, 44))
        val mode = "Enabled"; val action = "Do not disturb mode"
        val ordinaryWidth = shell.textWidth("A saved image")
        val ordinaryFit = shell.fitsToast("A saved image", 200, 40)
        val height = shell.statusHeight(mode, 240, 40, 40)!!
        shell.bind(mode, action, null, false, false, false, 0, false, status = true)
        shell.geometry(240, height, 40)
        shell.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        shell.layout(0, 0, 240, height)
        val label = (shell.getChildAt(1) as LinearLayout).getChildAt(0) as TextView
        assertEquals(mode, label.text.toString())
        assertTrue(shell.contentDescription.toString().contains(action))
        assertNull(label.ellipsize)
        assertEquals(label.text.length, label.layout.getLineEnd(label.layout.lineCount - 1))
        assertEquals(1, label.layout.lineCount)
        assertTrue(label.height >= label.layout.height)
        for (line in 0 until label.layout.lineCount) assertEquals(0, label.layout.getEllipsisCount(line))
        val image = android.graphics.Bitmap.createBitmap(240, height, android.graphics.Bitmap.Config.ARGB_8888)
        shell.draw(android.graphics.Canvas(image))
        var visibleTextPixels = 0
        for (y in 0 until height) for (x in 140 until 224) {
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
}
