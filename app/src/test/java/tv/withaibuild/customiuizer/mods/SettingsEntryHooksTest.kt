package tv.withaibuild.customiuizer.mods

import android.content.Intent
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.utils.PrefMap
import tv.withaibuild.customiuizer.mods.utils.feature.SettingsMiuizerIconFeature

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsEntryHooksTest {
    class Header(@JvmField val intent: Intent?)
    class Page(private val target: Preference?) {
        fun findPreference(key: CharSequence): Preference? = target?.takeIf { it.key == key }
    }

    @Test fun removesOnlyTheExactUpdaterComponentAndPreservesOrderAndIdentity() {
        val ota = Header(Intent().setClassName("com.android.updater", "com.android.updater.MainActivity"))
        val other = Header(Intent().setClassName("com.xiaomi.discover", "com.xiaomi.market.ui.SettingsActivity"))
        val separator = Header(null)
        val target = Header(Intent().setClassName("com.xiaomi.discover", "com.xiaomi.market.ui.UpdateAppsActivity"))
        val headers = mutableListOf(ota, target, separator, other)
        SettingsEntryHooks.removeSystemAppUpdaterHeaders(headers)
        assertEquals(listOf(ota, separator, other), headers)
        SettingsEntryHooks.removeSystemAppUpdaterHeaders(headers)
        assertEquals(listOf(ota, separator, other), headers)
    }

    @Test fun removesNativePreferenceWithoutRemovingItsSharedCategory() {
        val context = RuntimeEnvironment.getApplication()
        val screen = PreferenceManager(context).createPreferenceScreen(context)
        val group = PreferenceCategory(context).apply { key = "development_settings_category" }
        screen.addPreference(group)
        val before = Preference(context).apply { key = "account_sync_settings" }
        val target = Preference(context).apply { key = "enterprise_mode_settings" }
        val after = Preference(context).apply { key = "development_settings" }
        listOf(before, target, after).forEach(group::addPreference)
        SettingsEntryHooks.removeEnterprisePreference(Page(target))
        assertNull(target.parent)
        assertEquals(2, group.preferenceCount)
        assertSame(before, group.getPreference(0))
        assertSame(after, group.getPreference(1))
        assertSame(screen, group.parent)
        SettingsEntryHooks.removeEnterprisePreference(Page(null))
        assertEquals(2, group.preferenceCount)
    }

    @Test fun removesAnEmptyWrapperOnlyAfterRemovingTheIdentifiedEntry() {
        val context = RuntimeEnvironment.getApplication()
        val screen = PreferenceManager(context).createPreferenceScreen(context)
        val group = PreferenceCategory(context)
        screen.addPreference(group)
        val target = Preference(context).apply { key = "enterprise_mode_settings" }
        group.addPreference(target)
        SettingsEntryHooks.removeEnterprisePreference(Page(target))
        assertEquals(0, screen.preferenceCount)
    }

    @Test fun bothEntryOptionsInstallWithTheModuleIconDisabled() {
        val prefs = PrefMap().apply { put("miuizer_settingsiconpos", "0") }
        assertFalse(SettingsMiuizerIconFeature.evaluateEnabled(prefs))
        for (key in listOf("various_disable_system_app_updater", "various_hide_enterprise_mode")) {
            prefs.put(key, true)
            assertTrue(SettingsMiuizerIconFeature.evaluateEnabled(prefs))
            prefs.put(key, false)
        }
    }

    @Test fun searchExcludesOnlyIdentifiedNodesAndRestoresWithoutChangingTheIndex() {
        val updater = Intent().setClassName("com.xiaomi.discover", "com.xiaomi.market.ui.UpdateAppsActivity")
        assertTrue(SettingsEntryHooks.hideSearchEntry("system_apps_updater", "", updater, true, false))
        assertFalse(SettingsEntryHooks.hideSearchEntry("system_apps_updater", "", updater, false, true))
        assertFalse(SettingsEntryHooks.hideSearchEntry("system_apps_updater", "", null, true, true))
        assertTrue(SettingsEntryHooks.hideSearchEntry("enterprise_mode", "enterprise_mode_settings", null, false, true))
        assertTrue(SettingsEntryHooks.hideSearchEntry("enterprise_mode", "ex_func_enterprise_mode", null, false, true))
        assertFalse(SettingsEntryHooks.hideSearchEntry("enterprise_privacy_settings", "enterprise_privacy", null, true, true))
        assertFalse(SettingsEntryHooks.hideSearchEntry("enterprise_mode", "unknown", null, true, true))
        assertFalse(SettingsEntryHooks.hideSearchEntry("enterprise_mode", "enterprise_mode_settings", null, false, false))
    }
}
