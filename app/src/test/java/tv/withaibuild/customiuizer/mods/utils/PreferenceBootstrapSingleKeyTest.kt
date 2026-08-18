package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.utils.FakeSharedPreferences
import tv.withaibuild.customiuizer.utils.PrefMap
import tv.withaibuild.customiuizer.utils.PreferenceValueType
import tv.withaibuild.customiuizer.utils.PreferenceValueTypes

class PreferenceBootstrapSingleKeyTest {

    @Test
    fun knownBooleanDoesNotCopyFullMap() {
        val fake = primed("pref_key_system_statusbarcolor", true)
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }
        assertTrue(bootstrap.bootstrap())
        val afterBootstrap = fake.getAllCount

        notify(bootstrap, fake, "pref_key_system_statusbarcolor", false)

        assertFalse(prefs.getBoolean("system_statusbarcolor", true))
        assertEquals(afterBootstrap, fake.getAllCount)
    }

    @Test
    fun knownIntDoesNotCopyFullMap() {
        val fake = primed("pref_key_system_charginginfo_fontsize", 16)
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }
        assertTrue(bootstrap.bootstrap())
        val afterBootstrap = fake.getAllCount

        notify(bootstrap, fake, "pref_key_system_charginginfo_fontsize", 22)

        assertEquals(22, prefs.getInt("system_charginginfo_fontsize", 16))
        assertEquals(afterBootstrap, fake.getAllCount)
    }

    @Test
    fun knownStringDoesNotCopyFullMap() {
        val fake = primed("pref_key_system_strong_toast_mode", "0")
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }
        assertTrue(bootstrap.bootstrap())
        val afterBootstrap = fake.getAllCount

        notify(bootstrap, fake, "pref_key_system_strong_toast_mode", "2")

        assertEquals("2", prefs.getString("system_strong_toast_mode", "0"))
        assertEquals(afterBootstrap, fake.getAllCount)
    }

    @Test
    fun knownStringSetCopiesOwnershipWithoutFullMap() {
        val original = linkedSetOf("com.one")
        val fake = primed("pref_key_system_statusbarcolor_apps", original)
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }
        assertTrue(bootstrap.bootstrap())
        val afterBootstrap = fake.getAllCount

        val updated = linkedSetOf("com.one", "com.two")
        notify(bootstrap, fake, "pref_key_system_statusbarcolor_apps", updated)
        updated.add("com.mutated-after-put")

        assertEquals(setOf("com.one", "com.two"), prefs.getStringSet("system_statusbarcolor_apps"))
        assertEquals(afterBootstrap, fake.getAllCount)
    }

    @Test
    fun longAndFloatUnknownKeysUseFallbackCopy() {
        val fake = primed("pref_key_unknown_long_metric", 7L)
        fake.put("pref_key_unknown_float_metric", 1.5f)
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }
        assertTrue(bootstrap.bootstrap())
        val afterBootstrap = fake.getAllCount

        notify(bootstrap, fake, "pref_key_unknown_long_metric", 9L)
        notify(bootstrap, fake, "pref_key_unknown_float_metric", 2.5f)

        assertEquals(9L, prefs.getLong("unknown_long_metric", 0L))
        assertEquals(2.5f, prefs.getAll()["unknown_float_metric"])
        assertTrue(fake.getAllCount > afterBootstrap)
    }

    @Test
    fun removeDeletesLocalKey() {
        val fake = primed("pref_key_system_statusbarcolor", true)
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }
        assertTrue(bootstrap.bootstrap())

        fake.edit().remove("pref_key_system_statusbarcolor").apply()
        getListener(bootstrap)?.onSharedPreferenceChanged(fake, "pref_key_system_statusbarcolor")

        assertFalse(prefs.getBoolean("system_statusbarcolor", false))
        assertFalse("system_statusbarcolor" in prefs.getAll())
    }

    @Test
    fun dynamicMimeKeyUsesIntPath() {
        assertEquals(
            PreferenceValueType.INT,
            PreferenceValueTypes.resolve("pref_key_system_cleanopenwith_apps_com.foo|0"),
        )
        val fake = primed("pref_key_system_cleanopenwith_apps_com.foo|0", 3)
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }
        assertTrue(bootstrap.bootstrap())
        val afterBootstrap = fake.getAllCount

        notify(bootstrap, fake, "pref_key_system_cleanopenwith_apps_com.foo|0", 7)

        assertEquals(7, prefs.getInt("system_cleanopenwith_apps_com.foo|0", 0))
        assertEquals(afterBootstrap, fake.getAllCount)
    }

    @Test
    fun typeMismatchFallsBackWithoutCrashing() {
        val fake = primed("pref_key_system_statusbarcolor", true)
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }
        assertTrue(bootstrap.bootstrap())

        fake.put("pref_key_system_statusbarcolor", "not-a-boolean")
        getListener(bootstrap)?.onSharedPreferenceChanged(fake, "pref_key_system_statusbarcolor")

        assertEquals("not-a-boolean", prefs.getAll()["system_statusbarcolor"])
        assertEquals(PreferenceBootstrap.State.LOADED, bootstrap.getState())
    }

    private fun primed(key: String, value: Any): FakeSharedPreferences {
        val fake = FakeSharedPreferences()
        fake.put(key, value)
        return fake
    }

    private fun notify(
        bootstrap: PreferenceBootstrap,
        fake: FakeSharedPreferences,
        key: String,
        value: Any,
    ) {
        fake.put(key, value)
        getListener(bootstrap)?.onSharedPreferenceChanged(fake, key)
    }

    private fun getListener(bootstrap: PreferenceBootstrap): android.content.SharedPreferences.OnSharedPreferenceChangeListener? {
        val field = PreferenceBootstrap::class.java.getDeclaredField("listener")
        field.isAccessible = true
        return field.get(bootstrap) as? android.content.SharedPreferences.OnSharedPreferenceChangeListener
    }
}
