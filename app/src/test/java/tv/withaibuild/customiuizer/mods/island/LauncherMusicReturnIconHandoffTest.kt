package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test

class LauncherMusicReturnIconHandoffTest {
    private val handoff = LauncherMusicReturnIconHandoff()
    private val owner = Any()
    private val copy = Any()
    private val original = Any()

    @Test fun onlyTheSynchronousMatchingNativeIconSetupReceivesOriginalParams() {
        assertSame(copy, handoff.nativeParams(owner, copy))
        val result = handoff.during(owner, copy, original) {
            assertSame(original, handoff.nativeParams(owner, copy))
            assertSame(copy, handoff.nativeParams(Any(), copy))
            val another = Any()
            assertSame(another, handoff.nativeParams(owner, another))
            "native result"
        }
        assertEquals("native result", result)
        assertSame(copy, handoff.nativeParams(owner, copy))
    }

    @Test fun replacementSetupAndExceptionsRestoreTheOuterCallThenReleaseEverything() {
        handoff.during(owner, copy, original) {
            val nextOwner = Any(); val nextCopy = Any(); val nextOriginal = Any()
            try {
                handoff.during(nextOwner, nextCopy, nextOriginal) {
                    assertSame(nextOriginal, handoff.nativeParams(nextOwner, nextCopy))
                    assertSame(copy, handoff.nativeParams(owner, copy))
                    throw IllegalStateException("native failure")
                }
                fail("Native failure must propagate")
            } catch (expected: IllegalStateException) {
                assertEquals("native failure", expected.message)
            }
            assertSame(original, handoff.nativeParams(owner, copy))
        }
        assertSame(copy, handoff.nativeParams(owner, copy))
    }

    @Test fun anUnrelatedThreadCannotBorrowAnInFlightWindow() {
        handoff.during(owner, copy, original) {
            var observed: Any? = null
            val thread = Thread { observed = handoff.nativeParams(owner, copy) }
            thread.start(); thread.join()
            assertSame(copy, observed)
        }
    }
}
