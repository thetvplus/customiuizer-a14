package tv.withaibuild.customiuizer.mods

import android.content.res.Resources
import android.graphics.Typeface
import android.text.TextPaint
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryViewStateTest {

    @Test
    fun defaultUntouchedAfterRestore() {
        val view = createBatteryView()
        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view)!!

        val custom = customStyle()
        SystemUIBatteryHooks.applyBatteryStyle(view, baseline, custom)
        assertFalse(SystemUIBatteryHooks.matchesBaseline(view, baseline))

        SystemUIBatteryHooks.restoreBatteryBaseline(view, baseline)
        assertTrue("default style must restore original baseline", SystemUIBatteryHooks.matchesBaseline(view, baseline))
    }

    @Test
    fun fontCustomToDefault() {
        val view = createBatteryView()
        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view)!!

        val custom = SystemUIBatteryHooks.BatteryStyle(
            swap = false,
            fontSizeDp = 12.0f,
            markFontSizeDp = 7.5f,
            bold = false,
            leftMarginDp = 0f,
            rightMarginDp = 0f,
            verticalOffset = 8,
            markVerticalOffset = 17,
            battery4 = false
        )
        SystemUIBatteryHooks.applyBatteryStyle(view, baseline, custom)

        val digitSize = view.mBatteryTextDigitView.textSize
        val percentSize = view.mBatteryPercentView.textSize
        assertNotEquals(baseline.digitTextSize, digitSize)
        assertNotEquals(baseline.percentTextSize, percentSize)

        SystemUIBatteryHooks.restoreBatteryBaseline(view, baseline)

        assertEquals(baseline.digitTextSize, view.mBatteryTextDigitView.textSize, 0.001f)
        assertEquals(baseline.percentTextSize, view.mBatteryPercentView.textSize, 0.001f)
    }

    @Test
    fun boldCustomToDefault() {
        val view = createBatteryView()
        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view)!!

        val custom = SystemUIBatteryHooks.BatteryStyle(
            swap = false,
            fontSizeDp = 7.5f,
            markFontSizeDp = 7.5f,
            bold = true,
            leftMarginDp = 0f,
            rightMarginDp = 0f,
            verticalOffset = 8,
            markVerticalOffset = 17,
            battery4 = false
        )
        SystemUIBatteryHooks.applyBatteryStyle(view, baseline, custom)

        assertEquals(Typeface.DEFAULT_BOLD, view.mBatteryTextDigitView.typeface)
        assertEquals(Typeface.DEFAULT_BOLD, view.mBatteryPercentView.typeface)

        SystemUIBatteryHooks.restoreBatteryBaseline(view, baseline)

        assertEquals(baseline.digitTypeface, view.mBatteryTextDigitView.typeface)
        assertEquals(baseline.percentTypeface, view.mBatteryPercentView.typeface)
    }

    @Test
    fun paddingCustomToDefault() {
        val view = createBatteryView()
        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view)!!

        val custom = SystemUIBatteryHooks.BatteryStyle(
            swap = false,
            fontSizeDp = 7.5f,
            markFontSizeDp = 7.5f,
            bold = false,
            leftMarginDp = 8f,
            rightMarginDp = 4f,
            verticalOffset = 12,
            markVerticalOffset = 20,
            battery4 = false
        )
        SystemUIBatteryHooks.applyBatteryStyle(view, baseline, custom)

        val percentPad = capturedPadding(view.mBatteryPercentView)
        assertNotEquals(baseline.percentPadding, percentPad)

        SystemUIBatteryHooks.restoreBatteryBaseline(view, baseline)

        assertEquals(baseline.percentPadding, capturedPadding(view.mBatteryPercentView))
        assertEquals(baseline.markPadding, capturedPadding(view.mBatteryPercentMarkView))
    }

    @Test
    fun markPaddingCustomToDefault() {
        val view = createBatteryView()
        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view)!!

        val custom = SystemUIBatteryHooks.BatteryStyle(
            swap = false,
            fontSizeDp = 7.5f,
            markFontSizeDp = 13.0f,
            bold = false,
            leftMarginDp = 0f,
            rightMarginDp = 0f,
            verticalOffset = 8,
            markVerticalOffset = 20,
            battery4 = false
        )
        SystemUIBatteryHooks.applyBatteryStyle(view, baseline, custom)

        assertNotEquals(baseline.markTextSize, view.mBatteryPercentMarkView.textSize)

        val markPad = capturedPadding(view.mBatteryPercentMarkView)
        assertNotEquals(baseline.markPadding, markPad)

        SystemUIBatteryHooks.restoreBatteryBaseline(view, baseline)

        assertEquals(baseline.markPadding, capturedPadding(view.mBatteryPercentMarkView))
    }

    @Test
    fun swapCustomToDefault() {
        val view = createBatteryView()
        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view)!!

        assertTrue(baseline.percentIndex != 0 || baseline.markIndex != 1)

        val custom = SystemUIBatteryHooks.BatteryStyle(
            swap = true,
            fontSizeDp = 7.5f,
            markFontSizeDp = 7.5f,
            bold = false,
            leftMarginDp = 0f,
            rightMarginDp = 0f,
            verticalOffset = 8,
            markVerticalOffset = 17,
            battery4 = false
        )
        SystemUIBatteryHooks.applyBatteryStyle(view, baseline, custom)

        assertEquals(0, view.indexOfChild(view.mBatteryPercentView))
        assertEquals(1, view.indexOfChild(view.mBatteryPercentMarkView))

        SystemUIBatteryHooks.restoreBatteryBaseline(view, baseline)

        assertEquals(baseline.percentIndex, view.indexOfChild(view.mBatteryPercentView))
        assertEquals(baseline.markIndex, view.indexOfChild(view.mBatteryPercentMarkView))
    }

    @Test
    fun sameCustomStyleDoesNotRearrangeChildren() {
        val view = createBatteryView()
        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view)!!

        val custom = customStyle()
        SystemUIBatteryHooks.applyBatteryStyle(view, baseline, custom)
        val afterFirst = view.childCount

        view.resetMutationCount()
        SystemUIBatteryHooks.applyBatteryStyle(view, baseline, custom)

        // No views should have been added or removed on re-application.
        assertEquals(afterFirst, view.childCount)
        assertEquals(0, view.mutationCount)
        assertEquals(0, view.indexOfChild(view.mBatteryPercentView))
        assertEquals(1, view.indexOfChild(view.mBatteryPercentMarkView))
    }

    @Test
    fun battery4WithDefaultMarginsPreservesOemPadding() {
        val view = createBatteryView()
        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view)!!

        val custom = SystemUIBatteryHooks.BatteryStyle(
            swap = false,
            fontSizeDp = 7.5f,
            markFontSizeDp = 7.5f,
            bold = false,
            leftMarginDp = 0f,
            rightMarginDp = 0f,
            verticalOffset = 8,
            markVerticalOffset = 17,
            battery4 = true
        )
        SystemUIBatteryHooks.applyBatteryStyle(view, baseline, custom)

        assertEquals(baseline.percentPadding, capturedPadding(view.mBatteryPercentView))
        assertEquals(baseline.markPadding, capturedPadding(view.mBatteryPercentMarkView))
    }

    @Test
    fun battery4CustomRightMarginGoesToPercent() {
        val view = createBatteryView()
        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view)!!

        val custom = SystemUIBatteryHooks.BatteryStyle(
            swap = false,
            fontSizeDp = 7.5f,
            markFontSizeDp = 7.5f,
            bold = false,
            leftMarginDp = 0f,
            rightMarginDp = 6f,
            verticalOffset = 8,
            markVerticalOffset = 17,
            battery4 = true
        )
        SystemUIBatteryHooks.applyBatteryStyle(view, baseline, custom)

        val metrics = view.resources.displayMetrics
        val rightMarginPx = (6f * metrics.density).toInt()

        val percentPad = capturedPadding(view.mBatteryPercentView)
        val markPad = capturedPadding(view.mBatteryPercentMarkView)

        assertEquals("percent view should carry the right margin", rightMarginPx, percentPad.end)
        assertEquals("mark view should not carry the right margin", 0, markPad.end)
    }

    @Test
    fun nonBattery4CustomRightMarginGoesToMark() {
        val view = createBatteryView()
        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view)!!

        val custom = SystemUIBatteryHooks.BatteryStyle(
            swap = false,
            fontSizeDp = 7.5f,
            markFontSizeDp = 7.5f,
            bold = false,
            leftMarginDp = 0f,
            rightMarginDp = 6f,
            verticalOffset = 8,
            markVerticalOffset = 17,
            battery4 = false
        )
        SystemUIBatteryHooks.applyBatteryStyle(view, baseline, custom)

        val metrics = view.resources.displayMetrics
        val rightMarginPx = (6f * metrics.density).toInt()

        val percentPad = capturedPadding(view.mBatteryPercentView)
        val markPad = capturedPadding(view.mBatteryPercentMarkView)

        assertEquals("percent view should not carry the right margin", 0, percentPad.end)
        assertEquals("mark view should carry the right margin", rightMarginPx, markPad.end)
    }

    @Test
    fun moduleReorderDoesNotInvalidateBaseline() {
        val view = createBatteryView()
        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view)!!
        val state = SystemUIBatteryHooks.BatteryViewState()
        state.baseline = baseline

        val swapStyle = customStyle()
        SystemUIBatteryHooks.reconcileBatteryView(view, swapStyle, state)

        // After swap: [percent, mark, digit]
        assertEquals(0, view.indexOfChild(view.mBatteryPercentView))
        assertEquals(1, view.indexOfChild(view.mBatteryPercentMarkView))
        assertEquals(2, view.indexOfChild(view.mBatteryTextDigitView))

        // The same children are still present; only order changed.
        assertFalse("module reorder must not be treated as child replacement", SystemUIBatteryHooks.childIdentitiesChanged(view, baseline.childIds))
        assertSame("baseline must not be recaptured on module reorder", baseline, state.baseline)
        assertTrue("swapped state must still match target style", SystemUIBatteryHooks.matchesTarget(view, baseline, swapStyle))

        val defaultStyle = defaultStyle()
        SystemUIBatteryHooks.reconcileBatteryView(view, defaultStyle, state)

        // Restore brings back the original OEM order.
        assertEquals(baseline.percentIndex, view.indexOfChild(view.mBatteryPercentView))
        assertEquals(baseline.markIndex, view.indexOfChild(view.mBatteryPercentMarkView))
        assertSame("baseline must stay the same after restore", baseline, state.baseline)
    }

    @Test
    fun swapAcrossRepeatedUpdateKeepsOriginalBaseline() {
        val view = createBatteryView()
        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view)!!
        val state = SystemUIBatteryHooks.BatteryViewState()
        state.baseline = baseline

        val swapStyle = customStyle()

        // First updateAll
        SystemUIBatteryHooks.reconcileBatteryView(view, swapStyle, state)
        assertSame(baseline, state.baseline)

        view.resetMutationCount()

        // Second updateAll with the same style
        SystemUIBatteryHooks.reconcileBatteryView(view, swapStyle, state)
        assertSame("repeated update must not recapture baseline", baseline, state.baseline)
        assertTrue("repeated update must keep matching target", SystemUIBatteryHooks.matchesTarget(view, baseline, swapStyle))
        assertEquals(0, view.mutationCount)

        // Back to default
        SystemUIBatteryHooks.reconcileBatteryView(view, defaultStyle(), state)
        assertEquals(baseline.percentIndex, view.indexOfChild(view.mBatteryPercentView))
        assertEquals(baseline.markIndex, view.indexOfChild(view.mBatteryPercentMarkView))
    }

    @Test
    fun childReplacementRecapturesBaseline() {
        val view = createBatteryView()
        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view)!!

        val custom = customStyle()
        SystemUIBatteryHooks.applyBatteryStyle(view, baseline, custom)

        // Simulate ROM replacing the mark view with a new instance.
        val newMark = FakeTextView()
        newMark.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseline.markTextSize)
        view.removeView(view.mBatteryPercentMarkView)
        view.mBatteryPercentMarkView = newMark
        view.addView(newMark)

        val changed = SystemUIBatteryHooks.childIdentitiesChanged(view, baseline.childIds)
        assertTrue("child identity change must be detected", changed)

        val newBaseline = SystemUIBatteryHooks.captureBatteryBaseline(view)!!
        assertEquals(view.indexOfChild(newMark), newBaseline.markIndex)
    }

    private fun createBatteryView(): FakeBatteryView {
        val view = FakeBatteryView()
        view.mBatteryTextDigitView = FakeTextView()
        view.mBatteryPercentView = FakeTextView()
        view.mBatteryPercentMarkView = FakeTextView()

        // Default text sizes in px from 7.5dp (density 2.0).
        view.mBatteryTextDigitView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 7.5f)
        view.mBatteryPercentView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 7.5f)
        view.mBatteryPercentMarkView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 7.5f)

        view.mBatteryPercentView.setPaddingRelative(4, 0, 0, 0)
        view.mBatteryPercentMarkView.setPaddingRelative(0, 2, 0, 0)

        // Add views in original order: [digit, percent, mark] with percent at index 1, mark at index 2.
        view.addView(view.mBatteryTextDigitView)
        view.addView(view.mBatteryPercentView)
        view.addView(view.mBatteryPercentMarkView)

        return view
    }

    private fun customStyle(): SystemUIBatteryHooks.BatteryStyle = SystemUIBatteryHooks.BatteryStyle(
        swap = true,
        fontSizeDp = 12.0f,
        markFontSizeDp = 10.0f,
        bold = true,
        leftMarginDp = 6f,
        rightMarginDp = 4f,
        verticalOffset = 12,
        markVerticalOffset = 20,
        battery4 = false
    )

    private fun defaultStyle(): SystemUIBatteryHooks.BatteryStyle = SystemUIBatteryHooks.BatteryStyle(
        swap = false,
        fontSizeDp = 7.5f,
        markFontSizeDp = 7.5f,
        bold = false,
        leftMarginDp = 0f,
        rightMarginDp = 0f,
        verticalOffset = 8,
        markVerticalOffset = 17,
        battery4 = false
    )

    private fun capturedPadding(view: TextView): SystemUIBatteryHooks.Padding = SystemUIBatteryHooks.Padding(
        view.paddingStart,
        view.paddingTop,
        view.paddingEnd,
        view.paddingBottom
    )

    @Suppress("DEPRECATION")
    private class FakeResources(density: Float = 2.0f) : Resources(null, DisplayMetrics().apply { this.density = density }, android.content.res.Configuration()) {
        val metrics = DisplayMetrics().apply { this.density = density }
        override fun getDisplayMetrics(): DisplayMetrics = metrics
    }

    private class FakeTextView(density: Float = 2.0f) : TextView(null) {
        private val fakePaint = TextPaint()
        private var storedTypeface: Typeface? = null
        private var storedTextSize: Float = 0f
        private var storedPaddingStart: Int = 0
        private var storedPaddingTop: Int = 0
        private var storedPaddingEnd: Int = 0
        private var storedPaddingBottom: Int = 0
        private var storedPaddingLeft: Int = 0
        private var storedPaddingRight: Int = 0

        val fakeResources = FakeResources(density)

        override fun getResources(): Resources = fakeResources

        override fun getPaint(): TextPaint = fakePaint

        override fun getTypeface(): Typeface? = storedTypeface

        override fun getTextSize(): Float = storedTextSize

        override fun getPaddingStart(): Int = if (storedPaddingStart != 0) storedPaddingStart else storedPaddingLeft

        override fun getPaddingTop(): Int = storedPaddingTop

        override fun getPaddingEnd(): Int = if (storedPaddingEnd != 0) storedPaddingEnd else storedPaddingRight

        override fun getPaddingBottom(): Int = storedPaddingBottom

        override fun getPaddingLeft(): Int = storedPaddingLeft

        override fun getPaddingRight(): Int = storedPaddingRight

        override fun setTypeface(tf: Typeface?) {
            storedTypeface = tf
        }

        override fun setTextSize(unit: Int, size: Float) {
            storedTextSize = when (unit) {
                TypedValue.COMPLEX_UNIT_DIP -> size * fakeResources.displayMetrics.density
                else -> size
            }
        }

        override fun setPaddingRelative(start: Int, top: Int, end: Int, bottom: Int) {
            storedPaddingStart = start
            storedPaddingTop = top
            storedPaddingEnd = end
            storedPaddingBottom = bottom
        }

        override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
            storedPaddingLeft = left
            storedPaddingTop = top
            storedPaddingRight = right
            storedPaddingBottom = bottom
        }
    }

    private class FakeBatteryView : LinearLayout(null) {
        lateinit var mBatteryTextDigitView: TextView
        lateinit var mBatteryPercentView: TextView
        lateinit var mBatteryPercentMarkView: TextView

        private val backingChildren = mutableListOf<View>()
        var mutationCount = 0
            private set

        override fun getResources(): Resources = FakeResources()
        override fun getChildCount(): Int = backingChildren.size
        override fun getChildAt(index: Int): View = backingChildren[index]
        override fun indexOfChild(child: View?): Int = if (child == null) -1 else backingChildren.indexOf(child)

        override fun addView(child: View?) {
            if (child == null) return
            addView(child, -1)
        }

        override fun addView(child: View?, index: Int) {
            if (child == null) return
            mutationCount++
            if (index < 0 || index > backingChildren.size) {
                backingChildren.add(child)
            } else {
                backingChildren.add(index, child)
            }
        }

        override fun removeView(child: View?) {
            if (child == null) return
            mutationCount++
            backingChildren.remove(child)
        }

        fun resetMutationCount() {
            mutationCount = 0
        }
    }
}
