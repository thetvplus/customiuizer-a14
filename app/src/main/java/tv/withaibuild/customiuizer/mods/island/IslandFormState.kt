package tv.withaibuild.customiuizer.mods.island

/** One presentation identity, no retained messages or extra windows. Main-thread owned. */
internal class IslandFormState {
    enum class Source { IDLE, MEDIA, NOTIFICATION, ACTIVITY, FEEDBACK }
    enum class Form { IDLE, SHORT, LONG }
    enum class Dismissal { COLLAPSE, SOURCE, IDLE }
    private var identity: Any? = null
    private var manualExpansion: Boolean? = null
    var source = Source.IDLE
        private set
    var expanded = false
        private set
    val form get() = if (identity == null) Form.IDLE else if (expanded) Form.LONG else Form.SHORT
    var idleSuppressed = false
        private set

    fun select(next: Any?, automaticExpanded: Boolean = false, source: Source = Source.FEEDBACK) {
        val nextSource = if (next == null) Source.IDLE else source
        if (identity != next || this.source != nextSource) {
            identity = next
            manualExpansion = null
        }
        this.source = nextSource
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

    /** A long form first collapses within its current owner. A second upward gesture
     * releases that source and selects the next live slot. */
    fun dismissal() = when {
        source == Source.IDLE -> Dismissal.IDLE
        expanded -> Dismissal.COLLAPSE
        else -> Dismissal.SOURCE
    }

    companion object {
        fun source(content: IslandToastContent?, media: Boolean): Source = when {
            content?.notificationKey != null -> Source.NOTIFICATION
            content?.liveActivity != null -> Source.ACTIVITY
            content != null -> Source.FEEDBACK
            media -> Source.MEDIA
            else -> Source.IDLE
        }
    }
}
