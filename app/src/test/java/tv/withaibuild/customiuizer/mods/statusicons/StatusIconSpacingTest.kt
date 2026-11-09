package tv.withaibuild.customiuizer.mods.statusicons

import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import android.widget.FrameLayout
import android.widget.LinearLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "mdpi")
class StatusIconSpacingTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun icon() = View(context).apply { setPadding(2, 1, 3, 1) }

    @Test fun adjacentIconsUseOneGapAndRepeatedUpdatesDoNotAccumulate() {
        val row = LinearLayout(context)
        val signal = icon(); val battery = icon()
        row.addView(signal); row.addView(battery)
        try {
            repeat(20) {
                StatusIconSpacing.update(signal, 2, 8)
                StatusIconSpacing.update(battery, 12, 4)
                assertEquals(3 + 2 + 6, signal.paddingRight + battery.paddingLeft)
                assertEquals(3, signal.paddingLeft)
                assertEquals(5, battery.paddingRight)
            }
            StatusIconSpacing.restore(battery)
            assertEquals(7, signal.paddingRight)
            assertEquals(2, battery.paddingLeft)
        } finally { StatusIconSpacing.restore(signal); StatusIconSpacing.restore(battery) }
        assertEquals(2, signal.paddingLeft); assertEquals(3, signal.paddingRight)
    }

    @Test fun interveningWifiKeepsBothIndependentEdgesAndGoneWifiDoesNot() {
        val row = NativeRow()
        val signal = icon(); val wifi = icon(); val battery = icon()
        row.addView(signal); row.addView(wifi); row.addView(battery)
        try {
            StatusIconSpacing.update(signal, 0, 8); StatusIconSpacing.update(battery, 12, 0)
            assertEquals(7, signal.paddingRight); assertEquals(8, battery.paddingLeft)
            assertEquals(2, wifi.paddingLeft); assertEquals(3, wifi.paddingRight)
            wifi.visibility = View.GONE
            row.traverse()
            assertEquals(3, signal.paddingRight)
        } finally { StatusIconSpacing.restore(signal); StatusIconSpacing.restore(battery) }
    }

    @Test fun nativeWrappersAndRtlPreservePhysicalLeftAndRightSemantics() {
        val row = LinearLayout(context).apply { layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val battery = icon(); val signal = icon(); val group = FrameLayout(context)
        group.addView(signal); row.addView(battery); row.addView(group)
        try {
            StatusIconSpacing.update(signal, 0, 8); StatusIconSpacing.update(battery, 12, 0)
            assertEquals(3 + 2 + 6, signal.paddingRight + battery.paddingLeft)
        } finally { StatusIconSpacing.restore(signal); StatusIconSpacing.restore(battery) }
    }

    @Test fun nativePaddingReplacementIsTheNewBaselineAndDetachReleasesOnlyOwnedValues() {
        val view = icon()
        val owner = StatusIconSpacing(view)
        owner.update(4, 8)
        view.setPadding(9, 5, 10, 6)
        owner.update(4, 8)
        assertEquals(11, view.paddingLeft); assertEquals(14, view.paddingRight)
        owner.onViewDetachedFromWindow(view)
        assertEquals(9, view.paddingLeft); assertEquals(10, view.paddingRight)
        assertEquals(5, view.paddingTop); assertEquals(6, view.paddingBottom)
        StatusIconSpacing.update(view, 0, 0)
        assertEquals(9, view.paddingLeft)
    }
    /** External native measurement model: the production boundary runs before
     * the native common parent consumes either battery or status-icons widths. */
    private class NativeRow : LinearLayout(RuntimeEnvironment.getApplication()) {
        private val boundary = StatusIconSpacing.measureHook()
        var nativeCalls = 0
        var argumentLists = 0
        var failure: Throwable? = null
        fun nativeMeasure(width: Int, height: Int) {
            nativeCalls++
            failure?.let { throw it }
            super.onMeasure(width, height)
        }
        override fun onMeasure(width: Int, height: Int) {
            boundary.intercept(object : XposedInterface.Chain {
                override fun getExecutable() = NativeRow::class.java.getDeclaredMethod("onMeasure", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                override fun getThisObject(): Any = this@NativeRow
                override fun getArg(index: Int): Any = if (index == 0) width else height
                override fun getArgs(): List<Any?> { argumentLists++; return listOf(width, height) }
                override fun proceed(): Any? { nativeMeasure(width, height); return null }
                override fun proceed(args: Array<Any?>): Any? = throw AssertionError("measurement arguments changed")
                override fun proceedWith(receiver: Any): Any? = throw AssertionError("measurement receiver changed")
                override fun proceedWith(receiver: Any, args: Array<Any?>): Any? = throw AssertionError("measurement owner changed")
            })
        }
        fun traverse() {
            measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(24, View.MeasureSpec.EXACTLY))
            layout(0, 0, measuredWidth, measuredHeight)
        }
    }
    private class MeasuredIcon : View(RuntimeEnvironment.getApplication()) {
        var paddingWrites = 0
        override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
            paddingWrites++; super.setPadding(left, top, right, bottom)
        }
        override fun onMeasure(width: Int, height: Int) = setMeasuredDimension(10 + paddingLeft + paddingRight, 20)
    }

    @Test fun wifiVisibilityAloneReconcilesNestedBatteryAndSignalBeforeNativeMeasurement() {
        for (rtl in listOf(false, true)) {
            val root = NativeRow().apply { layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR }
            val icons = LinearLayout(context).apply { layoutDirection = root.layoutDirection }
            val signal = MeasuredIcon().apply { setPadding(2, 1, 3, 1) }
            val wifi = icon()
            val battery = MeasuredIcon().apply { setPadding(2, 1, 3, 1) }
            if (rtl) { icons.addView(wifi); icons.addView(signal); root.addView(battery); root.addView(icons) }
            else { icons.addView(signal); icons.addView(wifi); root.addView(icons); root.addView(battery) }
            try {
                StatusIconSpacing.update(signal, 0, 8); StatusIconSpacing.update(battery, 12, 0)
                root.traverse()
                assertEquals(7, signal.paddingRight)
                wifi.visibility = View.GONE
                root.traverse() // No signal or battery update is allowed to hide the bug.
                assertEquals(3, signal.paddingRight)
                assertEquals(15, signal.measuredWidth)
                assertEquals(8, battery.paddingLeft)
                wifi.visibility = View.VISIBLE
                root.traverse()
                assertEquals(7, signal.paddingRight)
                assertEquals(19, signal.measuredWidth)
                wifi.visibility = View.GONE; root.traverse()
                battery.visibility = View.GONE; root.traverse()
                assertEquals(7, signal.paddingRight)
                battery.visibility = View.VISIBLE; root.traverse()
                assertEquals(3, signal.paddingRight)
                val writes = signal.paddingWrites + battery.paddingWrites
                repeat(20) { root.forceLayout(); root.traverse() }
                assertEquals("Stable native traversals do not rewrite padding", writes, signal.paddingWrites + battery.paddingWrites)
                assertEquals(0, root.argumentLists)
            } finally { StatusIconSpacing.restore(signal); StatusIconSpacing.restore(battery) }
        }
    }

    @Test fun theTwoFeatureConsumersShareOneNativeMeasurementInstallation() {
        val owner = NativeRow::class.java
        val method = owner.getDeclaredMethod("onMeasure", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        var installs = 0
        try {
            repeat(2) { index ->
                val result = StatusIconSpacing.install(method) { _, _ ->
                    installs++
                    object : CustomMethodUnhooker { override fun unhook() = Unit }
                }
                assertEquals(if (index == 0) FeatureInstallResult.INSTALLED else FeatureInstallResult.ALREADY_INSTALLED, result)
            }
            assertEquals(1, installs)
        } finally { XposedHelpers.removeAdditionalStaticField(owner, "a14.status.spacing.measure") }
    }

    @Test fun theMeasurementBoundaryKeepsNativeFailureIdentityAndNeverRetries() {
        val root = NativeRow()
        val failure = IllegalStateException("native measure")
        root.failure = failure
        assertSame(failure, runCatching { root.traverse() }.exceptionOrNull())
        assertEquals(1, root.nativeCalls); assertEquals(0, root.argumentLists)
    }
}
