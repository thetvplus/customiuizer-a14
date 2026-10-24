package tv.withaibuild.customiuizer.mods

import tv.withaibuild.customiuizer.mods.utils.FatalErrors

/** Only window operations are exposed here; no media transport or task-close operation exists. */
internal interface PipWindowPort {
    data class Task(val token: Any, val surface: Any, val stable: Boolean = true,
        val locked: Boolean = false, val display: Int = 0)
    fun task(): Task?
    fun inputRegistered(): Boolean
    fun input(enabled: Boolean): Boolean
    /** Releasing clears our Surface hidden flag even when native keyguard still wants alpha 0. */
    fun visibility(visible: Boolean, releasing: Boolean = false)
    fun hideMenu()
    fun toggleStash(): Boolean
}

/** One borrowed task/surface lease. Native transitions reclaim it before their first transaction. */
internal class PipWindowControl(private val port: PipWindowPort) {
    private data class Hidden(val task: PipWindowPort.Task, var nativeVisible: Boolean = true)
    @Volatile private var hidden: Hidden? = null
    private var writingVisibility = false
    val isHidden: Boolean get() = hidden != null
    private fun same(first: PipWindowPort.Task, second: PipWindowPort.Task?) = second != null &&
        first.token == second.token && first.surface === second.surface
    private fun visible(value: Boolean, releasing: Boolean = false) {
        writingVisibility = true
        try { port.visibility(value, releasing) } finally { writingVisibility = false }
    }

    fun toggleFull(expected: PipWindowPort.Task): Boolean {
        val current = port.task() ?: return false
        if (!same(expected, current) || !current.stable || current.locked || current.display != 0) return false
        hidden?.let { lease ->
            if (same(lease.task, current)) return restore(lease, current)
            hidden = null // A replacement task never inherits the previous task's hidden state.
        }
        if (!port.inputRegistered()) return false
        port.hideMenu()
        val lease = Hidden(current)
        hidden = lease
        try {
            if (!port.input(false)) { hidden = null; return false }
            visible(false)
            return true
        } catch (error: Throwable) {
            // Repair the already-released input before propagating the original failure.
            try { visible(lease.nativeVisible && !current.locked, releasing = true) } catch (repair: Throwable) { FatalErrors.unwrapAndRethrowIfFatal(repair); error.addSuppressed(repair) }
            try { port.input(true) } catch (repair: Throwable) { FatalErrors.unwrapAndRethrowIfFatal(repair); error.addSuppressed(repair) }
            hidden = null
            throw error
        }
    }

    private fun restore(lease: Hidden, current: PipWindowPort.Task): Boolean {
        visible(lease.nativeVisible && !current.locked, releasing = true)
        return try {
            if (!port.input(true)) { visible(false); false }
            else { hidden = null; true }
        } catch (error: Throwable) {
            try { visible(false) } catch (repair: Throwable) { FatalErrors.unwrapAndRethrowIfFatal(repair); error.addSuppressed(repair) }
            throw error
        }
    }

    fun toggleStash(expected: PipWindowPort.Task): Boolean {
        val current = port.task() ?: return false
        if (!same(expected, current) || !current.stable || current.locked || current.display != 0) return false
        hidden?.let {
            if (!same(it.task, current)) hidden = null
            else if (!restore(it, current)) return false
        }
        port.hideMenu()
        return port.toggleStash()
    }

    fun nativeVisibility(requested: Boolean): Boolean {
        if (writingVisibility) return requested
        val lease = hidden ?: return requested
        if (!same(lease.task, port.task())) { hidden = null; return requested }
        lease.nativeVisible = requested
        return false
    }

    fun nativeTransition(surface: Any? = null) {
        val lease = hidden ?: return
        val current = port.task()
        if (surface != null && surface !== lease.task.surface) return
        if (!same(lease.task, current)) { hidden = null; return }
        restore(lease, current!!)
    }
}
