package tv.withaibuild.customiuizer.mods.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry.PreferenceObserver
import tv.withaibuild.customiuizer.utils.canonicalPreferenceKey

/**
 * Boundary tests for the [PreferenceObserverRegistry] dispatch path.
 *
 * The registry is the canonicalization boundary: any caller (including direct
 * production calls to [ModuleHelper.handlePreferenceChanged]) may pass either a
 * storage key or a short key, and every observer must receive the canonical
 * short key exactly once.
 */
class PreferenceObserverRegistryTest {

    @After
    fun tearDown() {
        ModuleHelper.unregisterPreferenceObserver(this)
        clearProcessScopedObservers()
    }

    private fun recordingObserver(received: MutableList<String?>): ModuleHelper.PreferenceObserver {
        return object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) {
                received.add(key)
            }
        }
    }

    private fun clearProcessScopedObservers() {
        val registry = PreferenceObserverRegistry
        val field = PreferenceObserverRegistry::class.java.getDeclaredField("observers")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val set = field.get(registry) as? java.util.concurrent.CopyOnWriteArraySet<PreferenceObserver>
        set?.clear()
    }

    @Test
    fun handlePreferenceChanged_rawKey_isCanonicalizedForProcessScopedObserver() {
        val received = mutableListOf<String?>()
        ModuleHelper.observePreferenceChange(recordingObserver(received))

        ModuleHelper.handlePreferenceChanged("pref_key_system_visualizer_animdur")

        assertEquals(listOf("system_visualizer_animdur"), received)
    }

    @Test
    fun handlePreferenceChanged_shortKey_isUnchangedForProcessScopedObserver() {
        val received = mutableListOf<String?>()
        ModuleHelper.observePreferenceChange(recordingObserver(received))

        ModuleHelper.handlePreferenceChanged("system_visualizer_animdur")

        assertEquals(listOf("system_visualizer_animdur"), received)
    }

    @Test
    fun handlePreferenceChanged_rawKeyTwice_deliversCanonicalKeyTwice() {
        val received = mutableListOf<String?>()
        ModuleHelper.observePreferenceChange(recordingObserver(received))

        ModuleHelper.handlePreferenceChanged("pref_key_system_visualizer_animdur")
        ModuleHelper.handlePreferenceChanged("system_visualizer_animdur")

        assertEquals(listOf("system_visualizer_animdur", "system_visualizer_animdur"), received)
    }

    @Test
    fun handlePreferenceChanged_nullKey_deliversNull() {
        val received = mutableListOf<String?>()
        ModuleHelper.observePreferenceChange(recordingObserver(received))

        ModuleHelper.handlePreferenceChanged(null)

        assertEquals(listOf<String?>(null), received)
    }

    @Test
    fun handlePreferenceChanged_ownerBoundObserver_receivesCanonicalKey() {
        val received = mutableListOf<String?>()
        ModuleHelper.observePreferenceChange(
            recordingObserver(received),
            this
        )

        ModuleHelper.handlePreferenceChanged("pref_key_system_visualizer_animdur")

        assertEquals(listOf("system_visualizer_animdur"), received)
    }

    @Test
    fun handlePreferenceChanged_nonPrefixedKey_isUnchanged() {
        val received = mutableListOf<String?>()
        ModuleHelper.observePreferenceChange(recordingObserver(received))

        ModuleHelper.handlePreferenceChanged("custom_key_without_prefix")

        assertEquals(listOf("custom_key_without_prefix"), received)
    }

    @Test
    fun handlePreferenceChanged_systemuiRestartTime_isCanonicalizedAndDelivered() {
        // The registry does not filter systemui_restart_time. The Bootstrap layer
        // excludes it because it is a bookkeeping key, not a preference change.
        val received = mutableListOf<String?>()
        ModuleHelper.observePreferenceChange(recordingObserver(received))

        ModuleHelper.handlePreferenceChanged("pref_key_systemui_restart_time")

        assertEquals(listOf("systemui_restart_time"), received)
    }

    @Test
    fun canonicalPreferenceKey_contract() {
        assertNull(canonicalPreferenceKey(null))
        assertEquals("system_charginginfo", canonicalPreferenceKey("pref_key_system_charginginfo"))
        assertEquals("system_charginginfo", canonicalPreferenceKey("system_charginginfo"))
        assertEquals("custom", canonicalPreferenceKey("custom"))
    }
}
