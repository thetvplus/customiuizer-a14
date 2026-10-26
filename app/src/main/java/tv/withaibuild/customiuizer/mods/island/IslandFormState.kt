package tv.withaibuild.customiuizer.mods.island

/** One presentation identity, no retained messages or extra windows. Main-thread owned. */
internal class IslandFormState {
    private var identity: Any? = null
    private var manualExpansion: Boolean? = null
    var expanded = false
        private set
    var idleSuppressed = false
        private set

    fun select(next: Any?, automaticExpanded: Boolean = false) {
        if (identity != next) {
            identity = next
            manualExpansion = null
        }
        expanded = next != null && (manualExpansion ?: automaticExpanded)
        if (next != null) idleSuppressed = false
    }

    fun expand(): Boolean {
        if (identity == null || expanded) return false
        manualExpansion = true
        expanded = true
        return true
    }

    fun collapse() { manualExpansion = false; expanded = false }
    fun dismissIdle() { idleSuppressed = true }
    fun wake() { idleSuppressed = false }
}
