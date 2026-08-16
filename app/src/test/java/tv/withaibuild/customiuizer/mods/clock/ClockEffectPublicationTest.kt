package tv.withaibuild.customiuizer.mods.clock

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockEffectPublicationTest {

    // ------------------------------------------------------------------------
    // A. Cold complete ABI returns effect immediately.
    // ------------------------------------------------------------------------

    @Test
    fun coldComplete_resolveReturnsEffectImmediately() {
        val publication = publicationWithColdComplete()

        val current = publication.currentEffect()
        assertNotNull(current)
        assertEquals(0, publication.calibrationAttempts)

        val clock = FakeB2MiuiClock().apply {
            mMiuiStatusBarClockController = FakeB2ControllerColdComplete()
        }

        val resolved = publication.resolveForClock(clock, FakeB2Context::class.java)

        assertSame(current, resolved)
        assertEquals(0, publication.calibrationAttempts)
    }

    @Test
    fun coldComplete_secondCallReturnsSameEffect() {
        val publication = publicationWithColdComplete()
        val clock = FakeB2MiuiClock().apply {
            mMiuiStatusBarClockController = FakeB2ControllerColdComplete()
        }

        val current = publication.currentEffect()
        val first = publication.resolveForClock(clock, FakeB2Context::class.java)
        val second = publication.resolveForClock(clock, FakeB2Context::class.java)

        assertSame(current, first)
        assertSame(first, second)
        assertEquals(0, publication.calibrationAttempts)
    }

    // ------------------------------------------------------------------------
    // B. Cold incomplete runtime calibration.
    // ------------------------------------------------------------------------

    @Test
    fun coldIncomplete_runtimeCalendarCalibrationPublishesEffect() {
        val publication = publicationWithColdIncomplete()

        assertNull(publication.currentEffect())

        val clock = FakeB2MiuiClock().apply {
            mMiuiStatusBarClockController = FakeB2ControllerColdIncomplete()
        }

        val effect = publication.resolveForClock(clock, FakeB2Context::class.java)

        assertNotNull(effect)
        assertEquals(1, publication.calibrationAttempts)
        assertSame(effect, publication.currentEffect())
    }

    @Test
    fun runtimePublication_fastPathAfterSuccess() {
        val publication = publicationWithColdIncomplete()
        val clock = FakeB2MiuiClock().apply {
            mMiuiStatusBarClockController = FakeB2ControllerColdIncomplete()
        }

        assertNull(publication.currentEffect())

        val first = publication.resolveForClock(clock, FakeB2Context::class.java)
        assertNotNull(first)
        assertEquals(1, publication.calibrationAttempts)
        assertSame(first, publication.currentEffect())

        val second = publication.resolveForClock(clock, FakeB2Context::class.java)
        assertSame(first, second)
        assertEquals(1, publication.calibrationAttempts)
        assertSame(first, publication.currentEffect())
    }

    // ------------------------------------------------------------------------
    // D/E. Most-specific target and order independence.
    // ------------------------------------------------------------------------

    @Test
    fun mostSpecificTargetWins_baseFirst() {
        val controller = FakeB2ControllerColdComplete()
        val clock = FakeB2ChildClock().apply {
            mMiuiStatusBarClockController = controller
        }

        val baseTarget = ClockResolver.resolveClockTargetClass(FakeB2BaseClock::class.java)!!
        val childTarget = ClockResolver.resolveClockTargetClass(FakeB2ChildClock::class.java)!!
        val calendar = calendarFor(FakeB2Calendar::class.java)

        val publication = ClockEffectPublication(
            ClockAbi(
                controllerFor(FakeB2ControllerColdComplete::class.java),
                arrayOf(baseTarget, childTarget),
                calendar,
            ),
        )

        val effect = publication.resolveForClock(clock, FakeB2Context::class.java)!!
        assertTrue(effect.invokeUpdateTime(clock))
        assertTrue(clock.childCalled)
        assertFalse(clock.parentCalled)
    }

    @Test
    fun mostSpecificTargetWins_childFirst() {
        val controller = FakeB2ControllerColdComplete()
        val clock = FakeB2ChildClock().apply {
            mMiuiStatusBarClockController = controller
        }

        val baseTarget = ClockResolver.resolveClockTargetClass(FakeB2BaseClock::class.java)!!
        val childTarget = ClockResolver.resolveClockTargetClass(FakeB2ChildClock::class.java)!!
        val calendar = calendarFor(FakeB2Calendar::class.java)

        val publication = ClockEffectPublication(
            ClockAbi(
                controllerFor(FakeB2ControllerColdComplete::class.java),
                arrayOf(childTarget, baseTarget),
                calendar,
            ),
        )

        val effect = publication.resolveForClock(clock, FakeB2Context::class.java)!!
        assertTrue(effect.invokeUpdateTime(clock))
        assertTrue(clock.childCalled)
        assertFalse(clock.parentCalled)
    }

    // ------------------------------------------------------------------------
    // F. Duplicate / incomparable targets fail closed.
    // ------------------------------------------------------------------------

    @Test
    fun duplicateTargetClassFailsClosed() {
        val target = ClockResolver.resolveClockTargetClass(FakeB2Clock::class.java)!!
        val publication = ClockEffectPublication(
            ClockAbi(
                controllerFor(FakeB2ControllerColdIncomplete::class.java),
                arrayOf(target, target),
                null,
            ),
        )
        val clock = FakeB2Clock().apply {
            mMiuiStatusBarClockController = FakeB2ControllerColdIncomplete()
        }

        assertNull(publication.resolveForClock(clock, FakeB2Context::class.java))
        assertEquals(0, publication.calibrationAttempts)
    }

    @Test
    fun incomparableTargetsFailClosed() {
        val controllerField = FakeB2Clock::class.java.getDeclaredField("mMiuiStatusBarClockController").also { it.isAccessible = true }
        val updateTimeMethod = FakeB2Clock::class.java.getDeclaredMethod("updateTime").also { it.isAccessible = true }
        val clock = FakeB2AmbiguousClock().apply {
            mMiuiStatusBarClockController = FakeB2ControllerColdIncomplete()
        }

        val targets = arrayOf(
            ClockTargetCapability(Runnable::class.java, controllerField, updateTimeMethod),
            ClockTargetCapability(Cloneable::class.java, controllerField, updateTimeMethod),
        )

        val publication = ClockEffectPublication(
            ClockAbi(
                controllerFor(FakeB2ControllerColdIncomplete::class.java),
                targets,
                null,
            ),
        )

        assertNull(publication.resolveForClock(clock, FakeB2Context::class.java))
        assertEquals(0, publication.calibrationAttempts)
    }

    // ------------------------------------------------------------------------
    // G/H. Bounded failure memory.
    // ------------------------------------------------------------------------

    @Test
    fun failedTargetIsRemembered_noRetry() {
        val controller = FakeB2ControllerColdIncomplete().apply {
            mCalendar = FakeB2CalendarNoFormat()
        }
        val clock = FakeB2Clock().apply { mMiuiStatusBarClockController = controller }
        val publication = publicationWithColdIncomplete()

        assertNull(publication.resolveForClock(clock, FakeB2Context::class.java))
        assertEquals(1, publication.calibrationAttempts)

        assertNull(publication.resolveForClock(clock, FakeB2Context::class.java))
        assertEquals(1, publication.calibrationAttempts)
    }

    @Test
    fun failureOfOneTargetDoesNotPoisonOther() {
        val badMiui = FakeB2MiuiClock().apply {
            mMiuiStatusBarClockController = FakeB2ControllerColdIncomplete().apply {
                mCalendar = FakeB2CalendarNoFormat()
            }
        }

        val goodStatusBar = FakeB2ChildClock().apply {
            mMiuiStatusBarClockController = FakeB2ControllerColdIncomplete().apply {
                mCalendar = FakeB2Calendar()
            }
        }

        val miuiTarget = ClockResolver.resolveClockTargetClass(FakeB2MiuiClock::class.java)!!
        val childTarget = ClockResolver.resolveClockTargetClass(FakeB2ChildClock::class.java)!!

        val publication = ClockEffectPublication(
            ClockAbi(
                controllerFor(FakeB2ControllerColdIncomplete::class.java),
                arrayOf(miuiTarget, childTarget),
                null,
            ),
        )

        assertNull(publication.resolveForClock(badMiui, FakeB2Context::class.java))
        assertNotNull(publication.resolveForClock(goodStatusBar, FakeB2Context::class.java))
        assertEquals(2, publication.calibrationAttempts)
    }

    // ------------------------------------------------------------------------
    // I. No Android owner retained.
    // ------------------------------------------------------------------------

    @Test
    fun publicationRetainsNoAndroidOwners() {
        val publicationClass = ClockEffectPublication::class.java
        for (field in publicationClass.declaredFields) {
            val type = field.type
            assertFalse(
                "publication must not retain Context, View or controller; found ${field.name}: ${type.name}",
                Context::class.java.isAssignableFrom(type) ||
                    android.view.View::class.java.isAssignableFrom(type),
            )
        }
    }

    // ------------------------------------------------------------------------
    // Helpers and fakes.
    // ------------------------------------------------------------------------

    private fun controllerFor(controllerClass: Class<*>): ControllerCapability {
        return ClockResolver.resolveControllerClass(controllerClass)!!
    }

    private fun calendarFor(calendarClass: Class<*>): CalendarCapability {
        return ClockResolver.resolveCalendarFromDeclaredType(calendarClass, Context::class.java)!!
    }

    private fun publicationWithColdComplete(): ClockEffectPublication {
        val controller = controllerFor(FakeB2ControllerColdComplete::class.java)
        val target = ClockResolver.resolveClockTargetClass(FakeB2Clock::class.java)!!
        val calendar = calendarFor(FakeB2Calendar::class.java)
        return ClockEffectPublication(ClockAbi(controller, arrayOf(target), calendar))
    }

    private fun publicationWithColdIncomplete(): ClockEffectPublication {
        val controller = controllerFor(FakeB2ControllerColdIncomplete::class.java)
        val target = ClockResolver.resolveClockTargetClass(FakeB2Clock::class.java)!!
        return ClockEffectPublication(ClockAbi(controller, arrayOf(target), null))
    }

    open class FakeB2ControllerColdComplete {
        @JvmField
        var mCalendar: FakeB2Calendar = FakeB2Calendar()

        @JvmField
        var mClockListeners: ArrayList<Any> = ArrayList()

        @JvmField
        var mIs24: Boolean = false
    }

    open class FakeB2ControllerColdIncomplete {
        @JvmField
        var mCalendar: Any = FakeB2Calendar()

        @JvmField
        var mClockListeners: ArrayList<Any> = ArrayList()

        @JvmField
        var mIs24: Boolean = false
    }

    open class FakeB2Clock {
        @JvmField
        var mMiuiStatusBarClockController: Any = FakeB2ControllerColdIncomplete()

        open fun updateTime() {}
    }

    open class FakeB2MiuiClock : FakeB2Clock()

    open class FakeB2BaseClock {
        @JvmField
        var mMiuiStatusBarClockController: Any = FakeB2ControllerColdIncomplete()

        open fun updateTime() {
            parentCalled = true
        }

        var parentCalled: Boolean = false
    }

    open class FakeB2ChildClock : FakeB2BaseClock() {
        override fun updateTime() {
            childCalled = true
        }

        var childCalled: Boolean = false
    }

    class FakeB2AmbiguousClock : FakeB2Clock(), Runnable, Cloneable {
        override fun run() {}
    }

    class FakeB2Context : ContextWrapper(null)

    open class FakeB2Calendar {
        @JvmField
        var lastMillis: Long = -1

        @JvmField
        var lastPattern: StringBuilder? = null

        open fun setTimeInMillis(millis: Long) {
            lastMillis = millis
        }

        open fun format(ctx: Context, out: StringBuilder, pattern: StringBuilder) {
            lastPattern = pattern
            out.append(pattern)
        }
    }

    open class FakeB2CalendarNoFormat {
        fun setTimeInMillis(millis: Long) {}
    }
}
