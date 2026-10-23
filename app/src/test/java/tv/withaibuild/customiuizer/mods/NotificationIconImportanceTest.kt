package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import org.junit.Assert.*
import org.junit.Test

class NotificationIconImportanceTest {
    class Entry(private val value: Int) { fun getImportance() = value }
    class ListEntry(private val entry: Entry?) { fun getRepresentativeEntry() = entry }
    class Controller(@JvmField var mNotificationEntries: List<ListEntry>)
    private val effect = NotificationIconImportance(Controller::class.java, ListEntry::class.java, Entry::class.java)

    @Test fun filteringIsLimitedToTheStatusBarCallAndIsRestoredAfterFailure() {
        val low = ListEntry(Entry(1)); val high = ListEntry(Entry(4))
        val original = listOf(low, high)
        val owner = Controller(original)
        val failure = OutOfMemoryError("native update failed")
        val chain = Chain(owner) {
            assertEquals(listOf(high), owner.mNotificationEntries)
            throw failure
        }
        try { effect.intercept(chain); fail("Expected failure") }
        catch (actual: Throwable) { assertSame(failure, actual) }
        assertEquals(1, chain.calls)
        assertSame(original, owner.mNotificationEntries)
    }

    @Test fun aNewNativeListIsNeverOverwrittenByTheTemporaryFilter() {
        val owner = Controller(listOf(ListEntry(Entry(1))))
        val replacement = listOf(ListEntry(Entry(4)))
        val chain = Chain(owner) { owner.mNotificationEntries = replacement; "ok" }
        assertEquals("ok", effect.intercept(chain))
        assertSame(replacement, owner.mNotificationEntries)
        assertEquals(1, chain.calls)
    }

    @Test fun unchangedAndUnknownEntriesReuseTheOriginalListAndNestedCallsRestoreCorrectly() {
        val original = listOf(ListEntry(Entry(4)), ListEntry(null))
        val owner = Controller(original)
        effect.intercept(Chain(owner) { assertSame(original, owner.mNotificationEntries) })
        val full = original + ListEntry(Entry(1))
        owner.mNotificationEntries = full
        effect.intercept(Chain(owner) {
            val temporary = owner.mNotificationEntries
            effect.intercept(Chain(owner) { assertSame(temporary, owner.mNotificationEntries) })
            assertSame(temporary, owner.mNotificationEntries)
        })
        assertSame(full, owner.mNotificationEntries)
    }

    private class Chain(private val owner: Controller, private val action: () -> Any?) : XposedInterface.Chain {
        var calls = 0
        override fun getExecutable(): Executable = error("unused")
        override fun getThisObject(): Any = owner
        override fun getArgs(): List<Any?> = emptyList()
        override fun getArg(index: Int): Any? = error("unused")
        override fun proceed(): Any? { calls++; return action() }
        override fun proceed(args: Array<Any?>): Any? = error("unused")
        override fun proceedWith(p0: Any): Any? = error("unused")
        override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("unused")
    }
}
