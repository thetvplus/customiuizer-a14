package tv.withaibuild.customiuizer.mods.utils

/**
 * Zero-allocation wrappers for framework and listener callbacks.
 *
 * Framework-invoked callbacks run outside the [MethodHook] try/catch, so an
 * unhandled throw from one of them can kill the host process. These inline
 * helpers log non-fatal failures and continue; [OutOfMemoryError] is rethrown
 * so the process does not continue in a corrupt state.
 */
object CallbackGuard {

    /**
     * Runs [block], logging failures other than [OutOfMemoryError].
     *
     * Framework-invoked callbacks — `Handler.handleMessage`, `BroadcastReceiver.onReceive`,
     * `ContentObserver.onChange`, `Runnable.run` — execute outside the [MethodHook] try/catch.
     * A throw there kills system_server, SystemUI or Launcher, so every such body is wrapped.
     * The function is inline: no object is allocated and no frame is added on the hot path.
     *
     * [OutOfMemoryError] is rethrown; continuing after OOM would run the host with corrupt state.
     */
    inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            if (t is OutOfMemoryError || t is ThreadDeath || t is VirtualMachineError) throw t
            XposedHelpers.log(t)
        }
    }

    /**
     * [guarded] for a callback that has to return a value, such as `OnLongClickListener`.
     *
     * [fallback] is what the framework sees when the body fails with a non-OOM error, so it
     * must be the answer that leaves the host's own behavior intact — usually "not consumed".
     * [OutOfMemoryError] is rethrown instead of returning [fallback].
     */
    inline fun <T> guarded(fallback: T, block: () -> T): T {
        return try {
            block()
        } catch (t: Throwable) {
            if (t is OutOfMemoryError || t is ThreadDeath || t is VirtualMachineError) throw t
            XposedHelpers.log(t)
            fallback
        }
    }
}
