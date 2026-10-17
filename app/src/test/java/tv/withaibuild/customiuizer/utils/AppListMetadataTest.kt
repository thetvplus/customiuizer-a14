package tv.withaibuild.customiuizer.utils

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListMetadataTest {
    @Test
    fun packageActivityAndInstalledAppKeysPreserveExistingCacheIdentity() {
        val app = AppData().apply { pkgName = "example.app" }
        for ((activity, expected) in listOf("" to "example.app", "-" to "example.app|-",
            "example.app.Main" to "example.app|example.app.Main")) {
            app.actName = activity
            app.prepareForList()
            assertEquals(expected, app.iconKey)
        }
        app.user = 999
        app.prepareForList()
        assertEquals("example.app|example.app.Main", app.iconKey)
    }

    @Test
    fun preparationRefreshesDerivedFieldsAndUsesRootLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val app = AppData().apply { label = "IMEI"; actName = "MainActivity" }
            app.prepareForList()
            assertEquals("imei", app.labelLower)
            assertEquals("mainactivity", app.actNameLower)
            app.label = "蓝牙"
            app.actName = ""
            app.prepareForList()
            assertEquals("蓝牙", app.labelLower)
            assertEquals("", app.actNameLower)
            assertEquals("", app.iconKey)
        } finally {
            Locale.setDefault(previous)
        }
    }

    // Android view inflation is not exercised by the plain JVM suite. These contracts tie
    // the tested preparation to every loader caller and keep resource loads out of binding.
    private fun source(name: String): String = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/utils/$name.kt"))

    @Test
    fun allIconAdaptersPrepareMetadataAndUseTheLoaderCacheKey() {
        for (name in listOf("AppDataAdapter", "LockedAppAdapter", "PrivacyAppAdapter", "ResolveInfoAdapter")) {
            val text = source(name)
            assertTrue("$name must prepare metadata", text.contains("prepareForList()"))
            assertTrue("$name must use the loader key", text.contains("Helpers.memoryCache[ad.iconKey]"))
        }
    }

    @Test
    fun shortcutBindingAndFilteringUseAdapterOwnedMetadata() {
        val text = source("ResolveInfoAdapter")
        val hotPaths = text.substring(text.indexOf("override fun getView"))
        assertFalse(hotPaths.contains("loadLabel("))
        assertFalse(hotPaths.contains("AppData()"))
        assertTrue(text.contains("List<Entry>"))
        assertTrue(text.contains("filteredAppList[position].resolveInfo"))
        assertTrue(text.contains("entry.app.labelLower.contains(filterString)"))
    }

    @Test
    fun selectedFirstAdaptersUseSingleReadOrderingAndPreparedSearchKeys() {
        for (name in listOf("LockedAppAdapter", "PrivacyAppAdapter")) {
            val text = source(name)
            assertTrue(text.contains("selectedAppsFirst(filteredAppList)"))
            assertTrue(text.contains("app.labelLower.contains(filterString)"))
            assertFalse(text.contains("sortWith"))
            assertFalse(text.contains("CopyOnWriteArrayList"))
        }
    }
}
