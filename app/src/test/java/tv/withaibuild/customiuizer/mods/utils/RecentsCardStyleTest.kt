package tv.withaibuild.customiuizer.mods.utils

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.Launcher
import tv.withaibuild.customiuizer.mods.utils.feature.LauncherPostAttachFeatures
import tv.withaibuild.customiuizer.mods.utils.feature.LauncherRecentsCardStyleFeature
import tv.withaibuild.customiuizer.mods.utils.feature.LauncherRecentsCardStyleFeatureId
import tv.withaibuild.customiuizer.utils.PrefMap
import java.io.File
import java.lang.reflect.Proxy

class RecentsCardStyleTest {

    @Test
    fun resolverSupportsBooleanAndLegacyStringAndNumber() {
        assertFalse(Launcher.isRecentsHideAppNameEnabled(PrefMap()))
        assertTrue(Launcher.isRecentsHideAppNameEnabled(PrefMap().apply {
            put("system_recents_card_style", true)
        }))
        assertFalse(Launcher.isRecentsHideAppNameEnabled(PrefMap().apply {
            put("system_recents_card_style", false)
        }))
        assertTrue(Launcher.isRecentsHideAppNameEnabled(PrefMap().apply {
            put("system_recents_card_style", "1")
        }))
        assertFalse(Launcher.isRecentsHideAppNameEnabled(PrefMap().apply {
            put("system_recents_card_style", "0")
        }))
        assertFalse(Launcher.isRecentsHideAppNameEnabled(PrefMap().apply {
            put("system_recents_card_style", "2")
        }))
        assertFalse(Launcher.isRecentsHideAppNameEnabled(PrefMap().apply {
            put("system_recents_card_style", "99")
        }))
        assertTrue(Launcher.isRecentsHideAppNameEnabled(PrefMap().apply {
            put("system_recents_card_style", 1)
        }))
        assertFalse(Launcher.isRecentsHideAppNameEnabled(PrefMap().apply {
            put("system_recents_card_style", 2)
        }))
    }

    @Test
    fun featureOnlyEnablesForHideAppName() {
        assertFalse(LauncherRecentsCardStyleFeature.evaluateEnabled(PrefMap()))
        assertTrue(LauncherRecentsCardStyleFeature.evaluateEnabled(PrefMap().apply {
            put("system_recents_card_style", true)
        }))
        assertTrue(LauncherRecentsCardStyleFeature.evaluateEnabled(PrefMap().apply {
            put("system_recents_card_style", "1")
        }))
        assertFalse(LauncherRecentsCardStyleFeature.evaluateEnabled(PrefMap().apply {
            put("system_recents_card_style", "2")
        }))
        assertFalse(LauncherRecentsCardStyleFeature.evaluateEnabled(PrefMap().apply {
            put("system_recents_card_style", 2)
        }))
    }

    @Test
    fun featureRoutesToLauncherAttachOnly() {
        val feature = LauncherPostAttachFeatures.all(fakePackageReadyParam(), PrefMap()).find {
            it.id == LauncherRecentsCardStyleFeatureId
        }
        assertNotNull(feature)
        assertEquals("system_recents_card_style", feature?.preferenceKey)
        assertEquals(FeatureTarget.LAUNCHER, feature?.target)
        assertEquals(InstallPhase.APPLICATION_ATTACHED, feature?.phase)
    }

    @Test
    fun stackedImplementationIsRemovedFromSource() {
        val source = launcherSource()
        assertFalse("installRecentsStackedCards must be removed", source.contains("installRecentsStackedCards"))
        assertFalse("RECENTS_STACK_OVERLAP_RATIO must be removed", source.contains("RECENTS_STACK_OVERLAP_RATIO"))
        assertFalse("resolveRecentsStackGap must be removed", source.contains("resolveRecentsStackGap"))
        assertFalse("resolveRecentsStackDepth must be removed", source.contains("resolveRecentsStackDepth"))
        assertFalse("resolveRecentsStackScale must be removed", source.contains("resolveRecentsStackScale"))
        assertFalse("resolveRecentsStackTranslationZ must be removed", source.contains("resolveRecentsStackTranslationZ"))
        assertFalse("TaskStackViewsAlgorithmHorizontal must be removed", source.contains("TaskStackViewsAlgorithmHorizontal"))
        assertFalse("TaskViewTransform must be removed", source.contains("TaskViewTransform"))
    }

    @Test
    fun hideAppNameHookIsPreserved() {
        val source = launcherSource()
        assertTrue("RecentsHideAppNameHook must exist", source.contains("fun RecentsHideAppNameHook("))
        assertTrue("TaskView target must remain", source.contains("com.miui.home.recents.views.TaskView"))
        assertTrue("onFinishInflate hook must remain", source.contains("onFinishInflate"))
        assertTrue("title View must be hidden", source.contains("\"title\""))
        assertTrue("title View must be set to GONE", source.contains("View.GONE"))
        assertFalse("old RecentsCardStyleHook must be removed", source.contains("fun RecentsCardStyleHook("))
        assertFalse("resolveRecentsCardStyle must be removed", source.contains("resolveRecentsCardStyle"))
        assertFalse("mode-2 branch must be removed", source.contains("boundedMode == 2"))
        assertFalse("stacked installer must be removed", source.contains("installRecentsStackedCards"))
    }

    @Test
    fun cardStyleArraysAndModePlumbingRemoved() {
        val source = launcherSource()
        assertFalse("resolveRecentsCardStyle must be removed", source.contains("resolveRecentsCardStyle"))
        assertFalse("RecentsCardStyleHook must be removed", source.contains("RecentsCardStyleHook("))

        val arrays = source("app/src/main/res/values/arrays.xml").readText()
        assertFalse("recents_card_styles array must be removed", arrays.contains("name=\"recents_card_styles\""))
        assertFalse("recents_card_styles_val array must be removed", arrays.contains("name=\"recents_card_styles_val\""))
    }

    @Test
    fun prefsXmlUsesCheckBoxForRecentsCardStyle() {
        val xml = source("app/src/main/res/xml/prefs_system.xml").readText()
        val regex = Regex(
            """<tv\.withaibuild\.customiuizer\.prefs\.(\w+)\s+android:key="pref_key_system_recents_card_style"\s+android:title="@string/system_recents_card_style_hide_title"(?:\s+android:summary="@string/system_recents_card_style_summ")?\s+android:defaultValue="false"\s*/>"""
        )
        val m = regex.find(xml)
        assertNotNull("recents card style preference must match the CheckBox form", m)
        assertEquals("CheckBoxPreferenceEx", m?.groupValues?.get(1))
        assertFalse("must not reference recents_card_styles entries", m?.value?.contains("recents_card_styles") ?: true)
    }

    private fun launcherSource(): String =
        source("app/src/main/java/tv/withaibuild/customiuizer/mods/Launcher.kt").readText()

    private fun source(path: String): File {
        var directory = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: error("Repository root not found for $path")
        }
    }

    private fun fakePackageReadyParam(): PackageReadyParam {
        return Proxy.newProxyInstance(
            PackageReadyParam::class.java.classLoader,
            arrayOf(PackageReadyParam::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getPackageName" -> "com.miui.home"
                "getClassLoader" -> ClassLoader.getSystemClassLoader()
                "isFirstPackage" -> true
                else -> null
            }
        } as PackageReadyParam
    }
}
