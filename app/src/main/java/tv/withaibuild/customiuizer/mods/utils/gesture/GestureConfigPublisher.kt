package tv.withaibuild.customiuizer.mods.utils.gesture

import java.util.concurrent.atomic.AtomicReference

/**
 * Atomically publishes a snapshot of [GestureConfig] so that [ACTION_DOWN] does not have
 * to read and parse individual preferences on the hot path.
 *
 * The resolved config is replaced with a single atomic reference swap.  A gesture that is
 * already in progress keeps the config it started with; the next [ACTION_DOWN] sees the
 * latest published value.  If resolving the next config fails, the previous valid value is
 * retained.
 */
class GestureConfigPublisher(
    private val resolve: () -> GestureConfig,
    private val fallback: GestureConfig = GestureConfig(),
) {

    private val published = AtomicReference<GestureConfig>()

    /**
     * Resolve a fresh [GestureConfig] and atomically publish it.
     *
     * This is the only place where preference access, string-to-Int parsing and sensitivity
     * calculations are performed.
     */
    fun publish() {
        val next = try {
            resolve()
        } catch (err: Throwable) {
            when (err) {
                is OutOfMemoryError, is ThreadDeath, is VirtualMachineError -> throw err
                else -> null
            }
        }

        if (next != null) {
            published.set(next)
        } else if (published.get() == null) {
            published.set(fallback)
        }
    }

    /**
     * Returns the most recently published config, falling back to the constructor-supplied
     * fallback if nothing has been successfully published yet.
     */
    fun get(): GestureConfig = published.get() ?: fallback
}
