package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.feature.NoLightUpOnChargeFeature
import tv.withaibuild.customiuizer.mods.utils.feature.NoLightUpOnChargeSystemUiFeature
import tv.withaibuild.customiuizer.utils.PrefMap

class NoLightUpOnChargeContractTest {

    private val chargingWakeReasons = listOf(
        "android.server.power:POWER",
        "android.server.power:PLUGGED:USB",
        "com.android.systemui:RAPID_CHARGE",
        "com.android.systemui:WIRELESS_CHARGE",
        "com.android.systemui:WIRELESS_RAPID_CHARGE",
    )

    @Test
    fun installGatesFollowA14ProductOptions() {
        assertFalse(NoLightUpOnChargeFeature.evaluateEnabled(prefs(1)))
        assertFalse(NoLightUpOnChargeSystemUiFeature.evaluateEnabled(prefs(1)))

        assertTrue(NoLightUpOnChargeFeature.evaluateEnabled(prefs(2)))
        assertTrue(NoLightUpOnChargeSystemUiFeature.evaluateEnabled(prefs(2)))

        assertFalse(NoLightUpOnChargeFeature.evaluateEnabled(prefs(3)))
        assertTrue(NoLightUpOnChargeSystemUiFeature.evaluateEnabled(prefs(3)))
    }

    @Test
    fun option2BlocksAllKnownChargingWakeReasons() {
        for (reason in chargingWakeReasons) {
            assertTrue(reason, SystemDisplayHooks.shouldSkipChargeWake(2, reason))
        }
        assertFalse(SystemDisplayHooks.shouldSkipChargeWake(2, "android.server.power:DREAM"))
    }

    @Test
    fun option1AndOption3NeverBlockWake() {
        for (option in intArrayOf(1, 3)) {
            for (reason in chargingWakeReasons) {
                assertFalse(
                    "option $option must not block $reason",
                    SystemDisplayHooks.shouldSkipChargeWake(option, reason),
                )
            }
        }
    }

    @Test
    fun liveChangeFromOption2ToOption3StopsBlockingWake() {
        for (reason in chargingWakeReasons) {
            assertTrue(SystemDisplayHooks.shouldSkipChargeWake(2, reason))
        }
        for (reason in chargingWakeReasons) {
            assertFalse(SystemDisplayHooks.shouldSkipChargeWake(3, reason))
        }
    }

    private fun prefs(option: Int): PrefMap =
        PrefMap().apply { put("system_nolightuponcharges", option) }
}
