package tv.withaibuild.customiuizer.mods.notificationautoexpand

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.HookDiagnostics
import tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry

/**
 * Component tests for [NotificationAutoExpandHook].
 *
 * Evidence classification: RUNTIME_TESTED_COMPONENT.
 */
class NotificationAutoExpandHookTest {

    @Before
    fun setUp() {
        NotificationAutoExpandRuntimeState.installed = false
        NotificationAutoExpandRuntimeState.instance = null
        PreferenceObserverRegistry.observers.clear()
        PreferenceObserverRegistry.observerOwners.clear()
        HookDiagnostics.reset()
    }

    @After
    fun tearDown() {
        NotificationAutoExpandRuntimeState.installed = false
        NotificationAutoExpandRuntimeState.instance = null
        PreferenceObserverRegistry.observers.clear()
        PreferenceObserverRegistry.observerOwners.clear()
        HookDiagnostics.reset()
    }

    @Test
    fun install_targetClassMissing_doesNotCreateRuntimeState() {
        val missingLoader = object : ClassLoader(this.javaClass.classLoader) {
            override fun loadClass(name: String?, resolve: Boolean): Class<*> {
                if (name == "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow") {
                    throw ClassNotFoundException("simulated missing target class")
                }
                return super.loadClass(name, resolve)
            }
        }

        NotificationAutoExpandHook.install(missingLoader)

        assertFalse(
            "Runtime state must not be installed when target class is missing",
            NotificationAutoExpandRuntimeState.installed,
        )
    }

    @Test
    fun install_ordinaryProbeFailure_doesNotCreateRuntimeStateAndRecordsFailure() {
        val failingLoader = object : ClassLoader(this.javaClass.classLoader) {
            override fun loadClass(name: String?, resolve: Boolean): Class<*> {
                if (name == "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow") {
                    throw IllegalStateException("simulated ordinary class-loading failure")
                }
                return super.loadClass(name, resolve)
            }
        }

        var thrown: Throwable? = null
        try {
            NotificationAutoExpandHook.install(failingLoader)
        } catch (t: Throwable) {
            thrown = t
        }

        assertEquals(
            "Ordinary probe failure must be isolated; no exception must escape",
            null,
            thrown,
        )
        assertFalse(
            "Runtime state must not be installed when the target probe fails ordinarily",
            NotificationAutoExpandRuntimeState.installed,
        )

        val record = HookDiagnostics.snapshot().find {
            it.targetClass == "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow" &&
                it.targetMember == "setFeedbackIcon"
        }
        assertNotNull("Hook diagnostics must record the install failure", record)
        assertEquals(
            "Probe failure must be recorded as ALL_METHODS",
            HookDiagnostics.Kind.ALL_METHODS,
            record!!.kind,
        )
        assertEquals(
            "Probe failure must be recorded with INSTALL_FAILED",
            HookDiagnostics.Status.INSTALL_FAILED,
            record.status,
        )
        assertEquals(
            "Target class must be ExpandableNotificationRow",
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow",
            record.targetClass,
        )
        assertEquals(
            "Target member must be setFeedbackIcon",
            "setFeedbackIcon",
            record.targetMember,
        )
        assertEquals(
            "Exception type must identify the ordinary failure",
            "IllegalStateException",
            record.exceptionType,
        )
    }

    @Test
    fun install_fatalProbeFailure_rethrowsAndDoesNotCreateRuntimeState() {
        val fatalLoader = object : ClassLoader(this.javaClass.classLoader) {
            override fun loadClass(name: String?, resolve: Boolean): Class<*> {
                if (name == "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow") {
                    throw OutOfMemoryError("simulated fatal class-loading failure")
                }
                return super.loadClass(name, resolve)
            }
        }

        var thrown: Throwable? = null
        try {
            NotificationAutoExpandHook.install(fatalLoader)
        } catch (t: Throwable) {
            thrown = t
        }

        assertTrue("Fatal class-loading failure must propagate", thrown is OutOfMemoryError)
        assertFalse(
            "Runtime state must not be installed after a fatal failure",
            NotificationAutoExpandRuntimeState.installed,
        )
    }
}
