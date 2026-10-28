package tv.withaibuild.customiuizer.mods.island

/** One presentation identity, no retained messages or extra windows. Main-thread owned. */
internal class IslandFormState {
    enum class Source { IDLE, MEDIA, NOTIFICATION, ACTIVITY, FEEDBACK }
    enum class Form { IDLE, SHORT, LONG }
    enum class Dismissal { SOURCE, IDLE }
    private var identity: Any? = null
    private var manualExpansion: Boolean? = null
    private var interacting = false
    private var dwell = IslandMotion.EXPANDED_DWELL
    var expansionDeadline: Long? = null
        private set
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
            expansionDeadline = null
        }
        this.source = nextSource
        expanded = next != null && (manualExpansion ?: automaticExpanded)
        if (next != null) idleSuppressed = false
    }

    fun expand(now: Long = 0L, dwell: Long = IslandMotion.EXPANDED_DWELL): Boolean {
        if (identity == null || expanded) return false
        manualExpansion = true
        expanded = true
        this.dwell = dwell.coerceAtLeast(IslandMotion.EXPANDED_DWELL)
        expansionDeadline = if (interacting) null else now + this.dwell
        return true
    }

    fun interact(active: Boolean, now: Long) {
        if (interacting == active) return
        interacting = active
        if (manualExpansion == true && expanded) expansionDeadline = if (active) null else now + dwell
    }

    fun expireExpansion(now: Long) {
        if (expansionDeadline?.let { now >= it } == true) collapse()
    }

    fun collapse() { manualExpansion = false; expanded = false; expansionDeadline = null }
    fun dismissIdle() { idleSuppressed = true }
    fun wake() { idleSuppressed = false }

    /** One upward gesture dismisses the selected source, regardless of its width. */
    fun dismissal() = when {
        source == Source.IDLE -> Dismissal.IDLE
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
