package tv.withaibuild.customiuizer.mods.island

/** One controller owns one policy. Sources describe content; none owns an animator.
 * Decisions are committed only after the shared window accepts the presentation. */
internal class IslandMotionCoordinator {
    enum class Arrival { NONE, STANDARD, CONTENT, NATIVE_WINDOW }
    private var identity: Any? = null
    private var source = IslandFormState.Source.IDLE
    private var expanded = false

    fun replacement(next: Any?) = identity != null && identity != next
    fun arrival(next: Any?, nextSource: IslandFormState.Source, entering: Boolean, resuming: Boolean,
        nativeWindow: Boolean, expanded: Boolean = false): Arrival = when {
        nativeWindow -> Arrival.NATIVE_WINDOW
        entering -> Arrival.STANDARD
        nextSource != IslandFormState.Source.IDLE && (resuming || source != nextSource ||
            identity != next || this.expanded != expanded) -> Arrival.CONTENT
        else -> Arrival.NONE
    }
    fun commit(next: Any?, nextSource: IslandFormState.Source, expanded: Boolean = false) {
        identity = next; source = nextSource; this.expanded = expanded
    }

    companion object {
        /** A message body needs its own line; manual sizing still uses the chosen long width. */
        fun prefersLong(content: IslandToastContent?): Boolean {
            content ?: return false
            val notification = content.notificationKey != null
            if (content.liveActivity != null) return true
            val detail = content.detail?.trim()
            if (detail.isNullOrEmpty() || detail == content.text) return false
            if (notification) return true
            return false
        }
    }
}
