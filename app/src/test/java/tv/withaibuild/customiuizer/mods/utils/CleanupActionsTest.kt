package tv.withaibuild.customiuizer.mods.utils

import java.lang.reflect.InvocationTargetException
import org.junit.Assert.*
import org.junit.Test

class CleanupActionsTest {
    @Test fun failuresNeverSkipIndependentReleases() {
        val first=IllegalStateException("release")
        val cleanup=CleanupActions(); var released=false
        cleanup.run { throw first }; cleanup.run { released=true }
        assertSame(first, assertThrows(IllegalStateException::class.java) { cleanup.finish() })
        assertTrue(released)
    }
    @Test fun wrappedFatalWinsAfterRollbackAndRetainsOriginalFailure() {
        val primary=IllegalArgumentException("initialize")
        val fatal=OutOfMemoryError("release"); var released=false
        val cleanup=CleanupActions(primary)
        cleanup.run { throw InvocationTargetException(fatal) }; cleanup.run { released=true }
        assertSame(fatal, assertThrows(OutOfMemoryError::class.java) { cleanup.finish() })
        assertTrue(released); assertTrue(fatal.suppressed.contains(primary))
    }
}
