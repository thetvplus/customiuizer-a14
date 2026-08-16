package tv.withaibuild.customiuizer.mods.utils.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.utils.PrefMap

class GestureConfigResolverTest {

    private fun prefs(vararg pairs: Pair<String, Any>): PrefMap {
        val map = PrefMap()
        map.replaceSnapshot(pairs.toMap())
        return map
    }

    @Test
    fun defaultValues() {
        val config = GestureConfigResolver.resolve(PrefMap())
        assertEquals(1, config.singleAction)
        assertEquals(1, config.dualAction)
        assertEquals(1.0f * 0.618f, config.brightnessSensitivityFactor, 0.0001f)
        assertEquals(1.0f, config.volumeSensitivityFactor, 0.0001f)
        assertEquals(1, config.doubleTapAction)
        assertEquals(1, config.doubleTapLeftAction)
        assertEquals(1, config.doubleTapRightAction)
        assertEquals(1, config.longPressAction)
        assertFalse(config.longPressVibrate)
        assertFalse(config.ignoreVibrateOff)
    }

    @Test
    fun allLegalValues() {
        val config = GestureConfigResolver.resolve(
            prefs(
                "system_statusbarcontrols_single" to 2,
                "system_statusbarcontrols_dual" to 3,
                "system_statusbarcontrols_sens_bright" to 3,
                "system_statusbarcontrols_sens_vol" to 1,
                "system_statusbarcontrols_dt_action" to 10,
                "system_statusbarcontrols_dt_left_action" to 20,
                "system_statusbarcontrols_dt_right_action" to 30,
                "system_statusbarcontrols_longpress_action" to 40,
                "system_statusbarcontrols_longpress_vibrate" to true,
                "system_statusbarcontrols_longpress_vibrate_ignoreoff" to true,
            )
        )
        assertEquals(2, config.singleAction)
        assertEquals(3, config.dualAction)
        assertEquals(1.66f * 0.618f, config.brightnessSensitivityFactor, 0.0001f)
        assertEquals(0.66f, config.volumeSensitivityFactor, 0.0001f)
        assertEquals(10, config.doubleTapAction)
        assertEquals(20, config.doubleTapLeftAction)
        assertEquals(30, config.doubleTapRightAction)
        assertEquals(40, config.longPressAction)
        assertTrue(config.longPressVibrate)
        assertTrue(config.ignoreVibrateOff)
    }

    @Test
    fun illegalSensitivityFallsBack() {
        val config = GestureConfigResolver.resolve(
            prefs(
                "system_statusbarcontrols_sens_bright" to 99,
                "system_statusbarcontrols_sens_vol" to -1,
            )
        )
        assertEquals(1.0f * 0.618f, config.brightnessSensitivityFactor, 0.0001f)
        assertEquals(1.0f, config.volumeSensitivityFactor, 0.0001f)
    }

    @Test
    fun singleAndDualDistinguish() {
        val single = GestureConfigResolver.resolve(prefs("system_statusbarcontrols_single" to 2))
        val dual = GestureConfigResolver.resolve(prefs("system_statusbarcontrols_dual" to 3))
        assertEquals(2, single.singleAction)
        assertEquals(1, single.dualAction)
        assertEquals(1, dual.singleAction)
        assertEquals(3, dual.dualAction)
    }

    @Test
    fun emptySnapshot_isSafe() {
        assertEquals(GestureConfig.DEFAULT, GestureConfigResolver.resolve(PrefMap()))
    }
}
