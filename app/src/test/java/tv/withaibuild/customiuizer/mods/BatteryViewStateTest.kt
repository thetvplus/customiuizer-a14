package tv.withaibuild.customiuizer.mods

import android.app.Application
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.SystemUIBatteryHooks.BatteryMeterFields
import tv.withaibuild.customiuizer.mods.SystemUIBatteryHooks.BatteryStyle

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "xhdpi")
class BatteryViewStateTest {
    private val fields = BatteryMeterFields.resolve(BatteryView::class.java)
    private val view = BatteryView()

    @Test
    fun appliesCustomStyleAndRestoresNativeAppearance() {
        val digit = view.mBatteryTextDigitView
        val percent = view.mBatteryPercentView
        val mark = view.mBatteryPercentMarkView
        val nativeTypeface = digit.typeface

        update(style(swap = true, digitSize = 12f, markSize = 10f, bold = true, rightMargin = 4f,
            leftMargin = 6f, verticalOffset = 12, markOffset = 20))
        assertSame(percent, view.getChildAt(0))
        assertSame(mark, view.getChildAt(1))
        assertEquals(24f, digit.textSize, 0f)
        assertEquals(24f, percent.textSize, 0f)
        assertEquals(20f, mark.textSize, 0f)
        assertSame(Typeface.DEFAULT_BOLD, digit.typeface)
        assertSame(Typeface.DEFAULT_BOLD, percent.typeface)
        assertEquals(12, percent.paddingStart)
        assertEquals(4, percent.paddingTop)
        assertEquals(12, mark.paddingTop)
        assertEquals(8, mark.paddingEnd)

        update(style())
        assertSame(digit, view.getChildAt(0))
        assertSame(percent, view.getChildAt(1))
        assertSame(mark, view.getChildAt(2))
        assertEquals(15f, digit.textSize, 0f)
        assertEquals(15f, percent.textSize, 0f)
        assertEquals(15f, mark.textSize, 0f)
        assertSame(nativeTypeface, digit.typeface)
        assertSame(nativeTypeface, percent.typeface)
        assertEquals(4, percent.paddingStart)
        assertEquals(0, percent.paddingTop)
        assertEquals(2, mark.paddingTop)
        assertEquals(0, mark.paddingEnd)
    }

    @Test
    fun repeatedUpdatesDoNotReorderAndNativePropertyWritesAreReconciled() {
        val custom = style(swap = true, digitSize = 12f)
        update(custom)
        view.childMutations = 0

        update(custom)
        assertEquals(0, view.childMutations)

        view.mBatteryTextDigitView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 18f)
        update(custom)
        assertEquals(24f, view.mBatteryTextDigitView.textSize, 0f)
        assertEquals(0, view.childMutations)

        update(style())
        assertSame(view.mBatteryTextDigitView, view.getChildAt(0))
        assertEquals(15f, view.mBatteryTextDigitView.textSize, 0f)
    }

    @Test
    fun customRightMarginFollowsNativePercentageStyle() {
        update(style(rightMargin = 6f, battery4 = true))
        assertEquals(12, view.mBatteryPercentView.paddingEnd)
        assertEquals(0, view.mBatteryPercentMarkView.paddingEnd)

        update(style(rightMargin = 6f))
        assertEquals(0, view.mBatteryPercentView.paddingEnd)
        assertEquals(12, view.mBatteryPercentMarkView.paddingEnd)
    }

    @Test
    fun nativeStyleRemainsUntouchedAndSuppliesLatestBaseline() {
        update(style(battery4 = true))
        assertEquals(4, view.mBatteryPercentView.paddingStart)
        assertEquals(2, view.mBatteryPercentMarkView.paddingTop)

        view.mBatteryTextDigitView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 18f)
        update(style(digitSize = 12f))
        update(style())
        assertEquals(18f, view.mBatteryTextDigitView.textSize, 0f)
    }

    @Test
    fun replacingNativeChildRebasesOnItsNewAppearance() {
        val custom = style(markSize = 10f)
        update(custom)
        val replacement = TextView(view.context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 17f)
        }
        view.removeView(view.mBatteryPercentMarkView)
        view.mBatteryPercentMarkView = replacement
        view.addView(replacement)

        update(custom)
        assertEquals(20f, replacement.textSize, 0f)
        update(style())
        assertEquals(17f, replacement.textSize, 0f)
    }

    @Test
    fun keepsDeclaredHostFieldsForSubclassInstances() {
        val subclass = object : BatteryView() {}
        SystemUIBatteryHooks.reconcileBatteryView(subclass, style(digitSize = 12f), fields)
        assertEquals(24f, subclass.mBatteryTextDigitView.textSize, 0f)
    }

    private fun update(style: BatteryStyle) = SystemUIBatteryHooks.reconcileBatteryView(view, style, fields)

    private fun style(
        swap: Boolean = false,
        digitSize: Float = 7.5f,
        markSize: Float = 7.5f,
        bold: Boolean = false,
        leftMargin: Float = 0f,
        rightMargin: Float = 0f,
        verticalOffset: Int = 8,
        markOffset: Int = 17,
        battery4: Boolean = false,
    ) = BatteryStyle(swap, digitSize, markSize, bold, leftMargin, rightMargin, verticalOffset, markOffset, battery4)

    private open class BatteryView : LinearLayout(RuntimeEnvironment.getApplication()) {
        val mBatteryTextDigitView = textView()
        val mBatteryPercentView = textView()
        var mBatteryPercentMarkView = textView()
        var childMutations = 0

        init {
            mBatteryPercentView.setPaddingRelative(4, 0, 0, 0)
            mBatteryPercentMarkView.setPaddingRelative(0, 2, 0, 0)
            addView(mBatteryTextDigitView)
            addView(mBatteryPercentView)
            addView(mBatteryPercentMarkView)
        }

        override fun addView(child: View?, index: Int) {
            childMutations++
            super.addView(child, index)
        }

        override fun removeView(view: View?) {
            childMutations++
            super.removeView(view)
        }

        private fun textView() = TextView(context).apply {
            typeface = Typeface.DEFAULT
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 15f)
        }
    }
}
