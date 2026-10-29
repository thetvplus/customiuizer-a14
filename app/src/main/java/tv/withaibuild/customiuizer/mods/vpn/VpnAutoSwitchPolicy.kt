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
    private data class Task(val pkg: String, val process: Any)
    private val tasks = HashMap<Int, Task>()
    private var overflow = false
    private var suppressed = false
    private var pendingUntil = 0L
    private var closeAt = 0L
    var owned: Session? = null
        private set
    var status = Status.READY
        private set

    fun update(updated: Settings.Options, now: Long) {
        require(updated.configured && updated.target == options.target)
        options = updated
        tasks.entries.removeIf { it.value.pkg !in updated.apps }
        if (tasks.isEmpty()) {
            suppressed = false
            if (closeAt == 0L) closeAt = now + Settings.CLOSE_DELAY
        }
    }

    val deadline: Long get() = when {
        pendingUntil > 0 -> pendingUntil
        owned != null && tasks.isEmpty() && !overflow && options.autoClose -> closeAt
        else -> 0
    }

    fun nextDelay(snapshot: Snapshot, now: Long): Long? = deadline.takeIf { snapshot.known && it > now }?.minus(now)

    fun resumed(pkg: String, user: Int, taskId: Int, process: Any): Boolean {
        if (!options.configured || user != 0 || taskId < 0 || pkg !in options.apps) return false
        val task = Task(pkg, process)
        if (tasks[taskId] == task) return false
        if (tasks.size >= Settings.MAX_TASKS && taskId !in tasks) {
            // Unknown live tasks must never cause a premature automatic disconnect.
            overflow = true
            return false
        }
        tasks[taskId] = task
        closeAt = 0
        return true
    }

    fun removed(taskId: Int, user: Int, now: Long): Boolean {
        if (user != 0 || tasks.remove(taskId) == null) return false
        return afterRemoval(now)
    }

    fun died(pkg: String, process: Any, now: Long): Boolean {
        val changed = tasks.entries.removeIf { it.value.pkg == pkg && it.value.process === process }
        return changed && afterRemoval(now)
    }

    private fun afterRemoval(now: Long): Boolean {
        if (tasks.isEmpty()) {
            suppressed = false
            closeAt = now + Settings.CLOSE_DELAY
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
            if (owned != null && options.autoClose && now >= closeAt) { status = Status.READY; return Action.STOP }
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
