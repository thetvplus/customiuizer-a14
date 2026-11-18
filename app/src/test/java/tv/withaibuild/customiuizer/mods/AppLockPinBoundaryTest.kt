package tv.withaibuild.customiuizer.mods

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.testsupport.repositorySource
import java.lang.reflect.Executable
import java.lang.reflect.InvocationTargetException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppLockPinBoundaryTest {
    @Test fun completeKeypadRetainsDigitsClicksAndBothBottomControls() {
        val pin = keypad(); val chain = Chain(pin)
        assertSame(chain.result, SystemLockScreenHooks.createScrambleAppLockPinHook().intercept(chain))
        assertEquals(1, chain.calls)
        assertEquals(listOf(1, 1, 1, 1), pin.rows.map { it.removals })
        assertSame(pin.left, pin.rows[3].getChildAt(0))
        assertSame(pin.right, pin.rows[3].getChildAt(2))
        val moved = ArrayList<View>()
        for (row in 0..3) for (column in 0..2) {
            assertEquals(3, pin.rows[row].childCount)
            if (row == 3 && column != 1) continue
            val key = pin.rows[row].getChildAt(column)
            moved.add(key); assertTrue(key.performClick())
        }
        assertEquals(pin.digits.toSet(), moved.toSet())
        assertEquals(10, moved.toSet().size)
        assertEquals((0..9).toSet(), pin.clicks.toSet())
    }

    @Test fun nativeConstructorFailureCannotReadOrChangeTheKeypad() {
        val pin = keypad(); val before = snapshot(pin)
        val error = IllegalStateException("native constructor failed"); val chain = Chain(pin, nativeFailure = error)
        assertSame(error, assertThrows(IllegalStateException::class.java) {
            SystemLockScreenHooks.createScrambleAppLockPinHook().intercept(chain)
        })
        assertEquals(1, chain.calls); assertEquals(0, chain.ownerReads)
        assertUnchanged(pin, before)
    }

    @Test fun nativeFatalErrorEscapesBeforeReadingTheUnfinishedOwner() {
        val pin = keypad(); val before = snapshot(pin)
        val error = OutOfMemoryError("native fatal"); val chain = Chain(pin, nativeFailure = error)
        assertSame(error, assertThrows(OutOfMemoryError::class.java) {
            SystemLockScreenHooks.createScrambleAppLockPinHook().intercept(chain)
        })
        assertEquals(1, chain.calls); assertEquals(0, chain.ownerReads)
        assertUnchanged(pin, before)
    }

    @Test fun anIncompleteRootCannotLeaveItsEarlierRowsEmpty() {
        val pin = keypad().apply { removeView(rows[3]) }
        assertSkippedWithoutMutation(pin)
    }

    @Test fun aMissingBottomDigitCannotMoveTheFixedControlsIntoTheNumbers() {
        val pin = keypad().apply { rows[3].removeViewAt(1) }
        assertSkippedWithoutMutation(pin)
    }

    @Test fun aLaterNonRowChildCannotClearTheEarlierRows() {
        val pin = keypad().apply { removeView(rows[2]); addView(View(context), 2) }
        assertSkippedWithoutMutation(pin)
    }

    @Test fun additionalNativeRowControlsCannotBeDiscarded() {
        val pin = keypad().apply { rows[1].addView(View(context)) }
        assertSkippedWithoutMutation(pin)
    }

    @Test fun aRootFooterOutsideTheFourRowsIsPreserved() {
        val pin = keypad(); val footer = View(pin.context); pin.addView(footer)
        val chain = Chain(pin)
        assertSame(chain.result, SystemLockScreenHooks.createScrambleAppLockPinHook().intercept(chain))
        assertSame(footer, pin.getChildAt(4)); assertEquals(5, pin.childCount)
        assertEquals(listOf(1, 1, 1, 1), pin.rows.map { it.removals })
        assertSame(pin.left, pin.rows[3].getChildAt(0)); assertSame(pin.right, pin.rows[3].getChildAt(2))
    }

    @Test fun ordinaryModuleFailureRetainsTheNativeResult() {
        val chain = Chain(keypad(), ownerFailure = IllegalStateException("owner unavailable"))
        assertSame(chain.result, SystemLockScreenHooks.createScrambleAppLockPinHook().intercept(chain))
        assertEquals(1, chain.calls)
    }

    @Test fun directModuleFatalErrorRetainsItsIdentity() {
        val fatal = OutOfMemoryError("module fatal"); val chain = Chain(keypad(), ownerFailure = fatal)
        assertSame(fatal, assertThrows(OutOfMemoryError::class.java) {
            SystemLockScreenHooks.createScrambleAppLockPinHook().intercept(chain)
        })
        assertEquals(1, chain.calls)
    }

    @Test fun wrappedModuleFatalErrorStillEscapesTheExistingLogger() {
        val fatal = OutOfMemoryError("wrapped fatal"); val chain = Chain(keypad(), ownerFailure = InvocationTargetException(fatal))
        assertSame(fatal, assertThrows(OutOfMemoryError::class.java) {
            SystemLockScreenHooks.createScrambleAppLockPinHook().intercept(chain)
        })
        assertEquals(1, chain.calls)
    }

    @Test fun theConstructorInstallerUsesTheTestedCallback() {
        val source = repositorySource("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt")
            .replace("\r\n", "\n")
        val start = source.indexOf("fun ScrambleAppLockPINHook(")
        val end = source.indexOf("internal fun createScrambleAppLockPinHook()", start)
        assertTrue(start >= 0 && end > start)
        assertTrue(source.substring(start, end).contains("lpparam.classLoader, createScrambleAppLockPinHook())"))
    }

    private fun assertSkippedWithoutMutation(pin: Keypad) {
        val before = snapshot(pin); val chain = Chain(pin)
        assertSame(chain.result, SystemLockScreenHooks.createScrambleAppLockPinHook().intercept(chain))
        assertEquals(1, chain.calls); assertUnchanged(pin, before)
    }
    private data class Snapshot(val root: List<View>, val children: List<List<View>>, val removals: List<Int>)
    private fun children(group: LinearLayout) = (0 until group.childCount).map { group.getChildAt(it) }
    private fun snapshot(pin: Keypad) = Snapshot(children(pin), pin.rows.map(::children), pin.rows.map { it.removals })
    private fun assertUnchanged(pin: Keypad, before: Snapshot) {
        assertEquals(before.root, children(pin))
        assertEquals("Every native child and position remains", before.children, pin.rows.map(::children))
        assertEquals("No native row was detached before complete preparation", before.removals, pin.rows.map { it.removals })
    }
    private fun keypad() = Keypad(RuntimeEnvironment.getApplication())

    private class Row(context: Context) : LinearLayout(context) {
        var removals = 0
        override fun removeAllViews() { removals++; super.removeAllViews() }
    }
    private class Keypad(context: Context) : LinearLayout(context) {
        val rows = List(4) { Row(context) }
        val clicks = ArrayList<Int>()
        val digits = List(10) { digit -> TextView(context).apply { text = digit.toString(); setOnClickListener { clicks.add(digit) } } }
        val left = View(context); val right = View(context)
        init {
            orientation = VERTICAL; rows.forEach(::addView)
            for (row in 0..2) for (column in 0..2) rows[row].addView(digits[row * 3 + column + 1])
            rows[3].addView(left); rows[3].addView(digits[0]); rows[3].addView(right)
        }
    }
    private class Chain(private val owner: Any, private val nativeFailure: Throwable? = null, private val ownerFailure: Throwable? = null) : XposedInterface.Chain {
        val result = Any(); var calls = 0; var ownerReads = 0
        override fun getExecutable(): Executable = error("No reflective chain access")
        override fun getThisObject(): Any { ownerReads++; ownerFailure?.let { throw it }; return owner }
        override fun getArgs(): List<Any?> = error("No argument copies")
        override fun getArg(index: Int): Any? = error("No argument reads")
        override fun proceed(): Any? { calls++; nativeFailure?.let { throw it }; return result }
        override fun proceed(args: Array<Any?>): Any? = error("No argument rewriting")
        override fun proceedWith(receiver: Any): Any? = error("No receiver rewriting")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("No receiver rewriting")
    }
}
