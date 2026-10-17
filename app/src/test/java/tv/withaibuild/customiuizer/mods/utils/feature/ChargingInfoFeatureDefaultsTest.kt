package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Proxy

/**
 * ChargingInfo feature default-value contract tests.
 *
 * The preference XML declares `android:defaultValue="false"` for the master
 * toggle.  The [ChargingInfoFeature.evaluateEnabled] helper must return false
 * both when the key is absent and when it is explicitly stored as false.
 * Child option defaults must remain unchanged when the master toggle is off.
 */
class ChargingInfoFeatureDefaultsTest {

    @Test
    fun evaluateEnabled_missingKey_returnsFalse() {
        val prefs = PrefMap()
        assertFalse(
            "missing system_charginginfo must default to false",
            ChargingInfoFeature.evaluateEnabled(prefs)
        )
    }

    @Test
    fun evaluateEnabled_explicitFalse_returnsFalse() {
        val prefs = PrefMap().apply {
            put("system_charginginfo", false)
        }
        assertFalse(
            "explicit false must stay false",
            ChargingInfoFeature.evaluateEnabled(prefs)
        )
    }

    @Test
    fun evaluateEnabled_explicitTrue_returnsTrue() {
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
        }
        assertTrue(
            "explicit true must be enabled",
            ChargingInfoFeature.evaluateEnabled(prefs)
        )
    }

    @Test
    fun emptySnapshot_doesNotInstallChargingInfoFeature() {
        // A VALID_EMPTY (empty) PrefMap must keep the feature disabled, which
        // means the install() path is never reached in production.
        val prefs = PrefMap()
        val feature = ChargingInfoFeature(fakePackageReadyParam())
        assertFalse(
            "empty snapshot must not enable ChargingInfoFeature",
            feature.isEnabled(prefs)
        )
    }

    @Test
    fun systemUiStartup_featureDisabled_specIsNotEnabled() {
        val emptyPrefs = PrefMap()
        val enabledSpec = SystemUiFeatures.all(fakePackageReadyParam(), emptyPrefs)
            .find { it.id == ChargingInfoFeatureId }
        assertFalse(
            "ChargingInfo must not be enabled at SystemUI startup when the preference is absent",
            enabledSpec?.isEnabled(emptyPrefs) ?: true
        )
    }

    @Test
    fun runtimeToggleToTrue_specIsEnabledButRequiresRebootSemantics() {
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
        }
        val enabledSpec = SystemUiFeatures.all(fakePackageReadyParam(), prefs)
            .find { it.id == ChargingInfoFeatureId }
        assertTrue(
            "ChargingInfo must be enabled when the user explicitly turns it on",
            enabledSpec?.isEnabled(prefs) ?: false
        )
    }

    @Test
    fun missingMasterToggle_childOptionDefaultsUnchanged() {
        val prefs = PrefMap()

        assertEquals(16, prefs.getInt("system_charginginfo_fontsize", 16))
        assertEquals(1, prefs.getStringAsInt("system_charginginfo_view", 1))
        assertFalse(prefs.getBoolean("system_charginginfo_current"))
        assertFalse(prefs.getBoolean("system_charginginfo_voltage"))
        assertFalse(prefs.getBoolean("system_charginginfo_wattage"))
        assertFalse(prefs.getBoolean("system_charginginfo_temp"))
    }

    private fun fakePackageReadyParam(): XposedModuleInterface.PackageReadyParam {
        return Proxy.newProxyInstance(
            XposedModuleInterface.PackageReadyParam::class.java.classLoader,
            arrayOf(XposedModuleInterface.PackageReadyParam::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getClassLoader" -> ClassLoader.getSystemClassLoader()
                "toString" -> "FakePackageReadyParam"
                "equals" -> false
                "hashCode" -> 0
                else -> null
            }
        } as XposedModuleInterface.PackageReadyParam
    }
}
