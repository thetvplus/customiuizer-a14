package tv.withaibuild.customiuizer.mods

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule

/**
 * Behavioral tests for launcher icon-style and folder close/privacy snapshots.
 */
class LauncherIconFolderSnapshotTest {

    private var savedPrefs: Map<String, Any> = emptyMap()

    @Before
    fun setUp() {
        savedPrefs = MainModule.mPrefs.getAll()
        MainModule.mPrefs.clear()
        LauncherIconHooks.refreshIconStyleSnapshot()
        LauncherFolderHooks.refreshFolderPreferences()
    }

    @After
    fun tearDown() {
        MainModule.mPrefs.clear()
        if (savedPrefs.isNotEmpty()) {
            MainModule.mPrefs.replaceSnapshot(savedPrefs)
        }
        LauncherIconHooks.refreshIconStyleSnapshot()
        LauncherFolderHooks.refreshFolderPreferences()
    }

    @Test
    fun iconStyleRebuildsOnPreferenceChangeWithoutHookReinstall() {
        MainModule.mPrefs.put("launcher_titlefontsize", 5)
        MainModule.mPrefs.put("launcher_titletopmargin", 0)
        LauncherIconHooks.refreshIconStyleSnapshot()
        assertEquals(5f, LauncherIconHooks.iconStyleConfig.titleFontSizeSp, 0.001f)
        assertEquals(0, LauncherIconHooks.iconStyleConfig.titleTopMargin)

        MainModule.mPrefs.put("launcher_titlefontsize", 12)
        MainModule.mPrefs.put("launcher_titletopmargin", 8)
        MainModule.mPrefs.put("launcher_renameapps_list:pkg|act|1", "Renamed")
        LauncherIconHooks.refreshIconStyleSnapshot()

        assertEquals(12f, LauncherIconHooks.iconStyleConfig.titleFontSizeSp, 0.001f)
        assertEquals(8, LauncherIconHooks.iconStyleConfig.titleTopMargin)
        assertEquals("Renamed", LauncherIconHooks.iconStyleConfig.renameTitles["launcher_renameapps_list:pkg|act|1"])
    }

    @Test
    fun folderCloseAndPrivacyRebuildFromPrefs() {
        MainModule.mPrefs.put("launcher_closefolders", "1")
        MainModule.mPrefs.put("launcher_folder_cols", 3)
        MainModule.mPrefs.put("launcher_folderspace", false)
        MainModule.mPrefs.put("launcher_privacyapps_gest", false)
        MainModule.mPrefs.put("launcher_closedrawer", false)
        LauncherFolderHooks.refreshFolderPreferences()

        assertEquals(1, folderInt("closeFoldersMode"))
        assertEquals(3, folderInt("folderCols"))
        assertFalse(folderBool("folderSpace"))
        assertFalse(folderBool("privacyGest"))
        assertFalse(folderBool("closeDrawer"))

        MainModule.mPrefs.put("launcher_closefolders", "2")
        MainModule.mPrefs.put("launcher_folder_cols", 4)
        MainModule.mPrefs.put("launcher_folderspace", true)
        MainModule.mPrefs.put("launcher_privacyapps_gest", true)
        MainModule.mPrefs.put("launcher_closedrawer", true)
        LauncherFolderHooks.refreshFolderPreferences()

        assertEquals(2, folderInt("closeFoldersMode"))
        assertEquals(4, folderInt("folderCols"))
        assertTrue(folderBool("folderSpace"))
        assertTrue(folderBool("privacyGest"))
        assertTrue(folderBool("closeDrawer"))
    }

    private fun folderInt(name: String): Int {
        val field = LauncherFolderHooks::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(null) as Int
    }

    private fun folderBool(name: String): Boolean {
        val field = LauncherFolderHooks::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(null) as Boolean
    }
}
