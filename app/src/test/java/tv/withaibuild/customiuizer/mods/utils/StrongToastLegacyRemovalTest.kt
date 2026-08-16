package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode
import tv.withaibuild.customiuizer.mods.utils.feature.ChargingInfoFeatureId
import tv.withaibuild.customiuizer.mods.utils.feature.DisableFoldNotificationsFeatureId
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastPresentationFeature
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastPresentationFeatureId
import tv.withaibuild.customiuizer.mods.utils.feature.SystemUiFeatures
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Proxy

class StrongToastLegacyRemovalTest {

    @Test
    fun legacyDisableStrongToastFeature_isNotRegistered() {
        val features = SystemUiFeatures.all(fakePackageReadyParam(), PrefMap())
        assertNull(features.find { it.preferenceKey == "system_notif_disable_strong_toast" })
        assertNull(features.find { it.name == "Disable Strong Toast" })
        assertTrue(features.none { it.preferenceKey in legacyKeys })
    }

    @Test
    fun legacyKeysDoNotEnableAnySystemUiFeature() {
        val legacyAlways = PrefMap().apply {
            put("system_notif_disable_strong_toast", "true")
            put("system_notif_disable_strong_toast_always", "true")
        }
        val legacyDnd = PrefMap().apply {
            put("system_notif_disable_strong_toast", "true")
            put("system_notif_disable_strong_toast_always", "false")
            put("system_notif_disable_strong_toast_dnd", "true")
        }

        for (prefs in listOf(legacyAlways, legacyDnd)) {
            val enabled = SystemUiFeatures.all(fakePackageReadyParam(), prefs)
                .filter { it.preferenceKey in legacyKeys }
            assertTrue("Legacy keys must not enable any SystemUi feature", enabled.isEmpty())
        }
    }

    @Test
    fun newStrongToastPresentationFeature_remainsRegistered() {
        val feature = SystemUiFeatures.all(fakePackageReadyParam(), PrefMap()).find {
            it.id == StrongToastPresentationFeatureId
        }
        assertNotNull(feature)
        assertEquals("system_strong_toast_mode", feature?.preferenceKey)
    }

    @Test
    fun featureIdsDidNotRenumber() {
        assertEquals(105, DisableFoldNotificationsFeatureId.id)
        assertEquals(107, ChargingInfoFeatureId.id)
        assertEquals(246, StrongToastPresentationFeatureId.id)
    }

    @Test
    fun newModeResolution_isUnchanged() {
        assertEquals(StrongToastPresentationMode.SYSTEM_DEFAULT, StrongToastPresentationMode.fromPreference(0))
        assertEquals(StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT, StrongToastPresentationMode.fromPreference(1))
        assertEquals(StrongToastPresentationMode.HIDE, StrongToastPresentationMode.fromPreference(2))
    }

    @Test
    fun newFeatureEvaluatesCorrectly() {
        assertTrue("StrongToast must install in default mode for live updates",
            StrongToastPresentationFeature.evaluateEnabled(PrefMap()))
        assertTrue("StrongToast must install in default mode for live updates",
            StrongToastPresentationFeature.evaluateEnabled(PrefMap().apply {
                put("system_strong_toast_mode", "0")
            }))
        assertTrue(StrongToastPresentationFeature.evaluateEnabled(PrefMap().apply {
            put("system_strong_toast_mode", "1")
        }))
        assertTrue(StrongToastPresentationFeature.evaluateEnabled(PrefMap().apply {
            put("system_strong_toast_mode", "2")
        }))
    }

    private companion object {
        val legacyKeys = setOf(
            "system_notif_disable_strong_toast",
            "system_notif_disable_strong_toast_always",
            "system_notif_disable_strong_toast_dnd",
        )
    }

    private fun fakePackageReadyParam(): io.github.libxposed.api.XposedModuleInterface.PackageReadyParam {
        return Proxy.newProxyInstance(
            io.github.libxposed.api.XposedModuleInterface.PackageReadyParam::class.java.classLoader,
            arrayOf(io.github.libxposed.api.XposedModuleInterface.PackageReadyParam::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getPackageName" -> "com.android.systemui"
                "getClassLoader" -> ClassLoader.getSystemClassLoader()
                "isFirstPackage" -> true
                else -> null
            }
        } as io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
    }
}
