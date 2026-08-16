package tv.withaibuild.customiuizer.mods.utils

/**
 * Process-level once-guard for a hook that must be installed at most once.
 *
 * Transitions: UNINSTALLED -> INSTALLING -> INSTALLED (success) or UNINSTALLED (failure).
 * Fatal errors are rethrown after resetting to UNINSTALLED so the next lifecycle boundary can
 * retry. Reentrant calls while INSTALLING are ignored. Calls after INSTALLED are ignored.
 */
class HookInstallStateMachine {

    enum class State {
        UNINSTALLED,
        INSTALLING,
        INSTALLED,
    }

    @Volatile
    private var currentState: State = State.UNINSTALLED

    val state: State get() = currentState

    /**
     * Run [install] unless already [State.INSTALLED] or [State.INSTALLING].
     *
     * Returns whether this attempt succeeded. A non-fatal failure leaves the state as
     * [State.UNINSTALLED] and returns false so the caller can retry on the next generation.
     * A fatal error is rethrown after resetting to [State.UNINSTALLED].
     */
    fun install(install: () -> Boolean): Boolean {
        synchronized(this) {
            if (currentState == State.INSTALLED || currentState == State.INSTALLING) return false
            currentState = State.INSTALLING
        }
        val ok = try {
            install()
        } catch (t: Throwable) {
            currentState = State.UNINSTALLED
            FatalErrors.rethrowIfFatal(t)
            false
        }
        currentState = if (ok) State.INSTALLED else State.UNINSTALLED
        return ok
    }
}
