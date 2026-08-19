package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.utils.FakeSharedPreferences
import tv.withaibuild.customiuizer.utils.PrefMap

class SystemServerPreferenceInvalidationTest {

    @Test
    fun `refreshRemoteKey single key updates PrefMap and dispatches canonical key`() {
        val prefs = PrefMap()
        val dispatched = mutableListOf<String?>()
        val key = "pref_key_controls_fingerprintfailure"
        val fakeRemote = primed(key, true)
        val bootstrap = PreferenceBootstrap.create(prefs, { fakeRemote }, dispatched::add)
        assertTrue("bootstrap must succeed", bootstrap.bootstrap())
        assertTrue("initial value", prefs.getBoolean("controls_fingerprintfailure", false))
        dispatched.clear()

        fakeRemote.put(key, false)
        bootstrap.refreshRemoteKey(key)

        assertEquals(false, prefs.getBoolean("controls_fingerprintfailure", true))
        assertEquals(listOf("controls_fingerprintfailure"), dispatched)
    }

    @Test
    fun `refreshRemoteKey with removed key removes from PrefMap`() {
        val prefs = PrefMap()
        val dispatched = mutableListOf<String?>()
        val fakeRemote = primed("pref_key_test", 5)
        val bootstrap = PreferenceBootstrap.create(prefs, { fakeRemote }, dispatched::add)
        bootstrap.bootstrap()
        dispatched.clear()

        fakeRemote.edit().remove("pref_key_test").apply()
        bootstrap.refreshRemoteKey("pref_key_test")

        assertTrue("test" !in prefs.getAll())
        assertEquals(listOf("test"), dispatched)
    }

    @Test
    fun `refreshRemoteKey null triggers bulk snapshot replacement`() {
        val prefs = PrefMap()
        val dispatched = mutableListOf<String?>()
        val fakeRemote = primed("pref_key_a", 1)
        fakeRemote.put("pref_key_b", 2)
        val bootstrap = PreferenceBootstrap.create(prefs, { fakeRemote }, dispatched::add)
        bootstrap.bootstrap()
        dispatched.clear()

        fakeRemote.put("pref_key_a", 10)
        fakeRemote.put("pref_key_c", 3)
        bootstrap.refreshRemoteKey(null)

        assertEquals(10, prefs.getInt("a", 0))
        assertEquals(3, prefs.getInt("c", 0))
        assertEquals(listOf<String?>(null), dispatched)
    }

    @Test
    fun `bulk refresh drops keys removed remotely`() {
        val prefs = PrefMap()
        val fakeRemote = primed("pref_key_a", 1)
        fakeRemote.put("pref_key_b", 2)
        val bootstrap = PreferenceBootstrap.create(prefs, { fakeRemote }) {}
        bootstrap.bootstrap()

        fakeRemote.edit().remove("pref_key_b").apply()
        val getAllBefore = fakeRemote.getAllCount
        bootstrap.refreshRemoteKey(null)

        assertTrue("b" !in prefs.getAll())
        assertEquals("bulk must read the remote map exactly once", getAllBefore + 1, fakeRemote.getAllCount)
    }

    @Test
    fun `rapid repeated invalidations converge to latest value`() {
        val prefs = PrefMap()
        val dispatched = mutableListOf<String?>()
        val fakeRemote = primed("pref_key_x", 1)
        val bootstrap = PreferenceBootstrap.create(prefs, { fakeRemote }, dispatched::add)
        bootstrap.bootstrap()
        dispatched.clear()

        fakeRemote.put("pref_key_x", 5)
        bootstrap.refreshRemoteKey("pref_key_x")
        fakeRemote.put("pref_key_x", 10)
        bootstrap.refreshRemoteKey("pref_key_x")
        fakeRemote.put("pref_key_x", 42)
        bootstrap.refreshRemoteKey("pref_key_x")

        assertEquals(42, prefs.getInt("x", 0))
        assertEquals(3, dispatched.size)
    }

    @Test
    fun `canonical key dispatched exactly once per refresh`() {
        val prefs = PrefMap()
        val dispatched = mutableListOf<String?>()
        val fakeRemote = primed("pref_key_foo_bar", "a")
        val bootstrap = PreferenceBootstrap.create(prefs, { fakeRemote }, dispatched::add)
        bootstrap.bootstrap()
        dispatched.clear()

        fakeRemote.put("pref_key_foo_bar", "b")
        bootstrap.refreshRemoteKey("pref_key_foo_bar")

        assertEquals(1, dispatched.size)
        assertEquals("foo_bar", dispatched[0])
    }

    @Test
    fun `no full map read on known single-key update`() {
        val prefs = PrefMap()
        val fakeRemote = primed("pref_key_controls_powerdt_action", 1)
        val bootstrap = PreferenceBootstrap.create(prefs, { fakeRemote }) {}
        bootstrap.bootstrap()
        val getAllAfterBootstrap = fakeRemote.getAllCount

        fakeRemote.put("pref_key_controls_powerdt_action", 10)
        bootstrap.refreshRemoteKey("pref_key_controls_powerdt_action")

        assertEquals(10, prefs.getInt("controls_powerdt_action", 0))
        assertEquals(getAllAfterBootstrap, fakeRemote.getAllCount)
    }

    @Test
    fun `systemui_restart_time key is not dispatched`() {
        val prefs = PrefMap()
        val dispatched = mutableListOf<String?>()
        val fakeRemote = primed("pref_key_systemui_restart_time", 123L)
        val bootstrap = PreferenceBootstrap.create(prefs, { fakeRemote }, dispatched::add)
        bootstrap.bootstrap()
        dispatched.clear()

        fakeRemote.put("pref_key_systemui_restart_time", 456L)
        bootstrap.refreshRemoteKey("pref_key_systemui_restart_time")

        assertEquals(456L, prefs.getLong("systemui_restart_time", 0L))
        assertTrue(dispatched.isEmpty())
    }

    @Test
    fun `native callback and explicit refresh share same implementation`() {
        val prefs = PrefMap()
        val dispatched = mutableListOf<String?>()
        val fakeRemote = primed("pref_key_test_val", 1)
        val bootstrap = PreferenceBootstrap.create(prefs, { fakeRemote }, dispatched::add)
        bootstrap.bootstrap()
        dispatched.clear()

        fakeRemote.put("pref_key_test_val", 99)
        listener(bootstrap)?.onSharedPreferenceChanged(fakeRemote, "pref_key_test_val")
        val fromListener = prefs.getInt("test_val", 0)
        val dispatchedByListener = dispatched.toList()

        fakeRemote.put("pref_key_test_val", 7)
        dispatched.clear()
        bootstrap.refreshRemoteKey("pref_key_test_val")

        assertEquals(99, fromListener)
        assertEquals(listOf("test_val"), dispatchedByListener)
        assertEquals(7, prefs.getInt("test_val", 0))
        assertEquals(dispatchedByListener, dispatched)
    }

    private fun primed(key: String, value: Any): FakeSharedPreferences {
        val fake = FakeSharedPreferences()
        fake.put(key, value)
        return fake
    }

    private fun listener(
        bootstrap: PreferenceBootstrap,
    ): android.content.SharedPreferences.OnSharedPreferenceChangeListener? {
        val field = PreferenceBootstrap::class.java.getDeclaredField("listener")
        field.isAccessible = true
        return field.get(bootstrap) as? android.content.SharedPreferences.OnSharedPreferenceChangeListener
    }
}
