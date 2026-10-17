package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

class SystemNotificationReviewTest {
    interface PreferenceListener {
        fun onPreferenceChange(preference: Any?, value: Any?): Boolean
    }

    private fun listener(update: (Int) -> Unit): PreferenceListener =
        Proxy.newProxyInstance(
            PreferenceListener::class.java.classLoader,
            arrayOf(PreferenceListener::class.java),
            SystemNotificationHooks.ImportanceChangeHandler(update),
        ) as PreferenceListener

    @Test
    fun allFourImportanceChoicesReachTheBackendExactlyOnce() {
        val changes = mutableListOf<Int>()
        val listener = listener { changes.add(it) }
        for (value in 1..4) assertTrue(listener.onPreferenceChange(null, value.toString()))
        assertEquals(listOf(1, 2, 3, 4), changes)
    }

    @Test
    fun invalidImportanceCannotDisableOrRewriteAChannel() {
        var writes = 0
        val listener = listener { writes++ }
        for (value in listOf(null, "", " ", "abc", "0", "-1", "5", "2147483648", 2, true)) {
            assertFalse("Rejected value: $value", listener.onPreferenceChange(null, value))
        }
        assertEquals(0, writes)
    }

    @Test
    fun ordinaryBackendFailureIsRejectedWithoutEscapingTheSettingsListener() {
        var writes = 0
        val listener = listener {
            writes++
            throw InvocationTargetException(IllegalStateException("backend unavailable"))
        }
        assertFalse(listener.onPreferenceChange(null, "2"))
        assertEquals(1, writes)
    }

    @Test
    fun fatalBackendFailuresAreUnwrappedAndPropagated() {
        for (fatal in listOf(OutOfMemoryError("oom"), StackOverflowError("vm"), ThreadDeath())) {
            val listener = listener { throw InvocationTargetException(fatal) }
            try {
                listener.onPreferenceChange(null, "2")
                throw AssertionError("Fatal error was swallowed")
            } catch (actual: Throwable) {
                assertSame(fatal, actual)
            }
        }
    }

    @Test
    fun proxyObjectMethodsDoNotChangeNotificationImportance() {
        var writes = 0
        val listener = listener { writes++ }
        assertTrue(listener == listener)
        assertFalse(listener.equals(Any()))
        assertEquals(java.lang.System.identityHashCode(listener), listener.hashCode())
        assertTrue(listener.toString().contains("NotificationImportanceListener"))
        assertEquals(0, writes)
    }

    @Test
    fun regularChannelUsesTheChannelRoute() {
        assertTrue(SystemNotificationHooks.shouldOpenNotificationChannel("shell_cmd", false))
        assertTrue(SystemNotificationHooks.shouldOpenNotificationChannel("messages", false))
    }

    @Test
    fun missingAndLegacyChannelsKeepTheOriginalClick() {
        for (id in listOf(null, "", " ", "miscellaneous")) {
            assertFalse(SystemNotificationHooks.shouldOpenNotificationChannel(id, false))
        }
    }

    @Test
    fun hybridNotificationsKeepTheirRomSpecificSettingsRoute() {
        assertFalse(SystemNotificationHooks.shouldOpenNotificationChannel("messages", true))
    }
}
