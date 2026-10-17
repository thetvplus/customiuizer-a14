package tv.withaibuild.customiuizer.mods.utils.gesture

import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Builds an immutable [GestureConfig] from a [PrefMap] snapshot.
 *
 * All preference access happens inside this function.  The returned object is consumed
 * unchanged for the lifetime of the current gesture.
 */
object GestureConfigResolver {

    private const val KEY_SINGLE = "system_statusbarcontrols_single"
    private const val KEY_DUAL = "system_statusbarcontrols_dual"
    private const val KEY_BRIGHT_SENS = "system_statusbarcontrols_sens_bright"
    private const val KEY_VOL_SENS = "system_statusbarcontrols_sens_vol"
    private const val KEY_DOUBLE_TAP = "system_statusbarcontrols_dt_action"
    private const val KEY_DOUBLE_TAP_LEFT = "system_statusbarcontrols_dt_left_action"
    private const val KEY_DOUBLE_TAP_RIGHT = "system_statusbarcontrols_dt_right_action"
    private const val KEY_LONG_PRESS = "system_statusbarcontrols_longpress_action"
    private const val KEY_LONG_PRESS_VIBRATE = "system_statusbarcontrols_longpress_vibrate"
    private const val KEY_IGNORE_VIBRATE_OFF = "system_statusbarcontrols_longpress_vibrate_ignoreoff"

    private const val DEFAULT_ACTION = 1
    private const val DEFAULT_SENS = 2

    private val SENS_VALUES = mapOf(
        1 to 0.66f,
        2 to 1.0f,
        3 to 1.66f,
    )

    /** Returns the immutable gesture configuration for the given preference snapshot. */
    fun resolve(prefs: PrefMap): GestureConfig {
        val brightSens = prefs.getStringAsInt(KEY_BRIGHT_SENS, DEFAULT_SENS)
        val volSens = prefs.getStringAsInt(KEY_VOL_SENS, DEFAULT_SENS)
        val brightFactor = (SENS_VALUES[brightSens] ?: 1.0f) * 0.618f
        val volFactor = SENS_VALUES[volSens] ?: 1.0f

        return GestureConfig(
            singleAction = prefs.getStringAsInt(KEY_SINGLE, DEFAULT_ACTION),
            dualAction = prefs.getStringAsInt(KEY_DUAL, DEFAULT_ACTION),
            brightnessSensitivityFactor = brightFactor,
            volumeSensitivityFactor = volFactor,
            doubleTapAction = prefs.getInt(KEY_DOUBLE_TAP, DEFAULT_ACTION),
            doubleTapLeftAction = prefs.getInt(KEY_DOUBLE_TAP_LEFT, DEFAULT_ACTION),
            doubleTapRightAction = prefs.getInt(KEY_DOUBLE_TAP_RIGHT, DEFAULT_ACTION),
            longPressAction = prefs.getInt(KEY_LONG_PRESS, DEFAULT_ACTION),
            longPressVibrate = prefs.getBoolean(KEY_LONG_PRESS_VIBRATE, false),
            ignoreVibrateOff = prefs.getBoolean(KEY_IGNORE_VIBRATE_OFF, false),
        )
    }
}
