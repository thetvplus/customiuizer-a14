package tv.withaibuild.customiuizer.mods.utils

import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.utils.FakeSharedPreferences
import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Full dispatch-chain tests for the canonical observer key contract.
 *
 * The chain is:
 *     FakeSharedPreferences -> PreferenceBootstrap -> PrefMap
 *     -> ModuleHelper.handlePreferenceChanged -> PreferenceObserverRegistry
 *     -> registered observers.
 *
 * Observers must always receive the short, source-level key (`system_*`) with the
 * `pref_key_` storage prefix removed. A single storage change must produce exactly
 * one short-key callback, never a raw-key callback, and never two callbacks.
 */
class PreferenceObserverKeyContractTest {

    @After
    fun tearDown() {
        ModuleHelper.unregisterPreferenceObserver(this)
    }

    private fun buildBootstrap(
        prefs: PrefMap,
        fake: FakeSharedPreferences,
        log: MutableList<String?>,
    ): PreferenceBootstrap {
        return PreferenceBootstrap.create(prefs, { fake }) { key ->
            log.add(key)
            ModuleHelper.handlePreferenceChanged(key)
        }
    }

    @Test
    fun chargingFontSize_rawStorageKey_deliversShortKeyOnce() {
        val fake = FakeSharedPreferences()
        val prefs = PrefMap()
        val dispatchedKeys = mutableListOf<String?>()
        val receivedKeys = mutableListOf<String?>()

        ModuleHelper.observePreferenceChange(
            object : ModuleHelper.PreferenceObserver {
                override fun onChange(key: String?) {
                    receivedKeys.add(key)
                }
            },
            this,
        )

        val bootstrap = buildBootstrap(prefs, fake, dispatchedKeys)
        bootstrap.bootstrap()

        fake.put("pref_key_system_charginginfo_fontsize", 20)
        val listener = getListener(bootstrap)
        listener?.onSharedPreferenceChanged(fake, "pref_key_system_charginginfo_fontsize")

        assertEquals(20, prefs.getInt("system_charginginfo_fontsize", 16))
        assertEquals(listOf("system_charginginfo_fontsize"), dispatchedKeys)
        assertEquals(listOf("system_charginginfo_fontsize"), receivedKeys)
    }

    @Test
    fun audioVisualizer_rawStorageKey_deliversShortKeyOnce() {
        val fake = FakeSharedPreferences()
        val prefs = PrefMap()
        val dispatchedKeys = mutableListOf<String?>()
        val receivedKeys = mutableListOf<String?>()

        ModuleHelper.observePreferenceChange(
            object : ModuleHelper.PreferenceObserver {
                override fun onChange(key: String?) {
                    if (key != null) receivedKeys.add(key)
                }
            },
            this,
        )

        val bootstrap = buildBootstrap(prefs, fake, dispatchedKeys)
        bootstrap.bootstrap()

        fake.put("pref_key_system_visualizer_animdur", 80)
        val listener = getListener(bootstrap)
        listener?.onSharedPreferenceChanged(fake, "pref_key_system_visualizer_animdur")

        assertEquals(80, prefs.getInt("system_visualizer_animdur", 65))
        assertEquals(listOf("system_visualizer_animdur"), dispatchedKeys)
        assertEquals(listOf("system_visualizer_animdur"), receivedKeys)
    }

    @Test
    fun batteryIndicator_rawStorageKey_deliversShortKeyOnce() {
        val fake = FakeSharedPreferences()
        val prefs = PrefMap()
        val dispatchedKeys = mutableListOf<String?>()
        val receivedKeys = mutableListOf<String?>()

        ModuleHelper.observePreferenceChange(
            object : ModuleHelper.PreferenceObserver {
                override fun onChange(key: String?) {
                    if (key?.startsWith("system_batteryindicator") == true) {
                        receivedKeys.add(key)
                    }
                }
            },
            this,
        )

        val bootstrap = buildBootstrap(prefs, fake, dispatchedKeys)
        bootstrap.bootstrap()

        fake.put("pref_key_system_batteryindicator_color", 2)
        val listener = getListener(bootstrap)
        listener?.onSharedPreferenceChanged(fake, "pref_key_system_batteryindicator_color")

        assertEquals(2, prefs.getInt("system_batteryindicator_color", 1))
        assertEquals(listOf("system_batteryindicator_color"), dispatchedKeys)
        assertEquals(listOf("system_batteryindicator_color"), receivedKeys)
    }

    @Test
    fun b1b2b3_shortKeys_stillDeliveredOnce() {
        val fake = FakeSharedPreferences()
        val prefs = PrefMap()
        val dispatchedKeys = mutableListOf<String?>()
        val receivedKeys = mutableListOf<String?>()

        ModuleHelper.observePreferenceChange(
            object : ModuleHelper.PreferenceObserver {
                override fun onChange(key: String?) {
                    if (key in B1B2B3_KEYS) receivedKeys.add(key)
                }
            },
            this,
        )

        val bootstrap = buildBootstrap(prefs, fake, dispatchedKeys)
        bootstrap.bootstrap()

        fake.put("pref_key_system_netspeed_fontsize", 18)
        fake.put("pref_key_system_statusbaricons_signal", true)
        val listener = getListener(bootstrap)
        listener?.onSharedPreferenceChanged(fake, "pref_key_system_netspeed_fontsize")
        listener?.onSharedPreferenceChanged(fake, "pref_key_system_statusbaricons_signal")

        assertEquals(18, prefs.getInt("system_netspeed_fontsize", 0))
        assertEquals(true, prefs.getBoolean("system_statusbaricons_signal", false))
        assertEquals(listOf("system_netspeed_fontsize", "system_statusbaricons_signal"), dispatchedKeys)
        assertEquals(listOf("system_netspeed_fontsize", "system_statusbaricons_signal"), receivedKeys)
    }

    @Test
    fun nonPrefixedStorageKey_isDispatchedUnchanged() {
        val fake = FakeSharedPreferences()
        val prefs = PrefMap()
        val dispatchedKeys = mutableListOf<String?>()
        val receivedKeys = mutableListOf<String?>()

        ModuleHelper.observePreferenceChange(
            object : ModuleHelper.PreferenceObserver {
                override fun onChange(key: String?) {
                    receivedKeys.add(key)
                }
            },
            this,
        )

        val bootstrap = buildBootstrap(prefs, fake, dispatchedKeys)
        bootstrap.bootstrap()

        fake.put("custom_key_without_prefix", 42)
        val listener = getListener(bootstrap)
        listener?.onSharedPreferenceChanged(fake, "custom_key_without_prefix")

        assertEquals(42, prefs.getInt("custom_key_without_prefix", 0))
        assertEquals(listOf("custom_key_without_prefix"), dispatchedKeys)
        assertEquals(listOf("custom_key_without_prefix"), receivedKeys)
    }

    @Test
    fun systemuiRestartTime_isNotDispatched() {
        val fake = FakeSharedPreferences()
        val prefs = PrefMap()
        val dispatchedKeys = mutableListOf<String?>()
        val receivedKeys = mutableListOf<String?>()

        ModuleHelper.observePreferenceChange(
            object : ModuleHelper.PreferenceObserver {
                override fun onChange(key: String?) {
                    receivedKeys.add(key)
                }
            },
            this,
        )

        val bootstrap = buildBootstrap(prefs, fake, dispatchedKeys)
        bootstrap.bootstrap()

        fake.put("pref_key_systemui_restart_time", 12345678L)
        val listener = getListener(bootstrap)
        listener?.onSharedPreferenceChanged(fake, "pref_key_systemui_restart_time")

        assertTrue("systemui_restart_time must not be dispatched", dispatchedKeys.isEmpty())
        assertTrue("systemui_restart_time must not reach observers", receivedKeys.isEmpty())
    }

    @Test
    fun singleChange_doesNotDispatchRawAndNormalized() {
        val fake = FakeSharedPreferences()
        val prefs = PrefMap()
        val dispatchedKeys = mutableListOf<String?>()

        ModuleHelper.observePreferenceChange(
            object : ModuleHelper.PreferenceObserver {
                override fun onChange(key: String?) {
                    dispatchedKeys.add("observer:$key")
                }
            },
            this,
        )

        val bootstrap = PreferenceBootstrap.create(prefs, { fake }) { key ->
            dispatchedKeys.add("dispatcher:$key")
            ModuleHelper.handlePreferenceChanged(key)
        }
        bootstrap.bootstrap()

        fake.put("pref_key_system_charginginfo_view", 2)
        val listener = getListener(bootstrap)
        listener?.onSharedPreferenceChanged(fake, "pref_key_system_charginginfo_view")

        assertEquals(
            listOf("dispatcher:system_charginginfo_view", "observer:system_charginginfo_view"),
            dispatchedKeys
        )
        assertFalse(
            "raw pref_key_ must not appear in dispatch",
            dispatchedKeys.any { it?.contains("pref_key_") == true }
        )
    }

    private fun getListener(bootstrap: PreferenceBootstrap): SharedPreferences.OnSharedPreferenceChangeListener? {
        val field = PreferenceBootstrap::class.java.getDeclaredField("listener")
            .apply { isAccessible = true }
        return field.get(bootstrap) as? SharedPreferences.OnSharedPreferenceChangeListener
    }

    companion object {
        private val B1B2B3_KEYS = setOf(
            "system_netspeed_fontsize",
            "system_statusbaricons_signal",
        )
    }
}
