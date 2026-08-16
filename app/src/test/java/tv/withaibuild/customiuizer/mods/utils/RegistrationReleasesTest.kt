package tv.withaibuild.customiuizer.mods.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RegistrationReleasesTest {

    @Before
    fun setup() {
        HookDiagnostics.reset()
        HookDiagnostics.currentProcessName = "test-process"
    }

    @After
    fun tearDown() {
        HookDiagnostics.reset()
        HookDiagnostics.currentProcessName = null
    }

    @Test
    fun successfulRelease() {
        val target = FakeReleaseTarget()
        releaseRegistrationSilently(target, "release", "arg", "tag")
        assertTrue(target.released)
        assertEquals(0, HookDiagnostics.snapshot().size)
    }

    @Test
    fun targetNullIsSilentlySkipped() {
        releaseRegistrationSilently(null, "release", "arg", "tag")
        val records = HookDiagnostics.snapshot()
        assertEquals(1, records.size)
        assertEquals(HookDiagnostics.Status.SILENTLY_SKIPPED, records[0].status)
    }

    @Test
    fun missingMethodIsRecorded() {
        val target = FakeReleaseTarget()
        releaseRegistrationSilently(target, "missingMethod", "arg", "tag")
        val records = HookDiagnostics.snapshot()
        assertEquals(1, records.size)
        assertEquals(HookDiagnostics.Status.TARGET_MEMBER_MISSING, records[0].status)
    }

    @Test
    fun releaseFailureIsRecorded() {
        val target = FakeReleaseTarget()
        releaseRegistrationSilently(target, "explodingRelease", "arg", "tag")
        val records = HookDiagnostics.snapshot()
        assertEquals(1, records.size)
        assertEquals(HookDiagnostics.Status.RECEIVER_UNREGISTER_FAILED, records[0].status)
    }

    @Test(expected = OutOfMemoryError::class)
    fun fatalErrorPropagates() {
        val target = FakeReleaseTarget()
        releaseRegistrationSilently(target, "fatalRelease", "arg", "tag")
    }

    class FakeReleaseTarget {
        var released = false

        fun release(arg: Any?) {
            released = true
        }

        fun explodingRelease(arg: Any?) {
            throw IllegalStateException("boom")
        }

        fun fatalRelease(arg: Any?) {
            throw OutOfMemoryError("fatal")
        }
    }
}
