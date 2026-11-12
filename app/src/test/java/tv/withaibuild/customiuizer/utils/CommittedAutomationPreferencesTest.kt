package tv.withaibuild.customiuizer.utils

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.appbehavior.AppBehaviorPolicy
import tv.withaibuild.customiuizer.mods.locationauto.LocationAutomationOptions
import tv.withaibuild.customiuizer.mods.utils.PreferenceBootstrap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CommittedAutomationPreferencesTest {
    private val enabled = "pref_key_system_wifiauto_enabled"
    private val rules = "pref_key_system_wifiauto_rules"
    private fun read(prefs: FakeSharedPreferences, key: String? = null) =
        CommittedAutomationPreferences.values(CommittedAutomationPreferences.read(prefs, key))

    @Test fun singleChangesUseTypedReadsAndDeletionExplicitlyPublishesTheDefault() {
        val committed = FakeSharedPreferences().apply { put(enabled, true) }
        assertEquals(mapOf(enabled to true), read(committed, enabled))
        committed.edit().remove(enabled).commit()
        assertEquals(mapOf(enabled to false), read(committed, enabled))
        assertEquals(0, committed.getAllCount)
        assertThrows(IllegalArgumentException::class.java) { read(committed, "pref_key_other") }
        assertFalse(CommittedAutomationPreferences.owns("pref_key_system_appbehavior_unrecognized"))
    }

    @Test fun bulkSnapshotIsAtomicTypedAndRestrictedToTheAutomationKeys() {
        val apps = linkedSetOf("sample.player")
        val committed = FakeSharedPreferences().apply {
            put("pref_key_system_appbehavior_rotation_apps", apps)
            put("pref_key_system_appbehavior_block_limit", 30)
            put(rules, "[]")
            put("pref_key_other_private", "secret")
        }
        val snapshot = read(committed)
        assertEquals(1, committed.getAllCount)
        assertEquals(17, snapshot.size)
        assertEquals(30, snapshot["pref_key_system_appbehavior_block_limit"])
        assertEquals("[]", snapshot[rules])
        assertFalse(snapshot.containsKey("pref_key_other_private"))
        apps.clear()
        assertEquals(setOf("sample.player"), snapshot["pref_key_system_appbehavior_rotation_apps"])
    }

    @Test fun acknowledgedBulkAndSingleChangesReplaceAStaleSystemServerCacheBeforeDispatch() {
        val stale = FakeSharedPreferences().apply {
            put("pref_key_system_noscreenlock", "1")
            put(enabled, true)
            put("pref_key_system_appbehavior_brightness", true)
            put("pref_key_system_appbehavior_brightness_apps", setOf("sample.player"))
            put("pref_key_system_appbehavior_brightness_values", "sample.player=10")
        }
        val committed = FakeSharedPreferences().apply {
            put(enabled, false)
            put("pref_key_system_appbehavior_brightness", true)
            put("pref_key_system_appbehavior_brightness_apps", setOf("sample.player"))
            put("pref_key_system_appbehavior_brightness_values", "sample.player=45")
        }
        val prefs = PrefMap()
        val observations = mutableListOf<Pair<Boolean, Float>>()
        val bootstrap = PreferenceBootstrap.create(prefs, { stale }) {
            observations.add(prefs.getBoolean("system_wifiauto_enabled") to
                AppBehaviorPolicy.read(prefs).brightness("sample.player", "Player", .9f))
        }
        assertTrue(bootstrap.bootstrap())
        bootstrap.refreshRemoteKey(null, read(committed))
        assertEquals(listOf(false to .45f), observations)
        committed.put(enabled, true)
        bootstrap.refreshRemoteKey(enabled, read(committed, enabled))
        committed.edit().remove(enabled).commit()
        bootstrap.refreshRemoteKey(enabled, read(committed, enabled))
        assertEquals(listOf(false to .45f, true to .45f, false to .45f), observations)
        assertTrue(stale.getBoolean(enabled, false))
        assertEquals("sample.player=10", stale.getString("pref_key_system_appbehavior_brightness_values", ""))
    }

    @Test fun transportIsBoundedWithoutMakingOversizedRulesValid() {
        val committed = FakeSharedPreferences().apply {
            put(rules, "x".repeat(100000))
            put("pref_key_system_appbehavior_volume_apps", (0..999).map { "sample.player$it" }.toSet())
        }
        val snapshot = read(committed)
        assertEquals(65537, (snapshot[rules] as String).length)
        assertTrue(WifiAutomaticSettings.decode(snapshot[rules] as String).isEmpty())
        assertEquals(256, (snapshot["pref_key_system_appbehavior_volume_apps"] as Set<*>).size)
    }

    @Test fun committedLocationRulesReplaceStaleValuesBeforeObserversRun() {
        val prefix = "pref_key_system_locationauto_"
        val stale = FakeSharedPreferences().apply {
            put(prefix + "enabled", true)
            put(prefix + "close", true)
            put(prefix + "apps", setOf("sample.old"))
        }
        val committed = FakeSharedPreferences().apply {
            put(prefix + "enabled", true)
            put(prefix + "apps", setOf("sample.maps"))
        }
        val prefs = PrefMap()
        val observed = mutableListOf<LocationAutomationOptions>()
        val bootstrap = PreferenceBootstrap.create(prefs, { stale }) {
            observed.add(LocationAutomationOptions.read(prefs))
        }
        assertTrue(bootstrap.bootstrap())
        bootstrap.refreshRemoteKey(null, read(committed))
        committed.put(prefix + "mode", "block")
        bootstrap.refreshRemoteKey(prefix + "mode", read(committed, prefix + "mode"))
        assertEquals(listOf(
            LocationAutomationOptions(true, "allow", setOf("sample.maps"), false),
            LocationAutomationOptions(true, "block", setOf("sample.maps"), false),
        ), observed)
    }
}
