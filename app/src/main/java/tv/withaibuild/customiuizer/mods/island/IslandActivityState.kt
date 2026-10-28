package tv.withaibuild.customiuizer.mods.island

/** Main-thread state, with transient, selected native activity and media slots. No queue. */
internal class IslandActivityState<T, M>(private val dismissal: MediaDismissal = MediaDismissal()) {
    /** Main-owned, capacity one; only the session identity survives a status-bar View rebuild. */
    class MediaDismissal { var key: Any? = null }
    class Lease internal constructor()
    private data class Transient<T>(val lease: Lease, val value: T, val deadline: Long)
    private var transient: Transient<T>? = null
    private var media: M? = null
    private var mediaKey: Any? = null
    private var activity: T? = null
    private var activityKey: Any? = null
    private var sequence = 0L
    private var mediaOrder = 0L
    private var mediaActivationKey: Any? = null
    private var activityOrder = 0L
    private var urgentActivity = false
    var allowed = true
    var mediaEligible = true

    /** Shared with the fixed live slots; only real starts/resumes advance it. */
    fun nextOrder(): Long = ++sequence

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
        if (key != null && key != mediaActivationKey && value != null) {
            mediaOrder = nextOrder(); mediaActivationKey = key
        }
        mediaKey = key
        media = value
    }

    fun activateMedia(key: Any) { if (mediaKey == key) mediaOrder = nextOrder() }

    fun setActivity(key: Any?, value: T?, order: Long? = null, urgent: Boolean = false) {
        activityOrder = order ?: if (key != activityKey && value != null) nextOrder() else activityOrder
        activityKey = key; activity = value; urgentActivity = urgent
    }

    private fun prefersMedia() = mediaEligible && media != null && mediaKey != dismissal.key &&
        (activity == null || !urgentActivity && mediaOrder > activityOrder)

    fun activity(now: Long): T? {
        expire(now)
        return if (allowed && transient == null && !prefersMedia()) activity else null
    }

    fun uncoveredMedia(): M? = if (allowed && mediaKey != dismissal.key) media else null

    fun identity(now: Long): Any? {
        expire(now)
        if (!allowed) return null
        return transient?.lease ?: if (prefersMedia()) mediaKey else activityKey
    }

    fun toast(now: Long): T? {
        expire(now)
        return if (allowed) transient?.value else null
    }

    fun media(now: Long): M? {
        expire(now)
        return if (allowed && transient == null && prefersMedia()) media else null
    }

    /** Preparation remains available while the player owns the foreground or its
     * Home admission is pending. It must not depend on current drawing eligibility. */
    fun mediaForReturn(now: Long, foreground: Boolean): M? {
        expire(now)
        return if (allowed && transient == null && mediaKey != dismissal.key &&
            (activity == null || !urgentActivity && (foreground || mediaOrder > activityOrder))) media else null
    }

    fun deadline(): Long? = transient?.deadline
    fun clearToast() { transient = null }
    fun dismiss() {
        if (transient != null) transient = null
        else if (activity != null && !prefersMedia()) { activity = null; activityKey = null }
        else dismissal.key = mediaKey
    }

    fun allowMediaAgain() { dismissal.key = null }
    fun isMediaDismissed(key: Any) = dismissal.key == key
    fun sessionDestroyed(key: Any) { if (dismissal.key == key) dismissal.key = null }

    fun clear(preserveDismissal: Boolean = false) {
        transient = null
        media = null
        mediaKey = null
        mediaActivationKey = null
        activity = null
        activityKey = null
        if (!preserveDismissal) dismissal.key = null
    }

    private fun expire(now: Long) {
        if (transient?.let { now >= it.deadline } == true) transient = null
    }
}
