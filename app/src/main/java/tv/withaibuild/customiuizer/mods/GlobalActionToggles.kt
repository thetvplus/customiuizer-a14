package tv.withaibuild.customiuizer.mods

import tv.withaibuild.customiuizer.R

/**
 * MultiAction toggle IDs 1..12 map onto the existing GlobalActions Toggle* names
 * and their UI labels. Transport is [GlobalActions.commonSendAction].
 */
object GlobalActionToggles {

    private val suffixes = arrayOf(
        "WiFi",
        "Bluetooth",
        "GPS",
        "NFC",
        "SoundProfile",
        "AutoBrightness",
        "AutoRotation",
        "Flashlight",
        "MobileData",
        "Hotspot",
        "ZenMode",
        "NightMode",
    )

    private val labelResIds = intArrayOf(
        R.string.array_global_toggle_wifi,
        R.string.array_global_toggle_bt,
        R.string.array_global_toggle_gps,
        R.string.array_global_toggle_nfc,
        R.string.array_global_toggle_sound,
        R.string.array_global_toggle_brightness,
        R.string.array_global_toggle_rotation,
        R.string.array_global_toggle_torch,
        R.string.array_global_toggle_mobiledata,
        R.string.system_statusbaricons_hotspot_title,
        R.string.system_statusbaricons_dnd_title,
        R.string.various_calluibright_night_title,
    )

    @JvmStatic
    fun suffix(what: Int): String? = suffixes.getOrNull(what - 1)

    @JvmStatic
    fun labelResId(what: Int): Int? = labelResIds.getOrNull(what - 1)

    @JvmStatic
    fun broadcastAction(what: Int): String? {
        val name = suffix(what) ?: return null
        return "Toggle$name"
    }

    @JvmStatic
    fun idCount(): Int = suffixes.size
}
