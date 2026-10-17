package tv.withaibuild.customiuizer.tasker

import android.content.ContextWrapper
import android.content.pm.PackageManager
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class UnlockTokenFailureBoundaryTest {

    private val provider = UnlockTokenProvider()

    @Test
    fun unavailablePackageManagerReturnsNoHostInfo() {
        assertNull(provider.getHostInfo(ThrowingPackageContext(IllegalStateException("unavailable")), "com.host"))
    }

    @Test
    fun linkageFailureReturnsNoHostInfo() {
        assertNull(provider.getHostInfo(ThrowingPackageContext(NoClassDefFoundError("unavailable")), "com.host"))
    }

    @Test
    fun outOfMemoryEscapesHostLookup() {
        assertFatalEscapes(OutOfMemoryError("fixture"))
    }

    @Test
    fun threadDeathEscapesHostLookup() {
        assertFatalEscapes(ThreadDeath())
    }

    @Test
    fun virtualMachineErrorEscapesHostLookup() {
        assertFatalEscapes(InternalError("fixture"))
    }

    private fun assertFatalEscapes(failure: Error) {
        try {
            provider.getHostInfo(ThrowingPackageContext(failure), "com.host")
            fail("Fatal failure must escape host lookup")
        } catch (actual: Throwable) {
            assertSame(failure, actual)
        }
    }

    private class ThrowingPackageContext(private val failure: Throwable) : ContextWrapper(null) {
        override fun getPackageManager(): PackageManager = throw failure
    }
}
