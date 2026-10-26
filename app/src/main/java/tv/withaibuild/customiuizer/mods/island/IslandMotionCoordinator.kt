package tv.withaibuild.customiuizer.mods.island

/** One controller owns one policy. Sources describe content; none owns an animator.
 * Decisions are committed only after the shared window accepts the presentation. */
internal class IslandMotionCoordinator {
    enum class Kind { IDLE, MEDIA, ACTIVITY, NOTICE }
    enum class Arrival { NONE, STANDARD, CONTENT, NATIVE_WINDOW }
    private var identity: Any? = null
    private var kind = Kind.IDLE

    fun replacement(next: Any?) = identity != null && identity != next
    fun arrival(next: Any?, nextKind: Kind, entering: Boolean, resuming: Boolean,
        nativeWindow: Boolean): Arrival = when {
        nativeWindow -> Arrival.NATIVE_WINDOW
        entering -> Arrival.STANDARD
        nextKind != Kind.IDLE && (resuming || kind == Kind.IDLE ||
            nextKind == Kind.ACTIVITY && identity != next) -> Arrival.CONTENT
        else -> Arrival.NONE
    }
    fun commit(next: Any?, nextKind: Kind) { identity = next; kind = nextKind }

    companion object {
        /** A message body needs its own line; manual sizing still uses the chosen long width. */
        fun prefersLong(content: IslandToastContent?, automaticWidth: Boolean = true): Boolean {
            content ?: return false
            val notification = content.notificationKey != null
            if (!notification && !automaticWidth) return false
            if (!notification && content.status && content.text.length >= 12) return true
            val detail = content.detail?.trim()
            if (detail.isNullOrEmpty() || detail == content.text) return false
            if (notification) return true
            val activity = content.liveActivity ?: return false
            return (activity.kind in IslandLiveKind.clockKinds || activity.kind == IslandLiveKind.RECORDER) &&
                activity.clock?.pausedMillis != null
        }
    }
}
