package tv.withaibuild.customiuizer.mods.duostatusbar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DuoStatusBarViewTest {
    private fun view(padding: Int, autoSize: Boolean = true, offset: Int = 8): DuoStatusBarView =
        DuoStatusBarView(RuntimeEnvironment.getApplication(), DuoConfig(true, 28,
            autoSize = autoSize, horizontalPadding = padding, verticalOffset = offset),
            { throw AssertionError(it) }, {})

    private fun layout(view: DuoStatusBarView, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(view.layoutWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun pixels(view: DuoStatusBarView, rowHeight: Int = view.height): IntArray {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            val width = view.width - view.paddingLeft - view.paddingRight
            return IntArray(width * rowHeight).also {
                bitmap.getPixels(it, 0, width, view.paddingLeft, (view.height - rowHeight) / 2,
                    width, rowHeight)
            }
        } finally { bitmap.recycle() }
    }

    @Test fun spacingChangesOnlyTheReservedWidthInBothSizingModesAndLayoutDirections() {
        for (automatic in listOf(false, true)) for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
            val plain = view(0, automatic)
            val padded = view(3, automatic)
            plain.layoutDirection = direction
            padded.layoutDirection = direction
            assertEquals(plain.iconSizePx, padded.iconSizePx)
            assertEquals(plain.iconSizePx + 6, padded.layoutWidthPx)
            assertEquals(3, padded.paddingLeft)
            assertEquals(padded.paddingLeft, padded.paddingRight)
            for (height in intArrayOf(36, 60, 100)) {
                layout(plain, height)
                layout(padded, height)
                val state = DuoNetworkState()
                state.subscriptions(10, -1, true)
                state.wifi(true, true, 4)
                for (level in 0..5) {
                    state.mobile(0, 10, level, true, 5)
                    plain.render(50, false, false, -1, state.snapshot, 0)
                    padded.render(50, false, false, -1, state.snapshot, 0)
                    val expected = pixels(plain)
                    assertTrue("the native canvas must actually draw", expected.any { it != 0 })
                    assertArrayEquals(expected, pixels(padded))
                    assertEquals(plain.requiredHeight(height), padded.requiredHeight(height))
                }
            }
        }
    }

    @Test fun controlCenterAndTransitionRowsKeepTheStatusBarGeometryWithSpacingAndVerticalAdjustment() {
        val source = view(3, offset = 12)
        val header = view(3, offset = 12)
        layout(source, 36)
        layout(header, 100)
        header.shareHeight(36)
        source.render(50, false, false, -1, 0, 0)
        header.render(50, false, false, -1, 0, 0)
        assertArrayEquals(pixels(source), pixels(header, 36))
    }

    @Test fun densityChangesUpdatePaddingAndHostWidthTogether() {
        val view = view(3)
        view.layoutParams = ViewGroup.LayoutParams(view.layoutWidthPx, 100)
        val oldSize = view.iconSizePx
        RuntimeEnvironment.setQualifiers("xxxhdpi")
        view.dispatchConfigurationChanged(view.resources.configuration)
        assertEquals(6, view.paddingLeft)
        assertEquals(view.paddingLeft, view.paddingRight)
        assertTrue(view.iconSizePx > oldSize)
        assertEquals(view.iconSizePx + 12, view.layoutParams.width)
    }
}
