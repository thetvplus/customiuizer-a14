package tv.withaibuild.customiuizer.mods.notificationautoexpand

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.HookDiagnostics

class NotificationAutoExpandHookTest {
    @Before
    @After
    fun resetDiagnostics() = HookDiagnostics.reset()

    @Test
    fun missingClassIsProbedOnceAndRecordsTheMissingTarget() {
        var attempts = 0
        NotificationAutoExpandHook.install(failingLoader {
            attempts++
            throw ClassNotFoundException()
        })
        assertEquals(1, attempts)
        assertEquals(HookDiagnostics.Status.TARGET_CLASS_MISSING, record().status)
    }

    @Test
    fun ordinaryProbeFailureIsRecordedWithoutEscapingInstallation() {
        NotificationAutoExpandHook.install(failingLoader { throw IllegalStateException("loader failed") })
        val failure = record()
        assertEquals(HookDiagnostics.Kind.ALL_METHODS, failure.kind)
        assertEquals(HookDiagnostics.Status.INSTALL_FAILED, failure.status)
        assertEquals("IllegalStateException", failure.exceptionType)
    }

    @Test
    fun fatalProbeFailureKeepsItsIdentity() {
        val fatal = OutOfMemoryError("loader failed")
        assertSame(fatal, assertThrows(OutOfMemoryError::class.java) {
            NotificationAutoExpandHook.install(failingLoader { throw fatal })
        })
    }

    private fun record() = HookDiagnostics.snapshot().single {
        it.targetClass == TARGET_CLASS && it.targetMember == "setFeedbackIcon"
    }

    private fun failingLoader(fail: () -> Nothing) = object : ClassLoader(javaClass.classLoader) {
        override fun loadClass(name: String?, resolve: Boolean): Class<*> {
            if (name == TARGET_CLASS) fail()
            return super.loadClass(name, resolve)
        }
    }

    private companion object {
        const val TARGET_CLASS = "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow"
    }
}
