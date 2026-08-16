package tv.withaibuild.customiuizer.mods.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule

/**
 * Regression guard for the SystemUI global-action status-bar setup gate.
 *
 * The one-time [GlobalActionConfig] cache must not be built from a preference
 * snapshot that has not yet reached a ready state, otherwise a transient
 * bootstrap miss permanently poisons the cache and custom status-bar actions
 * are never installed even when the snapshot later becomes ready.
 *
 * Note: [hasConfiguredGlobalActions] uses [android.util.SparseBooleanArray] to
 * communicate configured action codes. In the JVM unit-test environment
 * [SparseBooleanArray] does not behave like it does on a real device, so these
 * tests verify the cache-ready gate and the snapshot values rather than the
 * exact return value of [hasConfiguredGlobalActions].
 */
class GlobalActionBootstrapReadinessTest {

    @Before
    fun setUp() {
        MainModule.mPrefs.clear()
        resetGlobalActionConfig()
    }

    @After
    fun tearDown() {
        MainModule.mPrefs.clear()
        resetGlobalActionConfig()
    }

    private fun resetGlobalActionConfig() {
        val clazz = Class.forName("tv.withaibuild.customiuizer.mods.utils.GlobalActionConfigKt")
        clazz.getDeclaredField("customActionsReady").apply { isAccessible = true }.set(null, false)
        clazz.getDeclaredField("customActionCodeMap").apply { isAccessible = true }.set(null, null)
        clazz.getDeclaredField("customToggleMap").apply { isAccessible = true }.set(null, null)
    }

    private fun isCacheReady(): Boolean =
        Class.forName("tv.withaibuild.customiuizer.mods.utils.GlobalActionConfigKt")
            .getDeclaredField("customActionsReady").apply { isAccessible = true }
            .get(null) as Boolean

    @Test
    fun packageReady_notReady_doesNotEvaluateCache() {
        // Case 2: a transient preference bootstrap miss must not freeze the cache.
        MainModule.mPrefs.put("controls_backlong_action", 2)
        assertFalse(shouldSetupGlobalActionStatusBar(prefReady = false, alreadyDone = false))
        assertFalse("Cache must not be built before the snapshot is ready", isCacheReady())
    }

    @Test
    fun packageReady_ready_evaluatesFromSnapshot() {
        // Case 1: a stable ready snapshot allows the gate to evaluate.
        MainModule.mPrefs.put("controls_backlong_action", 2)
        assertEquals(2, MainModule.mPrefs.getInt("controls_backlong_action", 1))
        assertTrue(shouldSetupGlobalActionStatusBar(prefReady = true, alreadyDone = false))

        // Simulates the call that the real coordinator would make after the gate opens.
        hasConfiguredGlobalActions()
        assertTrue("Cache must be built once the ready snapshot is evaluated", isCacheReady())
    }

    @Test
    fun initRetry_readyAfterNotReady_usesFreshSnapshot() {
        // Case 3: package-ready miss defers evaluation until the init retry is ready.
        assertFalse(shouldSetupGlobalActionStatusBar(prefReady = false, alreadyDone = false))
        assertFalse("Cache must not be built from an unready snapshot", isCacheReady())

        // Preferences become ready while the SystemUI initializer hook is pending.
        MainModule.mPrefs.put("launcher_shake_action", 5)
        assertEquals(5, MainModule.mPrefs.getInt("launcher_shake_action", 1))
        assertTrue(shouldSetupGlobalActionStatusBar(prefReady = true, alreadyDone = false))

        hasConfiguredGlobalActions()
        assertTrue("Cache must be built from the newly ready snapshot", isCacheReady())
    }

    @Test
    fun evaluatedOnce_doesNotReevaluate() {
        // Case 4: the done flag prevents repeated setupStatusBar scans.
        MainModule.mPrefs.put("controls_powerdt_action", 3)
        assertTrue(shouldSetupGlobalActionStatusBar(prefReady = true, alreadyDone = false))
        hasConfiguredGlobalActions()
        assertTrue(isCacheReady())

        // Even if prefReady flips around, a done gate stays closed and the cache
        // is not re-scanned.
        assertFalse(shouldSetupGlobalActionStatusBar(prefReady = true, alreadyDone = true))
        assertFalse(shouldSetupGlobalActionStatusBar(prefReady = false, alreadyDone = true))
        assertTrue(isCacheReady())
    }
}
