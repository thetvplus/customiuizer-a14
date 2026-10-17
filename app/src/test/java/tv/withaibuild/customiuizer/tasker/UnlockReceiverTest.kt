package tv.withaibuild.customiuizer.tasker

import android.app.Application
import android.content.Intent
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UnlockReceiverTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun unrelatedActionsAndUnidentifiedSendersNeverUnparcelExtras() {
        for (action in listOf(null, "unrelated", Constants.ACTION_FIRE_SETTING)) {
            val intent = object : Intent(action) {
                var reads = 0
                override fun getBundleExtra(name: String): Bundle? {
                    reads++
                    throw IllegalArgumentException("untrusted extras")
                }
            }
            UnlockReceiver().onReceive(context, intent)
            assertEquals(0, intent.reads)
        }
    }

    @Test
    fun ordinaryMalformedIntentIsIsolated() {
        UnlockReceiver().onReceive(context, failingIntent(IllegalArgumentException("fixture")))
    }

    @Test
    fun fatalErrorsEscapeTheReceiverBoundary() {
        for (failure in listOf(OutOfMemoryError("fixture"), ThreadDeath(), InternalError("fixture"))) {
            val actual = assertThrows(failure.javaClass) {
                UnlockReceiver().onReceive(context, failingIntent(failure))
            }
            assertSame(failure, actual)
        }
    }

    private fun failingIntent(failure: Throwable): Intent = object : Intent() {
        override fun getAction(): String? = throw failure
    }
}
