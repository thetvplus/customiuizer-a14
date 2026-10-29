package tv.withaibuild.customiuizer.mods.vpn

import tv.withaibuild.customiuizer.utils.VpnAutoSwitchSettings as Settings

/** Single-handler state. No process scans, clock loop, VPN configuration or Android dependencies. */
internal class VpnAutoSwitchPolicy(options: Settings.Options) {
    var options = options
        private set
    data class Session(val target: Settings.Target, val token: Any) {
        fun same(other: Session?): Boolean = other != null && target == other.target && token === other.token
    }
    data class Snapshot(val known: Boolean, val protected: Boolean = false, val session: Session? = null, val connected: Boolean = true)
    enum class Action { NONE, START, STOP }
    enum class Status { READY, CONNECTING, OWNED, EXISTING, PROTECTED, PAUSED, FAILED, UNAVAILABLE }
    private data class TaskKey(val id: Int, val pkg: String)
    private val tasks = HashMap<TaskKey, Any>()
    private var overflow = false
    private var suppressed = false
    private var pendingUntil = 0L
    private var idleSince: Long? = null
    val pendingStartDeadline: Long get() = pendingUntil
    var owned: Session? = null
        private set
    var status = Status.READY
        private set
    val hasTasks: Boolean get() = tasks.isNotEmpty()

    fun forOptions(updated: Settings.Options): VpnAutoSwitchPolicy = VpnAutoSwitchPolicy(updated).also { next ->
        if (options.configured && updated.configured) {
            tasks.forEach { (key, owner) -> if (key.pkg in updated.apps) next.tasks[key] = owner }
            next.overflow = overflow
        }
    }

    fun continuePendingStart(until: Long) {
        check(owned == null && pendingUntil == 0L)
        pendingUntil = until
    }

    fun update(updated: Settings.Options, now: Long) {
        require(updated.configured && updated.target == options.target)
        val rearmed = !options.autoClose && updated.autoClose
        options = updated
        tasks.entries.removeIf { it.key.pkg !in updated.apps }
        if (tasks.isEmpty()) {
            suppressed = false
            if (idleSince == null || rearmed) idleSince = now
        }
    }

    val deadline: Long get() = when {
        pendingUntil > 0 -> pendingUntil
        owned != null && tasks.isEmpty() && !overflow && options.autoClose -> (idleSince ?: 0L) + options.closeDelayMs
        else -> 0
    }

    fun nextDelay(snapshot: Snapshot, now: Long): Long? = deadline.takeIf { snapshot.known && it > now }?.minus(now)

    fun resumed(pkg: String, user: Int, taskId: Int, taskOwner: Any): Boolean {
        if (!options.configured || user != 0 || taskId < 0 || pkg !in options.apps) return false
        val key = TaskKey(taskId, pkg)
        if (tasks[key] === taskOwner) return false
        tasks.entries.removeIf { it.key.id == taskId && it.value !== taskOwner }
        if (tasks.size >= Settings.MAX_TASKS && key !in tasks) {
            // Unknown live tasks must never cause a premature automatic disconnect.
            overflow = true
            return false
        }
        tasks[key] = taskOwner
        idleSince = null
        return true
    }

    fun removed(taskId: Int, user: Int, now: Long, taskOwner: Any? = null): Boolean {
        if (user != 0 || !tasks.entries.removeIf { it.key.id == taskId && (taskOwner == null || it.value === taskOwner) }) return false
        return afterRemoval(now)
    }

    private fun afterRemoval(now: Long): Boolean {
        if (tasks.isEmpty()) {
            suppressed = false
            idleSince = now
        }
        return true
    }

    fun evaluate(snapshot: Snapshot, now: Long): Action {
        if (!snapshot.known) { status = Status.UNAVAILABLE; return Action.NONE }
        if (snapshot.protected) {
            owned = null; pendingUntil = 0
            if (tasks.isNotEmpty()) suppressed = true
            status = Status.PROTECTED
            return Action.NONE
        }
        if (owned != null && !owned!!.same(snapshot.session)) {
            owned = null
            if (tasks.isNotEmpty()) suppressed = true
        }
        if (pendingUntil > 0) {
            if (snapshot.session != null) {
                if (now <= pendingUntil && snapshot.session.target == options.target) owned = snapshot.session
                else if (tasks.isNotEmpty()) suppressed = true
                pendingUntil = 0
            } else if (now >= pendingUntil) {
                pendingUntil = 0
                suppressed = tasks.isNotEmpty()
                status = Status.FAILED
                return Action.NONE
            }
        }
        if (tasks.isEmpty() && !overflow) {
            if (owned != null && options.autoClose && now >= (idleSince ?: now) + options.closeDelayMs) {
                status = Status.READY
                return Action.STOP
            }
            status = when {
                owned != null && snapshot.connected -> Status.OWNED
                owned != null || pendingUntil > 0 -> Status.CONNECTING
                else -> Status.READY
            }
            return Action.NONE
        }
        if (owned != null) { status = if (snapshot.connected) Status.OWNED else Status.CONNECTING; return Action.NONE }
        if (snapshot.session != null) {
            suppressed = true
            status = Status.EXISTING
            return Action.NONE
        }
        if (suppressed || overflow) { status = Status.PAUSED; return Action.NONE }
        if (pendingUntil > 0) { status = Status.CONNECTING; return Action.NONE }
        if (!options.configured) { status = Status.UNAVAILABLE; return Action.NONE }
        pendingUntil = now + Settings.START_TIMEOUT
        status = Status.CONNECTING
        return Action.START
    }

    fun startFailed() { pendingUntil = 0; suppressed = true; status = Status.FAILED }
    fun relinquish() { owned = null; pendingUntil = 0 }

}
