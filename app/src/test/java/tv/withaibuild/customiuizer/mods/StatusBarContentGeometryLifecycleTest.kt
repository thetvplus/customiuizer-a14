package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.statusbarheight.StatusBarHeightArchitectureCH1Test.FakeChain
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "mdpi")
class StatusBarContentGeometryLifecycleTest {
    private lateinit var activity: Activity
    private lateinit var window: TestStatusBarWindowView
    private lateinit var bar: CountedLayout
    private lateinit var contents: CountedLayout

    @Before fun setup() {
        MainModule.mPrefs.clear()
        activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        window = TestStatusBarWindowView(activity)
        bar = CountedLayout(activity)
        contents = CountedLayout(activity)
        bar.addView(contents, FrameLayout.LayoutParams(-1, 80, Gravity.TOP or Gravity.START))
        window.addView(bar, FrameLayout.LayoutParams(-1, 80))
        activity.setContentView(window)
        layoutWindow(129)
    }

    @After fun cleanup() {
        activity.setContentView(FrameLayout(activity))
        activity.finish()
        MainModule.mPrefs.clear()
    }

    private fun layoutWindow(height: Int, width: Int = 400, target: FrameLayout = window) {
        // This fixture drives a traversal below ViewRootImpl. Model its requested
        // second measure pass when a parent layout callback changes a child's LP.
        target.forceLayout()
        target.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        target.layout(0, 0, width, height)
    }

    private fun settle(height: Int = 129, target: FrameLayout = window) {
        repeat(4) { layoutWindow(height, target = target) }
    }

    private fun offset(raw: Int = 30) {
        MainModule.mPrefs.put("system_statusbar_content_vertical_offset", raw)
    }

    private fun bindAndSettle() {
        StatusBarContentGeometryHooks.bind(bar)
        settle()
    }

    private fun apply() = StatusBarContentGeometryHooks.applyToView(bar)

    @Test fun parentHeightChangeRefreshesEvenWhenChildBoundsHaveNotChanged() {
        offset()
        bindAndSettle()
        assertEquals(129, bar.height)
        layoutWindow(60)
        assertEquals("parent event must correct LP before any child-size event", 60, bar.layoutParams.height)
        settle(60)
        assertEquals(60, bar.height)
        assertEquals(60, bar.layoutParams.height)
        assertTrue(contents.height <= 60)
        assertTrue(contents.top + contents.translationY >= -.5f)
        assertTrue(contents.bottom + contents.translationY <= 60.5f)
    }

    @Test fun latestNativeOwnerHeightSurvivesReturningToDefaultOffset() {
        offset()
        bindAndSettle()
        bar.layoutParams = bar.layoutParams.apply { height = 96 }
        settle()
        offset(20)
        apply()
        settle()
        assertEquals(96, bar.layoutParams.height)
    }

    @Test fun returningToDefaultWithoutInterveningLayoutKeepsExternalOwnerValue() {
        offset()
        bindAndSettle()
        bar.layoutParams = bar.layoutParams.apply { height = 96 }
        offset(20)
        apply()
        assertEquals(96, bar.layoutParams.height)
    }

    @Test fun zeroOffsetNeverClaimsNativeTranslation() {
        contents.translationY = 7f
        bindAndSettle()
        assertEquals(7f, contents.translationY, .001f)
    }

    @Test fun positiveNegativeOffsetsUseNativeTranslationWithoutAccumulating() {
        contents.translationY = 7f
        offset()
        bindAndSettle()
        assertEquals(12f, contents.translationY, .001f)
        offset(10)
        apply()
        assertEquals(2f, contents.translationY, .001f)
        offset(20)
        apply()
        assertEquals(7f, contents.translationY, .001f)
        assertEquals(80, contents.layoutParams.height)
        assertEquals(Gravity.TOP or Gravity.START,
            (contents.layoutParams as FrameLayout.LayoutParams).gravity)
    }

    @Test fun nativeTranslationWrittenWhileEnabledIsNotRolledBackOnDisable() {
        offset()
        bindAndSettle()
        contents.translationY = 11f
        offset(20)
        apply()
        assertEquals(11f, contents.translationY, .001f)
    }

    @Test fun interleavedNativeTranslationBecomesTheNextBaseline() {
        contents.translationY = 7f
        offset()
        bindAndSettle()
        contents.translationY = 11f
        apply()
        assertEquals(16f, contents.translationY, .001f)
        offset(20)
        apply()
        assertEquals(11f, contents.translationY, .001f)
    }

    @Test fun nativeContentsHeightAndGravityAreNotRestoredToInflationValues() {
        offset()
        bindAndSettle()
        contents.layoutParams = FrameLayout.LayoutParams(-1, 64, Gravity.BOTTOM or Gravity.END)
        offset(20)
        apply()
        assertEquals(64, contents.layoutParams.height)
        assertEquals(Gravity.BOTTOM or Gravity.END,
            (contents.layoutParams as FrameLayout.LayoutParams).gravity)
    }

    @Test fun repeatedBindAndUnchangedLayoutHaveNoAdditionalWritesOrListeners() {
        offset()
        bindAndSettle()
        val barWrites = bar.parameterWrites
        val contentsWrites = contents.parameterWrites
        repeat(12) { StatusBarContentGeometryHooks.bind(bar); settle() }
        assertEquals(barWrites, bar.parameterWrites)
        assertEquals(contentsWrites, contents.parameterWrites)
        assertEquals(1, bar.layoutListeners.size)
        assertEquals(1, bar.attachListeners.size)
        assertEquals(1, window.layoutListeners.size)
    }

    @Test fun parentShrinkAndRecoveryAreBoundedAndIdempotent() {
        offset()
        bindAndSettle()
        repeat(12) {
            settle(60)
            assertEquals(60, bar.layoutParams.height)
            assertTrue(contents.bottom + contents.translationY <= 60.5f)
            settle(129)
            assertEquals(129, bar.layoutParams.height)
        }
        val writes = bar.parameterWrites + contents.parameterWrites
        settle()
        assertEquals(writes, bar.parameterWrites + contents.parameterWrites)
        assertEquals(1, window.layoutListeners.size)
    }

    @Test fun detachRestoresOwnedAttributesAndRemovesParentListener() {
        contents.translationY = 7f
        offset()
        bindAndSettle()
        window.removeView(bar)
        assertEquals(0, window.layoutListeners.size)
        assertEquals(80, bar.layoutParams.height)
        assertEquals(80, contents.layoutParams.height)
        assertEquals(7f, contents.translationY, .001f)
    }

    @Test fun detachPreservesAttributesAlreadyReplacedByNativeCode() {
        offset()
        bindAndSettle()
        bar.layoutParams = bar.layoutParams.apply { height = 96 }
        contents.layoutParams = FrameLayout.LayoutParams(-1, 64, Gravity.BOTTOM)
        contents.translationY = 11f
        window.removeView(bar)
        assertEquals(96, bar.layoutParams.height)
        assertEquals(64, contents.layoutParams.height)
        assertEquals(Gravity.BOTTOM, (contents.layoutParams as FrameLayout.LayoutParams).gravity)
        assertEquals(11f, contents.translationY, .001f)
    }

    @Test fun sameViewReattachesWithoutDuplicatingParentListenerOrLosingPreferences() {
        offset()
        bindAndSettle()
        window.removeView(bar)
        window.addView(bar)
        settle()
        assertEquals(1, window.layoutListeners.size)
        assertEquals(1, bar.layoutListeners.size)
        assertEquals(129, bar.layoutParams.height)
        offset(20)
        val refresh = StatusBarContentGeometryHooks::class.java.getDeclaredMethod("applyToAttached")
        refresh.isAccessible = true
        refresh.invoke(StatusBarContentGeometryHooks)
        settle()
        assertEquals(80, bar.layoutParams.height)
    }

    @Test fun replacedWindowCannotKeepDrivingItsFormerChild() {
        offset()
        bindAndSettle()
        val stale = window.layoutListeners.single()
        val oldWindow = window
        oldWindow.removeView(bar)
        val replacement = TestStatusBarWindowView(activity)
        activity.setContentView(replacement)
        replacement.addView(bar)
        window = replacement
        settle(70)
        assertEquals(70, bar.layoutParams.height)
        assertEquals(0, oldWindow.layoutListeners.size)
        assertEquals(1, replacement.layoutListeners.size)
        val writes = bar.parameterWrites + contents.parameterWrites
        stale.onLayoutChange(oldWindow, 0, 0, 400, 30, 0, 0, 400, 129)
        assertEquals(writes, bar.parameterWrites + contents.parameterWrites)
        assertEquals(70, bar.layoutParams.height)
    }

    @Test fun ordinaryRootIsNotSubscribedAsAStatusBarWindow() {
        window.removeView(bar)
        val ordinary = CountedLayout(activity)
        ordinary.addView(bar)
        activity.setContentView(ordinary)
        layoutWindow(129, target = ordinary)
        offset()
        StatusBarContentGeometryHooks.bind(bar)
        assertEquals(0, ordinary.layoutListeners.size)
    }

    @Test fun unmeasuredWindowWaitsForItsFirstRealLayout() {
        window.removeView(bar)
        window = TestStatusBarWindowView(activity)
        window.addView(bar)
        activity.setContentView(window)
        offset()
        StatusBarContentGeometryHooks.bind(bar)
        assertEquals(80, bar.layoutParams.height)
        assertEquals(0f, contents.translationY, .001f)
        settle(60)
        assertEquals(60, bar.layoutParams.height)
        assertTrue(contents.bottom + contents.translationY <= 60.5f)
    }

    @Test fun dualRowsFollowParentWithoutAContentOffset() {
        MainModule.mPrefs.put("system_statusbar_dualrows", true)
        contents.layoutParams = FrameLayout.LayoutParams(-1, -1, Gravity.TOP)
        bindAndSettle()
        assertEquals(129, bar.layoutParams.height)
        settle(60)
        assertEquals(60, bar.layoutParams.height)
        assertEquals(60, contents.height)
        MainModule.mPrefs.put("system_statusbar_dualrows", false)
        apply()
        settle()
        assertEquals(80, bar.layoutParams.height)
        assertEquals(-1, contents.layoutParams.height)
    }

    @Test fun defaultLayoutDoesNotClaimContainerGravity() {
        window.removeView(bar)
        val parent = CountedLayout(activity)
        parent.addView(bar)
        window.addView(parent, FrameLayout.LayoutParams(-1, -1, Gravity.BOTTOM or Gravity.END))
        bindAndSettle()
        assertEquals(Gravity.BOTTOM or Gravity.END, (parent.layoutParams as FrameLayout.LayoutParams).gravity)
    }

    @Test fun containerGravityRestoresOnlyItsOwnLastWrite() {
        window.removeView(bar)
        val parent = CountedLayout(activity)
        parent.addView(bar)
        window.addView(parent, FrameLayout.LayoutParams(-1, -1, Gravity.BOTTOM or Gravity.END))
        offset()
        bindAndSettle()
        assertEquals(Gravity.TOP or Gravity.END, (parent.layoutParams as FrameLayout.LayoutParams).gravity)
        offset(20)
        apply()
        assertEquals(Gravity.BOTTOM or Gravity.END, (parent.layoutParams as FrameLayout.LayoutParams).gravity)
        offset()
        apply()
        parent.layoutParams = FrameLayout.LayoutParams(-1, -1, Gravity.CENTER_VERTICAL or Gravity.START)
        offset(20)
        apply()
        assertEquals(Gravity.CENTER_VERTICAL or Gravity.START,
            (parent.layoutParams as FrameLayout.LayoutParams).gravity)
    }

    @Test fun activeContentsKeepLatestNativeHeightAndGravityAsTheirBaseline() {
        offset()
        bindAndSettle()
        contents.layoutParams = FrameLayout.LayoutParams(-1, 64, Gravity.BOTTOM or Gravity.END)
        apply()
        settle()
        assertEquals(64, contents.layoutParams.height)
        assertEquals(Gravity.CENTER_VERTICAL or Gravity.END,
            (contents.layoutParams as FrameLayout.LayoutParams).gravity)
        offset(20)
        apply()
        assertEquals(64, contents.layoutParams.height)
        assertEquals(Gravity.BOTTOM or Gravity.END,
            (contents.layoutParams as FrameLayout.LayoutParams).gravity)
    }

    @Test fun replacingContentsRestoresTheOldViewAndCapturesTheNewOne() {
        contents.translationY = 7f
        offset()
        bindAndSettle()
        val previous = contents
        bar.removeView(previous)
        contents = CountedLayout(activity).apply { translationY = 3f }
        bar.addView(contents, FrameLayout.LayoutParams(-1, 64, Gravity.BOTTOM))
        apply()
        settle()
        assertEquals(7f, previous.translationY, .001f)
        assertEquals(80, previous.layoutParams.height)
        assertEquals(8f, contents.translationY, .001f)
        offset(20)
        apply()
        assertEquals(3f, contents.translationY, .001f)
        assertEquals(64, contents.layoutParams.height)
        assertEquals(Gravity.BOTTOM, (contents.layoutParams as FrameLayout.LayoutParams).gravity)
    }

    @Test fun oldBindingCallbackCannotTouchANewStatusBarInTheSameWindow() {
        offset()
        bindAndSettle()
        val stale = window.layoutListeners.single()
        val previous = bar
        window.removeView(previous)
        bar = CountedLayout(activity)
        contents = CountedLayout(activity).apply { translationY = 9f }
        bar.addView(contents, FrameLayout.LayoutParams(-1, 64, Gravity.TOP))
        window.addView(bar, FrameLayout.LayoutParams(-1, 96))
        bindAndSettle()
        val writes = previous.parameterWrites + bar.parameterWrites + contents.parameterWrites
        stale.onLayoutChange(window, 0, 0, 400, 30, 0, 0, 400, 129)
        assertEquals(writes, previous.parameterWrites + bar.parameterWrites + contents.parameterWrites)
        offset(20)
        apply()
        assertEquals(96, bar.layoutParams.height)
        assertEquals(9f, contents.translationY, .001f)
        assertEquals(80, previous.layoutParams.height)
    }

    @Test fun explicitNativeHeightCallbackInvalidatesEvenAnEqualLastModuleValue() {
        offset()
        bindAndSettle()
        assertEquals(129, bar.layoutParams.height)
        // updateStatusBarHeight can legitimately calculate the same integer we last wrote.
        val nativeCall = NativeCall(bar, newHeight = 129)
        StatusBarContentGeometryHooks.NativeHeightCallback().intercept(nativeCall)
        assertEquals(1, nativeCall.calls)
        offset(20)
        apply()
        assertEquals(129, bar.layoutParams.height)
    }

    @Test fun configurationRefreshUsesCurrentDensityAndNativeHeight() {
        contents.translationY = 7f
        offset()
        bindAndSettle()
        org.robolectric.RuntimeEnvironment.setQualifiers("hdpi")
        bar.layoutParams = bar.layoutParams.apply { height = 96 }
        StatusBarContentGeometryHooks.nativeHeightChanged(bar)
        assertEquals(14.5f, contents.translationY, .001f)
        offset(20)
        apply()
        assertEquals(96, bar.layoutParams.height)
        assertEquals(7f, contents.translationY, .001f)
    }

    @Test fun widthOnlyRotationLayoutDoesNotRepeatVerticalWrites() {
        offset()
        bindAndSettle()
        val writes = bar.parameterWrites + contents.parameterWrites
        layoutWindow(129, width = 720)
        assertEquals(writes, bar.parameterWrites + contents.parameterWrites)
        assertEquals(1, window.layoutListeners.size)
    }

    @Test fun heightSentinelAndExplicit27HaveDifferentOwnership() {
        bindAndSettle()
        settle(60)
        assertEquals(80, bar.layoutParams.height)
        MainModule.mPrefs.put("system_statusbarheight", 27)
        apply()
        settle(60)
        assertEquals(60, bar.layoutParams.height)
        MainModule.mPrefs.put("system_statusbarheight", 11)
        apply()
        assertEquals(80, bar.layoutParams.height)
    }

    @Test fun reattachDiscardsOldBaselinesAndUsesDetachedNativeWrites() {
        contents.translationY = 7f
        offset()
        bindAndSettle()
        window.removeView(bar)
        bar.layoutParams = bar.layoutParams.apply { height = 96 }
        contents.translationY = 11f
        window.addView(bar)
        settle()
        assertEquals(16f, contents.translationY, .001f)
        offset(20)
        apply()
        assertEquals(96, bar.layoutParams.height)
        assertEquals(11f, contents.translationY, .001f)
    }

    @Test fun temporarilyEmptyOrInvisibleWindowDoesNotForceVisibilityOrCollapseNativeState() {
        offset()
        bindAndSettle()
        val writes = bar.parameterWrites + contents.parameterWrites
        window.visibility = View.INVISIBLE
        settle(0)
        assertEquals(View.INVISIBLE, window.visibility)
        assertEquals(writes, bar.parameterWrites + contents.parameterWrites)
        window.visibility = View.VISIBLE
        settle(60)
        assertEquals(60, bar.layoutParams.height)
        assertTrue(contents.bottom + contents.translationY <= 60.5f)
    }

    @Test fun changedParentWritesEachLayoutOnceAndSteadyInputsWriteNothing() {
        offset()
        bindAndSettle()
        val beforeBar = bar.parameterWrites
        val beforeContents = contents.parameterWrites
        settle(60)
        assertEquals(1, bar.parameterWrites - beforeBar)
        assertEquals(1, contents.parameterWrites - beforeContents)
        val changedBar = bar.parameterWrites
        val changedContents = contents.parameterWrites
        repeat(12) { apply(); settle(60) }
        assertEquals(changedBar, bar.parameterWrites)
        assertEquals(changedContents, contents.parameterWrites)
        println("parent 129->60: owner LP writes=1, contents LP writes=1; 12 unchanged applies: 0/0")
        println("listeners: child=" + bar.layoutListeners.size + ", attach=" + bar.attachListeners.size +
            ", window=" + window.layoutListeners.size)
    }

    @Test fun unsupportedContentsParamsStillReleaseOwnedTranslation() {
        contents.translationY = 7f
        offset()
        bindAndSettle()
        contents.layoutParams = LinearLayout.LayoutParams(-1, 64)
        offset(20)
        apply()
        assertEquals(7f, contents.translationY, .001f)
        contents.layoutParams = FrameLayout.LayoutParams(-1, 64, Gravity.BOTTOM)
        offset()
        apply()
        assertEquals(12f, contents.translationY, .001f)
        offset(20)
        apply()
        assertEquals(7f, contents.translationY, .001f)
        assertEquals(64, contents.layoutParams.height)
    }

    @Test fun failedNativeHeightUpdateDoesNotAdoptTheModulesValue() {
        offset()
        bindAndSettle()
        val error = IllegalStateException("native update failed before writing")
        val nativeCall = NativeCall(bar, failure = error)
        val caught = assertThrows(IllegalStateException::class.java) {
            StatusBarContentGeometryHooks.NativeHeightCallback().intercept(nativeCall)
        }
        assertSame(error, caught)
        assertEquals(1, nativeCall.calls)
        offset(20)
        apply()
        assertEquals(80, bar.layoutParams.height)
    }

    @Test fun skippedNativeHeightUpdateDoesNotAdoptTheModulesValue() {
        offset()
        bindAndSettle()
        val nativeCall = NativeCall(bar)
        val before = BeforeHookCallback(nativeCall).apply { returnAndSkip(null) }
        StatusBarContentGeometryHooks.NativeHeightCallback().afterHook(AfterHookCallback(before, null, null))
        assertEquals(0, nativeCall.calls)
        offset(20)
        apply()
        assertEquals(80, bar.layoutParams.height)
    }

    @Test fun fatalNativeHeightFailureStillEscapesWithoutAnAfterWrite() {
        offset()
        bindAndSettle()
        val error = OutOfMemoryError("native fatal")
        val nativeCall = NativeCall(bar, failure = error)
        val caught = assertThrows(OutOfMemoryError::class.java) {
            StatusBarContentGeometryHooks.NativeHeightCallback().intercept(nativeCall)
        }
        assertSame(error, caught)
        assertEquals(1, nativeCall.calls)
        offset(20)
        apply()
        assertEquals(80, bar.layoutParams.height)
    }

    @Test fun staleChildSizeEventAfterReattachReadsOnlyCurrentGeometry() {
        offset()
        bindAndSettle()
        val stale = bar.layoutListeners.single()
        window.removeView(bar)
        bar.layoutParams = bar.layoutParams.apply { height = 96 }
        window.addView(bar)
        settle(70)
        val writes = bar.parameterWrites + contents.parameterWrites
        stale.onLayoutChange(bar, 0, 0, 400, 129, 0, 0, 400, 80)
        assertEquals(writes, bar.parameterWrites + contents.parameterWrites)
        assertEquals(70, bar.layoutParams.height)
        offset(20)
        apply()
        assertEquals(96, bar.layoutParams.height)
    }

    private class NativeCall(
        private val view: View, private val newHeight: Int? = null, private val failure: Throwable? = null,
    ) : XposedInterface.Chain by FakeChain(view, emptyArray()) {
        var calls = 0
        override fun proceed(): Any? {
            calls++
            failure?.let { throw it }
            newHeight?.let { height -> view.layoutParams = view.layoutParams.apply { this.height = height } }
            return null
        }
        override fun proceed(args: Array<Any>): Any? = error("native height callback must not copy arguments")
    }

    private open class CountedLayout(context: Context) : FrameLayout(context) {
        var parameterWrites = 0
        val layoutListeners = LinkedHashSet<View.OnLayoutChangeListener>()
        val attachListeners = LinkedHashSet<View.OnAttachStateChangeListener>()
        override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
            parameterWrites++
            super.setLayoutParams(params)
        }
        override fun addOnLayoutChangeListener(listener: View.OnLayoutChangeListener) {
            layoutListeners.add(listener)
            super.addOnLayoutChangeListener(listener)
        }
        override fun removeOnLayoutChangeListener(listener: View.OnLayoutChangeListener) {
            layoutListeners.remove(listener)
            super.removeOnLayoutChangeListener(listener)
        }
        override fun addOnAttachStateChangeListener(listener: View.OnAttachStateChangeListener) {
            attachListeners.add(listener)
            super.addOnAttachStateChangeListener(listener)
        }
        override fun removeOnAttachStateChangeListener(listener: View.OnAttachStateChangeListener) {
            attachListeners.remove(listener)
            super.removeOnAttachStateChangeListener(listener)
        }
    }
    private class TestStatusBarWindowView(context: Context) : CountedLayout(context)
}
