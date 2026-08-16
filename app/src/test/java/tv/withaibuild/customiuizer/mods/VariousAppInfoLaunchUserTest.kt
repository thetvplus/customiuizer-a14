package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.InvocationTargetException

class VariousAppInfoLaunchUserTest {

    @Test
    fun resolveAppInfoLaunchUserId_returnsResolvedSecondaryUser() {
        assertEquals(10, Various.resolveAppInfoLaunchUserId(1_012_345) { 10 })
    }

    @Test
    fun resolveAppInfoLaunchUserId_returnsResolvedOwnerUserZero() {
        assertEquals(0, Various.resolveAppInfoLaunchUserId(0) { 0 })
    }

    @Test
    fun resolveAppInfoLaunchUserId_rejectsMissingUid() {
        var calls = 0
        assertNull(Various.resolveAppInfoLaunchUserId(-1) { calls++; 0 })
        assertEquals(0, calls)
    }

    @Test
    fun resolveAppInfoLaunchUserId_rejectsNegativeUid() {
        var calls = 0
        assertNull(Various.resolveAppInfoLaunchUserId(-10_000) { calls++; 0 })
        assertEquals(0, calls)
    }

    @Test
    fun resolveAppInfoLaunchUserId_returnsNullOnNonFatalResolverFailure() {
        assertNull(Various.resolveAppInfoLaunchUserId(100) { throw RuntimeException("no such method") })
    }

    @Test(expected = OutOfMemoryError::class)
    fun resolveAppInfoLaunchUserId_propagatesDirectFatalError() {
        Various.resolveAppInfoLaunchUserId(100) { throw OutOfMemoryError("oom") }
    }

    @Test(expected = OutOfMemoryError::class)
    fun resolveAppInfoLaunchUserId_propagatesWrappedFatalError() {
        Various.resolveAppInfoLaunchUserId(100) {
            throw InvocationTargetException(OutOfMemoryError("wrapped oom"))
        }
    }
}
