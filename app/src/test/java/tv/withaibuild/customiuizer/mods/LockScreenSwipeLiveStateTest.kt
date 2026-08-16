package tv.withaibuild.customiuizer.mods

import android.view.VelocityTracker
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Executable

/**
 * Behavioral tests for the lock screen swipe suppression live-state contract in
 * [SystemUILockScreenHooks]. The auxiliary hooks are installed once and remain active;
 * preference changes gate the callbacks through volatile primitives only.
 */
class LockScreenSwipeLiveStateTest {

    private val rightOffKey = "system_lockscreenshortcuts_right_off"
    private val leftOffKey = "system_lockscreenshortcuts_left_off"

    private var savedPrefs: Map<String, Any> = emptyMap()

    @Before
    fun setUp() {
        savedPrefs = MainModule.mPrefs.getAll()
        MainModule.mPrefs.clear()
        SystemUILockScreenHooks.swipeRightOff = false
        SystemUILockScreenHooks.swipeLeftOff = false
    }

    @After
    fun tearDown() {
        MainModule.mPrefs.clear()
        if (savedPrefs.isNotEmpty()) {
            (MainModule.mPrefs as PrefMap).replaceSnapshot(savedPrefs)
        } else {
            MainModule.mPrefs.clear()
        }
        SystemUILockScreenHooks.swipeRightOff = false
        SystemUILockScreenHooks.swipeLeftOff = false
    }

    @Test
    fun initialFalseSetTranslationPassesThrough() {
        val callback = newCallback(target = FakeKeyguardMoveHelper(mCurrentScreen = 1), args = listOf(-10.0f, false, false, false, false))

        SystemUILockScreenHooks.onKeyguardMoveHelperSetTranslationBefore(callback)

        assertFalse("callback must not skip original method", callback.skipped)
        assertEquals(-10.0f, callback.getArgs()[0])
    }

    @Test
    fun initialFalseEndMotionPassesThrough() {
        val callback = newCallback(target = FakeKeyguardMoveHelper(mCurrentScreen = 1, mTranslation = 0.0f))

        SystemUILockScreenHooks.onKeyguardMoveHelperEndMotionBefore(callback)

        assertFalse(callback.skipped)
        assertNull(callback.result)
    }

    @Test
    fun initialFalseRightControllerCallbacksPassThrough() {
        val down = newCallback()
        val move = newCallback()

        SystemUILockScreenHooks.onKeyguardMoveRightControllerOnTouchDownBefore(down)
        SystemUILockScreenHooks.onKeyguardMoveRightControllerOnTouchMoveBefore(move)

        assertFalse(down.skipped)
        assertFalse(move.skipped)
        assertNull(down.result)
        assertNull(move.result)
    }

    @Test
    fun falseToTrueWithoutRestartActivatesRightSwipeSuppression() {
        MainModule.mPrefs.put(rightOffKey, true)
        SystemUILockScreenHooks.onSwipeSuppressionPreferenceChanged(null)

        val setTranslation = newCallback(
            target = FakeKeyguardMoveHelper(mCurrentScreen = 1),
            args = listOf(-10.0f, false, false, false, false)
        )
        val endMotion = newCallback(target = FakeKeyguardMoveHelper(mCurrentScreen = 1, mTranslation = 0.0f))
        val down = newCallback()
        val move = newCallback()

        SystemUILockScreenHooks.onKeyguardMoveHelperSetTranslationBefore(setTranslation)
        SystemUILockScreenHooks.onKeyguardMoveHelperEndMotionBefore(endMotion)
        SystemUILockScreenHooks.onKeyguardMoveRightControllerOnTouchDownBefore(down)
        SystemUILockScreenHooks.onKeyguardMoveRightControllerOnTouchMoveBefore(move)

        assertEquals("setTranslation must zero out rightward swipe", 0.0f, setTranslation.getArgs()[0])
        assertFalse("setTranslation must not skip; it rewrites the arg", setTranslation.skipped)
        assertTrue(endMotion.skipped)
        assertNull(endMotion.result)
        assertTrue(down.skipped)
        assertNull(down.result)
        assertTrue(move.skipped)
        assertEquals(true, move.result)
    }

    @Test
    fun trueToFalseWithoutRestartDisablesRightSwipeSuppression() {
        MainModule.mPrefs.put(rightOffKey, true)
        SystemUILockScreenHooks.onSwipeSuppressionPreferenceChanged(null)

        MainModule.mPrefs.put(rightOffKey, false)
        SystemUILockScreenHooks.onSwipeSuppressionPreferenceChanged(null)

        val setTranslation = newCallback(
            target = FakeKeyguardMoveHelper(mCurrentScreen = 1),
            args = listOf(-10.0f, false, false, false, false)
        )
        val endMotion = newCallback(target = FakeKeyguardMoveHelper(mCurrentScreen = 1, mTranslation = 0.0f))
        val down = newCallback()
        val move = newCallback()

        SystemUILockScreenHooks.onKeyguardMoveHelperSetTranslationBefore(setTranslation)
        SystemUILockScreenHooks.onKeyguardMoveHelperEndMotionBefore(endMotion)
        SystemUILockScreenHooks.onKeyguardMoveRightControllerOnTouchDownBefore(down)
        SystemUILockScreenHooks.onKeyguardMoveRightControllerOnTouchMoveBefore(move)

        assertFalse(setTranslation.skipped)
        assertEquals(-10.0f, setTranslation.getArgs()[0])
        assertFalse(endMotion.skipped)
        assertFalse(down.skipped)
        assertFalse(move.skipped)
    }

    @Test
    fun unrelatedKeyDoesNotRefreshSwipeState() {
        MainModule.mPrefs.put(rightOffKey, true)
        SystemUILockScreenHooks.swipeRightOff = false
        SystemUILockScreenHooks.swipeLeftOff = false

        SystemUILockScreenHooks.onSwipeSuppressionPreferenceChanged("system_lockscreenshortcuts_left_tapaction")

        assertFalse(SystemUILockScreenHooks.swipeRightOff)
    }

    @Test
    fun nullKeyRefreshesBothSwipeStates() {
        MainModule.mPrefs.put(rightOffKey, true)
        MainModule.mPrefs.put(leftOffKey, true)
        SystemUILockScreenHooks.swipeRightOff = false
        SystemUILockScreenHooks.swipeLeftOff = false

        SystemUILockScreenHooks.onSwipeSuppressionPreferenceChanged(null)

        assertTrue(SystemUILockScreenHooks.swipeRightOff)
        assertTrue(SystemUILockScreenHooks.swipeLeftOff)
    }

    @Test
    fun setTranslationRightAndLeftDirectionsAreIndependent() {
        SystemUILockScreenHooks.swipeRightOff = true
        SystemUILockScreenHooks.swipeLeftOff = false

        val rightSwipe = newCallback(
            target = FakeKeyguardMoveHelper(mCurrentScreen = 1),
            args = listOf(-10.0f, false, false, false, false)
        )
        val leftSwipe = newCallback(
            target = FakeKeyguardMoveHelper(mCurrentScreen = 1),
            args = listOf(10.0f, false, false, false, false)
        )

        SystemUILockScreenHooks.onKeyguardMoveHelperSetTranslationBefore(rightSwipe)
        SystemUILockScreenHooks.onKeyguardMoveHelperSetTranslationBefore(leftSwipe)

        assertEquals(0.0f, rightSwipe.getArgs()[0])
        assertEquals(10.0f, leftSwipe.getArgs()[0])
    }

    @Test
    fun setTranslationLeftOffSuppression() {
        SystemUILockScreenHooks.swipeRightOff = false
        SystemUILockScreenHooks.swipeLeftOff = true

        val leftSwipe = newCallback(
            target = FakeKeyguardMoveHelper(mCurrentScreen = 1),
            args = listOf(10.0f, false, false, false, false)
        )

        SystemUILockScreenHooks.onKeyguardMoveHelperSetTranslationBefore(leftSwipe)

        assertEquals(0.0f, leftSwipe.getArgs()[0])
    }

    @Test
    fun setTranslationNonCurrentScreenIsNotSuppressed() {
        SystemUILockScreenHooks.swipeRightOff = true
        SystemUILockScreenHooks.swipeLeftOff = true

        val callback = newCallback(
            target = FakeKeyguardMoveHelper(mCurrentScreen = 0),
            args = listOf(-10.0f, false, false, false, false)
        )

        SystemUILockScreenHooks.onKeyguardMoveHelperSetTranslationBefore(callback)

        assertEquals(-10.0f, callback.getArgs()[0])
    }

    @Test
    fun originalReturnSemanticsArePreservedWhenSuppressionIsActive() {
        SystemUILockScreenHooks.swipeRightOff = true
        SystemUILockScreenHooks.swipeLeftOff = false

        val endMotion = newCallback(target = FakeKeyguardMoveHelper(mCurrentScreen = 1, mTranslation = 0.0f))
        val down = newCallback()
        val move = newCallback()

        SystemUILockScreenHooks.onKeyguardMoveHelperEndMotionBefore(endMotion)
        SystemUILockScreenHooks.onKeyguardMoveRightControllerOnTouchDownBefore(down)
        SystemUILockScreenHooks.onKeyguardMoveRightControllerOnTouchMoveBefore(move)

        assertTrue(endMotion.skipped)
        assertNull(endMotion.result)
        assertTrue(down.skipped)
        assertNull(down.result)
        assertTrue(move.skipped)
        assertEquals(true, move.result)
    }

    @Test
    fun endMotionSkipsWhenVelocityTranslationProductIsZero() {
        SystemUILockScreenHooks.swipeRightOff = true
        SystemUILockScreenHooks.swipeLeftOff = false

        val belowThreshold = newCallback(target = FakeKeyguardMoveHelper(mCurrentScreen = 1, mTranslation = 0.0f))

        SystemUILockScreenHooks.onKeyguardMoveHelperEndMotionBefore(belowThreshold)

        assertTrue(belowThreshold.skipped)
        assertNull(belowThreshold.result)
    }

    @Test
    fun fatalErrorPropagatesFromSetTranslationCallback() {
        SystemUILockScreenHooks.swipeRightOff = true
        SystemUILockScreenHooks.swipeLeftOff = false
        val error = OutOfMemoryError("setTranslation OOM")
        val callback = newCallback(
            target = FakeKeyguardMoveHelper(mCurrentScreen = 1),
            args = listOf(-10.0f, false, false, false, false),
            argsThrow = error
        )

        try {
            SystemUILockScreenHooks.onKeyguardMoveHelperSetTranslationBefore(callback)
            assertTrue("fatal error must propagate", false)
        } catch (t: Throwable) {
            assertSame(error, t)
        }
    }

    @Test
    fun fatalErrorPropagatesFromEndMotionCallback() {
        SystemUILockScreenHooks.swipeRightOff = true
        SystemUILockScreenHooks.swipeLeftOff = false
        val error = OutOfMemoryError("endMotion OOM")
        val callback = newCallback(thisThrow = error)

        try {
            SystemUILockScreenHooks.onKeyguardMoveHelperEndMotionBefore(callback)
            assertTrue("fatal error must propagate", false)
        } catch (t: Throwable) {
            assertSame(error, t)
        }
    }

    @Test
    fun concurrentVisibilityOfSwipeRightOff() {
        SystemUILockScreenHooks.swipeRightOff = false
        SystemUILockScreenHooks.swipeLeftOff = false

        val latch = java.util.concurrent.CountDownLatch(1)
        Thread {
            SystemUILockScreenHooks.swipeRightOff = true
        SystemUILockScreenHooks.swipeLeftOff = false
            latch.countDown()
        }.start()

        var seen = false
        val deadline = java.lang.System.currentTimeMillis() + 5_000
        while (java.lang.System.currentTimeMillis() < deadline) {
            if (SystemUILockScreenHooks.swipeRightOff) {
                seen = true
                break
            }
        }
        latch.await()

        assertTrue("write from another thread must become visible", seen)
    }

    private fun newCallback(
        target: Any? = null,
        args: List<Any?> = emptyList(),
        thisThrow: Throwable? = null,
        argsThrow: Throwable? = null,
    ): HookerClassHelper.BeforeHookCallback {
        val chain = FakeChain(target = target, argList = args, thisThrow = thisThrow, argsThrow = argsThrow)
        return HookerClassHelper.BeforeHookCallback(chain)
    }

    private class FakeKeyguardMoveHelper(
        val mCurrentScreen: Int = 1,
        val mTranslation: Float = 0.0f,
        val mVelocityTracker: VelocityTracker? = null,
    )

    private class FakeChain(
        private val target: Any? = null,
        private val argList: List<Any?> = emptyList(),
        private val thisThrow: Throwable? = null,
        private val argsThrow: Throwable? = null,
    ) : XposedInterface.Chain {

        var proceedCount = 0
            private set

        override fun getExecutable(): Executable = error("not used in test")
        override fun getThisObject(): Any? {
            thisThrow?.let { throw it }
            return target
        }

        override fun getArgs(): List<Any?> {
            argsThrow?.let { throw it }
            return argList
        }

        override fun getArg(index: Int): Any? = if (index in argList.indices) argList[index] else null

        override fun proceed(): Any? {
            proceedCount++
            return null
        }

        override fun proceed(p0: Array<Any>): Any? {
            proceedCount++
            return null
        }

        override fun proceedWith(p0: Any): Any? = error("not used in test")
        override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("not used in test")
    }
}
