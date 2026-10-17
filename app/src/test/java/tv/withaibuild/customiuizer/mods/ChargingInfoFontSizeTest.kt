package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargingInfoFontSizeTest {

    @Test
    fun resolveChargingInfoFontSizeSp_defaultReturnsNull() {
        assertNull(SystemLockScreenHooks.resolveChargingInfoFontSizeSp(16))
    }

    @Test
    fun resolveChargingInfoFontSizeSp_validRangeMapsToHalfSp() {
        val cases = mapOf(
            17 to 8.5f,
            20 to 10.0f,
            21 to 10.5f,
            22 to 11.0f,
            24 to 12.0f,
            28 to 14.0f,
            40 to 20.0f
        )
        cases.forEach { (raw, expected) ->
            assertEquals(
                "raw=$raw should map to $expected sp",
                expected,
                SystemLockScreenHooks.resolveChargingInfoFontSizeSp(raw)
            )
        }
    }

    @Test
    fun resolveChargingInfoFontSizeSp_outOfRangeReturnsNull() {
        val invalidValues = listOf(
            -1,
            0,
            15,
            41,
            Int.MAX_VALUE
        )
        invalidValues.forEach { raw ->
            assertNull("raw=$raw should be rejected", SystemLockScreenHooks.resolveChargingInfoFontSizeSp(raw))
        }
    }
}
