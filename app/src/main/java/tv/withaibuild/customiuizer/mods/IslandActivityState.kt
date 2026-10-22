package tv.withaibuild.customiuizer.mods

/** Main-thread state, with one transient slot and one native-selected media slot. No queue. */
internal class IslandActivityState<T, M>(private val dismissal: MediaDismissal = MediaDismissal()) {
    /** Main-owned, capacity one; only the session identity survives a status-bar View rebuild. */
    class MediaDismissal { var key: Any? = null }
    class Lease internal constructor()
    private data class Transient<T>(val lease: Lease, val value: T, val deadline: Long)
    private var transient: Transient<T>? = null
    private var media: M? = null
    private var mediaKey: Any? = null
    var allowed = true

    fun offer(value: T, now: Long, duration: Long): Lease {
        val lease = Lease()
        transient = Transient(lease, value, now + duration.coerceIn(1, 120_000))
        return lease
    }

    fun update(lease: Lease, value: T) {
        val current = transient ?: return
        if (current.lease === lease) transient = current.copy(value = value)
    }

    fun release(lease: Lease) {
        if (transient?.lease === lease) transient = null
    }

    fun owns(lease: Lease, now: Long): Boolean {
        expire(now)
        return transient?.lease === lease
    }

    fun setMedia(key: Any?, value: M?) {
        // Privacy/temporary removal must not undo an explicit dismissal of this session.
        if (key != null && key != dismissal.key) dismissal.key = null
        mediaKey = key
        media = value
    }

    fun identity(now: Long): Any? {
        expire(now)
        if (!allowed) return null
        return transient?.lease ?: mediaKey?.takeUnless { it == dismissal.key }
    }

    fun toast(now: Long): T? {
        expire(now)
        return if (allowed) transient?.value else null
    }

    fun media(now: Long): M? {
        expire(now)
        return if (allowed && transient == null &&
            mediaKey != dismissal.key) media else null
    }

    fun deadline(): Long? = transient?.deadline
    fun clearToast() { transient = null }
    fun dismiss() {
        if (transient != null) transient = null else dismissal.key = mediaKey
    }

    fun allowMediaAgain() { dismissal.key = null }
    fun sessionDestroyed(key: Any) { if (dismissal.key == key) dismissal.key = null }

    fun clear(preserveDismissal: Boolean = false) {
        transient = null
        media = null
        mediaKey = null
        if (!preserveDismissal) dismissal.key = null
    }

    private fun expire(now: Long) {
        if (transient?.let { now >= it.deadline } == true) transient = null
    }
}
