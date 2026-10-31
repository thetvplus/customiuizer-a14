package tv.withaibuild.customiuizer.utils

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DocumentOperationTest {
    @Test fun cancellationKeepsTheSlotUntilIoClosesAndNeverDeliversSuccess() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var delivered = false
        val callerThread = Thread.currentThread()
        val operation = launch {
            BackupRestore.runDocumentOperation {
                assertNotSame(callerThread, Thread.currentThread())
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                true
            }
            delivered = true
        }
        yield()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            operation.cancel()
            assertNull(BackupRestore.runDocumentOperation { error("Overlapping write") })
        } finally {
            release.countDown()
            operation.join()
        }
        assertFalse(delivered)
        assertEquals(true, BackupRestore.runDocumentOperation { true })
    }

    @Test fun failedIoReleasesSlot() = runBlocking {
        try {
            BackupRestore.runDocumentOperation<Boolean> { throw java.io.IOException("Synthetic provider failure") }
            fail("Expected IO failure")
        } catch (_: java.io.IOException) { }
        assertEquals(true, BackupRestore.runDocumentOperation { true })
    }
}
