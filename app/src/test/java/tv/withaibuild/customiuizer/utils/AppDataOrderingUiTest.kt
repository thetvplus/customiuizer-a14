package tv.withaibuild.customiuizer.utils

import android.app.Application
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppDataOrderingUiTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun apps() = ArrayList<AppData>().apply {
        repeat(80) { index -> add(AppData().apply {
            pkgName = "app.${index / 2}"
            actName = "Activity.${index / 2}"
            label = "Name ${index / 2}"
            user = if (index % 2 == 0) 0 else 999
        }) }
    }
    private fun items(adapter: AppDataAdapter) = (0 until adapter.count).map(adapter::getItem)
    private fun filter(adapter: AppDataAdapter, query: String) {
        val filter = adapter.filter
        val results = filter.javaClass.getDeclaredMethod("performFiltering", CharSequence::class.java)
            .apply { isAccessible = true }.invoke(filter, query)
        filter.javaClass.getDeclaredMethod("publishResults", CharSequence::class.java, results.javaClass)
            .apply { isAccessible = true }.invoke(filter, query, results)
    }
    @After fun clean() { AppHelper.appPrefs = null }

    @Test fun positiveNegativeAndUserSelectionsKeepStableGroupsAcrossFiltering() {
        val rows = apps()
        val chosen = setOf("app.3", "app.8|0", "app.8|999", "app.15|999")
        val excluded = setOf("app.6", "app.9")
        for ((key, blackWhite) in listOf("pref_key_selection" to false, "pref_key_selection" to true,
            "pref_key_system_cleanshare_apps" to false)) {
            AppHelper.appPrefs = context.getSharedPreferences("ordering", 0).also {
                it.edit().clear().putStringSet(key, chosen).putStringSet(key + "_black", excluded).commit()
            }
            val multiUser = key == "pref_key_system_cleanshare_apps"
            val normalized = chosen.map { if ('|' !in it && multiUser) "$it|0" else it }.toSet()
            val admitted = rows.filter { multiUser || it.user == 0 }
            fun selected(app: AppData) = if (blackWhite) app.pkgName in chosen || app.pkgName in excluded
                else if (multiUser) "${app.pkgName}|${app.user}" in normalized
                else app.pkgName in chosen || "${app.pkgName}|0" in chosen
            val adapter = AppDataAdapter(context, rows, AppHelper.AppAdapterType.Mutli, key, blackWhite)
            for (query in listOf("", "Name 1", "missing", "")) {
                filter(adapter, query)
                val matching = admitted.filter { it.label.contains(query, ignoreCase = true) }
                val expected = matching.filter(::selected) + matching.filterNot(::selected)
                assertEquals("$key / blackWhite=$blackWhite / query=$query",
                    expected.map { "${it.pkgName}|${it.user}" }, items(adapter).map { "${it.pkgName}|${it.user}" })
            }
        }
        assertEquals("Sorting must not mutate the cached source", "app.0", rows.first().pkgName)
    }

    @Test fun standaloneDefaultRemainsFirstAndChangingSelectionDoesNotReuseOldMembership() {
        val rows = apps()
        val prefs = context.getSharedPreferences("standalone-ordering", 0)
        AppHelper.appPrefs = prefs
        prefs.edit().clear().putString("pref_key_pick", "app.8|Activity.8").putInt("pref_key_pick_user", 999).commit()
        val adapter = AppDataAdapter(context, rows, AppHelper.AppAdapterType.Standalone, "pref_key_pick")
        assertEquals("", adapter.getItem(0).pkgName)
        assertSame(rows[17], adapter.getItem(1))
        prefs.edit().putString("pref_key_pick", "app.15|Activity.15").putInt("pref_key_pick_user", 0).commit()
        adapter.updateSelectedApps()
        filter(adapter, "Name 1")
        assertEquals("", adapter.getItem(0).pkgName)
        assertSame(rows[30], adapter.getItem(1))
        assertEquals(listOf(adapter.getItem(0)) + rows.filter { it.label.startsWith("Name 1") }
            .sortedBy { if (it === rows[30]) 0 else 1 }, items(adapter))
    }
}
