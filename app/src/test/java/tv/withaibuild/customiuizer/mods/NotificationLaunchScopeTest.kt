package tv.withaibuild.customiuizer.mods

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class NotificationLaunchScopeTest {
    @Test fun normalAndMismatchedLaunchesCannotConsumeFloatingOptions() {
        val scope = NotificationLaunchScope<String>()
        assertNull(scope.consumeIf { true })
        scope.within("notification-a") {
            assertNull(scope.consumeIf { it == "unrelated-pending-intent" })
            scope.within(null) { assertNull(scope.consumeIf { true }) }
            assertEquals("notification-a", scope.consumeIf { true })
            assertNull(scope.consumeIf { true })
        }
        assertNull(scope.consumeIf { true })
    }

    @Test fun nestedNotificationsRestoreTheirOwnTransaction() {
        val scope = NotificationLaunchScope<String>()
        scope.within("a") {
            scope.within("b") { assertEquals("b", scope.consumeIf { true }) }
            assertEquals("a", scope.consumeIf { true })
        }
        assertNull(scope.consumeIf { true })
    }

    @Test fun failuresIncludingFatalErrorsAlwaysClearTheScope() {
        val scope = NotificationLaunchScope<String>()
        for (failure in listOf(IllegalStateException(), OutOfMemoryError(), ThreadDeath())) {
            try {
                scope.within("a") { throw failure }
                fail("Expected the original failure")
            } catch (actual: Throwable) { assertSame(failure, actual) }
            assertNull(scope.consumeIf { true })
        }
    }

    @Test fun concurrentLaunchesOnDifferentThreadsNeverShareOptions() {
        val scope = NotificationLaunchScope<String>()
        val entered = CountDownLatch(2)
        val finished = CountDownLatch(2)
        val error = AtomicReference<Throwable?>()
        for (name in listOf("a", "b")) Thread {
            try {
                scope.within(name) {
                    entered.countDown()
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    assertEquals(name, scope.consumeIf { true })
                    assertNull(scope.consumeIf { true })
                }
                assertNull(scope.consumeIf { true })
            } catch (t: Throwable) { error.compareAndSet(null, t) }
            finally { finished.countDown() }
        }.start()
        assertTrue(finished.await(10, TimeUnit.SECONDS))
        error.get()?.let { throw it }
        assertNull(scope.consumeIf { true })
    }

    @Test fun tileRequestsAreBoundToComponentCallerUserAndADeadline() {
        var time = 100L
        val request = FreeformRequest { time }
        request.replace("app", 0, "app/Activity")
        assertFalse(request.consume("app", 10, "app/Activity", "com.android.systemui"))
        assertFalse(request.consume("app", 0, "app/Other", "com.android.systemui"))
        assertFalse(request.consume("app", 0, "app/Activity", "another.app"))
        assertTrue(request.consume("app", 0, "app/Activity", "com.android.systemui"))
        assertFalse(request.consume("app", 0, "app/Activity", "com.android.systemui"))
        request.replace("app", 0, "app/Activity")
        time += 5000
        assertFalse(request.consume("app", 0, "app/Activity", "com.android.systemui"))
        request.replace("app", 0, "app/Activity")
        request.replace("other", 0, "other/Activity")
        assertFalse(request.consume("app", 0, "app/Activity", "com.android.systemui"))
        assertTrue(request.consume("other", 0, "other/Activity", "com.android.systemui"))
        request.replace("app", -1, "app/Activity")
        assertFalse(request.consume("app", 0, "app/Activity", "com.android.systemui"))
    }

    @Test fun soundAndVibrationMaskPreservesLightsAndUnknownFlags() {
        for (flags in 0..255) {
            assertEquals(flags, NotificationAlertHook.alertFlags(flags, false))
            assertEquals(flags and 3.inv(), NotificationAlertHook.alertFlags(flags, true))
            assertEquals(flags and 4, NotificationAlertHook.alertFlags(flags, true) and 4)
        }
    }

    @Test fun autoDismissOnlyRelaxesExpandedPinnedStickiness() {
        for (bits in 0..31) {
            assertEquals(bits == 3, SystemNotificationHooks.expandedPopupMayAutoDismiss(
                bits and 1 != 0, bits and 2 != 0, bits and 4 != 0, bits and 8 != 0, bits and 16 != 0))
        }
    }

    @Test fun protectedAndOtherUserNotificationControlsRemainNative() {
        assertTrue(SystemNotificationHooks.ordinaryNotificationTarget(10023, 0, 0))
        assertTrue(SystemNotificationHooks.ordinaryNotificationTarget(1010023, 0, 10))
        for (uid in listOf(-1, 0, 1000, 9999, 20000, 99000, 1010023))
            assertFalse(SystemNotificationHooks.ordinaryNotificationTarget(uid, 0, 0))
        for (flags in listOf(android.content.pm.ApplicationInfo.FLAG_SYSTEM, android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))
            assertFalse(SystemNotificationHooks.ordinaryNotificationTarget(10023, flags, 0))
    }
}
