package tv.withaibuild.customiuizer.mods.duostatusbar

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class DuoReducedMotionTest {
    private val savedScale = ValueAnimator.getDurationScale()
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
    private val view = DuoStatusBarView(activity.get(), DuoConfig(true, 28), { throw AssertionError(it) }, {})
    private val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    private val transition: DuoTransition get() = ReflectionHelpers.getField(view, "transition")
    private val secondary: DuoTransition get() = ReflectionHelpers.getField(view, "secondaryTransition")

    init {
        activity.get().setContentView(view, FrameLayout.LayoutParams(64, 64))
        view.measure(View.MeasureSpec.makeMeasureSpec(64, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(64, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 64, 64)
        assertTrue(view.isShown)
        assertEquals(View.VISIBLE, view.windowVisibility)
    }

    @After fun close() { scale(savedScale); bitmap.recycle(); activity.pause().stop().destroy() }

    private fun scale(value: Float) {
        ReflectionHelpers.callStaticMethod<Unit>(ValueAnimator::class.java, "setDurationScale",
            ClassParameter.from(Float::class.javaPrimitiveType!!, value))
    }

    private fun changeCenter() {
        view.render(50, false, false, -1, 0, 0)
        val network = DuoNetworkState().apply { wifi(true, true, 4) }
        view.render(50, false, false, -1, network.snapshot, 0)
    }

    @Test fun disablingAnimationsMidTransitionFinishesOnTheNextActualDraw() {
        scale(1f)
        changeCenter()
        assertTrue(transition.fraction(android.os.SystemClock.uptimeMillis()) < 1f)
        assertTrue(secondary.fraction(android.os.SystemClock.uptimeMillis()) < 1f)
        scale(0f)
        view.draw(Canvas(bitmap))
        assertEquals(1f, transition.fraction(android.os.SystemClock.uptimeMillis()), 0f)
        assertEquals(transition.current, transition.previous)
        assertEquals(255, transition.currentAlpha)
        assertEquals(1f,secondary.fraction(android.os.SystemClock.uptimeMillis()),0f)
        assertEquals(secondary.current,secondary.previous)
    }

    @Test fun disabledAnimationsFromTheStartCommitWithoutAClock() {
        scale(0f)
        changeCenter()
        view.draw(Canvas(bitmap))
        assertEquals(1f, transition.fraction(android.os.SystemClock.uptimeMillis()), 0f)
        assertEquals(transition.current, transition.previous)
        assertEquals(secondary.current,secondary.previous)
    }

    @Test fun enabledAnimationsKeepTheirOriginalFiniteDeadline() {
        scale(1f)
        changeCenter()
        view.draw(Canvas(bitmap))
        val now = android.os.SystemClock.uptimeMillis()
        assertTrue(transition.fraction(now + 60) in 0f..0.5f)
        assertEquals(1f, transition.fraction(now + 1600), 0f)
        assertEquals(1f, secondary.fraction(now + 1600), 0f)
    }
}
