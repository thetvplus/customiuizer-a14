package tv.withaibuild.customiuizer.utils

/** Shared wire/data contract. Safe to load in the settings process without the Hook API. */
internal object VpnAutoSwitchSettings {
    const val PREFIX = "system_vpnauto_"
    const val OPTIONS = "tv.withaibuild.customiuizer.r14.action.VPN_AUTO_OPTIONS"
    const val LEGACY = "[Legacy VPN]"
    const val CLOSE_DELAY = 3_000L
    const val START_TIMEOUT = 20_000L
    const val MAX_APPS = 256
    const val MAX_TASKS = 128

    data class Target(val kind: String, val id: String) {
        val valid: Boolean get() = id.isNotBlank() && id.length <= 256 && (kind == "app" || kind == "system")
    }

    data class Options(val enabled: Boolean, val target: Target, val apps: Set<String>, val autoClose: Boolean) {
        val configured: Boolean = enabled && target.valid && apps.isNotEmpty() && apps.size <= MAX_APPS
        companion object {
            fun read(prefs: PrefMap): Options {
                val values = prefs.getAll()
                val kind = values["${PREFIX}kind"] as? String ?: "app"
                val target = Target(kind, values["${PREFIX}${if (kind == "system") "profile" else "client"}"] as? String ?: "")
                val apps = (values["${PREFIX}apps"] as? Set<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
                val selected = if (apps.size > MAX_APPS) emptySet() else apps.filterTo(HashSet()) {
                    it.isNotBlank() && (target.kind != "app" || it != target.id)
                }
                return Options(values["${PREFIX}enabled"] as? Boolean ?: false,
                    target, selected,
                    values["${PREFIX}close"] as? Boolean ?: true)
            }
        }
    }
}
