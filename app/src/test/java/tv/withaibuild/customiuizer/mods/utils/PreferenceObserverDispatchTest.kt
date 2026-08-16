package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the two contracts [ModuleHelper.handlePreferenceChanged] has to keep.
 *
 * Dispatch runs on the remote-preferences listener thread of system_server, SystemUI and
 * Launcher, and the observers registered from hooks do reflection against ROM objects that
 * may have been replaced. Before these were enforced, one throwing observer both killed that
 * thread and silently stopped every later observer from seeing the change.
 */
class PreferenceObserverDispatchTest {

    private class RecordingObserver(
        private val log: MutableList<String>,
        private val name: String,
        private val throwOnChange: Boolean = false,
        private val throwOomOnChange: Boolean = false,
    ) : ModuleHelper.PreferenceObserver {
        override fun onChange(key: String?) {
            log.add(name)
            if (throwOomOnChange) throw OutOfMemoryError("ROM field missing")
            if (throwOnChange) throw IllegalStateException("ROM field missing")
        }
    }

    private class Owner

    @Test
    fun aThrowingObserverNeitherPropagatesNorStopsTheRest() {
        val log = mutableListOf<String>()
        val first = Owner()
        val second = Owner()
        val third = Owner()
        ModuleHelper.observePreferenceChange(RecordingObserver(log, "first"), first)
        ModuleHelper.observePreferenceChange(RecordingObserver(log, "boom", throwOnChange = true), second)
        ModuleHelper.observePreferenceChange(RecordingObserver(log, "third"), third)

        try {
            ModuleHelper.handlePreferenceChanged("system_statusbar_clocktweak")

            assertTrue("first observer was not dispatched to", log.contains("first"))
            assertTrue("throwing observer was not dispatched to", log.contains("boom"))
            assertTrue("dispatch stopped at the throwing observer", log.contains("third"))
        } finally {
            ModuleHelper.unregisterPreferenceObserver(first)
            ModuleHelper.unregisterPreferenceObserver(second)
            ModuleHelper.unregisterPreferenceObserver(third)
        }
    }

    @Test
    fun reRegisteringTheSameOwnerReplacesItsObserver() {
        val log = mutableListOf<String>()
        val owner = Owner()
        ModuleHelper.observePreferenceChange(RecordingObserver(log, "stale"), owner)
        ModuleHelper.observePreferenceChange(RecordingObserver(log, "fresh"), owner)

        try {
            ModuleHelper.handlePreferenceChanged("system_statusbar_clocktweak")

            assertEquals(listOf("fresh"), log)
        } finally {
            ModuleHelper.unregisterPreferenceObserver(owner)
        }
    }

    @Test
    fun removingAnOwnerStopsItsObserver() {
        val log = mutableListOf<String>()
        val owner = Owner()
        ModuleHelper.observePreferenceChange(RecordingObserver(log, "gone"), owner)
        ModuleHelper.unregisterPreferenceObserver(owner)

        ModuleHelper.handlePreferenceChanged("system_statusbar_clocktweak")

        assertTrue("observer ran after its owner was removed", log.isEmpty())
    }

    @Test
    fun anObserverIsDroppedOnceItsOwnerIsCollected() {
        val log = mutableListOf<String>()
        var owner: Owner? = Owner()
        ModuleHelper.observePreferenceChange(RecordingObserver(log, "collected"), owner!!)

        // The only strong reference to the observer lives in the owner's additional instance
        // field, which XposedHelpers keeps in a WeakHashMap. Dropping the owner must therefore
        // make the observer unreachable; a strong registry would pin it for the whole process.
        owner = null
        val survivor = Owner()
        var dropped = false
        repeat(20) {
            if (dropped) return@repeat
            System.gc()
            log.clear()
            // Registration is what sweeps cleared references.
            ModuleHelper.observePreferenceChange(RecordingObserver(log, "survivor"), survivor)
            ModuleHelper.handlePreferenceChanged("system_statusbar_clocktweak")
            dropped = !log.contains("collected")
        }
        ModuleHelper.unregisterPreferenceObserver(survivor)

        assertTrue("observer stayed reachable after its owner was collected", dropped)
    }

    @Test(expected = OutOfMemoryError::class)
    fun anOomObserverIsPropagatedToCrashTheHost() {
        val owner = Owner()
        ModuleHelper.observePreferenceChange(RecordingObserver(mutableListOf(), "oom", throwOomOnChange = true), owner)
        try {
            ModuleHelper.handlePreferenceChanged("system_statusbar_clocktweak")
        } finally {
            ModuleHelper.unregisterPreferenceObserver(owner)
        }
    }
}
