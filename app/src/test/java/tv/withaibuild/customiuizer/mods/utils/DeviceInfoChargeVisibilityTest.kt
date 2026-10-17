package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceInfoChargeVisibilityTest {

    private val notExist = Any()

    @Test
    fun missingBatteryStatusHidesWhenInChargeOnly() {
        assertFalse(
            DeviceInfoChargeVisibility.shouldShowWhenInChargeOnly(null, notExist) { true }
        )
        assertFalse(
            DeviceInfoChargeVisibility.shouldShowWhenInChargeOnly(notExist, notExist) { true }
        )
    }

    @Test
    fun missingStatusStaysHiddenOnRepeatedTicks() {
        repeat(3) {
            assertFalse(
                DeviceInfoChargeVisibility.shouldShowWhenInChargeOnly(notExist, notExist) { true }
            )
        }
    }

    @Test
    fun chargingStatusShowsWhenInChargeOnly() {
        val status = Any()
        assertTrue(
            DeviceInfoChargeVisibility.shouldShowWhenInChargeOnly(status, notExist) { true }
        )
        assertFalse(
            DeviceInfoChargeVisibility.shouldShowWhenInChargeOnly(status, notExist) { false }
        )
    }

    @Test
    fun unknownChargingResultHidesWhenInChargeOnly() {
        val status = Any()
        assertFalse(
            DeviceInfoChargeVisibility.shouldShowWhenInChargeOnly(status, notExist) { null }
        )
    }

    @Test
    fun prefersMiuiChargeUtilsThenKeyguardFallback() {
        val miui = String::class.java
        val keyguard = Int::class.java
        val found = DeviceInfoChargeVisibility.resolveChargeUtilsClass(
            ClassLoader.getSystemClassLoader()
        ) { name, _ ->
            when (name) {
                "com.miui.charge.ChargeUtils" -> miui
                "com.android.keyguard.charge.ChargeUtils" -> keyguard
                else -> null
            }
        }
        assertSame(miui, found)
    }

    @Test
    fun fallsBackToKeyguardChargeUtils() {
        val keyguard = Int::class.java
        val found = DeviceInfoChargeVisibility.resolveChargeUtilsClass(
            ClassLoader.getSystemClassLoader()
        ) { name, _ ->
            if (name == "com.android.keyguard.charge.ChargeUtils") keyguard else null
        }
        assertSame(keyguard, found)
    }
}
