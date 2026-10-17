package tv.withaibuild.customiuizer.mods.clock

import android.content.Context
import android.content.ContextWrapper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Field
import java.lang.reflect.Method

class ClockArchitectureCEffectTest {

    @After
    fun tearDown() {
        FakeEffectCalendarOom.lastOom = null
        FakeEffectCalendar.lastMillis = -1
        FakeEffectCalendar.lastPattern = null
    }

    // ------------------------------------------------------------------------
    // Controller field operations.
    // ------------------------------------------------------------------------

    @Test
    fun readCalendar_returnsCalendarObject() {
        val effect = defaultEffect()
        val controller = FakeEffectController().apply { mCalendar = FakeWrongCalendar() }

        val calendar = effect.readCalendar(controller)

        assertNotNull(calendar)
        assertSame(controller.mCalendar, calendar)
    }

    @Test
    fun readClockListeners_returnsListWhenValueIsList() {
        val effect = defaultEffect()
        val controller = FakeEffectController()

        val listeners = effect.readClockListeners(controller)

        assertNotNull(listeners)
        assertTrue(listeners is List<*>)
        assertEquals(0, listeners!!.size)
    }

    @Test
    fun readClockListeners_returnsNullWhenValueIsNotList() {
        val effect = effectWith(controllerClass = ArrayListenerController::class.java)
        val controller = ArrayListenerController()

        val listeners = effect.readClockListeners(controller)

        assertNull(listeners)
    }

    @Test
    fun writeIs24_primitiveBooleanField() {
        val effect = defaultEffect()
        val controller = FakeEffectController()

        assertTrue(effect.writeIs24(controller, true))
        assertTrue(controller.mIs24)

        assertTrue(effect.writeIs24(controller, false))
        assertFalse(controller.mIs24)
    }

    @Test
    fun writeIs24_nonPrimitiveField_returnsFalse() {
        val effect = effectWith(controllerClass = BoxedIs24Controller::class.java)
        val controller = BoxedIs24Controller()

        assertFalse(effect.writeIs24(controller, true))
    }

    // ------------------------------------------------------------------------
    // Target selection and controller read.
    // ------------------------------------------------------------------------

    @Test
    fun readController_perTargetField() {
        val statusController = Any()
        val clock = FakeEffectStatusBarClock().apply {
            mMiuiStatusBarClockController = statusController
        }

        val effect = effectWith(
            controller = controllerFor(FakeEffectController::class.java),
            targets = arrayOf(
                targetFor(FakeEffectMiuiClock::class.java),
                targetFor(FakeEffectStatusBarClock::class.java),
            ),
            calendar = calendarFor(FakeEffectCalendar::class.java),
        )

        val result = effect.readController(clock)

        assertSame(statusController, result)
    }

    @Test
    fun readController_mostSpecificTargetWins() {
        val baseController = Any()
        val statusController = Any()
        val clock = FakeEffectStatusBarClock().apply {
            mMiuiStatusBarClockController = statusController
        }

        val effect = effectWith(
            controller = controllerFor(FakeEffectController::class.java),
            targets = arrayOf(
                targetFor(FakeEffectClock::class.java),
                targetFor(FakeEffectStatusBarClock::class.java),
            ),
            calendar = calendarFor(FakeEffectCalendar::class.java),
        )

        val result = effect.readController(clock)

        assertSame(statusController, result)
    }

    @Test
    fun readController_incomparableTargetsFailsClosed() {
        val clock = FakeEffectAmbiguousClock()

        val effect = effectWith(
            controller = controllerFor(FakeEffectController::class.java),
            targets = arrayOf(
                ClockTargetCapability(
                    targetClass = Runnable::class.java,
                    controllerField = FakeEffectClock::class.java.getDeclaredField("mMiuiStatusBarClockController").also { it.isAccessible = true },
                    updateTimeMethod = FakeEffectClock::class.java.getDeclaredMethod("updateTime").also { it.isAccessible = true },
                ),
                ClockTargetCapability(
                    targetClass = Cloneable::class.java,
                    controllerField = FakeEffectClock::class.java.getDeclaredField("mMiuiStatusBarClockController").also { it.isAccessible = true },
                    updateTimeMethod = FakeEffectClock::class.java.getDeclaredMethod("updateTime").also { it.isAccessible = true },
                ),
            ),
            calendar = calendarFor(FakeEffectCalendar::class.java),
        )

        assertNull(effect.readController(clock))
        assertFalse(effect.invokeUpdateTime(clock))
    }

    // ------------------------------------------------------------------------
    // invokeUpdateTime.
    // ------------------------------------------------------------------------

    @Test
    fun invokeUpdateTime_callsFrozenMethod() {
        val clock = FakeEffectMiuiClock()
        val effect = defaultEffect()

        assertTrue(effect.invokeUpdateTime(clock))
        assertEquals(1, clock.updateTimeCalls)
    }

    @Test
    fun invokeUpdateTime_mostSpecificCallsSubclassMethod_baseFirst() {
        val clock = FakeEffectStatusBarClock()
        val effect = effectWith(
            controller = controllerFor(FakeEffectController::class.java),
            targets = arrayOf(
                targetFor(FakeEffectMiuiClock::class.java),
                targetFor(FakeEffectStatusBarClock::class.java),
            ),
            calendar = calendarFor(FakeEffectCalendar::class.java),
        )

        assertTrue(effect.invokeUpdateTime(clock))
        assertEquals(1, clock.updateTimeCalls)
        assertFalse(clock.parentCalled)
    }

    @Test
    fun invokeUpdateTime_mostSpecificCallsSubclassMethod_childFirst() {
        val clock = FakeEffectStatusBarClock()
        val effect = effectWith(
            controller = controllerFor(FakeEffectController::class.java),
            targets = arrayOf(
                targetFor(FakeEffectStatusBarClock::class.java),
                targetFor(FakeEffectMiuiClock::class.java),
            ),
            calendar = calendarFor(FakeEffectCalendar::class.java),
        )

        assertTrue(effect.invokeUpdateTime(clock))
        assertEquals(1, clock.updateTimeCalls)
        assertFalse(clock.parentCalled)
    }

    @Test
    fun invokeUpdateTime_duplicateTargetClassFailsClosed() {
        val clock = FakeEffectMiuiClock()
        val effect = effectWith(
            controller = controllerFor(FakeEffectController::class.java),
            targets = arrayOf(
                targetFor(FakeEffectMiuiClock::class.java),
                targetFor(FakeEffectMiuiClock::class.java),
            ),
            calendar = calendarFor(FakeEffectCalendar::class.java),
        )

        assertFalse(effect.invokeUpdateTime(clock))
    }

    @Test
    fun readController_orderIndependentAndDuplicateFailsClosed() {
        val clock = FakeEffectStatusBarClock().apply {
            mMiuiStatusBarClockController = Any()
        }

        val baseFirst = effectWith(
            controller = controllerFor(FakeEffectController::class.java),
            targets = arrayOf(
                targetFor(FakeEffectClock::class.java),
                targetFor(FakeEffectStatusBarClock::class.java),
            ),
            calendar = calendarFor(FakeEffectCalendar::class.java),
        )

        val childFirst = effectWith(
            controller = controllerFor(FakeEffectController::class.java),
            targets = arrayOf(
                targetFor(FakeEffectStatusBarClock::class.java),
                targetFor(FakeEffectClock::class.java),
            ),
            calendar = calendarFor(FakeEffectCalendar::class.java),
        )

        assertSame(clock.mMiuiStatusBarClockController, baseFirst.readController(clock))
        assertSame(clock.mMiuiStatusBarClockController, childFirst.readController(clock))

        val duplicate = effectWith(
            controller = controllerFor(FakeEffectController::class.java),
            targets = arrayOf(
                targetFor(FakeEffectMiuiClock::class.java),
                targetFor(FakeEffectMiuiClock::class.java),
            ),
            calendar = calendarFor(FakeEffectCalendar::class.java),
        )

        assertNull(duplicate.readController(clock))
    }

    @Test
    fun invokeUpdateTime_fatalUnwrappedWithExactIdentity() {
        val oom = OutOfMemoryError("updateTime OOM")
        val clock = FakeEffectClockThrowing(oom)
        val effect = defaultEffect()

        try {
            effect.invokeUpdateTime(clock)
            fail("expected OutOfMemoryError")
        } catch (thrown: OutOfMemoryError) {
            assertSame(oom, thrown)
        }
    }

    // ------------------------------------------------------------------------
    // setTimeInMillis.
    // ------------------------------------------------------------------------

    @Test
    fun setTimeInMillis_callsFrozenMethod() {
        val calendar = FakeEffectCalendar()
        val effect = defaultEffect()

        assertTrue(effect.setTimeInMillis(calendar, 1234567890L))
        assertEquals(1234567890L, FakeEffectCalendar.lastMillis)
    }

    @Test
    fun setTimeInMillis_wrongCalendarClass_returnsFalse() {
        val effect = defaultEffect()

        assertFalse(effect.setTimeInMillis(FakeWrongCalendar(), 1234567890L))
    }

    @Test
    fun setTimeInMillis_fatalUnwrappedWithExactIdentity() {
        val oom = OutOfMemoryError("setTime OOM")
        val calendar = FakeEffectCalendarOom(oom)
        val effect = defaultEffect()

        try {
            effect.setTimeInMillis(calendar, 0L)
            fail("expected OutOfMemoryError")
        } catch (thrown: OutOfMemoryError) {
            assertSame(oom, thrown)
        }
    }

    // ------------------------------------------------------------------------
    // format.
    // ------------------------------------------------------------------------

    @Test
    fun format_callsFrozenMethod() {
        val calendar = FakeEffectCalendar()
        val effect = defaultEffect()
        val out = StringBuilder()
        val pattern = StringBuilder("HH:mm")

        assertTrue(effect.format(calendar, FakeEffectContext(), out, pattern))
        assertEquals("HH:mm", out.toString())
        assertSame(pattern, FakeEffectCalendar.lastPattern)
    }

    @Test
    fun format_wrongCalendarClass_returnsFalse() {
        val effect = defaultEffect()

        assertFalse(effect.format(FakeWrongCalendar(), FakeEffectContext(), StringBuilder(), StringBuilder()))
    }

    @Test
    fun format_nonFatalTargetException_returnsFalse() {
        val calendar = FakeEffectCalendarRuntimeException()
        val effect = defaultEffect()

        assertFalse(effect.format(calendar, FakeEffectContext(), StringBuilder(), StringBuilder()))
    }

    @Test
    fun format_fatalUnwrappedWithExactIdentity() {
        val oom = OutOfMemoryError("format OOM")
        val calendar = FakeEffectCalendarOom(oom)
        val effect = defaultEffect()

        try {
            effect.format(calendar, FakeEffectContext(), StringBuilder(), StringBuilder())
            fail("expected OutOfMemoryError")
        } catch (thrown: OutOfMemoryError) {
            assertSame(oom, thrown)
        }
    }

    // ------------------------------------------------------------------------
    // Helpers.
    // ------------------------------------------------------------------------

    private fun defaultEffect(): ClockEffect {
        return effectWith(
            controller = controllerFor(FakeEffectController::class.java),
            targets = arrayOf(targetFor(FakeEffectMiuiClock::class.java)),
            calendar = calendarFor(FakeEffectCalendar::class.java),
        )
    }

    private fun effectWith(controller: ControllerCapability, targets: Array<ClockTargetCapability>, calendar: CalendarCapability): ClockEffect {
        val abi = ClockAbi(controller, targets, calendar)
        return ClockEffect(abi, calendar)
    }

    private fun effectWith(controllerClass: Class<*> = FakeEffectController::class.java): ClockEffect {
        return effectWith(
            controller = controllerFor(controllerClass),
            targets = arrayOf(targetFor(FakeEffectMiuiClock::class.java)),
            calendar = calendarFor(FakeEffectCalendar::class.java),
        )
    }

    private fun controllerFor(controllerClass: Class<*>): ControllerCapability {
        return ControllerCapability(
            controllerClass = controllerClass,
            calendarField = fieldFor(controllerClass, "mCalendar"),
            clockListenersField = fieldFor(controllerClass, "mClockListeners"),
            is24Field = fieldFor(controllerClass, "mIs24"),
        )
    }

    private fun fieldFor(clazz: Class<*>, name: String): Field {
        return clazz.getDeclaredField(name).also { it.isAccessible = true }
    }

    private fun targetFor(targetClass: Class<*>): ClockTargetCapability {
        return ClockResolver.resolveClockTargetClass(targetClass)!!
    }

    private fun calendarFor(calendarClass: Class<*>): CalendarCapability {
        return ClockResolver.resolveCalendarFromDeclaredType(calendarClass, Context::class.java)!!
    }

    // ------------------------------------------------------------------------
    // Fake classes.
    // ------------------------------------------------------------------------

    open class FakeEffectController {
        @JvmField
        var mCalendar: Any = FakeEffectCalendar()

        @JvmField
        var mClockListeners: ArrayList<Any> = ArrayList()

        @JvmField
        var mIs24: Boolean = false
    }

    open class BoxedIs24Controller {
        @JvmField
        var mCalendar: Any = FakeEffectCalendar()

        @JvmField
        var mClockListeners: ArrayList<Any> = ArrayList()

        @JvmField
        var mIs24: Boolean? = false
    }

    open class ArrayListenerController {
        @JvmField
        var mCalendar: Any = FakeEffectCalendar()

        @JvmField
        var mClockListeners: Array<Any>? = emptyArray()

        @JvmField
        var mIs24: Boolean = false
    }

    open class FakeEffectClock {
        @JvmField
        var mMiuiStatusBarClockController: Any = FakeEffectController()

        open fun updateTime() {
            (this as? FakeEffectMiuiClock)?.parentCalled = true
        }
    }

    open class FakeEffectMiuiClock : FakeEffectClock() {
        var updateTimeCalls: Int = 0
        var parentCalled: Boolean = false

        override fun updateTime() {
            updateTimeCalls++
            super.updateTime()
        }
    }

    open class FakeEffectStatusBarClock : FakeEffectMiuiClock() {
        override fun updateTime() {
            updateTimeCalls++
            // Do not call super; the test proves the most-specific subclass method is used.
        }
    }

    class FakeEffectAmbiguousClock : FakeEffectClock(), Runnable, Cloneable {
        override fun run() {}
    }

    open class FakeEffectCalendar {
        companion object {
            @JvmField
            var lastMillis: Long = -1

            @JvmField
            var lastPattern: StringBuilder? = null
        }

        open fun setTimeInMillis(millis: Long) {
            lastMillis = millis
        }

        open fun format(ctx: Context, out: StringBuilder, pattern: StringBuilder) {
            lastPattern = pattern
            out.append(pattern)
        }
    }

    open class FakeEffectCalendarOom(private val oom: OutOfMemoryError) : FakeEffectCalendar() {
        companion object {
            @JvmField
            var lastOom: OutOfMemoryError? = null
        }

        init {
            lastOom = oom
        }

        override fun setTimeInMillis(millis: Long) {
            throw oom
        }

        override fun format(ctx: Context, out: StringBuilder, pattern: StringBuilder) {
            throw oom
        }
    }

    open class FakeEffectCalendarRuntimeException : FakeEffectCalendar() {
        override fun format(ctx: Context, out: StringBuilder, pattern: StringBuilder) {
            throw RuntimeException("format failure")
        }
    }

    open class FakeWrongCalendar

    class FakeEffectClockThrowing(private val oom: OutOfMemoryError) : FakeEffectMiuiClock() {
        override fun updateTime() {
            throw oom
        }
    }

    class FakeEffectContext : ContextWrapper(null)
}
