package tv.withaibuild.customiuizer.mods

/**
 * MultiAction toggle IDs 1..12 map onto the existing GlobalActions Toggle* names.
 * Transport is [GlobalActions.commonSendAction]; this table only names the action suffix.
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

    @JvmStatic
    fun suffix(what: Int): String? = suffixes.getOrNull(what - 1)

    @JvmStatic
    fun broadcastAction(what: Int): String? {
        val name = suffix(what) ?: return null
        return "Toggle$name"
    }

    @JvmStatic
    fun idCount(): Int = suffixes.size
}
