package tv.withaibuild.customiuizer.utils

import org.junit.Assert.*
import org.junit.Test

class PrefMapTest {
    @Test
    fun normalizesRemoteKeysOnlyWhenStored() {
        val prefs = PrefMap()

        prefs.put("pref_key_enabled", true)
        prefs.put("count", 3)

        assertTrue(prefs.getBoolean("enabled"))
        assertTrue(prefs.getBoolean("pref_key_enabled"))
        assertEquals(3, prefs.getInt("count", 0))
        assertTrue(prefs.containsKey("enabled"))
        assertFalse(prefs.containsKey("pref_key_enabled"))
    }

    @Test
    fun putAllAndRemoveUseTheSameCanonicalKey() {
        val prefs = PrefMap()
        val remoteValues = HashMap<String, Any>()
        remoteValues["pref_key_mode"] = "2"
        remoteValues["pref_key_timeout"] = 1500L

        prefs.putAll(remoteValues)

        assertEquals(2, prefs.getStringAsInt("mode", 0))
        assertEquals(1500L, prefs.getLong("timeout", 0L))
        prefs.remove("pref_key_mode")
        assertEquals(7, prefs.getStringAsInt("mode", 7))
    }

    @Test
    fun missingValuesReuseImmutableDefaults() {
        val prefs = PrefMap()

        assertEquals("fallback", prefs.getString("missing", "fallback"))
        assertFalse(prefs.getBoolean("missing"))
        assertSame(prefs.getStringSet("missing"), prefs.getStringSet("missing"))
    }

    @Test
    fun getStringAsIntCachesParsedValueAndInvalidatesOnChange() {
        val prefs = PrefMap()
        prefs.put("pref_key_mode", "3")

        assertEquals(3, prefs.getStringAsInt("mode", 0))
        assertEquals(3, prefs.getStringAsInt("pref_key_mode", 0))

        prefs.put("mode", "7")
        assertEquals(7, prefs.getStringAsInt("mode", 0))

        prefs.remove("mode")
        assertEquals(42, prefs.getStringAsInt("mode", 42))
    }

    @Test
    fun getStringAsIntReturnsDefaultForNonNumericString() {
        // This runs inside SystemUI and system_server hooks. A restored backup or a key
        // whose type changed between releases must degrade to the caller's default, not
        // throw out of the hook and take the host process down.
        val prefs = PrefMap()
        prefs.put("pref_key_label", "not_a_number")

        assertEquals(5, prefs.getStringAsInt("label", 5))
    }

    @Test
    fun getStringAsIntReturnsDefaultForTheWrongStoredType() {
        val prefs = PrefMap()
        prefs.put("pref_key_flag", true)
        prefs.put("pref_key_apps", setOf("a"))

        assertEquals(5, prefs.getStringAsInt("flag", 5))
        assertEquals(5, prefs.getStringAsInt("apps", 5))
    }

    @Test
    fun getStringAsIntKeepsAnswersStableForAnUnparseableValue() {
        // The bad value is parsed once and then answered from the cache; a two-second
        // ticker reading it must not pay for a failed parse on every tick.
        val prefs = PrefMap()
        prefs.put("pref_key_mode", "12x")

        assertEquals(1, prefs.getStringAsInt("mode", 1))
        assertEquals(1, prefs.getStringAsInt("mode", 1))
        assertEquals(9, prefs.getStringAsInt("mode", 9))

        // A repaired value must still take effect, so the negative entry cannot be sticky.
        prefs.put("pref_key_mode", "3")
        assertEquals(3, prefs.getStringAsInt("mode", 1))
    }

    @Test
    fun getStringAsIntAcceptsSignedAndNumericValues() {
        val prefs = PrefMap()
        prefs.put("pref_key_offset", "-4")
        prefs.put("pref_key_stored_as_int", 6)
        prefs.put("pref_key_stored_as_long", 7L)

        assertEquals(-4, prefs.getStringAsInt("offset", 0))
        assertEquals(6, prefs.getStringAsInt("stored_as_int", 0))
        assertEquals(7, prefs.getStringAsInt("stored_as_long", 0))
    }
}
