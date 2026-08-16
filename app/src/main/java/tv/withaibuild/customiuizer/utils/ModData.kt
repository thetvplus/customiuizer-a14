package tv.withaibuild.customiuizer.utils

import java.util.Locale

class ModData {

    enum class ModCat {
        pref_key_system,
        pref_key_launcher,
        pref_key_controls,
        pref_key_various
    }

    /**
     * Setting the title also derives [titleLower].
     *
     * The search filter runs over every mod on every keystroke, and the row renderer
     * needs the same lowered form to place the highlight span. Lowering on demand meant
     * a locale-aware transform and a fresh String per mod per keystroke, and again per
     * visible row while scrolling. The title is written once when the index is parsed,
     * so the lowered form is derived there instead.
     */
    var title: String = ""
        set(value) {
            field = value
            titleLower = value.lowercase(Locale.ROOT)
        }

    var titleLower: String = ""
        private set

    @JvmField
    var breadcrumbs: String = ""

    @JvmField
    var key: String = ""

    @JvmField
    var cat: ModCat = ModCat.pref_key_system

    @JvmField
    var sub: String? = null

    @JvmField
    var order: Int = 0
}
