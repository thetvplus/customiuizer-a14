package tv.withaibuild.customiuizer.mods.utils

/**
 * Framework-free state machine for a single dark-tint receiver registration.
 *
 * It tracks the lifecycle of one receiver through three phases:
 * 1. `attach/register` — try to add the receiver and apply the current tint;
 * 2. `detach/unregister` — remove the receiver when the View detaches;
 * 3. `terminal dispose` — remove the attach listener and any tracking, regardless
 *    of whether the receiver was ever registered.
 *
 * The caller is responsible for the actual [registerFn]/[releaseFn]/[disposeFn]
 * side effects; this class only enforces the exact-once, idempotency and
 * generation-isolation invariants, which makes it unit-testable without Android
 * Views or ROM classes.
 */
internal class DarkTintRegistrationState(
    val owner: Any,
    val route: String,
) {

    private var registered: Boolean = false
    private var released: Boolean = false
    private var disposed: Boolean = false

    /**
     * True if the receiver is currently live and should be receiving dark callbacks.
     */
    val isActive: Boolean get() = registered && !disposed

    /**
     * True if this registration has been released at least once (normal View detach).
     */
    val isReleased: Boolean get() = released

    /**
     * True if this registration has been terminally disposed.
     */
    val isDisposed: Boolean get() = disposed

    /**
     * True if this state machine is still eligible for a new registration attempt.
     * A released-but-not-disposed state is eligible so the same View can reattach
     * and register again.
     */
    fun canRegister(): Boolean = !registered && !disposed

    /**
     * Attempt to register. Calls [registerFn] at most once; if [applyInitialTint] is
     * non-null it is also called once after a successful registration.
     *
     * Returns the result of [registerFn] if a registration attempt happened, false
     * if it was already registered or already disposed.
     */
    fun register(
        registerFn: () -> Boolean,
        applyInitialTint: (() -> Boolean)? = null,
    ): Boolean {
        if (registered || disposed) return false
        released = false
        val ok = registerFn()
        if (ok) {
            registered = true
            applyInitialTint?.invoke()
        }
        return ok
    }

    /**
     * Release the receiver by calling [releaseFn] if the registration is active.
     * Idempotent: subsequent calls return false and do not re-run [releaseFn].
     *
     * A release after a failed registration (never active) is recorded but does not
     * invoke [releaseFn].
     */
    fun release(releaseFn: () -> Unit): Boolean {
        if (disposed || released) return false
        released = true
        if (registered) {
            registered = false
            releaseFn()
            return true
        }
        return false
    }

    /**
     * Terminally dispose this registration. [disposeFn] is called exactly once, even
     * if the receiver was never successfully registered. [disposeFn] receives
     * `wasRegistered` so it can decide whether `removeDarkReceiver` is necessary.
     *
     * After dispose, [register] and [release] are both no-ops.
     */
    fun dispose(disposeFn: (wasRegistered: Boolean) -> Unit): Boolean {
        if (disposed) return false
        disposed = true
        val wasRegistered = registered
        registered = false
        released = true
        disposeFn(wasRegistered)
        return true
    }

    override fun toString(): String {
        return "DarkTintRegistrationState(owner=$owner, route=$route, registered=$registered, released=$released, disposed=$disposed)"
    }
}
