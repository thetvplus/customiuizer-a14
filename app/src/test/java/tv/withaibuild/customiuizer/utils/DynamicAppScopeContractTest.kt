package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DynamicAppScopeContractTest {

    @Test
    fun onlyTargetAppHookListsRequireScope() {
        assertTrue(DynamicAppScope.requiresTargetAppScope("pref_key_system_statusbarcolor_apps"))
        assertTrue(DynamicAppScope.requiresTargetAppScope("system_nooverscroll_apps"))
        assertTrue(DynamicAppScope.requiresTargetAppScope("pref_key_controls_mediaplayer_apps"))
        assertTrue(DynamicAppScope.requiresTargetAppScope("various_alarmcompat_apps"))

        assertFalse(DynamicAppScope.requiresTargetAppScope("pref_key_system_cleanshare_apps"))
        assertFalse(DynamicAppScope.requiresTargetAppScope("pref_key_system_cleanopenwith_apps"))
        assertFalse(DynamicAppScope.requiresTargetAppScope("pref_key_system_forceclose_apps"))
        assertFalse(DynamicAppScope.requiresTargetAppScope("pref_key_system_ignorecalls_apps"))
        assertFalse(DynamicAppScope.requiresTargetAppScope("pref_key_launcher_renameapps_list"))
        assertFalse(DynamicAppScope.requiresTargetAppScope(null))
        assertFalse(DynamicAppScope.requiresTargetAppScope(""))
    }

    @Test
    fun packageNamesStripUserSuffixAndDedup() {
        assertEquals(
            listOf("com.example.one", "com.example.two"),
            DynamicAppScope.packageNamesOf(
                listOf("com.example.one", "com.example.two|0", "com.example.one|10", ""),
            ),
        )
    }

    @Test
    fun appSelectorRequestsOnlyThroughIsolatedManager() {
        val selector = source("app/src/main/java/tv/withaibuild/customiuizer/subs/AppSelector.kt")
        val scope = source("app/src/main/java/tv/withaibuild/customiuizer/utils/DynamicAppScope.kt")
        val manager = source("app/src/main/java/tv/withaibuild/customiuizer/utils/XposedServiceManager.kt")

        assertTrue(selector.contains("DynamicAppScope.requestForSelection"))
        assertTrue(selector.contains("DynamicAppScope.requiresTargetAppScope"))
        assertFalse(selector.contains("Api102ScopeRequester"))
        assertFalse(selector.contains("OnScopeEventListener"))
        assertFalse(scope.contains("OnScopeEventListener"))
        assertFalse(scope.contains(".requestScope("))
        assertTrue(scope.contains("XposedServiceManager.requestApi102Scope"))
        assertTrue(manager.contains("boundService.apiVersion < XposedService.API_102"))
        assertTrue(manager.contains("Api102ScopeRequester.request"))
    }

    private fun source(relativePath: String): String {
        var directory = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }
}
