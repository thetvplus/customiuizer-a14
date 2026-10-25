package tv.withaibuild.customiuizer.mods.island

/** One presentation identity, no retained messages or extra windows. Main-thread owned. */
internal class IslandFormState {
    private var identity: Any? = null
    var expanded = false
        private set
    var idleSuppressed = false
        private set

    fun select(next: Any?) {
        if (identity == next) return
        identity = next
        expanded = false
        if (next != null) idleSuppressed = false
    }

    fun expand(): Boolean {
        if (identity == null || expanded) return false
        expanded = true
        return true
    }

    fun collapse() { expanded = false }
    fun dismissIdle() { idleSuppressed = true }
    fun wake() { idleSuppressed = false }
}
